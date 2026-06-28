package com.chronie.homemoney.core.error

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Error Reporter Test Class
 * Provides methods to test the error reporter class
 * Only use in development environment, disable in production environment
 */
@Singleton
class ErrorReporterTest @Inject constructor(
    private val errorReporter: ErrorReporter
) {

    companion object {
        private const val TAG = "ErrorReporterTest"
    }

    /**
     * Test logging a normal error message
     * Simulate a custom error in the application
     */
    fun testLogError() {
        Log.d(TAG, "Testing log error functionality")
        try {
            errorReporter.logError(
                tag = "Test",
                message = "This is a test error message",
                throwable = Exception("Test exception for error logging")
            )
            Log.d(TAG, "Log error test completed")
        } catch (e: Exception) {
            Log.e(TAG, "Log error test failed", e)
        }
    }

    /**
     * Test logging network errors
     * Simulate a scenario where a network request fails
     */
    fun testNetworkError() {
        Log.d(TAG, "Testing network error functionality")
        try {
            errorReporter.logNetworkError(
                endpoint = "/api/test",
                errorCode = 404,
                message = "Resource not found",
                throwable = Exception("Test network exception")
            )
            Log.d(TAG, "Network error test completed")
        } catch (e: Exception) {
            Log.e(TAG, "Network error test failed", e)
        }
    }

    /**
     * Test uncaught exception handling
     * Note: This method will crash the app, only use in test environment
     * Warning: Do not call in production environment
     */
    fun testUncaughtException() {
        Log.d(TAG, "Testing uncaught exception handling")
        Log.w(TAG, "WARNING: This will crash the app. Only call in test environment!")
        // Throw an uncaught exception
        throw RuntimeException("Test uncaught exception")
    }

    /**
     * Test logging errors without an exception
     */
    fun testErrorWithoutException() {
        Log.d(TAG, "Testing error logging without exception")
        try {
            errorReporter.logError(
                tag = "Test",
                message = "This is a test error without exception"
            )
            Log.d(TAG, "Error without exception test completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error without exception test failed", e)
        }
    }

    /**
     * Test logging errors in different threads
     */
    fun testErrorInDifferentThreads() {
        Log.d(TAG, "Testing error logging in different threads")
        
        // Log an error in the main thread
        errorReporter.logError(
            tag = "MainThread",
            message = "Error logged from main thread"
        )
        
        // Log an error in a worker thread
        Thread {
            errorReporter.logError(
                tag = "WorkerThread",
                message = "Error logged from worker thread"
            )
        }.start()
        
        Log.d(TAG, "Multi-thread error logging test completed")
    }
}