package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserSubscriptionDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("memberId")
    val memberId: String,
    @SerializedName("planId")
    val planId: String,
    @SerializedName("startDate")
    val startDate: String,
    @SerializedName("endDate")
    val endDate: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("paymentId")
    val paymentId: String?,
    @SerializedName("autoRenew")
    val autoRenew: Boolean,
    @SerializedName("SubscriptionPlan")
    val subscriptionPlan: SubscriptionPlanDto?
)

data class SubscriptionPlanDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("duration")
    val duration: Int,
    @SerializedName("price")
    val price: Double,
    @SerializedName("period")
    val period: String,
    @SerializedName("isActive")
    val isActive: Boolean
)
