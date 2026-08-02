package com.chronie.homemoney.domain.model

import java.time.LocalDate

/**
 * Flexible filter criteria for querying and sorting expense records.
 *
 * All properties are optional — filters are only applied when non-null.
 * Combine multiple filters to narrow down the expense list.
 *
 * @property keyword Search term matched against expense remarks (case-insensitive substring).
 * @property type Filter by a specific expense category.
 * @property month Filter by month in "YYYY-MM" format.
 * @property minAmount Filter expenses with amount >= this value.
 * @property maxAmount Filter expenses with amount <= this value.
 * @property startDate Filter expenses on or after this date.
 * @property endDate Filter expenses on or before this date.
 * @property sortBy The sort order for results, defaults to newest first.
 */
data class ExpenseFilters(
    val keyword: String? = null,
    val type: ExpenseType? = null,
    val month: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val sortBy: SortOption = SortOption.DATE_DESC
)

/**
 * Sort order options for expense list queries.
 */
enum class SortOption {
    /** Sort by date from oldest to newest. */
    DATE_ASC,
    /** Sort by date from newest to oldest (default). */
    DATE_DESC,
    /** Sort by amount from smallest to largest. */
    AMOUNT_ASC,
    /** Sort by amount from largest to smallest. */
    AMOUNT_DESC
}
