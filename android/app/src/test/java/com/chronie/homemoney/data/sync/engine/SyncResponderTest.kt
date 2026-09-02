package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.sync.auth.SyncAuthorizer
import com.chronie.homemoney.data.sync.auth.SyncPairing
import com.chronie.homemoney.data.sync.generated.AuthAckPayload
import com.chronie.homemoney.data.sync.generated.AuthPayload
import com.chronie.homemoney.data.sync.generated.ChunkAckPayload
import com.chronie.homemoney.data.sync.generated.CommitAckPayload
import com.chronie.homemoney.data.sync.generated.CommitPayload
import com.chronie.homemoney.data.sync.generated.HelloAckPayload
import com.chronie.homemoney.data.sync.generated.HelloPayload
import com.chronie.homemoney.data.sync.generated.ManifestAckPayload
import com.chronie.homemoney.data.sync.generated.PullAckPayload
import com.chronie.homemoney.data.sync.generated.PullPayload
import com.chronie.homemoney.data.sync.generated.SyncCapability
import com.chronie.homemoney.data.sync.generated.SyncError
import com.chronie.homemoney.data.sync.generated.SyncOperation
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol
import com.chronie.homemoney.data.sync.session.SyncSession
import com.chronie.homemoney.data.sync.session.SyncSessionRegistry
import com.chronie.homemoney.data.sync.session.SyncState
import com.google.protobuf.ByteString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end behaviour of the v2 responder, driven the way a real peer drives it.
 *
 * Every case here corresponds to something the v1 path got wrong. v1 had one callback that
 * parsed a blob, applied it and returned the whole database, so there was no phase to be in
 * the wrong one of, no checkpoint to resume from, and a retry after a lost response applied
 * everything a second time. The assertions below pin down the replacements for exactly those
 * behaviours:
 *
 *  - a frame that does not fit the session's phase is refused with a typed error rather than
 *    silently acted on,
 *  - a replayed COMMIT is answered from cache instead of applied twice (D4),
 *  - a peer that streams more than was agreed hits a buffer cap instead of an OOM (C8),
 *  - deletions cross the wire as tombstones (F3),
 *  - a PULL is a pure function of (watermark, index), so it is safely repeatable.
 *
 * The transport is deliberately absent: [SyncResponder] implements `SyncFrameHandler`, whose
 * whole contract is `bytes in, bytes out`, so the full protocol can be exercised on the JVM
 * without a socket, an emulator or a second device.
 */
class SyncResponderTest {

    private val peerAddress = "192.168.1.44:41000"
    private val peerDeviceId = "peer-device-id"
    private val localIdentity = SyncIdentity(deviceId = "local-device-id", deviceName = "Local Phone")

    // ------------------------------------------------------------------ happy path

    @Test
    fun `a full push applies every entity`() {
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(
            expense("a", amount = 10.0, updatedAt = 1_000),
            expense("b", amount = 20.0, updatedAt = 2_000)
        )
        h.pushManifest(delta)
        h.pushAllChunks(delta)
        val ack = h.commit(delta)

        assertTrue("commit must be applied", ack.applied)
        assertEquals(2, ack.insertedCount)
        assertEquals(0, ack.updatedCount)
        assertEquals(setOf("a", "b"), h.store.rows.keys)
        assertEquals(10.0, h.store.rows.getValue("a").amount, 0.0)
        assertEquals(SyncState.COMPLETED, h.session().machine.state)
    }

    @Test
    fun `the whole delta lands in exactly one transaction`() {
        // v1 inserted row by row with no transaction, so a failure part way through left the
        // database holding half a sync. The applier must hand the store a single batch.
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(*(1..40).map { expense("e$it", updatedAt = 1_000L + it) }.toTypedArray())
        h.pushManifest(delta)
        h.pushAllChunks(delta)
        h.commit(delta)

        assertEquals(40, h.store.rows.size)
        assertEquals("expected a single batched write", 1, h.store.writeCalls)
    }

    @Test
    fun `a multi chunk push is reassembled in order`() {
        val h = harness()
        h.handshakeAsTrustedPeer()

        val rows = (1..600).map { expense("row-%04d".format(it), updatedAt = 1_000L + it) }
        val delta = h.deltaOf(*rows.toTypedArray(), chunkSize = SyncWireProtocol.MIN_CHUNK_SIZE)
        assertTrue("test needs several chunks to be meaningful", delta.chunkCount > 1)

        h.pushManifest(delta)
        h.pushAllChunks(delta)
        val ack = h.commit(delta)

        assertTrue(ack.applied)
        assertEquals(600, h.store.rows.size)
    }

