package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.ExpenseDto
import com.chronie.homemoney.data.remote.dto.ExpenseListResponse
import com.chronie.homemoney.data.remote.dto.ExpenseStatisticsDto
import retrofit2.Response
import retrofit2.http.*

/**
 * 支出记录API接口
 */
interface ExpenseApi {
    
    /**
     * 获取支出记录列表
     */
    @GET("api/expenses")
    suspend fun getExpenses(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("type") type: String? = null,
        @Query("month") month: String? = null,
        @Query("minAmount") minAmount: Double? = null,
        @Query("maxAmount") maxAmount: Double? = null,
        @Query("sort") sort: String = "dateDesc"
    ): Response<ExpenseListResponse>
    
    /**
     * 添加支出记录
     */
    @POST("api/expenses")
    suspend fun addExpense(
        @Body expense: ExpenseDto
    ): Response<ExpenseDto>
    
    /**
     * 批量添加支出记录
     */
    @POST("api/expenses/batch")
    suspend fun addExpensesBatch(
        @Body expenses: List<ExpenseDto>
    ): Response<List<ExpenseDto>>
    
    /**
     * 删除支出记录
     */
    @DELETE("api/expenses/{id}")
    suspend fun deleteExpense(
        @Path("id") id: Long
    ): Response<Unit>
    
    /**
     * 获取支出统计数据
     */
    @GET("api/expenses/statistics")
    suspend fun getStatistics(
        @Query("keyword") keyword: String? = null,
        @Query("type") type: String? = null,
        @Query("month") month: String? = null,
        @Query("minAmount") minAmount: Double? = null,
        @Query("maxAmount") maxAmount: Double? = null
    ): Response<ExpenseStatisticsDto>
}
