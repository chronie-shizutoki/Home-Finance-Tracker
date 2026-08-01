package com.chronie.homemoney.data.sync.session

import com.chronie.homemoney.data.sync.protocol.SyncErrorCode

/**
 * The one state machine both ends of a sync run.
 *
 * Before this existed the initiator tracked progress with a `isSyncing` boolean plus a few
 * magic float values (0.1 / 0.4 / 0.7 / 1.0) while the responder tracked nothing at all, so
 * neither side could tell what phase the other was in, and "sync failed" was the only
 * diagnosis available. Here both sides instantiate the *same* machine; only the source of
 * the events differs - the initiator is driven by its own actions, the responder by the
 * frames it receives.
 *
 * The machine is deliberately pure: no coroutines, no I/O, no logging side effects. That
 * makes the full transition matrix testable in microseconds, which matters because an
 * illegal transition slipping through is exactly the class of bug that used to surface only
 * as a hung sync on a real device.
 */
class SyncSessionStateMachine(initial: SyncState = SyncState.IDLE) {

    var state: SyncState = initial
        private set

    /** Error recorded when the machine entered [SyncState.FAILED]; null otherwise. */
    var failureCode: SyncErrorCode? = null
        private set

    /** Number of accepted transitions so far. Cheap telemetry for the session log. */
    var transitionCount: Int = 0
        private set

    /**
     * Applies [event] and reports whether it was legal.
     *
     * Rejection is a normal return value rather than an exception: a peer can always send a
     * frame that does not fit the local phase, and the transport answers that with a
     * structured ERROR frame. Throwing would turn a remote protocol violation into a local
     * crash.
     */
    fun dispatch(event: SyncEvent): TransitionResult {
        val from = state

        if (from.isTerminal) {
            return TransitionResult.Rejected(from, event, "session already finished in $from")
        }

        // Cancellation and failure are legal from every live state and are handled ahead of
        // the table so that the table only has to describe the happy path plus the few
        // genuinely interesting recovery edges.
        when (event) {
            is SyncEvent.Cancel -> return accept(from, SyncState.CANCELLED, event)
            is SyncEvent.Fail -> {
                failureCode = event.code
                return accept(from, SyncState.FAILED, event)
            }
            else -> Unit
        }

        val target = TRANSITIONS[from]?.get(event.kind)
            ?: return TransitionResult.Rejected(
                from,
                event,
                "${event.kind} is not accepted in $from"
            )

        return accept(from, target, event)
    }

    /** Convenience for the common "fail with this code" path. */
    fun fail(code: SyncErrorCode): TransitionResult = dispatch(SyncEvent.Fail(code))

    /** True when the peer may still send frames that this session will act on. */
    fun isLive(): Boolean = !state.isTerminal

    private fun accept(from: SyncState, to: SyncState, event: SyncEvent): TransitionResult {
        state = to
        transitionCount++
        return TransitionResult.Accepted(from, to, event)
    }

