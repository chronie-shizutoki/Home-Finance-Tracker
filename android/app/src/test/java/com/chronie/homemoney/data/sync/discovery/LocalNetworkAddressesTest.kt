package com.chronie.homemoney.data.sync.discovery

import com.chronie.homemoney.data.sync.discovery.LocalNetworkAddresses.Nic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAddressesTest {

    private fun nic(
        name: String,
        up: Boolean = true,
        loopback: Boolean = false,
        virtual: Boolean = false,
        pointToPoint: Boolean = false,
        vararg addresses: Pair<String, String?>
    ) = Nic(
        name = name,
        isUp = up,
        isLoopback = loopback,
        isVirtual = virtual,
        isPointToPoint = pointToPoint,
        addresses = addresses.map { Nic.Address(it.first, it.second) }
    )

    private val wlan = nic("wlan0", addresses = arrayOf("192.168.1.5" to "192.168.1.255"))
    private val loopback = nic("lo", loopback = true, addresses = arrayOf("127.0.0.1" to null))
    private val vpn = nic("tun0", pointToPoint = true, addresses = arrayOf("10.8.0.2" to null))
    private val hotspot = nic("ap0", addresses = arrayOf("192.168.43.1" to "192.168.43.255"))
    private val cellular = nic("rmnet0", pointToPoint = true, addresses = arrayOf("10.20.30.40" to null))
    private val down = nic("eth0", up = false, addresses = arrayOf("192.168.99.9" to "192.168.99.255"))

    // ---------------------------------------------------------------- local address set

    @Test
    fun `every address on every live interface counts as local`() {
        // This set is used to recognise our own traffic. Missing one address means the device
        // answers its own broadcast and lists itself as a peer — the v1 bug.
        val local = LocalNetworkAddresses.selectLocalIpv4(listOf(wlan, vpn, hotspot, loopback))
        assertEquals(setOf("192.168.1.5", "10.8.0.2", "192.168.43.1"), local)
    }

    @Test
    fun `interfaces that are down contribute no addresses`() {
        assertEquals(setOf("192.168.1.5"), LocalNetworkAddresses.selectLocalIpv4(listOf(wlan, down)))
    }

    @Test
    fun `loopback is excluded`() {
        assertTrue("127.0.0.1" !in LocalNetworkAddresses.selectLocalIpv4(listOf(wlan, loopback)))
    }

    @Test
    fun `an empty interface list yields an empty set`() {
        assertTrue(LocalNetworkAddresses.selectLocalIpv4(emptyList()).isEmpty())
    }

    @Test
    fun `blank addresses are dropped`() {
        val broken = nic("wlan0", addresses = arrayOf("" to null, "192.168.1.5" to "192.168.1.255"))
        assertEquals(setOf("192.168.1.5"), LocalNetworkAddresses.selectLocalIpv4(listOf(broken)))
    }

    // ---------------------------------------------------------------- broadcast targets

    @Test
    fun `broadcast targets come only from interfaces that have one`() {
        assertEquals(
            listOf("192.168.1.255", "192.168.43.255"),
            LocalNetworkAddresses.selectBroadcasts(listOf(wlan, vpn, hotspot, loopback))
        )
    }

    @Test
    fun `point to point interfaces are never broadcast targets`() {
        // Broadcasting down a VPN tunnel or a cellular link reaches nobody, and on a metered
        // link it is not free.
        val targets = LocalNetworkAddresses.selectBroadcasts(listOf(vpn, cellular))
        assertTrue("point-to-point links should not be broadcast on: $targets", targets.isEmpty())
    }

    @Test
    fun `broadcast targets are deduplicated`() {
        val a = nic("wlan0", addresses = arrayOf("192.168.1.5" to "192.168.1.255"))
        val b = nic("wlan1", addresses = arrayOf("192.168.1.6" to "192.168.1.255"))
        assertEquals(listOf("192.168.1.255"), LocalNetworkAddresses.selectBroadcasts(listOf(a, b)))
    }

    @Test
    fun `the global broadcast address is not used`() {
        // 255.255.255.255 leaves via whichever interface the kernel picks, which on a
        // multi-NIC device is precisely the coin flip this module exists to remove.
        val targets = LocalNetworkAddresses.selectBroadcasts(listOf(wlan, hotspot))
        assertFalse("255.255.255.255" in targets)
    }

    @Test
    fun `interfaces that are down are not broadcast on`() {
        assertEquals(listOf("192.168.1.255"), LocalNetworkAddresses.selectBroadcasts(listOf(wlan, down)))
    }

    // ---------------------------------------------------------------- preferred address

    @Test
    fun `a plain single-nic phone picks its wifi address`() {
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(wlan, loopback)))
    }

    @Test
    fun `wifi beats a vpn tunnel regardless of enumeration order`() {
        // The v1 rule was "first non-loopback IPv4", and interface order is not something the
        // platform promises. Both orders must give the same answer.
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(vpn, wlan)))
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(wlan, vpn)))
    }

    @Test
    fun `wifi beats cellular`() {
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(cellular, wlan)))
    }

    @Test
    fun `a physical interface beats a virtual one on equal footing`() {
        val virtual = nic("wlan0:1", virtual = true, addresses = arrayOf("192.168.1.90" to "192.168.1.255"))
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(virtual, wlan)))
    }

    @Test
    fun `a private address beats a public one`() {
        val public = nic("eth1", addresses = arrayOf("203.0.113.7" to "203.0.113.255"))
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(public, wlan)))
    }

    @Test
    fun `the choice is stable across calls when nothing changes`() {
        // An advertised address that flips between announcements makes one device look like
        // several in the peer's list.
        val nics = listOf(hotspot, wlan)
        val first = LocalNetworkAddresses.preferredLocalIpv4(nics)
        repeat(20) { assertEquals(first, LocalNetworkAddresses.preferredLocalIpv4(nics)) }
    }

    @Test
    fun `interface name breaks a tie deterministically`() {
        val a = nic("wlan1", addresses = arrayOf("192.168.1.6" to "192.168.1.255"))
        val b = nic("wlan0", addresses = arrayOf("192.168.1.5" to "192.168.1.255"))
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(a, b)))
        assertEquals("192.168.1.5", LocalNetworkAddresses.preferredLocalIpv4(listOf(b, a)))
    }

    @Test
    fun `a vpn-only device still gets an answer`() {
        // Ranked, not filtered. Returning null here would disable discovery entirely rather
        // than merely making it unlikely to find anyone.
        assertEquals("10.8.0.2", LocalNetworkAddresses.preferredLocalIpv4(listOf(vpn, loopback)))
    }

    @Test
    fun `no usable interface yields null`() {
        assertNull(LocalNetworkAddresses.preferredLocalIpv4(listOf(loopback, down)))
        assertNull(LocalNetworkAddresses.preferredLocalIpv4(emptyList()))
    }

    // ---------------------------------------------------------------- private range check

    @Test
    fun `private ranges are recognised`() {
        for (ip in listOf("10.0.0.1", "10.255.255.255", "192.168.0.1", "172.16.0.1", "172.31.255.255", "169.254.1.1")) {
            assertTrue("$ip should be private", LocalNetworkAddresses.isPrivateIpv4(ip))
        }
    }

    @Test
    fun `public and near-miss ranges are not private`() {
        // 172.15 and 172.32 bracket the RFC 1918 block; an off-by-one here would classify a
        // public address as LAN.
        for (ip in listOf("8.8.8.8", "203.0.113.7", "172.15.0.1", "172.32.0.1", "192.169.0.1", "11.0.0.1")) {
            assertFalse("$ip should not be private", LocalNetworkAddresses.isPrivateIpv4(ip))
        }
    }

    @Test
    fun `malformed addresses are not private`() {
        for (ip in listOf("", "192.168.1", "192.168.1.1.1", "abc", "192.168.1.256", "-1.0.0.1", "192.168.x.1")) {
            assertFalse("'$ip' should not parse as private", LocalNetworkAddresses.isPrivateIpv4(ip))
        }
    }

    // ---------------------------------------------------------------- real enumeration

    @Test
    fun `enumerating the real interfaces does not throw`() {
        // Runs on the JVM, so the result varies by machine. The only claim worth making is
        // that it degrades to an empty list instead of propagating.
        val nics = LocalNetworkAddresses.enumerate()
        LocalNetworkAddresses.selectLocalIpv4(nics)
        LocalNetworkAddresses.selectBroadcasts(nics)
        LocalNetworkAddresses.preferredLocalIpv4(nics)
    }
}
