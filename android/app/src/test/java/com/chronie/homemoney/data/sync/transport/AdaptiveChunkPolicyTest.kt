package com.chronie.homemoney.data.sync.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AdaptiveChunkPolicyTest {

    private fun policy() = AdaptiveChunkPolicy()

    /** Feeds a full sample window so the policy actually decides. */
    private fun AdaptiveChunkPolicy.acknowledgeRound(bytes: Int = chunkBytes, roundTripMs: Long) {
        repeat(AdaptiveChunkPolicy.DEFAULT_SAMPLE_SIZE) { onChunkAcknowledged(bytes, roundTripMs) }
    }

    // ---------------------------------------------------------------- defaults

    @Test
    fun `it starts at the documented defaults`() {
        val p = policy()
        assertEquals(64 * 1024, p.chunkBytes)
        assertEquals(4, p.windowSize)
        assertEquals(0, p.consecutiveTimeouts)
    }

    @Test
    fun `the configured bounds are respected at construction`() {
        assertEquals(16 * 1024, AdaptiveChunkPolicy(initialChunkBytes = 1).chunkBytes)
        assertEquals(256 * 1024, AdaptiveChunkPolicy(initialChunkBytes = Int.MAX_VALUE).chunkBytes)
        assertEquals(1, AdaptiveChunkPolicy(initialWindow = 0).windowSize)
        assertEquals(8, AdaptiveChunkPolicy(initialWindow = 999).windowSize)
    }

    @Test
    fun `nonsensical configuration is refused rather than silently clamped`() {
        assertTrue(runCatching { AdaptiveChunkPolicy(minChunkBytes = 0) }.isFailure)
        assertTrue(runCatching { AdaptiveChunkPolicy(minChunkBytes = 1000, maxChunkBytes = 500) }.isFailure)
        assertTrue(runCatching { AdaptiveChunkPolicy(minWindow = 0) }.isFailure)
        assertTrue(runCatching { AdaptiveChunkPolicy(sampleSize = 0) }.isFailure)
        assertTrue(runCatching { AdaptiveChunkPolicy(targetChunkDurationMs = 0) }.isFailure)
    }

    // ---------------------------------------------------------------- adapting up and down

    @Test
    fun `a fast link grows the chunk`() {
        val p = policy()
        p.acknowledgeRound(roundTripMs = 50)
        assertTrue("chunk should have grown from ${64 * 1024}, got ${p.chunkBytes}", p.chunkBytes > 64 * 1024)
    }

    @Test
    fun `a slow link shrinks the chunk`() {
        val p = policy()
        p.acknowledgeRound(roundTripMs = 4_000)
        assertTrue("chunk should have shrunk, got ${p.chunkBytes}", p.chunkBytes < 64 * 1024)
    }

    @Test
    fun `a link already hitting the latency target is left alone`() {
        // 3 x 64 KiB at exactly the 800ms target is by definition the right size. Moving it
        // here would mean the policy never settles.
        val p = policy()
        p.acknowledgeRound(bytes = 64 * 1024, roundTripMs = 800)
        assertEquals(64 * 1024, p.chunkBytes)
    }

    @Test
    fun `growth stops at the ceiling`() {
        val p = policy()
        repeat(10) { p.acknowledgeRound(roundTripMs = 1) }
        assertEquals(256 * 1024, p.chunkBytes)
    }

    @Test
    fun `shrinking stops at the floor`() {
        val p = policy()
        repeat(10) { p.acknowledgeRound(roundTripMs = 60_000) }
        assertEquals(16 * 1024, p.chunkBytes)
    }

    @Test
    fun `no single step more than doubles the chunk`() {
        // Without damping one lucky sample sends the size across the whole range, and the
        // next ordinary sample sends it back. That oscillation transfers less than a fixed
        // size would.
        val p = policy()
        var previous = p.chunkBytes
        repeat(6) {
            p.acknowledgeRound(roundTripMs = 1)
            assertTrue(
                "jumped from $previous to ${p.chunkBytes} in one step",
                p.chunkBytes <= previous * 2
            )
            previous = p.chunkBytes
        }
    }

    @Test
    fun `no single step more than halves the chunk`() {
        val p = policy()
        var previous = p.chunkBytes
        repeat(6) {
            p.acknowledgeRound(roundTripMs = 100_000)
            assertTrue(
                "dropped from $previous to ${p.chunkBytes} in one step",
                p.chunkBytes >= previous / 2
            )
            previous = p.chunkBytes
        }
    }

    @Test
    fun `an alternating link does not swing between the extremes`() {
        // A link that flips between fast and slow is the realistic bad case: microwave on,
        // microwave off. The size should hover, not slam into both rails.
        val p = policy()
        var sawFloor = false
        var sawCeiling = false
        repeat(40) { i ->
            p.onChunkAcknowledged(p.chunkBytes, if (i % 2 == 0) 20 else 2_500)
            if (p.chunkBytes == p.minChunkBytes) sawFloor = true
            if (p.chunkBytes == p.maxChunkBytes) sawCeiling = true
        }
        assertFalse("the size reached both extremes - it is oscillating", sawFloor && sawCeiling)
    }

    // ---------------------------------------------------------------- decision timing

    @Test
    fun `nothing changes before a full sample window is collected`() {
        val p = policy()
        p.onChunkAcknowledged(64 * 1024, 1)
        assertEquals(64 * 1024, p.chunkBytes)
        p.onChunkAcknowledged(64 * 1024, 1)
        assertEquals("two samples is still not a decision", 64 * 1024, p.chunkBytes)
        p.onChunkAcknowledged(64 * 1024, 1)
        assertTrue("the third sample should have triggered a decision", p.chunkBytes > 64 * 1024)
    }

    @Test
    fun `only the most recent samples matter`() {
        // The window slides. Old measurements from before a network change must age out, or
        // the policy keeps sizing for a link that is gone.
        val p = policy()
        p.acknowledgeRound(roundTripMs = 1)
        val fast = p.chunkBytes
        p.acknowledgeRound(bytes = fast, roundTripMs = 60_000)
        assertTrue("stale fast samples kept the chunk large", p.chunkBytes < fast)
    }

    @Test
    fun `an acknowledgement of nothing is ignored`() {
        val p = policy()
        repeat(5) { p.onChunkAcknowledged(0, 1) }
        repeat(5) { p.onChunkAcknowledged(-1, 1) }
        assertEquals(64 * 1024, p.chunkBytes)
    }

    @Test
    fun `a zero round trip does not divide by zero`() {
        // A fast LAN transfer can complete inside the clock granularity. Treating that as an
        // error would mean the fastest links are the ones that never adapt.
        val p = policy()
        p.acknowledgeRound(roundTripMs = 0)
        assertTrue(p.chunkBytes in p.minChunkBytes..p.maxChunkBytes)
        assertTrue(p.throughputBytesPerMs() > 0.0)
    }

    // ---------------------------------------------------------------- timeout behaviour

    @Test
    fun `a timeout halves the chunk and the window at once`() {
        val p = policy()
        p.onTimeout()
        assertEquals(32 * 1024, p.chunkBytes)
        assertEquals(2, p.windowSize)
        assertEquals(1, p.consecutiveTimeouts)
    }

    @Test
    fun `consecutive timeouts compound down to the floor`() {
        val p = policy()
        p.onTimeout()
        p.onTimeout()
        assertEquals(16 * 1024, p.chunkBytes)
        assertEquals(1, p.windowSize)

        p.onTimeout()
        assertEquals("already at the floor", 16 * 1024, p.chunkBytes)
        assertEquals(1, p.windowSize)
        assertEquals(3, p.consecutiveTimeouts)
        assertTrue(p.isAtFloor)
    }

    @Test
    fun `a timeout discards the samples that predate it`() {
        // Pre-congestion throughput would otherwise argue for a large chunk on the very next
        // decision, undoing the backoff immediately.
        val p = policy()
        p.onChunkAcknowledged(64 * 1024, 1)
        p.onChunkAcknowledged(64 * 1024, 1)
        p.onTimeout()
        val afterTimeout = p.chunkBytes

        p.onChunkAcknowledged(afterTimeout, 1)
        assertEquals("a stale sample completed a decision window", afterTimeout, p.chunkBytes)

        // Two more get it back to a full window, and only then may it decide.
        p.onChunkAcknowledged(afterTimeout, 1)
        p.onChunkAcknowledged(afterTimeout, 1)
        assertTrue("three fresh samples should have produced a decision", p.chunkBytes > afterTimeout)
    }

    @Test
    fun `a success resets the timeout counter`() {
        val p = policy()
        p.onTimeout()
        p.onTimeout()
        assertEquals(2, p.consecutiveTimeouts)
        p.onChunkAcknowledged(16 * 1024, 10)
        assertEquals(0, p.consecutiveTimeouts)
    }

    @Test
    fun `recovery after a timeout is gradual not immediate`() {
        val p = policy()
        p.onTimeout()
        val backedOff = p.chunkBytes
        p.onChunkAcknowledged(backedOff, 1)
        assertEquals("one success should not undo the backoff", backedOff, p.chunkBytes)
    }

    @Test
    fun `isAtFloor is false while there is still room to back off`() {
        val p = policy()
        assertFalse(p.isAtFloor)
        p.onTimeout()
        assertFalse(p.isAtFloor)
    }

    // ---------------------------------------------------------------- window

    @Test
    fun `the window grows only while inside the latency target`() {
        val p = policy()
        p.acknowledgeRound(roundTripMs = 10)
        assertEquals(5, p.windowSize)
    }

    @Test
    fun `the window does not grow on a link that is over the target`() {
        // Refilling the pipe right after the congestion that emptied it is how a struggling
        // link is turned into a failing one.
        val p = policy()
        p.acknowledgeRound(roundTripMs = 5_000)
        assertEquals(4, p.windowSize)
    }

    @Test
    fun `the window is capped`() {
        val p = policy()
        repeat(20) { p.acknowledgeRound(roundTripMs = 1) }
        assertEquals(8, p.windowSize)
    }

    @Test
    fun `the window never drops below one`() {
        // A window of zero would stall the transfer forever with no error to explain it.
        val p = policy()
        repeat(10) { p.onTimeout() }
        assertEquals(1, p.windowSize)
    }

    // ---------------------------------------------------------------- peer limit

    @Test
    fun `a peer limit is honoured directly`() {
        val p = policy()
        p.onPeerLimit(20 * 1024)
        assertEquals(20 * 1024, p.chunkBytes)
    }

    @Test
    fun `a peer limit never pushes below the floor`() {
        val p = policy()
        p.onPeerLimit(1)
        assertEquals(16 * 1024, p.chunkBytes)
    }

    @Test
    fun `a peer limit above the current size changes nothing`() {
        val p = policy()
        p.onPeerLimit(1024 * 1024)
        assertEquals(64 * 1024, p.chunkBytes)
    }

    @Test
    fun `a nonsense peer limit is ignored`() {
        val p = policy()
        p.onPeerLimit(0)
        p.onPeerLimit(-5)
        assertEquals(64 * 1024, p.chunkBytes)
    }

    // ---------------------------------------------------------------- chunk counting

    @Test
    fun `chunk count rounds up`() {
        val p = policy()
        assertEquals(0, p.chunkCountFor(0))
        assertEquals(1, p.chunkCountFor(1))
        assertEquals(1, p.chunkCountFor((64 * 1024).toLong()))
        assertEquals(2, p.chunkCountFor((64 * 1024 + 1).toLong()))
        assertEquals(3, p.chunkCountFor((64 * 1024 * 3).toLong()))
    }

    @Test
    fun `chunk count is not negative for a negative size`() {
        assertEquals(0, policy().chunkCountFor(-1))
    }

    @Test
    fun `chunk count survives a very large payload`() {
        assertTrue(policy().chunkCountFor(Long.MAX_VALUE) > 0)
    }

    // ---------------------------------------------------------------- reset

    @Test
    fun `reset returns to the defaults`() {
        val p = policy()
        repeat(5) { p.onTimeout() }
        p.reset()
        assertEquals(64 * 1024, p.chunkBytes)
        assertEquals(4, p.windowSize)
        assertEquals(0, p.consecutiveTimeouts)
    }

    @Test
    fun `reset clears the samples`() {
        val p = policy()
        p.onChunkAcknowledged(64 * 1024, 1)
        p.onChunkAcknowledged(64 * 1024, 1)
        p.reset()
        p.onChunkAcknowledged(64 * 1024, 1)
        assertEquals("a pre-reset sample completed a decision window", 64 * 1024, p.chunkBytes)
    }

    // ---------------------------------------------------------------- invariants

    @Test
    fun `the bounds hold under an arbitrary sequence of events`() {
        // Whatever the link does, the size handed to the transport must be usable. A value
        // outside the bounds becomes an oversized frame the peer rejects, or a zero-length
        // one that never completes.
        val random = Random(20260801)
        val p = AdaptiveChunkPolicy()
        repeat(20_000) {
            when (random.nextInt(4)) {
                0 -> p.onTimeout()
                1 -> p.onChunkAcknowledged(p.chunkBytes, random.nextLong(0, 30_000))
                2 -> p.onPeerLimit(random.nextInt(-10, 2_000_000))
                else -> p.onChunkAcknowledged(random.nextInt(-10, 1_000_000), random.nextLong(0, 5_000))
            }
            assertTrue("chunk out of bounds: ${p.chunkBytes}", p.chunkBytes in p.minChunkBytes..p.maxChunkBytes)
            assertTrue("window out of bounds: ${p.windowSize}", p.windowSize in p.minWindow..p.maxWindow)
        }
    }

    @Test
    fun `the chunk size stays aligned so it does not drift by single bytes`() {
        val random = Random(7)
        val p = AdaptiveChunkPolicy()
        repeat(2_000) {
            p.onChunkAcknowledged(p.chunkBytes, random.nextLong(1, 5_000))
            val aligned = p.chunkBytes % (4 * 1024) == 0 || p.chunkBytes == p.minChunkBytes
            assertTrue("unaligned chunk size ${p.chunkBytes}", aligned)
        }
    }
}
