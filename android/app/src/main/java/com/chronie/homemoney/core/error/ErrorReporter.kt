package com.chronie.homemoney.core.error

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Error Reporter Class
 * Responsible for collecting, saving, and reporting error information to the backend server
 */
@Singleton
class ErrorReporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val errorReportApi = MockErrorReportApi()
    private val logFileManager = LogFileManager(context)
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val mainThreadId = ThreadUtils.getMainThreadId()

    companion object {
        private const val TAG = "ErrorReporter"
        private const val MAX_QUEUE_SIZE = 10
        private const val RETRY_COUNT = 3
    }

    private val errorQueue = ArrayDeque<ErrorInfo>()

    /**
     * Initialize the error reporter class
     */
    fun initialize() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler(
            UncaughtExceptionHandler(defaultHandler ?: Thread.UncaughtExceptionHandler { _, _ -> }, this)
        )

        Log.d(TAG, "Error reporter initialized")
    }

    /**
     * Public method to report errors to the server, called by UncaughtExceptionHandler
     */
    @WorkerThread
    suspend fun reportErrorToServer(errorInfo: ErrorInfo) {
        reportErrorToServerInternal(errorInfo)
    }

    /**
     * Record a custom error
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val errorInfo = ErrorInfo(
            errorType = "CUSTOM_ERROR",
            message = "[$tag] $message",
            stackTrace = throwable?.let { getStackTraceString(it) } ?: getCurrentStackTrace(),
            threadName = Thread.currentThread().name,
            isMainThread = ThreadUtils.isMainThread(),
            timestamp = System.currentTimeMillis(),
            deviceInfo = DeviceInfoUtils.getDeviceInfo()
        )

        addToQueue(errorInfo)
        saveErrorToLocalAsync(errorInfo)
        reportErrorToServerAsync(errorInfo)
    }

    /**
     * Record network errors
     */
    fun logNetworkError(endpoint: String, errorCode: Int, message: String, throwable: Throwable? = null) {
        val errorInfo = ErrorInfo(
            errorType = "NETWORK_ERROR",
            message = "Network error at $endpoint: $errorCode - $message",
            stackTrace = throwable?.let { getStackTraceString(it) } ?: getCurrentStackTrace(),
            threadName = Thread.currentThread().name,
            isMainThread = ThreadUtils.isMainThread(),
            timestamp = System.currentTimeMillis(),
            deviceInfo = DeviceInfoUtils.getDeviceInfo(),
            additionalInfo = mapOf(
                "endpoint" to endpoint,
                "errorCode" to errorCode.toString()
            )
        )

        addToQueue(errorInfo)
        saveErrorToLocalAsync(errorInfo)
        reportErrorToServerAsync(errorInfo)
    }

    /**
     * Save error information to local log file
     */
    @WorkerThread
    suspend fun saveErrorToLocal(errorInfo: ErrorInfo) {
        withContext(Dispatchers.IO) {
            try {
                logFileManager.saveErrorLog(errorInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save error to local file", e)
            }
        }
    }

    /**
     * Internal method to report errors to the server
     */
    @WorkerThread
    private suspend fun reportErrorToServerInternal(errorInfo: ErrorInfo) {
        Log.d(TAG, "Preparing to report error: ${errorInfo.message}")

        var retryCount = 0
        var success = false

        while (retryCount < RETRY_COUNT && !success) {
            try {
                val appVersionInfo = DeviceInfoUtils.getAppVersion(context)
                val result = errorReportApi.reportError(
                    ErrorReportRequest(
                        errorType = errorInfo.errorType,
                        message = errorInfo.message,
                        stackTrace = errorInfo.stackTrace,
                        timestamp = errorInfo.timestamp,
                        deviceInfo = errorInfo.deviceInfo,
                        appVersion = appVersionInfo.versionName,
                        appBuild = appVersionInfo.versionCode,
                        environment = getEnvironment(),
                        additionalInfo = errorInfo.additionalInfo
                    )
                )
                
                success = result.isSuccessful
                if (success) {
                    Log.d(TAG, "Error reported successfully")
                } else {
                    Log.e(TAG, "Failed to report error, response code: ${result.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception when reporting error, retry $retryCount", e)
            } finally {
                retryCount++
                if (!success && retryCount < RETRY_COUNT) {
                    Thread.sleep(1000L * retryCount)
                }
            }
        }
    }

    /**
     * Get the stack trace string of a throwable
     */
    private fun getStackTraceString(throwable: Throwable): String {
        return Log.getStackTraceString(throwable)
    }

    /**
     * Get the current thread's stack trace
     */
    private fun getCurrentStackTrace(): String {
        return Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }
    }

    /**
     * Get environment information
     */
    private fun getEnvironment(): String {
        return if (isDebugMode()) "development" else "production"
    }

    /**
     * Check if the app is running in debug mode
     */
    private fun isDebugMode(): Boolean {
        return try {
            val appInfo = context.applicationInfo
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Add error information to the queue for reporting
     */
    private fun addToQueue(errorInfo: ErrorInfo) {
        synchronized(errorQueue) {
            if (errorQueue.size >= MAX_QUEUE_SIZE) {
                errorQueue.removeFirst()
            }
            errorQueue.add(errorInfo)
        }
    }

    /**
     * Asynchronously save error information to local log file
     */
    private fun saveErrorToLocalAsync(errorInfo: ErrorInfo) {
        executorService.execute {
            kotlinx.coroutines.runBlocking {
                saveErrorToLocal(errorInfo)
            }
        }
    }

    /**
     * Asynchronously report error information to the server
     */
    private fun reportErrorToServerAsync(errorInfo: ErrorInfo) {
        if (!ThreadUtils.isMainThread()) {
            executorService.execute {
                kotlinx.coroutines.runBlocking {
                    reportErrorToServerInternal(errorInfo)
                }
            }
        } else {
            handler.post {
                executorService.execute {
                    kotlinx.coroutines.runBlocking {
                        reportErrorToServerInternal(errorInfo)
                    }
                }
            }
        }
    }

    /**
     * Get the number of errors in the error queue
     */
    fun getErrorQueueSize(): Int {
        synchronized(errorQueue) {
            return errorQueue.size
        }
    }

    /**
     * Clear the error queue
     */
    fun clearErrorQueue() {
        synchronized(errorQueue) {
            errorQueue.clear()
        }
    }

    /**
     * Get all log files
     */
    fun getLogFiles() = logFileManager.getLogFiles()

    /**
     * Clear all log files
     */
    fun clearLogFiles() = logFileManager.clearLogFiles()
}
