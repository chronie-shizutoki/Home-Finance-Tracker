package com.chronie.homemoney.data.sync.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.random.Random

class DiscoveryPacketTest {

    private fun sample(
        type: DiscoveryType = DiscoveryType.QUERY,
        deviceId: String = "device-a",
        deviceName: String = "Pixel 8",
        deviceType: String = "ANDROID",
        syncPort: Int = 50051,
        capabilities: Int = DiscoveryCapability.DEFAULT,
        nonce: Long = 0x0123456789ABCDEFL
    ) = DiscoveryPacket(
        type = type,
        deviceId = deviceId,
        deviceName = deviceName,
        deviceType = deviceType,
        syncPort = syncPort,
        capabilities = capabilities,
        nonce = nonce
    )

    private fun ok(data: ByteArray, length: Int = data.size): DiscoveryPacket {
        val result = DiscoveryWire.parse(data, length)
        assertTrue("expected a parse, got $result", result is DiscoveryParse.Ok)
        return (result as DiscoveryParse.Ok).packet
    }

    private fun rejection(data: ByteArray, length: Int = data.size): DiscoveryParse.Rejected {
        val result = DiscoveryWire.parse(data, length)
        assertTrue("expected a rejection, got $result", result is DiscoveryParse.Rejected)
        return result as DiscoveryParse.Rejected
    }

    // ---------------------------------------------------------------- round trip

    @Test
    fun `a packet survives encode and parse unchanged`() {
        val original = sample()
        assertEquals(original, ok(DiscoveryWire.encode(original)))
    }

    @Test
    fun `both types round trip`() {
        for (type in DiscoveryType.entries) {
            assertEquals(type, ok(DiscoveryWire.encode(sample(type = type))).type)
        }
    }

    @Test
    fun `non-ascii device names survive the round trip`() {
        val name = "客厅的平板 📱"
        assertEquals(name, ok(DiscoveryWire.encode(sample(deviceName = name))).deviceName)
    }

    @Test
    fun `an empty device name is legal`() {
        // A user can clear the name field. That is not a protocol error.
        assertEquals("", ok(DiscoveryWire.encode(sample(deviceName = ""))).deviceName)
    }

    @Test
    fun `the port survives values above the signed short range`() {
        // 50051 fits, but a user-chosen port above 32767 would come back negative if the
        // decoder forgot to mask. That is the whole reason the field is read unsigned.
        assertEquals(65535, ok(DiscoveryWire.encode(sample(syncPort = 65535))).syncPort)
        assertEquals(50051, ok(DiscoveryWire.encode(sample(syncPort = 50051))).syncPort)
        assertEquals(40000, ok(DiscoveryWire.encode(sample(syncPort = 40000))).syncPort)
    }

    @Test
    fun `all capability bits survive including the sign bit`() {
        val allBits = -1
        assertEquals(allBits, ok(DiscoveryWire.encode(sample(capabilities = allBits))).capabilities)
    }

    @Test
    fun `a negative nonce survives`() {
        val nonce = Long.MIN_VALUE
        assertEquals(nonce, ok(DiscoveryWire.encode(sample(nonce = nonce))).nonce)
    }

    @Test
    fun `encoding is deterministic`() {
        // Two devices must produce identical bytes for identical state, otherwise nothing
        // downstream can be compared or cached.
        val packet = sample()
        assertTrue(DiscoveryWire.encode(packet).contentEquals(DiscoveryWire.encode(packet)))
    }

    @Test
    fun `the header is the documented size`() {
        val encoded = DiscoveryWire.encode(sample(deviceId = "a", deviceName = "b", deviceType = "c"))
        assertEquals(DiscoveryWire.HEADER_SIZE + 3 * (2 + 1), encoded.size)
    }

    @Test
    fun `a realistic packet stays well under the MTU`() {
        val encoded = DiscoveryWire.encode(
            sample(deviceId = "x".repeat(64), deviceName = "名".repeat(32), deviceType = "ANDROID")
        )
        assertTrue("packet was ${encoded.size} bytes", encoded.size <= DiscoveryWire.MAX_PACKET_SIZE)
    }

