package com.chronie.homemoney.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chronie.homemoney.data.local.dao.ExpenseDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodically empties the recycle bin of expired tombstones.
 *
 * Runs once per day and hard-deletes soft-deleted expenses whose `deleted_at`
 * is older than [PURGE_AGE_DAYS] (default 30) **and** that have already been
 * pushed to the server. The sync guard prevents wiping a deletion that has not
 * yet propagated — otherwise the server would re-push the original record.
 *
 * Modeled on [SyncWorker]; scheduled from [com.chronie.homemoney.data.sync.SyncScheduler].
 */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val expenseDao: ExpenseDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "TrashCleanupWorker"
        const val WORK_NAME = "trash_cleanup_work"
        const val PURGE_AGE_DAYS = 30L

        /** Builds the daily periodic request used by [SyncScheduler]. */
        fun periodicRequest() = PeriodicWorkRequestBuilder<TrashCleanupWorker>(
            PURGE_INTERVAL_DAYS,
            TimeUnit.DAYS
        ).build()

        private const val PURGE_INTERVAL_DAYS = 1L
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running recycle bin cleanup")
        return try {
            val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PURGE_AGE_DAYS)
            val removed = expenseDao.purgeExpiredTombstones(threshold)
            Log.d(TAG, "Purged $removed expired trash item(s)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Trash cleanup failed", e)
            Result.retry()
        }
    }
}
