package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 通用API响应包装类
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean = true,
    
    @SerializedName("data")
    val data: T? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("message")
    val message: String? = null
)

/**
 * 健康检查响应
 */
data class HealthCheckResponse(
    @SerializedName("status")
    val status: String,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("service")
    val service: String,
    
    @SerializedName("version")
    val version: String? = null,
    
    @SerializedName("database")
    val database: DatabaseStatus? = null
)

/**
 * 数据库状态
 */
data class DatabaseStatus(
    @SerializedName("connected")
    val connected: Boolean,
    
    @SerializedName("message")
    val message: String? = null
)
