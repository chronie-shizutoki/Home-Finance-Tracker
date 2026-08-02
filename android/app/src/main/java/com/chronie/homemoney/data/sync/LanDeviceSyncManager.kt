package com.chronie.homemoney.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.sync.auth.SyncAuthorizer
import com.chronie.homemoney.data.sync.discovery.DiscoveredDevice
import com.chronie.homemoney.data.sync.discovery.DiscoveryIdentity
import com.chronie.homemoney.data.sync.discovery.LanDiscoveryService
import com.chronie.homemoney.data.sync.discovery.MulticastGate
import com.chronie.homemoney.data.sync.engine.RoomSyncEntityStore
import com.chronie.homemoney.data.sync.engine.SyncIdentity
import com.chronie.homemoney.data.sync.engine.SyncInitiator
import com.chronie.homemoney.data.sync.engine.SyncResponder
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.transport.NativeSyncTransport
import com.chronie.homemoney.data.sync.telemetry.LogcatSyncLogSink
import com.chronie.homemoney.data.sync.telemetry.MetricsDiscoveryTelemetry
import com.chronie.homemoney.data.sync.telemetry.MetricsResponderObserver
import com.chronie.homemoney.data.sync.telemetry.SyncMetrics
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.chronie.homemoney.domain.sync.SyncProgressInfo
import com.chronie.homemoney.domain.sync.SyncRequestInfo
import com.chronie.homemoney.data.sync.generated.ConflictSummary
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * LAN Device Sync Manager - High Performance gRPC + UDP Discovery Version
 */
