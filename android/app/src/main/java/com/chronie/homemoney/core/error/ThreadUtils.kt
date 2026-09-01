package com.chronie.homemoney.core.error

import android.os.Looper

/**
 * Thread Utils class
 * Provides thread-related utility methods, handling compatibility issues across different Android versions
 */
object ThreadUtils {

    /**
     * Get thread ID
     * Compatible with Android versions at least 16
     */
    fun getThreadId(thread: Thread): Long {
        return thread.threadId()
    }

    /**
     * Check if the current thread is the main thread
     */
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * Get the main thread ID
     */
    fun getMainThreadId(): Long {
        return getThreadId(Looper.getMainLooper().thread)
    }
}