    companion object {

        /**
         * The complete legal-transition table, minus the universal Cancel/Fail edges.
         *
         * Two absences are intentional rather than oversights:
         *
         *  - [SyncEventKind.CONNECTION_LOST] is only accepted in
         *    [SyncState.TRANSFERRING]. That is the sole state holding a checkpoint worth
         *    resuming; losing the socket during a handshake or a manifest exchange has
         *    nothing to resume, so it is reported as a plain failure and the client-level
         *    backoff redials from scratch. Allowing RECONNECTING everywhere would let a
         *    session ping-pong forever without ever transferring a byte.
         *
         *  - [SyncEventKind.START] is absent from every state except [SyncState.IDLE].
         *    This is what replaces the old `isSyncing` flag: a second concurrent start is
         *    rejected by the machine itself and answered with BUSY, instead of relying on a
         *    boolean that two threads could both observe as false.
         */
        private val TRANSITIONS: Map<SyncState, Map<SyncEventKind, SyncState>> = mapOf(
            SyncState.IDLE to mapOf(
                SyncEventKind.START to SyncState.HANDSHAKING
            ),
            SyncState.HANDSHAKING to mapOf(
                // HELLO_ACK cleared us straight through: the peer already trusts this device.
                SyncEventKind.HANDSHAKE_ACCEPTED to SyncState.EXCHANGING_MANIFEST,
                // HELLO_ACK asked for a pairing proof or a human tap.
                SyncEventKind.AUTHORIZATION_REQUIRED to SyncState.AUTHORIZING
            ),
            SyncState.AUTHORIZING to mapOf(
                SyncEventKind.AUTHORIZATION_GRANTED to SyncState.EXCHANGING_MANIFEST
            ),
            SyncState.EXCHANGING_MANIFEST to mapOf(
                SyncEventKind.MANIFEST_AGREED to SyncState.TRANSFERRING
            ),
            SyncState.TRANSFERRING to mapOf(
                // Cumulative acks keep the session in place; they only move the checkpoint.
                SyncEventKind.CHUNK_ACKNOWLEDGED to SyncState.TRANSFERRING,
                SyncEventKind.TRANSFER_COMPLETE to SyncState.COMMITTING,
                SyncEventKind.CONNECTION_LOST to SyncState.RECONNECTING
            ),
            SyncState.RECONNECTING to mapOf(
                SyncEventKind.RECONNECT_SUCCEEDED to SyncState.TRANSFERRING
            ),
            SyncState.COMMITTING to mapOf(
                SyncEventKind.COMMIT_ACCEPTED to SyncState.COMPLETED
            )
        )

        /**
         * Every (state, event) pair the table accepts, for the conformance test.
         *
         * Exposed so the test can assert the matrix exhaustively instead of restating it,
         * which would let the two copies drift.
         */
        fun allowedTransitions(): Map<SyncState, Map<SyncEventKind, SyncState>> = TRANSITIONS
    }
}

/**
 * Phases of a sync session.
 *
 * Mirrors the diagram in `docs/sync/REFACTOR_PLAN.md` section 3.4. The UI progress bar is
 * derived from this enum rather than from hard-coded floats, so the two ends necessarily
 * report the same phase.
 */
enum class SyncState {
    /** Nothing in flight. The only state that accepts a new session. */
    IDLE,

    /** HELLO sent or received; versions and capabilities are being negotiated. */
    HANDSHAKING,

    /** Waiting on a pairing proof and/or the user's explicit acceptance. */
    AUTHORIZING,

    /** Both sides are describing what they have before any data moves. */
    EXCHANGING_MANIFEST,

    /** Chunks are on the wire. */
    TRANSFERRING,

    /** The socket dropped mid-transfer; the checkpoint is intact and resume is pending. */
    RECONNECTING,

    /** All chunks acknowledged; the receiver is applying them in one transaction. */
    COMMITTING,

    /** Applied successfully. */
    COMPLETED,

    /** Gave up. [SyncSessionStateMachine.failureCode] says why. */
    FAILED,

    /** Aborted by the user or by a BYE from the peer. */
    CANCELLED;

    /** Terminal states accept no further events. */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED

    /** True while the session occupies a connection slot on the server. */
    val isActive: Boolean
        get() = !isTerminal && this != IDLE

    /**
     * Coarse progress for the UI, in 0..1.
     *
     * Both devices compute this from the same enum, which is what stops the two progress
     * bars from disagreeing. RECONNECTING deliberately reports the same value as
     * TRANSFERRING: the work already done is not lost, so the bar must not jump backwards.
     */
    val progress: Float
        get() = when (this) {
            IDLE -> 0f
            HANDSHAKING -> 0.05f
            AUTHORIZING -> 0.15f
            EXCHANGING_MANIFEST -> 0.25f
            TRANSFERRING, RECONNECTING -> 0.45f
            COMMITTING -> 0.9f
            COMPLETED -> 1f
            FAILED, CANCELLED -> 1f
        }
}

/**
 * Event kinds, split from [SyncEvent] so the transition table can be a plain map and the
 * test can enumerate the full state x event matrix.
 */
