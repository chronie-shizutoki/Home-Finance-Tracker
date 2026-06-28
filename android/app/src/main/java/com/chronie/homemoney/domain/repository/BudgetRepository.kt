package com.chronie.homemoney.domain.repository

import com.chronie.homemoney.domain.model.Budget
import com.chronie.homemoney.domain.model.BudgetUsage
import kotlinx.coroutines.flow.Flow

/**
 * Budget Repository Interface
 */
interface BudgetRepository {
    
    /**
     * Get Budget Settings
     */
    fun getBudget(): Flow<Budget?>
    
    /**
     * Get Budget Settings (Once)
     */
    suspend fun getBudgetOnce(): Budget?
    
    /**
     * Save Budget Settings
     */
    suspend fun saveBudget(budget: Budget)
    
    /**
     * Get Current Month Budget Usage
     */
    suspend fun getCurrentMonthUsage(): BudgetUsage?
    
    /**
     * Toggle Budget Enabled
     */
    suspend fun toggleBudgetEnabled(enabled: Boolean)
}
