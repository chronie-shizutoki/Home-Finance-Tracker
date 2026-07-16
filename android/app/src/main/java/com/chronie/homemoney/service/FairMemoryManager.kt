package com.chronie.homemoney.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import coil3.ImageLoader
import com.chronie.homemoney.core.error.ErrorReporter
import com.chronie.homemoney.data.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FairMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val errorReporter: ErrorReporter,
    private val syncScheduler: SyncScheduler
) : IBinder.DeathRecipient {

    companion object {
        private const val TAG = "FairMemoryManager"
        private const val ITGSA_ACTION_TRIM = "itgsa.intent.action.TRIM"
        private const val ITGSA_ACTION_KILL = "itgsa.intent.action.KILL"
        private const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION
    }

    private var mRemote: IBinder? = null
    private var mInitialized = false
    private var mHandler: Handler? = null

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ITGSA_ACTION_TRIM == action || ITGSA_ACTION_KILL == action) {
                handleMemoryBroadcast(intent, action)
            }
        }
    }

    fun initialize() {
        synchronized(this) {
            if (!mInitialized) {
                val handlerThread = HandlerThread(TAG)
                handlerThread.start()
                mHandler = Handler(handlerThread.looper)

                val filter = IntentFilter()
                filter.addAction(ITGSA_ACTION_TRIM)
                filter.addAction(ITGSA_ACTION_KILL)

                context.registerReceiver(
                    mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED
                )

                mInitialized = true
                Log.d(TAG, "FairMemoryManager initialized")
            }
        }
    }

    private fun handleMemoryBroadcast(intent: Intent, action: String) {
        val data = intent.extras ?: return
        val bundle = data.getBundle("common") ?: return

        val notifyType = bundle.getInt("notifyType", 0)
        val notifyId = bundle.getInt("notifyId", 0)
        val reason = bundle.getString("reason")
        val callbackBinder = bundle.getBinder("callback")

        Log.w(
            TAG,
            "Received memory broadcast: action=$action, notifyType=$notifyType, " +
                    "notifyId=$notifyId, reason=$reason"
        )

        if (action == ITGSA_ACTION_TRIM) {
            handleTrimAction(notifyType, notifyId, callbackBinder)
        } else if (action == ITGSA_ACTION_KILL) {
            handleKillAction(notifyType, notifyId, callbackBinder)
        }
    }

    private fun handleTrimAction(notifyType: Int, notifyId: Int, callback: IBinder?) {
        Log.i(TAG, "Handling TRIM action, notifyType=$notifyType")
        releaseMemory()
        if (checkRemote(callback)) {
            val replyData = Bundle()
            replyData.putString("reply", "Memory released successfully")
            reply(notifyType, notifyId, 0, replyData)
        }
    }

    private fun handleKillAction(notifyType: Int, notifyId: Int, callback: IBinder?) {
        Log.w(TAG, "Handling KILL action, notifyType=$notifyType")
        saveAppState()
        if (checkRemote(callback)) {
            val replyData = Bundle()
            replyData.putString("reply", "App state saved successfully")
            reply(notifyType, notifyId, 0, replyData)
        }
    }

    /**
     * 释放内存：清除图片内存缓存、清空错误队列、建议 GC 回收
     * 注意：不调用 clearApplicationUserData()，那会清除所有用户数据
     */
    private fun releaseMemory() {
        Log.i(TAG, "Releasing memory resources")

        // 1. 清除 Coil 图片内存缓存（磁盘缓存保留，避免重复下载）
        imageLoader.memoryCache?.clear()
        Log.d(TAG, "Cleared Coil memory cache")

        // 2. 清空错误报告队列（错误日志已异步保存到本地文件）
        errorReporter.clearErrorQueue()
        Log.d(TAG, "Cleared error queue")

        // 3. 建议 JVM 进行垃圾回收
        System.gc()
        Log.d(TAG, "Suggested GC")
    }

    /**
     * 保存应用状态：触发即时同步，将本地未同步的数据推送到云端
     * Room 数据库中的账单数据已持久化，不会因查杀丢失
     * WorkManager 会保证同步任务在下次启动时继续执行
     */
    private fun saveAppState() {
        Log.i(TAG, "Saving app state before kill")

        // 触发即时同步，尽力推送未同步数据到云端
        try {
            syncScheduler.triggerImmediateSync()
            Log.d(TAG, "Triggered immediate sync before kill")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger sync before kill", e)
        }
    }

    private fun checkRemote(callback: IBinder?): Boolean {
        synchronized(this) {
            if (mRemote == null) {
                try {
                    mRemote = callback
                    mRemote?.linkToDeath(this, 0)
                } catch (_: RemoteException) {
                    mRemote = null
                    return false
                }
            }
        }
        return true
    }

    override fun binderDied() {
        synchronized(this) {
            if (mRemote != null) {
                try {
                    mRemote?.unlinkToDeath(this, 0)
                } catch (_: Exception) {
                }
            }
            mRemote = null
        }
    }

    fun reply(notifyType: Int, notifyId: Int, result: Int, extra: Bundle?) {
        synchronized(this) {
            val remote = mRemote
            if (remote != null) {
                val data = Parcel.obtain()
                val replyParcel = Parcel.obtain()
                try {
                    data.writeInt(notifyType)
                    data.writeInt(notifyId)
                    data.writeInt(result)
                    if (extra == null) {
                        data.writeBundle(Bundle())
                    } else {
                        data.writeBundle(extra)
                    }
                    remote.transact(
                        TRANSACTION_EXCEPTION_REPLY, data, replyParcel, IBinder.FLAG_ONEWAY
                    )
                    replyParcel.readException()
                } catch (e: Exception) {
                    Log.e(TAG, "reply failed.", e)
                } finally {
                    replyParcel.recycle()
                    data.recycle()
                }
            }
        }
    }
}
