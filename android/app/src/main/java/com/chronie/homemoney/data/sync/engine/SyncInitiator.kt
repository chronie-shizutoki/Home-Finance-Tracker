package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.sync.auth.SyncAuthorizer
import com.chronie.homemoney.data.sync.auth.SyncPairing
import com.chronie.homemoney.data.sync.generated.AuthAckPayload
import com.chronie.homemoney.data.sync.generated.AuthPayload
import com.chronie.homemoney.data.sync.generated.ChunkAckPayload
import com.chronie.homemoney.data.sync.generated.CommitAckPayload
import com.chronie.homemoney.data.sync.generated.CommitPayload
import com.chronie.homemoney.data.sync.generated.ConflictSummary
import com.chronie.homemoney.data.sync.generated.HelloAckPayload
import com.chronie.homemoney.data.sync.generated.HelloPayload
import com.chronie.homemoney.data.sync.generated.ManifestAckPayload
import com.chronie.homemoney.data.sync.generated.PullAckPayload
import com.chronie.homemoney.data.sync.generated.PullPayload
import com.chronie.homemoney.data.sync.generated.SyncCapability
import com.chronie.homemoney.data.sync.generated.SyncEntityV2
import com.chronie.homemoney.data.sync.generated.SyncError
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol
import com.chronie.homemoney.data.sync.session.SyncEvent
import com.chronie.homemoney.data.sync.session.SyncSessionStateMachine
import com.chronie.homemoney.data.sync.session.SyncState
import com.chronie.homemoney.data.sync.transport.SyncTransport
import com.chronie.homemoney.data.sync.transport.TransportReply
import com.google.protobuf.ByteString
import java.security.SecureRandom

/**
 * The client half of the v2 LAN sync protocol.
 *
 * This is the half that was missing. The refactored native transport only ever emitted a
 * bare COMMIT frame from the initiator, so the responder's [SyncResponder] - which requires
 * the full HELLO / AUTH / MANIFEST / CHUNK(s) / COMMIT handshake and then serves the reverse
 * direction on PULL - never saw a session and never showed its confirmation dialog. The
 * symptom was exactly what the user reported: B does nothing, A shows "10% / 100% sync
 * failed".
 *
 * The handshake lives here, in Kotlin, because that is where the protobuf schema and the
 * session state machine live. Native is reduced to moving frames over one long-lived socket
 * ([com.chronie.homemoney.data.sync.transport.NativeSyncTransport]); it never interprets an
 * opcode.
 *
 * Two things make the result converge with the peer rather than merely transfer bytes:
 *  - the push leg builds a [DeltaBuilder] delta and verifies the responder applied every
 *    entity via the aggregate content hash in COMMIT_ACK;
 *  - the pull leg asks the responder for its own delta chunk by chunk and applies it locally
 *    with the same [EntityApplier] the responder uses, so both devices end up holding both
 *    halves.
 */
