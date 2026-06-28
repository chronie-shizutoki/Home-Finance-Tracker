package com.chronie.homemoney.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Logging Interceptor
 * Logs network requests and responses
 */
class LoggingInterceptor : Interceptor {
    
    companion object {
        private const val TAG = "NetworkLog"
        private const val MAX_LOG_LENGTH = 4000
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Log request
        val requestStartTime = System.currentTimeMillis()
        Log.d(TAG, "→ ${request.method} ${request.url}")
        
        // Log request headers
        request.headers.forEach { (name, value) ->
            // Do not log sensitive information
            if (name.equals("Authorization", ignoreCase = true)) {
                Log.d(TAG, "  $name: [REDACTED]")
            } else {
                Log.d(TAG, "  $name: $value")
            }
        }
        
        // Log request body
        request.body?.let { body ->
            try {
                val buffer = Buffer()
                body.writeTo(buffer)
                val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                val content = buffer.readString(charset)
                logLongString("  Request Body: $content")
            } catch (e: IOException) {
                Log.e(TAG, "  Failed to read request body", e)
            }
        }
        
        // Execute request and log response duration
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "← Request failed: ${e.message}", e)
            throw e
        }
        
        val requestDuration = System.currentTimeMillis() - requestStartTime
        
        // Log response
        Log.d(TAG, "← ${response.code} ${request.url} (${requestDuration}ms)")
        
        // Log response headers
        response.headers.forEach { (name, value) ->
            Log.d(TAG, "  $name: $value")
        }
        
        // Log response body
        val responseBody = response.body
        if (responseBody != null) {
            val source = responseBody.source()
            source.request(Long.MAX_VALUE)
            val buffer = source.buffer
            
            val charset: Charset = responseBody.contentType()?.charset(StandardCharsets.UTF_8) 
                ?: StandardCharsets.UTF_8
            
            if (responseBody.contentLength() != 0L) {
                val content = buffer.clone().readString(charset)
                logLongString("  Response Body: $content")
            }
        }
        
        return response
    }
    
    /**
     * Log long strings in segments to avoid exceeding Android Log's length limit
     */
    private fun logLongString(message: String) {
        if (message.length <= MAX_LOG_LENGTH) {
            Log.d(TAG, message)
        } else {
            var i = 0
            while (i < message.length) {
                val end = minOf(i + MAX_LOG_LENGTH, message.length)
                Log.d(TAG, message.substring(i, end))
                i = end
            }
        }
    }
}
