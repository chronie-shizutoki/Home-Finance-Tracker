package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.sync.generated.ChunkPayload
import com.chronie.homemoney.data.sync.generated.ExpensePayload
import com.chronie.homemoney.data.sync.generated.ManifestPayload
import com.chronie.homemoney.data.sync.generated.SyncEntityV2
import com.chronie.homemoney.data.sync.generated.SyncOperation
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol
import com.google.protobuf.ByteString

/**
 * Turns a set of local rows into the chunked delta that goes on the wire.
 *
 * Three things here replace behaviour that was actively broken:
 *
 *  1. **Incremental.** v1 shipped the entire table on every sync, so cost grew with history
 *     and a large ledger could not finish over a weak link at all. A watermark limits the
 *     set to what actually changed.
 *  2. **Tombstones included.** v1 filtered on `deleted_at IS NULL` and hard-coded the
 *     operation to `CREATE`, which meant a deletion could never reach the peer and the two
 *     databases diverged permanently. Deletions travel as first-class `DELETE` revisions.
 *  3. **Chunked.** A single oversized message could not be resumed; if it failed at 99% the
 *     whole thing was refetched. Chunks are individually acknowledged and individually
 *     retransmitted.
 *
 * The builder is pure - it touches no database and no clock - so the chunking and hashing
 * rules are testable without an emulator.
 */
class DeltaBuilder(chunkSizeBytes: Int = SyncWireProtocol.DEFAULT_CHUNK_SIZE) {

    /**
     * Clamped on construction rather than trusted.
     *
     * The value can arrive from a peer via `ManifestAckPayload.chunk_size`, and a hostile or
     * simply buggy peer proposing 0 would otherwise produce one chunk per entity, or
     * proposing 2 GiB would blow the frame cap.
     */
    val chunkSize: Int = chunkSizeBytes.coerceIn(
        SyncWireProtocol.MIN_CHUNK_SIZE,
        SyncWireProtocol.MAX_CHUNK_SIZE
    )

    /**
     * Builds the delta for [rows].
     *
     * @param rows every row modified after [sinceWatermark], tombstones included. Callers
     *   normally pass `ExpenseDao.getChangesSinceForSync`, or
     *   `getAllExpensesForSync` when [sinceWatermark] is 0.
     * @param sinceWatermark 0 requests a full snapshot.
     */
    fun build(rows: List<ExpenseEntity>, sinceWatermark: Long = 0L): DeltaSet {
        // Sorting by (updatedAt, id) is what makes the content hash reproducible: the two
        // devices must agree on the order, and SQLite gives no ordering guarantee for rows
        // sharing an updated_at value.
        val ordered = rows.sortedWith(compareBy({ it.updatedAt }, { it.id }))

        val entities = ArrayList<SyncEntityV2>(ordered.size)
        val hashes = ArrayList<Int>(ordered.size)
        var newWatermark = sinceWatermark

        for (row in ordered) {
            val hash = EntityFingerprint.hash(row)
            entities.add(toWireEntity(row, hash))
            hashes.add(hash)
            if (row.updatedAt > newWatermark) newWatermark = row.updatedAt
        }

        val chunks = pack(entities)
        val totalBytes = entities.sumOf { it.serializedSize.toLong() }

        return DeltaSet(
            sinceWatermark = sinceWatermark,
            newWatermark = newWatermark,
            entities = entities,
            chunks = chunks,
            contentHash = EntityFingerprint.aggregate(hashes),
            totalBytes = totalBytes,
            chunkSize = chunkSize
        )
    }

