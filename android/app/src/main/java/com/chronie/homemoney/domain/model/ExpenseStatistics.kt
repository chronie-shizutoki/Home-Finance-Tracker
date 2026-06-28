package com.chronie.homemoney.domain.model

/**
 * Expense Statistics Domain Model
 */
data class ExpenseStatistics(
    val count: Int,
    val totalAmount: Double,
    val averageAmount: Double,
    val medianAmount: Double,
    val minAmount: Double = 0.0,
    val maxAmount: Double = 0.0
)

/**
 * Time Range Type
 */
enum class TimeRange {
    THIS_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    THIS_QUARTER,
    THIS_YEAR,
    CUSTOM
}
