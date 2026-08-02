package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import com.chronie.homemoney.domain.repository.ExpenseRepository
import javax.inject.Inject

/**
 * Computes aggregate statistics (count, total, average, median, min/max)
 * for a filtered set of expense records.
 *
 * The filters determine which expenses are included in the calculation.
 * Pass an empty [ExpenseFilters] to include all expenses.
 *
 * @param expenseRepository Repository providing the raw expense data.
 */
class GetStatisticsUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository
) {
    /**
     * Retrieves expense statistics for the given filter criteria.
     *
     * @param filters Criteria for narrowing the expense dataset.
     * @return [Result.success] with the computed [ExpenseStatistics],
     *         or [Result.failure] if the query fails.
     */
    suspend operator fun invoke(filters: ExpenseFilters): Result<ExpenseStatistics> {
        return expenseRepository.getStatistics(filters)
    }
}