class LanDeviceSyncManager(
    private val context: Context,
    expenseDao: ExpenseDao,
    gson: Gson,
    private val wifiManager: WifiManager
) : BaseDeviceSyncManager(expenseDao, gson) {
    
    companion object {
        private const val DISCOVERY_PORT = LanDiscoveryService.DEFAULT_DISCOVERY_PORT
        private const val GRPC_SYNC_PORT = 50051
        private const val LAN_SIGNAL_PLACEHOLDER = 80
        private const val SYNC_PREFS = "sync_prefs"
        private const val KEY_TRUSTED_DEVICES = "trusted_device_ids"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val TAG = "LanDeviceSyncManager"
    }
    
    private val nativeSyncEngine = NativeSyncEngine()
    private var isServerRunning = AtomicBoolean(false)
    private var assignedPort = 0
    val metrics = SyncMetrics()
    private val logSink = LogcatSyncLogSink()
    private val discoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var responderJob: Job? = null

    private val multicastGate = MulticastGate { lockTag ->
        try {
            val lock = wifiManager.createMulticastLock(lockTag).apply {
                setReferenceCounted(false)
                acquire()
            }
            AutoCloseable { if (lock.isHeld) lock.release() }
        } catch (e: Exception) {
            Log.w(TAG, "Multicast lock unavailable for $lockTag", e)
            null
        }
    }

    private val discovery: LanDiscoveryService by lazy {
        LanDiscoveryService(
            identity = {
                DiscoveryIdentity(
                    deviceId = localDeviceId,
                    deviceName = localDeviceName,
                    deviceType = "ANDROID",
                    syncPort = if (assignedPort > 0) assignedPort else GRPC_SYNC_PORT
                )
            },
            discoveryPort = DISCOVERY_PORT,
            multicastGate = multicastGate,
            telemetry = MetricsDiscoveryTelemetry(metrics, logSink)
        )
    }

    
    private val syncLock = Any()
    @Volatile
    private var isSyncing = false

    private val _syncProgress = MutableStateFlow(SyncProgressInfo())
    override val syncProgress: StateFlow<SyncProgressInfo> = _syncProgress.asStateFlow()

    @Volatile
    private var syncRequestCallback: com.chronie.homemoney.domain.sync.SyncRequestCallback? = null
    private var pendingSyncResponse: kotlin.coroutines.Continuation<Boolean>? = null

    private val prefs get() = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)

    override val localDeviceId: String get() {
        return prefs.getString("device_sync_id", null) ?: "android_${UUID.randomUUID().toString().substring(0, 8)}".also {
            prefs.edit().putString("device_sync_id", it).apply()
        }
    }

    override val localDeviceName: String get() {
        return prefs.getString("device_custom_name", null) ?: Build.MODEL ?: "Android Device"
    }

    // ------------------------------------------------------------------ v2 responder

    private val syncInitiator: SyncInitiator by lazy {
        SyncInitiator(
            store = RoomSyncEntityStore(expenseDao),
            identity = {
                SyncIdentity(
                    deviceId = localDeviceId,
                    deviceName = localDeviceName,
                    deviceType = "ANDROID"
                )
            },
            authorizer = PromptingSyncAuthorizer()
        )
    }

    private val syncResponder: SyncResponder by lazy {
        SyncResponder(
            store = RoomSyncEntityStore(expenseDao),
            identity = {
                SyncIdentity(
                    deviceId = localDeviceId,
                    deviceName = localDeviceName,
                    deviceType = "ANDROID"
                )
            },
            authorizer = PromptingSyncAuthorizer(),
            observer = MetricsResponderObserver(metrics, logSink)
        )
    }

    private inner class PromptingSyncAuthorizer : SyncAuthorizer {
        override fun pairingCode(): String? =
            prefs.getString(KEY_PAIRING_CODE, null)?.takeIf { it.isNotBlank() }

        override fun isTrusted(deviceId: String): Boolean =
            prefs.getStringSet(KEY_TRUSTED_DEVICES, emptySet())?.contains(deviceId) == true

        override fun confirm(
            request: SyncAuthorizer.Request,
            timeoutMs: Long
        ): SyncAuthorizer.Decision {
            val info = SyncRequestInfo(request.deviceId, request.deviceName, request.peerAddress)
            val accepted = runBlocking { confirmRequest(info, timeoutMs) }

            return when (accepted) {
                true -> SyncAuthorizer.Decision.ACCEPTED
                false -> SyncAuthorizer.Decision.REJECTED
                else -> SyncAuthorizer.Decision.TIMED_OUT
            }
        }

        override fun remember(deviceId: String, deviceName: String) {
            val current = prefs.getStringSet(KEY_TRUSTED_DEVICES, emptySet()) ?: emptySet()
            if (deviceId in current) return
            prefs.edit().putStringSet(KEY_TRUSTED_DEVICES, current + deviceId).apply()
        }
    }

    private suspend fun confirmRequest(info: SyncRequestInfo, timeoutMs: Long): Boolean? {
        val callback = syncRequestCallback
        if (callback == null) {
            Log.i(TAG, "No screen-specific callback; using app-wide prompt for ${info.deviceName}")
            val future = SyncRequestBus.post(info)
            return try {
                withContext(Dispatchers.IO) {
                    future.get(timeoutMs, TimeUnit.MILLISECONDS)
                }
            } catch (e: java.util.concurrent.TimeoutException) {
                SyncRequestBus.cancel()
                Log.w(TAG, "App-wide sync prompt timed out for ${info.deviceName}")
                null
            } catch (e: Exception) {
                SyncRequestBus.cancel()
                Log.e(TAG, "App-wide sync prompt failed for ${info.deviceName}", e)
                false
            }
        }

        val accepted = AtomicBoolean(false)
        val answered = CountDownLatch(1)
        val job = CoroutineScope(Dispatchers.Main).launch {
            try {
                accepted.set(callback.onSyncRequest(info))
            } catch (e: Exception) {
                Log.e(TAG, "Sync request dialog failed for ${info.deviceName}", e)
            } finally {
                answered.countDown()
            }
        }

        val inTime = try {
            withContext(Dispatchers.IO) {
                answered.await(timeoutMs, TimeUnit.MILLISECONDS)
            }
        } catch (e: InterruptedException) {
            job.cancel()
            return false
        }

        if (!inTime) {
            job.cancel()
            return null
        }
        return accepted.get()
    }

    override fun updateSyncProgress(progress: Float, message: String, isActive: Boolean) {
        _syncProgress.value = SyncProgressInfo(progress, message, isActive, localDeviceName)
    }

    override fun clearSyncProgress() {
        _syncProgress.value = SyncProgressInfo()
    }

    override fun setSyncRequestCallback(callback: com.chronie.homemoney.domain.sync.SyncRequestCallback?) {
        Log.d(TAG, "Setting syncRequestCallback: ${callback != null}")
        syncRequestCallback = callback
    }

    override fun respondToSyncRequest(accepted: Boolean) {
        pendingSyncResponse?.resume(accepted)
        pendingSyncResponse = null
    }

    private fun isWifiConnected(): Boolean {
        val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java) ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        if (!isWifiConnected()) {
            Log.d(TAG, "Skipping LAN search: no Wi-Fi transport")
            return@flow
        }
        startSyncServer()
        emitAll(discovery.search().map { it.toDeviceInfo() })
    }.flowOn(Dispatchers.IO)

    private fun DiscoveredDevice.toDeviceInfo() = DeviceInfo(
        deviceId,
        deviceName,
        deviceType.ifBlank { "ANDROID" },
        "LAN",
        address,
        LAN_SIGNAL_PLACEHOLDER
    )

    private fun resolveSyncPort(device: DeviceInfo): Int =
        discovery.registry.get(device.deviceId, System.currentTimeMillis())?.syncPort
            ?: GRPC_SYNC_PORT

    private fun wifiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        return try {
            cm.allNetworks.firstOrNull { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not enumerate networks; sync will use the default route", e)
            null
        }
    }

    private fun wifiNetworkHandle(): Long {
        val net = wifiNetwork()
        val handle = net?.networkHandle ?: 0L
        val caps = if (net != null) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val nc = cm?.getNetworkCapabilities(net)
            val lp = cm?.getLinkProperties(net)
            "transport=${nc?.transportInfo} link=${lp?.interfaceName} addrs=${lp?.linkAddresses}"
        } else "none"
        Log.i(TAG, "wifiNetwork: handle=$handle $caps")
        return handle
    }

    private fun describeConnectFailure(error: SyncErrorCode): String = when (error) {
        SyncErrorCode.CONNECT_TIMEOUT ->
            "Peer did not answer. Check both devices are on the same Wi-Fi."
        SyncErrorCode.NETWORK_UNREACHABLE ->
            "Could not reach the peer. Open Home Money on the other device and keep it on the same Wi-Fi."
        else -> "Connection failed ($error)"
    }

    fun startSyncServer() {
        if (!isServerRunning.compareAndSet(false, true)) return
        
        nativeSyncEngine.setFrameHandler(syncResponder)
        discoveryScope.launch {
            // Passing 0 tells the kernel to assign a random available port.
            val port = nativeSyncEngine.startServer(0)
            if (port > 0) {
                assignedPort = port
                Log.i(TAG, "native TCP server started on port $port")
            } else {
                Log.e(TAG, "native TCP server FAILED to start (port busy? permission denied?)")
                isServerRunning.set(false)
            }
        }
        responderJob = discoveryScope.launch {
            Log.i(TAG, "starting UDP discovery responder on port $DISCOVERY_PORT...")
            discovery.runResponder()
        }
    }

    fun stopSyncServer() {
        val wasRunning = isServerRunning.getAndSet(false)
        nativeSyncEngine.stopServer()
        nativeSyncEngine.setFrameHandler(null)
        if (wasRunning) {
            syncResponder.sessions.clear()
        }
        responderJob?.cancel()
        responderJob = null
        discovery.closeResponder()
    }

    override suspend fun connect(device: DeviceInfo): Boolean {
        currentDevice = device
        isConnected = true
        return true
    }
    
    override suspend fun disconnect(): Boolean {
        isConnected = false
        currentDevice = null
        return true
    }

    override suspend fun sendData(data: DeviceSyncData) = true
    override suspend fun receiveData(): DeviceSyncData? = null

    override suspend fun syncWithDevice(device: DeviceInfo): com.chronie.homemoney.domain.model.SyncResult {
        synchronized(syncLock) {
            if (isSyncing) return createFailedSyncResult("Busy")
            isSyncing = true
        }
        return try {
            withContext(Dispatchers.IO) {
            updateSyncProgress(0.1f, "Connecting...", true)
            val port = resolveSyncPort(device)
            val netHandle = wifiNetworkHandle()
            val transport = NativeSyncTransport(
                nativeSyncEngine,
                device.address,
                port,
                connectTimeoutMs = 10_000,
                netHandle = netHandle
            )
            val outcome = try {
                syncInitiator.sync(transport, device.address) { progress, message ->
                    updateSyncProgress(progress, message, true)
                }
            } finally {
                transport.close()
            }
            if (!outcome.success) {
                val reason = transport.connectError?.let { describeConnectFailure(it) }
                    ?: outcome.errorMessage
                    ?: "sync failed"
                Log.w(TAG, "v2 sync with ${device.deviceName} failed: $reason")
                
                // If the peer is unreachable or times out, it's likely our discovery info is stale.
                // Clear it so the user doesn't keep retrying the same broken address.
                if (transport.connectError == SyncErrorCode.NETWORK_UNREACHABLE || 
                    transport.connectError == SyncErrorCode.CONNECT_TIMEOUT) {
                    Log.i(TAG, "Invalidating stale registry entry for ${device.deviceId}")
                    discovery.registry.remove(device.deviceId)
                }

                updateSyncProgress(1f, "Sync failed: $reason", false)
                return@withContext createFailedSyncResult(reason)
            }
            updateSyncProgress(1f, "Done", false)
            com.chronie.homemoney.domain.model.SyncResult(
                success = true,
                uploadResult = com.chronie.homemoney.domain.model.UploadResult(
                    totalItems = outcome.uploadedEntities,
                    successCount = outcome.uploadedEntities,
                    failedCount = 0
                ),
                downloadResult = com.chronie.homemoney.domain.model.DownloadResult(
                    totalItems = outcome.downloadedEntities,
                    newItems = outcome.inserted,
                    updatedItems = outcome.updated,
                    conflicts = outcome.conflicts.map { toDomainConflict(it) }
                ),
                conflicts = outcome.conflicts.map { toDomainConflict(it) }
            )
            }
        } catch (e: Exception) {
            createFailedSyncResult(e.message ?: "Error")
        } finally {
            isSyncing = false
            delay(2000)
            clearSyncProgress()
        }
    }

    private fun toDomainConflict(c: ConflictSummary): com.chronie.homemoney.domain.model.SyncConflict =
        com.chronie.homemoney.domain.model.SyncConflict(
            entityType = c.entityType,
            entityId = c.entityId,
            conflictType = com.chronie.homemoney.domain.model.ConflictType.UPDATE_CONFLICT,
            localTimestamp = c.localUpdatedAt,
            serverTimestamp = c.remoteUpdatedAt,
            resolution = if (c.keptLocal) {
                com.chronie.homemoney.domain.model.ConflictResolution.USE_LOCAL
            } else {
                com.chronie.homemoney.domain.model.ConflictResolution.USE_SERVER
            }
        )

    fun cleanup() {
        stopSyncServer()
        discovery.registry.clear()
        discoveryScope.launch { disconnect() }
    }
}
