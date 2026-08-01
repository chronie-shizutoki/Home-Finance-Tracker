package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.sync.generated.SyncOperation
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The builder is where v1's three structural faults were fixed, so the tests are written
 * against those faults rather than against the implementation:
 *
 *  - a deletion must be able to reach the peer (v1 filtered tombstones out entirely),
 *  - the watermark must advance to exactly the newest row seen (v1 had no watermark and
 *    resent the whole table forever),
 *  - two devices must derive the same content hash from the same rows regardless of the
 *    order SQLite handed them over (v1 had no integrity check at all).
 *
 * Everything here is pure: no database, no clock, no emulator.
 */
class DeltaBuilderTest {

    private val builder = DeltaBuilder()

    // ------------------------------------------------------------------ empty

    @Test
    fun `an empty change set produces an empty delta, not an empty chunk`() {
        val delta = builder.build(emptyList(), sinceWatermark = 500)

        assertTrue(delta.isEmpty)
        assertEquals(0, delta.entityCount)
        // A zero-entity chunk would still cost a frame and an ack round trip for nothing.
        assertEquals(0, delta.chunkCount)
        assertEquals(0, delta.totalBytes)
    }

    @Test
    fun `an empty delta keeps the caller's watermark`() {
        val delta = builder.build(emptyList(), sinceWatermark = 1_234)

        // Rewinding to 0 here would make the next sync a full resend.
        assertEquals(1_234, delta.newWatermark)
        assertEquals(1_234, delta.sinceWatermark)
    }

    @Test
    fun `an empty delta still describes itself in a manifest`() {
        val manifest = builder.build(emptyList()).manifest(sessionId = 7)

        assertEquals(7, manifest.sessionId)
        assertEquals(0, manifest.totalEntities)
        assertEquals(0, manifest.chunkCount)
        // The peer needs a hash even for "nothing changed", to confirm we agree on that.
        assertTrue(manifest.contentHash.size() > 0)
    }

    // ------------------------------------------------------------------ tombstones

    @Test
    fun `a deleted row travels as a DELETE, not as an omission`() {
        val delta = builder.build(listOf(expense("a", deletedAt = 900)))

        val entity = delta.entities.single()
        // v1's `deleted_at IS NULL` filter is the reason a deletion never propagated and
        // the two databases diverged permanently.
        assertEquals(SyncOperation.SYNC_OPERATION_DELETE, entity.operation)
        assertEquals(900, entity.deletedAt)
    }

    @Test
    fun `a tombstone still carries its body`() {
        val delta = builder.build(listOf(expense("a", type = "food", amount = 12.5, deletedAt = 900)))

        val payload = delta.entities.single().expense
        // The receiver has to be able to store a complete row: a bare id is something it
        // can neither display in a trash view nor merge against a local edit.
        assertEquals("a", payload.id)
        assertEquals("food", payload.type)
        assertEquals(12.5, payload.amount, 1e-9)
    }

    @Test
    fun `a live row is an UPSERT with a zero tombstone`() {
        val delta = builder.build(listOf(expense("a")))

        val entity = delta.entities.single()
        assertEquals(SyncOperation.SYNC_OPERATION_UPSERT, entity.operation)
        // Proto3 has no null; 0 is the agreed "not deleted" marker.
        assertEquals(0, entity.deletedAt)
    }

    @Test
    fun `deletions and edits travel together in one delta`() {
        val delta = builder.build(
            listOf(
                expense("a", updatedAt = 100),
                expense("b", updatedAt = 200, deletedAt = 200),
                expense("c", updatedAt = 300)
            )
        )

        assertEquals(3, delta.entityCount)
        assertEquals(1, delta.entities.count { it.operation == SyncOperation.SYNC_OPERATION_DELETE })
    }

    @Test
    fun `a null remark becomes an empty string, not a crash`() {
        val delta = builder.build(listOf(expense("a", remark = null)))

        assertEquals("", delta.entities.single().expense.remark)
    }

    // ------------------------------------------------------------------ watermark

    @Test
    fun `the watermark advances to the newest row`() {
        val delta = builder.build(
            listOf(expense("a", updatedAt = 100), expense("b", updatedAt = 900), expense("c", updatedAt = 500)),
            sinceWatermark = 50
        )

        assertEquals(900, delta.newWatermark)
    }

    @Test
    fun `the watermark never moves backwards`() {
        // Clock skew, or a row written by an older build with a stale timestamp.
        val delta = builder.build(listOf(expense("a", updatedAt = 10)), sinceWatermark = 1_000)

        // Accepting 10 here would resend everything between 10 and 1000 on the next sync.
        assertEquals(1_000, delta.newWatermark)
    }

    @Test
    fun `a zero watermark means a full snapshot`() {
        val delta = builder.build(listOf(expense("a", updatedAt = 100)), sinceWatermark = 0)

        assertEquals(0, delta.sinceWatermark)
        assertEquals(100, delta.newWatermark)
    }

    // ------------------------------------------------------------------ ordering & hashing

