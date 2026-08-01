package com.chronie.homemoney.data.sync.protocol

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for the Kotlin frame codec.
 *
 * Where ProtocolConformanceTest proves the codec agrees with the native side, this class
 * proves it behaves sanely on its own: boundary values survive the round trip, corrupted
 * or hostile headers are rejected with the right error code, and a legacy v1 stream is
 * never mistaken for a v2 frame.
 */
class SyncWireProtocolTest {

    private fun header(
        opcode: SyncOpcode = SyncOpcode.CHUNK,
        flags: Int = SyncFrameFlags.NONE,
        sessionId: Long = 0x0123456789ABCDEFL,
        seq: Int = 7,
        payloadLen: Int = 0,
        payloadCrc32: Int = 0
    ) = FrameHeader(
        opcode = opcode,
        flags = flags,
        sessionId = sessionId,
        seq = seq,
        payloadLen = payloadLen,
        payloadCrc32 = payloadCrc32
    )

    private fun decodeSuccess(bytes: ByteArray): FrameHeader {
        val result = SyncWireProtocol.decodeHeader(bytes)
        assertTrue("expected success, got $result", result is FrameDecodeResult.Success)
        return (result as FrameDecodeResult.Success).header
    }

    private fun decodeFailure(bytes: ByteArray): SyncErrorCode {
        val result = SyncWireProtocol.decodeHeader(bytes)
        assertTrue("expected failure, got $result", result is FrameDecodeResult.Failure)
        return (result as FrameDecodeResult.Failure).code
    }

    /** Re-seals a mutated header so semantic checks are exercised instead of the checksum. */
    private fun reseal(bytes: ByteArray): ByteArray {
        val crc = Crc32c.compute(bytes, 0, SyncWireProtocol.HEADER_SIZE - 4)
        bytes[28] = (crc ushr 24).toByte()
        bytes[29] = (crc ushr 16).toByte()
        bytes[30] = (crc ushr 8).toByte()
        bytes[31] = crc.toByte()
        return bytes
    }

    // ------------------------------------------------------------- round trips

    @Test
    fun `header round trips`() {
        val original = header(
            opcode = SyncOpcode.MANIFEST,
            flags = SyncFrameFlags.REQUIRE_ACK or SyncFrameFlags.RESUMED,
            seq = 42,
            payloadLen = 1234,
            payloadCrc32 = 0x1A2B3C4D
        )

        assertEquals(original, decodeSuccess(SyncWireProtocol.encodeHeader(original)))
    }

    @Test
    fun `encoded header is exactly 32 bytes`() {
        assertEquals(32, SyncWireProtocol.encodeHeader(header()).size)
    }

    @Test
    fun `every opcode round trips`() {
        for (opcode in SyncOpcode.entries) {
            val decoded = decodeSuccess(SyncWireProtocol.encodeHeader(header(opcode = opcode)))
            assertEquals(opcode, decoded.opcode)
        }
    }

    @Test
    fun `all-ones session id and seq survive the round trip`() {
        // The JVM has no unsigned 64 bit type, so this is where a naive implementation
        // sign-extends and silently corrupts the field.
        val original = header(sessionId = -1L, seq = -1)
        val decoded = decodeSuccess(SyncWireProtocol.encodeHeader(original))

        assertEquals(-1L, decoded.sessionId)
        assertEquals(-1, decoded.seq)
        assertEquals(4_294_967_295L, decoded.seqUnsigned)
    }

    @Test
    fun `maximum payload length is accepted`() {
        val original = header(payloadLen = SyncWireProtocol.MAX_PAYLOAD_SIZE)
        assertEquals(
            SyncWireProtocol.MAX_PAYLOAD_SIZE,
            decodeSuccess(SyncWireProtocol.encodeHeader(original)).payloadLen
        )
    }

    @Test
    fun `random headers round trip`() {
        val random = Random(20260801)
        repeat(500) {
            val original = FrameHeader(
                opcode = SyncOpcode.entries[random.nextInt(SyncOpcode.entries.size)],
                flags = random.nextInt(0, 0x10000),
                sessionId = random.nextLong(),
                seq = random.nextInt(),
                payloadLen = random.nextInt(0, SyncWireProtocol.MAX_PAYLOAD_SIZE + 1),
                payloadCrc32 = random.nextInt()
            )
            assertEquals(original, decodeSuccess(SyncWireProtocol.encodeHeader(original)))
        }
    }

