package com.chronie.homemoney.data.sync.protocol

/**
 * Kotlin side of the LAN sync wire protocol.
 *
 * This is one half of a two-sided single source of truth; the other half is
 * `app/src/main/cpp/protocol/sync_protocol.h`. Both are pinned to the golden vectors in
 * `app/src/main/cpp/protocol/frame_vectors.txt`:
 *   - C++ asserts them at compile time in `protocol_conformance.cpp`,
 *   - Kotlin asserts them in `ProtocolConformanceTest`.
 * Editing a constant on one side without regenerating the vectors breaks a build, which
 * is the whole point: the previous implementation had no such guard and the two ends
 * drifted apart.
 *
 * Frame layout (32 byte header, big endian, then the payload):
 *
 * ```
 *  offset size field           notes
 *  0      4    magic           0x48465331 ("HFS1")
 *  4      1    version         protocol version
 *  5      1    opcode          see SyncOpcode
 *  6      2    flags           see SyncFrameFlags
 *  8      8    session_id      unique per session
 *  16     4    seq             monotonically increasing within a session
 *  20     4    payload_len     capped at MAX_PAYLOAD_SIZE
 *  24     4    payload_crc32   CRC-32C of the payload
 *  28     4    header_crc32    CRC-32C of bytes 0..27
 * ```
 *
 * 32-bit wire fields are carried as [Int] and the 64-bit session id as [Long], using the
 * raw two's complement bit pattern rather than a widened unsigned value. That keeps the
 * JVM representation identical to the C++ `uint32_t` / `uint64_t` and removes any chance
 * of a sign-extension mismatch at the boundary.
 */
object SyncWireProtocol {

    /** "HFS1". Chosen so a v2 frame differs from a v1 length prefix in the very first byte. */
    const val MAGIC: Int = 0x48465331

    /** Protocol version implemented by this build. */
    const val PROTOCOL_VERSION: Int = 2

    /** Oldest version this build can still speak over the v2 framing. */
    const val MIN_SUPPORTED_VERSION: Int = 2

    /** Fixed header size in bytes. */
    const val HEADER_SIZE: Int = 32

    /** Hard cap for a single frame payload (1 MiB), enforced before any allocation. */
    const val MAX_PAYLOAD_SIZE: Int = 1 shl 20

    /** Default chunk payload size; adapts at runtime between [MIN_CHUNK_SIZE] and [MAX_CHUNK_SIZE]. */
    const val DEFAULT_CHUNK_SIZE: Int = 64 * 1024
    const val MIN_CHUNK_SIZE: Int = 16 * 1024
    const val MAX_CHUNK_SIZE: Int = 256 * 1024

    private const val OFFSET_MAGIC = 0
    private const val OFFSET_VERSION = 4
    private const val OFFSET_OPCODE = 5
    private const val OFFSET_FLAGS = 6
    private const val OFFSET_SESSION_ID = 8
    private const val OFFSET_SEQ = 16
    private const val OFFSET_PAYLOAD_LEN = 20
    private const val OFFSET_PAYLOAD_CRC = 24
    private const val OFFSET_HEADER_CRC = 28

    /**
     * Serialise a header and fill in the header checksum.
     *
     * The payload checksum is taken from [header] rather than computed here because the
     * payload is normally hashed while it is being streamed, not held in one buffer.
     */
    fun encodeHeader(header: FrameHeader): ByteArray {
        require(header.payloadLen in 0..MAX_PAYLOAD_SIZE) {
            "payloadLen ${header.payloadLen} outside 0..$MAX_PAYLOAD_SIZE"
        }
        val out = ByteArray(HEADER_SIZE)
        putInt(out, OFFSET_MAGIC, MAGIC)
        out[OFFSET_VERSION] = header.version.toByte()
        out[OFFSET_OPCODE] = header.opcode.value.toByte()
        putShort(out, OFFSET_FLAGS, header.flags)
        putLong(out, OFFSET_SESSION_ID, header.sessionId)
        putInt(out, OFFSET_SEQ, header.seq)
        putInt(out, OFFSET_PAYLOAD_LEN, header.payloadLen)
        putInt(out, OFFSET_PAYLOAD_CRC, header.payloadCrc32)
        putInt(out, OFFSET_HEADER_CRC, Crc32c.compute(out, 0, HEADER_SIZE - 4))
        return out
    }

