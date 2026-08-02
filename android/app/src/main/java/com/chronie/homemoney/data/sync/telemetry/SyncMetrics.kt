package com.chronie.homemoney.data.sync.telemetry

import com.chronie.homemoney.data.sync.discovery.IgnoreReason
import com.chronie.homemoney.data.sync.engine.WireEntityMapper
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.session.SyncState
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Counters for the sync stack.
 *
 * ### Why this exists
 *
 * Every v1 failure reached the user as the string "sync failed". There was no way to tell a
 * refused pairing from a dropped Wi-Fi link from a database conflict, which meant every bug
 * report was unactionable and every fix was a guess. The responder already knows the
 * difference — [com.chronie.homemoney.data.sync.engine.SyncResponderObserver] has carried it
 * since P3 — but nothing was listening.
 *
 * ### Shape
 *
 * Pure counters: no Android, no logging, no I/O. Recording is lock-free, because it happens
 * on native transport threads that are holding a peer's connection open and must not block
 * on a metrics write. Reading takes a [snapshot], which is a consistent-enough view — the
 * counters are read one at a time, so a snapshot taken mid-session can show a session opened
 * but not yet counted as finished. That is acceptable for diagnostics and is the reason not
 * to pay for a lock on every increment.
 *
 * Key spaces are bounded by enums wherever possible so the maps cannot grow from network
 * input. The one string-keyed map, [errorsByStage], is keyed by our own stage constants and
 * capped regardless.
 */
class SyncMetrics(private val clock: () -> Long = System::currentTimeMillis) {

    // ------------------------------------------------------------------ sessions

    private val sessionsOpened = AtomicLong()
    private val sessionsFinished = AtomicLong()
    private val sessionDurationTotalMs = AtomicLong()
    private val sessionDurationMaxMs = AtomicLong()

    private val finishesByState = SyncState.entries.associateWithAtomic()
    private val phasesEntered = SyncState.entries.associateWithAtomic()

    // ------------------------------------------------------------------ frames

    private val rejectionsByOpcode = SyncOpcode.entries.associateWithAtomic()
    private val rejectionsByCode = SyncErrorCode.entries.associateWithAtomic()

    /** Rejections the peer is expected to retry, split out because they are not real failures. */
    private val retryableRejections = AtomicLong()

    // ------------------------------------------------------------------ merge

    private val commitsApplied = AtomicLong()
    private val entitiesReceived = AtomicLong()
    private val entitiesInserted = AtomicLong()
    private val entitiesUpdated = AtomicLong()
    private val entitiesSkipped = AtomicLong()
    private val entitiesRejected = AtomicLong()
    private val conflictsResolved = AtomicLong()

    /**
     * Why records were dropped.
     *
     * "3 of 120 records were rejected" is a bug report nobody can act on; "3 HASH_MISMATCH"
     * points straight at a serialisation difference between the two builds.
     */
    private val rejectReasons = WireEntityMapper.RejectReason.entries.associateWithAtomic()

    // ------------------------------------------------------------------ discovery

    private val discoveryIgnored = IgnoreReason.entries.associateWithAtomic()
    private val devicesFirstSeen = AtomicLong()
    private val devicesMoved = AtomicLong()
    private val devicesRefreshed = AtomicLong()
    private val discoveryRepliesV2 = AtomicLong()
    private val errorsByStage = ConcurrentHashMap<String, AtomicLong>()

    // ------------------------------------------------------------------ recording

    fun recordSessionOpened() {
        sessionsOpened.incrementAndGet()
    }

    fun recordPhase(state: SyncState) {
        phasesEntered[state]?.incrementAndGet()
    }

    /** @param durationMs wall time from session open; negative values are treated as zero. */
    fun recordSessionFinished(state: SyncState, durationMs: Long) {
        sessionsFinished.incrementAndGet()
        finishesByState[state]?.incrementAndGet()

        val duration = durationMs.coerceAtLeast(0)
        sessionDurationTotalMs.addAndGet(duration)
        // A clock that jumped, or two threads finishing at once, must not lose the maximum.
        sessionDurationMaxMs.accumulateAndGet(duration, ::maxOf)
    }

    fun recordRejection(opcode: SyncOpcode, code: SyncErrorCode) {
        rejectionsByOpcode[opcode]?.incrementAndGet()
        rejectionsByCode[code]?.incrementAndGet()
        if (code.retryable) retryableRejections.incrementAndGet()
    }