    // ---------------------------------------------------------------- noise rejection

    @Test
    fun `random noise is rejected`() {
        // The v1 format accepted anything containing pipes. Port 12345 is a popular scratch
        // port, so this is not a hypothetical.
        val random = Random(20260801)
        var accepted = 0
        repeat(5000) {
            val junk = random.nextBytes(random.nextInt(1, 200))
            if (DiscoveryWire.parse(junk) is DiscoveryParse.Ok) accepted++
        }
        assertEquals("random noise parsed as a device", 0, accepted)
    }

    @Test
    fun `an empty datagram is rejected`() {
        assertEquals(DiscoveryParse.Reason.TOO_SHORT, rejection(ByteArray(0)).reason)
    }

    @Test
    fun `a packet cut short in the header is rejected`() {
        val encoded = DiscoveryWire.encode(sample())
        for (length in 0 until DiscoveryWire.HEADER_SIZE) {
            assertEquals(
                "length=$length should be too short",
                DiscoveryParse.Reason.TOO_SHORT,
                rejection(encoded, length).reason
            )
        }
    }

    @Test
    fun `a packet cut short inside a string field is rejected`() {
        val encoded = DiscoveryWire.encode(sample())
        // Everything past the header but short of the full packet must fail, and must never
        // return a half-filled device. A truncated datagram is exactly what a lossy link
        // produces, so this is the common case, not the exotic one.
        for (length in (DiscoveryWire.HEADER_SIZE + 6) until encoded.size) {
            val result = DiscoveryWire.parse(encoded, length)
            assertTrue("length=$length parsed: $result", result is DiscoveryParse.Rejected)
        }
    }

    @Test
    fun `a wrong magic is rejected before anything else is read`() {
        val encoded = DiscoveryWire.encode(sample())
        encoded[0] = 0x00
        assertEquals(DiscoveryParse.Reason.BAD_MAGIC, rejection(encoded).reason)
    }

    @Test
    fun `a version below the compatible floor is rejected`() {
        val encoded = DiscoveryWire.encode(sample())
        encoded[4] = 1
        assertEquals(DiscoveryParse.Reason.UNSUPPORTED_VERSION, rejection(encoded).reason)
    }

    @Test
    fun `an unknown type is rejected`() {
        val encoded = DiscoveryWire.encode(sample())
        encoded[5] = 99
        assertEquals(DiscoveryParse.Reason.UNKNOWN_TYPE, rejection(encoded).reason)
    }

    @Test
    fun `a zero port is rejected`() {
        val encoded = DiscoveryWire.encode(sample())
        encoded[8] = 0
        encoded[9] = 0
        assertEquals(DiscoveryParse.Reason.BAD_PORT, rejection(encoded).reason)
    }

    @Test
    fun `an oversized datagram is rejected without allocating`() {
        val huge = ByteArray(DiscoveryWire.MAX_PACKET_SIZE + 1)
        ByteBuffer.wrap(huge).order(ByteOrder.BIG_ENDIAN).putInt(DiscoveryWire.MAGIC)
        assertEquals(DiscoveryParse.Reason.TOO_LONG, rejection(huge).reason)
    }

    @Test
    fun `a length prefix larger than the field cap is rejected`() {
        // The attack shape: a valid header followed by a declared length that would make the
        // parser allocate or read past the datagram.
        val encoded = DiscoveryWire.encode(sample())
        val forged = encoded.copyOf()
        ByteBuffer.wrap(forged).order(ByteOrder.BIG_ENDIAN)
            .putShort(DiscoveryWire.HEADER_SIZE, 0x7FFF)
        assertEquals(DiscoveryParse.Reason.FIELD_TOO_LONG, rejection(forged).reason)
    }

    @Test
    fun `a length prefix pointing past the buffer is rejected as truncated`() {
        val encoded = DiscoveryWire.encode(sample())
        val forged = encoded.copyOf()
        // Within the cap, but longer than what is actually present.
        ByteBuffer.wrap(forged).order(ByteOrder.BIG_ENDIAN)
            .putShort(DiscoveryWire.HEADER_SIZE, DiscoveryWire.MAX_DEVICE_ID_BYTES.toShort())
        assertEquals(DiscoveryParse.Reason.TRUNCATED_FIELD, rejection(forged).reason)
    }

