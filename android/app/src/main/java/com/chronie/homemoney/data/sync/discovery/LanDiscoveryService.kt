package com.chronie.homemoney.data.sync.discovery

import com.chronie.homemoney.data.sync.discovery.LanDiscoveryService.Companion.RECEIVE_POLL_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Wi-Fi broadcast reception on Android is off unless something holds a lock, because the
 * radio drops frames not addressed to this device to save power.
 *
 * v1 took a `MulticastLock` and then never joined a multicast group, which reads like dead
 * code but is not: the lock is also what makes plain *broadcast* reception work. The lock was
 * right; the multicast group it implied never existed. This abstraction keeps the lock, drops
 * the pretense, and lets the service be tested without a WifiManager.
 */
fun interface MulticastGate {

    /** Returns a handle to release when done, or null if this platform needs no lock. */
    fun acquire(tag: String): AutoCloseable?

    companion object {
        val NONE = MulticastGate { null }
    }
}

/** Discovery counters and failures. Wired to the app's metrics in P5; no-op by default. */
interface DiscoveryTelemetry {
    fun onIgnored(reason: IgnoreReason, detail: String) = Unit
    fun onRecorded(update: DiscoveryRegistry.Update, device: DiscoveredDevice) = Unit
    fun onReplied(to: String) = Unit
    fun onError(stage: String, error: Throwable) = Unit

    companion object {
        val NONE = object : DiscoveryTelemetry {}
    }
}

/**
 * UDP discovery: one long-lived responder, plus short search rounds on demand.
 *
 * ### What this replaces
 *
 * v1 sent the same plain-text message in both directions on a single well-known port, kept
 * every device it ever saw, guessed the peer's sync port, and polled a 12-second loop that
 * never checked whether it had been canceled. The field failures that followed: phantom
 * devices from unrelated UDP traffic, ghosts for devices long gone, stale IPs after a Wi-Fi
 * switch, devices that appear but cannot be connected to, self-discovery on multi-NIC phones,
 * and a reception loop that outlived the screen that started it.
 *
 * ### Shape
 *
 * - The **responder** binds the discovery port for the life of the sync server. It answers
 *   queries by unicast to the querier's ephemeral port, records who asked, and re-announces
 *   itself periodically so peers' TTLs stay fresh.
 * - A **search** uses its own ephemeral socket. Replies come back there, so they never touch
 *   the broadcast port and cannot be mistaken for queries and re-answered.
 * - Both share one [registry], so a search begins by emitting whatever the responder already
 *   learned while the user was elsewhere in the app. Discovery usually looks instant.
 *
 * Cancellation is honored within [RECEIVE_POLL_MS]: the blocking receive uses a socket
 * timeout as its checkpoint, and [closeResponder] can cut that short.
 */
