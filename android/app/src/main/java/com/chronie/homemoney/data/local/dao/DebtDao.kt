package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

/**
 * 债务记录数据访问对象
 */
@Dao
interface DebtDao {
    
    @Query("SELECT * FROM debts ORDER BY date DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>
    
    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: String): DebtEntity?
    
    @Query("SELECT * FROM debts WHERE type = :type ORDER BY date DESC")
    fun getDebtsByType(type: String): Flow<List<DebtEntity>>
    
    @Query("SELECT * FROM debts WHERE is_repaid = :isRepaid ORDER BY date DESC")
    fun getDebtsByRepaidStatus(isRepaid: Boolean): Flow<List<DebtEntity>>
    
    @Query("SELECT * FROM debts WHERE is_synced = 0")
    suspend fun getUnsyncedDebts(): List<DebtEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebts(debts: List<DebtEntity>)
    
    @Update
    suspend fun updateDebt(debt: DebtEntity)
    
    @Delete
    suspend fun deleteDebt(debt: DebtEntity)
    
    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebtById(id: String)
    
    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()
    
    @Query("SELECT SUM(amount) FROM debts WHERE type = :type AND is_repaid = 0")
    suspend fun getTotalAmountByType(type: String): Double?
}
