package com.chronie.homemoney.data.sync.telemetry

import com.chronie.homemoney.data.sync.discovery.DiscoveredDevice
import com.chronie.homemoney.data.sync.discovery.DiscoveryRegistry
import com.chronie.homemoney.data.sync.discovery.DiscoveryTelemetry
import com.chronie.homemoney.data.sync.discovery.IgnoreReason
import com.chronie.homemoney.data.sync.engine.EntityApplier
import com.chronie.homemoney.data.sync.engine.SyncResponderObserver
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.session.SyncSession
import com.chronie.homemoney.data.sync.session.SyncState

enum class SyncLogLevel { DEBUG, INFO, WARN }

/** Where structured sync lines go. Kept abstract so tests can read them back. */
fun interface SyncLogSink {
    fun emit(level: SyncLogLevel, line: String)

    companion object {
        val NONE = SyncLogSink { _, _ -> }
    }
}

/**
 * Builds one `logfmt`-style line: `event key=value key="value with spaces"`.
 *
 * The format is chosen so a logcat dump can be filtered by `trace=` and read as a single
 * session's story. That is the whole point of the trace id: a sync involves two devices, and
 * until both logs can be zipped together on a shared key, a failure report from one side is
 * half a sentence.
 */
internal class SyncLogLine(event: String) {

    private val builder = StringBuilder(event)

    fun add(key: String, value: Any?): SyncLogLine {
        val text = value?.toString().orEmpty()
        builder.append(' ').append(key).append('=')
        // Quote anything that would break field splitting; truncate anything that would
        // turn one log line into a wall of text. A peer controls some of these strings.
        val safe = text.replace('\n', ' ').replace('"', '\'').take(MAX_VALUE_CHARS)
        if (safe.isEmpty() || safe.any { it.isWhitespace() }) {
            builder.append('"').append(safe).append('"')
        } else {
            builder.append(safe)
        }
        return this
    }

    override fun toString(): String = builder.toString()

    private companion object {
        const val MAX_VALUE_CHARS = 120
    }
}

/**
 * Turns responder callbacks into counters and one structured line each.
 *
 * Runs on native transport threads while a peer's connection is held open, so every method
 * must be cheap and non-blocking: increment, format, emit, return. No allocation-heavy work,
 * no locks, no I/O beyond the sink's own.
 */
