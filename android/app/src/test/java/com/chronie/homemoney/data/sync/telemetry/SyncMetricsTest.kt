package com.chronie.homemoney.data.sync.telemetry

import com.chronie.homemoney.data.sync.discovery.IgnoreReason
import com.chronie.homemoney.data.sync.engine.WireEntityMapper
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.session.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The counters are the only evidence a support conversation has, so the tests care about
 * two things: that a number means what its name says, and that recording from the transport
 * threads cannot lose an increment or corrupt a map.
 */
class SyncMetricsTest {

    private val metrics = SyncMetrics(clock = { 1_700_000_000_000 })

    // ------------------------------------------------------------------ sessions

    @Test
    fun `fresh metrics report nothing rather than zeroes`() {
        val snapshot = metrics.snapshot()

        assertEquals(0, snapshot.sessionsOpened)
        assertEquals(0, snapshot.sessionsFinished)
        assertTrue(snapshot.finishesByState.isEmpty())
        assertTrue(snapshot.phasesEntered.isEmpty())
        assertTrue(snapshot.rejectionsByCode.isEmpty())
        assertTrue(snapshot.discoveryIgnored.isEmpty())
    }

    @Test
    fun `success rate is null until a session finishes`() {
        metrics.recordSessionOpened()
        metrics.recordSessionOpened()

        // Two sessions in flight is not "0% success" - it is "no answer yet". Reporting a
        // rate here would put a red 0% on the diagnostics screen during a normal sync.
        assertNull(metrics.snapshot().successRate)
    }

    @Test
    fun `success rate counts only COMPLETED finishes`() {
        metrics.recordSessionFinished(SyncState.COMPLETED, 100)
        metrics.recordSessionFinished(SyncState.COMPLETED, 100)
        metrics.recordSessionFinished(SyncState.FAILED, 100)
        metrics.recordSessionFinished(SyncState.CANCELLED, 100)

        val snapshot = metrics.snapshot()
        assertEquals(0.5, snapshot.successRate!!, 1e-9)
        assertEquals(2, snapshot.sessionsFailed)
    }

    @Test
    fun `cancelled sessions count as failed but are distinguishable`() {
        metrics.recordSessionFinished(SyncState.CANCELLED, 10)

        val snapshot = metrics.snapshot()
        // Both numbers matter: a user who backed out is not a bug, but it is not a success
        // either, and the breakdown is what tells the two apart.
        assertEquals(1, snapshot.sessionsFailed)
        assertEquals(1L, snapshot.finishesByState[SyncState.CANCELLED])
        assertNull(snapshot.finishesByState[SyncState.COMPLETED])
    }

    @Test
    fun `durations average and keep the worst case`() {
        metrics.recordSessionFinished(SyncState.COMPLETED, 100)
        metrics.recordSessionFinished(SyncState.COMPLETED, 300)

        val snapshot = metrics.snapshot()
        assertEquals(200, snapshot.averageSessionMs)
        assertEquals(300, snapshot.longestSessionMs)
    }

    @Test
    fun `a backwards clock cannot produce a negative duration`() {
        // NTP correction mid-session, or a session that survived a suspend/resume.
        metrics.recordSessionFinished(SyncState.COMPLETED, -5_000)

        val snapshot = metrics.snapshot()
        assertEquals(0, snapshot.averageSessionMs)
        assertEquals(0, snapshot.longestSessionMs)
    }

    @Test
    fun `a later shorter session does not lower the maximum`() {
        metrics.recordSessionFinished(SyncState.COMPLETED, 9_000)
        metrics.recordSessionFinished(SyncState.COMPLETED, 10)

        assertEquals(9_000, metrics.snapshot().longestSessionMs)
    }

    @Test
    fun `phases are counted every time they are entered`() {
        metrics.recordPhase(SyncState.HANDSHAKING)
        metrics.recordPhase(SyncState.TRANSFERRING)
        // A reconnect re-enters TRANSFERRING; that repetition is the signal we want.
        metrics.recordPhase(SyncState.RECONNECTING)
        metrics.recordPhase(SyncState.TRANSFERRING)

        val phases = metrics.snapshot().phasesEntered
        assertEquals(2L, phases[SyncState.TRANSFERRING])
        assertEquals(1L, phases[SyncState.RECONNECTING])
    }

    // ------------------------------------------------------------------ rejections

    @Test
    fun `rejections are split by opcode and by code`() {
        metrics.recordRejection(SyncOpcode.HELLO, SyncErrorCode.PROTOCOL_MISMATCH)
        metrics.recordRejection(SyncOpcode.CHUNK, SyncErrorCode.PROTOCOL_MISMATCH)

        val snapshot = metrics.snapshot()
        assertEquals(1L, snapshot.rejectionsByOpcode[SyncOpcode.HELLO])
        assertEquals(1L, snapshot.rejectionsByOpcode[SyncOpcode.CHUNK])
        assertEquals(2L, snapshot.rejectionsByCode[SyncErrorCode.PROTOCOL_MISMATCH])
    }

