package com.chronie.homemoney

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.PlatformContext
import com.chronie.homemoney.core.error.ErrorReporter
import com.chronie.homemoney.data.sync.DeviceSyncManagerFactory
import com.chronie.homemoney.service.FairMemoryManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var deviceSyncManagerFactory: DeviceSyncManagerFactory

    /** Outlives every screen, which is the point: the sync server must not be tied to one. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // Start listening for sync requests as soon as the app exists.
        //
        // This used to happen inside DeviceSyncManagerFactory's `by lazy`, which is only
        // touched when a screen asks for the sync manager - in practice, when the user opens
        // the sync settings page. A device sitting on any other screen therefore had no TCP
        // listener at all: it still answered UDP discovery (a separate socket started by the
        // same call, so it was equally absent) and, once found, refused every connection.
        // From the initiator's side that surfaced as a connect failure with no counterpart
        // log on the receiver, because the receiver genuinely never saw the connection.
        //
        // Off the main thread so a cold start is not charged for building the sync stack.
        appScope.launch {
            try {
                deviceSyncManagerFactory.createDeviceSyncManager()
                Log.d("HomeMoneyApplication", "LAN sync server started at app launch")
            } catch (e: Exception) {
                Log.e("HomeMoneyApplication", "Failed to start LAN sync server", e)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
