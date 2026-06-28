package com.chronie.homemoney

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.chronie.homemoney.core.error.ErrorReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HomeMoneyApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var errorReporter: ErrorReporter

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()

        // Initialize error reporting system
        try {
            errorReporter.initialize()
            Log.d("HomeMoneyApplication", "Error reporting system initialized")
        } catch (e: Exception) {
            // Even if error reporting system initialization fails, ensure app can run
            Log.e("HomeMoneyApplication", "Failed to initialize error reporting system", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
