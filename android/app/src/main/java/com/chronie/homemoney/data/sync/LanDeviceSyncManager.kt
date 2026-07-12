package com.chronie.homemoney.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.chronie.homemoney.domain.sync.SyncProgressInfo
import com.chronie.homemoney.data.sync.generated.DeviceSyncData as ProtoSyncData
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
        private const val DISCOVERY_PORT = 12345
        private const val GRPC_SYNC_PORT = 50051
        private const val BROADCAST_INTERVAL = 1000L
        private const val BROADCAST_COUNT = 5
        private const val DISCOVERY_TIMEOUT = 12000L
        private const val MULTICAST_GROUP = "239.255.255.250"
    }
    
    private val nativeSyncEngine = NativeSyncEngine()
    private var isServerRunning = AtomicBoolean(false)
    private val discoveredDevices = ConcurrentHashMap<String, DeviceInfo>()
    private var discoveryResponseSocket: DatagramSocket? = null
    private var multicastLock: MulticastLock? = null
    
    init {
        nativeSyncEngine.setSyncRequestListener(object : NativeSyncEngine.SyncRequestListener {
            override fun onSyncDataReceived(deviceId: String, deviceName: String, data: ByteArray): ByteArray? {
                Log.d(tag, "B-Side: Received sync data request from $deviceName ($deviceId)")
                
                // 1. Parse remote data packet
                val remoteProto = try {
                    if (data.isEmpty()) {
                        Log.e(tag, "Received empty data from $deviceName")
                        return null
                    }
                    ProtoSyncData.parseFrom(data)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse remote proto from $deviceName", e)
                    return null
                }

                // Use parsed device name and ID from remote proto
                val realName = if (remoteProto.deviceName.isNotEmpty()) remoteProto.deviceName else deviceName
                val realId = if (remoteProto.deviceId.isNotEmpty()) remoteProto.deviceId else deviceId
                
                Log.d(tag, "Parsed remote device info: $realName ($realId)")

                // 2. Ask user if to accept (Blocking call)
                val callback = syncRequestCallback
                if (callback == null) {
                    Log.w(tag, "B-Side: No syncRequestCallback set! The app might not be on the sync screen. Current callback is null.")
                    return null
                }
                
                val acceptedResult = AtomicBoolean(false)
                val latch = java.util.concurrent.CountDownLatch(1)
                
                Log.d(tag, "B-Side: Launching Main coroutine for sync request dialog")
                // Use CoroutineScope instead of GlobalScope to ensure UI thread is not blocked by network operations
                val requestJob = CoroutineScope(Dispatchers.Main).launch {
                    try {
                        Log.d(tag, "B-Side: Showing sync request dialog for $realName")
                        val info = com.chronie.homemoney.domain.sync.SyncRequestInfo(realId, realName, "Remote LAN")
                        val accepted = callback!!.onSyncRequest(info)
                        acceptedResult.set(accepted)
                        Log.d(tag, "B-Side: User response for $realName: $accepted")
                    } catch (e: Exception) {
                        Log.e(tag, "B-Side: Error in onSyncRequest callback", e)
                    } finally {
                        latch.countDown()
                    }
                }
                
                // Wait for user response with timeout (e.g., 60 seconds)
                try {
                    val waitSuccess = latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
                    if (!waitSuccess) {
                        Log.w(tag, "B-Side: Sync request timed out waiting for user response")
                        requestJob.cancel()
                        return null
                    }
                } catch (e: InterruptedException) {
                    Log.e(tag, "B-Side: Latch interrupted", e)
                    requestJob.cancel()
                    return null
                }
                
                if (!acceptedResult.get()) {
                    Log.d(tag, "B-Side: Sync request rejected by user")
                    return null
                }

                // 3. User accepted, process remote data and prepare local data for sync
                Log.d(tag, "B-Side: Processing sync data from $realName")
                return runBlocking(Dispatchers.IO) {
                    try {
                        updateSyncProgress(0.4f, "Processing remote $realName data...", true)
                        processDeviceData(SyncProtoConverter.toDomain(remoteProto))
                        
                        updateSyncProgress(0.7f, "Syncing local data to $realName...", true)
                        val localData = prepareLocalData()
                        val responseBytes = SyncProtoConverter.toProto(localData).toByteArray()
                        
                        updateSyncProgress(1.0f, "Sync completed!", false)
                        delay(1000)
                        clearSyncProgress()
                        
                        Log.d(tag, "B-Side: Sync with $realName completed, sending response")
                        responseBytes
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing sync on B-side", e)
                        updateSyncProgress(1.0f, "Sync failed: ${e.message}", false)
                        delay(2000)
                        clearSyncProgress()
                        null
                    }
                }
            }
        })
    }
    
    private val syncLock = Any()
    @Volatile
    private var isSyncing = false

    private val _syncProgress = MutableStateFlow(SyncProgressInfo())
    override val syncProgress: StateFlow<SyncProgressInfo> = _syncProgress.asStateFlow()

    @Volatile
    private var syncRequestCallback: com.chronie.homemoney.domain.sync.SyncRequestCallback? = null
    private var pendingSyncResponse: kotlin.coroutines.Continuation<Boolean>? = null

    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.getString("device_sync_id", null) ?: "android_${UUID.randomUUID().toString().substring(0, 8)}".also {
            prefs.edit().putString("device_sync_id", it).apply()
        }
    }
    
    private val deviceName: String by lazy {
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            .getString("device_custom_name", null) ?: Build.MODEL ?: "Android Device"
    }

    override fun updateSyncProgress(progress: Float, message: String, isActive: Boolean) {
        _syncProgress.value = SyncProgressInfo(progress, message, isActive, deviceName)
    }

    override fun clearSyncProgress() {
        _syncProgress.value = SyncProgressInfo()
    }

    override fun setSyncRequestCallback(callback: com.chronie.homemoney.domain.sync.SyncRequestCallback?) {
        Log.d(tag, "Setting syncRequestCallback: ${callback != null}")
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
    
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                if (!intf.isUp || intf.isLoopback || intf.isVirtual) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (_: Exception) {}
        return null
    }
    
    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableSetOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                if (!intf.isUp || intf.isLoopback) continue
                intf.interfaceAddresses.forEach { addr -> 
                    addr.broadcast?.let { addresses.add(addr.broadcast) }
                }
            }
            addresses.add(InetAddress.getByName("255.255.255.255"))
            addresses.add(InetAddress.getByName(MULTICAST_GROUP))
        } catch (_: Exception) {}
        return addresses.toList()
    }

    private fun createDiscoveryMessage(ip: String) = "DISCOVERY|$deviceId|$deviceName|$ip|${System.currentTimeMillis()}"
    
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        Log.d(tag, "Starting robust LAN search")
        discoveredDevices.clear()
        if (!isWifiConnected()) return@flow

        multicastLock = try {
            wifiManager.createMulticastLock("sync_discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { null }

        val socket = DatagramSocket().apply { broadcast = true; soTimeout = 1000 }
        try {
            val localIp = getLocalIpAddress() ?: return@flow
            startSyncServer()

            // Sender
            CoroutineScope(Dispatchers.IO).launch {
                val data = createDiscoveryMessage(localIp).toByteArray()
                val targetAddresses = getBroadcastAddresses()
                repeat(BROADCAST_COUNT) {
                    targetAddresses.forEach { addr ->
                        try { socket.send(DatagramPacket(data, data.size, addr, DISCOVERY_PORT)) } catch (_: Exception) {}
                    }
                    delay(BROADCAST_INTERVAL)
                }
            }

            // Receiver
            val buffer = ByteArray(2048)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress ?: continue
                    if (senderIp == localIp) continue

                    val parts = msg.split("|")
                    if (parts.size >= 4 && parts[0] == "DISCOVERY") {
                        val device = DeviceInfo(parts[1], parts[2], "ANDROID", "LAN", senderIp, 80)
                        if (!discoveredDevices.containsKey(device.deviceId)) {
                            discoveredDevices[device.deviceId] = device
                            emit(device)
                        }
                    }
                } catch (_: SocketTimeoutException) {} catch (e: Exception) { Log.e(tag, "Search error", e) }
            }
        } finally {
            socket.close()
            multicastLock?.let { if (it.isHeld) it.release() }
        }
    }.flowOn(Dispatchers.IO)

    fun startSyncServer() {
        if (isServerRunning.get()) return
        isServerRunning.set(true)
        CoroutineScope(Dispatchers.IO).launch { nativeSyncEngine.startServer(GRPC_SYNC_PORT) }
        startDiscoveryResponseServer()
    }

    private fun startDiscoveryResponseServer() {
        Thread {
            try {
                discoveryResponseSocket = DatagramSocket(DISCOVERY_PORT).apply { soTimeout = 2000 }
                val buffer = ByteArray(2048)
                while (isServerRunning.get()) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        discoveryResponseSocket?.receive(packet)
                        val localIp = getLocalIpAddress() ?: ""
                        if (packet.address.hostAddress == localIp) continue
                        
                        val response = createDiscoveryMessage(localIp).toByteArray()
                        discoveryResponseSocket?.send(DatagramPacket(response, response.size, packet.address, packet.port))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {} finally { discoveryResponseSocket?.close() }
        }.start()
    }

    fun stopSyncServer() {
        isServerRunning.set(false)
        nativeSyncEngine.stopServer()
        discoveryResponseSocket?.close()
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

    override suspend fun prepareLocalData(): DeviceSyncData {
        val baseData = super.prepareLocalData()
        return baseData.copy(
            deviceId = deviceId,
            deviceName = deviceName
        )
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
                updateSyncProgress(0.1f, "Native Sync...", true)
                val localData = prepareLocalData()
                val responseBytes = nativeSyncEngine.performSync(device.address, GRPC_SYNC_PORT, SyncProtoConverter.toProto(localData).toByteArray())
                if (responseBytes == null) return@withContext createFailedSyncResult("Fail")

                val deviceData = SyncProtoConverter.toDomain(ProtoSyncData.parseFrom(responseBytes))
                val downloadResult = processDeviceData(deviceData)
                updateSyncProgress(1f, "Done", false)
                com.chronie.homemoney.domain.model.SyncResult(true, com.chronie.homemoney.domain.model.UploadResult(localData.entities.size, localData.entities.size, 0), downloadResult, downloadResult.conflicts)
            }
        } catch (e: Exception) {
            createFailedSyncResult(e.message ?: "Error")
        } finally {
            isSyncing = false
            delay(2000)
            clearSyncProgress()
        }
    }

    fun cleanup() {
        stopSyncServer()
        CoroutineScope(Dispatchers.IO).launch { disconnect() }
    }
}