class LanDiscoveryService(
    private val identity: () -> DiscoveryIdentity,
    private val discoveryPort: Int = DEFAULT_DISCOVERY_PORT,
    val registry: DiscoveryRegistry = DiscoveryRegistry(),
    private val multicastGate: MulticastGate = MulticastGate.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val telemetry: DiscoveryTelemetry = DiscoveryTelemetry.NONE
) {

    private val responderSocket = AtomicReference<DatagramSocket?>(null)
    private val random = SecureRandom()

    // ------------------------------------------------------------------ responder

    /**
     * Serves discovery until the calling coroutine is canceled. Returns normally if the port
     * cannot be bound: another process holding it is a reason for discovery to be unavailable,
     * not a reason to take the sync server down.
     */
    suspend fun runResponder(
        announceIntervalMs: Long = DEFAULT_ANNOUNCE_INTERVAL_MS
    ) = withContext(Dispatchers.IO) {
        val gate = runCatching { multicastGate.acquire(GATE_RESPONDER) }.getOrNull()
        var opened: DatagramSocket? = null
        var announcer: Job? = null
        try {
            val socket = DatagramSocket(null).apply {
                // A fast restart of this app would otherwise fail to rebind and leave the
                // device silently undiscoverable until the OS released the port.
                reuseAddress = true
                broadcast = true
                soTimeout = RECEIVE_POLL_MS
                bind(InetSocketAddress(discoveryPort))
            }
            opened = socket
            responderSocket.set(socket)

            // Enumerating interfaces per packet would be wasteful, and per process would go
            // stale the moment Wi-Fi changes. Refreshed on the announcement tick instead.
            val decider = AtomicReference(newDecider())
            announcer = launch { announceLoop(socket, announceIntervalMs, decider) }

            val buffer = ByteArray(DiscoveryWire.MAX_PACKET_SIZE)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue                                  // the cancellation checkpoint
                } catch (e: Exception) {
                    if (!isActive || socket.isClosed) break
                    telemetry.onError(STAGE_RESPOND, e)
                    continue
                }
                handle(socket, packet, decider.get(), expectedNonce = null, emit = null)
            }
        } catch (e: Exception) {
            telemetry.onError(STAGE_BIND, e)
        } finally {
            announcer?.cancel()
            responderSocket.compareAndSet(opened, null)
            opened.closeQuietly()
            gate.closeQuietly()
        }
    }

    /** Unblocks a responder sitting in [DatagramSocket.receive] instead of waiting out the poll. */
    fun closeResponder() {
        responderSocket.getAndSet(null).closeQuietly()
    }

    /**
     * Unsolicited presence, so a peer that is not actively searching still keeps us alive in
     * its registry. Without it the TTL would expire anyone merely idle, and every search would
     * start from an empty list.
     */
    private suspend fun announceLoop(
        socket: DatagramSocket,
        intervalMs: Long,
        decider: AtomicReference<DiscoveryDecider>
    ) {
        while (currentCoroutineContext().isActive) {
            val nics = LocalNetworkAddresses.enumerate()
            decider.set(newDecider(nics))

            val targets = LocalNetworkAddresses.broadcastAddresses(nics)
            if (targets.isNotEmpty()) {
                val payload = runCatching { encodeAnnounce(identity(), nonce = 0L) }.getOrNull()
                if (payload != null) {
                    targets.forEach { sendDatagram(socket, payload, it, discoveryPort, STAGE_ANNOUNCE) }
                }
            }
            delay(intervalMs.milliseconds)
        }
    }

    // ------------------------------------------------------------------ search

    /**
     * One search round. Emits each device once, and again if it moved to a new address, port
     * or capability set — v1 kept the first sighting forever, so after a network change the
     * only entry the user could see was the one guaranteed to be stale.
     */
    // NOTE: this method sends UDP broadcasts, which on Android 17 (API 37) require the
    // ACCESS_LOCAL_NETWORK permission (Local Network Protection). This class holds no
    // Activity Context, so it cannot request the permission itself - the caller
    // (DeviceSearchDialog via rememberLocalNetworkPermissionRequester) MUST ensure the
    // permission is granted before invoking search(). Without it the socket send fails
    // silently with EPERM.
    fun search(
        timeoutMs: Long = DEFAULT_SEARCH_TIMEOUT_MS,
        queryBursts: Int = DEFAULT_QUERY_BURSTS,
        burstIntervalMs: Long = DEFAULT_BURST_INTERVAL_MS
    ): Flow<DiscoveredDevice> = channelFlow {
        // Whatever the responder picked up while the user was on another screen.
        registry.snapshot(clock()).forEach { send(it) }

        val self = identity()
        val nics = LocalNetworkAddresses.enumerate()
        val broadcasts = LocalNetworkAddresses.selectBroadcasts(nics)
        if (broadcasts.isEmpty()) {
            telemetry.onError(STAGE_SEARCH, IllegalStateException("no broadcast-capable interface"))
            return@channelFlow
        }

        val socket = try {
            DatagramSocket().apply {
                broadcast = true
                soTimeout = RECEIVE_POLL_MS
            }
        } catch (e: Exception) {
            telemetry.onError(STAGE_SEARCH, e)
            return@channelFlow
        }

        val gate = runCatching { multicastGate.acquire(GATE_SEARCH) }.getOrNull()
        val nonce = newNonce()
        val decider = newDecider(nics, self)

        val sender = launch {
            broadcastQuery(socket, self, nonce, broadcasts, queryBursts, burstIntervalMs)
        }

        try {
            val deadline = clock() + timeoutMs
            val buffer = ByteArray(DiscoveryWire.MAX_PACKET_SIZE)
            while (isActive && clock() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    if (!isActive || socket.isClosed) break
                    telemetry.onError(STAGE_SEARCH, e)
                    continue
                }
                handle(socket, packet, decider, expectedNonce = nonce, emit = { send(it) })
            }
        } finally {
            sender.cancel()
            socket.closeQuietly()
            gate.closeQuietly()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun broadcastQuery(
        socket: DatagramSocket,
        self: DiscoveryIdentity,
        nonce: Long,
        broadcasts: List<String>,
        bursts: Int,
        intervalMs: Long
    ) {
        val query = runCatching {
            DiscoveryWire.encode(
                DiscoveryPacket(
                    type = DiscoveryType.QUERY,
                    deviceId = self.deviceId,
                    deviceName = self.deviceName,
                    deviceType = self.deviceType,
                    syncPort = self.syncPort,
                    capabilities = self.capabilities,
                    nonce = nonce
                )
            )
        }.getOrElse {
            telemetry.onError(STAGE_QUERY, it)
            null
        }

        if (query == null) return

        val targets = broadcasts.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
        repeat(bursts) { round ->
            targets.forEach { target ->
                sendDatagram(socket, query, target, discoveryPort, STAGE_QUERY)
            }
            if (round < bursts - 1) delay(intervalMs.milliseconds)
        }
    }

    // ------------------------------------------------------------------ shared

    /**
     * Runs one datagram through the decision and performs it.
     *
     * [emit] is null on the responder, which has nowhere to publish to; it still records, so
     * the next search starts warm.
     */
    private suspend fun handle(
        socket: DatagramSocket,
        packet: DatagramPacket,
        decider: DiscoveryDecider,
        expectedNonce: Long?,
        emit: (suspend (DiscoveredDevice) -> Unit)?
    ) {
        val action = decider.decide(
            data = packet.data,
            length = packet.length,
            senderIp = packet.address?.hostAddress.orEmpty(),
            senderPort = packet.port,
            expectedNonce = expectedNonce
        )

        when (action) {
            is DiscoveryAction.Ignore -> telemetry.onIgnored(action.reason, action.detail)

            is DiscoveryAction.Record -> record(action.device, emit)

            is DiscoveryAction.Reply -> {
                // The querier is a live device too; recording it halves the time to a
                // complete list when both sides search at once.
                record(action.querier, emit)
                respond(socket, action)
            }
        }
    }

    private suspend fun record(
        device: DiscoveredDevice,
        emit: (suspend (DiscoveredDevice) -> Unit)?
    ) {
        val update = registry.observe(device, clock())
        telemetry.onRecorded(update, device)
        if (update != DiscoveryRegistry.Update.REFRESHED) emit?.invoke(device)
    }

    private fun respond(socket: DatagramSocket, reply: DiscoveryAction.Reply) {
        val self = identity()
        val payload = runCatching {
            // Echo the querier's nonce so it can tell our reply from a straggler.
            encodeAnnounce(self, reply.nonce)
        }.getOrElse {
            telemetry.onError(STAGE_REPLY, it)
            return
        }

        val target = runCatching { InetAddress.getByName(reply.to) }.getOrNull() ?: return
        if (sendDatagram(socket, payload, target, reply.port, STAGE_REPLY)) {
            telemetry.onReplied(reply.to)
        }
    }

    private fun newDecider(
        nics: List<LocalNetworkAddresses.Nic> = LocalNetworkAddresses.enumerate(),
        self: DiscoveryIdentity = identity()
    ) = DiscoveryDecider(self, LocalNetworkAddresses.selectLocalIpv4(nics))

    private fun encodeAnnounce(self: DiscoveryIdentity, nonce: Long): ByteArray =
        DiscoveryWire.encode(
            DiscoveryPacket(
                type = DiscoveryType.ANNOUNCE,
                deviceId = self.deviceId,
                deviceName = self.deviceName,
                deviceType = self.deviceType,
                syncPort = self.syncPort,
                capabilities = self.capabilities,
                nonce = nonce
            )
        )

    private fun sendDatagram(
        socket: DatagramSocket,
        payload: ByteArray,
        target: InetAddress,
        port: Int,
        stage: String
    ): Boolean = try {
        socket.send(DatagramPacket(payload, payload.size, target, port))
        true
    } catch (e: Exception) {
        // One unreachable interface must not abort the round; the others may still work.
        telemetry.onError(stage, e)
        false
    }

    /** Non-zero, so it can never be confused with the "unsolicited" nonce. */
    private fun newNonce(): Long {
        var value = random.nextLong()
        while (value == 0L) value = random.nextLong()
        return value
    }

    private fun AutoCloseable?.closeQuietly() {
        try {
            this?.close()
        } catch (_: Exception) {
            // Best effort: a failure to close has nothing left to affect.
        }
    }

    companion object {
        const val DEFAULT_DISCOVERY_PORT = 12345

        /**
         * How long a blocking receive waits before the loop re-checks cancellation. Short
         * enough that leaving the sync screen feels immediate, long enough that an idle
         * responder is not spinning.
         */
        const val RECEIVE_POLL_MS = 500

        /** Paired with [DiscoveryRegistry.DEFAULT_TTL_MS]: four misses before a peer expires. */
        const val DEFAULT_ANNOUNCE_INTERVAL_MS = 8_000L

        const val DEFAULT_SEARCH_TIMEOUT_MS = 12_000L

        /** Bursts absorb the single-packet loss that is normal on a busy 2.4GHz channel. */
        const val DEFAULT_QUERY_BURSTS = 5
        const val DEFAULT_BURST_INTERVAL_MS = 1_000L

        private const val GATE_RESPONDER = "hf-discovery-responder"
        private const val GATE_SEARCH = "hf-discovery-search"
        private const val STAGE_BIND = "discovery-bind"
        private const val STAGE_RESPOND = "discovery-respond"
        private const val STAGE_REPLY = "discovery-reply"
        private const val STAGE_QUERY = "discovery-query"
        private const val STAGE_ANNOUNCE = "discovery-announce"
        private const val STAGE_SEARCH = "discovery-search"
    }
}
