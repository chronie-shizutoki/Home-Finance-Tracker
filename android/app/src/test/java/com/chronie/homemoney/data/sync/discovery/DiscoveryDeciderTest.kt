package com.chronie.homemoney.data.sync.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The v1 discovery bugs that hurt in the field were all decisions, not I/O: answering
 * yourself, trusting a claimed IP, accepting a reply meant for a search that already ended.
 * Each one is reproduced here without a socket.
 */
class DiscoveryDeciderTest {

    private val self = DiscoveryIdentity(
        deviceId = "self-device",
        deviceName = "My Phone",
        deviceType = "ANDROID",
        syncPort = 50051
    )

    private val localAddresses = setOf("192.168.1.10", "10.0.2.5", "169.254.7.7")

    private fun decider(
        identity: DiscoveryIdentity = self,
        addresses: Set<String> = localAddresses
    ) = DiscoveryDecider(identity, addresses)

    private fun packet(
        type: DiscoveryType = DiscoveryType.ANNOUNCE,
        deviceId: String = "peer-1",
        deviceName: String = "Peer Phone",
        deviceType: String = "ANDROID",
        syncPort: Int = 51000,
        capabilities: Int = DiscoveryCapability.DEFAULT,
        nonce: Long = 0L
    ) = DiscoveryWire.encode(
        DiscoveryPacket(type, deviceId, deviceName, deviceType, syncPort, capabilities, nonce)
    )

    // ------------------------------------------------------------------ self filtering

    @Test
    fun `a packet from one of our own addresses is ignored`() {
        val data = packet()
        val action = decider().decide(data, data.size, "192.168.1.10", 40000, expectedNonce = null)

        assertEquals(
            DiscoveryAction.Ignore(IgnoreReason.SELF_ADDRESS, "192.168.1.10"),
            action
        )
    }

    @Test
    fun `every local address is checked, not just the first`() {
        // v1 compared against a single "the" local IP picked by interface order. On a phone
        // with Wi-Fi plus a VPN that comparison misses, and the device discovers itself.
        localAddresses.forEach { address ->
            val data = packet()
            val action = decider().decide(data, data.size, address, 40000, expectedNonce = null)
            assertTrue(
                "expected $address to be recognised as local, got $action",
                action is DiscoveryAction.Ignore && action.reason == IgnoreReason.SELF_ADDRESS
            )
        }
    }

    @Test
    fun `our own device id is rejected even from an address we do not recognise`() {
        // The backstop for the case above: the address check can miss, the id cannot.
        val data = packet(deviceId = self.deviceId)
        val action = decider().decide(data, data.size, "203.0.113.9", 40000, expectedNonce = null)

        assertEquals(
            DiscoveryAction.Ignore(IgnoreReason.SELF_DEVICE_ID, self.deviceId),
            action
        )
    }

    @Test
    fun `a device with no local addresses at all still functions`() {
        val data = packet()
        val action = decider(addresses = emptySet())
            .decide(data, data.size, "192.168.1.55", 40000, expectedNonce = null)

        assertTrue(action is DiscoveryAction.Reply || action is DiscoveryAction.Record)
    }

    // ------------------------------------------------------------------ garbage

    @Test
    fun `an empty datagram is ignored`() {
        val action = decider().decide(ByteArray(0), 0, "192.168.1.55", 40000, null)
        assertEquals(DiscoveryAction.Ignore(IgnoreReason.EMPTY), action)
    }

    @Test
    fun `a negative length is ignored rather than throwing`() {
        val action = decider().decide(ByteArray(64), -1, "192.168.1.55", 40000, null)
        assertEquals(DiscoveryAction.Ignore(IgnoreReason.EMPTY), action)
    }

    @Test
    fun `random noise on the discovery port never produces a device`() {
        // Port 12345 is a popular scratch port. v1 turned anything containing pipes into a
        // phantom entry in the user's list.
        val random = Random(20260801)
        var ignored = 0
        repeat(3000) {
            val size = random.nextInt(1, DiscoveryWire.MAX_PACKET_SIZE)
            val noise = ByteArray(size) { random.nextInt(256).toByte() }
            val action = decider().decide(noise, size, "192.168.1.55", 40000, null)
            if (action is DiscoveryAction.Ignore) ignored++
        }
        assertEquals(3000, ignored)
    }

    // ------------------------------------------------------------------ queries

    @Test
    fun `a query on the responder is answered at the sender's ephemeral port`() {
        // The whole point: the reply must not land on the discovery port, or every listener
        // sees it and the v1 amplification loop is back.
        val data = packet(type = DiscoveryType.QUERY, nonce = 99L)
        val action = decider().decide(data, data.size, "192.168.1.55", 41234, expectedNonce = null)

        assertTrue(action is DiscoveryAction.Reply)
        action as DiscoveryAction.Reply
        assertEquals("192.168.1.55", action.to)
        assertEquals(41234, action.port)
        assertNotEquals(LanDiscoveryService.DEFAULT_DISCOVERY_PORT, action.port)
        assertEquals(99L, action.nonce)
    }

