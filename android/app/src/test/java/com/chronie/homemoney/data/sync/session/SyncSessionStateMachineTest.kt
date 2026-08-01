package com.chronie.homemoney.data.sync.session

import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive transition matrix for [SyncSessionStateMachine].
 *
 * The interesting assertion is not that the happy path works - it is that *every other*
 * combination is refused. A state machine that silently accepts a stray event is worse than
 * no state machine at all, because it converts a peer's protocol violation into local state
 * corruption that only surfaces much later.
 *
 * The expected matrix is derived from the machine's own published table rather than
 * restated here; a second hand-written copy would just drift. What this file adds is the
 * *rules around* the table: terminal states absorb nothing, fail and cancel are universal,
 * and the specific edges that were deliberately left out stay out.
 */
class SyncSessionStateMachineTest {

    /** One representative event per kind. Payload values are irrelevant to the transitions. */
    private fun sample(kind: SyncEventKind): SyncEvent = when (kind) {
        SyncEventKind.START -> SyncEvent.Start
        SyncEventKind.HANDSHAKE_ACCEPTED -> SyncEvent.HandshakeAccepted(negotiatedVersion = 2)
        SyncEventKind.AUTHORIZATION_REQUIRED -> SyncEvent.AuthorizationRequired
        SyncEventKind.AUTHORIZATION_GRANTED -> SyncEvent.AuthorizationGranted
        SyncEventKind.MANIFEST_AGREED -> SyncEvent.ManifestAgreed(4, -1, 65536)
        SyncEventKind.CHUNK_ACKNOWLEDGED -> SyncEvent.ChunkAcknowledged(0)
        SyncEventKind.TRANSFER_COMPLETE -> SyncEvent.TransferComplete
        SyncEventKind.CONNECTION_LOST -> SyncEvent.ConnectionLost(SyncErrorCode.PEER_CLOSED)
        SyncEventKind.RECONNECT_SUCCEEDED -> SyncEvent.ReconnectSucceeded
        SyncEventKind.COMMIT_ACCEPTED -> SyncEvent.CommitAccepted(1, 2, 3)
        SyncEventKind.FAIL -> SyncEvent.Fail(SyncErrorCode.IO_TIMEOUT)
        SyncEventKind.CANCEL -> SyncEvent.Cancel
    }

    /** The rule the machine is supposed to implement, expressed independently of its code. */
    private fun expectedTarget(state: SyncState, kind: SyncEventKind): SyncState? = when {
        state.isTerminal -> null
        kind == SyncEventKind.FAIL -> SyncState.FAILED
        kind == SyncEventKind.CANCEL -> SyncState.CANCELLED
        else -> SyncSessionStateMachine.allowedTransitions()[state]?.get(kind)
    }

    // ------------------------------------------------------------ full matrix

    @Test
    fun `every state and event pair behaves exactly as specified`() {
        var accepted = 0
        var rejected = 0

        for (state in SyncState.entries) {
            for (kind in SyncEventKind.entries) {
                val machine = SyncSessionStateMachine(state)
                val result = machine.dispatch(sample(kind))
                val expected = expectedTarget(state, kind)

                if (expected == null) {
                    assertTrue(
                        "$state + $kind should have been rejected but produced $result",
                        result is TransitionResult.Rejected
                    )
                    assertEquals(
                        "$state + $kind was rejected but the state still moved",
                        state,
                        machine.state
                    )
                    rejected++
                } else {
                    assertTrue(
                        "$state + $kind should have been accepted but produced $result",
                        result is TransitionResult.Accepted
                    )
                    assertEquals("$state + $kind target", expected, machine.state)
                    assertEquals("$state + $kind reported target", expected, result.resultingState)
                    accepted++
                }
            }
        }

        // Sanity check on the matrix itself: a table that accidentally became empty, or one
        // that accepted everything, would otherwise pass the loop above.
        assertEquals(
            "matrix size changed unexpectedly",
            SyncState.entries.size * SyncEventKind.entries.size,
            accepted + rejected
        )
        assertTrue("no transition was accepted at all", accepted > 0)
        assertTrue("nothing was rejected - the machine is not guarding anything", rejected > 0)
    }

    // -------------------------------------------------------------- happy path

    @Test
    fun `a trusted peer runs handshake to completion`() {
        val machine = SyncSessionStateMachine()
        val path = listOf(
            SyncEvent.Start to SyncState.HANDSHAKING,
            SyncEvent.HandshakeAccepted(2) to SyncState.EXCHANGING_MANIFEST,
            SyncEvent.ManifestAgreed(3, -1, 65536) to SyncState.TRANSFERRING,
            SyncEvent.ChunkAcknowledged(0) to SyncState.TRANSFERRING,
            SyncEvent.ChunkAcknowledged(1) to SyncState.TRANSFERRING,
            SyncEvent.TransferComplete to SyncState.COMMITTING,
            SyncEvent.CommitAccepted(3, 0, 0) to SyncState.COMPLETED
        )
        for ((event, expected) in path) {
            val result = machine.dispatch(event)
            assertTrue("$event was rejected: $result", result.accepted)
            assertEquals(expected, machine.state)
        }
        assertNull("a completed session must not carry a failure code", machine.failureCode)
        assertFalse(machine.isLive())
    }

    @Test
    fun `an unpaired peer detours through authorisation`() {
        val machine = SyncSessionStateMachine()
        machine.dispatch(SyncEvent.Start)
        assertTrue(machine.dispatch(SyncEvent.AuthorizationRequired).accepted)
        assertEquals(SyncState.AUTHORIZING, machine.state)
        assertTrue(machine.dispatch(SyncEvent.AuthorizationGranted).accepted)
        assertEquals(SyncState.EXCHANGING_MANIFEST, machine.state)
    }