    @Test
    fun `chunks arriving out of order still assemble`() {
        // Reordering is normal once chunks are individually retransmitted; the checkpoint
        // must only advance over a contiguous prefix, and the assembled set must still be in
        // manifest order or the aggregate hash would not match.
        val h = harness()
        h.handshakeAsTrustedPeer()

        val rows = (1..600).map { expense("row-%04d".format(it), updatedAt = 1_000L + it) }
        val delta = h.deltaOf(*rows.toTypedArray(), chunkSize = SyncWireProtocol.MIN_CHUNK_SIZE)
        h.pushManifest(delta)

        val indices = (0 until delta.chunkCount).toList()
        for (index in indices.reversed()) {
            h.pushChunk(delta, index)
        }
        val ack = h.commit(delta)

        assertTrue("reordered chunks must still verify: ${ack.errorMessage}", ack.applied)
        assertEquals(600, h.store.rows.size)
    }

    @Test
    fun `a deletion travels as a tombstone and is applied`() {
        // F3 at the v2 level. v1 filtered `deleted_at IS NULL` out of the delta and hardcoded
        // the operation to CREATE, so a deletion could never reach the peer at all.
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(expense("gone", updatedAt = 5_000, deletedAt = 5_000))
        assertEquals(
            SyncOperation.SYNC_OPERATION_DELETE,
            delta.entities.single().operation
        )

        h.pushManifest(delta)
        h.pushAllChunks(delta)
        assertTrue(h.commit(delta).applied)

        val stored = h.store.rows.getValue("gone")
        assertEquals("tombstone must survive the round trip", 5_000L, stored.deletedAt)
    }

    // ------------------------------------------------------------- idempotency

    @Test
    fun `a replayed commit is answered from cache and not applied twice`() {
        // D4. The peer never saw COMMIT_ACK and resends. Applying a second time would double
        // the ledger; refusing would strand a sync that in fact succeeded.
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(expense("a", amount = 10.0, updatedAt = 1_000))
        h.pushManifest(delta)
        h.pushAllChunks(delta)

        val first = h.commit(delta)
        val writesAfterFirst = h.store.writeCalls
        val second = h.commit(delta)

        assertTrue(first.applied)
        assertTrue("the replay must report success too", second.applied)
        assertEquals(first.insertedCount, second.insertedCount)
        assertEquals("no second write may reach the store", writesAfterFirst, h.store.writeCalls)
        assertEquals(1, h.store.rows.size)
    }

    @Test
    fun `a duplicate chunk is acknowledged rather than rejected`() {
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(expense("a", updatedAt = 1_000))
        h.pushManifest(delta)
        val firstAck = h.pushChunk(delta, 0)
        val replayAck = h.pushChunk(delta, 0)

        assertEquals(SyncError.SYNC_ERROR_OK, replayAck.error)
        assertEquals(
            "a retransmission must get the same checkpoint back",
            firstAck.ackedThroughChunk,
            replayAck.ackedThroughChunk
        )
    }

    @Test
    fun `the same revision arriving in two chunks is applied once`() {
        // A resume renumbers chunks, so one revision can legitimately be delivered twice
        // inside a single transfer. The entity-level guard is what catches that.
        val h = harness()
        h.handshakeAsTrustedPeer()

        val row = expense("dup", amount = 33.0, updatedAt = 4_000)
        val delta = h.deltaOf(row, row)

        h.pushManifest(delta)
        h.pushAllChunks(delta)
        val ack = h.commit(delta)

        assertTrue(ack.applied)
        assertEquals(1, h.store.rows.size)
        assertEquals(1, ack.insertedCount)
        assertTrue("the second copy must be counted as skipped", ack.skippedCount >= 1)
    }

    // ------------------------------------------------------------ phase guards

    @Test
    fun `a chunk before any manifest is refused`() {
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(expense("a", updatedAt = 1_000))
        val ack = ChunkAckPayload.parseFrom(
            h.send(SyncOpcode.CHUNK, delta.chunkPayload(0, h.sessionId).toByteArray())!!
        )

        assertEquals(SyncError.SYNC_ERROR_PROTOCOL_MISMATCH, ack.error)
        assertTrue(h.store.rows.isEmpty())
    }

