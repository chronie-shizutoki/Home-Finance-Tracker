package com.chronie.homemoney.ui.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Recycle Bin screen.
 *
 * Exposes the list of soft-deleted expenses via [deletedExpenses] and supports
 * both single-item and batch operations (restore / permanent delete).
 * Multi-select state is managed via [selectedIds] and [isSelectMode].
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    /** Reactive list of soft-deleted expenses, newest tombstone first. */
    val deletedExpenses: Flow<List<com.chronie.homemoney.domain.model.Expense>> =
        expenseRepository.getDeletedExpenses()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    private val _pendingBatchAction = MutableStateFlow<BatchAction?>(null)
    val pendingBatchAction: StateFlow<BatchAction?> = _pendingBatchAction.asStateFlow()

    /** Restores a single soft-deleted expense so it reappears in the main list. */
    fun restoreExpense(id: String) {
        viewModelScope.launch {
            expenseRepository.restoreExpense(id)
        }
    }

    /** Permanently removes a single soft-deleted expense. */
    fun permanentDeleteExpense(id: String) {
        viewModelScope.launch {
            expenseRepository.permanentDeleteExpense(id)
        }
    }

    fun enterSelectMode() {
        _isSelectMode.value = true
        _selectedIds.value = emptySet()
    }

    fun exitSelectMode() {
        _isSelectMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: String) {
        _selectedIds.value = if (id in _selectedIds.value) {
            _selectedIds.value - id
        } else {
            _selectedIds.value + id
        }
        if (_selectedIds.value.isEmpty()) {
            _isSelectMode.value = false
        }
    }

    fun selectAll(ids: List<String>) {
        _selectedIds.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun restoreSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            expenseRepository.restoreExpenses(ids)
            exitSelectMode()
        }
    }

    fun permanentDeleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            expenseRepository.permanentDeleteExpenses(ids)
            exitSelectMode()
        }
    }

    fun restoreAll() {
        viewModelScope.launch {
            expenseRepository.restoreAllExpenses()
            exitSelectMode()
        }
    }

    fun permanentDeleteAll() {
        viewModelScope.launch {
            expenseRepository.permanentDeleteAllExpenses()
            exitSelectMode()
        }
    }

    fun requestBatchAction(action: BatchAction) {
        _pendingBatchAction.value = action
    }

    fun dismissBatchAction() {
        _pendingBatchAction.value = null
    }
}

enum class BatchAction {
    RESTORE_ALL,
    DELETE_ALL,
    RESTORE_SELECTED,
    DELETE_SELECTED
}