    @Test
    fun `answering a query also records the querier`() {
        val data = packet(type = DiscoveryType.QUERY, deviceId = "peer-9", syncPort = 51999)
        val action = decider().decide(data, data.size, "192.168.1.55", 41234, null)

        val querier = (action as DiscoveryAction.Reply).querier
        assertEquals("peer-9", querier.deviceId)
        assertEquals("192.168.1.55", querier.address)
        assertEquals(51999, querier.syncPort)
    }

    @Test
    fun `a query arriving on the search socket is ignored, not answered`() {
        val data = packet(type = DiscoveryType.QUERY)
        val action = decider().decide(data, data.size, "192.168.1.55", 41234, expectedNonce = 7L)

        assertTrue(action is DiscoveryAction.Ignore)
        assertEquals(IgnoreReason.UNEXPECTED_QUERY, (action as DiscoveryAction.Ignore).reason)
    }

    // ------------------------------------------------------------------ announcements

    @Test
    fun `an announcement carrying our nonce is recorded`() {
        val data = packet(type = DiscoveryType.ANNOUNCE, nonce = 4242L)
        val action = decider().decide(data, data.size, "192.168.1.55", 12345, expectedNonce = 4242L)

        assertTrue(action is DiscoveryAction.Record)
        assertEquals("peer-1", (action as DiscoveryAction.Record).device.deviceId)
    }

    @Test
    fun `an announcement from a finished search round is dropped`() {
        // Otherwise a late reply repopulates a list the user just cleared.
        val data = packet(type = DiscoveryType.ANNOUNCE, nonce = 1111L)
        val action = decider().decide(data, data.size, "192.168.1.55", 12345, expectedNonce = 2222L)

        assertTrue(action is DiscoveryAction.Ignore)
        assertEquals(IgnoreReason.STALE_NONCE, (action as DiscoveryAction.Ignore).reason)
    }

    @Test
    fun `an unsolicited announcement is accepted during a search`() {
        // nonce 0 is "I am here", not "you asked". It is never stale.
        val data = packet(type = DiscoveryType.ANNOUNCE, nonce = 0L)
        val action = decider().decide(data, data.size, "192.168.1.55", 12345, expectedNonce = 8888L)

        assertTrue(action is DiscoveryAction.Record)
    }

    @Test
    fun `the responder records announcements regardless of nonce`() {
        val data = packet(type = DiscoveryType.ANNOUNCE, nonce = 555L)
        val action = decider().decide(data, data.size, "192.168.1.55", 12345, expectedNonce = null)

        assertTrue(action is DiscoveryAction.Record)
    }

    // ------------------------------------------------------------------ payload trust

    @Test
    fun `the address comes from the socket, not from the payload`() {
        val data = packet(deviceId = "peer-1")
        val action = decider().decide(data, data.size, "192.168.1.55", 12345, expectedNonce = 0L)

        assertEquals("192.168.1.55", (action as DiscoveryAction.Record).device.address)
    }

    @Test
    fun `a v2 announcement carries the peer's real sync port`() {
        val data = packet(syncPort = 60123)
        val action = decider().decide(data, data.size, "192.168.1.55", 12345, null)

        assertEquals(60123, (action as DiscoveryAction.Record).device.syncPort)
    }

    @Test
    fun `capabilities survive the round trip`() {
        val caps = DiscoveryCapability.FRAME_V2 or DiscoveryCapability.COMPRESSION
        val data = packet(capabilities = caps)
        val device = (decider().decide(data, data.size, "192.168.1.55", 12345, null) as DiscoveryAction.Record).device

        assertEquals(caps, device.capabilities)
        assertTrue(device.supportsFrameV2)
    }

    // ------------------------------------------------------------------ invariants

    @Test
    fun `no input ever produces a reply addressed to ourselves`() {
        // The amplification guard, stated as a property rather than a case.
        val random = Random(7)
        repeat(2000) {
            val fromSelf = random.nextBoolean()
            val sender = if (fromSelf) localAddresses.random(random) else "192.168.1.${random.nextInt(20, 250)}"
            val id = if (random.nextBoolean()) self.deviceId else "peer-${random.nextInt(50)}"
            val type = if (random.nextBoolean()) DiscoveryType.QUERY else DiscoveryType.ANNOUNCE
            val data = packet(type = type, deviceId = id, nonce = random.nextLong())

            val action = decider().decide(data, data.size, sender, random.nextInt(1024, 65535), null)
            if (action is DiscoveryAction.Reply) {
                assertTrue("replied to a local address", action.to !in localAddresses)
                assertNotEquals(self.deviceId, action.querier.deviceId)
            }
        }
    }

    @Test
    fun `a recorded device always has a usable port`() {
        val random = Random(11)
        repeat(1000) {
            val data = packet(
                deviceId = "peer-${random.nextInt(50)}",
                syncPort = random.nextInt(1, 65536),
                nonce = 0L
            )
            val action = decider().decide(data, data.size, "192.168.1.55", 12345, null)
            if (action is DiscoveryAction.Record) {
                assertTrue(action.device.syncPort in 1..65535)
            }
        }
    }
}