    /**
     * Greedily packs entities into chunks that stay inside [chunkSize].
     *
     * An entity larger than a whole chunk still gets its own chunk rather than being
     * dropped or split - splitting is not possible at this layer, and the frame cap
     * (1 MiB) is four times the largest permitted chunk, so there is headroom.
     */
    private fun pack(entities: List<SyncEntityV2>): List<List<SyncEntityV2>> {
        if (entities.isEmpty()) return emptyList()

        val budget = chunkSize - ENVELOPE_RESERVE_BYTES
        val chunks = ArrayList<List<SyncEntityV2>>()
        var current = ArrayList<SyncEntityV2>()
        var currentBytes = 0

        for (entity in entities) {
            val cost = entity.serializedSize + PER_ENTITY_OVERHEAD_BYTES
            if (current.isNotEmpty() && currentBytes + cost > budget) {
                chunks.add(current)
                current = ArrayList()
                currentBytes = 0
            }
            current.add(entity)
            currentBytes += cost
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    private fun toWireEntity(row: ExpenseEntity, hash: Int): SyncEntityV2 =
        SyncEntityV2.newBuilder()
            .setEntityType(EntityFingerprint.ENTITY_TYPE_EXPENSE)
            .setEntityId(row.id)
            .setOperation(
                if (row.deletedAt != null) SyncOperation.SYNC_OPERATION_DELETE
                else SyncOperation.SYNC_OPERATION_UPSERT
            )
            .setVersion(row.version)
            .setUpdatedAt(row.updatedAt)
            .setDeletedAt(row.deletedAt ?: 0L)
            .setEntityHash(hash)
            .setExpense(
                // The body travels even for a tombstone, so the receiver can persist a
                // complete row instead of a bare id it cannot display or merge.
                ExpensePayload.newBuilder()
                    .setId(row.id)
                    .setType(row.type)
                    .setRemark(row.remark ?: "")
                    .setAmount(row.amount)
                    .setDate(row.date)
                    .build()
            )
            .build()

    companion object {
        /** Room for `session_id` (9 B) and `chunk_index` (up to 6 B), rounded up. */
        private const val ENVELOPE_RESERVE_BYTES = 32

        /** Field tag plus length prefix for one repeated entry. */
        private const val PER_ENTITY_OVERHEAD_BYTES = 6
    }
}

/**
 * An immutable, ready-to-send delta.
 *
 * Not a `data class`: it holds a [ByteArray], whose generated `equals` compares references
 * and would quietly make two identical deltas unequal.
 */
class DeltaSet(
    /** Watermark this delta was computed from; 0 for a full snapshot. */
    val sinceWatermark: Long,
    /** Highest `updatedAt` in the set. The receiver stores it as its next watermark. */
    val newWatermark: Long,
    val entities: List<SyncEntityV2>,
    val chunks: List<List<SyncEntityV2>>,
    /** SHA-256 over the ordered entity hashes; see [EntityFingerprint.aggregate]. */
    val contentHash: ByteArray,
    val totalBytes: Long,
    val chunkSize: Int
) {

    val entityCount: Int get() = entities.size
    val chunkCount: Int get() = chunks.size
    val isEmpty: Boolean get() = entities.isEmpty()

    /** Manifest describing this delta, sent before any data moves. */
    fun manifest(sessionId: Long): ManifestPayload =
        ManifestPayload.newBuilder()
            .setSessionId(sessionId)
            .setSinceWatermark(sinceWatermark)
            .setTotalEntities(entityCount)
            .setTotalBytes(totalBytes)
            .setChunkCount(chunkCount)
            .setContentHash(ByteString.copyFrom(contentHash))
            .setChunkSize(chunkSize)
            .build()

    /**
     * Chunk [index] as a wire payload.
     *
     * @throws IndexOutOfBoundsException if [index] is outside `0 until chunkCount`. A peer
     *   asking to resume from a chunk that does not exist is a protocol error and must be
     *   reported, not silently answered with an empty chunk.
     */
    fun chunkPayload(index: Int, sessionId: Long): ChunkPayload {
        val entities = chunks[index]
        return ChunkPayload.newBuilder()
            .setSessionId(sessionId)
            .setChunkIndex(index)
            .addAllEntities(entities)
            .build()
    }

    /** Chunks still needed by a receiver holding everything through [ackedThroughChunk]. */
    fun remainingChunks(ackedThroughChunk: Int): IntRange {
        val first = (ackedThroughChunk + 1).coerceAtLeast(0)
        return first until chunkCount
    }
}
