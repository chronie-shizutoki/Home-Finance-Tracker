package com.chronie.homemoney.data.sync.telemetry

import android.util.Log

/**
 * Sends structured sync lines to logcat.
 *
 * Kept in its own file, and out of [SyncObservability], so the observers themselves stay
 * free of Android types and can be exercised by plain JVM unit tests with a recording sink.
 *
 * ### Levels
 *
 * [SyncLogLevel.DEBUG] maps to `Log.d`, which is stripped from release builds by the log
 * guard below rather than by proguard, because these lines are emitted per frame and the
 * string building — not the write — is what costs. Callers pass an already-built string, so
 * the only saving available here is skipping the write; [minLevel] exists so a release build
 * can be configured to skip the building too, at the observer's call site, if it ever
 * matters. Today it does not: a sync involves tens of frames, not thousands.
 */
class LogcatSyncLogSink(
    private val tag: String = DEFAULT_TAG,
    private val minLevel: SyncLogLevel = SyncLogLevel.DEBUG
) : SyncLogSink {

    override fun emit(level: SyncLogLevel, line: String) {
        if (level < minLevel) return
        when (level) {
            SyncLogLevel.DEBUG -> Log.d(tag, line)
            SyncLogLevel.INFO -> Log.i(tag, line)
            SyncLogLevel.WARN -> Log.w(tag, line)
        }
    }

    companion object {
        /**
         * One tag for the whole sync stack, so `adb logcat -s HomeMoneySync` gives the
         * complete story of a sync and `grep trace=` narrows it to one session.
         */
        const val DEFAULT_TAG = "HomeMoneySync"
    }
}
