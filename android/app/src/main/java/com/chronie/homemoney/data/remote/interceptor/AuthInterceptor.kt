package com.chronie.homemoney.data.remote.interceptor

import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Authentication Interceptor
 * Automatically adds JWT token to request headers if available
 */
class AuthInterceptor @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : Interceptor {
    
    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val TOKEN_PREFIX = "Bearer "
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // If request already has Authorization header, skip adding it
        if (originalRequest.header(HEADER_AUTHORIZATION) != null) {
            return chain.proceed(originalRequest)
        }
        
        // Get token from SharedPreferences
        val token = sharedPreferences.getString(KEY_TOKEN, null)
        
        // If no token, return original request as is
        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }
        
        // Add Authorization header
        val newRequest = originalRequest.newBuilder()
            .header(HEADER_AUTHORIZATION, "$TOKEN_PREFIX$token")
            .build()
        
        return chain.proceed(newRequest)
    }
}
