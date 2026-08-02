package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.Budget
import com.chronie.homemoney.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves the current budget configuration as a reactive [Flow].
 *
 * The flow emits null when no budget has been configured yet.
 * Observers receive automatic updates whenever the budget changes.
 *
 * @param budgetRepository Repository providing budget data.
 */
class GetBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository
) {
    /**
     * Returns a [Flow] that emits the current [Budget] (or null).
     *
     * @return A cold [Flow] of [Budget?] that updates on every budget change.
     */
    operator fun invoke(): Flow<Budget?> {
        return budgetRepository.getBudget()
    }
}
