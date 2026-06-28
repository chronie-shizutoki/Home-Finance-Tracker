package com.chronie.homemoney.domain.model

import java.time.LocalDate

/**
 * Expense Filters Domain Model
 */
data class ExpenseFilters(
    val keyword: String? = null,
    val type: ExpenseType? = null,
    val month: String? = null,  // Format: YYYY-MM
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val sortBy: SortOption = SortOption.DATE_DESC
)

/**
 * Sort Option
 */
enum class SortOption {
    DATE_ASC,
    DATE_DESC,
    AMOUNT_ASC,
    AMOUNT_DESC
}
