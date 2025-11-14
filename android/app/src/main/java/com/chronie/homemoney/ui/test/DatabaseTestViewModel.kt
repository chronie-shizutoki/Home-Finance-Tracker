package com.chronie.homemoney.ui.test

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.R
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * 数据库测试 ViewModel
 */
@HiltViewModel
class DatabaseTestViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val application: Application
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DatabaseTestUiState())
    val uiState: StateFlow<DatabaseTestUiState> = _uiState.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    init {
        loadExpenses()
    }
    
    /**
     * 加载支出列表
     */
    private fun loadExpenses() {
        viewModelScope.launch {
            try {
                expenseDao.getAllExpenses()
                    .catch { e ->
                        _uiState.update { it.copy(
                            message = application.getString(R.string.load_failed, e.message ?: ""),
                            isError = true
                        ) }
                    }
                    .collect { expenses ->
                        val count = expenses.size
                        val total = expenses.sumOf { it.amount }
                        
                        _uiState.update { it.copy(
                            expenses = expenses.map { expense ->
                                ExpenseItemUiModel(
                                    id = expense.id,
                                    type = expense.type,
                                    remark = expense.remark ?: "",
                                    amount = expense.amount,
                                    timeFormatted = dateFormat.format(Date(expense.time)),
                                    isSynced = expense.isSynced
                                )
                            },
                            expenseCount = count,
                            totalAmount = total,
                            message = if (count > 0) application.getString(R.string.load_success) else "",
                            isError = false
                        ) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    message = application.getString(R.string.load_failed, e.message ?: ""),
                    isError = true
                ) }
            }
        }
    }
    
    /**
     * 添加测试支出记录
     */
    fun addTestExpense() {
        viewModelScope.launch {
            try {
                val types = listOf("餐饮", "交通", "购物", "娱乐", "医疗", "其他")
                val remarks = listOf("早餐", "午餐", "晚餐", "打车", "地铁", "购物", "看电影", "买药")
                
                val expense = ExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    type = types.random(),
                    remark = remarks.random(),
                    amount = (10..200).random().toDouble(),
                    time = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false,
                    serverId = null
                )
                
                expenseDao.insertExpense(expense)
                
                _uiState.update { it.copy(
                    message = application.getString(R.string.add_success),
                    isError = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    message = application.getString(R.string.add_failed, e.message ?: ""),
                    isError = true
                ) }
            }
        }
    }
    
    /**
     * 清空所有支出记录
     */
    fun clearAllExpenses() {
        viewModelScope.launch {
            try {
                expenseDao.deleteAllExpenses()
                
                _uiState.update { it.copy(
                    message = application.getString(R.string.clear_success),
                    isError = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    message = application.getString(R.string.clear_failed, e.message ?: ""),
                    isError = true
                ) }
            }
        }
    }
}

/**
 * 数据库测试 UI 状态
 */
data class DatabaseTestUiState(
    val expenses: List<ExpenseItemUiModel> = emptyList(),
    val expenseCount: Int = 0,
    val totalAmount: Double = 0.0,
    val message: String = "",
    val isError: Boolean = false
)
