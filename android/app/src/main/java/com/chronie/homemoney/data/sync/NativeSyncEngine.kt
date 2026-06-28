package com.chronie.homemoney.data.sync

import android.util.Log
import androidx.annotation.Keep

@Keep
class NativeSyncEngine {
    
    interface SyncRequestListener {
        /**
         * 当接收到同步数据时回调
         * @param deviceId 发送方的设备 ID
         * @param deviceName 发送方的设备名称
         * @param data 远程发送过来的 Protobuf 数据
         * @return 返回本地要同步给对方的 Protobuf 数据，若拒绝同步返回 null
         */
        fun onSyncDataReceived(deviceId: String, deviceName: String, data: ByteArray): ByteArray?
    }

    private var listener: SyncRequestListener? = null

    fun setSyncRequestListener(listener: SyncRequestListener) {
        this.listener = listener
    }

    /**
     * 由 Native 层通过 JNI 调用
     */
    @Keep
    fun handleIncomingSyncRequest(deviceId: String, deviceName: String, data: ByteArray): ByteArray? {
        Log.d("NativeSyncEngine", "JNI: Incoming sync data from $deviceName ($deviceId)")
        return listener?.onSyncDataReceived(deviceId, deviceName, data)
    }

    companion object {
        private const val TAG = "NativeSyncEngine"
        init {
            try {
                System.loadLibrary("sync_engine")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library sync_engine", e)
            }
        }
    }

    external fun startServer(port: Int): Boolean
    external fun stopServer()
    external fun performSync(address: String, port: Int, data: ByteArray): ByteArray?
}
