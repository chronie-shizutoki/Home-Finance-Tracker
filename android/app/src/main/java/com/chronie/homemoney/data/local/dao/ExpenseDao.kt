package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the expenses table.
 *
 * Provides both UI-facing queries (excluding soft-deleted records) and
 * sync-facing queries (including tombstones) to support reliable
 * device-to-device and server synchronization.
 *
 * General rule: methods without "ForSync" in their name hide soft-deleted
 * records and are safe for UI display. Methods with "ForSync" include
 * tombstones and should only be used by the sync engine.
 */
@Dao
interface ExpenseDao {
    
    /** Returns a reactive Flow of all active (non-deleted) expenses, newest first. */
    @Query("SELECT * FROM expenses WHERE deleted_at IS NULL ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    
    /** Fetches a single active expense by ID; returns null for soft-deleted records. */
    @Query("SELECT * FROM expenses WHERE id = :id AND deleted_at IS NULL")
    suspend fun getExpenseById(id: String): ExpenseEntity?
    
    /** Returns active expenses within a date range as a reactive Flow. */
    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate AND deleted_at IS NULL ORDER BY date DESC")
    fun getExpensesByDateRange(startDate: String, endDate: String): Flow<List<ExpenseEntity>>
    
    /** Returns active expenses filtered by type/category. */
    @Query("SELECT * FROM expenses WHERE type = :type AND deleted_at IS NULL ORDER BY date DESC")
    fun getExpensesByType(type: String): Flow<List<ExpenseEntity>>
    
    /** Gets all unsynced active expenses pending server upload. */
    @Query("SELECT * FROM expenses WHERE is_synced = 0 AND deleted_at IS NULL")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>
    
    /** Gets all unsynced records (including soft-deleted) for sync queue building. */
    @Query("SELECT * FROM expenses WHERE is_synced = 0")
    suspend fun getPendingChanges(): List<ExpenseEntity>
    
    /** Gets all active expenses modified since a given timestamp. */
    @Query("SELECT * FROM expenses WHERE updated_at > :lastSyncTime ORDER BY updated_at ASC")
    suspend fun getChangesSince(lastSyncTime: Long): List<ExpenseEntity>
    
    /**
     * Full table snapshot including soft-deleted tombstones.
     *
     * Device-to-device sync must see tombstones, otherwise a deletion on one
     * device can never propagate and the databases diverge permanently.
     * UI queries must use [getAllExpenses] instead.
     */
    @Query("SELECT * FROM expenses ORDER BY updated_at ASC")
    suspend fun getAllExpensesForSync(): List<ExpenseEntity>
    
    /**
     * Incremental changes since a watermark, including soft-deleted tombstones.
     */
    @Query("SELECT * FROM expenses WHERE updated_at > :lastSyncTime ORDER BY updated_at ASC")
    suspend fun getChangesSinceForSync(lastSyncTime: Long): List<ExpenseEntity>
    
    /**
     * ID lookup that returns even soft-deleted rows.
     *
     * [getExpenseById] hides tombstones, which would make a locally deleted
     * record appear absent and allow a stale incoming record to resurrect it.
     * Merge logic must use this variant.
     */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseByIdForSync(id: String): ExpenseEntity?
    
    /** Returns all expense IDs (including soft-deleted) for sync diffing. */
    @Query("SELECT id FROM expenses")
    suspend fun getAllIds(): List<String>
    
    /** Inserts or replaces a single expense. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)
    
    /** Bulk inserts/replaces multiple expenses. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)
    
    /** Updates an existing expense by primary key. */
    @Update
    suspend fun updateExpense(expense: ExpenseEntity)
    
    /** Hard-deletes an expense (use soft-delete via update for sync compatibility). */
    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)
    
    /** Hard-deletes an expense by ID. */
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)
    
    /** Purges all expense records (use with caution). */
    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
    
    /** Returns the total count of active expenses. */
    @Query("SELECT COUNT(*) FROM expenses WHERE deleted_at IS NULL")
    suspend fun getExpenseCount(): Int
    
    /** Sums all active expense amounts within a date range; returns null if no records. */
    @Query("SELECT SUM(amount) FROM expenses WHERE date BETWEEN :startDate AND :endDate AND deleted_at IS NULL")
    suspend fun getTotalAmountByDateRange(startDate: String, endDate: String): Double?
    
    /** Batch-fetches active expenses within a date range for export operations. */
    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate AND deleted_at IS NULL ORDER BY date DESC")
    suspend fun getExpensesByDateRangeSync(startDate: String, endDate: String): List<ExpenseEntity>
    
    /** Returns the maximum updatedAt timestamp across all expenses. */
    @Query("SELECT MAX(updated_at) FROM expenses")
    suspend fun getLastUpdateTime(): Long?
    
    /**
     * Inserts the expense only if no newer local version exists.
     * Prevents stale data from overwriting newer local changes during sync.
     */
    @Transaction
    suspend fun upsertExpense(expense: ExpenseEntity) {
        // Use the sync-aware lookup: a locally soft-deleted tombstone must not be
        // overwritten by an incoming (possibly stale) server record. Otherwise a
        // pending delete is resurrected and never reaches the cloud.
        val existing = getExpenseByIdForSync(expense.id)
        if (existing != null) {
            if (existing.deletedAt != null) {
                // A local delete wins locally until it has been pushed to the server.
                return
            }
            if (existing.updatedAt >= expense.updatedAt) {
                return
            }
        }
        insertExpense(expense)
    }
    
    /**
     * Batch-syncs server expenses into the local database.
     * Each server record is only written if it's newer than the local copy
     * or if no local copy exists. All inserted records are marked as synced.
     */
    @Transaction
    suspend fun syncExpenses(
        serverExpenses: List<ExpenseEntity>,
        lastSyncTime: Long
    ) {
        for (serverExpense in serverExpenses) {
            val local = getExpenseById(serverExpense.id)
            if (local == null || local.updatedAt < serverExpense.updatedAt) {
                insertExpense(serverExpense.copy(isSynced = true))
            }
        }
    }
}