    fun recordApplied(
        received: Int,
        inserted: Int,
        updated: Int,
        skipped: Int,
        rejected: Int,
        conflicts: Int,
        reasons: Map<WireEntityMapper.RejectReason, Int> = emptyMap()
    ) {
        commitsApplied.incrementAndGet()
        entitiesReceived.addAndGet(received.toLong())
        entitiesInserted.addAndGet(inserted.toLong())
        entitiesUpdated.addAndGet(updated.toLong())
        entitiesSkipped.addAndGet(skipped.toLong())
        entitiesRejected.addAndGet(rejected.toLong())
        conflictsResolved.addAndGet(conflicts.toLong())
        reasons.forEach { (reason, count) -> rejectReasons[reason]?.addAndGet(count.toLong()) }
    }

    fun recordDiscoveryIgnored(reason: IgnoreReason) {
        discoveryIgnored[reason]?.incrementAndGet()
    }

    fun recordDeviceSeen(isNew: Boolean, moved: Boolean) {
        when {
            isNew -> devicesFirstSeen
            moved -> devicesMoved
            else -> devicesRefreshed
        }.incrementAndGet()
    }

    fun recordDiscoveryReply() {
        discoveryRepliesV2.incrementAndGet()
    }

    fun recordError(stage: String) {
        // Bounded even though every key is ours: a future stage added inside a loop should
        // degrade to under-counting, not to an unbounded map.
        if (errorsByStage.size >= MAX_STAGES && !errorsByStage.containsKey(stage)) {
            errorsByStage.computeIfAbsent(OVERFLOW_STAGE) { AtomicLong() }.incrementAndGet()
            return
        }
        errorsByStage.computeIfAbsent(stage) { AtomicLong() }.incrementAndGet()
    }

    // ------------------------------------------------------------------ reading

    fun snapshot(): SyncMetricsSnapshot {
        val finished = sessionsFinished.get()
        return SyncMetricsSnapshot(
            capturedAtMs = clock(),
            sessionsOpened = sessionsOpened.get(),
            sessionsFinished = finished,
            finishesByState = finishesByState.readNonZero(),
            phasesEntered = phasesEntered.readNonZero(),
            averageSessionMs = if (finished > 0) sessionDurationTotalMs.get() / finished else 0,
            longestSessionMs = sessionDurationMaxMs.get(),
            rejectionsByOpcode = rejectionsByOpcode.readNonZero(),
            rejectionsByCode = rejectionsByCode.readNonZero(),
            retryableRejections = retryableRejections.get(),
            commitsApplied = commitsApplied.get(),
            entitiesReceived = entitiesReceived.get(),
            entitiesInserted = entitiesInserted.get(),
            entitiesUpdated = entitiesUpdated.get(),
            entitiesSkipped = entitiesSkipped.get(),
            entitiesRejected = entitiesRejected.get(),
            conflictsResolved = conflictsResolved.get(),
            rejectReasons = rejectReasons.readNonZero(),
            discoveryIgnored = discoveryIgnored.readNonZero(),
            devicesFirstSeen = devicesFirstSeen.get(),
            devicesMoved = devicesMoved.get(),
            devicesRefreshed = devicesRefreshed.get(),
            discoveryRepliesV2 = discoveryRepliesV2.get(),
            errorsByStage = errorsByStage.entries
                .mapNotNull { (key, value) -> value.get().takeIf { it > 0 }?.let { key to it } }
                .toMap()
        )
    }

    /** Zeroes everything. For tests, and for starting a clean capture before reproducing a bug. */
    fun reset() {
        listOf(
            sessionsOpened, sessionsFinished, sessionDurationTotalMs, sessionDurationMaxMs,
            retryableRejections, commitsApplied, entitiesReceived, entitiesInserted,
            entitiesUpdated, entitiesSkipped, entitiesRejected, conflictsResolved,
            devicesFirstSeen, devicesMoved, devicesRefreshed,
            discoveryRepliesV2
        ).forEach { it.set(0) }

        listOf(
            finishesByState, phasesEntered, rejectionsByOpcode,
            rejectionsByCode, rejectReasons, discoveryIgnored
        ).forEach { map -> map.values.forEach { it.set(0) } }

        errorsByStage.clear()
    }

