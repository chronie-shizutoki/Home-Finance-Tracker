package com.chronie.homemoney.domain.model

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val description: String?,
    val duration: Int,  // 天数
    val price: Double,
    val period: String,  // monthly, quarterly, yearly
    val isActive: Boolean
)
