package com.chronie.homemoney.data.sync.protocol

/**
 * Frame types of the LAN sync protocol.
 *
 * Mirrors `homemoney::sync::Opcode` in `app/src/main/cpp/protocol/sync_protocol.h`.
 * Values are frozen: never renumber an existing entry, only append new ones, otherwise
 * a device running an older build silently misinterprets every frame.
 */
enum class SyncOpcode(val value: Int) {
    /** A -> B: protocol version, device identity, capability bits. */
    HELLO(0x01),

    /** B -> A: negotiation result and whether the user still has to confirm. */
    HELLO_ACK(0x02),

    /** A -> B: pairing proof. */
    AUTH(0x03),

    /** B -> A: pairing verdict. */
    AUTH_ACK(0x04),

    /** Delta manifest: entity count, byte count, chunk count, watermark. */
    MANIFEST(0x10),

    /** Receiver reports the checkpoint it already holds, enabling resume. */
    MANIFEST_ACK(0x11),

    /** A single data chunk. */
    CHUNK(0x12),

    /** Cumulative chunk acknowledgement. */
    CHUNK_ACK(0x13),

    /**
     * Ask the peer for its own delta, one chunk at a time.
     *
     * The transport is strictly request/response, so the responder can never speak first.
     * v1 papered over that by returning B's entire database inside the single response,
     * which is also why the reverse direction could not be resumed, retried or bounded.
     * Pulling makes it an ordinary exchange with all the same guarantees as the push.
     */
    PULL(0x14),

    /** One chunk of the peer's delta, carrying the manifest metadata alongside. */
    PULL_ACK(0x15),

    /** Request to apply everything received so far. */
    COMMIT(0x20),

    /** Apply result plus conflict summary. */
    COMMIT_ACK(0x21),

    /** Application level keepalive probe. */
    PING(0x30),

    /** Keepalive response. */
    PONG(0x31),

    /** Structured error carrying a retryable hint. */
    ERROR(0x40),

    /** Graceful shutdown. */
    BYE(0x41);

    companion object {
        private val BY_VALUE = entries.associateBy(SyncOpcode::value)

        /** Returns null for an opcode this build does not know, never throws. */
        fun fromValue(value: Int): SyncOpcode? = BY_VALUE[value]
    }
}

/**
 * Frame flag bits.
 *
 * Mirrors `homemoney::sync::FrameFlag`. Unknown bits are preserved rather than rejected
 * so that a newer peer can add a flag without breaking this build.
 */
object SyncFrameFlags {
    const val NONE = 0x0000

    /** Payload is compressed. */
    const val COMPRESSED = 0x0001

    /** Final chunk of a sequence. */
    const val LAST_CHUNK = 0x0002

    /** Session was resumed from a checkpoint. */
    const val RESUMED = 0x0004

    /** Sender expects an explicit acknowledgement. */
    const val REQUIRE_ACK = 0x0008

    /** Every bit defined so far. */
    const val KNOWN = COMPRESSED or LAST_CHUNK or RESUMED or REQUIRE_ACK

    fun has(flags: Int, flag: Int): Boolean = flags and flag != 0

    /** Bits set by the peer that this build does not understand. */
    fun unknownBits(flags: Int): Int = flags and KNOWN.inv() and 0xFFFF

    fun describe(flags: Int): String {
        if (flags == NONE) return "NONE"
        val names = buildList {
            if (has(flags, COMPRESSED)) add("COMPRESSED")
            if (has(flags, LAST_CHUNK)) add("LAST_CHUNK")
            if (has(flags, RESUMED)) add("RESUMED")
            if (has(flags, REQUIRE_ACK)) add("REQUIRE_ACK")
            val unknown = unknownBits(flags)
            if (unknown != 0) add("UNKNOWN(0x%04X)".format(unknown))
        }
        return names.joinToString("|")
    }
}

/**
 * Error codes shared by both ends.
 *
 * Mirrors `homemoney::sync::SyncErrorCode`. [retryable] is part of the contract: the
 * backoff policy retries exactly these and fails fast on everything else, so that a
 * deterministic failure such as a protocol mismatch is reported immediately instead of
 * after the full retry budget has been burned.
 */
enum class SyncErrorCode(val code: Int, val retryable: Boolean) {
    OK(0, false),
    PROTOCOL_MISMATCH(1, false),
    AUTH_REJECTED(2, false),
    AUTH_TIMEOUT(3, false),
    NETWORK_UNREACHABLE(4, true),
    CONNECT_TIMEOUT(5, true),
    IO_TIMEOUT(6, true),
    PEER_CLOSED(7, true),
    CRC_MISMATCH(8, true),
    PAYLOAD_TOO_LARGE(9, false),
    PARSE_ERROR(10, false),
    APPLY_ERROR(11, false),
    BUSY(12, true),
    CANCELLED(13, false),
    INTERNAL(14, false),
    BAD_MAGIC(15, false),
    UNKNOWN_OPCODE(16, false);

    companion object {
        private val BY_CODE = entries.associateBy(SyncErrorCode::code)

        fun fromCode(code: Int): SyncErrorCode = BY_CODE[code] ?: INTERNAL
    }
}
