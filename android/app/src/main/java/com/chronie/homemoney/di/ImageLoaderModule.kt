package com.chronie.homemoney.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.chronie.homemoney.core.coil.DataUriFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.Path.Companion.toOkioPath
import javax.inject.Singleton

/**
 * Dagger Hilt module that provides a singleton [ImageLoader] for Coil 3.
 *
 * Configures:
 * - A custom [DataUriFetcher] factory for rendering data: URI images.
 * - OkHttp-based network fetching for remote images.
 * - In-memory cache capped at 25% of the available heap.
 * - Disk cache limited to 50 MB, stored in the app's cache directory.
 *
 * Installed in [SingletonComponent] so the same [ImageLoader] is shared
 * across the entire application lifecycle.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(DataUriFetcher.Factory())
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
