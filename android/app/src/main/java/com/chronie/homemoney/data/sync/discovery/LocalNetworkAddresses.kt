package com.chronie.homemoney.data.sync.discovery

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Which of this machine's addresses matter for LAN discovery.
 *
 * v1 answered this with `getLocalIpAddress()`: walk the interfaces, return the first
 * non-loopback IPv4. On a phone that is frequently the wrong answer — a VPN `tun0`, a
 * tethering `ap0` and `wlan0` can all be up at once, and the enumeration order is not
 * something the platform promises. Two failures followed from it:
 *
 *  - **Self-discovery.** The reception loop skipped packets whose source equaled "the" local
 *    IP. Pick the wrong NIC and your own broadcast no longer matches, so the device lists
 *    itself as a peer.
 *  - **Broadcasting into a tunnel.** Sending discovery down a point-to-point VPN reaches
 *    nobody and, on a metered link, is not free.
 *
 * The fix is two-part: compare against **every** local address rather than one, and when a
 * single address must be named, choose deliberately instead of taking whatever came first.
 *
 * The selection logic is pure and takes a [Nic] list, so it can be tested against the messy
 * topologies that cause the bug. [enumerate] is the only part that touches the JDK.
 */
object LocalNetworkAddresses {

    /** A network interface reduced to the facts that drive selection. */
    data class Nic(
        val name: String,
        val isUp: Boolean,
        val isLoopback: Boolean,
        val isVirtual: Boolean,
        val isPointToPoint: Boolean,
        /** IPv4 address paired with its broadcast address, if the platform reports one. */
        val addresses: List<Address>
    ) {
        data class Address(val ip: String, val broadcast: String?)
    }

    /**
     * Every IPv4 address this machine holds, including ones on interfaces we would never
     * broadcast on.
     *
     * Deliberately permissive: this set is used to recognize our own traffic, and a missed
     * address means a self-discovery loop. An extra address costs nothing.
     */
    fun selectLocalIpv4(nics: List<Nic>): Set<String> =
        nics.asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.addresses.asSequence() }
            .map { it.ip }
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * Where to send a discovery query.
     *
     * Only interface-scoped broadcast addresses are used. `255.255.255.255` is left out on
     * purpose: Android routes it out one interface of the kernel's choosing, which on a
     * multi-NIC device is the coin flip this function exists to avoid, and some OEM builds
     * drop it outright.
     */
    fun selectBroadcasts(nics: List<Nic>): List<String> =
        nics.asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
            .flatMap { it.addresses.asSequence() }
            .mapNotNull { it.broadcast }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    /**
     * The address to advertise as "me".
     *
     * Ranked, not filtered, so a device on an unusual topology still gets an answer rather
     * than null:
     *
     *  1. has a broadcast address — it is on a real shared segment;
     *  2. is not point-to-point — VPN tunnels reach nobody on the LAN;
     *  3. is not virtual — prefer physical over an alias;
     *  4. sits in a private range — a public IPv4 on a phone means carrier NAT, not LAN;
     *  5. interface name, purely so the result is deterministic across calls.
     *
     * Determinism in step 5 matters more than it looks: an advertised address that changes
     * between announcements makes one device look like several.
     */
    fun preferredLocalIpv4(nics: List<Nic>): String? =
        nics.asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.addresses.asSequence().map { nic to it } }
            .filter { (_, address) -> address.ip.isNotBlank() }
            .sortedWith(
                compareByDescending<Pair<Nic, Nic.Address>> { (_, a) -> a.broadcast != null }
                    .thenByDescending { (nic, _) -> !nic.isPointToPoint }
                    .thenByDescending { (nic, _) -> !nic.isVirtual }
                    .thenByDescending { (_, a) -> isPrivateIpv4(a.ip) }
                    .thenBy { (nic, _) -> nic.name }
            )
            .firstOrNull()
            ?.second
            ?.ip

    /** RFC 1918 plus the 169.254/16 link-local block. */
    fun isPrivateIpv4(ip: String): Boolean {
        val octets = ip.split('.')
        if (octets.size != 4) return false
        val parsed = octets.map { it.toIntOrNull() ?: return false }
        if (parsed.any { it !in 0..255 }) return false
        val (a, b) = parsed
        return when (a) {
            10 -> true
            192 if b == 168 -> true
            172 if b in 16..31 -> true
            169 if b == 254 -> true
            else -> false
        }
    }

    /** Reads the live interface list. The only part of this file that can throw. */
    fun enumerate(): List<Nic> = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.map { nic ->
                Nic(
                    name = nic.name ?: "",
                    isUp = runCatching { nic.isUp }.getOrDefault(false),
                    isLoopback = runCatching { nic.isLoopback }.getOrDefault(false),
                    isVirtual = runCatching { nic.isVirtual }.getOrDefault(false),
                    isPointToPoint = runCatching { nic.isPointToPoint }.getOrDefault(false),
                    addresses = runCatching {
                        nic.interfaceAddresses
                            .filter { it.address is Inet4Address }
                            .map {
                                Nic.Address(
                                    ip = it.address.hostAddress.orEmpty(),
                                    broadcast = it.broadcast?.hostAddress
                                )
                            }
                    }.getOrDefault(emptyList())
                )
            }
            ?.toList()
            .orEmpty()
    } catch (_: Exception) {
        // A phone flipping between networks can throw mid-enumeration. An empty list makes
        // discovery a no-op for this round rather than tearing down the whole search.
        emptyList()
    }

    fun broadcastAddresses(nics: List<Nic> = enumerate()): List<InetAddress> =
        selectBroadcasts(nics).mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
}
