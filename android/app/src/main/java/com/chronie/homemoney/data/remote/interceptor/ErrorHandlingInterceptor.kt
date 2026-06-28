package com.chronie.homemoney.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Error Handling Interceptor
 * Handles HTTP errors uniformly
 */
class ErrorHandlingInterceptor : Interceptor {
    
    companion object {
        private const val TAG = "ErrorHandling"
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        try {
            val response = chain.proceed(request)

            // Handle HTTP error status codes
            when (response.code) {
                in 200..299 -> {
                    // Successful response, return as is
                    return response
                }
                400 -> {
                    val errorBody = response.peekBody(Long.MAX_VALUE).string()
                    Log.e(TAG, "Bad Request (400): ${request.url}, Response: $errorBody")
                }
                401 -> {
                    val errorBody = response.peekBody(Long.MAX_VALUE).string()
                    Log.e(TAG, "Unauthorized (401): ${request.url}, Response: $errorBody")
                    // Trigger token refresh or redirect to login page here
                    // Logging only, actual handling is done by Repository layer
                }
                403 -> {
                    val errorBody = response.peekBody(Long.MAX_VALUE).string()
                    Log.e(TAG, "Forbidden (403): ${request.url}, Response: $errorBody")
                }
                404 -> {
                    val errorBody = response.peekBody(Long.MAX_VALUE).string()
                    Log.e(TAG, "Not Found (404): ${request.url}, Response: $errorBody")
                }
                429 -> {
                    val errorBody = response.peekBody(Long.MAX_VALUE).string()
                    Log.e(TAG, "Too Many Requests (429): ${request.url}, Response: $errorBody")
                }
                in 500..599 -> {
                    val errorBody = response.peekBody(Long.MAX_VALUE).string()
                    Log.e(TAG, "Server Error (${response.code}): ${request.url}, Response: $errorBody")
                }
                else -> {
                    val errorBody = response.peekBody(Long.MAX_VALUE).string()
                    Log.w(TAG, "HTTP Error (${response.code}): ${request.url}, Response: $errorBody")
                }
            }

            return response

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Request timeout: ${request.url}", e)
            throw IOException("请求超时，请检查网络连接", e)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host: ${request.url}", e)
            throw IOException("无法连接到服务器，请检查网络连接", e)
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${request.url}", e)
            throw IOException("网络错误: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${request.url}", e)
            throw IOException("请求失败: ${e.message}", e)
        }
    }
}
