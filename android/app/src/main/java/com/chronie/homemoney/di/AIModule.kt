package com.chronie.homemoney.di

import android.content.Context
import com.chronie.homemoney.data.ocr.DocumentScanProcessor
import com.chronie.homemoney.data.repository.AIRecordRepositoryImpl
import com.chronie.homemoney.data.vlm.MnnVlmEngine
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import com.chronie.homemoney.domain.repository.AIRecordRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AI Module Dependency Injection
 *
 * Everything AI-related runs on-device now: OpenCV document scanning plus
 * the MNN-backed Qwen3-VL engine. The former SiliconFlow cloud Retrofit
 * stack was removed together with its API key handling.
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
        /**
         * Provides the OpenCV document scan processor (stateless besides
         * the OpenCV loader flag, safe as a singleton).
         */
        @Provides
        @Singleton
        fun provideDocumentScanProcessor(
            @ApplicationContext context: Context
        ): DocumentScanProcessor {
            return DocumentScanProcessor(context)
        }

        /**
         * Provides the on-device multimodal LLM engine wrapper.
         */
        @Provides
        @Singleton
        fun provideMnnVlmEngine(): MnnVlmEngine {
            return MnnVlmEngine()
        }

        /**
         * Provides the model download / lifecycle manager.
         */
        @Provides
        @Singleton
        fun provideOnDeviceModelManager(
            @ApplicationContext context: Context
        ): OnDeviceModelManager {
            return OnDeviceModelManager(context)
        }
    }
}
