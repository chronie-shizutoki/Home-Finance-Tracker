package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.DebtDto
import com.chronie.homemoney.data.remote.dto.DebtListResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * 债务记录API接口
 */
interface DebtApi {
    
    /**
     * 获取债务记录列表
     */
    @GET("api/debts")
    suspend fun getDebts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 1000000,
        @Query("type") type: String? = null, // "lend" 或 "borrow"
        @Query("isRepaid") isRepaid: Boolean? = null
    ): Response<DebtListResponse>
    
    /**
     * 添加债务记录
     */
    @POST("api/debts")
    suspend fun addDebt(
        @Body debt: DebtDto
    ): Response<DebtDto>
    
    /**
     * 更新债务记录
     */
    @PUT("api/debts/{id}")
    suspend fun updateDebt(
        @Path("id") id: Long,
        @Body debt: DebtDto
    ): Response<DebtDto>
    
    /**
     * 删除债务记录
     */
    @DELETE("api/debts/{id}")
    suspend fun deleteDebt(
        @Path("id") id: Long
    ): Response<Unit>
}
