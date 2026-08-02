package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the budgets table.
 *
 * The budget table uses a singleton pattern (id=1), meaning only one
 * budget configuration exists at a time. This DAO provides both
 * reactive (Flow-based) and one-shot access to the budget record.
 */
@Dao
interface BudgetDao {
    
    /** Observes the single budget record reactively; emits null if none exists. */
    @Query("SELECT * FROM budgets WHERE id = 1")
    fun getBudget(): Flow<BudgetEntity?>
    
    /** Fetches the current budget record once (non-reactive). */
    @Query("SELECT * FROM budgets WHERE id = 1")
    suspend fun getBudgetOnce(): BudgetEntity?
    
    /** Saves or overwrites the budget configuration. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)
    
    /** Updates the existing budget configuration. */
    @Update
    suspend fun updateBudget(budget: BudgetEntity)
    
    /** Removes the budget configuration entirely. */
    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