    @Test
    fun `a manifest before authorisation is refused`() {
        // The peer is unknown and the user has not accepted, so the session sits in
        // AUTHORIZING. Any data frame that slips past must be refused, not buffered.
        val h = harness(authorizer = FakeAuthorizer(decision = SyncAuthorizer.Decision.ACCEPTED))
        h.hello()

        val delta = h.deltaOf(expense("a", updatedAt = 1_000))
        val ack = ManifestAckPayload.parseFrom(
            h.send(SyncOpcode.MANIFEST, delta.manifest(h.sessionId).toByteArray())!!
        )

        assertFalse(ack.accepted)
        assertEquals(SyncError.SYNC_ERROR_AUTH_REJECTED, ack.error)
    }

    @Test
    fun `a commit for an unknown session is refused without a crash`() {
        val h = harness()
        val ack = CommitAckPayload.parseFrom(
            h.send(
                SyncOpcode.COMMIT,
                CommitPayload.newBuilder().setSessionId(h.sessionId).setExpectedEntities(1).build()
                    .toByteArray()
            )!!
        )

        assertFalse(ack.applied)
        assertEquals(SyncError.SYNC_ERROR_PROTOCOL_MISMATCH, ack.error)
    }

    @Test
    fun `a session id of zero is refused`() {
        // 0 is what an uninitialised header looks like. Accepting it would merge every buggy
        // peer on the network into one shared session.
        val h = harness()
        val ack = HelloAckPayload.parseFrom(
            h.responder.handleFrame(peerAddress, SyncOpcode.HELLO, 0L, 0, h.helloPayload())!!
        )

        assertEquals(SyncError.SYNC_ERROR_PROTOCOL_MISMATCH, ack.error)
        assertEquals(0, h.registry.activeCount())
    }

    @Test
    fun `an unsupported protocol version is refused`() {
        val h = harness()
        val hello = HelloPayload.newBuilder()
            .setProtocolVersion(99)
            .setMinSupportedVersion(99)
            .setDeviceId(peerDeviceId)
            .setDeviceName("Old Phone")
            .build()

        val ack = HelloAckPayload.parseFrom(h.send(SyncOpcode.HELLO, hello.toByteArray())!!)

        assertEquals(SyncError.SYNC_ERROR_PROTOCOL_MISMATCH, ack.error)
    }

    @Test
    fun `a malformed payload is refused instead of killing the connection`() {
        // Returning null here would drop the socket and tell the peer nothing. It gets a
        // typed PARSE_ERROR so it can stop retrying a frame that will never be accepted.
        val h = harness()
        val garbage = ByteArray(64) { 0xFF.toByte() }

        val reply = h.send(SyncOpcode.HELLO, garbage)

        assertTrue("a parse failure must still produce an answer", reply != null)
        assertEquals(SyncError.SYNC_ERROR_PARSE_ERROR, HelloAckPayload.parseFrom(reply!!).error)
    }

    // ------------------------------------------------------------ integrity

    @Test
    fun `a commit with missing chunks names the holes and stays resumable`() {
        val h = harness()
        h.handshakeAsTrustedPeer()

        val rows = (1..600).map { expense("row-%04d".format(it), updatedAt = 1_000L + it) }
        val delta = h.deltaOf(*rows.toTypedArray(), chunkSize = SyncWireProtocol.MIN_CHUNK_SIZE)
        h.pushManifest(delta)

        // Everything except the last chunk.
        for (index in 0 until delta.chunkCount - 1) h.pushChunk(delta, index)

        val partial = h.commit(delta)
        assertFalse(partial.applied)
        assertEquals(SyncError.SYNC_ERROR_CRC_MISMATCH, partial.error)
        assertTrue(h.store.rows.isEmpty())

        // The session survives, so the peer only has to send what was missing.
        h.pushChunk(delta, delta.chunkCount - 1)
        val complete = h.commit(delta)

        assertTrue("the transfer must finish after the hole is filled", complete.applied)
        assertEquals(600, h.store.rows.size)
    }

