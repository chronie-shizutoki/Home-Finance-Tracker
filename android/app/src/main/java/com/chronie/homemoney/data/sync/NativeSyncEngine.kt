package com.chronie.homemoney.data.sync

import android.util.Log
import androidx.annotation.Keep

@Keep
class NativeSyncEngine {
    
    interface SyncRequestListener {
        /**
         * Callback when a sync request is received from a remote device
         * @param deviceId Remote device ID
         * @param deviceName Remote device name
         * @param data Remote Protobuf data
         * @return Local Protobuf data to sync to the remote device, or null to reject sync
         */
        fun onSyncDataReceived(deviceId: String, deviceName: String, data: ByteArray): ByteArray?
    }

    private var listener: SyncRequestListener? = null

    fun setSyncRequestListener(listener: SyncRequestListener) {
        this.listener = listener
    }

    /**
     * Handle incoming sync requests from remote devices via JNI
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
