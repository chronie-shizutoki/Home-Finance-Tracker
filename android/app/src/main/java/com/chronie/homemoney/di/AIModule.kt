package com.chronie.homemoney.di

import android.content.Context
import com.chronie.homemoney.data.remote.api.AIRecordApi
import com.chronie.homemoney.data.repository.AIRecordRepositoryImpl
import com.chronie.homemoney.domain.repository.AIRecordRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * AI Module Dependency Injection
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {
    
    /**
     * Bind AIRecordRepository
     */
    @Binds
    @Singleton
    abstract fun bindAIRecordRepository(
        impl: AIRecordRepositoryImpl
    ): AIRecordRepository
    
    companion object {
        @Qualifier
        @Retention(AnnotationRetention.BINARY)
        annotation class AIRetrofit
        
        @Qualifier
        @Retention(AnnotationRetention.BINARY)
        annotation class AIOkHttpClient
        
        /**
         * Provides AI API OkHttpClient instance
         */
        @Provides
        @Singleton
        @AIOkHttpClient
        fun provideAIOkHttpClient(
            @ApplicationContext context: Context
        ): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    
                    // Get API Key from SharedPreferences
                    val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
                    val apiKey = prefs.getString("siliconflow_api_key", "") ?: ""
                    
                    val request = original.newBuilder()
                        .header("Content-Type", "application/json")
                        .apply {
                            if (apiKey.isNotEmpty()) {
                                header("Authorization", "Bearer $apiKey")
                            }
                        }
                        .method(original.method, original.body)
                        .build()
                    
                    chain.proceed(request)
                }
                .addInterceptor(loggingInterceptor)
                .connectTimeout(600, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
        
        /**
         * Provides AI API Retrofit instance
         */
        @Provides
        @Singleton
        @AIRetrofit
        fun provideAIRetrofit(
            @AIOkHttpClient okHttpClient: OkHttpClient,
            gson: Gson
        ): Retrofit {
            return Retrofit.Builder()
                .baseUrl("https://api.siliconflow.cn/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }
        
        /**
         * Provides AIRecordApi instance
         */
        @Provides
        @Singleton
        fun provideAIRecordApi(
            @AIRetrofit retrofit: Retrofit
        ): AIRecordApi {
            return retrofit.create(AIRecordApi::class.java)
        }
    }
}
