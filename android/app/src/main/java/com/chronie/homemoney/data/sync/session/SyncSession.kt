package com.chronie.homemoney.data.sync.session

import com.chronie.homemoney.data.sync.engine.DeltaSet
import com.chronie.homemoney.data.sync.generated.ManifestPayload
import com.chronie.homemoney.data.sync.generated.SyncEntityV2
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol

/**
 * Everything one sync session needs to remember between frames.
 *
 * The v1 responder was stateless: each connection carried the whole database in a single
 * request and was answered in a single response, so there was nothing to remember and
 * nothing to resume. v2 spreads one logical sync over many frames, which means the phase,
 * the negotiated parameters and the partially received data all have to live somewhere
 * across frame boundaries. That somewhere is here.
 *
 * ### Thread safety
 *
 * Normally one session is touched by exactly one native thread, because one session maps to
 * one connection. A resume breaks that assumption: the initiator redials while the old
 * connection has not been reaped yet, and two pool threads briefly hold the same session.
 * Every compound operation is therefore synchronised. The cost is irrelevant next to the
 * I/O it guards.
 *
 * ### Buffering policy
 *
 * Chunks accumulate until COMMIT so the whole delta can be applied in one transaction - a
 * half-applied sync is worse than no sync. That makes the buffer an obvious denial-of-service
 * target: a peer that streams chunks and never commits would grow it without bound. [Limits]
 * caps both the entity count and the byte count, and [recordChunk] refuses politely once
 * either is exceeded instead of letting the process die on an OutOfMemoryError.
 */
