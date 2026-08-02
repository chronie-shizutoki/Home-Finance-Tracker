package com.chronie.homemoney.data.remote.dto

/**
 * Data Transfer Object for the server health check response.
 *
 * Returned by [MemberApi.checkHealth] to confirm server and database reachability.
 *
 * @property status The health status of the server (e.g. "ok").
 * @property timestamp The ISO 8601 timestamp when the health check was performed.
 * @property database The connectivity status of the database (e.g. "connected").
 */
data class HealthDto(
    val status: String,
    val timestamp: String,
    val database: String
)
