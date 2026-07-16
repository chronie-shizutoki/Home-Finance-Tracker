package com.chronie.homemoney

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.PlatformContext
import com.chronie.homemoney.core.error.ErrorReporter
import com.chronie.homemoney.service.FairMemoryManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HomeMoneyApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var errorReporter: ErrorReporter

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var fairMemoryManager: FairMemoryManager

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()

        try {
            errorReporter.initialize()
            Log.d("HomeMoneyApplication", "Error reporting system initialized")
        } catch (e: Exception) {
            Log.e("HomeMoneyApplication", "Failed to initialize error reporting system", e)
        }

        try {
            fairMemoryManager.initialize()
            Log.d("HomeMoneyApplication", "FairMemoryManager initialized")
        } catch (e: Exception) {
            Log.e("HomeMoneyApplication", "Failed to initialize FairMemoryManager", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