    @Test
    fun `retryable rejections are counted apart from real failures`() {
        val retryable = SyncErrorCode.BUSY
        val fatal = SyncErrorCode.AUTH_REJECTED

        metrics.recordRejection(SyncOpcode.CHUNK, retryable)
        metrics.recordRejection(SyncOpcode.CHUNK, retryable)
        metrics.recordRejection(SyncOpcode.HELLO, fatal)

        // Three rejections, but only one of them is a fault worth chasing.
        assertEquals(2, metrics.snapshot().retryableRejections)
    }

    // ------------------------------------------------------------------ merge

    @Test
    fun `applied totals accumulate across commits`() {
        metrics.recordApplied(received = 10, inserted = 6, updated = 2, skipped = 1, rejected = 1, conflicts = 3)
        metrics.recordApplied(received = 5, inserted = 5, updated = 0, skipped = 0, rejected = 0, conflicts = 0)

        val snapshot = metrics.snapshot()
        assertEquals(2, snapshot.commitsApplied)
        assertEquals(15, snapshot.entitiesReceived)
        assertEquals(11, snapshot.entitiesInserted)
        assertEquals(2, snapshot.entitiesUpdated)
        assertEquals(1, snapshot.entitiesSkipped)
        assertEquals(1, snapshot.entitiesRejected)
        assertEquals(3, snapshot.conflictsResolved)
    }

    @Test
    fun `reject reasons are summed by reason, not by call`() {
        metrics.recordApplied(
            received = 4, inserted = 0, updated = 0, skipped = 0, rejected = 4, conflicts = 0,
            reasons = mapOf(
                WireEntityMapper.RejectReason.HASH_MISMATCH to 3,
                WireEntityMapper.RejectReason.MISSING_ID to 1
            )
        )
        metrics.recordApplied(
            received = 2, inserted = 0, updated = 0, skipped = 0, rejected = 2, conflicts = 0,
            reasons = mapOf(WireEntityMapper.RejectReason.HASH_MISMATCH to 2)
        )

        val reasons = metrics.snapshot().rejectReasons
        // "5 HASH_MISMATCH" points at a serialisation difference between the two builds;
        // "6 rejected" points at nothing.
        assertEquals(5L, reasons[WireEntityMapper.RejectReason.HASH_MISMATCH])
        assertEquals(1L, reasons[WireEntityMapper.RejectReason.MISSING_ID])
        assertNull(reasons[WireEntityMapper.RejectReason.INVALID_TIMESTAMP])
    }

    // ------------------------------------------------------------------ discovery