    /**
     * Parse and validate a header.
     *
     * Checks run in the same order as the native implementation, and the order is load
     * bearing: the magic first because it is the cheapest and identifies a legacy peer,
     * then the header checksum so that every field below is known good, then the semantic
     * limits. A caller may only trust [FrameHeader.payloadLen] once this returns
     * [FrameDecodeResult.Success].
     */
    fun decodeHeader(source: ByteArray, offset: Int = 0): FrameDecodeResult {
        if (offset < 0 || source.size - offset < HEADER_SIZE) {
            return FrameDecodeResult.Failure(SyncErrorCode.PARSE_ERROR, "header truncated")
        }

        if (readInt(source, offset + OFFSET_MAGIC) != MAGIC) {
            return FrameDecodeResult.Failure(SyncErrorCode.BAD_MAGIC, "not a v2 frame")
        }

        val expectedCrc = readInt(source, offset + OFFSET_HEADER_CRC)
        val actualCrc = Crc32c.compute(source, offset, HEADER_SIZE - 4)
        if (actualCrc != expectedCrc) {
            return FrameDecodeResult.Failure(
                SyncErrorCode.CRC_MISMATCH,
                "header crc 0x%08X != 0x%08X".format(actualCrc, expectedCrc)
            )
        }

        val version = source[offset + OFFSET_VERSION].toInt() and 0xFF
        if (version < MIN_SUPPORTED_VERSION || version > PROTOCOL_VERSION) {
            return FrameDecodeResult.Failure(
                SyncErrorCode.PROTOCOL_MISMATCH,
                "peer speaks version $version, this build supports " +
                        "$MIN_SUPPORTED_VERSION..$PROTOCOL_VERSION"
            )
        }

        val rawOpcode = source[offset + OFFSET_OPCODE].toInt() and 0xFF
        val opcode = SyncOpcode.fromValue(rawOpcode)
            ?: return FrameDecodeResult.Failure(
                SyncErrorCode.UNKNOWN_OPCODE,
                "opcode 0x%02X".format(rawOpcode)
            )

        val payloadLen = readInt(source, offset + OFFSET_PAYLOAD_LEN)
        // The unsigned comparison also rejects a negative value, which is what a hostile
        // or corrupted 0xFFFFFFFF length decodes to on the JVM.
        if (payloadLen.toUInt() > MAX_PAYLOAD_SIZE.toUInt()) {
            return FrameDecodeResult.Failure(
                SyncErrorCode.PAYLOAD_TOO_LARGE,
                "payloadLen ${payloadLen.toUInt()} exceeds $MAX_PAYLOAD_SIZE"
            )
        }

        return FrameDecodeResult.Success(
            FrameHeader(
                version = version,
                opcode = opcode,
                flags = readShort(source, offset + OFFSET_FLAGS),
                sessionId = readLong(source, offset + OFFSET_SESSION_ID),
                seq = readInt(source, offset + OFFSET_SEQ),
                payloadLen = payloadLen,
                payloadCrc32 = readInt(source, offset + OFFSET_PAYLOAD_CRC)
            )
        )
    }

    /**
     * Tells a v2 stream from a legacy v1 stream by its first four bytes.
     *
     * v1 framing is a bare big endian length prefix, so its leading byte is 0x00 for any
     * payload below 16 MiB, while v2 always starts with the magic. This is what lets the
     * server accept both without a flag day.
     */
    fun looksLikeV2Frame(source: ByteArray, offset: Int = 0): Boolean =
        source.size - offset >= 4 && readInt(source, offset) == MAGIC

    /** Convenience wrapper that hashes the payload and returns header + payload. */
    fun encodeFrame(
        opcode: SyncOpcode,
        payload: ByteArray = ByteArray(0),
        flags: Int = SyncFrameFlags.NONE,
        sessionId: Long = 0L,
        seq: Int = 0
    ): ByteArray {
        val header = encodeHeader(
            FrameHeader(
                opcode = opcode,
                flags = flags,
                sessionId = sessionId,
                seq = seq,
                payloadLen = payload.size,
                payloadCrc32 = Crc32c.compute(payload)
            )
        )
        return header + payload
    }

    // ------------------------------------------------------------ byte helpers

    private fun putShort(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value ushr 8).toByte()
        out[offset + 1] = value.toByte()
    }

    private fun putInt(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value ushr 24).toByte()
        out[offset + 1] = (value ushr 16).toByte()
        out[offset + 2] = (value ushr 8).toByte()
        out[offset + 3] = value.toByte()
    }

    private fun putLong(out: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            out[offset + i] = (value ushr (56 - 8 * i)).toByte()
        }
    }

    private fun readShort(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 8) or (source[offset + 1].toInt() and 0xFF)

    private fun readInt(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
                ((source[offset + 1].toInt() and 0xFF) shl 16) or
                ((source[offset + 2].toInt() and 0xFF) shl 8) or
                (source[offset + 3].toInt() and 0xFF)

    private fun readLong(source: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (source[offset + i].toLong() and 0xFF)
        }
        return value
    }
}

/**
 * A decoded frame header.
 *
 * [seq] and [payloadCrc32] hold raw 32-bit patterns and [sessionId] a raw 64-bit pattern,
 * so they may read as negative. Use [seqUnsigned] when a human readable value is needed.
 */
data class FrameHeader(
    val version: Int = SyncWireProtocol.PROTOCOL_VERSION,
    val opcode: SyncOpcode,
    val flags: Int = SyncFrameFlags.NONE,
    val sessionId: Long = 0L,
    val seq: Int = 0,
    val payloadLen: Int = 0,
    val payloadCrc32: Int = 0
) {
    fun hasFlag(flag: Int): Boolean = SyncFrameFlags.has(flags, flag)

    val seqUnsigned: Long get() = seq.toLong() and 0xFFFFFFFFL

    /** Compact single-line form for the structured sync log. */
    fun toLogString(): String =
        "op=${opcode.name} seq=$seqUnsigned len=$payloadLen " +
                "flags=${SyncFrameFlags.describe(flags)} " +
                "session=%016X".format(sessionId)
}

/** Outcome of [SyncWireProtocol.decodeHeader]. */
sealed interface FrameDecodeResult {

    data class Success(val header: FrameHeader) : FrameDecodeResult

    /**
     * @param code the shared error code, whose [SyncErrorCode.retryable] flag decides
     *   whether the transport reconnects or gives up.
     * @param detail human readable context for the log, never shown to the user.
     */
    data class Failure(val code: SyncErrorCode, val detail: String) : FrameDecodeResult

    val headerOrNull: FrameHeader?
        get() = (this as? Success)?.header
}
