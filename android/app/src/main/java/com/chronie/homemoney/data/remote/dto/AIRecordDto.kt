package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Recognized Expense Record DTO produced by the on-device AI model.
 * (Kept in the dto package because the JSON contract is model-facing.)
 */
data class AIExpenseRecordDto(
    @SerializedName("type")
    val type: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("date")
    val date: String,
    @SerializedName("remark")
    val remark: String
)
