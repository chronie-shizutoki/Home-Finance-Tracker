package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.sync.protocol.Crc32c

/**
 * Makes a replayed frame, chunk or COMMIT harmless.
 *
 * Retries are now a first-class part of the transport, which means the *same* bytes can
 * legitimately arrive twice: the responder applied a chunk, its CHUNK_ACK was lost, and the
 * initiator resent. Without a guard the second delivery is applied a second time, which is
 * how the old code could double-count a record whenever the network hiccuped at exactly the
 * wrong moment.
 *
 * Three independent layers, because each catches a duplicate the others cannot see:
 *
 *  1. **Frame level** - `(sessionId, seq)`. Catches a verbatim retransmission before any
 *     parsing work is done.
 *  2. **Entity level** - `(entityId, updatedAt, entityHash)`. Catches the same revision
 *     arriving through a *different* frame, which is what happens after a résumé renumbers
 *     the chunks.
 *  3. **Commit level** - `sessionId` to a cached outcome. Let's COMMIT be replayed safely:
 *     the peer gets the original answer instead of a second apply.
 *
 * All three are bounded LRU maps. An unbounded set here would be a slow memory leak on a
 * device that syncs often, and the entries lose their value quickly anyway - a duplicate
 * that shows up thousands of frames later is a different problem.
 *
 * Every method is synchronized: the native thread pool dispatches connections from several
 * threads at once, so this is genuinely shared mutable state.
 */
class IdempotencyGuard(
    private val frameCapacity: Int = DEFAULT_FRAME_CAPACITY,
    private val entityCapacity: Int = DEFAULT_ENTITY_CAPACITY,
    private val commitCapacity: Int = DEFAULT_COMMIT_CAPACITY
) {

    /** Cached result of an already-applied COMMIT, replayed verbatim to the peer. */
    data class CommitRecord(
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val applied: Boolean
    )

    private val lock = Any()

    private val seenFrames = lruSet<Long>(frameCapacity)
    private val appliedEntities = lruSet<Long>(entityCapacity)
    private val commits = lruMap<Long, CommitRecord>(commitCapacity)

    /**
     * Records a frame and reports whether it is new.
     *
     * @return true when the caller should process the frame, false when it is a duplicate
     *   that has already been handled.
     */
    fun acceptFrame(sessionId: Long, seq: Int): Boolean = synchronized(lock) {
        seenFrames.add(frameKey(sessionId, seq))
    }

    /** True when `(sessionId, seq)` was already delivered. Does not record anything. */
    fun isDuplicateFrame(sessionId: Long, seq: Int): Boolean = synchronized(lock) {
        seenFrames.contains(frameKey(sessionId, seq))
    }

    /**
     * Reports whether this exact entity revision still needs to be applied.
     *
     * The triple is the identity of a *revision*, not of a record: a genuine later edit has
     * a different `updatedAt` or a different hash and is therefore not filtered out. Only a
     * byte-identical replay is suppressed.
     */
    fun shouldApplyEntity(entityId: String, updatedAt: Long, entityHash: Int): Boolean =
        synchronized(lock) {
            !appliedEntities.contains(entityKey(entityId, updatedAt, entityHash))
        }

    /** Marks a revision as applied. Idempotent. */
    fun recordEntityApplied(entityId: String, updatedAt: Long, entityHash: Int) {
        synchronized(lock) {
            appliedEntities.add(entityKey(entityId, updatedAt, entityHash))
        }
    }

    /**
     * Atomically claims a revision.
     *
     * Prefer this over [shouldApplyEntity] + [recordEntityApplied] when two connections may
     * race on the same entity, which is exactly the multi-device case the thread pool
     * enabled.
     *
     * @return true when the caller won the claim and must apply the revision.
     */
    fun claimEntity(entityId: String, updatedAt: Long, entityHash: Int): Boolean =
        synchronized(lock) {
            appliedEntities.add(entityKey(entityId, updatedAt, entityHash))
        }

    /** Previously cached COMMIT outcome for a session, or null if it never committed. */
    fun cachedCommit(sessionId: Long): CommitRecord? = synchronized(lock) {
        commits[sessionId]
    }

    /** Stores the outcome so a replayed COMMIT can be answered without applying twice. */
    fun recordCommit(sessionId: Long, record: CommitRecord) {
        synchronized(lock) {
            commits[sessionId] = record
        }
    }

    /**
     * Drops everything remembered about one session.
     *
     * Called when a session reaches a terminal state, so a long-lived process does not
     * carry frame keys for sessions that can never recur. Entity keys are intentionally
     * *not* cleared here - they protect against a replay arriving in a brand-new session,
     * which is precisely the case a résumé creates.
     */
    fun forgetSession(sessionId: Long) {
        synchronized(lock) {
            seenFrames.removeAll { matchesSession(it, sessionId) }
            commits.remove(sessionId)
        }
    }

    /** Test and diagnostics hook. */
    fun stats(): Stats = synchronized(lock) {
        Stats(seenFrames.size, appliedEntities.size, commits.size)
    }

    data class Stats(val frames: Int, val entities: Int, val commits: Int)

    private fun matchesSession(key: Long, sessionId: Long): Boolean =
        (key ushr SEQ_BITS) == (sessionId and SESSION_MASK)

    companion object {
        const val DEFAULT_FRAME_CAPACITY = 4096
        const val DEFAULT_ENTITY_CAPACITY = 8192
        const val DEFAULT_COMMIT_CAPACITY = 64

        private const val SEQ_BITS = 32
        private const val SESSION_MASK = 0xFFFFFFFFL

        /**
         * Packs `(sessionId, seq)` into one long.
         *
         * Only the low 32 bits of the session id are used. A collision needs two live
         * sessions whose ids agree in 32 bits *and* the same seq, which for a
         * randomly-generated 64-bit id on a LAN with a handful of peers is not a risk worth
         * a wider key.
         */
        private fun frameKey(sessionId: Long, seq: Int): Long =
            ((sessionId and SESSION_MASK) shl SEQ_BITS) or (seq.toLong() and SESSION_MASK)

        /**
         * Fingerprints an entity revision.
         *
         * `updatedAt` alone is not enough: two devices can write different content within
         * the same millisecond. Mixing the caller-supplied content hash in makes an
         * accidental match require a CRC collision on top of a timestamp collision.
         */
        private fun entityKey(entityId: String, updatedAt: Long, entityHash: Int): Long {
            var h = Crc32c.compute(entityId.toByteArray(Charsets.UTF_8)).toLong() and 0xFFFFFFFFL
            h = h * 31 + updatedAt
            h = h * 31 + entityHash
            return h
        }

        private fun <K, V> lruMap(capacity: Int): LinkedHashMap<K, V> =
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                    size > capacity
            }

        private fun <T> lruSet(capacity: Int): MutableSet<T> =
            java.util.Collections.newSetFromMap(lruMap<T, Boolean>(capacity))
    }
}
