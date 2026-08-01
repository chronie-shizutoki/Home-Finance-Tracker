package com.chronie.homemoney.data.sync.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiscoveryRegistryTest {

    private fun device(
        id: String = "device-a",
        name: String = "Pixel 8",
        address: String = "192.168.1.5",
        port: Int = 50051,
        capabilities: Int = DiscoveryCapability.DEFAULT
    ) = DiscoveredDevice(
        deviceId = id,
        deviceName = name,
        deviceType = "ANDROID",
        address = address,
        syncPort = port,
        capabilities = capabilities
    )

    // ---------------------------------------------------------------- basics

    @Test
    fun `a device seen for the first time is new`() {
        val registry = DiscoveryRegistry()
        assertEquals(DiscoveryRegistry.Update.NEW, registry.observe(device(), nowMs = 0))
    }

    @Test
    fun `seeing the same device again is a refresh not a new device`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(), nowMs = 0)
        assertEquals(DiscoveryRegistry.Update.REFRESHED, registry.observe(device(), nowMs = 1_000))
        assertEquals(1, registry.size(nowMs = 1_000))
    }

    @Test
    fun `a device that changed address is reported as moved and the entry is replaced`() {
        // v1 short-circuited on containsKey, so the stale address was the one kept — the
        // single worst outcome, because the user sees exactly one entry and it is the dead one.
        val registry = DiscoveryRegistry()
        registry.observe(device(address = "192.168.1.5"), nowMs = 0)

        val update = registry.observe(device(address = "10.0.0.9"), nowMs = 1_000)

        assertEquals(DiscoveryRegistry.Update.MOVED, update)
        assertEquals(1, registry.size(nowMs = 1_000))
        assertEquals("10.0.0.9", registry.get("device-a", nowMs = 1_000)?.address)
    }

    @Test
    fun `a device that changed port is reported as moved`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(port = 50051), nowMs = 0)
        assertEquals(DiscoveryRegistry.Update.MOVED, registry.observe(device(port = 50052), nowMs = 1))
        assertEquals(50052, registry.get("device-a", nowMs = 1)?.syncPort)
    }

    @Test
    fun `a device that changed name is reported as moved`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(name = "Old"), nowMs = 0)
        assertEquals(DiscoveryRegistry.Update.MOVED, registry.observe(device(name = "New"), nowMs = 1))
        assertEquals("New", registry.get("device-a", nowMs = 1)?.deviceName)
    }

    @Test
    fun `different devices coexist`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(id = "a"), nowMs = 0)
        registry.observe(device(id = "b"), nowMs = 0)
        assertEquals(2, registry.size(nowMs = 0))
    }

    @Test
    fun `two devices sharing an address are still two devices`() {
        // Happens behind a NAT, and after a device id is regenerated. Keying by address
        // instead of id would silently merge them.
        val registry = DiscoveryRegistry()
        registry.observe(device(id = "a", address = "192.168.1.5"), nowMs = 0)
        registry.observe(device(id = "b", address = "192.168.1.5"), nowMs = 0)
        assertEquals(2, registry.size(nowMs = 0))
    }

    // ---------------------------------------------------------------- expiry

    @Test
    fun `a device disappears once its ttl elapses`() {
        val registry = DiscoveryRegistry(ttlMs = 10_000)
        registry.observe(device(), nowMs = 0)

        assertEquals(1, registry.size(nowMs = 9_999))
        assertEquals(0, registry.size(nowMs = 10_000))
        assertNull(registry.get("device-a", nowMs = 10_000))
    }

    @Test
    fun `being seen again extends the ttl`() {
        val registry = DiscoveryRegistry(ttlMs = 10_000)
        registry.observe(device(), nowMs = 0)
        registry.observe(device(), nowMs = 9_000)
        assertEquals("the refresh should have restarted the clock", 1, registry.size(nowMs = 18_000))
        assertEquals(0, registry.size(nowMs = 19_000))
    }

    @Test
    fun `a device seen again after expiring is new again`() {
        val registry = DiscoveryRegistry(ttlMs = 10_000)
        registry.observe(device(), nowMs = 0)
        assertEquals(DiscoveryRegistry.Update.NEW, registry.observe(device(), nowMs = 20_000))
    }

    @Test
    fun `prune reports what it dropped`() {
        val registry = DiscoveryRegistry(ttlMs = 10_000)
        registry.observe(device(id = "a"), nowMs = 0)
        registry.observe(device(id = "b"), nowMs = 5_000)

        val dropped = registry.prune(nowMs = 12_000)

        assertEquals(listOf("a"), dropped.map { it.deviceId })
        assertEquals(1, registry.size(nowMs = 12_000))
    }

    @Test
    fun `prune on an empty registry is harmless`() {
        assertTrue(DiscoveryRegistry().prune(nowMs = 1_000).isEmpty())
    }

    @Test
    fun `a clock that jumps backwards does not evict everything`() {
        // NTP correction, or the user changing the system time mid-search. The list going
        // blank at that moment would look like every device left the network at once.
        val registry = DiscoveryRegistry(ttlMs = 10_000)
        registry.observe(device(), nowMs = 1_000_000)
        assertEquals(1, registry.size(nowMs = 500_000))
    }

    @Test
    fun `an expired entry does not block a moved entry from registering`() {
        val registry = DiscoveryRegistry(ttlMs = 10_000)
        registry.observe(device(address = "192.168.1.5"), nowMs = 0)
        registry.observe(device(address = "10.0.0.9"), nowMs = 30_000)
        assertEquals("10.0.0.9", registry.get("device-a", nowMs = 30_000)?.address)
    }

    // ---------------------------------------------------------------- capacity

    @Test
    fun `the table is capped and drops the least recently seen first`() {
        // A noisy or hostile LAN can announce unlimited ids. Without a cap the table is an
        // allocation controlled by whoever else is on the network.
        val registry = DiscoveryRegistry(ttlMs = 1_000_000, maxDevices = 3)
        registry.observe(device(id = "oldest"), nowMs = 0)
        registry.observe(device(id = "b"), nowMs = 100)
        registry.observe(device(id = "c"), nowMs = 200)
        registry.observe(device(id = "d"), nowMs = 300)

        val ids = registry.snapshot(nowMs = 300).map { it.deviceId }
        assertEquals(3, ids.size)
        assertTrue("the oldest entry should have been evicted", "oldest" !in ids)
        assertTrue(ids.containsAll(listOf("b", "c", "d")))
    }

    @Test
    fun `refreshing an entry protects it from capacity eviction`() {
        val registry = DiscoveryRegistry(ttlMs = 1_000_000, maxDevices = 2)
        registry.observe(device(id = "a"), nowMs = 0)
        registry.observe(device(id = "b"), nowMs = 100)
        registry.observe(device(id = "a"), nowMs = 200)   // a is now the newer of the two
        registry.observe(device(id = "c"), nowMs = 300)

        val ids = registry.snapshot(nowMs = 300).map { it.deviceId }
        assertTrue("a was refreshed and should have survived", "a" in ids)
        assertTrue("b was the stalest and should have gone", "b" !in ids)
    }

    // ---------------------------------------------------------------- snapshot / clear

    @Test
    fun `the snapshot is ordered most recently seen first`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(id = "a"), nowMs = 0)
        registry.observe(device(id = "b"), nowMs = 100)
        registry.observe(device(id = "c"), nowMs = 50)
        assertEquals(listOf("b", "c", "a"), registry.snapshot(nowMs = 100).map { it.deviceId })
    }

    @Test
    fun `the snapshot excludes expired devices`() {
        val registry = DiscoveryRegistry(ttlMs = 10_000)
        registry.observe(device(id = "gone"), nowMs = 0)
        registry.observe(device(id = "here"), nowMs = 9_000)
        assertEquals(listOf("here"), registry.snapshot(nowMs = 15_000).map { it.deviceId })
    }

    @Test
    fun `clear empties the table`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(), nowMs = 0)
        registry.clear()
        assertEquals(0, registry.size(nowMs = 0))
    }

    @Test
    fun `capabilities are carried through`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(capabilities = DiscoveryCapability.FRAME_V2), nowMs = 0)
        val stored = registry.get("device-a", nowMs = 0)
        assertNotNull(stored)
        assertTrue(stored!!.supportsFrameV2)
    }

    @Test
    fun `a v1 peer is not reported as speaking the v2 frame protocol`() {
        val registry = DiscoveryRegistry()
        registry.observe(device(capabilities = 0), nowMs = 0)
        assertTrue(registry.get("device-a", nowMs = 0)?.supportsFrameV2 == false)
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    fun `concurrent observers do not corrupt the table`() {
        // The receive loop writes while the UI reads. A plain HashMap here would surface as
        // a rare ConcurrentModificationException during a normal search.
        //
        // The cap is deliberately set above the total inserted: capacity eviction is covered
        // elsewhere, and letting it fire here would test the cap rather than thread safety.
        val threads = 8
        val perThread = 200
        val registry = DiscoveryRegistry(ttlMs = 1_000_000, maxDevices = threads * perThread)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = mutableListOf<Throwable>()

        repeat(threads) { t ->
            Thread {
                try {
                    start.await()
                    repeat(perThread) { i ->
                        registry.observe(device(id = "d-$t-$i"), nowMs = i.toLong())
                        registry.snapshot(nowMs = i.toLong())
                    }
                } catch (e: Throwable) {
                    synchronized(failures) { failures += e }
                } finally {
                    done.countDown()
                }
            }.start()
        }

        start.countDown()
        assertTrue("threads did not finish", done.await(30, TimeUnit.SECONDS))
        assertTrue("failures: $failures", failures.isEmpty())
        assertEquals(threads * perThread, registry.size(nowMs = perThread.toLong()))
    }
}
