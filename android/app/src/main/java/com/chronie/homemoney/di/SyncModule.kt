package com.chronie.homemoney.di

import android.content.Context
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import com.chronie.homemoney.data.remote.api.ExpenseApi
import com.chronie.homemoney.data.sync.SyncManagerImpl
import com.chronie.homemoney.data.sync.SyncScheduler
import com.chronie.homemoney.core.network.NetworkMonitor
import com.chronie.homemoney.domain.sync.SyncManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 同步模块
 * 提供数据同步相关的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    
    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        expenseDao: ExpenseDao,
        syncQueueDao: SyncQueueDao,
        expenseApi: ExpenseApi,
        gson: Gson
    ): SyncManager {
        return SyncManagerImpl(
            context = context,
            expenseDao = expenseDao,
            syncQueueDao = syncQueueDao,
            expenseApi = expenseApi,
            gson = gson
        )
    }
    
    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): NetworkMonitor {
        return NetworkMonitor(context)
    }
    
    @Provides
    @Singleton
    fun provideSyncScheduler(
        @ApplicationContext context: Context,
        networkMonitor: NetworkMonitor,
        syncManager: SyncManager
    ): SyncScheduler {
        return SyncScheduler(
            context = context,
            networkMonitor = networkMonitor,
            syncManager = syncManager
        )
    }
}
