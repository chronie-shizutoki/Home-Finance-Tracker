package com.chronie.homemoney.domain.model

/**
 * Statistical summary of a set of expense records.
 *
 * Provides aggregate metrics including count, total, average, median,
 * and range (min/max) for a filtered collection of expenses.
 *
 * @property count Number of expense records in the dataset.
 * @property totalAmount Sum of all expense amounts.
 * @property averageAmount Arithmetic mean (totalAmount / count).
 * @property medianAmount Median (50th percentile) expense amount.
 * @property minAmount Smallest expense amount in the dataset.
 * @property maxAmount Largest expense amount in the dataset.
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
 * Predefined time range options for filtering and analyzing expense data.
 */
enum class TimeRange {
    /** The current calendar week (Monday to Sunday). */
    THIS_WEEK,
    /** The current calendar month. */
    THIS_MONTH,
    /** The immediately preceding calendar month. */
    LAST_MONTH,
    /** The current quarter (Q1/Q2/Q3/Q4). */
    THIS_QUARTER,
    /** The current calendar year. */
    THIS_YEAR,
    /** A user-defined custom date range (start and end dates must be specified separately). */
    CUSTOM
}