    @Test
    fun `an aggregate hash mismatch resets the transfer and the retry succeeds`() {
        // Every frame passed its own CRC, so the damage is at the set level. Clearing the
        // buffers is what makes the retransmission look new instead of being answered as a
        // duplicate and wedging the session.
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(expense("a", updatedAt = 1_000), expense("b", updatedAt = 2_000))
        h.pushManifest(delta)
        h.pushAllChunks(delta)

        val corrupted = CommitPayload.newBuilder()
            .setSessionId(h.sessionId)
            .setExpectedEntities(delta.entityCount)
            .setContentHash(ByteString.copyFrom(ByteArray(32) { 0x5A }))
            .build()
        val rejected = CommitAckPayload.parseFrom(
            h.send(SyncOpcode.COMMIT, corrupted.toByteArray())!!
        )

        assertFalse(rejected.applied)
        assertEquals(SyncError.SYNC_ERROR_CRC_MISMATCH, rejected.error)
        assertTrue("nothing may be written on a failed verification", h.store.rows.isEmpty())

        h.pushManifest(delta)
        h.pushAllChunks(delta)
        val retried = h.commit(delta)

        assertTrue("the clean retransmission must be accepted: ${retried.errorMessage}", retried.applied)
        assertEquals(2, h.store.rows.size)
    }

    @Test
    fun `a peer that exceeds the buffer cap is refused instead of exhausting memory`() {
        // C8. The alternative to a cap is an OutOfMemoryError that takes the whole app down,
        // triggered by a peer that streams chunks and simply never commits.
        val h = harness(
            registry = SyncSessionRegistry(
                sessionLimits = SyncSession.Limits(maxBufferedEntities = 10, maxBufferedBytes = 4_096)
            )
        )
        h.handshakeAsTrustedPeer()

        val rows = (1..600).map { expense("row-%04d".format(it), updatedAt = 1_000L + it) }
        val delta = h.deltaOf(*rows.toTypedArray(), chunkSize = SyncWireProtocol.MIN_CHUNK_SIZE)
        h.pushManifest(delta)

        val ack = h.pushChunk(delta, 0)

        assertEquals(SyncError.SYNC_ERROR_PAYLOAD_TOO_LARGE, ack.error)
        assertEquals(SyncState.FAILED, h.session().machine.state)
        assertTrue(h.store.rows.isEmpty())
    }

    @Test
    fun `a store failure is reported as APPLY_ERROR rather than success`() {
        val h = harness()
        h.handshakeAsTrustedPeer()

        val delta = h.deltaOf(expense("a", updatedAt = 1_000))
        h.pushManifest(delta)
        h.pushAllChunks(delta)
        h.store.failNextWrite = true

        val ack = h.commit(delta)

        assertFalse(ack.applied)
        assertEquals(SyncError.SYNC_ERROR_APPLY_ERROR, ack.error)
        assertEquals(SyncState.FAILED, h.session().machine.state)
    }

    // ----------------------------------------------------------------- pull

    @Test
    fun `a pull serves this device's own delta`() {
        val h = harness()
        h.store.seed(expense("local-1", amount = 5.0, updatedAt = 900))
        h.handshakeAsTrustedPeer()

        val ack = h.pull(chunkIndex = 0, since = 0L)

        assertEquals(SyncError.SYNC_ERROR_OK, ack.error)
        assertEquals(1, ack.totalEntities)
        assertEquals("local-1", ack.entitiesList.single().entityId)
        assertEquals(900L, ack.newWatermark)
    }

    @Test
    fun `the same pull twice returns the same bytes`() {
        val h = harness()
        h.store.seed(expense("local-1", updatedAt = 900))
        h.handshakeAsTrustedPeer()

        val first = h.pull(chunkIndex = 0, since = 0L)
        val second = h.pull(chunkIndex = 0, since = 0L)

        assertEquals(first, second)
    }

    @Test
    fun `a pull in progress is not renumbered by a concurrent local edit`() {
        // The snapshot is taken once per (session, watermark). Recomputing per request would
        // be a correctness bug: a record added between chunk 3 and chunk 4 would renumber the
        // chunks and invalidate the aggregate hash the peer is verifying against.
        val h = harness()
        (1..600).forEach { h.store.seed(expense("local-%04d".format(it), updatedAt = 1_000L + it)) }
        h.handshakeAsTrustedPeer()

        val first = h.pull(chunkIndex = 0, since = 0L, chunkSize = SyncWireProtocol.MIN_CHUNK_SIZE)
        assertTrue("test needs several chunks", first.chunkCount > 1)

        h.store.seed(expense("added-mid-transfer", updatedAt = 9_999_999))
        val later = h.pull(chunkIndex = 1, since = 0L, chunkSize = SyncWireProtocol.MIN_CHUNK_SIZE)

        assertEquals("chunk count must not move mid-transfer", first.chunkCount, later.chunkCount)
        assertEquals(first.totalEntities, later.totalEntities)
        assertEquals(first.contentHash, later.contentHash)
    }