    // --------------------------------------------------------------- rejection

    @Test
    fun `a truncated buffer is rejected instead of throwing`() {
        val short = SyncWireProtocol.encodeHeader(header()).copyOf(31)
        assertEquals(SyncErrorCode.PARSE_ERROR, decodeFailure(short))
    }

    @Test
    fun `a wrong magic is reported as BAD_MAGIC not as a checksum failure`() {
        // The server depends on this distinction to fall back to the legacy v1 framing.
        val bytes = SyncWireProtocol.encodeHeader(header())
        bytes[0] = 0x00
        assertEquals(SyncErrorCode.BAD_MAGIC, decodeFailure(bytes))
    }

    @Test
    fun `a single flipped bit anywhere in the header is caught`() {
        for (byteIndex in 4 until SyncWireProtocol.HEADER_SIZE) {
            for (bit in 0 until 8) {
                val bytes = SyncWireProtocol.encodeHeader(
                    header(payloadLen = 4096, payloadCrc32 = 0x0BADC0DE)
                )
                bytes[byteIndex] = (bytes[byteIndex].toInt() xor (1 shl bit)).toByte()
                assertEquals(
                    "flipping bit $bit of byte $byteIndex went unnoticed",
                    SyncErrorCode.CRC_MISMATCH,
                    decodeFailure(bytes)
                )
            }
        }
    }

    @Test
    fun `an unsupported version is rejected`() {
        val tooOld = SyncWireProtocol.encodeHeader(header()).also { it[4] = 1 }
        assertEquals(SyncErrorCode.PROTOCOL_MISMATCH, decodeFailure(reseal(tooOld)))

        val tooNew = SyncWireProtocol.encodeHeader(header()).also { it[4] = 99.toByte() }
        assertEquals(SyncErrorCode.PROTOCOL_MISMATCH, decodeFailure(reseal(tooNew)))
    }

    @Test
    fun `an unknown opcode is rejected`() {
        val bytes = SyncWireProtocol.encodeHeader(header()).also { it[5] = 0x7F }
        assertEquals(SyncErrorCode.UNKNOWN_OPCODE, decodeFailure(reseal(bytes)))
    }

    @Test
    fun `an oversized payload length is rejected before anything is allocated`() {
        // 0xFFFFFFFF is what the old implementation happily turned into a huge allocation.
        val hostile = SyncWireProtocol.encodeHeader(header()).also {
            it[20] = 0xFF.toByte(); it[21] = 0xFF.toByte()
            it[22] = 0xFF.toByte(); it[23] = 0xFF.toByte()
        }
        assertEquals(SyncErrorCode.PAYLOAD_TOO_LARGE, decodeFailure(reseal(hostile)))

        val oneOver = SyncWireProtocol.encodeHeader(header()).also {
            val len = SyncWireProtocol.MAX_PAYLOAD_SIZE + 1
            it[20] = (len ushr 24).toByte(); it[21] = (len ushr 16).toByte()
            it[22] = (len ushr 8).toByte(); it[23] = len.toByte()
        }
        assertEquals(SyncErrorCode.PAYLOAD_TOO_LARGE, decodeFailure(reseal(oneOver)))
    }

