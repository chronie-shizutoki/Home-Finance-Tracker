package com.chronie.homemoney.data.sync.engine

import android.util.Log
import com.chronie.homemoney.data.sync.auth.SyncAuthorizer
import com.chronie.homemoney.data.sync.auth.SyncPairing
import com.chronie.homemoney.data.sync.generated.AuthAckPayload
import com.chronie.homemoney.data.sync.generated.AuthPayload
import com.chronie.homemoney.data.sync.generated.ChunkAckPayload
import com.chronie.homemoney.data.sync.generated.ChunkPayload
import com.chronie.homemoney.data.sync.generated.CommitAckPayload
import com.chronie.homemoney.data.sync.generated.CommitPayload
import com.chronie.homemoney.data.sync.generated.HelloAckPayload
import com.chronie.homemoney.data.sync.generated.HelloPayload
import com.chronie.homemoney.data.sync.generated.ManifestAckPayload
import com.chronie.homemoney.data.sync.generated.ManifestPayload
import com.chronie.homemoney.data.sync.generated.PullAckPayload
import com.chronie.homemoney.data.sync.generated.PullPayload
import com.chronie.homemoney.data.sync.generated.SyncCapability
import com.chronie.homemoney.data.sync.generated.SyncEntityV2
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol
import com.chronie.homemoney.data.sync.session.SyncEvent
import com.chronie.homemoney.data.sync.session.SyncSession
import com.chronie.homemoney.data.sync.session.SyncSessionRegistry
import com.chronie.homemoney.data.sync.session.SyncState
import com.chronie.homemoney.data.sync.transport.SyncFrameHandler
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.runBlocking

/**
 * The server half of the v2 protocol: turns inbound frames into database effects.
 *
 * ### What it replaces
 *
 * v1's responder was the anonymous listener inside `LanDeviceSyncManager`. In one callback it
 * parsed a blob, showed a dialog, applied every record and returned the entire local database
 * as the answer. There was no session, no phase, no integrity check and no way to resume, so
 * any interruption meant starting over and any failure reported itself as `null` - which the
 * transport turned into a closed socket and the user saw as "sync failed".
 *
 * Here each frame is a small, checked step against explicit session state.
 *
 * ### Frame map
 *
 * Only six opcodes ever reach this class; native answers PING itself, intercepts BYE and
 * refuses anything that is not in `requiresUpperLayer`.
 *
 * ```
 *   HELLO    -> HELLO_ACK     negotiate version, open the session, state the auth policy
 *   AUTH     -> AUTH_ACK      verify the pairing proof, ask the user
 *   MANIFEST -> MANIFEST_ACK  accept the transfer plan, report the resume checkpoint
 *   CHUNK    -> CHUNK_ACK     buffer one chunk, report the cumulative checkpoint
 *   PULL     -> PULL_ACK      serve one chunk of this device's own delta
 *   COMMIT   -> COMMIT_ACK    verify the set, apply it in one transaction, report conflicts
 * ```
 *
 * ### Two invariants worth stating
 *
 *  1. **Every reply carries a reason.** A refusal is an ack with an error code, never a
 *     silent null, because null costs the peer its connection and tells it nothing. null is
 *     reserved for the cases where continuing would be unsafe - an unparseable session, a
 *     brute-force attempt, an internal fault.
 *  2. **Every handler is idempotent.** Retransmission is now normal traffic, so a repeated
 *     frame must produce the same answer rather than a second effect. That is enforced at
 *     three levels: the session dedupes chunks, [IdempotencyGuard] dedupes entity revisions
 *     and whole commits, and each handler tolerates being called again in the state its own
 *     previous run left behind.
 */
