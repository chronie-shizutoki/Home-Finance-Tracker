package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * 会员管理API接口
 */
interface MemberApi {
    
    /**
     * 创建或获取会员
     */
    @POST("api/members")
    suspend fun getOrCreateMember(
        @Body request: Map<String, String> // {"username": "xxx"}
    ): Response<MemberResponse>
    
    /**
     * 获取会员信息
     */
    @GET("api/members/{username}")
    suspend fun getMemberInfo(
        @Path("username") username: String
    ): Response<MemberResponse>
    
    /**
     * 更新会员状态
     */
    @PUT("api/members/{id}")
    suspend fun updateMemberStatus(
        @Path("id") id: Long,
        @Body request: Map<String, Boolean> // {"isActive": true/false}
    ): Response<MemberResponse>
    
    /**
     * 获取会员订阅历史
     */
    @GET("api/members/{username}/subscriptions")
    suspend fun getMemberSubscriptions(
        @Path("username") username: String
    ): Response<SubscriptionHistoryResponse>
    
    /**
     * 获取当前订阅
     */
    @GET("api/members/{username}/current-subscription")
    suspend fun getCurrentSubscription(
        @Path("username") username: String
    ): Response<CurrentSubscriptionResponse>
    
    /**
     * 获取订阅计划列表
     */
    @GET("api/members/subscription-plans")
    suspend fun getSubscriptionPlans(): Response<SubscriptionPlansResponse>
    
    /**
     * 取消订阅
     */
    @DELETE("api/members/{username}/subscriptions")
    suspend fun cancelSubscription(
        @Path("username") username: String
    ): Response<ApiResponse<Unit>>
}
