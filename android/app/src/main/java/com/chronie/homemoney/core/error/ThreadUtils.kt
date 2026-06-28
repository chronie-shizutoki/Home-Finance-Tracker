package com.chronie.homemoney.core.error

import android.os.Build
import android.os.Looper

/**
 * Thread Utils class
 * Provides thread-related utility methods, handling compatibility issues across different Android versions
 */
object ThreadUtils {

    /**
     * Get thread ID
     * Compatible with Android versions below 14
     */
    fun getThreadId(thread: Thread): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            thread.threadId()
        } else {
            @Suppress("DEPRECATION")
            thread.id
        }
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
