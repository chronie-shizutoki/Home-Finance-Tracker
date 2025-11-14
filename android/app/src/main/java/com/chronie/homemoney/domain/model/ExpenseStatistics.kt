package com.chronie.homemoney.domain.model

/**
 * 支出统计数据
 */
data class ExpenseStatistics(
    val count: Int,
    val totalAmount: Double,
    val averageAmount: Double,
    val medianAmount: Double,
    val minAmount: Double,
    val maxAmount: Double
)
