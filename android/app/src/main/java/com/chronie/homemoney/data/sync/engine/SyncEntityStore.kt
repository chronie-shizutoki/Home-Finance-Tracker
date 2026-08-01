package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.entity.ExpenseEntity

/**
 * The only database surface the sync engine is allowed to touch.
 *
 * `ExpenseDao` carries about twenty-five methods, most of them for the UI, and several of
 * them are actively wrong for sync - `getExpenseById` and `getAllExpenses` filter out
 * tombstones, which is how a locally deleted record used to look absent and get resurrected
 * by a stale revision from the peer. Narrowing the surface to four sync-correct operations
 * makes that mistake unavailable rather than merely discouraged.
 *
 * It also makes the merge and idempotency logic unit-testable: a fake implementation is a
 * dozen lines, whereas faking the full DAO is not worth writing and an in-memory Room
 * instance needs an emulator.
 */
interface SyncEntityStore {

    /** Tombstone-aware lookup. Returns soft-deleted rows too. */
    suspend fun load(entityId: String): ExpenseEntity?

    /**
     * Writes every row in one transaction.
     *
     * All-or-nothing is required, not merely preferred: a delta is validated as a set
     * against the manifest's content hash, so a partial apply would leave the database in a
     * state neither device believes in.
     */
    suspend fun writeAll(rows: List<ExpenseEntity>)

    /** Rows modified after [watermark], tombstones included, ordered by `updated_at`. */
    suspend fun changedSince(watermark: Long): List<ExpenseEntity>

    /** Every row, tombstones included. Used when the peer has no usable watermark. */
    suspend fun snapshot(): List<ExpenseEntity>
}

/** Production implementation, backed by Room. */
class RoomSyncEntityStore(private val dao: ExpenseDao) : SyncEntityStore {

    override suspend fun load(entityId: String): ExpenseEntity? =
        dao.getExpenseByIdForSync(entityId)

    // Room wraps a list @Insert in a single transaction, so this is atomic as required.
    override suspend fun writeAll(rows: List<ExpenseEntity>) {
        if (rows.isEmpty()) return
        dao.insertExpenses(rows)
    }

    override suspend fun changedSince(watermark: Long): List<ExpenseEntity> =
        if (watermark <= 0L) dao.getAllExpensesForSync() else dao.getChangesSinceForSync(watermark)

    override suspend fun snapshot(): List<ExpenseEntity> = dao.getAllExpensesForSync()
}