class SyncInitiator(
    private val store: SyncEntityStore,
    private val identity: () -> SyncIdentity,
    private val guard: IdempotencyGuard = IdempotencyGuard(),
    private val authorizer: SyncAuthorizer = SyncAuthorizer.DENY_ALL,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Runs the full handshake against [transport] and applies the peer's delta locally.
     *
     * @param peerAddress diagnostics label, `ip:port`. The peer's real identity comes from
     *   HELLO_ACK, not from here.
     * @param onProgress called with a 0..1 value and a message as each phase completes, so
     *   the UI can show the same progress the responder reports.
     * @return an [InitiatorOutcome] the caller maps onto its own result type.
     */
    suspend fun sync(
        transport: SyncTransport,
        peerAddress: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): InitiatorOutcome {
        val sessionId = newSessionId()
        val machine = SyncSessionStateMachine()
        machine.dispatch(SyncEvent.Start)
        var seq = 1

        fun exchange(opcode: SyncOpcode, payload: ByteArray, timeoutMs: Int): Step<ByteArray> {
            val reply = transport.exchange(opcode, sessionId, seq++, payload, timeoutMs)
            return unwrap(reply, opcode.ackOpcode())
        }

        try {
            // ---------------------------------------------------------- 1. HELLO
            onProgress(machine.state.progress, "Negotiating...")
            val self = identity()
            val hello = HelloPayload.newBuilder()
                .setProtocolVersion(SyncWireProtocol.PROTOCOL_VERSION)
                .setMinSupportedVersion(SyncWireProtocol.MIN_SUPPORTED_VERSION)
                .setDeviceId(self.deviceId)
                .setDeviceName(self.deviceName)
                .setDeviceType(self.deviceType)
                .setCapabilities(localCapabilities())
                .setTraceId(sessionId.hex())
                .build()

            val helloAck = when (
                val r = exchange(SyncOpcode.HELLO, hello.toByteArray(), CONNECT_EXCHANGE_TIMEOUT_MS)
                    .parse { HelloAckPayload.parseFrom(it) }
            ) {
                is Step.Err -> return fail(r.code, r.message)
                is Step.Ok -> {
                    if (r.value.error != SyncError.SYNC_ERROR_OK) {
                        return fail(SyncErrorMapping.fromProto(r.value.error), r.value.errorMessage)
                    }
                    r.value
                }
            }

            val peerDeviceId = helloAck.deviceId
            val peerDeviceName = helloAck.deviceName
            onProgress(machine.state.progress, "Connected to $peerDeviceName")

            // ---------------------------------------------------------- 2. AUTH (if required)
            if (helloAck.requiresUserConfirmation) {
                machine.dispatch(SyncEvent.AuthorizationRequired)
                onProgress(machine.state.progress, "Authenticating...")
                val auth = buildAuth(authorizer.pairingCode(), helloAck.serverNonce.toByteArray(), sessionId)
                val authAck = when (
                    val r = exchange(SyncOpcode.AUTH, auth.toByteArray(), AUTH_EXCHANGE_TIMEOUT_MS)
                        .parse { AuthAckPayload.parseFrom(it) }
                ) {
                    is Step.Err -> return fail(r.code, r.message)
                    is Step.Ok -> {
                        if (!r.value.accepted) {
                            return fail(SyncErrorMapping.fromProto(r.value.error), r.value.errorMessage)
                        }
                        r.value
                    }
                }
                // Verify the responder's counter-proof so a rogue device on the Wi-Fi cannot
                // impersonate the peer and harvest the ledger.
                val code = authorizer.pairingCode()
                if (SyncPairing.isUsableCode(code) && authAck.proof.size() > 0) {
                    val clientNonce = auth.clientNonce.toByteArray()
                    if (!SyncPairing.verifyServerProof(
                            pairingCode = code!!,
                            clientNonce = clientNonce,
                            serverNonce = helloAck.serverNonce.toByteArray(),
                            sessionId = sessionId,
                            presented = authAck.proof.toByteArray()
                        )
                    ) {
                        return fail(SyncErrorCode.AUTH_REJECTED, "peer pairing proof mismatch")
                    }
                }
                machine.dispatch(SyncEvent.AuthorizationGranted)
            } else {
                machine.dispatch(SyncEvent.HandshakeAccepted(SyncWireProtocol.PROTOCOL_VERSION))
            }

            // ---------------------------------------------------------- 3. MANIFEST (push)
            onProgress(SyncState.EXCHANGING_MANIFEST.progress, "Exchanging manifest...")
            val localRows = store.snapshot()
            val delta = DeltaBuilder().build(localRows, 0L)
            val manifestAck = when (
                val r = exchange(SyncOpcode.MANIFEST, delta.manifest(sessionId).toByteArray(), IO_EXCHANGE_TIMEOUT_MS)
                    .parse { ManifestAckPayload.parseFrom(it) }
            ) {
                is Step.Err -> return fail(r.code, r.message)
                is Step.Ok -> {
                    if (!r.value.accepted) {
                        return fail(SyncErrorMapping.fromProto(r.value.error), r.value.errorMessage)
                    }
                    r.value
                }
            }
            machine.dispatch(
                SyncEvent.ManifestAgreed(
                    totalChunks = delta.chunkCount,
                    resumeFromChunk = manifestAck.resumeFromChunk,
                    chunkSize = manifestAck.chunkSize
                )
            )

            // ---------------------------------------------------------- 4. CHUNK(s) (push)
            var acked = manifestAck.resumeFromChunk
            var rounds = 0
            while (acked < delta.chunkCount - 1 && rounds < MAX_TRANSFER_ROUNDS) {
                rounds++
                for (index in (acked + 1) until delta.chunkCount) {
                    val chunkAck = when (
                        val r = exchange(
                            SyncOpcode.CHUNK,
                            delta.chunkPayload(index, sessionId).toByteArray(),
                            IO_EXCHANGE_TIMEOUT_MS
                        ).parse { ChunkAckPayload.parseFrom(it) }
                    ) {
                        is Step.Err -> return fail(r.code, r.message)
                        is Step.Ok -> {
                            if (r.value.error != SyncError.SYNC_ERROR_OK) {
                                return fail(SyncErrorMapping.fromProto(r.value.error), r.value.errorMessage)
                            }
                            r.value
                        }
                    }
                    acked = maxOf(acked, chunkAck.ackedThroughChunk)
                    val shown = minOf(acked + 1, delta.chunkCount)
                    onProgress(
                        SyncState.TRANSFERRING.progress,
                        "Sending data... ($shown/${delta.chunkCount})"
                    )
                }
            }
            if (acked < delta.chunkCount - 1) {
                return fail(
                    SyncErrorCode.IO_TIMEOUT,
                    "push incomplete after $rounds rounds (acked $acked/${delta.chunkCount - 1})"
                )
            }

            // ---------------------------------------------------------- 5. COMMIT (push)
            onProgress(SyncState.COMMITTING.progress, "Committing...")
            val commit = CommitPayload.newBuilder()
                .setSessionId(sessionId)
                .setExpectedEntities(delta.entityCount)
                .setContentHash(ByteString.copyFrom(delta.contentHash))
                .build()
            val commitAck = when (
                val r = exchange(SyncOpcode.COMMIT, commit.toByteArray(), IO_EXCHANGE_TIMEOUT_MS)
                    .parse { CommitAckPayload.parseFrom(it) }
            ) {
                is Step.Err -> return fail(r.code, r.message)
                is Step.Ok -> {
                    if (!r.value.applied) {
                        return fail(SyncErrorMapping.fromProto(r.value.error), r.value.errorMessage)
                    }
                    r.value
                }
            }
            machine.dispatch(SyncEvent.TransferComplete)
            machine.dispatch(
                SyncEvent.CommitAccepted(
                    inserted = commitAck.insertedCount,
                    updated = commitAck.updatedCount,
                    skipped = commitAck.skippedCount
                )
            )

            // ---------------------------------------------------------- 6. PULL (reverse)
            onProgress(SyncState.TRANSFERRING.progress, "Receiving peer data...")

            // We have no stored watermark for this peer, so PULL asks for everything - which
            // means the delta we just pushed comes straight back inside the answer. Applying
            // it would not corrupt anything (the merge is deterministic and would decide to
            // keep what is already there), but it would hand our own revisions to
            // [EntityApplier] labeled with the *peer's* device id, and that id is the
            // tie-break when two revisions share an `updatedAt`. Recognizing the echo by its
            // exact revision - id, timestamp and fingerprint - drops it before it can reach
            // the merger, and keeps the reported download count honest.
            val pushedRevisions = HashSet<Triple<String, Long, Int>>(delta.entityCount * 2)
            delta.entities.mapTo(pushedRevisions) {
                Triple(it.entityId, it.updatedAt, it.entityHash)
            }

            val pulled = mutableListOf<SyncEntityV2>()
            var pullIndex = 0
            var pullChunks = 1
            while (pullIndex < pullChunks) {
                val pull = PullPayload.newBuilder()
                    .setSessionId(sessionId)
                    .setSinceWatermark(0L)
                    .setChunkIndex(pullIndex)
                    .setChunkSize(SyncWireProtocol.DEFAULT_CHUNK_SIZE)
                    .build()
                when (
                    val r = exchange(SyncOpcode.PULL, pull.toByteArray(), IO_EXCHANGE_TIMEOUT_MS)
                        .parse { PullAckPayload.parseFrom(it) }
                ) {
                    is Step.Err -> return fail(r.code, r.message)
                    is Step.Ok -> {
                        if (r.value.error != SyncError.SYNC_ERROR_OK) {
                            return fail(SyncErrorMapping.fromProto(r.value.error), r.value.errorMessage)
                        }
                        pullChunks = r.value.chunkCount
                        r.value.entitiesList.filterTo(pulled) {
                            Triple(it.entityId, it.updatedAt, it.entityHash) !in pushedRevisions
                        }
                    }
                }
                pullIndex++
            }
            val pullReport = EntityApplier(store, guard, identity().deviceId).apply(pulled, peerDeviceId)

            onProgress(SyncState.COMPLETED.progress, "Sync complete")
            return InitiatorOutcome(
                success = true,
                uploadedEntities = delta.entityCount,
                downloadedEntities = pulled.size,
                inserted = pullReport.inserted,
                updated = pullReport.updated,
                skipped = pullReport.skipped,
                conflicts = pullReport.conflicts,
                peerDeviceId = peerDeviceId,
                peerDeviceName = peerDeviceName
            )
        } catch (e: Exception) {
            machine.fail(SyncErrorCode.INTERNAL)
            return fail(SyncErrorCode.INTERNAL, e.message ?: e.javaClass.simpleName)
        } finally {
            transport.close()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun unwrap(reply: TransportReply?, expectedAck: SyncOpcode): Step<ByteArray> {
        if (reply == null) {
            return Step.Err(SyncErrorCode.PEER_CLOSED, "transport closed before $expectedAck")
        }
        if (reply.opcode == SyncOpcode.ERROR) {
            val code = decodeErrorCode(reply.payload)
            return Step.Err(code, "peer reported ${code.name} during $expectedAck")
        }
        if (reply.opcode != expectedAck) {
            return Step.Err(
                SyncErrorCode.PROTOCOL_MISMATCH,
                "expected $expectedAck, got ${reply.opcode} for $expectedAck"
            )
        }
        return Step.Ok(reply.payload)
    }

    private inline fun <T> Step<ByteArray>.parse(block: (ByteArray) -> T): Step<T> = when (this) {
        is Step.Err -> this
        is Step.Ok -> try {
            Step.Ok(block(this.value))
        } catch (e: Exception) {
            Step.Err(SyncErrorCode.PARSE_ERROR, e.message ?: "could not parse reply")
        }
    }

    /** Native reports a transport failure as a 4-byte big-endian [SyncErrorCode]. */
    private fun decodeErrorCode(payload: ByteArray): SyncErrorCode {
        if (payload.size < 4) return SyncErrorCode.INTERNAL
        val code = ((payload[0].toInt() and 0xFF) shl 24) or
            ((payload[1].toInt() and 0xFF) shl 16) or
            ((payload[2].toInt() and 0xFF) shl 8) or
            (payload[3].toInt() and 0xFF)
        return SyncErrorCode.fromCode(code)
    }

    private fun localCapabilities(): Int {
        var caps = SyncCapability.SYNC_CAPABILITY_RESUME.number or
            SyncCapability.SYNC_CAPABILITY_DELTA.number
        if (SyncPairing.isUsableCode(authorizer.pairingCode())) {
            caps = caps or SyncCapability.SYNC_CAPABILITY_PAIRING.number
        }
        return caps
    }

    private fun buildAuth(code: String?, serverNonce: ByteArray, sessionId: Long): AuthPayload {
        val b = AuthPayload.newBuilder().setDeviceId(identity().deviceId)
        if (SyncPairing.isUsableCode(code)) {
            val clientNonce = SyncPairing.newNonce()
            val proof = SyncPairing.clientProof(code!!, clientNonce, serverNonce, sessionId)
            b.setClientNonce(ByteString.copyFrom(clientNonce)).setProof(ByteString.copyFrom(proof))
        }
        return b.build()
    }

    private fun newSessionId(): Long {
        val rng = SecureRandom()
        var id: Long
        do {
            id = rng.nextLong()
        } while (id == 0L)
        return id
    }

    private fun Long.hex(): String = "%016X".format(this)

    private fun fail(code: SyncErrorCode, message: String): InitiatorOutcome =
        InitiatorOutcome(success = false, errorCode = code, errorMessage = message)

    companion object {
        /** HELLO is the first round trip; a little slack over the connect timeout. */
        const val CONNECT_EXCHANGE_TIMEOUT_MS = 15_000

        /** MANIFEST / CHUNK / COMMIT / PULL all move data or wait on the local store. */
        const val IO_EXCHANGE_TIMEOUT_MS = 20_000

        /** The peer's user may take a while to tap "accept"; must sit well inside the native
         *  handler deadline (150 s) so a timeout reaches us as a typed error, not a dead socket. */
        const val AUTH_EXCHANGE_TIMEOUT_MS = 120_000

        /** Upper bound on re-sending chunks when acks are lost, before we give up. */
        const val MAX_TRANSFER_ROUNDS = 8
    }
}

/**
 * The structured result of one [SyncInitiator.sync].
 *
 * Kept in the engine package so the protocol layer stays free of the domain model; the
 * caller (e.g. [com.chronie.homemoney.data.sync.LanDeviceSyncManager]) maps it onto whatever
 * result type the UI expects.
 */
data class InitiatorOutcome(
    val success: Boolean,
    val uploadedEntities: Int = 0,
    val downloadedEntities: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val conflicts: List<ConflictSummary> = emptyList(),
    val errorCode: SyncErrorCode? = null,
    val errorMessage: String? = null,
    val peerDeviceId: String? = null,
    val peerDeviceName: String? = null
)

/** Either the parsed reply payload or the reason the exchange could not complete. */
private sealed interface Step<out T> {
    data class Ok<out T>(val value: T) : Step<T>
    data class Err(val code: SyncErrorCode, val message: String) : Step<Nothing>
}
