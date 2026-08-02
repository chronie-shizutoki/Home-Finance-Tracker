package com.chronie.homemoney.data.sync

import android.util.Log
import androidx.annotation.Keep
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.transport.SyncFrameHandler

/**
 * The JNI seam between Kotlin and the native transport.
 *
 * Two dialects live here on purpose. `startServer` sniffs each connection and routes it to
 * either the v1 or the v2 handler, so a phone still running the old build keeps working
 * while the other end has already moved on. The two paths share nothing but this class.
 *
 * Both upcalls are resolved by name and signature from `startServer` in native-lib.cpp. A
 * rename, a reordered parameter or a changed return type will not fail the build - it fails
 * at runtime with "handleIncomingFrame is missing" in logcat and a server that silently
 * refuses every v2 frame. Keep the signatures in step with the `GetMethodID` strings.
 */
@Keep
class NativeSyncEngine {

    /** v1 upcall contract. Superseded by [SyncFrameHandler]; kept until the old peers are gone. */
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

    // Both are read from native pool threads and written from the main thread, so neither
    // can be a plain field: without volatile a worker may never observe the installed
    // handler and would answer perfectly good frames with "no handler".
    @Volatile
    private var listener: SyncRequestListener? = null

    @Volatile
    private var frameHandler: SyncFrameHandler? = null

    fun setSyncRequestListener(listener: SyncRequestListener) {
        this.listener = listener
    }

    /**
     * Installs the v2 frame handler, or clears it with null.
     *
     * Safe to call while the server is running. Until this is set every v2 frame is
     * refused, which is the correct failure: answering frames with no engine behind them
     * would let a peer drive a handshake that can never apply anything.
     */
    fun setFrameHandler(handler: SyncFrameHandler?) {
        frameHandler = handler
    }

    /**
     * Handle incoming sync requests from remote devices via JNI
     */
    @Keep
    fun handleIncomingSyncRequest(deviceId: String, deviceName: String, data: ByteArray): ByteArray? {
        Log.d(TAG, "JNI: Incoming sync data from $deviceName ($deviceId)")
        return listener?.onSyncDataReceived(deviceId, deviceName, data)
    }

    /**
     * Handles one v2 frame handed up by `serveV2`.
     *
     * Called on a native worker thread, several at a time. Native has already validated the
     * magic, both checksums, the version and the payload cap, so everything arriving here is
     * structurally sound; what is left is protocol semantics, which is the handler's job.
     *
     * The return value is the reply *payload* only - native picks the reply opcode and
     * rebuilds the header. Null means "close this connection", which native turns into an
     * ERROR(CANCELLED) frame so the peer learns why instead of seeing a bare disconnect.
     *
     * @param opcode raw opcode byte; unknown values are refused rather than guessed at.
     * @param seq raw 32-bit sequence number, may be negative once it passes 2^31.
     */
    @Keep
    fun handleIncomingFrame(
        peerAddress: String,
        opcode: Int,
        sessionId: Long,
        seq: Int,
        payload: ByteArray
    ): ByteArray? {
        val handler = frameHandler
        if (handler == null) {
            Log.w(TAG, "v2 frame 0x%02X from %s dropped: no frame handler installed"
                .format(opcode, peerAddress))
            return null
        }

        val decoded = SyncOpcode.fromValue(opcode)
        if (decoded == null) {
            // Native filters on requiresUpperLayer, so this means the peer is newer than
            // this build. Closing is right: pretending to understand it would be worse.
            Log.w(TAG, "v2 frame from %s carries unknown opcode 0x%02X"
                .format(peerAddress, opcode))
            return null
        }

        return try {
            handler.handleFrame(peerAddress, decoded, sessionId, seq, payload)
        } catch (t: Throwable) {
            // An exception must not cross the JNI boundary. Native would only see
            // "Kotlin threw", clear it, and drop the connection - the stack trace, which is
            // the one thing that makes this diagnosable, would be lost. Log it here instead.
            Log.e(TAG, "frame handler threw on $decoded from $peerAddress", t)
            null
        }
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

    external fun startServer(port: Int): Int
    external fun stopServer()
    external fun performSync(address: String, port: Int, data: ByteArray): ByteArray?

    /**
     * Opens a client connection to a peer and returns an opaque handle, or 0 on failure.
     *
     * [performSync] is a one-shot - connect, send one frame, hang up - which is all a
     * legacy v1 peer needs. The v2 handshake is five request/response pairs that share one
     * session, and a session only exists for as long as the responder holds the socket
     * open, so the initiator needs a connection it can keep. Session semantics stay in
     * Kotlin because that is where the protobuf schema lives; native only moves frames.
     *
     * Every non-zero handle must reach [closeSyncConnection], including on failure paths,
     * or the file descriptor leaks for the life of the process.
     *
     * @param connectTimeoutMs 0 or less means "use the value from [configureTransport]".
     * @param netHandle `Network.getNetworkHandle()` of the network the socket must use, or 0
     *   for the app's default network. This is not a nicety: a socket opened natively
     *   inherits the process default network, and a phone that stays on cellular because its
     *   Wi-Fi has no internet has no route to a LAN peer, so `connect()` fails with
     *   ENETUNREACH before the handshake starts. `ConnectivityManager` is only visible from
     *   Kotlin, so the decision is made there and passed down as an opaque value.
     */
    external fun openSyncConnection(
        address: String,
        port: Int,
        connectTimeoutMs: Int,
        netHandle: Long
    ): Long

    /**
     * Sends one frame and returns the reply as a flat `header || payload` buffer, decodable
     * with `SyncWireProtocol.decodeHeader`.
     *
     * A transport failure comes back as a locally generated ERROR frame rather than null,
     * so there is exactly one shape to handle and the reason is always a `SyncErrorCode`
     * in the body. Null is reserved for a malformed call: an unknown handle, an opcode
     * outside the protocol, or a body over the frame cap.
     *
     * Once an exchange fails the connection is retired natively - the stream is out of
     * step mid-frame - and every later call on that handle answers PEER_CLOSED.
     *
     * @param timeoutMs deadline for this exchange alone. AUTH needs a long one because it
     *   blocks on the remote user tapping "accept"; a CHUNK does not.
     */
    external fun syncExchange(
        handle: Long,
        opcode: Int,
        flags: Int,
        sessionId: Long,
        seq: Int,
        payload: ByteArray,
        timeoutMs: Int
    ): ByteArray?

    /** Closes a client connection. Idempotent, and harmless on an unknown handle. */
    external fun closeSyncConnection(handle: Long)

    /**
     * Tunes the client transport. Values are clamped natively, so a nonsensical argument
     * degrades the timeout rather than wedging a worker.
     */
    external fun configureTransport(connectTimeoutMs: Int, ioTimeoutMs: Int, maxAttempts: Int)

    /** Error code of the last [performSync] on this thread; maps to `SyncErrorCode.fromCode`. */
    external fun lastErrorCode(): Int

    /** Transport counters as flat JSON, for the diagnostics screen and bug reports. */
    external fun transportStats(): String
}
