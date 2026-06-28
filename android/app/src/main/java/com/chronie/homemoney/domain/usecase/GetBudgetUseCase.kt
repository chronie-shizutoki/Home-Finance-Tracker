package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.Budget
import com.chronie.homemoney.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Get Budget Use Case
 */
class GetBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository
) {
    operator fun invoke(): Flow<Budget?> {
        return budgetRepository.getBudget()
    }
}