class MetricsResponderObserver(
    private val metrics: SyncMetrics,
    private val sink: SyncLogSink = SyncLogSink.NONE,
    private val clock: () -> Long = System::currentTimeMillis
) : SyncResponderObserver {

    override fun onSessionOpened(session: SyncSession) {
        metrics.recordSessionOpened()
        sink.emit(
            SyncLogLevel.INFO,
            SyncLogLine("sync.session.open")
                .add("trace", session.traceId)
                .add("session", "%016X".format(session.sessionId))
                .add("peer", session.peerAddress)
                .add("dev", session.peerDeviceId.ifEmpty { "?" })
                .add("name", session.peerDeviceName.ifEmpty { "?" })
                .add("ver", session.negotiatedVersion)
                .add("caps", "0x%02X".format(session.peerCapabilities))
                .toString()
        )
    }

    override fun onPhaseChanged(session: SyncSession, state: SyncState) {
        metrics.recordPhase(state)
        val buffers = session.bufferStats()
        sink.emit(
            SyncLogLevel.DEBUG,
            SyncLogLine("sync.phase")
                .add("trace", session.traceId)
                .add("state", state)
                .add("acked", session.ackedThroughChunk)
                .add("chunks", buffers.chunks)
                .add("entities", buffers.entities)
                .add("bytes", buffers.bytes)
                .toString()
        )
    }

    override fun onRejected(
        peerAddress: String,
        opcode: SyncOpcode,
        code: SyncErrorCode,
        detail: String
    ) {
        metrics.recordRejection(opcode, code)
        // A retryable rejection is the protocol working, not a fault. Logging it at WARN
        // would train everyone to ignore the warnings that matter.
        sink.emit(
            if (code.retryable) SyncLogLevel.DEBUG else SyncLogLevel.WARN,
            SyncLogLine("sync.reject")
                .add("peer", peerAddress)
                .add("op", opcode)
                .add("code", code)
                .add("retryable", code.retryable)
                .add("detail", detail)
                .toString()
        )
    }

    override fun onApplied(session: SyncSession, report: EntityApplier.ApplyReport) {
        metrics.recordApplied(
            received = report.received,
            inserted = report.inserted,
            updated = report.updated,
            skipped = report.skipped,
            rejected = report.rejected,
            conflicts = report.conflicts.size,
            reasons = report.rejections
        )
        sink.emit(
            SyncLogLevel.INFO,
            SyncLogLine("sync.applied")
                .add("trace", session.traceId)
                .add("recv", report.received)
                .add("ins", report.inserted)
                .add("upd", report.updated)
                .add("skip", report.skipped)
                .add("rej", report.rejected)
                .add("conflicts", report.conflicts.size)
                .toString()
        )

        // The rejection reasons are the actionable half of a partial sync: "117 applied,
        // 3 rejected" is only useful with the why. Bounded by the enum, so this is at most
        // one line per reason no matter how large the push was.
        report.rejections.forEach { (reason, count) ->
            sink.emit(
                SyncLogLevel.WARN,
                SyncLogLine("sync.applied.rejected")
                    .add("trace", session.traceId)
                    .add("reason", reason)
                    .add("n", count)
                    .toString()
            )
        }

        // "My edit disappeared after a sync" was the one report we could never answer.
        // One line per conflict says which record lost and why - capped, because a first
        // sync between two long-lived devices can legitimately conflict on thousands.
        report.conflicts.take(MAX_LOGGED_CONFLICTS).forEach { conflict ->
            sink.emit(
                SyncLogLevel.INFO,
                SyncLogLine("sync.conflict")
                    .add("trace", session.traceId)
                    .add("id", conflict.entityId)
                    .add("reason", conflict.reason)
                    .add("kept", if (conflict.keptLocal) "local" else "remote")
                    .toString()
            )
        }
        val unlogged = report.conflicts.size - MAX_LOGGED_CONFLICTS
        if (unlogged > 0) {
            sink.emit(
                SyncLogLevel.INFO,
                SyncLogLine("sync.conflict.more")
                    .add("trace", session.traceId)
                    .add("n", unlogged)
                    .toString()
            )
        }
    }

    override fun onSessionFinished(session: SyncSession, state: SyncState) {
        val durationMs = clock() - session.openedAtMs
        metrics.recordSessionFinished(state, durationMs)
        sink.emit(
            if (state == SyncState.COMPLETED) SyncLogLevel.INFO else SyncLogLevel.WARN,
            SyncLogLine("sync.session.end")
                .add("trace", session.traceId)
                .add("state", state)
                .add("ms", durationMs.coerceAtLeast(0))
                .add("peer", session.peerAddress)
                .toString()
        )
    }

    private companion object {
        /** A first sync could otherwise emit one line per diverged record. */
        const val MAX_LOGGED_CONFLICTS = 20
    }
}

/** The same treatment for discovery. Separate class because it is on a different hot path. */
class MetricsDiscoveryTelemetry(
    private val metrics: SyncMetrics,
    private val sink: SyncLogSink = SyncLogSink.NONE
) : DiscoveryTelemetry {

    override fun onIgnored(reason: IgnoreReason, detail: String) {
        metrics.recordDiscoveryIgnored(reason)
        // Self-echo is the normal state of affairs on a multi-NIC phone; counting it is
        // useful, logging every occurrence is not.
        if (reason == IgnoreReason.SELF_ADDRESS || reason == IgnoreReason.SELF_DEVICE_ID) return
        sink.emit(
            SyncLogLevel.DEBUG,
            SyncLogLine("discovery.ignored").add("reason", reason).add("detail", detail).toString()
        )
    }

    override fun onRecorded(update: DiscoveryRegistry.Update, device: DiscoveredDevice) {
        metrics.recordDeviceSeen(
            isNew = update == DiscoveryRegistry.Update.NEW,
            moved = update == DiscoveryRegistry.Update.MOVED
        )
        if (update == DiscoveryRegistry.Update.REFRESHED) return
        sink.emit(
            SyncLogLevel.INFO,
            SyncLogLine("discovery.device")
                .add("update", update)
                .add("dev", device.deviceId)
                .add("name", device.deviceName)
                .add("at", "${device.address}:${device.syncPort}")
                .add("ver", device.protocolVersion)
                .add("caps", "0x%02X".format(device.capabilities))
                .toString()
        )
    }

    override fun onReplied(to: String) {
        metrics.recordDiscoveryReply()
    }

    override fun onError(stage: String, error: Throwable) {
        metrics.recordError(stage)
        sink.emit(
            SyncLogLevel.WARN,
            SyncLogLine("discovery.error")
                .add("stage", stage)
                .add("error", error.javaClass.simpleName)
                .add("message", error.message.orEmpty())
                .toString()
        )
    }
}
