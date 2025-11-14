package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 会员数据传输对象
 */
data class MemberDto(
    @SerializedName("id")
    val id: Long? = null,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("isActive")
    val isActive: Boolean = true,
    
    @SerializedName("createdAt")
    val createdAt: String? = null,
    
    @SerializedName("updatedAt")
    val updatedAt: String? = null
)

/**
 * 会员响应
 */
data class MemberResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: MemberDto? = null,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * 订阅计划
 */
data class SubscriptionPlanDto(
    @SerializedName("id")
    val id: String, // UUID格式
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("duration")
    val duration: Int, // 天数
    
    @SerializedName("price")
    val price: Double,
    
    @SerializedName("description")
    val description: String? = null
)

/**
 * 用户订阅
 */
data class UserSubscriptionDto(
    @SerializedName("id")
    val id: String, // UUID格式
    
    @SerializedName("memberId")
    val memberId: Long,
    
    @SerializedName("planId")
    val planId: String, // UUID格式
    
    @SerializedName("status")
    val status: String, // "active", "expired", "cancelled"
    
    @SerializedName("startDate")
    val startDate: String,
    
    @SerializedName("endDate")
    val endDate: String,
    
    @SerializedName("SubscriptionPlan")
    val subscriptionPlan: SubscriptionPlanDto? = null,
    
    @SerializedName("createdAt")
    val createdAt: String? = null,
    
    @SerializedName("updatedAt")
    val updatedAt: String? = null
)

/**
 * 当前订阅响应
 */
data class CurrentSubscriptionResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: UserSubscriptionDto? = null,
    
    @SerializedName("totalActiveSubscriptions")
    val totalActiveSubscriptions: Int = 0,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * 订阅历史响应
 */
data class SubscriptionHistoryResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: List<UserSubscriptionDto> = emptyList(),
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * 订阅计划列表响应
 */
data class SubscriptionPlansResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: List<SubscriptionPlanDto> = emptyList(),
    
    @SerializedName("error")
    val error: String? = null
)
