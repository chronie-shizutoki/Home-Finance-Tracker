package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.BudgetUsage
import com.chronie.homemoney.domain.repository.BudgetRepository
import javax.inject.Inject

/**
 * Get Budget Usage Use Case
 */
class GetBudgetUsageUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository
) {
    suspend operator fun invoke(): BudgetUsage? {
        return budgetRepository.getCurrentMonthUsage()
    }
}
