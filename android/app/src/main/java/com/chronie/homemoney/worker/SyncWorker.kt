package com.chronie.homemoney.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chronie.homemoney.domain.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager-backed background sync worker.
 *
 * Scheduled by [SyncScheduler] to run periodically (default: every 1 hour)
 * or on-demand when network connectivity is restored. Uses HiltWorker
 * for automatic dependency injection.
 *
 * On failure, returns [Result.retry] so WorkManager applies exponential
 * backoff before retrying. The retry policy is configured in [SyncScheduler].
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "sync_work"
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background sync")
        
        return try {
            // Execute full sync.
            // TODO(future): two SyncWorker instances can run concurrently and both
            // call performFullSync(); also when there are 0 pending changes we still
            // POST all ~1765 localIds, producing two identical giant requests that
            // amplify failures on slow networks. Add a single-flight lock and switch
            // to an incremental/delta sync (hash digests) before shipping.
            val syncResult = syncManager.performFullSync()
            
            if (syncResult.isSuccess) {
                val result = syncResult.getOrNull()
                if (result?.success == true) {
                    Log.d(TAG, "Background sync completed successfully")
                    Result.success()
                } else {
                    Log.w(TAG, "Background sync completed with errors: ${result?.error}")
                    Result.retry()
                }
            } else {
                Log.e(TAG, "Background sync failed", syncResult.exceptionOrNull())
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed with exception", e)
            Result.retry()
        }
    }
}
