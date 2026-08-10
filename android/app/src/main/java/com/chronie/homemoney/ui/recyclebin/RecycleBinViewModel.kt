package com.chronie.homemoney.ui.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Recycle Bin screen.
 *
 * Exposes the list of soft-deleted expenses via [deletedExpenses] and the
 * two user actions the bin supports: [restoreExpense] (un-delete) and
 * [permanentDeleteExpense] (hard delete). The 30-day auto-purge is handled
 * out-of-process by [com.chronie.homemoney.worker.TrashCleanupWorker].
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    /** Reactive list of soft-deleted expenses, newest tombstone first. */
    val deletedExpenses: Flow<List<com.chronie.homemoney.domain.model.Expense>> =
        expenseRepository.getDeletedExpenses()

    /** Restores a soft-deleted expense so it reappears in the main list. */
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
}
