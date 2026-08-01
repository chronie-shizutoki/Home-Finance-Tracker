package com.chronie.homemoney.data.sync.telemetry

import com.chronie.homemoney.data.sync.discovery.DiscoveredDevice
import com.chronie.homemoney.data.sync.discovery.DiscoveryRegistry
import com.chronie.homemoney.data.sync.discovery.IgnoreReason
import com.chronie.homemoney.data.sync.engine.EntityApplier
import com.chronie.homemoney.data.sync.engine.WireEntityMapper
import com.chronie.homemoney.data.sync.generated.ConflictSummary
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.session.SyncSession
import com.chronie.homemoney.data.sync.session.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * The observers are the bridge between "something happened" and "someone can tell what".
 *
 * Two properties are worth defending. First, every line must be parseable: a peer controls
 * some of the values in them, so a device name with a space or a quote in it must not be
 * able to split one log line into two fields or forge a `trace=` of its own. Second, the
 * noisy paths - self-echo, retryable rejections, refreshed devices - must stay countable
 * without being loggable, or the warnings that matter get drowned.
 */
class SyncObservabilityTest {

    /** Records instead of writing, so assertions can be made on what would reach logcat. */
    private class RecordingSink : SyncLogSink {
        val lines: MutableList<Pair<SyncLogLevel, String>> =
            Collections.synchronizedList(mutableListOf())

        override fun emit(level: SyncLogLevel, line: String) {
            lines += level to line
        }

        fun events(): List<String> = lines.map { it.second.substringBefore(' ') }

        fun first(event: String): String =
            lines.map { it.second }.firstOrNull { it.startsWith("$event ") || it == event }
                ?: error("no line for '$event' in ${lines.map { it.second }}")

        fun levelOf(event: String): SyncLogLevel =
            lines.first { it.second.startsWith("$event ") }.first

        fun count(event: String): Int = lines.count { it.second.startsWith("$event ") }
    }

    private val metrics = SyncMetrics(clock = { NOW })
    private val sink = RecordingSink()
    private val observer = MetricsResponderObserver(metrics, sink, clock = { NOW })
    private val discovery = MetricsDiscoveryTelemetry(metrics, sink)

    private fun session(
        traceId: String = "abc123",
        peerAddress: String = "192.168.1.42:50051",
        openedAtMs: Long = NOW - 1_500
    ) = SyncSession(
        sessionId = 0x00FFL,
        peerAddress = peerAddress,
        traceId = traceId,
        openedAtMs = openedAtMs
    )

    private fun report(
        received: Int = 0,
        inserted: Int = 0,
        updated: Int = 0,
        skipped: Int = 0,
        rejected: Int = 0,
        conflicts: List<ConflictSummary> = emptyList(),
        rejections: Map<WireEntityMapper.RejectReason, Int> = emptyMap()
    ) = EntityApplier.ApplyReport(
        received, inserted, updated, skipped, rejected, conflicts, rejections
    )

    private fun conflict(id: String, reason: String = "REMOTE_NEWER", keptLocal: Boolean = false) =
        ConflictSummary.newBuilder()
            .setEntityId(id)
            .setReason(reason)
            .setKeptLocal(keptLocal)
            .build()

    // ------------------------------------------------------------------ session lines

    @Test
    fun `an opened session is counted and announced with its trace id`() {
        observer.onSessionOpened(session(traceId = "t-9"))

        assertEquals(1, metrics.snapshot().sessionsOpened)
        val line = sink.first("sync.session.open")
        assertTrue(line, line.contains("trace=t-9"))
        assertTrue(line, line.contains("peer=192.168.1.42:50051"))
        assertEquals(SyncLogLevel.INFO, sink.levelOf("sync.session.open"))
    }

    @Test
    fun `a session logged before HELLO says unknown rather than empty`() {
        // The open line is written the moment a connection is accepted, which is before the
        // peer has identified itself. `dev=""` reads like a bug; `dev=?` reads like a fact.
        observer.onSessionOpened(session())

        val line = sink.first("sync.session.open")
        assertTrue(line, line.contains("dev=?"))
        assertTrue(line, line.contains("name=?"))
    }

