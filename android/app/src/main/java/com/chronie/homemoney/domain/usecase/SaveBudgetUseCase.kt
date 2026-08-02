package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.Budget
import com.chronie.homemoney.domain.repository.BudgetRepository
import javax.inject.Inject

/**
 * Persists a new budget configuration, replacing any existing one.
 *
 * The budget defines the monthly spending limit, the warning threshold ratio,
 * and whether budget tracking is enabled.
 *
 * @param budgetRepository Repository for storing budget data.
 */
class SaveBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository
) {
    /**
     * Saves the given [Budget] to local storage.
     *
     * @param budget The budget configuration to persist.
     */
    suspend operator fun invoke(budget: Budget) {
        budgetRepository.saveBudget(budget)
    }
}
