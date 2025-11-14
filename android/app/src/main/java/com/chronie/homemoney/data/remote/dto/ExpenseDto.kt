package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 支出记录数据传输对象
 */
data class ExpenseDto(
    @SerializedName("id")
    val id: Long? = null,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("remark")
    val remark: String? = null,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("time")
    val time: String, // ISO 8601 格式
    
    @SerializedName("createdAt")
    val createdAt: String? = null,
    
    @SerializedName("updatedAt")
    val updatedAt: String? = null
)

/**
 * 支出列表响应
 */
data class ExpenseListResponse(
    @SerializedName("data")
    val data: List<ExpenseDto>,
    
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("page")
    val page: Int,
    
    @SerializedName("limit")
    val limit: Int,
    
    @SerializedName("meta")
    val meta: ExpenseMetaDto? = null
)

/**
 * 支出元数据
 */
data class ExpenseMetaDto(
    @SerializedName("uniqueTypes")
    val uniqueTypes: List<String>,
    
    @SerializedName("availableMonths")
    val availableMonths: List<String>
)

/**
 * 支出统计响应
 */
data class ExpenseStatisticsDto(
    @SerializedName("count")
    val count: Int,
    
    @SerializedName("totalAmount")
    val totalAmount: Double,
    
    @SerializedName("averageAmount")
    val averageAmount: Double,
    
    @SerializedName("medianAmount")
    val medianAmount: Double,
    
    @SerializedName("minAmount")
    val minAmount: Double,
    
    @SerializedName("maxAmount")
    val maxAmount: Double,
    
    @SerializedName("typeDistribution")
    val typeDistribution: Map<String, TypeDistributionDto>
)

/**
 * 类型分布统计
 */
data class TypeDistributionDto(
    @SerializedName("count")
    val count: Int,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("percentage")
    val percentage: Int
)
