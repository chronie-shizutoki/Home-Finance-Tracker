package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.HealthDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * General API Service Interface
 */
interface ApiService {
    
    /**
     * Health Check
     */
    @GET("api/health/lite")
    suspend fun checkHealth(): HealthDto
}
