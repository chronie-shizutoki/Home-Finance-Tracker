package com.chronie.homemoney.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chronie.homemoney.data.local.dao.DebtDao
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.MemberDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import com.chronie.homemoney.data.local.entity.DebtEntity
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.local.entity.MemberEntity
import com.chronie.homemoney.data.local.entity.SyncQueueEntity

/**
 * 应用数据库
 * 版本 1: 初始版本，包含 expenses, debts, members, sync_queue 表
 */
@Database(
    entities = [
        ExpenseEntity::class,
        DebtEntity::class,
        MemberEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun expenseDao(): ExpenseDao
    abstract fun debtDao(): DebtDao
    abstract fun memberDao(): MemberDao
    abstract fun syncQueueDao(): SyncQueueDao
    
    companion object {
        const val DATABASE_NAME = "homemoney.db"
    }
}
