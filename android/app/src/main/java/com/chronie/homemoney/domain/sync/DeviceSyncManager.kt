package com.chronie.homemoney.domain.sync

import com.chronie.homemoney.domain.model.SyncResult
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sync Progress Information
 */
data class SyncProgressInfo(
    @SerializedName("progress")
    val progress: Float = 0f,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("isActive")
    val isActive: Boolean = false,
    @SerializedName("deviceName")
    val deviceName: String = ""
)

/**
 * Sync Request Information
 */
data class SyncRequestInfo(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("deviceName")
    val deviceName: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Sync Request Callback Interface
 */
interface SyncRequestCallback {
    /**
     * Called when a sync request is received
     * @return Return true to accept sync, false to reject
     */
    suspend fun onSyncRequest(requestInfo: SyncRequestInfo): Boolean
}

/**
 * Device Sync Manager Interface
 */
interface DeviceSyncManager {

    /**
     * Sync Progress State Flow
     */
    val syncProgress: StateFlow<SyncProgressInfo>

    /**
     * Search for nearby devices
     */
    fun searchDevices(): Flow<DeviceInfo>

    /**
     * Connect to a specified device
     */
    suspend fun connect(device: DeviceInfo): Boolean

    /**
     * Disconnect from the current device
     */
    suspend fun disconnect(): Boolean

    /**
     * Send data to the connected device
     */
    suspend fun sendData(data: DeviceSyncData): Boolean

    /**
     * Receive data from the connected device
     */
    suspend fun receiveData(): DeviceSyncData?

    /**
     * Execute bidirectional sync with a device
     */
    suspend fun syncWithDevice(device: DeviceInfo): SyncResult

    /**
     * Update sync progress (for server-side notification to UI)
     */
    fun updateSyncProgress(progress: Float, message: String, isActive: Boolean = true)

    /**
     * Clear sync progress
     */
    fun clearSyncProgress()

    /**
     * Set sync request callback (for server-side notification to UI)
     */
    fun setSyncRequestCallback(callback: SyncRequestCallback?)

    /**
     * Respond to sync request (accept or reject)
     */
    fun respondToSyncRequest(accepted: Boolean)
}

/**
 * Device Sync Data Model
 */
data class DeviceSyncData(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("deviceName")
    val deviceName: String,
    @SerializedName("syncTimestamp")
    val syncTimestamp: Long,
    @SerializedName("entities")
    val entities: List<SyncEntity>
)

/**
 * Sync Entity
 */
data class SyncEntity(
    @SerializedName("entityType")
    val entityType: String,
    @SerializedName("entityId")
    val entityId: String,
    @SerializedName("operation")
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    @SerializedName("data")
    val data: String, // JSON-formatted data string
    @SerializedName("timestamp")
    val timestamp: Long
)

/**
 * Device Information
 */
data class DeviceInfo(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("deviceName")
    val deviceName: String,
    @SerializedName("deviceType")
    val deviceType: String, // "ANDROID", "IOS", "WEB"
    @SerializedName("connectionType")
    val connectionType: String, // "LAN", "BLUETOOTH", "NFC"
    @SerializedName("address")
    val address: String, // Device address
    @SerializedName("signalStrength")
    val signalStrength: Int // Signal strength (0-100)
)
