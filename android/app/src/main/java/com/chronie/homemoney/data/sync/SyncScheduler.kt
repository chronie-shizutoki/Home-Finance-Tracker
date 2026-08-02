package com.chronie.homemoney.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.chronie.homemoney.core.network.NetworkMonitor
import com.chronie.homemoney.core.network.NetworkStatus
import com.chronie.homemoney.domain.sync.SyncManager
import com.chronie.homemoney.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates periodic and event-driven synchronization.
 *
 * Responsibilities:
 * - Schedules periodic background sync via WorkManager (every 1 hour).
 * - Monitors network connectivity — triggers an immediate sync when the
 *   device regains network access after being offline.
 * - Provides manual and immediate sync triggers for user-initiated actions.
 *
 * Uses exponential backoff for failed sync attempts to avoid overwhelming
 * the server during temporary outages.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val syncManager: SyncManager
) {
    
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    companion object {
        private const val TAG = "SyncScheduler"
        private const val PERIODIC_SYNC_INTERVAL_HOURS = 1L
    }
    
    /**
     * Initialize sync scheduler
     */
    fun initialize() {
        Log.d(TAG, "Initializing sync scheduler")
        
        // Schedule periodic sync tasks
        schedulePeriodicSync()
        
        // Monitor network status changes
        observeNetworkChanges()
    }
    
    /**
     * Schedule periodic sync tasks
     */
    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        
        Log.d(TAG, "Periodic sync scheduled")
    }
    
    /**
     * Observe network status changes
     */
    private fun observeNetworkChanges() {
        var wasOffline = !networkMonitor.isNetworkAvailable()
        
        networkMonitor.observeNetworkStatus()
            .onEach { status ->
                when (status) {
                    is NetworkStatus.Available -> {
                        if (wasOffline) {
                            Log.d(TAG, "Network restored, triggering sync")
                            wasOffline = false
                            triggerImmediateSync()
                        }
                    }
                    is NetworkStatus.Unavailable -> {
                        Log.d(TAG, "Network unavailable")
                        wasOffline = true
                    }
                }
            }
            .launchIn(scope)
    }
    
    /**
     * Trigger immediate sync
     */
    fun triggerImmediateSync() {
        Log.d(TAG, "Triggering immediate sync")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniqueWork(
            "immediate_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
    
    /**
     * Cancel all sync tasks
     */
    fun cancelAllSync() {
        Log.d(TAG, "Cancelling all sync tasks")
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        workManager.cancelUniqueWork("immediate_sync")
    }
    
    /**
     * Manually trigger sync (in coroutine)
     */
    suspend fun manualSync(): Result<com.chronie.homemoney.domain.model.SyncResult> {
        Log.d(TAG, "Manual sync triggered")
        return syncManager.performFullSync()
    }
}