    @Test
    fun `encoding refuses an oversized payload length up front`() {
        val error = runCatching {
            SyncWireProtocol.encodeHeader(header(payloadLen = SyncWireProtocol.MAX_PAYLOAD_SIZE + 1))
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
    }

    @Test
    fun `unknown flag bits are preserved rather than rejected`() {
        // A newer peer must be able to add a flag without this build refusing its frames.
        val forwardCompatible = header(flags = SyncFrameFlags.LAST_CHUNK or 0x4000)
        val decoded = decodeSuccess(SyncWireProtocol.encodeHeader(forwardCompatible))

        assertEquals(SyncFrameFlags.LAST_CHUNK or 0x4000, decoded.flags)
        assertTrue(decoded.hasFlag(SyncFrameFlags.LAST_CHUNK))
        assertEquals(0x4000, SyncFrameFlags.unknownBits(decoded.flags))
    }

    // ------------------------------------------------------- version detection

    @Test
    fun `a legacy v1 length prefix is not mistaken for a v2 frame`() {
        // v1 framing is a bare big endian length; 1024 bytes looks like 00 00 04 00.
        val v1Prefix = byteArrayOf(0x00, 0x00, 0x04, 0x00)
        assertFalse(SyncWireProtocol.looksLikeV2Frame(v1Prefix))
        assertTrue(SyncWireProtocol.looksLikeV2Frame(SyncWireProtocol.encodeHeader(header())))
    }

    @Test
    fun `version detection tolerates a short buffer`() {
        assertFalse(SyncWireProtocol.looksLikeV2Frame(byteArrayOf(0x48, 0x46, 0x53)))
    }

    // ----------------------------------------------------------- full frames

    @Test
    fun `encodeFrame hashes the payload and appends it`() {
        val payload = "hello lan sync".toByteArray()
        val frame = SyncWireProtocol.encodeFrame(SyncOpcode.CHUNK, payload, seq = 3)

        val decoded = decodeSuccess(frame)
        assertEquals(payload.size, decoded.payloadLen)
        assertEquals(Crc32c.compute(payload), decoded.payloadCrc32)
        assertEquals(
            payload.toList(),
            frame.copyOfRange(SyncWireProtocol.HEADER_SIZE, frame.size).toList()
        )
    }

    @Test
    fun `a corrupted payload is detected by the payload checksum`() {
        val payload = ByteArray(4096) { it.toByte() }
        val frame = SyncWireProtocol.encodeFrame(SyncOpcode.CHUNK, payload)
        val declared = decodeSuccess(frame).payloadCrc32

        frame[SyncWireProtocol.HEADER_SIZE + 100] =
            (frame[SyncWireProtocol.HEADER_SIZE + 100].toInt() xor 0x01).toByte()

        val actual = Crc32c.compute(frame, SyncWireProtocol.HEADER_SIZE, payload.size)
        assertNotEquals(declared, actual)
    }

    @Test
    fun `frames can be decoded from the middle of a buffer`() {
        // The reader hands us its receive buffer, so decoding must honour the offset
        // rather than assuming the frame starts at index 0.
        val padding = ByteArray(11) { 0x5A }
        val buffer = padding + SyncWireProtocol.encodeFrame(SyncOpcode.PONG, seq = 9)

        assertEquals(SyncErrorCode.BAD_MAGIC, decodeFailure(buffer))

        val result = SyncWireProtocol.decodeHeader(buffer, offset = padding.size)
        assertTrue("expected success, got $result", result is FrameDecodeResult.Success)
        val header = (result as FrameDecodeResult.Success).header
        assertEquals(SyncOpcode.PONG, header.opcode)
        assertEquals(9, header.seq)
    }

    // -------------------------------------------------------------- error map

    @Test
    fun `retry classification matches the native table`() {
        val retryable = setOf(
            SyncErrorCode.NETWORK_UNREACHABLE,
            SyncErrorCode.CONNECT_TIMEOUT,
            SyncErrorCode.IO_TIMEOUT,
            SyncErrorCode.PEER_CLOSED,
            SyncErrorCode.CRC_MISMATCH,
            SyncErrorCode.BUSY
        )
        for (code in SyncErrorCode.entries) {
            assertEquals(
                "${code.name} retryable flag drifted from sync_protocol.h",
                code in retryable,
                code.retryable
            )
        }
    }

    @Test
    fun `error codes are stable and unique`() {
        val codes = SyncErrorCode.entries.map(SyncErrorCode::code)
        assertEquals("error code values must be unique", codes.size, codes.toSet().size)
        assertEquals(0, SyncErrorCode.OK.code)
        assertEquals(SyncErrorCode.INTERNAL, SyncErrorCode.fromCode(9999))
    }

    @Test
    fun `opcode values are stable and unique`() {
        val values = SyncOpcode.entries.map(SyncOpcode::value)
        assertEquals("opcode values must be unique", values.size, values.toSet().size)
        assertEquals(0x01, SyncOpcode.HELLO.value)
        assertEquals(0x41, SyncOpcode.BYE.value)
        assertNull(SyncOpcode.fromValue(0x99))
    }
}
