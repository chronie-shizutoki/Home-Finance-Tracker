package com.chronie.homemoney.ui.test

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.data.remote.api.ApiService
import com.chronie.homemoney.data.remote.api.ExpenseApi
import com.chronie.homemoney.data.remote.api.MemberApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * API测试ViewModel
 */
@HiltViewModel
class ApiTestViewModel @Inject constructor(
    private val apiService: ApiService,
    private val expenseApi: ExpenseApi,
    private val memberApi: MemberApi
) : ViewModel() {
    
    companion object {
        private const val TAG = "ApiTestViewModel"
    }
    
    private val _testResults = MutableStateFlow<List<String>>(emptyList())
    val testResults: StateFlow<List<String>> = _testResults.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * 测试健康检查API
     */
    fun testHealthCheck() {
        viewModelScope.launch {
            _isLoading.value = true
            addResult("开始测试健康检查API...")
            
            try {
                val response = apiService.healthCheck()
                if (response.isSuccessful) {
                    val data = response.body()
                    addResult("✓ 健康检查成功")
                    addResult("  状态: ${data?.status}")
                    addResult("  版本: ${data?.version}")
                    addResult("  运行时间: ${data?.uptime}")
                    addResult("  时间戳: ${data?.timestamp}")
                    data?.environment?.let { env ->
                        addResult("  环境: ${env.nodeEnv} (${env.platform})")
                    }
                    data?.services?.database?.let { db ->
                        addResult("  数据库: ${db.status}")
                    }
                    Log.d(TAG, "Health check successful: $data")
                } else {
                    addResult("✗ 健康检查失败: ${response.code()} ${response.message()}")
                    Log.e(TAG, "Health check failed: ${response.code()}")
                }
            } catch (e: Exception) {
                addResult("✗ 健康检查异常: ${e.message}")
                Log.e(TAG, "Health check error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 测试获取支出列表API
     */
    fun testGetExpenses() {
        viewModelScope.launch {
            _isLoading.value = true
            addResult("开始测试获取支出列表API...")
            
            try {
                val response = expenseApi.getExpenses(page = 1, limit = 10)
                if (response.isSuccessful) {
                    val data = response.body()
                    addResult("✓ 获取支出列表成功")
                    addResult("  总数: ${data?.total}")
                    addResult("  当前页: ${data?.page}")
                    addResult("  每页数量: ${data?.limit}")
                    addResult("  记录数: ${data?.data?.size}")
                    data?.data?.take(3)?.forEach { expense ->
                        addResult("  - ${expense.type}: ¥${expense.amount} (${expense.time})")
                    }
                    Log.d(TAG, "Get expenses successful: ${data?.data?.size} items")
                } else {
                    addResult("✗ 获取支出列表失败: ${response.code()} ${response.message()}")
                    Log.e(TAG, "Get expenses failed: ${response.code()}")
                }
            } catch (e: Exception) {
                addResult("✗ 获取支出列表异常: ${e.message}")
                Log.e(TAG, "Get expenses error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 测试获取订阅计划API
     */
    fun testGetSubscriptionPlans() {
        viewModelScope.launch {
            _isLoading.value = true
            addResult("开始测试获取订阅计划API...")
            
            try {
                val response = memberApi.getSubscriptionPlans()
                if (response.isSuccessful) {
                    val data = response.body()
                    addResult("✓ 获取订阅计划成功")
                    addResult("  计划数量: ${data?.data?.size}")
                    data?.data?.forEach { plan ->
                        addResult("  - ${plan.name}: ¥${plan.price} (${plan.duration}天)")
                    }
                    Log.d(TAG, "Get subscription plans successful: ${data?.data?.size} plans")
                } else {
                    addResult("✗ 获取订阅计划失败: ${response.code()} ${response.message()}")
                    Log.e(TAG, "Get subscription plans failed: ${response.code()}")
                }
            } catch (e: Exception) {
                addResult("✗ 获取订阅计划异常: ${e.message}")
                Log.e(TAG, "Get subscription plans error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 运行所有测试
     */
    fun runAllTests() {
        viewModelScope.launch {
            clearResults()
            addResult("=== 开始运行所有API测试 ===\n")
            
            testHealthCheck()
            kotlinx.coroutines.delay(1000)
            
            testGetExpenses()
            kotlinx.coroutines.delay(1000)
            
            testGetSubscriptionPlans()
            
            addResult("\n=== 所有测试完成 ===")
        }
    }
    
    /**
     * 清除测试结果
     */
    fun clearResults() {
        _testResults.value = emptyList()
    }
    
    private fun addResult(result: String) {
        _testResults.value = _testResults.value + result
    }
}
