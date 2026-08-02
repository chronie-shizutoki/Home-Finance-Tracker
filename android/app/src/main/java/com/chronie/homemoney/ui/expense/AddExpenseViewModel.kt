package com.chronie.homemoney.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Add / Edit expense screen.
 *
 * Handles form state management (type, amount, date, remark), validation,
 * and saving. Supports both creating new expenses and editing existing ones
 * (determined by whether [expenseId] is set).
 *
 * After saving, triggers an immediate sync via [SyncScheduler] so the
 * new/updated expense is pushed to the server promptly.
 */
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val syncScheduler: com.chronie.homemoney.data.sync.SyncScheduler,
    val checkLoginStatusUseCase: com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()
    
    /** Sets the expense category type for the form. */
    fun setType(type: ExpenseType) {
        _uiState.update { it.copy(
            selectedType = type,
            typeError = null
        ) }
    }
    
    /** Sets the amount as a raw string (for text field binding). */
    fun setAmount(amount: String) {
        _uiState.update { it.copy(
            amount = amount,
            amountError = null
        ) }
    }
    
    /** Sets the transaction date. */
    fun setDate(date: LocalDate) {
        _uiState.update { it.copy(
            selectedDate = date,
            dateError = null
        ) }
    }
    
    /** Sets the optional remark/note text. */
    fun setRemark(remark: String) {
        _uiState.update { it.copy(remark = remark) }
    }
    
    /**
     * Loads an existing expense into the form for editing.
     *
     * @param expenseId The ID of the expense to edit.
     */
    fun loadExpenseForEdit(expenseId: String) {
        _uiState.update { it.copy(isSaving = true) }
        
        viewModelScope.launch {
            try {
                val expenseResult = expenseRepository.getExpenseById(expenseId)
                
                if (expenseResult.isSuccess) {
                    val expense = expenseResult.getOrThrow()
                    _uiState.update {
                        it.copy(
                            expenseId = expenseId,
                            selectedType = expense.type,
                            amount = expense.amount.toString(),
                            selectedDate = LocalDate.parse(expense.date),
                            remark = expense.remark ?: "",
                            isSaving = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveError = expenseResult.exceptionOrNull()?.message ?: "Expense not found"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }
    
    /**
     * Validates the form and saves the expense.
     *
     * If [expenseId] is set, performs an update; otherwise creates a new record.
     * On success, triggers an immediate sync and calls [onSuccess].
     *
     * @param onSuccess Callback invoked after a successful save.
     * @param onError Callback invoked with the error message on failure.
     */
    fun saveExpense(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!validateForm()) {
            return
        }
        
        val state = _uiState.value
        
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        
        viewModelScope.launch {
            try {
                val dateStr = state.selectedDate.toString()
                
                // Generate a new UUID for new expenses, or reuse the existing ID for edits
                val expenseId = state.expenseId ?: UUID.randomUUID().toString()
                
                val expense = Expense(
                    id = expenseId,
                    type = state.selectedType!!,
                    amount = state.amount.toDouble(),
                    date = dateStr,
                    remark = state.remark.ifBlank { null },
                    isSynced = false
                )
                
                // Choose add or update based on whether we're editing
                val result = if (state.expenseId != null) {
                    expenseRepository.updateExpense(expense)
                } else {
                    expenseRepository.addExpense(expense)
                }
                
                if (result.isSuccess) {
                    _uiState.update { it.copy(isSaving = false) }
                    
                    // Trigger a sync so the new expense reaches the server
                    try {
                        syncScheduler.triggerImmediateSync()
                    } catch (e: Exception) {
                        android.util.Log.w("AddExpenseViewModel", "Failed to trigger sync after saving expense", e)
                    }
                    
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.update { it.copy(
                        isSaving = false,
                        saveError = error
                    ) }
                    onError(error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                _uiState.update { it.copy(
                    isSaving = false,
                    saveError = error
                ) }
                onError(error)
            }
        }
    }
    
    /**
     * Validates the form fields before saving.
     *
     * Checks:
     * - Type is selected (not null).
     * - Amount is non-blank and parses to a positive number.
     *
     * @return True if all validations pass.
     */
    private fun validateForm(): Boolean {
        val state = _uiState.value
        var isValid = true
        
        if (state.selectedType == null) {
            _uiState.update { it.copy(typeError = "TYPE_REQUIRED") }
            isValid = false
        }
        
        if (state.amount.isBlank()) {
            _uiState.update { it.copy(amountError = "AMOUNT_REQUIRED") }
            isValid = false
        } else {
            val amountValue = state.amount.toDoubleOrNull()
            if (amountValue == null || amountValue <= 0) {
                _uiState.update { it.copy(amountError = "AMOUNT_INVALID") }
                isValid = false
            }
        }
        
        return isValid
    }
    
    /** Resets the form state to defaults (for new expense). */
    fun resetState() {
        _uiState.value = AddExpenseUiState()
    }
}

/**
 * UI state for the Add/Edit expense form.
 *
 * @property expenseId Non-null when editing an existing expense; null for new.
 * @property selectedType The selected expense category.
 * @property amount Raw text of the amount field.
 * @property selectedDate The transaction date (defaults to today).
 * @property remark Optional note about the expense.
 * @property isSaving Whether a save operation is in progress.
 * @property saveError Error message from the last failed save attempt.
 * @property typeError Validation error for the type field.
 * @property amountError Validation error for the amount field.
 * @property dateError Validation error for the date field.
 */
data class AddExpenseUiState(
    val expenseId: String? = null,
    val selectedType: ExpenseType? = null,
    val amount: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val remark: String = "",
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val typeError: String? = null,
    val amountError: String? = null,
    val dateError: String? = null
)
