package com.chronie.homemoney.data.sync.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replay protection.
 *
 * These cases model what the retry logic actually does to the receiver: the same frame
 * arriving twice because an ack was lost, the same entity arriving inside a differently
 * numbered chunk after a resume, and a COMMIT replayed because its answer never made it
 * back. Each of those used to double-apply.
 */
class IdempotencyGuardTest {

    private val session = 0x1122334455667788L

    // ------------------------------------------------------------ frame level

    @Test
    fun `a repeated frame is accepted once`() {
        val guard = IdempotencyGuard()
        assertTrue("first delivery must be processed", guard.acceptFrame(session, 7))
        assertFalse("retransmission must be suppressed", guard.acceptFrame(session, 7))
        assertFalse(guard.acceptFrame(session, 7))
    }

    @Test
    fun `different sequence numbers stay independent`() {
        val guard = IdempotencyGuard()
        for (seq in 0 until 100) {
            assertTrue("seq $seq wrongly treated as a duplicate", guard.acceptFrame(session, seq))
        }
        for (seq in 0 until 100) {
            assertFalse("seq $seq should now be known", guard.acceptFrame(session, seq))
        }
    }

    @Test
    fun `the same sequence in a different session is not a duplicate`() {
        val guard = IdempotencyGuard()
        assertTrue(guard.acceptFrame(sessionId = 1L, seq = 5))
        assertTrue(guard.acceptFrame(sessionId = 2L, seq = 5))
    }

    @Test
    fun `a negative sequence is handled as an unsigned value`() {
        // seq is a raw 32-bit counter on the wire and wraps into negative Int territory
        // past 2^31. Treating it as signed here would alias two distinct frames.
        val guard = IdempotencyGuard()
        assertTrue(guard.acceptFrame(session, -1))
        assertFalse(guard.acceptFrame(session, -1))
        assertTrue(guard.acceptFrame(session, Int.MIN_VALUE))
    }

    @Test
    fun `probing does not consume the entry`() {
        val guard = IdempotencyGuard()
        assertFalse(guard.isDuplicateFrame(session, 3))
        assertTrue(guard.acceptFrame(session, 3))
        assertTrue(guard.isDuplicateFrame(session, 3))
    }

    // ----------------------------------------------------------- entity level

    @Test
    fun `an identical entity revision is applied once`() {
        val guard = IdempotencyGuard()
        assertTrue(guard.shouldApplyEntity("exp-1", 1000L, 0xABCD))
        guard.recordEntityApplied("exp-1", 1000L, 0xABCD)
        assertFalse(guard.shouldApplyEntity("exp-1", 1000L, 0xABCD))
    }

    @Test
    fun `a genuine later edit is not mistaken for a replay`() {
        val guard = IdempotencyGuard()
        guard.recordEntityApplied("exp-1", 1000L, 0xABCD)

        // Same record, newer timestamp: must pass through.
        assertTrue(guard.shouldApplyEntity("exp-1", 1001L, 0xABCD))
        // Same record and timestamp but different content: must pass through, because two
        // devices can legitimately write within the same millisecond.
        assertTrue(guard.shouldApplyEntity("exp-1", 1000L, 0x1234))
        // A different record entirely.
        assertTrue(guard.shouldApplyEntity("exp-2", 1000L, 0xABCD))
    }

    @Test
    fun `claim is atomic so only one caller applies`() {
        val guard = IdempotencyGuard()
        assertTrue(guard.claimEntity("exp-1", 1000L, 7))
        assertFalse(guard.claimEntity("exp-1", 1000L, 7))
    }

    @Test
    fun `concurrent claims elect exactly one winner`() {
        // The native thread pool dispatches connections from several threads, so two peers
        // pushing the same revision at once is a real scenario, not a theoretical one.
        val guard = IdempotencyGuard()
        val threads = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val winners = AtomicInteger(0)

        repeat(threads) {
            Thread {
                start.await()
                if (guard.claimEntity("exp-hot", 5000L, 0x55)) winners.incrementAndGet()
                done.countDown()
            }.start()
        }

        start.countDown()
        assertTrue("threads did not finish", done.await(10, TimeUnit.SECONDS))
        assertEquals("exactly one thread must win the claim", 1, winners.get())
    }

    // ----------------------------------------------------------- commit level

    @Test
    fun `a replayed commit returns the original answer`() {
        val guard = IdempotencyGuard()
        assertNull(guard.cachedCommit(session))

        val record = IdempotencyGuard.CommitRecord(
            inserted = 3,
            updated = 2,
            skipped = 1,
            applied = true
        )
        guard.recordCommit(session, record)

        val replayed = guard.cachedCommit(session)
        assertNotNull("a replayed COMMIT must be answerable from cache", replayed)
        assertEquals(record, replayed)
    }

    @Test
    fun `commits of different sessions do not collide`() {
        val guard = IdempotencyGuard()
        guard.recordCommit(1L, IdempotencyGuard.CommitRecord(1, 0, 0, true))
        guard.recordCommit(2L, IdempotencyGuard.CommitRecord(9, 0, 0, false))
        assertEquals(1, guard.cachedCommit(1L)?.inserted)
        assertEquals(9, guard.cachedCommit(2L)?.inserted)
    }

    // ------------------------------------------------------------- bookkeeping

    @Test
    fun `forgetting a session clears its frames but keeps entity history`() {
        val guard = IdempotencyGuard()
        guard.acceptFrame(session, 1)
        guard.acceptFrame(session, 2)
        guard.acceptFrame(otherSession, 1)
        guard.recordEntityApplied("exp-1", 1000L, 1)
        guard.recordCommit(session, IdempotencyGuard.CommitRecord(1, 1, 1, true))

        guard.forgetSession(session)

        assertFalse("frames of the closed session should be gone", guard.isDuplicateFrame(session, 1))
        assertTrue("another session must be untouched", guard.isDuplicateFrame(otherSession, 1))
        assertNull(guard.cachedCommit(session))
        // Entity history deliberately survives: a resume opens a *new* session and would
        // otherwise re-apply everything it already wrote.
        assertFalse(guard.shouldApplyEntity("exp-1", 1000L, 1))
    }

    @Test
    fun `memory stays bounded under a long running peer`() {
        val capacity = 64
        val guard = IdempotencyGuard(
            frameCapacity = capacity,
            entityCapacity = capacity,
            commitCapacity = 8
        )
        repeat(10_000) { i ->
            guard.acceptFrame(session, i)
            guard.recordEntityApplied("exp-$i", i.toLong(), i)
        }
        repeat(100) { i -> guard.recordCommit(i.toLong(), IdempotencyGuard.CommitRecord(0, 0, 0, true)) }

        val stats = guard.stats()
        assertTrue("frame set grew to ${stats.frames}", stats.frames <= capacity)
        assertTrue("entity set grew to ${stats.entities}", stats.entities <= capacity)
        assertTrue("commit cache grew to ${stats.commits}", stats.commits <= 8)

        // The most recent entries are the ones worth keeping: a duplicate that arrives
        // thousands of frames later is a different problem than a retransmission.
        assertTrue("the newest frame was evicted", guard.isDuplicateFrame(session, 9_999))
    }
}

private const val otherSession = 0x7766554433221100L
