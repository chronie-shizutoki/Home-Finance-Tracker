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
     * 示例: 添加新字段或新表时使用
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 示例: 添加新字段
            // database.execSQL("ALTER TABLE expenses ADD COLUMN new_field TEXT")
            
            // 示例: 创建新表
            // database.execSQL("""
            //     CREATE TABLE IF NOT EXISTS new_table (
            //         id TEXT PRIMARY KEY NOT NULL,
            //         field1 TEXT NOT NULL
            //     )
            // """.trimIndent())
        }
    }
    
    /**
     * 获取所有迁移策略
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            // MIGRATION_1_2  // 当需要时取消注释
        )
    }
}
