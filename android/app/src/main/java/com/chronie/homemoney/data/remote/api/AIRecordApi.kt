package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.AIRecordRequest
import com.chronie.homemoney.data.remote.dto.AIRecordResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AI Record Recognition API
 */
interface AIRecordApi {
    
    /**
     * Call AI model to perform record recognition
     */
    @POST("v1/chat/completions")
    suspend fun parseRecord(
        @Body request: AIRecordRequest
    ): Response<AIRecordResponse>
}
