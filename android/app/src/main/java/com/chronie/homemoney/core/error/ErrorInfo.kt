package com.chronie.homemoney.core.error

/**
 * Error Information Data Class
 * Contains all relevant information about an error
 */
data class ErrorInfo(
    val errorType: String,
    val message: String,
    val stackTrace: String,
    val threadName: String,
    val isMainThread: Boolean,
    val timestamp: Long,
    val deviceInfo: Map<String, String>,
    val additionalInfo: Map<String, String>? = null
)
