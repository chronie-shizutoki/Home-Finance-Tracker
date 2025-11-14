package com.chronie.homemoney.di

import com.chronie.homemoney.core.hybrid.HybridArchitectureManager
import com.chronie.homemoney.core.hybrid.HybridArchitectureManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HybridModule {

    @Binds
    @Singleton
    abstract fun bindHybridArchitectureManager(
        impl: HybridArchitectureManagerImpl
    ): HybridArchitectureManager
}
