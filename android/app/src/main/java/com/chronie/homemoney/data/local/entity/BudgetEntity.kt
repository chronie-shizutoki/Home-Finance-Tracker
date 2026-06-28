package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Budget Entity Class
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // Only one record
    
    @ColumnInfo(name = "monthly_limit")
    val monthlyLimit: Double,
    
    @ColumnInfo(name = "warning_threshold")
    val warningThreshold: Double = 0.8,
    
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = false,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
