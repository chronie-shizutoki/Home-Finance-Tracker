package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing the monthly budget configuration.
 *
 * This is a single-row table — only one budget record exists at a time.
 * The primary key is always 1 to enforce the singleton constraint.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    /** Primary key — always 1 since only one budget record exists. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,
    
    /** Maximum monthly spending limit. */
    @ColumnInfo(name = "monthly_limit")
    val monthlyLimit: Double,
    
    /** Warning threshold ratio (0.0-1.0). Defaults to 0.8 = 80% of limit. */
    @ColumnInfo(name = "warning_threshold")
    val warningThreshold: Double = 0.8,
    
    /** Whether budget tracking is currently enabled. */
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = false,
    
    /** Epoch millis timestamp of the last budget modification. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
