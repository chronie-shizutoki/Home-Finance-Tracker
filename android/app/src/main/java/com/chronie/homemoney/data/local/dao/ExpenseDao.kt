package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 支出记录数据访问对象
 */
@Dao
interface ExpenseDao {
    
    @Query("SELECT * FROM expenses ORDER BY time DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?
    
    @Query("SELECT * FROM expenses WHERE server_id = :serverId")
    suspend fun getExpenseByServerId(serverId: String): ExpenseEntity?
    
    @Query("SELECT * FROM expenses WHERE time BETWEEN :startTime AND :endTime ORDER BY time DESC")
    fun getExpensesByTimeRange(startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE type = :type ORDER BY time DESC")
    fun getExpensesByType(type: String): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE is_synced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>
    
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
    
    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getExpenseCount(): Int
    
    @Query("SELECT SUM(amount) FROM expenses WHERE time BETWEEN :startTime AND :endTime")
    suspend fun getTotalAmountByTimeRange(startTime: Long, endTime: Long): Double?
    
    @Query("SELECT * FROM expenses WHERE date(time/1000, 'unixepoch') BETWEEN :startDate AND :endDate ORDER BY time DESC")
    suspend fun getExpensesByDateRange(startDate: String, endDate: String): List<ExpenseEntity>
}
