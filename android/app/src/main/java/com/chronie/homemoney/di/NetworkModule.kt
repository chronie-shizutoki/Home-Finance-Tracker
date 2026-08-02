package com.chronie.homemoney.di

import android.content.Context
import android.content.SharedPreferences
import com.chronie.homemoney.data.remote.api.*
import com.chronie.homemoney.data.remote.interceptor.AuthInterceptor
import com.chronie.homemoney.data.remote.interceptor.ErrorHandlingInterceptor
import com.chronie.homemoney.data.remote.interceptor.LoggingInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt DI module providing networking dependencies.
 *
 * Configures two OkHttp/Retrofit stacks:
 * 1. **Default** — 5s timeouts, connection retries enabled. Used for all CRUD APIs.
 * 2. **HealthCheck** — 2s timeouts, no retries. Used for lightweight server health pings
 *    that must fail fast to avoid blocking.
 *
 * Interceptors are chained in a specific order:
 * - ErrorHandlingInterceptor (innermost: catches and wraps network errors)
 * - AuthInterceptor (adds Bearer token)
 * - LoggingInterceptor (outermost: logs full request/response)
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    // Note: BASE_URL does not include /api/ prefix, as each API interface will add it automatically
    private const val BASE_URL = "http://192.168.10.9:3010/"
    private const val CONNECT_TIMEOUT = 5L
    private const val READ_TIMEOUT = 5L
    private const val WRITE_TIMEOUT = 5L
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()
    }
    
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        sharedPreferences: SharedPreferences
    ): AuthInterceptor {
        return AuthInterceptor(sharedPreferences)
    }
    
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): LoggingInterceptor {
        return LoggingInterceptor()
    }
    
    @Provides
    @Singleton
    fun provideErrorHandlingInterceptor(): ErrorHandlingInterceptor {
        return ErrorHandlingInterceptor()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: LoggingInterceptor,
        errorHandlingInterceptor: ErrorHandlingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(errorHandlingInterceptor) // Error handling interceptor first to catch any errors
            .addInterceptor(authInterceptor) // Authentication interceptor
            .addInterceptor(loggingInterceptor) // Logging interceptor last to log all requests and responses
            .retryOnConnectionFailure(true) // Retry on connection failure
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideExpenseApi(retrofit: Retrofit): ExpenseApi {
        return retrofit.create(ExpenseApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideMemberApi(retrofit: Retrofit): MemberApi {
        return retrofit.create(MemberApi::class.java)
    }
    
    @Provides
    @Singleton
    @javax.inject.Named("HealthCheckClient")
    fun provideHealthCheckOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: LoggingInterceptor,
        errorHandlingInterceptor: ErrorHandlingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS) // Health check uses shorter timeouts
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .addInterceptor(errorHandlingInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(false) // Health check does not retry on connection failure
            .build()
    }
    
    @Provides
    @Singleton
    @javax.inject.Named("HealthCheckRetrofit")
    fun provideHealthCheckRetrofit(
        @javax.inject.Named("HealthCheckClient") okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    @javax.inject.Named("HealthCheckApi")
    fun provideHealthCheckMemberApi(
        @javax.inject.Named("HealthCheckRetrofit") retrofit: Retrofit
    ): MemberApi {
        return retrofit.create(MemberApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
}
