package com.chronie.homemoney.core.error

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Error Report API Interface
 * Defines the endpoint for reporting error information to the backend server
 */
interface ErrorReportApi {

    /**
     * Report error information to the server
     */
    @POST("api/error/report")
    suspend fun reportError(@Body request: ErrorReportRequest): Response<Unit>
}

/**
 * Error Report Request Data Class
 * Contains all error information to be reported to the server
 */
data class ErrorReportRequest(
    /**
     * Error type
     */
    val errorType: String,

    /**
     * Error message
     */
    val message: String,

    /**
     * Stack trace information
     */
    val stackTrace: String? = null,

    /**
     * Timestamp of the error occurrence
     */
    val timestamp: Long,

    /**
     * Device information
     */
    val deviceInfo: Map<String, String>? = null,

    /**
     * Application version name
     */
    val appVersion: String? = null,

    /**
     * Application build version number
     */
    val appBuild: String? = null,

    /**
     * Environment (e.g., production, development)
     */
    val environment: String? = null,

    /**
     * Member ID (optional)
     */
    val memberId: String? = null,

    /**
     * Additional information (optional)
     */
    val additionalInfo: Map<String, String>? = null
)
