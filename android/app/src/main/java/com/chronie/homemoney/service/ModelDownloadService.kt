package com.chronie.homemoney.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.chronie.homemoney.MainActivity
import com.chronie.homemoney.R
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the model download alive when the
 * app moves to the background.
 *
 * Android restricts network access for backgrounded apps (Doze mode,
 * app standby buckets). A 5+ GB download will inevitably outlive the
 * user's foreground session, so we promote ourselves to a foreground
 * service with a persistent notification. This exempts us from network
 * and CPU restrictions until the download completes, fails, or is
 * cancelled.
 *
 * On Android 16+ (API 36), the notification uses [Notification.ProgressStyle]
 * and requests promotion as a **Live Update**, which gives it a prominent
 * position at the top of the notification shade, an always-expanded card,
 * a countdown chip in the status bar (via [setWhen]), and a progress bar
 * with coloured segments.
 *
 * On older platforms we fall back to a standard [NotificationCompat] progress
 * notification.
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "ModelDownloadService"
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.chronie.homemoney.action.START_MODEL_DOWNLOAD"
        const val ACTION_CANCEL = "com.chronie.homemoney.action.CANCEL_MODEL_DOWNLOAD"

        /** Minimum interval between notification updates (ms). */
        private const val NOTIFICATION_THROTTLE_MS = 500L
    }

    @Inject
    lateinit var modelManager: OnDeviceModelManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var observationJob: Job? = null

    /**
     * Set to true once the first [ModelState.Downloading] emission is
     * observed.  Prevents the service from killing itself when the
     * initial [ModelState.NotDownloaded] value is collected before
     * [OnDeviceModelManager.downloadModel] has transitioned the state.
     */
    @Volatile
    private var downloadStarted = false

    /** Timestamp of the last notification update (for throttling). */
    private var lastNotificationTimeMs = 0L

    // ---- Lifecycle ----

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                Log.d(TAG, "Cancel requested via notification action")
                modelManager.cancelDownload()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                // Avoid duplicate observation jobs if the user taps download
                // while a download is already in progress.
                if (observationJob?.isActive == true) {
                    Log.d(TAG, "Download already in progress, ignoring duplicate start")
                    return START_STICKY
                }
                Log.d(TAG, "Starting model download foreground service")
                downloadStarted = false
                lastNotificationTimeMs = 0L
                startForeground(NOTIFICATION_ID, buildInitialNotification())
                observeStateAndDownload()
                return START_STICKY
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}, stopping")
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- State observation ----

    private fun observeStateAndDownload() {
        observationJob = serviceScope.launch {
            launch { modelManager.downloadModel() }

            modelManager.state.collect { state ->
                when (state) {
                    is OnDeviceModelManager.ModelState.Downloading -> {
                        downloadStarted = true
                        throttledUpdateProgressNotification(state)
                    }
                    is OnDeviceModelManager.ModelState.Ready -> {
                        notifyComplete(
                            getString(R.string.settings_ai_model_status_ready,
                                formatBytes(state.totalBytes))
                        )
                        stopSelf()
                    }
                    is OnDeviceModelManager.ModelState.Failed -> {
                        notifyFailed(state.reason)
                        stopSelf()
                    }
                    is OnDeviceModelManager.ModelState.NotDownloaded -> {
                        // Only stop if the download was already running
                        // (e.g. user cancelled).  At service start the
                        // state is NotDownloaded before downloadModel()
                        // transitions it to Downloading — don't kill the
                        // service prematurely.
                        if (downloadStarted) {
                            // Download was cancelled — remove notification
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE)
                                    as NotificationManager
                            nm.cancel(NOTIFICATION_ID)
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    // ---- Notification construction ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.model_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.model_download_channel_description)
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /** Very first notification posted via [startForeground] — minimal content. */
    private fun buildInitialNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= 36) {
            buildProgressStyleNotification(
                progress = 0f,
                currentFile = "",
                speedBps = 0L,
                etaSeconds = -1L,
                downloadedBytes = 0L,
                totalBytes = OnDeviceModelManager.TOTAL_BYTES
            )
        } else {
            buildLegacyNotification(0f, "")
        }
    }

    /**
     * Builds a **Live Update** notification on Android 16+ using
     * [Notification.ProgressStyle].
     *
     * Key Live Update requirements (all satisfied here):
     * - [POST_PROMOTED_NOTIFICATIONS][android.Manifest.permission.POST_PROMOTED_NOTIFICATIONS] declared in manifest
     * - [Notification.EXTRA_REQUEST_PROMOTED_ONGOING] set to true
     * - Notification is ongoing
     * - Uses ProgressStyle (eligible template)
     * - Has contentTitle set
     * - No custom RemoteViews
     *
     * The progress bar has two segments: completed (accent-coloured) and
     * remaining (grey). [setWhen] is set to the ETA timestamp so the
     * status bar chip shows a countdown automatically.
     */
    @RequiresApi(36)
    private fun buildProgressStyleNotification(
        progress: Float,
        currentFile: String,
        speedBps: Long,
        etaSeconds: Long,
        downloadedBytes: Long,
        totalBytes: Long
    ): Notification {
        val pct = (progress * 100).toInt()
        val progressValue = (progress * 1000).toInt()  // 0..1000 for fine-grained tracking

        // Two-segment progress bar: completed + remaining
        val completedLen = progressValue.coerceAtLeast(0)
        val remainingLen = (1000 - completedLen).coerceAtLeast(0)

        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgress(progressValue)
            .setProgressSegments(listOf(
                Notification.ProgressStyle.Segment(completedLen)
                    .setColor(getColor(R.color.progress_segment_done)),
                Notification.ProgressStyle.Segment(remainingLen)
                    .setColor(getColor(R.color.progress_segment_remaining))
            ))

        // Content text: current file + speed
        val speedText = if (speedBps > 0) formatSpeed(speedBps) else ""
        val contentText = if (currentFile.isNotEmpty()) {
            if (speedText.isNotEmpty()) "$currentFile · $speedText" else currentFile
        } else {
            getString(R.string.loading)
        }

        // Sub-text: ETA
        val subText = if (etaSeconds > 0) {
            getString(R.string.model_download_notification_eta, formatDuration(etaSeconds))
        } else {
            getString(R.string.model_download_notification_eta_calculating)
        }

        // setWhen -> ETA timestamp so the status bar chip counts down.
        // Must point to the future; if ETA is unknown, use now + a large
        // offset so the chip simply shows the icon without a countdown.
        val whenTimestamp = if (etaSeconds > 0) {
            System.currentTimeMillis() + etaSeconds * 1000L
        } else {
            System.currentTimeMillis() + 86_400_000L  // 24h placeholder
        }

        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use the platform Notification.Builder on API 36+ because
        // Notification.ProgressStyle extends Notification.Style (not
        // NotificationCompat.Style) and cannot be passed to the compat
        // builder's setStyle().
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(
                getString(R.string.model_download_notification_progress, pct)
            )
            .setContentText(contentText)
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(whenTimestamp)
            .setStyle(progressStyle)
            // Request promotion as a Live Update (prominent shade position,
            // lock screen card, status bar countdown chip).
            .setExtras(android.os.Bundle().apply {
                putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            })
            .setContentIntent(contentPending)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.cancel),
                    cancelPending
                ).build()
            )
            .build()
    }

    /**
     * Legacy (pre-Android 16) notification with a standard progress bar.
     */
    private fun buildLegacyNotification(progress: Float, detailText: String): Notification {
        val pct = (progress * 100).toInt()

        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.model_download_notification_title))
            .setContentText(if (detailText.isNotEmpty()) detailText else getString(R.string.loading))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, pct == 0)
            .setContentIntent(contentPending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel),
                cancelPending
            )
            .build()
    }

    // ---- Notification update helpers ----

    /**
     * Updates the progress notification, but throttles to at most one
     * update per [NOTIFICATION_THROTTLE_MS] to avoid excessive IPC.
     * Always allows the first update (when [lastNotificationTimeMs] is 0).
     */
    private fun throttledUpdateProgressNotification(state: OnDeviceModelManager.ModelState.Downloading) {
        val now = System.currentTimeMillis()
        if (lastNotificationTimeMs > 0 && now - lastNotificationTimeMs < NOTIFICATION_THROTTLE_MS) {
            return
        }
        lastNotificationTimeMs = now
        updateProgressNotification(state)
    }

    private fun updateProgressNotification(state: OnDeviceModelManager.ModelState.Downloading) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 36) {
            nm.notify(NOTIFICATION_ID, buildProgressStyleNotification(
                progress = state.progress,
                currentFile = state.currentFile,
                speedBps = state.speedBps,
                etaSeconds = state.etaSeconds,
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes
            ))
        } else {
            val pct = (state.progress * 100).toInt()
            val speedText = if (state.speedBps > 0) " · ${formatSpeed(state.speedBps)}" else ""
            val etaText = if (state.etaSeconds > 0) " · ~${formatDuration(state.etaSeconds)}" else ""
            val text = "${state.currentFile}  " +
                "${state.downloadedBytes / (1024 * 1024)} / " +
                "${state.totalBytes / (1024 * 1024)} MB$speedText$etaText"
            nm.notify(NOTIFICATION_ID, buildLegacyNotification(state.progress, text))
        }
    }

    private fun notifyComplete(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.model_download_complete_title))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyFailed(reason: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.model_download_failed_title))
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    // ---- Formatting utilities ----

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format("%.1f GB", gb)
    }

    private fun formatSpeed(bps: Long): String {
        return when {
            bps >= 1_000_000 -> String.format("%.1f MB/s", bps / 1_000_000.0)
            bps >= 1_000 -> String.format("%.0f KB/s", bps / 1_000.0)
            else -> "$bps B/s"
        }
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds >= 3600 -> {
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                getString(R.string.model_download_eta_hours_minutes, h, m)
            }
            seconds >= 60 -> {
                val m = seconds / 60
                getString(R.string.model_download_eta_minutes, m)
            }
            else -> getString(R.string.model_download_eta_seconds, seconds)
        }
    }
}
