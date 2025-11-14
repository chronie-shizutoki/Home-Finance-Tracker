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
 * 支出列表 ViewModel
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
    
    fun loadExpenses(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val filters = _uiState.value.filters
            val result = expenseRepository.getExpensesList(
                page = _uiState.value.currentPage,
                limit = _uiState.value.pageSize,
                filters = filters
            )
            
            result.fold(
                onSuccess = { expenses ->
                    _uiState.update {
                        it.copy(
                            expenses = expenses,
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
                currentPage = 1  // 重置到第一页
            )
        }
        loadExpenses()
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
        loadExpenses(refresh = true)
        loadStatistics()
    }
}

/**
 * 支出列表 UI 状态
 */
data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
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
    val pageSize: Int = 10,
    val isLoading: Boolean = false,
    val error: String? = null
)
