package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.BudgetUsage
import com.chronie.homemoney.domain.repository.BudgetRepository
import javax.inject.Inject

/**
 * Computes how much of the monthly budget has been used so far.
 *
 * Returns a [BudgetUsage] snapshot containing the current spending,
 * remaining amount, daily averages, and actionable recommendations
 * for staying within the monthly limit.
 *
 * @param budgetRepository Repository providing budget and spending data.
 */
class GetBudgetUsageUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository
) {
    /**
     * Returns a snapshot of the current month's budget usage,
     * or null if no budget has been configured.
     *
     * @return [BudgetUsage] with spending analysis, or null.
     */
    suspend operator fun invoke(): BudgetUsage? {
        return budgetRepository.getCurrentMonthUsage()
    }
}