    @Test
    fun `a pull for a chunk that does not exist is refused`() {
        val h = harness()
        h.store.seed(expense("local-1", updatedAt = 900))
        h.handshakeAsTrustedPeer()

        val ack = h.pull(chunkIndex = 99, since = 0L)

        assertEquals(SyncError.SYNC_ERROR_PARSE_ERROR, ack.error)
    }

    @Test
    fun `a pull from an empty ledger answers cleanly`() {
        // An empty result is a legitimate outcome, not an error: the peer must be able to
        // finish the exchange rather than having to interpret a failure.
        val h = harness()
        h.handshakeAsTrustedPeer()

        val ack = h.pull(chunkIndex = 0, since = 0L)

        assertEquals(SyncError.SYNC_ERROR_OK, ack.error)
        assertEquals(0, ack.totalEntities)
        assertEquals(0, ack.chunkCount)
        assertTrue(ack.entitiesList.isEmpty())
    }

    @Test
    fun `a pull before authorisation is refused`() {
        val h = harness(authorizer = FakeAuthorizer(decision = SyncAuthorizer.Decision.ACCEPTED))
        h.store.seed(expense("secret", updatedAt = 900))
        h.hello()

        val ack = h.pull(chunkIndex = 0, since = 0L)

        assertEquals(SyncError.SYNC_ERROR_AUTH_REJECTED, ack.error)
        assertTrue("no data may leak before the user accepts", ack.entitiesList.isEmpty())
    }

    // ------------------------------------------------------------------ auth

    @Test
    fun `an untrusted peer needs the user to accept`() {
        val authorizer = FakeAuthorizer(decision = SyncAuthorizer.Decision.ACCEPTED)
        val h = harness(authorizer = authorizer)

        val helloAck = h.hello()
        assertTrue(helloAck.requiresUserConfirmation)
        assertEquals(SyncState.AUTHORIZING, h.session().machine.state)

        val authAck = h.auth()
        assertTrue(authAck.accepted)
        assertEquals(1, authorizer.confirmCalls)
        assertEquals(SyncState.EXCHANGING_MANIFEST, h.session().machine.state)
    }

    @Test
    fun `a user refusal fails the session`() {
        val h = harness(authorizer = FakeAuthorizer(decision = SyncAuthorizer.Decision.REJECTED))
        h.hello()

        val ack = h.auth()

        assertFalse(ack.accepted)
        assertEquals(SyncError.SYNC_ERROR_AUTH_REJECTED, ack.error)
        assertEquals(SyncState.FAILED, h.session().machine.state)
    }

    @Test
    fun `nobody answering is reported as a timeout, not a refusal`() {
        // The distinction matters to the peer: a timeout is worth retrying later because the
        // app was probably in the background, whereas a refusal is not.
        val h = harness(authorizer = FakeAuthorizer(decision = SyncAuthorizer.Decision.TIMED_OUT))
        h.hello()

        val ack = h.auth()

        assertEquals(SyncError.SYNC_ERROR_AUTH_TIMEOUT, ack.error)
        assertEquals(SyncState.FAILED, h.session().machine.state)
    }

    @Test
    fun `a replayed AUTH does not prompt the user a second time`() {
        val authorizer = FakeAuthorizer(decision = SyncAuthorizer.Decision.ACCEPTED)
        val h = harness(authorizer = authorizer)
        h.hello()

        assertTrue(h.auth().accepted)
        assertTrue("the replay must be answered the same way", h.auth().accepted)
        assertEquals("the dialog must not reappear", 1, authorizer.confirmCalls)
    }

    @Test
    fun `a successful sync remembers the peer`() {
        val authorizer = FakeAuthorizer(decision = SyncAuthorizer.Decision.ACCEPTED)
        val h = harness(authorizer = authorizer)
        h.hello()
        h.auth()

        val delta = h.deltaOf(expense("a", updatedAt = 1_000))
        h.pushManifest(delta)
        h.pushAllChunks(delta)
        h.commit(delta)

        assertTrue("next time must be quieter", authorizer.remembered.contains(peerDeviceId))
    }

    // --------------------------------------------------------------- pairing