    @Test
    fun `an empty device id is rejected`() {
        val packet = sample(deviceId = "x")
        val encoded = DiscoveryWire.encode(packet)
        val forged = encoded.copyOf(encoded.size - 1)
        ByteBuffer.wrap(forged).order(ByteOrder.BIG_ENDIAN).putShort(DiscoveryWire.HEADER_SIZE, 0)
        // Re-encoding by hand is fragile, so assert on the reason rather than the shape.
        val result = DiscoveryWire.parse(forged)
        assertTrue("expected rejection, got $result", result is DiscoveryParse.Rejected)
    }

    @Test
    fun `encoding refuses fields that exceed the caps`() {
        val tooLong = "x".repeat(DiscoveryWire.MAX_DEVICE_ID_BYTES + 1)
        val threw = runCatching { DiscoveryWire.encode(sample(deviceId = tooLong)) }.isFailure
        assertTrue("encode accepted an oversized deviceId", threw)
    }

    @Test
    fun `encoding refuses an empty device id`() {
        assertTrue(runCatching { DiscoveryWire.encode(sample(deviceId = "")) }.isFailure)
    }

    @Test
    fun `encoding refuses an out of range port`() {
        assertTrue(runCatching { DiscoveryWire.encode(sample(syncPort = 0)) }.isFailure)
        assertTrue(runCatching { DiscoveryWire.encode(sample(syncPort = 70000)) }.isFailure)
    }

    // ---------------------------------------------------------------- forward compatibility

    @Test
    fun `trailing bytes from a future version are ignored not rejected`() {
        // This is the entire forward-compatibility contract. If it fails, a v3 build becomes
        // invisible to v2 and the two silently stop syncing with no error anywhere.
        val original = sample()
        val encoded = DiscoveryWire.encode(original)
        val extended = encoded + byteArrayOf(0x77, 0x66, 0x55, 0x44, 0x33)
        assertEquals(original, ok(extended))
    }

    @Test
    fun `a higher version number is accepted`() {
        val encoded = DiscoveryWire.encode(sample())
        encoded[4] = 3
        assertEquals(3, ok(encoded).version)
    }

    @Test
    fun `the sender's minimum supported version is reported`() {
        val packet = sample().copy(minSupportedVersion = 2)
        assertEquals(2, ok(DiscoveryWire.encode(packet)).minSupportedVersion)
    }

    // ---------------------------------------------------------------- capabilities

    @Test
    fun `capability bits are read back individually`() {
        val packet = ok(
            DiscoveryWire.encode(
                sample(capabilities = DiscoveryCapability.FRAME_V2 or DiscoveryCapability.COMPRESSION)
            )
        )
        assertTrue(DiscoveryCapability.has(packet.capabilities, DiscoveryCapability.FRAME_V2))
        assertTrue(DiscoveryCapability.has(packet.capabilities, DiscoveryCapability.COMPRESSION))
        assertTrue(!DiscoveryCapability.has(packet.capabilities, DiscoveryCapability.PAIRING_AUTH))
    }

    @Test
    fun `capability bits do not overlap`() {
        val bits = listOf(
            DiscoveryCapability.FRAME_V2,
            DiscoveryCapability.PAIRING_AUTH,
            DiscoveryCapability.COMPRESSION,
            DiscoveryCapability.RESUME
        )
        assertEquals("capability bits collide", bits.size, bits.toSet().size)
        assertEquals("a capability bit is not a single bit", bits.sum(), bits.reduce { a, b -> a or b })
    }

    @Test
    fun `the two magics differ`() {
        // "HFSD" for discovery, "HFS1" for the TCP frame. Sharing one would let a stray
        // discovery datagram look like the start of a session.
        assertNotEquals(0x48465331, DiscoveryWire.MAGIC)
    }
}
