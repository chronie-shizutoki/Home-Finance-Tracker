package com.chronie.homemoney.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.model.SortOption
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import javax.inject.Inject

/**
 * Expense List ViewModel
 */
@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ExpenseListUiState())
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()
    

    
    init {
        loadExpenses()
        loadStatistics()
    }
    
    /**
     * Group expenses by date, first globally sort then group
     */
    private fun groupExpensesByDate(expenses: List<Expense>, sortBy: SortOption): Map<String, List<Expense>> {
        // Globally sort expenses by date
        val globallySortedExpenses = when (sortBy) {
            SortOption.DATE_ASC -> expenses.sortedBy { it.date }
            SortOption.DATE_DESC -> expenses.sortedByDescending { it.date }
            SortOption.AMOUNT_ASC -> expenses.sortedBy { it.amount }
            SortOption.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
        }
        
        // Use LinkedHashMap to group by date while maintaining order
        val grouped = LinkedHashMap<String, MutableList<Expense>>()
        
        for (expense in globallySortedExpenses) {
            val date = expense.date
            grouped.computeIfAbsent(date) { mutableListOf() }.add(expense)
        }
        
        // Sort each group by date (ensure order is correct)
        val sortedGroups = grouped.mapValues { (_, dateExpenses) ->
            when (sortBy) {
                SortOption.DATE_ASC -> dateExpenses.sortedBy { it.date }
                SortOption.DATE_DESC -> dateExpenses.sortedByDescending { it.date }
                SortOption.AMOUNT_ASC -> dateExpenses.sortedBy { it.amount }
                SortOption.AMOUNT_DESC -> dateExpenses.sortedByDescending { it.amount }
            }
        }
        
        // If sorted by date, return sorted map by date
        return if (sortBy == SortOption.DATE_ASC || sortBy == SortOption.DATE_DESC) {
            sortedGroups.toSortedMap(compareByDescending { it })
        } else {
            sortedGroups
        }
    }
    
    fun loadExpenses(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val currentState = _uiState.value
            val page = if (refresh) 1 else currentState.currentPage
            val filters = currentState.filters
            
            val result = expenseRepository.getExpensesList(
                page = page,
                limit = currentState.pageSize,
                filters = filters
            )
            
            result.fold(
                onSuccess = { expenses ->
                    val newExpenses = if (refresh) {
                        expenses
                    } else {
                        currentState.expenses + expenses
                    }
                    
                    val grouped = groupExpensesByDate(newExpenses, filters.sortBy)
                    
                    _uiState.update {
                        it.copy(
                            expenses = newExpenses,
                            groupedExpenses = grouped,
                            currentPage = page,
                            hasMore = expenses.size >= currentState.pageSize,
                            isLoading = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                }
            )
        }
    }
    
    fun loadMore() {
        val currentState = _uiState.value
        if (!currentState.isLoading && currentState.hasMore) {
            viewModelScope.launch {
                val nextPage = currentState.currentPage + 1
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val result = expenseRepository.getExpensesList(
                    page = nextPage,
                    limit = currentState.pageSize,
                    filters = currentState.filters
                )
                
                result.fold(
                    onSuccess = { expenses ->
                        // Use distinctBy to prevent duplicate IDs causing key conflicts in LazyColumn
                        val newExpenses = (currentState.expenses + expenses)
                            .distinctBy { it.id }
                        val grouped = groupExpensesByDate(newExpenses, currentState.filters.sortBy)
                        
                        _uiState.update {
                            it.copy(
                                expenses = newExpenses,
                                groupedExpenses = grouped,
                                currentPage = nextPage,
                                hasMore = expenses.size >= currentState.pageSize,
                                isLoading = false,
                                error = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Unknown error"
                            )
                        }
                    }
                )
            }
        }
    }
    
    fun loadStatistics() {
        viewModelScope.launch {
            val filters = _uiState.value.filters
            val result = expenseRepository.getStatistics(filters)
            
            result.fold(
                onSuccess = { statistics ->
                    _uiState.update { it.copy(statistics = statistics) }
                },
                onFailure = { /* Ignore statistics errors */ }
            )
        }
    }
    
    fun updateFilters(filters: ExpenseFilters) {
        _uiState.update {
            it.copy(
                filters = filters,
                currentPage = 1,  // Reset to first page after filters change
                expenses = emptyList()  // Clear existing data
            )
        }
        loadExpenses(refresh = true)
        loadStatistics()
    }
    
    fun updateKeyword(keyword: String) {
        val newFilters = _uiState.value.filters.copy(keyword = keyword.ifBlank { null })
        updateFilters(newFilters)
    }
    
    fun updateType(type: ExpenseType?) {
        val newFilters = _uiState.value.filters.copy(type = type)
        updateFilters(newFilters)
    }
    
    fun updateMonth(month: String?) {
        val newFilters = _uiState.value.filters.copy(month = month)
        updateFilters(newFilters)
    }
    
    fun updateAmountRange(minAmount: Double?, maxAmount: Double?) {
        val newFilters = _uiState.value.filters.copy(
            minAmount = minAmount,
            maxAmount = maxAmount
        )
        updateFilters(newFilters)
    }
    
    fun updateSortOption(sortOption: SortOption) {
        val newFilters = _uiState.value.filters.copy(sortBy = sortOption)
        updateFilters(newFilters)
    }
    
    fun resetFilters() {
        updateFilters(ExpenseFilters())
    }
    
    fun nextPage() {
        _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        loadExpenses()
    }
    
    fun previousPage() {
        if (_uiState.value.currentPage > 1) {
            _uiState.update { it.copy(currentPage = it.currentPage - 1) }
            loadExpenses()
        }
    }
    
    fun goToPage(page: Int) {
        if (page >= 1) {
            _uiState.update { it.copy(currentPage = page) }
            loadExpenses()
        }
    }
    
    fun refresh() {
        _uiState.update { it.copy(currentPage = 1, expenses = emptyList()) }
        loadExpenses(refresh = true)
        loadStatistics()
    }
    
    /**
     * Delete expense record
     */
    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense.id).fold(
                onSuccess = {
                    // Remove deleted expense from current list and grouped data
                    val updatedExpenses = _uiState.value.expenses.filter { it.id != expense.id }
                    val grouped = groupExpensesByDate(updatedExpenses, _uiState.value.filters.sortBy)
                    
                    _uiState.update {
                        it.copy(
                            expenses = updatedExpenses,
                            groupedExpenses = grouped
                        )
                    }
                    
                    // Reload statistics after delete
                    loadStatistics()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Failed to delete expense"
                        )
                    }
                }
            )
        }
    }
}

/**
 * Expense List UI State
 */
data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
    val groupedExpenses: Map<String, List<Expense>> = emptyMap(),
    val statistics: ExpenseStatistics = ExpenseStatistics(
        count = 0,
        totalAmount = 0.0,
        averageAmount = 0.0,
        medianAmount = 0.0,
        minAmount = 0.0,
        maxAmount = 0.0
    ),
    val filters: ExpenseFilters = ExpenseFilters(),
    val currentPage: Int = 1,
    val pageSize: Int = 20,  // Increase page size
    val totalItems: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Expense Date Group Data
 */
data class ExpenseDateGroup(
    val date: String,
    val expenses: List<Expense>,
    val totalAmount: Double,
    val count: Int
)
