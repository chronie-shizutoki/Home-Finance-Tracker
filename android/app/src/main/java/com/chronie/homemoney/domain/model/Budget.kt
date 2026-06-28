package com.chronie.homemoney.domain.model

/**
 * Budget Domain Model
 */
data class Budget(
    val monthlyLimit: Double,
    val warningThreshold: Double = 0.8, // Default warning threshold is 80% when budget is exceeded
    val isEnabled: Boolean = false
)

/**
 * Budget Usage
 */
data class BudgetUsage(
    val monthlyLimit: Double,
    val currentSpending: Double,
    val remainingAmount: Double,
    val spendingPercentage: Double,
    val warningThreshold: Double,
    val isOverLimit: Boolean,
    val isNearLimit: Boolean,
    val dailyAverage: Double,
    val recommendedDaily: Double,
    val currentMonth: String
)

/**
 * Budget Status
 */
enum class BudgetStatus {
    NORMAL,      // Normal
    WARNING,     // Warning
    OVER_LIMIT   // Over Limit
}