    @Test
    fun `a correct pairing proof authenticates both directions`() {
        val code = "AB-12 cd"
        val authorizer = FakeAuthorizer(code = code, trusted = mutableSetOf(peerDeviceId))
        val h = harness(authorizer = authorizer)

        val helloAck = h.hello()
        val serverNonce = helloAck.serverNonce.toByteArray()
        assertTrue("the responder must publish a nonce", serverNonce.isNotEmpty())

        val clientNonce = ByteArray(SyncPairing.NONCE_SIZE) { it.toByte() }
        val authAck = h.auth(
            clientNonce = clientNonce,
            proof = SyncPairing.clientProof(code, clientNonce, serverNonce, h.sessionId)
        )

        assertTrue(authAck.accepted)
        assertTrue(
            "the counter-proof is what lets the initiator authenticate this device",
            SyncPairing.verifyServerProof(
                pairingCode = code,
                clientNonce = clientNonce,
                serverNonce = serverNonce,
                sessionId = h.sessionId,
                presented = authAck.proof.toByteArray()
            )
        )
        assertEquals(SyncState.EXCHANGING_MANIFEST, h.session().machine.state)
    }

    @Test
    fun `a wrong pairing proof is refused and the budget is finite`() {
        val code = "SECRET1"
        val h = harness(authorizer = FakeAuthorizer(code = code, trusted = mutableSetOf(peerDeviceId)))
        h.hello()

        val clientNonce = ByteArray(SyncPairing.NONCE_SIZE) { 7 }
        val wrong = ByteArray(SyncPairing.PROOF_SIZE) { 0 }

        repeat(SyncResponder.MAX_AUTH_ATTEMPTS - 1) {
            val ack = h.auth(clientNonce = clientNonce, proof = wrong)
            assertFalse(ack.accepted)
            assertEquals(SyncError.SYNC_ERROR_AUTH_REJECTED, ack.error)
        }

        // The last attempt drops the connection rather than answering, so a guesser pays a
        // full TCP handshake for every three tries.
        val dropped = h.sendRaw(
            SyncOpcode.AUTH,
            AuthPayload.newBuilder()
                .setDeviceId(peerDeviceId)
                .setClientNonce(ByteString.copyFrom(clientNonce))
                .setProof(ByteString.copyFrom(wrong))
                .build()
                .toByteArray()
        )
        assertNull("the budget must be enforced by closing the connection", dropped)
        assertTrue(h.store.rows.isEmpty())
    }

    @Test
    fun `pairing cannot be silently downgraded by a peer that omits the capability`() {
        // Fail closed. A security control that turns itself off when the peer says it cannot
        // comply is worse than not having one.
        val code = "SECRET1"
        val h = harness(authorizer = FakeAuthorizer(code = code, trusted = mutableSetOf(peerDeviceId)))

        val hello = HelloPayload.newBuilder()
            .setProtocolVersion(SyncWireProtocol.PROTOCOL_VERSION)
            .setMinSupportedVersion(SyncWireProtocol.MIN_SUPPORTED_VERSION)
            .setDeviceId(peerDeviceId)
            .setDeviceName("Downgrader")
            .setCapabilities(SyncCapability.SYNC_CAPABILITY_DELTA.number)
            .build()

        val ack = HelloAckPayload.parseFrom(h.send(SyncOpcode.HELLO, hello.toByteArray())!!)

        assertEquals(SyncError.SYNC_ERROR_AUTH_REJECTED, ack.error)
        assertEquals(0, h.registry.activeCount())
    }

    // ------------------------------------------------------------- admission

    @Test
    fun `a fifth concurrent peer is told to back off`() {
        // v1 used a plain `isSyncing` boolean that two threads could both read as false.
        // Admission is now decided under one lock and the refusal is retryable.
        val h = harness(registry = SyncSessionRegistry(maxConcurrentSessions = 2))

        assertEquals(SyncError.SYNC_ERROR_OK, h.hello(sessionId = 0x11L).error)
        assertEquals(SyncError.SYNC_ERROR_OK, h.hello(sessionId = 0x22L).error)
        val third = h.hello(sessionId = 0x33L)

        assertEquals(SyncError.SYNC_ERROR_BUSY, third.error)
        assertEquals(2, h.registry.activeCount())
    }