    @Test
    fun `a negotiated session reports who it is talking to`() {
        val session = session().apply {
            recordPeerIdentity("dev-b", "Pixel 7", "ANDROID", capabilities = 0x03, negotiatedVersion = 2)
        }

        observer.onSessionOpened(session)

        val line = sink.first("sync.session.open")
        assertTrue(line, line.contains("dev=dev-b"))
        assertTrue(line, line.contains("""name="Pixel 7""""))
        assertTrue(line, line.contains("ver=2"))
        assertTrue(line, line.contains("caps=0x03"))
    }

    @Test
    fun `a finished session records its state and duration`() {
        observer.onSessionFinished(session(openedAtMs = NOW - 2_500), SyncState.COMPLETED)

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.sessionsFinished)
        assertEquals(2_500, snapshot.longestSessionMs)
        assertTrue(sink.first("sync.session.end").contains("ms=2500"))
        assertEquals(SyncLogLevel.INFO, sink.levelOf("sync.session.end"))
    }

    @Test
    fun `a failed session is a warning, a completed one is not`() {
        observer.onSessionFinished(session(), SyncState.FAILED)

        assertEquals(SyncLogLevel.WARN, sink.levelOf("sync.session.end"))
        assertEquals(1, metrics.snapshot().sessionsFailed)
    }

    @Test
    fun `a session that outlived a clock correction reports zero, not a negative`() {
        // openedAtMs in the future: the wall clock moved backwards mid-session.
        observer.onSessionFinished(session(openedAtMs = NOW + 10_000), SyncState.COMPLETED)

        assertTrue(sink.first("sync.session.end").contains("ms=0"))
        assertEquals(0, metrics.snapshot().longestSessionMs)
    }

    @Test
    fun `phase changes are debug, because there are many of them`() {
        observer.onPhaseChanged(session(), SyncState.TRANSFERRING)

        assertEquals(SyncLogLevel.DEBUG, sink.levelOf("sync.phase"))
        val line = sink.first("sync.phase")
        assertTrue(line, line.contains("state=TRANSFERRING"))
        // The buffer numbers are here so a stalled transfer can be told apart from a slow
        // one without attaching a debugger.
        assertTrue(line, line.contains("chunks=0"))
        assertTrue(line, line.contains("bytes=0"))
        assertEquals(1L, metrics.snapshot().phasesEntered[SyncState.TRANSFERRING])
    }

    // ------------------------------------------------------------------ rejections

    @Test
    fun `a retryable rejection is not shouted about`() {
        observer.onRejected("10.0.0.5:50051", SyncOpcode.CHUNK, SyncErrorCode.BUSY, "queue full")

        // BUSY is the protocol working as designed. At WARN it would teach everyone to
        // ignore the warnings that are not.
        assertEquals(SyncLogLevel.DEBUG, sink.levelOf("sync.reject"))
        assertEquals(1, metrics.snapshot().retryableRejections)
    }

    @Test
    fun `a fatal rejection is a warning and keeps its detail`() {
        observer.onRejected("10.0.0.5:50051", SyncOpcode.HELLO, SyncErrorCode.AUTH_REJECTED, "bad proof")

        assertEquals(SyncLogLevel.WARN, sink.levelOf("sync.reject"))
        val line = sink.first("sync.reject")
        assertTrue(line, line.contains("code=AUTH_REJECTED"))
        assertTrue(line, line.contains("retryable=false"))
        assertTrue(line, line.contains("""detail="bad proof""""))
        assertEquals(0, metrics.snapshot().retryableRejections)
    }

    // ------------------------------------------------------------------ merge

    @Test
    fun `an applied commit reports totals and the reasons behind the drops`() {
        observer.onApplied(
            session(),
            report(
                received = 120, inserted = 100, updated = 15, skipped = 2, rejected = 3,
                rejections = mapOf(
                    WireEntityMapper.RejectReason.HASH_MISMATCH to 2,
                    WireEntityMapper.RejectReason.MISSING_ID to 1
                )
            )
        )

        val applied = sink.first("sync.applied")
        assertTrue(applied, applied.contains("recv=120"))
        assertTrue(applied, applied.contains("rej=3"))

        // One line per reason, never one per record: a malformed bulk push must not be able
        // to make the app write 120 warnings.
        assertEquals(2, sink.count("sync.applied.rejected"))
        assertTrue(sink.lines.any { it.second.contains("reason=HASH_MISMATCH") && it.second.contains("n=2") })

        val snapshot = metrics.snapshot()
        assertEquals(120, snapshot.entitiesReceived)
        assertEquals(2L, snapshot.rejectReasons[WireEntityMapper.RejectReason.HASH_MISMATCH])
    }

    @Test
    fun `a clean commit writes no warnings at all`() {
        observer.onApplied(session(), report(received = 10, inserted = 10))

        assertEquals(listOf("sync.applied"), sink.events())
        assertEquals(1, metrics.snapshot().commitsApplied)
    }

    @Test
    fun `each conflict says which side won`() {
        observer.onApplied(
            session(),
            report(
                received = 2, updated = 2,
                conflicts = listOf(
                    conflict("exp-1", keptLocal = true),
                    conflict("exp-2", keptLocal = false)
                )
            )
        )

        // "My edit disappeared after a sync" is answerable only if we said which record
        // lost and to whom.
        assertEquals(2, sink.count("sync.conflict"))
        val text = sink.lines.joinToString("\n") { it.second }
        assertTrue(text, text.contains("id=exp-1") && text.contains("kept=local"))
        assertTrue(text, text.contains("id=exp-2") && text.contains("kept=remote"))
        assertEquals(2, metrics.snapshot().conflictsResolved)
    }

    @Test
    fun `a first sync between two long-lived devices cannot flood the log`() {
        val many = (1..500).map { conflict("exp-$it") }

        observer.onApplied(session(), report(received = 500, conflicts = many))

        assertEquals(20, sink.count("sync.conflict"))
        assertTrue(sink.first("sync.conflict.more").contains("n=480"))
        // Truncating the log must not truncate the count.
        assertEquals(500, metrics.snapshot().conflictsResolved)
    }

    @Test
    fun `no overflow line when everything fit`() {
        observer.onApplied(session(), report(conflicts = listOf(conflict("exp-1"))))

        assertFalse(sink.events().contains("sync.conflict.more"))
    }

    // ------------------------------------------------------------------ discovery

    @Test
    fun `self-echo is counted but never logged`() {
        // Every broadcast comes back on a multi-NIC phone. Counting it proves the filter
        // is working; logging it would bury everything else.
        discovery.onIgnored(IgnoreReason.SELF_ADDRESS, "192.168.1.10")
        discovery.onIgnored(IgnoreReason.SELF_DEVICE_ID, "me")

        assertTrue(sink.lines.toString(), sink.lines.isEmpty())
        val ignored = metrics.snapshot().discoveryIgnored
        assertEquals(1L, ignored[IgnoreReason.SELF_ADDRESS])
        assertEquals(1L, ignored[IgnoreReason.SELF_DEVICE_ID])
    }

    @Test
    fun `a malformed packet is worth a line`() {
        discovery.onIgnored(IgnoreReason.MALFORMED, "bad magic")

        assertEquals(SyncLogLevel.DEBUG, sink.levelOf("discovery.ignored"))
        assertTrue(sink.first("discovery.ignored").contains("reason=MALFORMED"))
    }

    @Test
    fun `a new device is announced with where to reach it`() {
        discovery.onRecorded(DiscoveryRegistry.Update.NEW, device())

        val line = sink.first("discovery.device")
        assertTrue(line, line.contains("update=NEW"))
        assertTrue(line, line.contains("at=192.168.1.20:50051"))
        assertTrue(line, line.contains("caps=0x03"))
        assertEquals(1, metrics.snapshot().devicesFirstSeen)
    }

    @Test
    fun `a device that moved is announced, one that merely refreshed is not`() {
        discovery.onRecorded(DiscoveryRegistry.Update.MOVED, device(address = "192.168.1.77"))
        discovery.onRecorded(DiscoveryRegistry.Update.REFRESHED, device())
        discovery.onRecorded(DiscoveryRegistry.Update.REFRESHED, device())

        // A refresh happens every announce tick, per device, forever.
        assertEquals(1, sink.count("discovery.device"))
        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.devicesMoved)
        assertEquals(2, snapshot.devicesRefreshed)
    }

    @Test
    fun `replies are counted silently, by dialect`() {
        discovery.onReplied("192.168.1.20:41234", legacy = false)
        discovery.onReplied("192.168.1.21:41235", legacy = true)

        // Answering a query is the happy path; a line per reply would be pure noise. The
        // v2-vs-v1 split is what we actually need, and that is a counter.
        assertTrue(sink.lines.toString(), sink.lines.isEmpty())
        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.discoveryRepliesV2)
        assertEquals(1, snapshot.discoveryRepliesLegacy)
    }

    @Test
    fun `a discovery failure names the stage and the exception type`() {
        discovery.onError("bind", IllegalStateException("port in use"))

        assertEquals(SyncLogLevel.WARN, sink.levelOf("discovery.error"))
        val line = sink.first("discovery.error")
        assertTrue(line, line.contains("stage=bind"))
        assertTrue(line, line.contains("error=IllegalStateException"))
        assertTrue(line, line.contains("""message="port in use""""))
        assertEquals(1L, metrics.snapshot().errorsByStage["bind"])
    }

    @Test
    fun `an exception with no message still produces a usable line`() {
        discovery.onError("query", NullPointerException())

        val line = sink.first("discovery.error")
        assertTrue(line, line.contains("error=NullPointerException"))
        assertTrue(line, line.contains("""message="""""))
    }

    // ------------------------------------------------------------------ line safety

    @Test
    fun `a peer cannot split a log line with whitespace`() {
        discovery.onRecorded(DiscoveryRegistry.Update.NEW, device(name = "Bob's Phone"))

        val line = sink.first("discovery.device")
        // The value is quoted, so `at=` after it is still the next field and not part of
        // the name.
        assertTrue(line, line.contains("""name="Bob's Phone""""))
        assertTrue(line, line.contains("at=192.168.1.20:50051"))
    }

    @Test
    fun `a peer cannot forge a field by embedding a newline`() {
        discovery.onError("query", IllegalStateException("boom\ntrace=deadbeef sync.session.open"))

        val line = sink.first("discovery.error")
        // One event in, one line out. Otherwise a peer could inject fabricated events into
        // the very log used to diagnose it.
        assertEquals(1, line.lines().size)
        assertFalse(line.contains("\n"))
    }

    @Test
    fun `a peer cannot break out of a quoted value`() {
        discovery.onRecorded(DiscoveryRegistry.Update.NEW, device(name = """say "hi" ok"""))

        val line = sink.first("discovery.device")
        val nameValue = line.substringAfter("name=").substringBefore(" at=")
        assertEquals(""""say 'hi' ok"""", nameValue)
    }

    @Test
    fun `an absurdly long value is truncated`() {
        val huge = "x".repeat(5_000)

        discovery.onError("query", IllegalStateException(huge))

        val line = sink.first("discovery.error")
        assertTrue("line was ${line.length} chars", line.length < 300)
    }

    @Test
    fun `an empty value is still a parseable field`() {
        discovery.onIgnored(IgnoreReason.MALFORMED, "")

        // `detail=` with nothing after it would make the next field ambiguous.
        assertTrue(sink.first("discovery.ignored").endsWith("detail=\"\""))
    }

    // ------------------------------------------------------------------ defaults

    @Test
    fun `the default sink swallows everything and the counters still work`() {
        val quiet = MetricsResponderObserver(metrics)

        quiet.onSessionOpened(session())
        quiet.onSessionFinished(session(), SyncState.COMPLETED)

        // Metrics are cheap enough to always be on; logging is not always wanted.
        assertEquals(1, metrics.snapshot().sessionsOpened)
        assertEquals(1, metrics.snapshot().sessionsFinished)
        assertTrue(sink.lines.isEmpty())
    }

    @Test
    fun `two observers on one metrics object share the counters`() {
        val second = MetricsResponderObserver(metrics, SyncLogSink.NONE, clock = { NOW })

        observer.onSessionOpened(session())
        second.onSessionOpened(session())

        assertEquals(2, metrics.snapshot().sessionsOpened)
    }

    @Test
    fun `nothing recorded means nothing logged`() {
        assertTrue(sink.lines.isEmpty())
        assertNull(metrics.snapshot().successRate)
    }

    private fun device(
        id: String = "dev-b",
        name: String = "Pixel 7",
        address: String = "192.168.1.20"
    ) = DiscoveredDevice(
        deviceId = id,
        deviceName = name,
        deviceType = "ANDROID",
        address = address,
        syncPort = 50051,
        capabilities = 0x03
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
