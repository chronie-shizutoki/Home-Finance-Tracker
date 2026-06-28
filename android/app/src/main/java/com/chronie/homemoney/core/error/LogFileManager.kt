package com.chronie.homemoney.core.error

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log File Manager
 * Responsible for managing error log files creation
 */
class LogFileManager(private val context: Context) {

    companion object {
        private const val TAG = "LogFileManager"
        private const val CRASH_LOG_DIR = "crash_logs"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Get the log directory for saving crash logs
     */
    fun getLogDir(): File {
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            File(context.getExternalFilesDir(null), CRASH_LOG_DIR)
        } else {
            File(context.filesDir, CRASH_LOG_DIR)
        }
    }

    /**
     * Save a crash log to a file
     */
    fun saveCrashLog(thread: Thread, throwable: Throwable): File? {
        return try {
            val logDir = getLogDir()
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFileName = "crash-${dateFormat.format(Date())}.txt"
            val logFile = File(logDir, logFileName)

            FileWriter(logFile).use { fileWriter ->
                fileWriter.write("Crash Time: ${timestampFormat.format(Date())}\n\n")

                fileWriter.write("Device Information:\n")
                val deviceInfo = DeviceInfoUtils.getDeviceInfo()
                fileWriter.write("- OS Version: Android ${deviceInfo["osVersion"]} (API ${deviceInfo["sdkVersion"]})\n")
                fileWriter.write("- Device: ${deviceInfo["manufacturer"]} ${deviceInfo["deviceModel"]}\n")
                val appVersion = DeviceInfoUtils.getAppVersion(context)
                fileWriter.write("- App Version: ${appVersion.versionName} (${appVersion.versionCode})\n\n")

                fileWriter.write("Thread Information:\n")
                fileWriter.write("- Thread Name: ${thread.name}\n")
                fileWriter.write("- Thread ID: ${ThreadUtils.getThreadId(thread)}\n\n")

                fileWriter.write("Crash Stack Trace:\n")
                val stringWriter = StringWriter()
                val printWriter = PrintWriter(stringWriter)
                throwable.printStackTrace(printWriter)
                fileWriter.write(stringWriter.toString())
            }

            Log.d(TAG, "Crash log saved to: ${logFile.absolutePath}")
            logFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
            null
        }
    }

    /**
     * Save error information to a log file
     */
    fun saveErrorLog(errorInfo: ErrorInfo): File? {
        return try {
            val logDir = getLogDir()
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFileName = "error-${dateFormat.format(Date())}.txt"
            val logFile = File(logDir, logFileName)

            FileWriter(logFile).use { fileWriter ->
                fileWriter.write(convertErrorInfoToText(errorInfo))
            }

            Log.d(TAG, "Error log saved to: ${logFile.absolutePath}")
            logFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save error log", e)
            null
        }
    }

    /**
     * Convert error information to text format
     */
    private fun convertErrorInfoToText(errorInfo: ErrorInfo): String {
        val timestampStr = timestampFormat.format(Date(errorInfo.timestamp))
        val deviceInfoStr = errorInfo.deviceInfo.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        val additionalInfoStr = errorInfo.additionalInfo?.entries?.joinToString(", ") { "${it.key}: ${it.value}" }

        return "[${timestampStr}] ${errorInfo.errorType}: ${errorInfo.message}\n" +
               "Thread: ${errorInfo.threadName} (${if (errorInfo.isMainThread) "Main" else "Worker"})\n" +
               "Device: $deviceInfoStr\n" +
               (additionalInfoStr?.let { "Additional info: $it\n" } ?: "") +
               "Stack trace:\n${errorInfo.stackTrace}\n"
    }

    /**
     * Get all log files in the log directory
     */
    fun getLogFiles(): List<File> {
        val logDir = getLogDir()
        if (!logDir.exists()) {
            return emptyList()
        }
        return logDir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Clear all log files
     */
    fun clearLogFiles(): Boolean {
        return try {
            val logDir = getLogDir()
            if (!logDir.exists()) {
                return true
            }
            logDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log files", e)
            false
        }
    }
}