    @Test
    fun `row order from the database does not change the delta`() {
        val rows = listOf(
            expense("c", updatedAt = 300),
            expense("a", updatedAt = 100),
            expense("b", updatedAt = 200)
        )

        val forward = builder.build(rows)
        val shuffled = builder.build(rows.shuffled(Random(7)))

        // SQLite gives no ordering guarantee, so the two devices would otherwise compute
        // different hashes from identical data and declare a mismatch on every sync.
        assertEquals(forward.entities.map { it.entityId }, shuffled.entities.map { it.entityId })
        assertArrayEquals(forward.contentHash, shuffled.contentHash)
    }

    @Test
    fun `rows sharing a timestamp are ordered by id`() {
        val rows = listOf(
            expense("z", updatedAt = 100),
            expense("a", updatedAt = 100),
            expense("m", updatedAt = 100)
        )

        val ids = builder.build(rows).entities.map { it.entityId }

        // updatedAt alone is not a total order; a batch import writes many rows in the
        // same millisecond, which is exactly when the tie-break has to exist.
        assertEquals(listOf("a", "m", "z"), ids)
    }

    @Test
    fun `the content hash changes when any field changes`() {
        val base = builder.build(listOf(expense("a", amount = 10.0)))
        val edited = builder.build(listOf(expense("a", amount = 10.01)))

        assertNotEquals(
            base.contentHash.toList(),
            edited.contentHash.toList()
        )
    }

    @Test
    fun `the content hash changes when a row is removed from the set`() {
        val two = builder.build(listOf(expense("a"), expense("b", updatedAt = 200)))
        val one = builder.build(listOf(expense("a")))

        assertNotEquals(two.contentHash.toList(), one.contentHash.toList())
    }

    @Test
    fun `each entity carries its own hash for per-record validation`() {
        val delta = builder.build(listOf(expense("a"), expense("b", updatedAt = 200)))

        // The aggregate hash says "something is wrong"; the per-entity hash says which row.
        assertTrue(delta.entities.all { it.entityHash != 0 })
        assertEquals(2, delta.entities.map { it.entityHash }.distinct().size)
    }

    // ------------------------------------------------------------------ chunking

    @Test
    fun `a small delta is a single chunk`() {
        val delta = builder.build((1..20).map { expense("e$it", updatedAt = it.toLong()) })

        assertEquals(1, delta.chunkCount)
        assertEquals(20, delta.chunks.single().size)
    }

    @Test
    fun `chunks stay inside the negotiated size`() {
        val small = DeltaBuilder(SyncWireProtocol.MIN_CHUNK_SIZE)
        val rows = (1..2_000).map { expense("e$it", updatedAt = it.toLong(), remark = "r".repeat(64)) }

        val delta = small.build(rows)

        assertTrue("expected several chunks, got ${delta.chunkCount}", delta.chunkCount > 1)
        delta.chunks.forEachIndexed { index, chunk ->
            val bytes = chunk.sumOf { it.serializedSize }
            assertTrue(
                "chunk $index was $bytes bytes, over ${small.chunkSize}",
                bytes <= small.chunkSize
            )
        }
    }

    @Test
    fun `chunking loses nothing and duplicates nothing`() {
        val small = DeltaBuilder(SyncWireProtocol.MIN_CHUNK_SIZE)
        val rows = (1..2_000).map { expense("e$it", updatedAt = it.toLong(), remark = "r".repeat(64)) }

        val delta = small.build(rows)

        val flattened = delta.chunks.flatten()
        assertEquals(delta.entityCount, flattened.size)
        assertEquals(delta.entities.map { it.entityId }, flattened.map { it.entityId })
        assertEquals(2_000, flattened.map { it.entityId }.distinct().size)
    }

    @Test
    fun `one oversized row gets its own chunk instead of being dropped`() {
        val small = DeltaBuilder(SyncWireProtocol.MIN_CHUNK_SIZE)
        val huge = expense("huge", updatedAt = 2, remark = "x".repeat(SyncWireProtocol.MIN_CHUNK_SIZE * 2))

        val delta = small.build(listOf(expense("a", updatedAt = 1), huge, expense("b", updatedAt = 3)))

        // Splitting is impossible at this layer, and silently dropping the row would let
        // the two databases diverge with no error anywhere.
        assertEquals(3, delta.entityCount)
        val huged = delta.chunks.single { chunk -> chunk.any { it.entityId == "huge" } }
        assertEquals(1, huged.size)
    }

    // ------------------------------------------------------------------ chunk size clamp

    @Test
    fun `a peer proposing zero cannot force one chunk per entity`() {
        assertEquals(SyncWireProtocol.MIN_CHUNK_SIZE, DeltaBuilder(0).chunkSize)
        assertEquals(SyncWireProtocol.MIN_CHUNK_SIZE, DeltaBuilder(-1).chunkSize)
    }

    @Test
    fun `a peer proposing a huge chunk cannot blow the frame cap`() {
        assertEquals(SyncWireProtocol.MAX_CHUNK_SIZE, DeltaBuilder(Int.MAX_VALUE).chunkSize)
    }

