package com.chronie.homemoney.data.sync.discovery

/**
 * A device seen on the LAN, with everything needed to connect to it.
 *
 * Note [syncPort] — v1's discovery result had no port, so the connect path used a compile-time
 * constant and any peer that listened elsewhere was found but unreachable.
 */
data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val address: String,
    val syncPort: Int,
    val capabilities: Int = 0,
    val protocolVersion: Int = DiscoveryWire.VERSION
) {
    val supportsFrameV2: Boolean
        get() = DiscoveryCapability.has(capabilities, DiscoveryCapability.FRAME_V2)
}

/**
 * The set of devices currently believed to be reachable.
 *
 * v1 kept a `ConcurrentHashMap` that was only ever added to. Two consequences, both of which
 * users hit routinely:
 *
 *  - **Ghosts.** A device that left the network stayed in the list until the process died.
 *    Tapping it produced a connect timeout with no explanation.
 *  - **Duplicates after a Wi-Fi switch.** The map was keyed by device id but never updated,
 *    so `containsKey` short-circuited and the *old* IP was kept. The one entry the user could
 *    see was the one guaranteed to be stale.
 *
 * Entries here expire, and an address change replaces the entry instead of being discarded.
 *
 * Time is passed in rather than read from the clock so expiry is testable without sleeping,
 * and so a single receive loop iteration uses one consistent `now`.
 *
 * Thread-safe: the receive loop writes while the UI reads.
 */
class DiscoveryRegistry(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxDevices: Int = DEFAULT_MAX_DEVICES
) {

    /** What observing a device did to the table. The caller decides what is worth emitting. */
    enum class Update {
        /** Not previously known (or previously expired). */
        NEW,

        /** Same device, same address — just a liveness refresh. */
        REFRESHED,

        /** Same device, different address, port or capabilities. The entry was replaced. */
        MOVED
    }

    private data class Entry(val device: DiscoveredDevice, val lastSeenMs: Long)

    private val entries = LinkedHashMap<String, Entry>()
    private val lock = Any()

    fun observe(device: DiscoveredDevice, nowMs: Long): Update = synchronized(lock) {
        expireLocked(nowMs)

        val previous = entries[device.deviceId]
        val update = when {
            previous == null -> Update.NEW
            previous.device == device -> Update.REFRESHED
            else -> Update.MOVED
        }

        entries[device.deviceId] = Entry(device, nowMs)
        enforceCapacityLocked()
        update
    }

    /** Live devices, most recently seen first. */
    fun snapshot(nowMs: Long): List<DiscoveredDevice> = synchronized(lock) {
        expireLocked(nowMs)
        entries.values.sortedByDescending { it.lastSeenMs }.map { it.device }
    }

    fun get(deviceId: String, nowMs: Long): DiscoveredDevice? = synchronized(lock) {
        expireLocked(nowMs)
        entries[deviceId]?.device
    }

    /** Drops expired entries and returns them, so the caller can tell the UI what vanished. */
    fun prune(nowMs: Long): List<DiscoveredDevice> = synchronized(lock) { expireLocked(nowMs) }

    fun size(nowMs: Long): Int = synchronized(lock) {
        expireLocked(nowMs)
        entries.size
    }

    fun remove(deviceId: String) = synchronized(lock) {
        entries.remove(deviceId)
        Unit
    }

    fun clear() = synchronized(lock) { entries.clear() }

    private fun expireLocked(nowMs: Long): List<DiscoveredDevice> {
        if (entries.isEmpty()) return emptyList()
        val expired = mutableListOf<DiscoveredDevice>()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            // Guard against a clock that jumped backwards: a negative age is not expiry.
            if (nowMs - entry.lastSeenMs >= ttlMs) {
                expired += entry.device
                iterator.remove()
            }
        }
        return expired
    }

    /**
     * A noisy or hostile LAN can announce unlimited device ids. Without a cap the table is an
     * unbounded allocation driven by whatever else is on the network, so the oldest entries go
     * first — the ones least likely to be the device the user is looking at.
     */
    private fun enforceCapacityLocked() {
        if (entries.size <= maxDevices) return
        entries.entries
            .sortedBy { it.value.lastSeenMs }
            .take(entries.size - maxDevices)
            .forEach { entries.remove(it.key) }
    }

    companion object {
        /**
         * Four missed announcements at the 8s re-announce interval. Long enough that a device
         * behind a brief Wi-Fi hiccup does not blink out of the list mid-tap, short enough that
         * a device that actually left is gone before the user tries it.
         */
        const val DEFAULT_TTL_MS = 30_000L
        const val DEFAULT_MAX_DEVICES = 64
    }
}
