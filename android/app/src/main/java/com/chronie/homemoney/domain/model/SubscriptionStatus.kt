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
