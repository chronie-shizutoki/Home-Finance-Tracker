package com.chronie.homemoney.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.chronie.homemoney.R
import com.chronie.homemoney.data.remote.api.MemberApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Server health check daemon that periodically pings the backend.
 *
 * Polls the server every 5 seconds (2-second timeout per request).
 * When network is unavailable, skips the health check to avoid
 * unnecessary failures. After 3 consecutive failures, shows a Toast
 * to alert the user that the server may be down.
 *
 * Uses a lightweight Retrofit client with shorter timeouts (2s vs 5s)
 * so health checks never block for long.
 */
@Singleton
class HealthCheckService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:javax.inject.Named("HealthCheckApi") private val memberApi: MemberApi
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var healthCheckJob: Job? = null
    private var consecutiveFailures = 0
    private val maxConsecutiveFailures = 3

    companion object {
        private const val CHECK_INTERVAL = 5000L // 5 seconds
        // TODO(future): 2s is too aggressive. On Android 17 (LNP) a missing
        // ACCESS_LOCAL_NETWORK yields a silent socket timeout that this window
        // masks as a plain "server unreachable", hiding the real cause. Once the
        // permission flow is wired, distinguish NOT_CONNECTED from PERMISSION_BLOCKED
        // (e.g. via android_getnetworkblockedreason(sockFd)) and surface a targeted
        // message. Consider raising this or classifying the failure before retry.
        private const val HEALTH_CHECK_TIMEOUT = 2000L // 2 seconds timeout
    }

    fun start() {
        if (healthCheckJob?.isActive == true) {
            android.util.Log.d("HealthCheckService", "Service already running")
            return
        }
        startHealthCheck()
    }

    fun stop() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        consecutiveFailures = 0
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
                delay(CHECK_INTERVAL.milliseconds)
            }
        }
    }

    private suspend fun checkServerHealth() {
        try {
            android.util.Log.d("HealthCheckService", "Checking server health...")
            
            // Use timeout mechanism to avoid long blocking. NOTE: the 2s ceiling
            // (HEALTH_CHECK_TIMEOUT) currently swallows LNP permission errors as
            // generic timeouts - see the TODO on that constant.
            val response = withTimeout(HEALTH_CHECK_TIMEOUT.milliseconds) {
                memberApi.checkHealth()
            }
            
            android.util.Log.d("HealthCheckService", "Health check response: status=${response.status}, database=${response.database}")
            
            if (response.status == "OK" && response.database == "connected") {
                if (consecutiveFailures > 0) {
                    android.util.Log.i("HealthCheckService", "Server connection restored")
                    showToast(context.getString(R.string.server_connection_restored))
                }
                consecutiveFailures = 0
            } else {
                android.util.Log.w("HealthCheckService", "Health check failed: status=${response.status}, database=${response.database}")
                handleHealthCheckFailure()
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.e("HealthCheckService", "Health check timeout after ${HEALTH_CHECK_TIMEOUT}ms")
            handleHealthCheckFailure()
        } catch (e: Exception) {
            android.util.Log.e("HealthCheckService", "Health check exception: ${e.message}", e)
            handleHealthCheckFailure()
        }
    }

    private fun handleHealthCheckFailure() {
        consecutiveFailures++
        android.util.Log.w("HealthCheckService", "Consecutive failures: $consecutiveFailures/$maxConsecutiveFailures")
        
        if (consecutiveFailures == maxConsecutiveFailures) {
            android.util.Log.e("HealthCheckService", "Max consecutive failures reached, showing toast")
            showToast(context.getString(R.string.server_connection_error_message))
        }
    }
    
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

}