    private companion object {
        const val MAX_STAGES = 32
        const val OVERFLOW_STAGE = "other"

        fun <E : Enum<E>> List<E>.associateWithAtomic(): Map<E, AtomicLong> =
            // EnumMap over HashMap: fixed size, array-backed, and it cannot rehash under
            // concurrent reads because it is never written to after construction.
            associateWithTo(EnumMap(first().declaringJavaClass)) { AtomicLong() }

        fun <E : Enum<E>> Map<E, AtomicLong>.readNonZero(): Map<E, Long> =
            entries.mapNotNull { (key, value) -> value.get().takeIf { it > 0 }?.let { key to it } }
                .toMap()
    }
}

/**
 * An immutable read of [SyncMetrics].
 *
 * Zero-valued counters are omitted so a snapshot pasted into a bug report is short enough to
 * actually read. What is present is what happened.
 */
data class SyncMetricsSnapshot(
    val capturedAtMs: Long,
    val sessionsOpened: Long,
    val sessionsFinished: Long,
    val finishesByState: Map<SyncState, Long>,
    val phasesEntered: Map<SyncState, Long>,
    val averageSessionMs: Long,
    val longestSessionMs: Long,
    val rejectionsByOpcode: Map<SyncOpcode, Long>,
    val rejectionsByCode: Map<SyncErrorCode, Long>,
    val retryableRejections: Long,
    val commitsApplied: Long,
    val entitiesReceived: Long,
    val entitiesInserted: Long,
    val entitiesUpdated: Long,
    val entitiesSkipped: Long,
    val entitiesRejected: Long,
    val conflictsResolved: Long,
    val rejectReasons: Map<WireEntityMapper.RejectReason, Long>,
    val discoveryIgnored: Map<IgnoreReason, Long>,
    val devicesFirstSeen: Long,
    val devicesMoved: Long,
    val devicesRefreshed: Long,
    val discoveryRepliesV2: Long,
    val errorsByStage: Map<String, Long>
) {

    /** Sessions that ended in anything other than [SyncState.COMPLETED]. */
    val sessionsFailed: Long
        get() = finishesByState.entries.sumOf { (state, count) ->
            if (state == SyncState.COMPLETED) 0 else count
        }

    /** Null until at least one session has finished, rather than a misleading 0% or 100%. */
    val successRate: Double?
        get() = if (sessionsFinished == 0L) {
            null
        } else {
            (finishesByState[SyncState.COMPLETED] ?: 0L).toDouble() / sessionsFinished
        }

    /**
     * A multi-line report for the diagnostics screen and bug reports.
     *
     * Deliberately not the `toString` of a data class: this is read by people trying to
     * explain a failed sync, and the ordering is the order they need it in.
     */
    fun format(): String = buildString {
        appendLine("sync sessions: opened=$sessionsOpened finished=$sessionsFinished failed=$sessionsFailed")
        successRate?.let { appendLine("  success=%.1f%%".format(it * 100)) }
        if (sessionsFinished > 0) {
            appendLine("  duration: avg=${averageSessionMs}ms max=${longestSessionMs}ms")
        }
        finishesByState.forEachLine(this, "  end")
        phasesEntered.forEachLine(this, "  phase")

        if (commitsApplied > 0) {
            appendLine("merge: commits=$commitsApplied received=$entitiesReceived")
            appendLine(
                "  inserted=$entitiesInserted updated=$entitiesUpdated skipped=$entitiesSkipped " +
                    "rejected=$entitiesRejected conflicts=$conflictsResolved"
            )
            rejectReasons.forEachLine(this, "  why")
        }

        if (rejectionsByCode.isNotEmpty()) {
            appendLine("rejections: retryable=$retryableRejections")
            rejectionsByCode.forEachLine(this, "  code")
            rejectionsByOpcode.forEachLine(this, "  op")
        }

        appendLine(
            "discovery: new=$devicesFirstSeen moved=$devicesMoved refreshed=$devicesRefreshed " +
                "replies=$discoveryRepliesV2/v2"
        )
        discoveryIgnored.forEachLine(this, "  dropped")
        errorsByStage.forEachLine(this, "  error")
    }.trimEnd()

    private companion object {
        fun <K> Map<K, Long>.forEachLine(out: StringBuilder, prefix: String) {
            entries.sortedByDescending { it.value }
                .forEach { (key, value) -> out.appendLine("$prefix $key=$value") }
        }
    }
}