class SyncResponder(
    private val store: SyncEntityStore,
    private val identity: SyncIdentity,
    private val authorizer: SyncAuthorizer = SyncAuthorizer.DENY_ALL,
    private val registry: SyncSessionRegistry = SyncSessionRegistry(),
    private val guard: IdempotencyGuard = IdempotencyGuard(),
    private val observer: SyncResponderObserver = SyncResponderObserver.NONE,
    private val clock: () -> Long = System::currentTimeMillis
) : SyncFrameHandler {

    private val applier = EntityApplier(store, guard, identity.deviceId)

    /** Live sessions, for diagnostics and for the manager's shutdown path. */
    val sessions: SyncSessionRegistry get() = registry

    override fun handleFrame(
        peerAddress: String,
        opcode: SyncOpcode,
        sessionId: Long,
        seq: Int,
        payload: ByteArray
    ): ByteArray? {
        // Recorded for observability only. A duplicate is *not* skipped: the peer resent
        // because it never saw the answer, so what it needs is the answer again. Every
        // handler below is idempotent precisely so that recomputing it is safe.
        if (!guard.acceptFrame(sessionId, seq)) {
            Log.d(TAG, "duplicate frame op=$opcode seq=$seq session=${sessionId.hex()} from $peerAddress")
        }

        return try {
            when (opcode) {
                SyncOpcode.HELLO -> onHello(peerAddress, sessionId, payload)
                SyncOpcode.AUTH -> onAuth(peerAddress, sessionId, payload)
                SyncOpcode.MANIFEST -> onManifest(peerAddress, sessionId, payload)
                SyncOpcode.CHUNK -> onChunk(peerAddress, sessionId, payload)
                SyncOpcode.PULL -> onPull(peerAddress, sessionId, payload)
                SyncOpcode.COMMIT -> onCommit(peerAddress, sessionId, payload)
                else -> {
                    // Unreachable while the two ends agree on requiresUpperLayer; reaching
                    // it means they have drifted, which is worth a loud log rather than a
                    // shrug.
                    Log.w(TAG, "opcode $opcode was routed to the upper layer but has no handler")
                    observer.onRejected(peerAddress, opcode, SyncErrorCode.UNKNOWN_OPCODE, "no handler")
                    null
                }
            }
        } catch (t: Throwable) {
            // Deliberately broad. An exception escaping into JNI is logged by native and the
            // connection dies without a reason; catching everything here keeps the failure
            // inside Kotlin where it can be logged with context. Rethrowing would buy
            // nothing - there is no caller above this that could handle it.
            Log.e(TAG, "unhandled failure while serving $opcode from $peerAddress", t)
            observer.onRejected(peerAddress, opcode, SyncErrorCode.INTERNAL, t.toString())
            null
        }
    }

    /** Retires every session. Called when the server stops. */
    fun shutdown() {
        registry.snapshot().forEach { observer.onSessionFinished(it, it.machine.state) }
        registry.clear()
    }

    // ------------------------------------------------------------------ hello

    private fun onHello(peerAddress: String, sessionId: Long, payload: ByteArray): ByteArray {
        val hello = try {
            HelloPayload.parseFrom(payload)
        } catch (e: InvalidProtocolBufferException) {
            return helloError(peerAddress, SyncErrorCode.PARSE_ERROR, "malformed HELLO: ${e.message}")
        }

        // The initiator owns the session id because native rebuilds every reply header from
        // the request header; see the note on HelloAckPayload.session_id. A zero id is what
        // an uninitialised header looks like and would merge unrelated peers into one
        // session, so it is refused rather than repaired.
        if (sessionId == 0L) {
            return helloError(
                peerAddress,
                SyncErrorCode.PROTOCOL_MISMATCH,
                "initiator must generate a non-zero session id"
            )
        }

        val negotiated = negotiateVersion(hello) ?: return helloError(
            peerAddress,
            SyncErrorCode.PROTOCOL_MISMATCH,
            "peer speaks ${hello.protocolVersion} (min ${hello.minSupportedVersion}); " +
                    "this build supports ${SyncWireProtocol.MIN_SUPPORTED_VERSION}.." +
                    "${SyncWireProtocol.PROTOCOL_VERSION}"
        )

        val pairingCode = authorizer.pairingCode()
        val pairingConfigured = SyncPairing.isUsableCode(pairingCode)
        val peerSupportsPairing = hasCapability(hello.capabilities, SyncCapability.SYNC_CAPABILITY_PAIRING)
        // Fail closed. If the user has set a pairing code, a peer that cannot present a proof
        // is refused instead of being waved through - a security control that silently
        // downgrades itself is worse than not having one.
        if (pairingConfigured && !peerSupportsPairing) {
            return helloError(
                peerAddress,
                SyncErrorCode.AUTH_REJECTED,
                "pairing is required here but the peer does not support it"
            )
        }

        val traceId = hello.traceId.ifEmpty { sessionId.hex() }
        val acquisition = registry.acquire(sessionId, peerAddress, traceId)
        if (acquisition is SyncSessionRegistry.Acquisition.Busy) {
            return helloError(
                peerAddress,
                SyncErrorCode.BUSY,
                "already serving ${acquisition.activeSessions} sessions"
            )
        }
        val session = acquisition.sessionOrNull
            ?: return helloError(peerAddress, SyncErrorCode.INTERNAL, "session unavailable")
        if (acquisition is SyncSessionRegistry.Acquisition.Opened) {
            observer.onSessionOpened(session)
        }

        return session.serialized {
            session.touch(clock())
            session.recordPeerIdentity(
                deviceId = hello.deviceId,
                deviceName = hello.deviceName,
                deviceType = hello.deviceType,
                capabilities = hello.capabilities,
                negotiatedVersion = negotiated
            )

            // A session that already cleared authorisation is being resumed after the socket
            // dropped. Re-prompting there would be the single most annoying possible
            // behaviour on a weak link, and re-running the proof would be pointless: the
            // peer's identity was established when the session was opened and the session id
            // is bound into the proof.
            val alreadyAuthorized = session.authorized
            val needsPrompt = !alreadyAuthorized && !authorizer.isTrusted(hello.deviceId)
            val needsPairing = !alreadyAuthorized && pairingConfigured
            val needsAuthPhase = needsPrompt || needsPairing

            if (needsPairing && session.serverNonce.isEmpty()) {
                // Generated once per session. Regenerating it on a replayed HELLO would
                // invalidate a proof the peer has already computed.
                session.recordServerNonce(SyncPairing.newNonce())
            }

            if (session.machine.state == SyncState.IDLE) {
                session.machine.dispatch(SyncEvent.Start)
                if (needsAuthPhase) {
                    session.machine.dispatch(SyncEvent.AuthorizationRequired)
                } else {
                    session.markAuthorized()
                    session.machine.dispatch(SyncEvent.HandshakeAccepted(negotiated))
                }
                observer.onPhaseChanged(session, session.machine.state)
            }

            Log.i(
                TAG,
                "hello from ${hello.deviceName} (${hello.deviceId}) v$negotiated " +
                        "auth=${if (needsAuthPhase) "required" else "skipped"} ${session.describe()}"
            )

            HelloAckPayload.newBuilder()
                .setNegotiatedVersion(negotiated)
                .setDeviceId(identity.deviceId)
                .setDeviceName(identity.deviceName)
                .setCapabilities(LOCAL_CAPABILITIES)
                .setSessionId(sessionId)
                .setRequiresUserConfirmation(needsAuthPhase)
                .setServerNonce(ByteString.copyFrom(session.serverNonce))
                .build()
                .toByteArray()
        }
    }

    /**
     * Highest version both ends can speak, or null when the ranges do not overlap.
     *
     * A peer that omits `min_supported_version` is read as supporting only what it proposed,
     * which is the safe interpretation: assuming it could fall back further would let this
     * device pick a version the peer cannot actually parse.
     */
    private fun negotiateVersion(hello: HelloPayload): Int? {
        val peerVersion = hello.protocolVersion
        val peerMin = if (hello.minSupportedVersion == 0) peerVersion else hello.minSupportedVersion
        val negotiated = minOf(peerVersion, SyncWireProtocol.PROTOCOL_VERSION)
        val floor = maxOf(peerMin, SyncWireProtocol.MIN_SUPPORTED_VERSION)
        return if (negotiated >= floor) negotiated else null
    }

    // ------------------------------------------------------------------- auth

    private fun onAuth(peerAddress: String, sessionId: Long, payload: ByteArray): ByteArray? {
        val auth = try {
            AuthPayload.parseFrom(payload)
        } catch (e: InvalidProtocolBufferException) {
            return authError(peerAddress, SyncErrorCode.PARSE_ERROR, "malformed AUTH: ${e.message}")
        }

        val session = registry.find(sessionId)
            ?: return authError(peerAddress, SyncErrorCode.PROTOCOL_MISMATCH, "unknown session")

        return session.serialized {
            session.touch(clock())

            // Replayed AUTH after a lost AUTH_ACK. Recompute the same answer; do not prompt
            // the user a second time.
            if (session.authorized) {
                return@serialized authAccepted(session, auth)
            }

            if (session.machine.state != SyncState.AUTHORIZING) {
                return@serialized authError(
                    peerAddress,
                    SyncErrorCode.PROTOCOL_MISMATCH,
                    "AUTH arrived in ${session.machine.state}"
                )
            }

            val pairingCode = authorizer.pairingCode()
            if (SyncPairing.isUsableCode(pairingCode)) {
                val clientNonce = auth.clientNonce.toByteArray()
                if (clientNonce.isEmpty() || session.serverNonce.isEmpty()) {
                    return@serialized rejectAuthAttempt(
                        peerAddress, session, "missing nonce in pairing exchange"
                    )
                }
                val valid = SyncPairing.verifyClientProof(
                    pairingCode = pairingCode!!,
                    clientNonce = clientNonce,
                    serverNonce = session.serverNonce,
                    sessionId = sessionId,
                    presented = auth.proof.toByteArray()
                )
                if (!valid) {
                    return@serialized rejectAuthAttempt(peerAddress, session, "pairing proof rejected")
                }
            }

            if (!authorizer.isTrusted(session.peerDeviceId)) {
                val request = SyncAuthorizer.Request(
                    deviceId = session.peerDeviceId,
                    deviceName = session.peerDeviceName,
                    peerAddress = peerAddress,
                    trustedBefore = false
                )
                when (authorizer.confirm(request, AUTH_PROMPT_TIMEOUT_MS)) {
                    SyncAuthorizer.Decision.ACCEPTED -> Unit

                    SyncAuthorizer.Decision.REJECTED -> {
                        failSession(session, SyncErrorCode.AUTH_REJECTED)
                        observer.onRejected(
                            peerAddress, SyncOpcode.AUTH, SyncErrorCode.AUTH_REJECTED, "declined by user"
                        )
                        return@serialized authAckError(SyncErrorCode.AUTH_REJECTED, "declined by user")
                    }

                    SyncAuthorizer.Decision.TIMED_OUT -> {
                        // Not a refusal: most often the app simply was not in the foreground.
                        // The distinct code lets the peer retry later instead of giving up.
                        failSession(session, SyncErrorCode.AUTH_TIMEOUT)
                        observer.onRejected(
                            peerAddress, SyncOpcode.AUTH, SyncErrorCode.AUTH_TIMEOUT, "no answer"
                        )
                        return@serialized authAckError(SyncErrorCode.AUTH_TIMEOUT, "nobody answered")
                    }
                }
            }

            session.markAuthorized()
            session.machine.dispatch(SyncEvent.AuthorizationGranted)
            observer.onPhaseChanged(session, session.machine.state)
            Log.i(TAG, "authorised ${session.describe()}")
            authAccepted(session, auth)
        }
    }

    /**
     * Counts one failed attempt and answers, or kills the connection once the budget is gone.
     *
     * The budget matters because a pairing code is short enough to be guessed if a peer may
     * try indefinitely. Returning null on the last attempt drops the connection, so a
     * guesser pays a full TCP handshake plus a HELLO round trip per three guesses.
     */
    private fun rejectAuthAttempt(
        peerAddress: String,
        session: SyncSession,
        detail: String
    ): ByteArray? {
        val attempts = session.recordFailedAuth()
        observer.onRejected(peerAddress, SyncOpcode.AUTH, SyncErrorCode.AUTH_REJECTED, detail)
        Log.w(TAG, "auth attempt $attempts/$MAX_AUTH_ATTEMPTS failed ($detail) ${session.describe()}")

        if (attempts >= MAX_AUTH_ATTEMPTS) {
            failSession(session, SyncErrorCode.AUTH_REJECTED)
            registry.release(session.sessionId)
            return null
        }
        return authAckError(SyncErrorCode.AUTH_REJECTED, detail)
    }

    private fun authAccepted(session: SyncSession, auth: AuthPayload): ByteArray {
        val pairingCode = authorizer.pairingCode()
        val proof = if (SyncPairing.isUsableCode(pairingCode) &&
            auth.clientNonce.size() > 0 &&
            session.serverNonce.isNotEmpty()
        ) {
            // The counter-proof is what lets the initiator authenticate *this* device; an
            // exchange that only proves one direction still allows a rogue peer to
            // impersonate the user's other phone and collect the ledger.
            SyncPairing.serverProof(
                pairingCode = pairingCode!!,
                clientNonce = auth.clientNonce.toByteArray(),
                serverNonce = session.serverNonce,
                sessionId = session.sessionId
            )
        } else {
            ByteArray(0)
        }

        return AuthAckPayload.newBuilder()
            .setAccepted(true)
            .setServerNonce(ByteString.copyFrom(session.serverNonce))
            .setProof(ByteString.copyFrom(proof))
            .build()
            .toByteArray()
    }

    // --------------------------------------------------------------- manifest

    private fun onManifest(peerAddress: String, sessionId: Long, payload: ByteArray): ByteArray {
        val manifest = try {
            ManifestPayload.parseFrom(payload)
        } catch (e: InvalidProtocolBufferException) {
            return manifestError(peerAddress, SyncErrorCode.PARSE_ERROR, "malformed MANIFEST: ${e.message}")
        }

        val session = registry.find(sessionId)
            ?: return manifestError(peerAddress, SyncErrorCode.PROTOCOL_MISMATCH, "unknown session")

        return session.serialized {
            session.touch(clock())

            if (!session.authorized) {
                return@serialized manifestError(
                    peerAddress, SyncErrorCode.AUTH_REJECTED, "manifest before authorisation"
                )
            }
            // uint32 on the wire, Int on the JVM: anything above 2^31 arrives negative. The
            // range check therefore has to cover both ends, and the upper bound matches the
            // session's own entity cap - a chunk holds at least one entity, so more chunks
            // than that could never be buffered anyway.
            if (manifest.chunkCount < 0 || manifest.chunkCount > MAX_ANNOUNCED_CHUNKS) {
                return@serialized manifestError(
                    peerAddress,
                    SyncErrorCode.PAYLOAD_TOO_LARGE,
                    "chunk_count ${manifest.chunkCount} outside 0..$MAX_ANNOUNCED_CHUNKS"
                )
            }

            // A second, *different* manifest means the peer rebuilt its delta, which
            // renumbers the chunks. Anything buffered under the old numbering is now
            // meaningless and has to go, or the two sides would assemble different sets that
            // both look complete.
            val previous = session.manifest
            if (previous != null && previous.contentHash != manifest.contentHash) {
                Log.i(TAG, "peer re-announced a different delta; dropping buffers ${session.describe()}")
                session.resetTransfer()
            }

            session.acceptManifest(manifest)

            when (session.machine.state) {
                SyncState.EXCHANGING_MANIFEST -> {
                    session.machine.dispatch(
                        SyncEvent.ManifestAgreed(
                            totalChunks = manifest.chunkCount,
                            resumeFromChunk = session.ackedThroughChunk,
                            chunkSize = session.chunkSize
                        )
                    )
                    observer.onPhaseChanged(session, session.machine.state)
                }

                // Already transferring: this is a repeated or post-resume manifest. Accept it
                // and answer with the real checkpoint so the peer skips what is already here.
                SyncState.TRANSFERRING -> Unit

                else -> return@serialized manifestError(
                    peerAddress,
                    SyncErrorCode.PROTOCOL_MISMATCH,
                    "MANIFEST arrived in ${session.machine.state}"
                )
            }

            Log.i(
                TAG,
                "manifest entities=${manifest.totalEntities} chunks=${manifest.chunkCount} " +
                        "resumeFrom=${session.ackedThroughChunk} ${session.describe()}"
            )

            ManifestAckPayload.newBuilder()
                .setSessionId(sessionId)
                .setAccepted(true)
                .setResumeFromChunk(session.ackedThroughChunk)
                .setChunkSize(session.chunkSize)
                .setWindowSize(DEFAULT_WINDOW_SIZE)
                .build()
                .toByteArray()
        }
    }

    // ------------------------------------------------------------------ chunk

    private fun onChunk(peerAddress: String, sessionId: Long, payload: ByteArray): ByteArray {
        val chunk = try {
            ChunkPayload.parseFrom(payload)
        } catch (e: InvalidProtocolBufferException) {
            return chunkError(peerAddress, sessionId, SyncErrorCode.PARSE_ERROR, "malformed CHUNK: ${e.message}")
        }

        val session = registry.find(sessionId)
            ?: return chunkError(peerAddress, sessionId, SyncErrorCode.PROTOCOL_MISMATCH, "unknown session")

        return session.serialized {
            session.touch(clock())

            if (!session.authorized) {
                return@serialized chunkError(
                    peerAddress, sessionId, SyncErrorCode.AUTH_REJECTED, "chunk before authorisation"
                )
            }
            if (session.machine.state != SyncState.TRANSFERRING) {
                return@serialized chunkError(
                    peerAddress, sessionId, SyncErrorCode.PROTOCOL_MISMATCH,
                    "CHUNK arrived in ${session.machine.state}"
                )
            }

            when (val outcome = session.recordChunk(chunk.chunkIndex, chunk.entitiesList, payload.size)) {
                is SyncSession.ChunkOutcome.Accepted -> {
                    session.machine.dispatch(SyncEvent.ChunkAcknowledged(outcome.ackedThroughChunk))
                    chunkAck(sessionId, session)
                }

                is SyncSession.ChunkOutcome.Duplicate -> {
                    // The previous ack was lost. Answering with the same checkpoint is what
                    // unblocks the sender; treating it as an error would stall the transfer.
                    Log.d(TAG, "duplicate chunk ${chunk.chunkIndex} ${session.describe()}")
                    chunkAck(sessionId, session)
                }

                is SyncSession.ChunkOutcome.OutOfRange -> chunkError(
                    peerAddress, sessionId, SyncErrorCode.PARSE_ERROR,
                    "chunk ${outcome.index} outside 0..${outcome.chunkCount - 1}"
                )

                is SyncSession.ChunkOutcome.Overflow -> {
                    // The peer is streaming more than this device agreed to hold. Failing the
                    // session is the point: the alternative is an OutOfMemoryError that takes
                    // the whole app down.
                    failSession(session, SyncErrorCode.PAYLOAD_TOO_LARGE)
                    chunkError(
                        peerAddress, sessionId, SyncErrorCode.PAYLOAD_TOO_LARGE,
                        "buffer cap reached at ${outcome.bufferedEntities} entities / " +
                                "${outcome.bufferedBytes} bytes"
                    )
                }
            }
        }
    }

    private fun chunkAck(sessionId: Long, session: SyncSession): ByteArray =
        ChunkAckPayload.newBuilder()
            .setSessionId(sessionId)
            .setAckedThroughChunk(session.ackedThroughChunk)
            .addAllMissingChunks(session.missingChunks())
            .build()
            .toByteArray()

    // ------------------------------------------------------------------- pull

    /**
     * Serves one chunk of this device's own delta.
     *
     * Gated on authorisation but deliberately *not* on the transfer phase. The state machine
     * tracks the inbound push; a pull is a read-only side channel that is legitimate both
     * before the push starts and after it has been committed. Only a session that failed or
     * was cancelled refuses one.
     */
    private fun onPull(peerAddress: String, sessionId: Long, payload: ByteArray): ByteArray {
        val pull = try {
            PullPayload.parseFrom(payload)
        } catch (e: InvalidProtocolBufferException) {
            return pullError(peerAddress, sessionId, SyncErrorCode.PARSE_ERROR, "malformed PULL: ${e.message}")
        }

        val session = registry.find(sessionId)
            ?: return pullError(peerAddress, sessionId, SyncErrorCode.PROTOCOL_MISMATCH, "unknown session")

        return session.serialized {
            session.touch(clock())

            if (!session.authorized) {
                return@serialized pullError(
                    peerAddress, sessionId, SyncErrorCode.AUTH_REJECTED, "pull before authorisation"
                )
            }
            val state = session.machine.state
            if (state == SyncState.FAILED || state == SyncState.CANCELLED) {
                return@serialized pullError(
                    peerAddress, sessionId, SyncErrorCode.CANCELLED, "session ended in $state"
                )
            }

            val since = pull.sinceWatermark.coerceAtLeast(0L)
            val delta = session.cachedOutboundDelta(since) ?: buildOutbound(session, pull, since)

            val index = pull.chunkIndex
            val entities: List<SyncEntityV2> = when {
                // Nothing to send. Answering with an empty, well-formed PULL_ACK lets the
                // peer finish cleanly instead of having to interpret an error.
                delta.chunkCount == 0 -> emptyList()

                index < 0 || index >= delta.chunkCount -> return@serialized pullError(
                    peerAddress, sessionId, SyncErrorCode.PARSE_ERROR,
                    "pull chunk $index outside 0..${delta.chunkCount - 1}"
                )

                else -> delta.chunks[index]
            }

            PullAckPayload.newBuilder()
                .setSessionId(sessionId)
                .setChunkIndex(index)
                .setChunkCount(delta.chunkCount)
                .setTotalEntities(delta.entityCount)
                .setContentHash(ByteString.copyFrom(delta.contentHash))
                .setNewWatermark(delta.newWatermark)
                .addAllEntities(entities)
                .build()
                .toByteArray()
        }
    }

    /**
     * Builds and caches the outbound delta for this session.
     *
     * Built once and then served from the cache for every subsequent chunk. Recomputing per
     * request would be a correctness bug rather than a performance one: the user can add a
     * record between chunk 3 and chunk 4, which would renumber the chunks and invalidate the
     * aggregate hash the peer is verifying the set against.
     */
    private fun buildOutbound(session: SyncSession, pull: PullPayload, since: Long): DeltaSet {
        val requested = pull.chunkSize
        val builder = DeltaBuilder(if (requested > 0) requested else session.chunkSize)
        val rows = runBlocking { store.changedSince(since) }
        val delta = builder.build(rows, since)
        session.cacheOutboundDelta(since, delta)
        Log.i(
            TAG,
            "serving ${delta.entityCount} entities in ${delta.chunkCount} chunks " +
                    "since=$since ${session.describe()}"
        )
        return delta
    }

    // ----------------------------------------------------------------- commit

    private fun onCommit(peerAddress: String, sessionId: Long, payload: ByteArray): ByteArray {
        val commit = try {
            CommitPayload.parseFrom(payload)
        } catch (e: InvalidProtocolBufferException) {
            return commitError(peerAddress, sessionId, SyncErrorCode.PARSE_ERROR, "malformed COMMIT: ${e.message}")
        }

        // Checked before the session lookup on purpose: a replayed COMMIT can arrive after
        // the session has been swept, and the peer still deserves its original answer rather
        // than "unknown session".
        guard.cachedCommit(sessionId)?.let { return replayedCommitAck(sessionId, it) }

        val session = registry.find(sessionId)
            ?: return commitError(peerAddress, sessionId, SyncErrorCode.PROTOCOL_MISMATCH, "unknown session")

        return session.serialized {
            // Re-checked inside the lock: another connection of the same session may have
            // committed while this frame was waiting.
            guard.cachedCommit(sessionId)?.let { return@serialized replayedCommitAck(sessionId, it) }

            session.touch(clock())

            if (!session.authorized) {
                return@serialized commitError(
                    peerAddress, sessionId, SyncErrorCode.AUTH_REJECTED, "commit before authorisation"
                )
            }
            if (session.machine.state != SyncState.TRANSFERRING) {
                return@serialized commitError(
                    peerAddress, sessionId, SyncErrorCode.PROTOCOL_MISMATCH,
                    "COMMIT arrived in ${session.machine.state}"
                )
            }

            // Incomplete rather than corrupt: name the holes and leave the session running so
            // the peer can fill them and commit again. Failing here would throw away a
            // transfer that is one lost datagram from being finished.
            val missing = session.missingChunks()
            if (missing.isNotEmpty()) {
                observer.onRejected(
                    peerAddress, SyncOpcode.COMMIT, SyncErrorCode.CRC_MISMATCH,
                    "${missing.size} chunks still missing"
                )
                return@serialized commitAckBuilder(sessionId)
                    .setApplied(false)
                    .setError(SyncErrorMapping.toProto(SyncErrorCode.CRC_MISMATCH))
                    .setErrorMessage("missing chunks: ${missing.take(16)}")
                    .build()
                    .toByteArray()
            }

            val received = session.orderedEntities()
            if (!contentMatches(received, commit.contentHash, commit.expectedEntities)) {
                // Every frame passed its own CRC, so the damage is at the set level - a whole
                // chunk lost or reordered. Only a fresh transfer can fix that, and the
                // buffers must go with it or the retransmission would be answered as a
                // duplicate.
                session.resetTransfer()
                observer.onRejected(
                    peerAddress, SyncOpcode.COMMIT, SyncErrorCode.CRC_MISMATCH, "aggregate hash mismatch"
                )
                return@serialized commitAckBuilder(sessionId)
                    .setApplied(false)
                    .setError(SyncErrorMapping.toProto(SyncErrorCode.CRC_MISMATCH))
                    .setErrorMessage(
                        "content hash mismatch: expected ${commit.expectedEntities} entities, " +
                                "assembled ${received.size}"
                    )
                    .build()
                    .toByteArray()
            }

            session.machine.dispatch(SyncEvent.TransferComplete)
            observer.onPhaseChanged(session, session.machine.state)

            val report = try {
                runBlocking { applier.apply(received, session.peerDeviceId) }
            } catch (e: Exception) {
                // A write failure is local and non-retryable from the peer's point of view;
                // telling it to retry would just repeat the same failing transaction.
                Log.e(TAG, "apply failed ${session.describe()}", e)
                failSession(session, SyncErrorCode.APPLY_ERROR)
                return@serialized commitError(
                    peerAddress, sessionId, SyncErrorCode.APPLY_ERROR, e.message ?: e.toString()
                )
            }

            guard.recordCommit(
                sessionId,
                IdempotencyGuard.CommitRecord(
                    inserted = report.inserted,
                    updated = report.updated,
                    skipped = report.skipped,
                    applied = true
                )
            )
            // Inbound only. The peer usually pulls *after* committing its push, and dropping
            // the outbound snapshot here would renumber its chunks mid-sequence.
            session.releaseInboundBuffers()

            session.machine.dispatch(
                SyncEvent.CommitAccepted(report.inserted, report.updated, report.skipped)
            )
            observer.onApplied(session, report)
            observer.onPhaseChanged(session, session.machine.state)
            observer.onSessionFinished(session, session.machine.state)
            authorizer.remember(session.peerDeviceId, session.peerDeviceName)

            Log.i(TAG, "commit ${report.toLogString()} ${session.describe()}")

            commitAckBuilder(sessionId)
                .setApplied(true)
                .setInsertedCount(report.inserted)
                .setUpdatedCount(report.updated)
                .setSkippedCount(report.skipped)
                .addAllConflicts(report.conflicts)
                .build()
                .toByteArray()
        }
    }

    /**
     * Verifies the assembled set against what the sender said it sent.
     *
     * Per-frame CRCs cannot see a missing or reordered chunk - each surviving frame still
     * checksums perfectly - so this is the only check that covers the transfer as a whole.
     */
    private fun contentMatches(
        received: List<SyncEntityV2>,
        expectedHash: ByteString,
        expectedCount: Int
    ): Boolean {
        if (expectedCount != received.size) return false
        // An empty hash means the sender opted out; the count check above still applies.
        if (expectedHash.isEmpty) return true
        val computed = EntityFingerprint.aggregate(received.map(SyncEntityV2::getEntityHash))
        return expectedHash.toByteArray().contentEquals(computed)
    }

    /**
     * Answer for a COMMIT that was already applied.
     *
     * The conflict list is not replayed: it is derived state, it can be large, and caching it
     * for every session would turn the guard into a memory sink. The counts are what the peer
     * actually needs in order to finish.
     */
    private fun replayedCommitAck(sessionId: Long, record: IdempotencyGuard.CommitRecord): ByteArray {
        Log.d(TAG, "replaying cached commit for session ${sessionId.hex()}")
        return commitAckBuilder(sessionId)
            .setApplied(record.applied)
            .setInsertedCount(record.inserted)
            .setUpdatedCount(record.updated)
            .setSkippedCount(record.skipped)
            .build()
            .toByteArray()
    }

    private fun commitAckBuilder(sessionId: Long): CommitAckPayload.Builder =
        CommitAckPayload.newBuilder().setSessionId(sessionId)

    // ------------------------------------------------------------- rejections

    private fun failSession(session: SyncSession, code: SyncErrorCode) {
        session.machine.fail(code)
        observer.onSessionFinished(session, session.machine.state)
    }

    private fun helloError(peerAddress: String, code: SyncErrorCode, detail: String): ByteArray {
        Log.w(TAG, "hello refused from $peerAddress: $detail")
        observer.onRejected(peerAddress, SyncOpcode.HELLO, code, detail)
        return HelloAckPayload.newBuilder()
            .setNegotiatedVersion(SyncWireProtocol.PROTOCOL_VERSION)
            .setDeviceId(identity.deviceId)
            .setDeviceName(identity.deviceName)
            .setCapabilities(LOCAL_CAPABILITIES)
            .setError(SyncErrorMapping.toProto(code))
            .setErrorMessage(detail)
            .build()
            .toByteArray()
    }

    private fun authError(peerAddress: String, code: SyncErrorCode, detail: String): ByteArray {
        Log.w(TAG, "auth refused from $peerAddress: $detail")
        observer.onRejected(peerAddress, SyncOpcode.AUTH, code, detail)
        return authAckError(code, detail)
    }

    private fun authAckError(code: SyncErrorCode, detail: String): ByteArray =
        AuthAckPayload.newBuilder()
            .setAccepted(false)
            .setError(SyncErrorMapping.toProto(code))
            .setErrorMessage(detail)
            .build()
            .toByteArray()

    private fun manifestError(peerAddress: String, code: SyncErrorCode, detail: String): ByteArray {
        Log.w(TAG, "manifest refused from $peerAddress: $detail")
        observer.onRejected(peerAddress, SyncOpcode.MANIFEST, code, detail)
        return ManifestAckPayload.newBuilder()
            .setAccepted(false)
            .setResumeFromChunk(-1)
            .setError(SyncErrorMapping.toProto(code))
            .setErrorMessage(detail)
            .build()
            .toByteArray()
    }

    private fun chunkError(
        peerAddress: String,
        sessionId: Long,
        code: SyncErrorCode,
        detail: String
    ): ByteArray {
        Log.w(TAG, "chunk refused from $peerAddress: $detail")
        observer.onRejected(peerAddress, SyncOpcode.CHUNK, code, detail)
        return ChunkAckPayload.newBuilder()
            .setSessionId(sessionId)
            .setAckedThroughChunk(registry.find(sessionId)?.ackedThroughChunk ?: -1)
            .setError(SyncErrorMapping.toProto(code))
            .setErrorMessage(detail)
            .build()
            .toByteArray()
    }

    private fun pullError(
        peerAddress: String,
        sessionId: Long,
        code: SyncErrorCode,
        detail: String
    ): ByteArray {
        Log.w(TAG, "pull refused from $peerAddress: $detail")
        observer.onRejected(peerAddress, SyncOpcode.PULL, code, detail)
        return PullAckPayload.newBuilder()
            .setSessionId(sessionId)
            .setError(SyncErrorMapping.toProto(code))
            .setErrorMessage(detail)
            .build()
            .toByteArray()
    }

    private fun commitError(
        peerAddress: String,
        sessionId: Long,
        code: SyncErrorCode,
        detail: String
    ): ByteArray {
        Log.w(TAG, "commit refused from $peerAddress: $detail")
        observer.onRejected(peerAddress, SyncOpcode.COMMIT, code, detail)
        return commitAckBuilder(sessionId)
            .setApplied(false)
            .setError(SyncErrorMapping.toProto(code))
            .setErrorMessage(detail)
            .build()
            .toByteArray()
    }

    private fun hasCapability(mask: Int, capability: SyncCapability): Boolean =
        mask and capability.number != 0

    private fun Long.hex(): String = "%016X".format(this)

    companion object {
        private const val TAG = "SyncResponder"

        /**
         * Attempts allowed before the connection is dropped.
         *
         * Three is enough for a mistyped code and far too few to search even a short one,
         * especially since each further try costs a fresh TCP connection and handshake.
         */
        const val MAX_AUTH_ATTEMPTS = 3

        /**
         * How long the user has to answer the confirmation dialog.
         *
         * Must stay well below the native handler deadline (150 s), because native abandons
         * the connection at that point and the peer would then see a dead socket instead of
         * a typed AUTH_TIMEOUT it can report.
         */
        const val AUTH_PROMPT_TIMEOUT_MS = 60_000L

        /** Chunks the sender may keep in flight before waiting for an ack. */
        const val DEFAULT_WINDOW_SIZE = 4

        /** Matches the session's entity cap: a chunk always holds at least one entity. */
        const val MAX_ANNOUNCED_CHUNKS = 100_000

        /** What this build advertises in HELLO / HELLO_ACK. Compression is not wired up yet. */
        val LOCAL_CAPABILITIES: Int = SyncCapability.SYNC_CAPABILITY_RESUME.number or
                SyncCapability.SYNC_CAPABILITY_DELTA.number or
                SyncCapability.SYNC_CAPABILITY_PAIRING.number
    }
}