    @Test
    fun `two peers syncing at once do not mix data`() {
        // Both peers must already be trusted, otherwise HELLO parks them in AUTHORIZING and
        // this stops testing session isolation and starts testing the pairing prompt.
        val h = harness(
            registry = SyncSessionRegistry(maxConcurrentSessions = 4),
            authorizer = FakeAuthorizer(trusted = mutableSetOf("alice", "bob"))
        )

        val alice = h.peer(sessionId = 0xA1L, deviceId = "alice")
        val bob = h.peer(sessionId = 0xB2L, deviceId = "bob")
        alice.handshakeAsTrustedPeer()
        bob.handshakeAsTrustedPeer()

        val fromAlice = alice.deltaOf(expense("alice-1", amount = 1.0, updatedAt = 1_000))
        val fromBob = bob.deltaOf(expense("bob-1", amount = 2.0, updatedAt = 2_000))

        // Interleaved on purpose: the two sessions must not share a buffer.
        alice.pushManifest(fromAlice)
        bob.pushManifest(fromBob)
        alice.pushAllChunks(fromAlice)
        bob.pushAllChunks(fromBob)
        val bobAck = bob.commit(fromBob)
        val aliceAck = alice.commit(fromAlice)

        assertTrue(aliceAck.applied)
        assertTrue(bobAck.applied)
        assertEquals(1, aliceAck.insertedCount)
        assertEquals(1, bobAck.insertedCount)
        assertEquals(setOf("alice-1", "bob-1"), h.store.rows.keys)
    }

    @Test
    fun `an unknown opcode closes the connection instead of being ignored`() {
        val h = harness()
        h.handshakeAsTrustedPeer()

        assertNull(h.sendRaw(SyncOpcode.PONG, ByteArray(0)))
    }

    // ================================================================ harness

    private fun harness(
        authorizer: SyncAuthorizer = FakeAuthorizer(trusted = mutableSetOf(peerDeviceId)),
        registry: SyncSessionRegistry = SyncSessionRegistry(),
        store: FakeStore = FakeStore()
    ): Harness = Harness(store, registry, authorizer, localIdentity, peerAddress, peerDeviceId)

    /**
     * Drives one peer against a responder.
     *
     * Speaks only the public frame API, so nothing here can accidentally reach past the
     * transport boundary and set up state a real peer could not produce.
     */
    private class Harness(
        val store: FakeStore,
        val registry: SyncSessionRegistry,
        val authorizer: SyncAuthorizer,
        identity: SyncIdentity,
        private val peerAddress: String,
        private val peerDeviceId: String,
        val sessionId: Long = 0x0123456789ABCDEFL,
        val responder: SyncResponder = SyncResponder(
            store = store,
            identity = { identity },
            authorizer = authorizer,
            registry = registry,
            guard = IdempotencyGuard()
        )
    ) {
        private var seq = 0

        /** A second peer sharing this responder, for the concurrency cases. */
        fun peer(sessionId: Long, deviceId: String) = Harness(
            store = store,
            registry = registry,
            authorizer = authorizer,
            identity = SyncIdentity("local-device-id", "Local Phone"),
            peerAddress = "192.168.1.99:41001",
            peerDeviceId = deviceId,
            sessionId = sessionId,
            responder = responder
        )

        fun sendRaw(opcode: SyncOpcode, payload: ByteArray): ByteArray? =
            responder.handleFrame(peerAddress, opcode, sessionId, seq++, payload)

        fun send(opcode: SyncOpcode, payload: ByteArray): ByteArray? = sendRaw(opcode, payload)

        fun session(): SyncSession = requireNotNull(registry.find(sessionId)) { "session is gone" }

        // ---- frames

        fun helloPayload(capabilities: Int = ALL_CAPABILITIES): ByteArray =
            HelloPayload.newBuilder()
                .setProtocolVersion(SyncWireProtocol.PROTOCOL_VERSION)
                .setMinSupportedVersion(SyncWireProtocol.MIN_SUPPORTED_VERSION)
                .setDeviceId(peerDeviceId)
                .setDeviceName("Peer Phone")
                .setDeviceType("ANDROID")
                .setCapabilities(capabilities)
                .setTraceId("test-trace")
                .build()
                .toByteArray()

        fun hello(sessionId: Long = this.sessionId): HelloAckPayload =
            HelloAckPayload.parseFrom(
                responder.handleFrame(peerAddress, SyncOpcode.HELLO, sessionId, seq++, helloPayload())!!
            )

        fun auth(
            clientNonce: ByteArray = ByteArray(SyncPairing.NONCE_SIZE) { 1 },
            proof: ByteArray = ByteArray(0)
        ): AuthAckPayload = AuthAckPayload.parseFrom(
            sendRaw(
                SyncOpcode.AUTH,
                AuthPayload.newBuilder()
                    .setDeviceId(peerDeviceId)
                    .setClientNonce(ByteString.copyFrom(clientNonce))
                    .setProof(ByteString.copyFrom(proof))
                    .build()
                    .toByteArray()
            )!!
        )

        /** HELLO for a peer the user already trusts, which skips the AUTH phase entirely. */
        fun handshakeAsTrustedPeer() {
            val ack = hello()
            check(ack.error == SyncError.SYNC_ERROR_OK) { "handshake refused: ${ack.errorMessage}" }
            check(!ack.requiresUserConfirmation) { "peer was expected to be trusted" }
        }

        fun deltaOf(vararg rows: ExpenseEntity, chunkSize: Int = SyncWireProtocol.DEFAULT_CHUNK_SIZE) =
            DeltaBuilder(chunkSize).build(rows.toList())

        fun pushManifest(delta: DeltaSet): ManifestAckPayload =
            ManifestAckPayload.parseFrom(
                send(SyncOpcode.MANIFEST, delta.manifest(sessionId).toByteArray())!!
            )

        fun pushChunk(delta: DeltaSet, index: Int): ChunkAckPayload =
            ChunkAckPayload.parseFrom(
                send(SyncOpcode.CHUNK, delta.chunkPayload(index, sessionId).toByteArray())!!
            )

        fun pushAllChunks(delta: DeltaSet) {
            for (index in 0 until delta.chunkCount) pushChunk(delta, index)
        }

        fun commit(delta: DeltaSet): CommitAckPayload =
            CommitAckPayload.parseFrom(
                send(
                    SyncOpcode.COMMIT,
                    CommitPayload.newBuilder()
                        .setSessionId(sessionId)
                        .setExpectedEntities(delta.entityCount)
                        .setContentHash(ByteString.copyFrom(delta.contentHash))
                        .build()
                        .toByteArray()
                )!!
            )

        fun pull(chunkIndex: Int, since: Long, chunkSize: Int = 0): PullAckPayload =
            PullAckPayload.parseFrom(
                send(
                    SyncOpcode.PULL,
                    PullPayload.newBuilder()
                        .setSessionId(sessionId)
                        .setSinceWatermark(since)
                        .setChunkIndex(chunkIndex)
                        .setChunkSize(chunkSize)
                        .build()
                        .toByteArray()
                )!!
            )

        companion object {
            val ALL_CAPABILITIES = SyncCapability.SYNC_CAPABILITY_RESUME.number or
                    SyncCapability.SYNC_CAPABILITY_DELTA.number or
                    SyncCapability.SYNC_CAPABILITY_PAIRING.number
        }
    }

