package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    
    @Query("SELECT * FROM expenses WHERE deleted_at IS NULL ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE id = :id AND deleted_at IS NULL")
    suspend fun getExpenseById(id: String): ExpenseEntity?
    
    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate AND deleted_at IS NULL ORDER BY date DESC")
    fun getExpensesByDateRange(startDate: String, endDate: String): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE type = :type AND deleted_at IS NULL ORDER BY date DESC")
    fun getExpensesByType(type: String): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE is_synced = 0 AND deleted_at IS NULL")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>
    
    @Query("SELECT * FROM expenses WHERE is_synced = 0")
    suspend fun getPendingChanges(): List<ExpenseEntity>
    
    @Query("SELECT * FROM expenses WHERE updated_at > :lastSyncTime ORDER BY updated_at ASC")
    suspend fun getChangesSince(lastSyncTime: Long): List<ExpenseEntity>
    
    /**
     * Snapshot of every row including soft-deleted tombstones.
     *
     * Device-to-device sync must see tombstones, otherwise a deletion performed on one
     * device can never propagate to the other and the two databases diverge permanently.
     * Regular UI queries must keep using [getAllExpenses].
     */
    @Query("SELECT * FROM expenses ORDER BY updated_at ASC")
    suspend fun getAllExpensesForSync(): List<ExpenseEntity>
    
    /**
     * Changes since a watermark including soft-deleted tombstones, for incremental sync.
     */
    @Query("SELECT * FROM expenses WHERE updated_at > :lastSyncTime ORDER BY updated_at ASC")
    suspend fun getChangesSinceForSync(lastSyncTime: Long): List<ExpenseEntity>
    
    /**
     * Lookup that also returns soft-deleted rows.
     *
     * [getExpenseById] hides tombstones, which makes a locally deleted record look absent
     * and lets an incoming stale record resurrect it. Merge logic must use this variant.
     */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseByIdForSync(id: String): ExpenseEntity?
    
    @Query("SELECT id FROM expenses")
    suspend fun getAllIds(): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)
    
    @Update
    suspend fun updateExpense(expense: ExpenseEntity)
    
    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)
    
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)
    
    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
    
    @Query("SELECT COUNT(*) FROM expenses WHERE deleted_at IS NULL")
    suspend fun getExpenseCount(): Int
    
    @Query("SELECT SUM(amount) FROM expenses WHERE date BETWEEN :startDate AND :endDate AND deleted_at IS NULL")
    suspend fun getTotalAmountByDateRange(startDate: String, endDate: String): Double?
    
    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate AND deleted_at IS NULL ORDER BY date DESC")
    suspend fun getExpensesByDateRangeSync(startDate: String, endDate: String): List<ExpenseEntity>
    
    @Query("SELECT MAX(updated_at) FROM expenses")
    suspend fun getLastUpdateTime(): Long?
    
    @Transaction
    suspend fun upsertExpense(expense: ExpenseEntity) {
        val existing = getExpenseById(expense.id)
        if (existing != null && existing.updatedAt >= expense.updatedAt) {
            return
        }
        insertExpense(expense)
    }
    
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
