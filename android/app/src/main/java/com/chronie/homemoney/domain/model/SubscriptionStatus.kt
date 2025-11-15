package com.chronie.homemoney.domain.model

data class SubscriptionStatus(
    val isActive: Boolean,
    val planName: String?,
    val planDescription: String?,
    val startDate: Long?,
    val endDate: Long?,
    val status: String?, // "active", "expired", "canceled"
    val autoRenew: Boolean
)

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val description: String?,
    val duration: Int, // 天数
    val price: Double,
    val period: String, // "monthly", "quarterly", "yearly"
    val isActive: Boolean
)
