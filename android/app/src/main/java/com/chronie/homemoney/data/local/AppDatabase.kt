package com.chronie.homemoney.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chronie.homemoney.data.local.dao.BudgetDao
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.MemberDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import com.chronie.homemoney.data.local.entity.BudgetEntity
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.local.entity.MemberEntity
import com.chronie.homemoney.data.local.entity.SyncQueueEntity

/**
 * Room database class for the Home Finance Tracker application.
 *
 * Manages the local SQLite database containing four tables:
 * - [ExpenseEntity]: expense records
 * - [MemberEntity]: household member profiles
 * - [SyncQueueEntity]: offline sync queue for pending server operations
 * - [BudgetEntity]: monthly budget configuration
 *
 * Exposes DAO interfaces for each table to enable type-safe database access.
 * Current schema version: 6.
 */
@Database(
    entities = [
        ExpenseEntity::class,
        MemberEntity::class,
        SyncQueueEntity::class,
        BudgetEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun expenseDao(): ExpenseDao
    abstract fun memberDao(): MemberDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun budgetDao(): BudgetDao
    
    companion object {
        const val DATABASE_NAME = "homemoney.db"
    }
}
