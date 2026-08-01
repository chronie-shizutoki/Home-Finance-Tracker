package com.chronie.homemoney.data.sync.session

/**
 * The set of sync sessions this device is currently serving.
 *
 * ### Why a registry exists at all
 *
 * v1 needed none: a connection was a request/response pair, so nothing outlived the socket.
 * v2 must map an incoming frame back to the session it belongs to, and that mapping has to
 * survive the socket, because a resumed transfer arrives on a brand new connection carrying
 * the old session id.
 *
 * ### Two failure modes this closes
 *
 *  - **Unbounded growth.** A peer that opens a session and vanishes leaves state behind
 *    forever. Every acquisition sweeps sessions that have been idle past the timeout, so an
 *    abandoned sync cannot pin a session slot or its chunk buffer.
 *  - **Silent concurrency.** v1 relied on a plain `isSyncing` boolean, which two threads can
 *    both read as false. Admission is decided here under one lock: over the limit, the peer
 *    gets a clean BUSY it can retry, instead of a second sync racing the first into the
 *    same tables.
 *
 * @param maxConcurrentSessions how many peers may sync at once. Small on purpose: each
 *   session holds a chunk buffer, and a phone syncing with four devices simultaneously is
 *   not a real scenario, whereas a leak that pretends it is would be expensive.
 * @param idleTimeoutMs silence after which a session is considered abandoned. Must exceed
 *   the longest legitimate pause, which is the user staring at the confirmation dialog.
 * @param clock injectable so the eviction rules are testable without sleeping.
 */
class SyncSessionRegistry(
    private val maxConcurrentSessions: Int = DEFAULT_MAX_SESSIONS,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val sessionLimits: SyncSession.Limits = SyncSession.Limits(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Outcome of asking for the session a frame belongs to. */
    sealed interface Acquisition {
        /** A new session was created for this id. */
        data class Opened(val session: SyncSession) : Acquisition

        /** The id was already known; this is a later frame, or a resumed connection. */
        data class Resumed(val session: SyncSession) : Acquisition

        /** All slots are taken by live sessions. The peer should back off and retry. */
        data class Busy(val activeSessions: Int) : Acquisition

        val sessionOrNull: SyncSession?
            get() = when (this) {
                is Opened -> session
                is Resumed -> session
                is Busy -> null
            }
    }

    private val lock = Any()
    private val sessions = LinkedHashMap<Long, SyncSession>()

    /**
     * Finds or creates the session for [sessionId].
     *
     * @param sessionId must be non-zero; 0 is what an uninitialised header looks like and
     *   accepting it would merge every buggy peer into one shared session.
     */
    fun acquire(sessionId: Long, peerAddress: String, traceId: String): Acquisition {
        require(sessionId != 0L) { "session id 0 is reserved and must be rejected earlier" }

        synchronized(lock) {
            val now = clock()
            sweepIdle(now)

            sessions[sessionId]?.let { existing ->
                existing.touch(now)
                return Acquisition.Resumed(existing)
            }

            if (sessions.size >= maxConcurrentSessions) {
                return Acquisition.Busy(sessions.size)
            }

            val created = SyncSession(
                sessionId = sessionId,
                peerAddress = peerAddress,
                traceId = traceId,
                openedAtMs = now,
                limits = sessionLimits
            )
            sessions[sessionId] = created
            return Acquisition.Opened(created)
        }
    }

    /** Existing session for [sessionId], without creating one. */
    fun find(sessionId: Long): SyncSession? = synchronized(lock) { sessions[sessionId] }

    /** Drops a finished session. Safe to call twice. */
    fun release(sessionId: Long): SyncSession? = synchronized(lock) {
        sessions.remove(sessionId)?.also { it.releaseBuffers() }
    }

    /**
     * Evicts sessions that have gone quiet.
     *
     * @return how many were removed. Exposed so the metrics layer can surface abandoned
     *   syncs: a rising count means peers are disappearing mid-transfer, which is a network
     *   problem worth seeing rather than a silent cleanup.
     */
    fun evictIdle(): Int = synchronized(lock) { sweepIdle(clock()) }

    fun activeCount(): Int = synchronized(lock) { sessions.size }

    /** Snapshot for diagnostics. The sessions themselves stay live and mutable. */
    fun snapshot(): List<SyncSession> = synchronized(lock) { sessions.values.toList() }

    fun clear() {
        synchronized(lock) {
            sessions.values.forEach(SyncSession::releaseBuffers)
            sessions.clear()
        }
    }

    /** Must be called with [lock] held. */
    private fun sweepIdle(now: Long): Int {
        if (sessions.isEmpty()) return 0
        val iterator = sessions.entries.iterator()
        var removed = 0
        while (iterator.hasNext()) {
            val session = iterator.next().value
            // A terminal session is dropped as soon as it stops being useful for replaying
            // a COMMIT answer; a live one only when the peer has genuinely gone quiet.
            val expired = session.idleForMs(now) > idleTimeoutMs
            if (expired) {
                session.releaseBuffers()
                iterator.remove()
                removed++
            }
        }
        return removed
    }

    companion object {
        const val DEFAULT_MAX_SESSIONS = 4

        /**
         * Two minutes.
         *
         * The dominant legitimate pause is the user deciding whether to accept the sync,
         * for which the responder allows 60 s. Doubling that leaves room for a slow device
         * without letting a dead session linger for long.
         */
        const val DEFAULT_IDLE_TIMEOUT_MS = 120_000L
    }
}
