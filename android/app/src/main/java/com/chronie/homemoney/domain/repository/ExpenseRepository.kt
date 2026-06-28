package com.chronie.homemoney.domain.repository

import androidx.paging.PagingData
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import kotlinx.coroutines.flow.Flow

/**
 * Expense Repository Interface
 */
interface ExpenseRepository {
    
    /**
     * Get Expense List (Paged)
     */
    fun getExpenses(
        page: Int,
        limit: Int,
        filters: ExpenseFilters
    ): Flow<PagingData<Expense>>
    
    /**
     * Get Expense List (Simple Version, for Initial Implementation)
     */
    suspend fun getExpensesList(
        page: Int,
        limit: Int,
        filters: ExpenseFilters
    ): Result<List<Expense>>
    
    /**
     * Get Expense by ID
     */
    suspend fun getExpenseById(id: String): Result<Expense>
    
    /**
     * Add Expense
     */
    suspend fun addExpense(expense: Expense): Result<Expense>
    
    /**
     * Update Expense Record
     */
    suspend fun updateExpense(expense: Expense): Result<Expense>
    
    /**
     * Delete Expense Record
     */
    suspend fun deleteExpense(id: String): Result<Unit>
    
    /**
     * Get Statistics Data
     */
    suspend fun getStatistics(filters: ExpenseFilters): Result<ExpenseStatistics>
    
    /**
     * Sync with Server
     */
    suspend fun syncWithServer(): Result<Unit>
}