    /** In-memory [SyncEntityStore]. Tombstone-aware, exactly like the Room implementation. */
    private class FakeStore : SyncEntityStore {
        val rows = LinkedHashMap<String, ExpenseEntity>()
        var writeCalls = 0
        var failNextWrite = false

        fun seed(vararg entities: ExpenseEntity) {
            entities.forEach { rows[it.id] = it }
        }

        override suspend fun load(entityId: String): ExpenseEntity? = rows[entityId]

        override suspend fun writeAll(rows: List<ExpenseEntity>) {
            if (failNextWrite) {
                failNextWrite = false
                throw IllegalStateException("simulated write failure")
            }
            writeCalls++
            rows.forEach { this.rows[it.id] = it }
        }

        override suspend fun changedSince(watermark: Long): List<ExpenseEntity> =
            if (watermark <= 0L) snapshot() else snapshot().filter { it.updatedAt > watermark }

        override suspend fun snapshot(): List<ExpenseEntity> =
            rows.values.sortedWith(compareBy({ it.updatedAt }, { it.id }))
    }

    private class FakeAuthorizer(
        private val code: String? = null,
        private val trusted: MutableSet<String> = mutableSetOf(),
        private val decision: SyncAuthorizer.Decision = SyncAuthorizer.Decision.REJECTED
    ) : SyncAuthorizer {
        var confirmCalls = 0
        val remembered = mutableListOf<String>()

        override fun pairingCode(): String? = code
        override fun isTrusted(deviceId: String): Boolean = deviceId in trusted

        override fun confirm(request: SyncAuthorizer.Request, timeoutMs: Long): SyncAuthorizer.Decision {
            confirmCalls++
            return decision
        }

        override fun remember(deviceId: String, deviceName: String) {
            remembered += deviceId
            trusted += deviceId
        }
    }

    private fun expense(
        id: String,
        amount: Double = 12.5,
        updatedAt: Long = 1_700_000_000_000L,
        version: Int = 1,
        deletedAt: Long? = null,
        remark: String? = "lunch",
        type: String = "FOOD",
        date: String = "2026-08-01"
    ) = ExpenseEntity(
        id = id,
        type = type,
        remark = remark,
        amount = amount,
        date = date,
        version = version,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        isSynced = false
    )
}