enum class SyncEventKind {
    START,
    HANDSHAKE_ACCEPTED,
    AUTHORIZATION_REQUIRED,
    AUTHORIZATION_GRANTED,
    MANIFEST_AGREED,
    CHUNK_ACKNOWLEDGED,
    TRANSFER_COMPLETE,
    CONNECTION_LOST,
    RECONNECT_SUCCEEDED,
    COMMIT_ACCEPTED,
    FAIL,
    CANCEL
}

/** Input to [SyncSessionStateMachine.dispatch]. */
sealed interface SyncEvent {

    val kind: SyncEventKind

    /** Initiator dialled out, or responder accepted a connection. */
    data object Start : SyncEvent {
        override val kind = SyncEventKind.START
    }

    /** HELLO_ACK arrived and no further authorisation is needed. */
    data class HandshakeAccepted(val negotiatedVersion: Int) : SyncEvent {
        override val kind = SyncEventKind.HANDSHAKE_ACCEPTED
    }

    /** HELLO_ACK arrived with `requires_user_confirmation` or a pairing challenge. */
    data object AuthorizationRequired : SyncEvent {
        override val kind = SyncEventKind.AUTHORIZATION_REQUIRED
    }

    /** Pairing proof verified and, where required, the user tapped accept. */
    data object AuthorizationGranted : SyncEvent {
        override val kind = SyncEventKind.AUTHORIZATION_GRANTED
    }

    /**
     * MANIFEST_ACK settled the transfer parameters.
     *
     * @param resumeFromChunk -1 for a fresh transfer, otherwise the highest contiguous
     *   chunk the receiver already holds.
     */
    data class ManifestAgreed(
        val totalChunks: Int,
        val resumeFromChunk: Int,
        val chunkSize: Int
    ) : SyncEvent {
        override val kind = SyncEventKind.MANIFEST_AGREED
    }

    /** Cumulative CHUNK_ACK; moves the checkpoint without leaving TRANSFERRING. */
    data class ChunkAcknowledged(val ackedThroughChunk: Int) : SyncEvent {
        override val kind = SyncEventKind.CHUNK_ACKNOWLEDGED
    }

    /** Last chunk acknowledged. */
    data object TransferComplete : SyncEvent {
        override val kind = SyncEventKind.TRANSFER_COMPLETE
    }

    /** Socket died while chunks were still moving. */
    data class ConnectionLost(val code: SyncErrorCode) : SyncEvent {
        override val kind = SyncEventKind.CONNECTION_LOST
    }

    /** Redial succeeded and the peer agreed to continue from the checkpoint. */
    data object ReconnectSucceeded : SyncEvent {
        override val kind = SyncEventKind.RECONNECT_SUCCEEDED
    }

    /** COMMIT_ACK reported a successful apply. */
    data class CommitAccepted(
        val inserted: Int,
        val updated: Int,
        val skipped: Int
    ) : SyncEvent {
        override val kind = SyncEventKind.COMMIT_ACCEPTED
    }

    /** Terminal failure. Legal from any live state. */
    data class Fail(val code: SyncErrorCode) : SyncEvent {
        override val kind = SyncEventKind.FAIL
    }

    /** User abort or peer BYE. Legal from any live state. */
    data object Cancel : SyncEvent {
        override val kind = SyncEventKind.CANCEL
    }
}

/** Outcome of [SyncSessionStateMachine.dispatch]. */
sealed interface TransitionResult {

    data class Accepted(
        val from: SyncState,
        val to: SyncState,
        val event: SyncEvent
    ) : TransitionResult

    /**
     * @param reason human readable context for the structured log. A rejection is not an
     *   error on this device; it usually means the peer is out of step.
     */
    data class Rejected(
        val state: SyncState,
        val event: SyncEvent,
        val reason: String
    ) : TransitionResult

    val accepted: Boolean
        get() = this is Accepted

    /** State after the dispatch, unchanged when the event was rejected. */
    val resultingState: SyncState
        get() = when (this) {
            is Accepted -> to
            is Rejected -> state
        }
}
