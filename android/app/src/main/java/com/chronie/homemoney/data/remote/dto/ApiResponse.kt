package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Generic wrapper for all API responses from the server.
 *
 * @param T The type of the payload data when the request succeeds.
 * @property data The response payload; null on error.
 * @property message Human-readable success message.
 * @property error Human-readable error description; null on success.
 * @property success True if the server processed the request successfully.
 */
data class ApiResponse<T>(
    @SerializedName("data")
    val data: T? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error")
    val error: String? = null,
    @SerializedName("success")
    val success: Boolean = true
)
