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
import com.chronie.homemoney.data.sync.telemetry.SyncMetricsSnapshot
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.chronie.homemoney.domain.sync.SyncProgressInfo
import com.chronie.homemoney.domain.sync.SyncRequestInfo
import com.chronie.homemoney.data.sync.generated.ConflictSummary
import com.chronie.homemoney.data.sync.generated.DeviceSyncData as ProtoSyncData
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

        /**
         * The UI shows a signal bar for every device. LAN discovery has no RSSI to report —
         * a UDP datagram either arrived or it did not — so this is a constant, as it was in
         * v1. Named rather than inlined so it is obvious it is a placeholder and not a
         * measurement anyone should reason about.
         */
        private const val LAN_SIGNAL_PLACEHOLDER = 80

        private const val SYNC_PREFS = "sync_prefs"
        /** Devices the user has already accepted once; they skip the prompt, not the proof. */
        private const val KEY_TRUSTED_DEVICES = "trusted_device_ids"
        /** Shared pairing code. Absent means the proof exchange is off, as in v1. */
        private const val KEY_PAIRING_CODE = "pairing_code"
    }
    
    private val nativeSyncEngine = NativeSyncEngine()
    private var isServerRunning = AtomicBoolean(false)

    /**
     * Counters for everything the sync stack does.
     *
     * Public because the diagnostics screen and the bug-report exporter both need it, and
     * because a support conversation that starts with [SyncMetricsSnapshot.format] is worth
     * an hour of guessing. Recording is lock-free, so handing this out costs nothing.
     */
    val metrics = SyncMetrics()

    /**
     * Structured lines to logcat, keyed by trace id.
     *
     * The counters say *how often*; these say *which session, in what order*. Both ends of
     * a sync stamp the same trace id, so two logcat dumps can be zipped into one story —
     * which is the only way to debug a failure that only one side can see.
     */
    private val logSink = LogcatSyncLogSink()

    /**
     * Owns the discovery responder's lifetime. Not cancelled on stop — the manager can be
     * started again — so the job is cancelled individually instead.
     */
    private val discoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var responderJob: Job? = null

    /**
     * Wraps the Wi-Fi multicast lock as a plain handle so the discovery service has no
     * Android dependency and stays unit-testable.
     */
    private val multicastGate = MulticastGate { lockTag ->
        try {
            val lock = wifiManager.createMulticastLock(lockTag).apply {
                setReferenceCounted(false)
                acquire()
            }
            AutoCloseable { if (lock.isHeld) lock.release() }
        } catch (e: Exception) {
            // Some OEM builds refuse the lock. Discovery degrades to whatever broadcast the
            // radio lets through rather than failing outright.
            Log.w(tag, "Multicast lock unavailable for $lockTag", e)
            null
        }
    }

    private val discovery: LanDiscoveryService by lazy {
        LanDiscoveryService(
            identity = {
                DiscoveryIdentity(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    deviceType = "ANDROID",
                    syncPort = GRPC_SYNC_PORT
                )
            },
            discoveryPort = DISCOVERY_PORT,
            multicastGate = multicastGate,
            telemetry = MetricsDiscoveryTelemetry(metrics, logSink)
        )
    }

    
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
                        val accepted = callback.onSyncRequest(info)
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
        val prefs = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
        prefs.getString("device_sync_id", null) ?: "android_${UUID.randomUUID().toString().substring(0, 8)}".also {
            prefs.edit().putString("device_sync_id", it).apply()
        }
    }
    
    private val deviceName: String by lazy {
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getString("device_custom_name", null) ?: Build.MODEL ?: "Android Device"
    }

    // Expose the persisted identity to the base class so that prepareLocalData stamps the
    // real sender id and ExpenseMerger has a stable value for deterministic tie-breaking.
    override val localDeviceId: String get() = deviceId
    override val localDeviceName: String get() = deviceName

    // ------------------------------------------------------------------ v2 responder

    /**
     * Drives the client half of the v2 handshake.
     *
     * Built lazily because it captures [deviceId] / [deviceName], which are themselves lazy
     * over SharedPreferences and must not be touched during construction. It reuses this
     * manager's [PromptingSyncAuthorizer] so the initiator advertises the same pairing code
     * the responder enforces and, when the peer is known, skips the human prompt.
     */
    private val syncInitiator: SyncInitiator by lazy {
        SyncInitiator(
            store = RoomSyncEntityStore(expenseDao),
            identity = SyncIdentity(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceType = "ANDROID"
            ),
            authorizer = PromptingSyncAuthorizer()
        )
    }

    /**
     * Handles v2 frames. Built lazily because it captures [deviceId] and [deviceName], which
     * are themselves lazy over SharedPreferences and must not be touched during construction.
     *
     * This does not replace the v1 listener above; both stay installed. Native sniffs each
     * connection and picks a dialect, so an un-upgraded phone keeps syncing over v1 while a
     * v2 peer gets the resumable path. The two share the database and nothing else.
     */
    private val syncResponder: SyncResponder by lazy {
        SyncResponder(
            store = RoomSyncEntityStore(expenseDao),
            identity = SyncIdentity(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceType = "ANDROID"
            ),
            authorizer = PromptingSyncAuthorizer(),
            observer = MetricsResponderObserver(metrics, logSink)
        )
    }

    /**
     * Bridges the responder's two authorisation gates to what the app already has.
     *
     * Confirmation reuses the existing dialog callback. Pairing reads a code the user may
     * have set; when there is none the proof exchange is skipped, which is exactly v1's
     * behaviour and keeps this change from locking anyone out of their own devices. Trust is
     * remembered per device id so a repeat sync is quiet.
     */
    private inner class PromptingSyncAuthorizer : SyncAuthorizer {

        private val prefs get() = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)

        override fun pairingCode(): String? =
            prefs.getString(KEY_PAIRING_CODE, null)?.takeIf { it.isNotBlank() }

        override fun isTrusted(deviceId: String): Boolean =
            prefs.getStringSet(KEY_TRUSTED_DEVICES, emptySet())?.contains(deviceId) == true

        override fun confirm(
            request: SyncAuthorizer.Request,
            timeoutMs: Long
        ): SyncAuthorizer.Decision {
            val callback = syncRequestCallback
            if (callback == null) {
                // No screen is mounted that can ask. Fall back to an app-wide prompt so the
                // request is not silently refused - that silent refusal is exactly the
                // "B shows nothing, A fails" symptom.
                Log.i(tag, "No sync request callback installed; using app-wide prompt for ${request.deviceName}")
                return confirmViaBus(request, timeoutMs)
            }

            val accepted = AtomicBoolean(false)
            val answered = CountDownLatch(1)
            val job = CoroutineScope(Dispatchers.Main).launch {
                try {
                    accepted.set(
                        callback.onSyncRequest(
                            SyncRequestInfo(request.deviceId, request.deviceName, request.peerAddress)
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Sync request dialog failed for ${request.deviceName}", e)
                } finally {
                    answered.countDown()
                }
            }

            val inTime = try {
                // Called on a native worker, so blocking here is expected. The deadline is
                // the responder's, which sits inside the native handler timeout.
                answered.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                job.cancel()
                Log.w(tag, "Interrupted while waiting on the user for ${request.deviceName}")
                return SyncAuthorizer.Decision.REJECTED
            }

            if (!inTime) {
                job.cancel()
                // Distinct from a refusal: the peer should retry later, not give up.
                return SyncAuthorizer.Decision.TIMED_OUT
            }
            return if (accepted.get()) {
                SyncAuthorizer.Decision.ACCEPTED
            } else {
                SyncAuthorizer.Decision.REJECTED
            }
        }

        /**
         * Screen-independent fallback used when no UI screen has installed a
         * [syncRequestCallback]. Publishes the request on [SyncRequestBus] (observed by the
         * app root in [com.chronie.homemoney.MainActivity]) and blocks this native worker
         * thread until the user decides or the responder's deadline elapses. This is what
         * lets B confirm an incoming sync from any screen, not just the LAN-sync settings page.
         */
        private fun confirmViaBus(
            request: SyncAuthorizer.Request,
            timeoutMs: Long
        ): SyncAuthorizer.Decision {
            val info = SyncRequestInfo(request.deviceId, request.deviceName, request.peerAddress)
            val future = SyncRequestBus.post(info)
            return try {
                val accepted = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                if (accepted) SyncAuthorizer.Decision.ACCEPTED else SyncAuthorizer.Decision.REJECTED
            } catch (e: java.util.concurrent.TimeoutException) {
                SyncRequestBus.cancel()
                Log.w(tag, "App-wide sync prompt timed out for ${request.deviceName}")
                SyncAuthorizer.Decision.TIMED_OUT
            } catch (e: Exception) {
                SyncRequestBus.cancel()
                Log.e(tag, "App-wide sync prompt failed for ${request.deviceName}", e)
                SyncAuthorizer.Decision.REJECTED
            }
        }

        override fun remember(deviceId: String, deviceName: String) {
            val current = prefs.getStringSet(KEY_TRUSTED_DEVICES, emptySet()) ?: emptySet()
            if (deviceId in current) return
            // getStringSet hands back a set that must not be mutated in place, so build a
            // new one; writing the returned instance back is a documented way to lose data.
            prefs.edit().putStringSet(KEY_TRUSTED_DEVICES, current + deviceId).apply()
        }
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
    
    /**
     * Discovery is now [LanDiscoveryService]'s job: structured packets, a registry that
     * expires, interface-scoped broadcast, and a receive loop that honours cancellation.
     *
     * The server is started first so that a peer answering our query has something to
     * connect to, and so our responder is bound before the burst goes out.
     */
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        if (!isWifiConnected()) {
            Log.d(tag, "Skipping LAN search: no Wi-Fi transport")
            return@flow
        }
        startSyncServer()
        emitAll(discovery.search().map { it.toDeviceInfo() })
    }.flowOn(Dispatchers.IO)

    /**
     * The port is dropped here because [DeviceInfo] has no field for it. It is not lost:
     * [discovery]'s registry keeps it and [resolveSyncPort] reads it back at connect time.
     * Widening the domain model would touch every sync manager and the UI, which is a change
     * worth making on its own rather than smuggling into the discovery rewrite.
     */
    private fun DiscoveredDevice.toDeviceInfo() = DeviceInfo(
        deviceId,
        deviceName,
        deviceType.ifBlank { "ANDROID" },
        "LAN",
        address,
        LAN_SIGNAL_PLACEHOLDER
    )

    /**
     * The peer's advertised sync port, falling back to ours.
     *
     * v1 always used the constant, so a peer listening anywhere else was discoverable and
     * unreachable at the same time — the failure looked like a broken sync rather than a
     * mismatched port. The fallback keeps v1 peers working, since their packets carry no port.
     */
    private fun resolveSyncPort(device: DeviceInfo): Int =
        discovery.registry.get(device.deviceId, System.currentTimeMillis())?.syncPort
            ?: GRPC_SYNC_PORT

    /**
     * The Wi-Fi [Network], or null when Wi-Fi is not currently an active transport.
     *
     * Deliberately *not* `activeNetwork`: the whole point is that Wi-Fi is often connected
     * without being active. Android keeps cellular as the default network whenever the
     * Wi-Fi it is attached to fails the internet-validation probe - which is the normal
     * state of a router with no uplink, a guest network behind a captive portal, or simply a
     * phone that has decided the Wi-Fi is "poor". So we ask for the Wi-Fi transport by name.
     */
    @Suppress("DEPRECATION")  // allNetworks: the callback-based replacement answers
    // asynchronously, and this is called on the synchronous path right before connect().
    // Still supported on every API level this app targets; revisit if that changes.
    private fun wifiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        return try {
            cm.allNetworks.firstOrNull { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (e: Exception) {
            Log.w(tag, "could not enumerate networks; sync will use the default route", e)
            null
        }
    }

    /**
     * The opaque handle the native transport needs to pin its socket to Wi-Fi, or 0 for
     * "use the default network".
     *
     * Why a socket-level bind rather than [ConnectivityManager.bindProcessToNetwork]: the
     * process-wide call would drag every other socket in the app onto Wi-Fi for the duration
     * of a sync - including whatever the rest of the app is doing on cellular at the time -
     * and it is global mutable state that two overlapping callers can trample. Pinning the
     * one socket that needs the LAN keeps the blast radius at exactly that socket.
     */
    private fun wifiNetworkHandle(): Long {
        val handle = wifiNetwork()?.networkHandle ?: 0L
        if (handle == 0L) {
            // Worth a log: the sync is about to attempt the connect that has been failing.
            Log.w(tag, "no Wi-Fi network available; LAN connect will use the default route")
        }
        return handle
    }

    /**
     * Turns a connect failure into something a user can act on.
     *
     * The native layer collapses several kernel errors into NETWORK_UNREACHABLE, so the
     * wording stays honest about the two things it can actually mean rather than inventing a
     * precision the code does not have. The exact errno is in logcat under HomeMoneySync.
     */
    private fun describeConnectFailure(error: SyncErrorCode): String = when (error) {
        SyncErrorCode.CONNECT_TIMEOUT ->
            "Peer did not answer. Check both devices are on the same Wi-Fi."
        SyncErrorCode.NETWORK_UNREACHABLE ->
            "Could not reach the peer. Open Home Money on the other device and keep it on the same Wi-Fi."
        else -> "Connection failed ($error)"
    }

    fun startSyncServer() {
        // compareAndSet, not get-then-set: searchDevices and the UI can both call this, and
        // the v1 gap between the two let a second discovery responder bind the same port.
        if (!isServerRunning.compareAndSet(false, true)) return
        // Install before listening. The handler is volatile so a late install would still be
        // seen, but a peer that connects in that window would be refused for no good reason.
        nativeSyncEngine.setFrameHandler(syncResponder)
        discoveryScope.launch { nativeSyncEngine.startServer(GRPC_SYNC_PORT) }
        responderJob = discoveryScope.launch { discovery.runResponder() }
    }

    fun stopSyncServer() {
        val wasRunning = isServerRunning.getAndSet(false)
        nativeSyncEngine.stopServer()
        nativeSyncEngine.setFrameHandler(null)
        // stopServer only unblocks the sockets; a worker may still be mid-frame. Dropping
        // the sessions here releases their chunk buffers instead of holding tens of MB
        // until the next sync happens to reuse the registry. Guarded so that stopping a
        // server that never started does not build the responder just to clear it.
        if (wasRunning) {
            syncResponder.sessions.clear()
        }
        // Cancel then close: cancellation alone would leave the loop blocked in receive for
        // up to one poll interval, and the port with it.
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

    // prepareLocalData is inherited: the base implementation already stamps the sender
    // identity via localDeviceId / localDeviceName and includes deletion tombstones.

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
            // Pin the socket to Wi-Fi. The peer is on the Wi-Fi subnet, but the app's default
            // network is whatever Android validated as having internet - typically cellular
            // when the Wi-Fi router has no uplink. An unpinned connect() to a LAN IP then
            // fails with ENETUNREACH before the first frame, which is the "discoverable but
            // not connectable" report: the peer is found over inbound UDP, yet nothing we
            // send ever leaves the phone.
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
                // Prefer the connect error when there is one: the initiator can only report
                // that the transport never answered, which is true of every possible cause.
                val reason = transport.connectError?.let { describeConnectFailure(it) }
                    ?: outcome.errorMessage
                    ?: "sync failed"
                Log.w(tag, "v2 sync with ${device.deviceName} failed: $reason")
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

    /** Maps a wire [ConflictSummary] onto the domain [com.chronie.homemoney.domain.model.SyncConflict]. */
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
        // The registry outlives a single search on purpose, but not the manager: keeping it
        // would hand the next instance a list of devices nobody has heard from since.
        discovery.registry.clear()
        discoveryScope.launch { disconnect() }
    }
}
