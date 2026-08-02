package com.chronie.homemoney.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.domain.model.Budget
import com.chronie.homemoney.domain.model.BudgetStatus
import com.chronie.homemoney.domain.model.BudgetUsage
import com.chronie.homemoney.domain.usecase.GetBudgetUseCase
import com.chronie.homemoney.domain.usecase.GetBudgetUsageUseCase
import com.chronie.homemoney.domain.usecase.SaveBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Budget management screen.
 *
 * Manages the budget configuration (monthly limit, warning threshold, enabled state),
 * tracks current month spending vs. the budget, and exposes the computed
 * [BudgetStatus] for visual indicators.
 *
 * Budget data is observed reactively via [GetBudgetUseCase] which emits a [Flow].
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val getBudgetUseCase: GetBudgetUseCase,
    private val saveBudgetUseCase: SaveBudgetUseCase,
    private val getBudgetUsageUseCase: GetBudgetUsageUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()
    
    init {
        loadBudget()
    }
    
    /**
     * Observes the budget configuration reactively.
     * When a budget is found and enabled, usage data is recalculated.
     * When disabled, the usage display is cleared.
     */
    private fun loadBudget() {
        viewModelScope.launch {
            try {
                getBudgetUseCase().collect { budget ->
                    _uiState.update { it.copy(budget = budget, error = null) }
                    if (budget?.isEnabled == true) {
                        loadBudgetUsage()
                    } else {
                        // Clear usage data when budget tracking is turned off
                        _uiState.update { it.copy(budgetUsage = null) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BudgetViewModel", "Error loading budget", e)
                _uiState.update { it.copy(error = e.message ?: "Unknown error") }
            }
        }
    }
    
    /** Fetches the current month's spending vs. budget. */
    fun loadBudgetUsage() {
        viewModelScope.launch {
            try {
                android.util.Log.d("BudgetViewModel", "Loading budget usage...")
                val usage = getBudgetUsageUseCase()
                android.util.Log.d("BudgetViewModel", "Budget usage loaded: $usage")
                _uiState.update { it.copy(budgetUsage = usage, error = null) }
            } catch (e: Exception) {
                android.util.Log.e("BudgetViewModel", "Error loading budget usage", e)
                _uiState.update { it.copy(error = e.message ?: "Failed to load budget usage", budgetUsage = null) }
            }
        }
    }
    
    /**
     * Saves a new budget configuration.
     *
     * @param monthlyLimit The maximum monthly spending limit.
     * @param warningThreshold Ratio (0-1) that triggers a budget warning.
     * @param isEnabled Whether budget tracking is active.
     */
    fun saveBudget(monthlyLimit: Double, warningThreshold: Double, isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                val budget = Budget(
                    monthlyLimit = monthlyLimit,
                    warningThreshold = warningThreshold,
                    isEnabled = isEnabled
                )
                saveBudgetUseCase(budget)
                _uiState.update { it.copy(budget = budget, error = null) }
                if (isEnabled) {
                    loadBudgetUsage()
                } else {
                    _uiState.update { it.copy(budgetUsage = null) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    /**
     * Toggles budget enabled/disabled, preserving existing limit and threshold.
     */
    fun toggleBudgetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentBudget = _uiState.value.budget
                val budget = Budget(
                    monthlyLimit = currentBudget?.monthlyLimit ?: 0.0,
                    warningThreshold = currentBudget?.warningThreshold ?: 0.8,
                    isEnabled = enabled
                )
                saveBudgetUseCase(budget)
                _uiState.update { it.copy(budget = budget, error = null) }
                if (enabled) {
                    loadBudgetUsage()
                } else {
                    _uiState.update { it.copy(budgetUsage = null) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    /**
     * Computes the current budget status for UI indicators.
     *
     * @return [BudgetStatus.OVER_LIMIT] if spending exceeds limit,
     *         [BudgetStatus.WARNING] if approaching threshold,
     *         [BudgetStatus.NORMAL] otherwise.
     */
    fun getBudgetStatus(): BudgetStatus {
        val usage = _uiState.value.budgetUsage ?: return BudgetStatus.NORMAL
        return when {
            usage.isOverLimit -> BudgetStatus.OVER_LIMIT
            usage.isNearLimit -> BudgetStatus.WARNING
            else -> BudgetStatus.NORMAL
        }
    }
    
    /** Refreshes current month budget usage. */
    fun refresh() {
        loadBudgetUsage()
    }
}

/**
 * UI state for the Budget screen.
 *
 * @property budget The current budget configuration (null if not yet set).
 * @property budgetUsage Computed spending snapshot for the current month.
 * @property error Error message to display, or null.
 */
data class BudgetUiState(
    val budget: Budget? = null,
    val budgetUsage: BudgetUsage? = null,
    val error: String? = null
)
