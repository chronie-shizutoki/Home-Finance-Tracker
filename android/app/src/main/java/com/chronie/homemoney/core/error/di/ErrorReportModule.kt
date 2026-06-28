package com.chronie.homemoney.core.error.di

import android.content.Context
import com.chronie.homemoney.core.error.ErrorReportApi
import com.chronie.homemoney.core.error.ErrorReporter
import com.chronie.homemoney.core.error.LogFileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Error Report Module
 * Provides error reporting related dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object ErrorReportModule {

    /**
     * Provides ErrorReportApi instance
     * Reuses existing Retrofit instance
     */
    @Provides
    fun provideErrorReportApi(retrofit: Retrofit): ErrorReportApi {
        return retrofit.create(ErrorReportApi::class.java)
    }

    /**
     * Provides LogFileManager instance
     */
    @Provides
    @Singleton
    fun provideLogFileManager(@ApplicationContext context: Context): LogFileManager {
        return LogFileManager(context)
    }
}