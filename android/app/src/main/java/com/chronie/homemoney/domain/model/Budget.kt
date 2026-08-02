package com.chronie.homemoney.domain.model

/**
 * Monthly budget configuration for the household.
 *
 * Controls the spending limit and provides early warning when approaching
 * or exceeding the budget threshold.
 *
 * @property monthlyLimit The maximum amount allowed for spending in a single month.
 * @property warningThreshold A ratio (0.0–1.0) that triggers a budget warning.
 *                           Defaults to 0.8, meaning a warning fires at 80% of the limit.
 * @property isEnabled Whether budget tracking is currently active.
 */
data class Budget(
    val monthlyLimit: Double,
    val warningThreshold: Double = 0.8,
    val isEnabled: Boolean = false
)

/**
 * Computed budget usage snapshot for a given month.
 *
 * Provides a complete picture of current spending relative to the budget,
 * including daily averages and actionable recommendations.
 *
 * @property monthlyLimit The budget cap for the month.
 * @property currentSpending Total amount spent so far this month.
 * @property remainingAmount How much budget is left (monthlyLimit - currentSpending).
 * @property spendingPercentage Current spending as a percentage of the limit (0–100+).
 * @property warningThreshold The ratio that triggers a warning.
 * @property isOverLimit True when spending has exceeded the monthly limit.
 * @property isNearLimit True when spending has crossed the warning threshold.
 * @property dailyAverage Average daily spending so far in the current month.
 * @property recommendedDaily Recommended remaining daily budget to stay within limit.
 * @property currentMonth The month this usage data applies to, in "YYYY-MM" format.
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
 * Visual status indicator for budget health.
 */
enum class BudgetStatus {
    /** Spending is within the safe zone (below warning threshold). */
    NORMAL,
    /** Spending has crossed the warning threshold but is still under the limit. */
    WARNING,
    /** Spending has exceeded the monthly budget limit. */
    OVER_LIMIT
}