    // ----------------------------------------------------------------- resume

    @Test
    fun `a mid transfer drop resumes without losing the phase`() {
        val machine = SyncSessionStateMachine()
        machine.dispatch(SyncEvent.Start)
        machine.dispatch(SyncEvent.HandshakeAccepted(2))
        machine.dispatch(SyncEvent.ManifestAgreed(10, -1, 65536))
        machine.dispatch(SyncEvent.ChunkAcknowledged(4))

        assertTrue(machine.dispatch(SyncEvent.ConnectionLost(SyncErrorCode.PEER_CLOSED)).accepted)
        assertEquals(SyncState.RECONNECTING, machine.state)

        // The progress bar must not jump backwards while reconnecting, otherwise the user
        // reads it as "it started over".
        assertEquals(SyncState.TRANSFERRING.progress, SyncState.RECONNECTING.progress, 0.0001f)

        assertTrue(machine.dispatch(SyncEvent.ReconnectSucceeded).accepted)
        assertEquals(SyncState.TRANSFERRING, machine.state)
        assertTrue(machine.dispatch(SyncEvent.TransferComplete).accepted)
        assertEquals(SyncState.COMMITTING, machine.state)
    }

    @Test
    fun `reconnect is only offered where there is a checkpoint to resume`() {
        // Documented design decision, not an accident: outside TRANSFERRING there is
        // nothing to resume, so a drop is a plain failure and the caller redials from
        // scratch. Encoding it as a test stops a future edit from "helpfully" widening it.
        for (state in listOf(
            SyncState.HANDSHAKING,
            SyncState.AUTHORIZING,
            SyncState.EXCHANGING_MANIFEST,
            SyncState.COMMITTING
        )) {
            val machine = SyncSessionStateMachine(state)
            val result = machine.dispatch(SyncEvent.ConnectionLost(SyncErrorCode.PEER_CLOSED))
            assertTrue(
                "$state should not be able to enter RECONNECTING, got $result",
                result is TransitionResult.Rejected
            )
        }
    }

    // ---------------------------------------------------------------- guarding

    @Test
    fun `a second start is refused instead of racing`() {
        // This is what replaces the old isSyncing boolean, which two threads could both
        // read as false before either wrote to it.
        val machine = SyncSessionStateMachine()
        assertTrue(machine.dispatch(SyncEvent.Start).accepted)
        val second = machine.dispatch(SyncEvent.Start)
        assertTrue("concurrent start must be refused", second is TransitionResult.Rejected)
        assertEquals(SyncState.HANDSHAKING, machine.state)
    }

    @Test
    fun `terminal states absorb nothing`() {
        for (terminal in listOf(SyncState.COMPLETED, SyncState.FAILED, SyncState.CANCELLED)) {
            for (kind in SyncEventKind.entries) {
                val machine = SyncSessionStateMachine(terminal)
                val result = machine.dispatch(sample(kind))
                assertTrue(
                    "$terminal accepted $kind",
                    result is TransitionResult.Rejected
                )
                assertEquals(terminal, machine.state)
                assertEquals("a rejected event must not count", 0, machine.transitionCount)
            }
        }
    }

    @Test
    fun `failure records the code that caused it`() {
        val machine = SyncSessionStateMachine()
        machine.dispatch(SyncEvent.Start)
        machine.dispatch(SyncEvent.HandshakeAccepted(2))
        assertTrue(machine.fail(SyncErrorCode.CRC_MISMATCH).accepted)
        assertEquals(SyncState.FAILED, machine.state)
        assertEquals(SyncErrorCode.CRC_MISMATCH, machine.failureCode)
        assertFalse(machine.isLive())
    }

    @Test
    fun `cancellation is available from every live state`() {
        for (state in SyncState.entries.filterNot { it.isTerminal }) {
            val machine = SyncSessionStateMachine(state)
            assertTrue("$state refused a cancel", machine.dispatch(SyncEvent.Cancel).accepted)
            assertEquals(SyncState.CANCELLED, machine.state)
            assertNull("cancel is not a failure", machine.failureCode)
        }
    }

    @Test
    fun `transition count tracks only accepted events`() {
        val machine = SyncSessionStateMachine()
        machine.dispatch(SyncEvent.Start)
        machine.dispatch(SyncEvent.CommitAccepted(0, 0, 0)) // illegal here
        machine.dispatch(SyncEvent.HandshakeAccepted(2))
        assertEquals(2, machine.transitionCount)
    }

    // ---------------------------------------------------------------- progress

    @Test
    fun `progress is monotonic along the happy path`() {
        val ordered = listOf(
            SyncState.IDLE,
            SyncState.HANDSHAKING,
            SyncState.AUTHORIZING,
            SyncState.EXCHANGING_MANIFEST,
            SyncState.TRANSFERRING,
            SyncState.COMMITTING,
            SyncState.COMPLETED
        )
        for (i in 1 until ordered.size) {
            assertTrue(
                "${ordered[i]} reports less progress than ${ordered[i - 1]}",
                ordered[i].progress >= ordered[i - 1].progress
            )
        }
        assertEquals(0f, SyncState.IDLE.progress, 0.0001f)
        assertEquals(1f, SyncState.COMPLETED.progress, 0.0001f)
    }

    @Test
    fun `active means occupying a connection slot`() {
        assertFalse(SyncState.IDLE.isActive)
        assertTrue(SyncState.TRANSFERRING.isActive)
        assertTrue(SyncState.RECONNECTING.isActive)
        assertFalse(SyncState.COMPLETED.isActive)
        assertFalse(SyncState.FAILED.isActive)
        assertFalse(SyncState.CANCELLED.isActive)
    }
}
