package com.chronie.homemoney.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移策略
 * 用于处理数据库版本升级
 */
object DatabaseMigrations {
    
    /**
     * 从版本1到版本2的迁移
     * 添加预算表
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建预算表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id INTEGER PRIMARY KEY NOT NULL,
                    monthly_limit REAL NOT NULL,
                    warning_threshold REAL NOT NULL DEFAULT 0.8,
                    is_enabled INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    /**
     * 获取所有迁移策略
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2
        )
    }
}
