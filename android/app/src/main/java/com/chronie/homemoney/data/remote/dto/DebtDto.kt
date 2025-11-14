package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 债务记录数据传输对象
 */
data class DebtDto(
    @SerializedName("id")
    val id: Long? = null,
    
    @SerializedName("type")
    val type: String, // "lend" 或 "borrow"
    
    @SerializedName("person")
    val person: String,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("date")
    val date: String, // ISO 8601 格式
    
    @SerializedName("isRepaid")
    val isRepaid: Boolean = false,
    
    @SerializedName("remark")
    val remark: String? = null,
    
    @SerializedName("createdAt")
    val createdAt: String? = null,
    
    @SerializedName("updatedAt")
    val updatedAt: String? = null
)

/**
 * 债务列表响应
 */
data class DebtListResponse(
    @SerializedName("data")
    val data: List<DebtDto>,
    
    @SerializedName("total")
    val total: Int? = null,
    
    @SerializedName("page")
    val page: Int? = null,
    
    @SerializedName("limit")
    val limit: Int? = null
)