    @Test
    fun `a sane proposal is honoured and reported in the manifest`() {
        val proposed = 32 * 1024
        val delta = DeltaBuilder(proposed).build(listOf(expense("a")))

        assertEquals(proposed, delta.chunkSize)
        assertEquals(proposed, delta.manifest(sessionId = 1).chunkSize)
    }

    // ------------------------------------------------------------------ manifest & payloads

    @Test
    fun `the manifest describes exactly what will be sent`() {
        val small = DeltaBuilder(SyncWireProtocol.MIN_CHUNK_SIZE)
        val rows = (1..500).map { expense("e$it", updatedAt = it.toLong(), remark = "r".repeat(64)) }
        val delta = small.build(rows, sinceWatermark = 5)

        val manifest = delta.manifest(sessionId = 42)

        // The receiver sizes its buffers and its progress bar from these numbers before a
        // single byte of data arrives, so they have to agree with the delta itself.
        assertEquals(42, manifest.sessionId)
        assertEquals(5, manifest.sinceWatermark)
        assertEquals(delta.entityCount, manifest.totalEntities)
        assertEquals(delta.totalBytes, manifest.totalBytes)
        assertEquals(delta.chunkCount, manifest.chunkCount)
        assertArrayEquals(delta.contentHash, manifest.contentHash.toByteArray())
    }

    @Test
    fun `a chunk payload is stamped with its own index`() {
        val small = DeltaBuilder(SyncWireProtocol.MIN_CHUNK_SIZE)
        val delta = small.build((1..1_000).map { expense("e$it", updatedAt = it.toLong(), remark = "r".repeat(64)) })

        val payload = delta.chunkPayload(1, sessionId = 9)

        // A resume asks for chunk N by index; an unstamped payload could be applied twice
        // or out of order with no way to notice.
        assertEquals(1, payload.chunkIndex)
        assertEquals(9, payload.sessionId)
        assertEquals(delta.chunks[1].size, payload.entitiesCount)
    }

    @Test
    fun `asking for a chunk that does not exist is an error, not an empty answer`() {
        val delta = builder.build(listOf(expense("a")))

        // Answering with an empty chunk would let a confused peer "complete" a sync that
        // never transferred anything.
        assertThrows(IndexOutOfBoundsException::class.java) { delta.chunkPayload(1, sessionId = 1) }
        assertThrows(IndexOutOfBoundsException::class.java) { delta.chunkPayload(-1, sessionId = 1) }
    }

    // ------------------------------------------------------------------ resume

    @Test
    fun `a fresh receiver needs every chunk`() {
        val small = DeltaBuilder(SyncWireProtocol.MIN_CHUNK_SIZE)
        val delta = small.build((1..1_000).map { expense("e$it", updatedAt = it.toLong(), remark = "r".repeat(64)) })

        assertEquals(0 until delta.chunkCount, delta.remainingChunks(ackedThroughChunk = -1))
    }

    @Test
    fun `a resumed transfer restarts after the last acknowledged chunk`() {
        val small = DeltaBuilder(SyncWireProtocol.MIN_CHUNK_SIZE)
        val delta = small.build((1..1_000).map { expense("e$it", updatedAt = it.toLong(), remark = "r".repeat(64)) })

        // This is the whole point of chunking: a drop at 99% costs one chunk, not the sync.
        assertEquals(1 until delta.chunkCount, delta.remainingChunks(ackedThroughChunk = 0))
    }

    @Test
    fun `a fully acknowledged transfer needs nothing`() {
        val delta = builder.build(listOf(expense("a")))

        assertTrue(delta.remainingChunks(ackedThroughChunk = delta.chunkCount - 1).isEmpty())
    }

    @Test
    fun `a nonsense ack cannot produce a negative range`() {
        val delta = builder.build(listOf(expense("a")))

        // A peer sending -99 is buggy or hostile; either way it must not make us iterate
        // backwards or index out of bounds.
        assertEquals(0 until 1, delta.remainingChunks(ackedThroughChunk = -99))
        assertTrue(delta.remainingChunks(ackedThroughChunk = 99).isEmpty())
    }

    // ------------------------------------------------------------------ determinism

    @Test
    fun `the same rows always build the same delta`() {
        val rows = (1..300).map {
            expense("e$it", updatedAt = (it % 17).toLong(), amount = it.toDouble())
        }

        val first = builder.build(rows, sinceWatermark = 3)
        val second = builder.build(rows.reversed(), sinceWatermark = 3)

        // Two devices run this code independently and must agree, or the content hash is
        // worse than useless: it would fail every sync of identical data.
        assertArrayEquals(first.contentHash, second.contentHash)
        assertEquals(first.newWatermark, second.newWatermark)
        assertEquals(first.totalBytes, second.totalBytes)
        assertEquals(first.chunks.map { c -> c.map { it.entityId } }, second.chunks.map { c -> c.map { it.entityId } })
    }

    private fun expense(
        id: String,
        type: String = "expense",
        remark: String? = "note",
        amount: Double = 9.99,
        date: String = "2026-08-01",
        version: Int = 1,
        updatedAt: Long = 100,
        deletedAt: Long? = null
    ) = ExpenseEntity(
        id = id,
        type = type,
        remark = remark,
        amount = amount,
        date = date,
        version = version,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
}