class SyncSession(
    /** Session id from the frame header. Non-zero; the responder rejects 0 before we get here. */
    val sessionId: Long,
    /** Transport-level peer description, `ip:port`. Logging only - never a trust anchor. */
    val peerAddress: String,
    /** Correlation id shared with the peer, so both devices' logs can be zipped together. */
    val traceId: String,
    val openedAtMs: Long,
    private val limits: Limits = Limits()
) {

    /**
     * @param maxBufferedEntities cap on records held before COMMIT.
     * @param maxBufferedBytes cap on serialized payload bytes held before COMMIT.
     */
    data class Limits(
        val maxBufferedEntities: Int = 100_000,
        val maxBufferedBytes: Long = 32L * 1024 * 1024
    )

    /** Result of feeding one CHUNK into the session. */
    sealed interface ChunkOutcome {
        /** Stored. [ackedThroughChunk] is the new cumulative checkpoint. */
        data class Accepted(val ackedThroughChunk: Int) : ChunkOutcome

        /** Already held; the previous CHUNK_ACK was lost. Answer with the same checkpoint. */
        data class Duplicate(val ackedThroughChunk: Int) : ChunkOutcome

        /** Buffer limit hit. The session must be failed, not silently truncated. */
        data class Overflow(val bufferedEntities: Int, val bufferedBytes: Long) : ChunkOutcome

        /** Index outside `0 until chunkCount` announced by the manifest. */
        data class OutOfRange(val index: Int, val chunkCount: Int) : ChunkOutcome
    }

    val machine = SyncSessionStateMachine()

    private val lock = Any()

    /**
     * Coarse lock the responder uses to run one whole frame at a time for this session.
     *
     * Deliberately *not* [lock]. Handling a frame can block for a minute while the user
     * looks at the confirmation dialog, and [lock] is also taken by [idleForMs], which the
     * registry calls from inside its own lock during an idle sweep. Sharing one lock would
     * therefore let a pending dialog on one session freeze every other session's admission
     * for the duration. Two locks, with the long one never touched by the short accessors,
     * keeps the two concerns independent.
     */
    private val frameLock = java.util.concurrent.locks.ReentrantLock()

    /**
     * Runs [block] with no other frame of this session in flight.
     *
     * Needed because a single frame is a compound operation - check the phase, mutate, then
     * answer - and a resumed connection can genuinely deliver two frames of one session
     * concurrently while the old socket is still being reaped.
     */
    fun <T> serialized(block: () -> T): T {
        frameLock.lock()
        try {
            return block()
        } finally {
            frameLock.unlock()
        }
    }

    // ------------------------------------------------------------ negotiated

    /** Peer's stable device id, learned from HELLO. Empty until then. */
    var peerDeviceId: String = ""
        private set

    var peerDeviceName: String = ""
        private set

    var peerDeviceType: String = ""
        private set

    /** Version both ends settled on; never above this build's [SyncWireProtocol.PROTOCOL_VERSION]. */
    var negotiatedVersion: Int = SyncWireProtocol.PROTOCOL_VERSION
        private set

    /** Bitmask of `SyncCapability` values the peer advertised. */
    var peerCapabilities: Int = 0
        private set

    /** Chunk size in force, after both sides have had a say. */
    var chunkSize: Int = SyncWireProtocol.DEFAULT_CHUNK_SIZE
        private set

    // ------------------------------------------------------------------ auth

    /** Nonce this device generated for the pairing exchange; empty when pairing is off. */
    var serverNonce: ByteArray = EMPTY
        private set

    /** True once the peer cleared both the pairing proof and, where needed, the user prompt. */
    var authorized: Boolean = false
        private set

    /** Failed AUTH attempts, so a brute-force guesser can be cut off. */
    var authAttempts: Int = 0
        private set

    // -------------------------------------------------------------- transfer

    /** Manifest the peer announced, or null before MANIFEST arrives. */
    var manifest: ManifestPayload? = null
        private set

    /** Highest contiguous chunk index held; -1 when nothing has arrived. */
    var ackedThroughChunk: Int = -1
        private set

    var lastActivityMs: Long = openedAtMs
        private set

    private val chunks = HashMap<Int, List<SyncEntityV2>>()
    private var bufferedEntities = 0
    private var bufferedBytes = 0L

    // ------------------------------------------------------------- outbound

    /**
     * The delta this device is serving back to the peer, and the watermark it was built
     * from.
     *
     * Cached rather than recomputed per PULL for two reasons. The obvious one is cost. The
     * important one is consistency: a PULL asks for chunk *n* of a set the peer believes is
     * fixed, so recomputing between chunk 3 and chunk 4 - after the user has just added a
     * record - would renumber the chunks underneath it and silently corrupt the aggregate
     * content hash. The snapshot is taken once and served until the session ends.
     */
    private var outboundDelta: DeltaSet? = null
    private var outboundWatermark: Long = Long.MIN_VALUE

    /** @return the cached delta when it was built from [sinceWatermark], null otherwise. */
    fun cachedOutboundDelta(sinceWatermark: Long): DeltaSet? = synchronized(lock) {
        if (outboundWatermark == sinceWatermark) outboundDelta else null
    }

    fun cacheOutboundDelta(sinceWatermark: Long, delta: DeltaSet) {
        synchronized(lock) {
            outboundWatermark = sinceWatermark
            outboundDelta = delta
        }
    }

    // ------------------------------------------------------------- lifecycle

    fun touch(nowMs: Long) {
        synchronized(lock) { lastActivityMs = nowMs }
    }

    fun idleForMs(nowMs: Long): Long = synchronized(lock) { nowMs - lastActivityMs }

    fun recordPeerIdentity(
        deviceId: String,
        deviceName: String,
        deviceType: String,
        capabilities: Int,
        negotiatedVersion: Int
    ) {
        synchronized(lock) {
            this.peerDeviceId = deviceId
            this.peerDeviceName = deviceName
            this.peerDeviceType = deviceType
            this.peerCapabilities = capabilities
            this.negotiatedVersion = negotiatedVersion
        }
    }

    fun recordServerNonce(nonce: ByteArray) {
        synchronized(lock) { serverNonce = nonce }
    }

    fun markAuthorized() {
        synchronized(lock) { authorized = true }
    }

    /** @return the attempt count after the increment, for the lockout check. */
    fun recordFailedAuth(): Int = synchronized(lock) { ++authAttempts }

    /**
     * Accepts the peer's manifest and settles the chunk size.
     *
     * The peer's proposal is clamped rather than trusted: `chunk_size` arrives from the
     * network, and a value of 0 or 2 GiB would otherwise propagate into buffer arithmetic.
     */
    fun acceptManifest(payload: ManifestPayload) {
        synchronized(lock) {
            manifest = payload
            val proposed = payload.chunkSize
            chunkSize = if (proposed <= 0) {
                SyncWireProtocol.DEFAULT_CHUNK_SIZE
            } else {
                proposed.coerceIn(SyncWireProtocol.MIN_CHUNK_SIZE, SyncWireProtocol.MAX_CHUNK_SIZE)
            }
        }
    }

    /** Chunk count the manifest announced, or 0 when no manifest has arrived. */
    val expectedChunkCount: Int
        get() = synchronized(lock) { manifest?.chunkCount ?: 0 }

    /** Entity count the manifest announced. */
    val expectedEntityCount: Int
        get() = synchronized(lock) { manifest?.totalEntities ?: 0 }

    // ---------------------------------------------------------------- chunks

    fun recordChunk(index: Int, entities: List<SyncEntityV2>, sizeBytes: Int): ChunkOutcome =
        synchronized(lock) {
            val announced = manifest?.chunkCount ?: 0
            if (index < 0 || (announced > 0 && index >= announced)) {
                return ChunkOutcome.OutOfRange(index, announced)
            }
            if (chunks.containsKey(index)) {
                // Not an error: a lost CHUNK_ACK makes the sender retransmit, and answering
                // with the current checkpoint is exactly what unblocks it.
                return ChunkOutcome.Duplicate(ackedThroughChunk)
            }

            val nextEntities = bufferedEntities + entities.size
            val nextBytes = bufferedBytes + sizeBytes
            if (nextEntities > limits.maxBufferedEntities || nextBytes > limits.maxBufferedBytes) {
                return ChunkOutcome.Overflow(nextEntities, nextBytes)
            }

            chunks[index] = entities
            bufferedEntities = nextEntities
            bufferedBytes = nextBytes
            advanceCheckpoint()
            ChunkOutcome.Accepted(ackedThroughChunk)
        }

    /**
     * Chunks the peer still owes, given what the manifest announced.
     *
     * Reported in CHUNK_ACK so the sender retransmits only the holes. Restarting the whole
     * transfer because one datagram was dropped is precisely what made v1 unusable on a
     * weak link.
     */
    fun missingChunks(): List<Int> = synchronized(lock) {
        val announced = manifest?.chunkCount ?: return emptyList()
        (0 until announced).filterNot(chunks::containsKey)
    }

    /** True when every chunk the manifest announced is held. */
    val isComplete: Boolean
        get() = synchronized(lock) {
            val announced = manifest?.chunkCount ?: return false
            ackedThroughChunk == announced - 1
        }

    /**
     * Everything received so far, flattened in chunk order.
     *
     * Order matters: the aggregate content hash in the manifest is order sensitive, so a
     * mismatch here is the signal that a chunk was silently lost or reordered.
     */
    fun orderedEntities(): List<SyncEntityV2> = synchronized(lock) {
        val announced = manifest?.chunkCount ?: chunks.keys.maxOrNull()?.plus(1) ?: 0
        val out = ArrayList<SyncEntityV2>(bufferedEntities)
        for (i in 0 until announced) {
            chunks[i]?.let(out::addAll)
        }
        out
    }

    fun bufferStats(): BufferStats =
        synchronized(lock) { BufferStats(chunks.size, bufferedEntities, bufferedBytes) }

    data class BufferStats(val chunks: Int, val entities: Int, val bytes: Long)

    /**
     * Frees the received chunks once they have been applied.
     *
     * Called after COMMIT succeeds. Deliberately leaves the outbound delta alone: the peer
     * commonly commits its push and only then starts pulling, and dropping the cached
     * snapshot at that point would renumber its chunks mid-sequence. The session object
     * itself also stays alive, so a replayed COMMIT still finds it and is answered from the
     * idempotency cache instead of being applied twice.
     */
    fun releaseInboundBuffers() {
        synchronized(lock) {
            chunks.clear()
            bufferedEntities = 0
            bufferedBytes = 0L
        }
    }

    /**
     * Discards the inbound transfer entirely so the peer can start it over.
     *
     * Used when the assembled set fails its aggregate hash: every individual frame passed
     * its CRC, so the corruption is at the set level and the only remedy is a fresh
     * transfer. Merely clearing the chunks would not be enough - [ackedThroughChunk] would
     * still claim the data was held and [recordChunk] would answer the retransmission with
     * `Duplicate`, leaving the session wedged with data neither side believes in.
     */
    fun resetTransfer() {
        synchronized(lock) {
            chunks.clear()
            bufferedEntities = 0
            bufferedBytes = 0L
            ackedThroughChunk = -1
            manifest = null
        }
    }

    /** Frees everything. Called by the registry when the session is retired. */
    fun releaseBuffers() {
        synchronized(lock) {
            chunks.clear()
            bufferedEntities = 0
            bufferedBytes = 0L
            outboundDelta = null
            outboundWatermark = Long.MIN_VALUE
        }
    }

    /** Compact one-line description for the structured sync log. */
    fun describe(): String = synchronized(lock) {
        "session=%016X peer=%s dev=%s state=%s acked=%d buffered=%d/%dB trace=%s".format(
            sessionId, peerAddress, peerDeviceId.ifEmpty { "?" }, machine.state,
            ackedThroughChunk, bufferedEntities, bufferedBytes, traceId
        )
    }

    /** Must be called with [lock] held. Amortised O(1) per chunk. */
    private fun advanceCheckpoint() {
        while (chunks.containsKey(ackedThroughChunk + 1)) {
            ackedThroughChunk++
        }
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
