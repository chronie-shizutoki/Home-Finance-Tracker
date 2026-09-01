package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.session.SyncSession
import com.chronie.homemoney.data.sync.session.SyncState

/**
 * Everything the responder wants to tell the outside world.
 *
 * The protocol layer must not reach into the UI: it runs on native transport threads and
 * knows nothing about progress bars or coroutine scopes. It does, however, know things the
 * UI needs - which phase the session is in, what was applied, why a peer was refused - and
 * previously that knowledge stayed trapped inside the handler, which is why a failed sync
 * could only ever be reported as "sync failed".
 *
 * Every method has a default no-op body so an implementation can observe one thing without
 * restating the rest.
 *
 * Callbacks arrive on native transport threads, possibly several at once. Implementations
 * must not block: the calling thread is holding the peer's connection open.
 */
interface SyncResponderObserver {

    /** A peer opened a session. */
    fun onSessionOpened(session: SyncSession) = Unit

    /** The session's state machine moved. Drives the progress indicator. */
    fun onPhaseChanged(session: SyncSession, state: SyncState) = Unit

    /** A peer was refused, or a frame could not be honored. */
    fun onRejected(
        peerAddress: String,
        opcode: SyncOpcode,
        code: SyncErrorCode,
        detail: String
    ) = Unit

    /** A commit was applied. Carries the full merge accounting, conflicts included. */
    fun onApplied(session: SyncSession, report: EntityApplier.ApplyReport) = Unit

    /** The session reached a terminal state. */
    fun onSessionFinished(session: SyncSession, state: SyncState) = Unit

    companion object {
        val NONE: SyncResponderObserver = object : SyncResponderObserver {}
    }
}

/**
 * This device's identity as presented to a peer.
 *
 * [deviceId] must be the persisted value, not a per-process one: it is the deterministic
 * tie-break in [com.chronie.homemoney.data.sync.merge.ExpenseMerger], so a value that
 * changed between runs would let two devices disagree about a winner and never converge.
 */
data class SyncIdentity(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "ANDROID",
    val appVersion: String = ""
)
