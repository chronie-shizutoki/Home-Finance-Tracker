package com.chronie.homemoney.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chronie.homemoney.R
import com.chronie.homemoney.data.remote.api.MemberApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HealthCheckService : Service() {

    @Inject
    lateinit var memberApi: MemberApi

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var healthCheckJob: Job? = null
    private var consecutiveFailures = 0
    private val maxConsecutiveFailures = 3

    companion object {
        private const val CHANNEL_ID = "health_check_channel"
        private const val ERROR_CHANNEL_ID = "health_check_error_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ERROR_NOTIFICATION_ID = 1002
        private const val CHECK_INTERVAL = 15000L // 15秒

        fun start(context: Context) {
            val intent = Intent(context, HealthCheckService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HealthCheckService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startHealthCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        healthCheckJob?.cancel()
    }

    private fun startHealthCheck() {
        android.util.Log.i("HealthCheckService", "Starting health check service")
        healthCheckJob = serviceScope.launch {
            while (isActive) {
                val hasNetwork = isNetworkAvailable()
                android.util.Log.d("HealthCheckService", "Network available: $hasNetwork")
                
                if (hasNetwork) {
                    checkServerHealth()
                } else {
                    android.util.Log.w("HealthCheckService", "No network connection, skipping health check")
                }
                delay(CHECK_INTERVAL)
            }
        }
    }

    private suspend fun checkServerHealth() {
        try {
            android.util.Log.d("HealthCheckService", "Checking server health...")
            val response = memberApi.checkHealth()
            android.util.Log.d("HealthCheckService", "Health check response: status=${response.status}, database=${response.database}")
            
            if (response.status == "OK" && response.database == "connected") {
                if (consecutiveFailures > 0) {
                    android.util.Log.i("HealthCheckService", "Server connection restored")
                }
                consecutiveFailures = 0
            } else {
                android.util.Log.w("HealthCheckService", "Health check failed: status=${response.status}, database=${response.database}")
                handleHealthCheckFailure()
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthCheckService", "Health check exception: ${e.message}", e)
            handleHealthCheckFailure()
        }
    }

    private fun handleHealthCheckFailure() {
        consecutiveFailures++
        android.util.Log.w("HealthCheckService", "Consecutive failures: $consecutiveFailures/$maxConsecutiveFailures")
        
        if (consecutiveFailures == maxConsecutiveFailures) {
            android.util.Log.e("HealthCheckService", "Max consecutive failures reached, showing notification")
            showConnectionErrorNotification()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo?.isConnected == true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // 创建低优先级通道用于前台服务
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.health_check_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.health_check_channel_description)
            }
            notificationManager.createNotificationChannel(serviceChannel)
            
            // 创建高优先级通道用于错误通知
            val errorChannel = NotificationChannel(
                ERROR_CHANNEL_ID,
                getString(R.string.server_connection_error),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.server_connection_error_message)
            }
            notificationManager.createNotificationChannel(errorChannel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.health_check_running))
        .setContentText(getString(R.string.health_check_monitoring))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun showConnectionErrorNotification() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
                .setContentTitle(getString(R.string.server_connection_error))
                .setContentText(getString(R.string.server_connection_error_message))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            
            android.util.Log.i("HealthCheckService", "Showing error notification")
            notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.e("HealthCheckService", "Failed to show notification", e)
        }
    }
}
