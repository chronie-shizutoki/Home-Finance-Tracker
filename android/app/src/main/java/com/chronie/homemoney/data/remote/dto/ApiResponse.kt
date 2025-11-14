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
    
    @SerializedName("version")
    val version: String? = null,
    
    @SerializedName("uptime")
    val uptime: String? = null,
    
    @SerializedName("environment")
    val environment: EnvironmentInfo? = null,
    
    @SerializedName("resources")
    val resources: ResourcesInfo? = null,
    
    @SerializedName("services")
    val services: ServicesInfo? = null,
    
    @SerializedName("paths")
    val paths: PathsInfo? = null
)

/**
 * 环境信息
 */
data class EnvironmentInfo(
    @SerializedName("nodeVersion")
    val nodeVersion: String? = null,
    
    @SerializedName("nodeEnv")
    val nodeEnv: String? = null,
    
    @SerializedName("platform")
    val platform: String? = null,
    
    @SerializedName("arch")
    val arch: String? = null,
    
    @SerializedName("hostname")
    val hostname: String? = null
)

/**
 * 资源信息
 */
data class ResourcesInfo(
    @SerializedName("memory")
    val memory: MemoryInfo? = null,
    
    @SerializedName("cpu")
    val cpu: CpuInfo? = null
)

/**
 * 内存信息
 */
data class MemoryInfo(
    @SerializedName("rss")
    val rss: String? = null,
    
    @SerializedName("heapTotal")
    val heapTotal: String? = null,
    
    @SerializedName("heapUsed")
    val heapUsed: String? = null,
    
    @SerializedName("external")
    val external: String? = null
)

/**
 * CPU信息
 */
data class CpuInfo(
    @SerializedName("count")
    val count: Int? = null,
    
    @SerializedName("model")
    val model: String? = null,
    
    @SerializedName("usagePercent")
    val usagePercent: String? = null,
    
    @SerializedName("systemLoad")
    val systemLoad: SystemLoadInfo? = null
)

/**
 * 系统负载信息
 */
data class SystemLoadInfo(
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("rawValue")
    val rawValue: List<String>? = null
)

/**
 * 服务状态信息
 */
data class ServicesInfo(
    @SerializedName("database")
    val database: DatabaseStatus? = null,
    
    @SerializedName("fileSystem")
    val fileSystem: FileSystemStatus? = null
)

/**
 * 数据库状态
 */
data class DatabaseStatus(
    @SerializedName("status")
    val status: String? = null,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * 文件系统状态
 */
data class FileSystemStatus(
    @SerializedName("serverDirExists")
    val serverDirExists: Boolean? = null,
    
    @SerializedName("clientDistExists")
    val clientDistExists: Boolean? = null,
    
    @SerializedName("configExists")
    val configExists: Boolean? = null
)

/**
 * 路径信息
 */
data class PathsInfo(
    @SerializedName("serverDir")
    val serverDir: String? = null,
    
    @SerializedName("clientDistPath")
    val clientDistPath: String? = null,
    
    @SerializedName("serverConfigPath")
    val serverConfigPath: String? = null
)