    @Test
    fun `device sightings are classified new, moved or refreshed`() {
        metrics.recordDeviceSeen(isNew = true, moved = false)
        metrics.recordDeviceSeen(isNew = false, moved = true)
        metrics.recordDeviceSeen(isNew = false, moved = false)
        metrics.recordDeviceSeen(isNew = false, moved = false)

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.devicesFirstSeen)
        assertEquals(1, snapshot.devicesMoved)
        assertEquals(2, snapshot.devicesRefreshed)
    }

    @Test
    fun `a new device is never double counted as moved`() {
        // The registry reports NEW for a first sighting; whatever the caller passes for
        // `moved`, a first sighting must land in exactly one bucket.
        metrics.recordDeviceSeen(isNew = true, moved = true)

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.devicesFirstSeen)
        assertEquals(0, snapshot.devicesMoved)
    }

    @Test
    fun `discovery replies are counted`() {
        metrics.recordDiscoveryReply()
        metrics.recordDiscoveryReply()

        val snapshot = metrics.snapshot()
        assertEquals(2, snapshot.discoveryRepliesV2)
    }

    @Test
    fun `ignored packets are counted per reason`() {
        repeat(3) { metrics.recordDiscoveryIgnored(IgnoreReason.SELF_ADDRESS) }
        metrics.recordDiscoveryIgnored(IgnoreReason.MALFORMED)

        val ignored = metrics.snapshot().discoveryIgnored
        assertEquals(3L, ignored[IgnoreReason.SELF_ADDRESS])
        assertEquals(1L, ignored[IgnoreReason.MALFORMED])
    }

    // ------------------------------------------------------------------ errors

    @Test
    fun `errors are counted per stage`() {
        metrics.recordError("bind")
        metrics.recordError("bind")
        metrics.recordError("query")

        val errors = metrics.snapshot().errorsByStage
        assertEquals(2L, errors["bind"])
        assertEquals(1L, errors["query"])
    }

    @Test
    fun `the stage map cannot be grown without bound`() {
        // Guards against a future stage tag built inside a loop, e.g. "retry-$attempt".
        repeat(200) { metrics.recordError("stage-$it") }

        val errors = metrics.snapshot().errorsByStage
        assertTrue("stage map grew to ${errors.size}", errors.size <= 33)
        assertTrue("overflow was not accounted for", errors.getValue("other") > 0)
        assertEquals(200L, errors.values.sum())
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    fun `reset clears every counter`() {
        metrics.recordSessionOpened()
        metrics.recordSessionFinished(SyncState.FAILED, 500)
        metrics.recordPhase(SyncState.TRANSFERRING)
        metrics.recordRejection(SyncOpcode.HELLO, SyncErrorCode.PROTOCOL_MISMATCH)
        metrics.recordApplied(
            1, 1, 0, 0, 0, 0,
            mapOf(WireEntityMapper.RejectReason.MISSING_ID to 1)
        )
        metrics.recordDiscoveryIgnored(IgnoreReason.MALFORMED)
        metrics.recordDeviceSeen(isNew = true, moved = false)
        metrics.recordDiscoveryReply()
        metrics.recordError("bind")

        metrics.reset()

        val after = metrics.snapshot()
        val fresh = SyncMetrics(clock = { 1_700_000_000_000 }).snapshot()
        assertEquals(fresh, after)
    }

    @Test
    fun `a snapshot is a detached copy`() {
        metrics.recordSessionOpened()
        val before = metrics.snapshot()

        metrics.recordSessionOpened()

        // A snapshot handed to the bug-report exporter must not keep changing underneath it.
        assertEquals(1, before.sessionsOpened)
        assertEquals(2, metrics.snapshot().sessionsOpened)
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    fun `concurrent recording loses nothing`() {
        val threads = 8
        val perThread = 2_000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) {
            pool.execute {
                start.await()
                repeat(perThread) {
                    // Every counter shape at once: plain, enum-keyed and string-keyed.
                    metrics.recordSessionOpened()
                    metrics.recordPhase(SyncState.TRANSFERRING)
                    metrics.recordRejection(SyncOpcode.CHUNK, SyncErrorCode.PROTOCOL_MISMATCH)
                    metrics.recordError("bind")
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue("workers did not finish", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        val expected = (threads * perThread).toLong()
        val snapshot = metrics.snapshot()
        assertEquals(expected, snapshot.sessionsOpened)
        assertEquals(expected, snapshot.phasesEntered[SyncState.TRANSFERRING])
        assertEquals(expected, snapshot.rejectionsByCode[SyncErrorCode.PROTOCOL_MISMATCH])
        assertEquals(expected, snapshot.errorsByStage["bind"])
    }

    @Test
    fun `snapshots taken during recording stay readable`() {
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val done = CountDownLatch(4)

        repeat(3) {
            pool.execute {
                start.await()
                repeat(5_000) { metrics.recordError("stage-${it % 40}") }
                done.countDown()
            }
        }
        pool.execute {
            start.await()
            // Reading a ConcurrentHashMap mid-write must not throw, and the EnumMaps must
            // not rehash - that is why they are built once and never written to.
            repeat(2_000) { metrics.snapshot().format() }
            done.countDown()
        }

        start.countDown()
        assertTrue("workers did not finish", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(15_000L, metrics.snapshot().errorsByStage.values.sum())
    }

    // ------------------------------------------------------------------ formatting

    @Test
    fun `the report omits sections that never happened`() {
        metrics.recordSessionOpened()

        val text = metrics.snapshot().format()
        assertTrue(text, text.contains("sync sessions: opened=1"))
        // Nothing was merged and nothing was rejected, so those headings would be noise.
        assertTrue(text, !text.contains("merge:"))
        assertTrue(text, !text.contains("rejections:"))
        assertTrue(text, !text.contains("success="))
    }

    @Test
    fun `the report names the reason a record was dropped`() {
        metrics.recordSessionFinished(SyncState.COMPLETED, 120)
        metrics.recordApplied(
            received = 3, inserted = 2, updated = 0, skipped = 0, rejected = 1, conflicts = 0,
            reasons = mapOf(WireEntityMapper.RejectReason.HASH_MISMATCH to 1)
        )

        val text = metrics.snapshot().format()
        assertTrue(text, text.contains("success=100.0%"))
        assertTrue(text, text.contains("merge: commits=1 received=3"))
        assertTrue(text, text.contains("why HASH_MISMATCH=1"))
        assertTrue(text, text.contains("end COMPLETED=1"))
    }

    @Test
    fun `the report is ordered worst first`() {
        repeat(5) { metrics.recordDiscoveryIgnored(IgnoreReason.MALFORMED) }
        metrics.recordDiscoveryIgnored(IgnoreReason.STALE_NONCE)

        val lines = metrics.snapshot().format().lines().filter { it.startsWith("  dropped") }
        // Whoever is reading this is looking for the dominant failure, so it goes first.
        assertEquals(listOf("  dropped MALFORMED=5", "  dropped STALE_NONCE=1"), lines)
    }

    @Test
    fun `the report never ends in blank lines`() {
        val text = metrics.snapshot().format()
        assertEquals(text.trimEnd(), text)
        assertTrue(text.isNotEmpty())
    }
}
