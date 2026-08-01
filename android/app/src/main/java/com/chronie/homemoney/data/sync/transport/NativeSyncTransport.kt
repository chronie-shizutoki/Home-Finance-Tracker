package com.chronie.homemoney.data.sync.transport

import android.util.Log
import com.chronie.homemoney.data.sync.NativeSyncEngine
import com.chronie.homemoney.data.sync.protocol.FrameDecodeResult
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode
import com.chronie.homemoney.data.sync.protocol.SyncFrameFlags
import com.chronie.homemoney.data.sync.protocol.SyncOpcode
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol

/**
 * [SyncTransport] backed by the native client connection opened in [NativeSyncEngine].
 *
 * The native layer owns the framing and the socket; this class only turns the carrier's
 * `header || payload` flat buffer into a [TransportReply]. A transport failure comes back as
 * a locally generated ERROR frame, never as null, so the initiator has one shape to handle;
 * null is reserved for a socket that was never opened or has been retired after a mid-frame
 * failure.
 *
 * The reply opcode is whatever the native rebuilt from the request (the ack of the opcode we
 * sent), so the initiator validates it against the expected ack rather than assuming.
 */
/**
 * @param netHandle the `Network.getNetworkHandle()` the socket should be pinned to, or 0 for
 *   the app's default network. LAN peers are only reachable over Wi-Fi, and Wi-Fi is not
 *   always the default network, so the caller resolves it and passes it through.
 */
class NativeSyncTransport(
    engine: NativeSyncEngine,
    address: String,
    port: Int,
    connectTimeoutMs: Int = 0,
    netHandle: Long = 0L,
    private val defaultAckTimeoutMs: Int = DEFAULT_ACK_TIMEOUT_MS
) : SyncTransport {

    /** `ip:port`, kept for the log only - the peer's real identity comes from HELLO_ACK. */
    private val peer = "$address:$port"
    private val handle = engine.openSyncConnection(address, port, connectTimeoutMs, netHandle)
    private val engineRef = engine
    private var closed = false

    /**
     * Why the connection could not be opened, or null when it opened fine.
     *
     * Read here and not later: [NativeSyncEngine.lastErrorCode] is thread-local and reflects
     * the most recent native call, so a subsequent call on this thread would overwrite it.
     * Without this, a failed connect reached the user as "transport closed before HELLO_ACK",
     * which describes the symptom and hides every cause - a peer that is not listening, an
     * unreachable subnet and a malformed address all looked identical.
     */
    val connectError: SyncErrorCode? =
        if (handle == 0L) SyncErrorCode.fromCode(engine.lastErrorCode()) else null

    init {
        if (handle == 0L) {
            Log.w(TAG, "openSyncConnection to $peer failed: $connectError (net=$netHandle)")
        }
    }

    override fun exchange(
        opcode: SyncOpcode,
        sessionId: Long,
        seq: Int,
        payload: ByteArray,
        timeoutMs: Int
    ): TransportReply? {
        if (handle == 0L) return null
        val raw = engineRef.syncExchange(
            handle,
            opcode.value,
            SyncFrameFlags.NONE,
            sessionId,
            seq,
            payload,
            if (timeoutMs > 0) timeoutMs else defaultAckTimeoutMs
        ) ?: return null

        val decoded = SyncWireProtocol.decodeHeader(raw, 0)
        if (decoded !is FrameDecodeResult.Success) {
            val detail = (decoded as? FrameDecodeResult.Failure)?.detail ?: "bad header"
            Log.w(TAG, "reply header rejected from $peer: $detail")
            return null
        }

        val header = decoded.header
        val len = header.payloadLen
        if (len < 0 || SyncWireProtocol.HEADER_SIZE + len > raw.size) {
            Log.w(TAG, "reply payload length $len out of bounds from $peer")
            return null
        }
        val body = if (len == 0) {
            ByteArray(0)
        } else {
            raw.copyOfRange(SyncWireProtocol.HEADER_SIZE, SyncWireProtocol.HEADER_SIZE + len)
        }
        return TransportReply(header.opcode, header.sessionId, header.seq, body)
    }

    override fun close() {
        if (!closed) {
            closed = true
            if (handle != 0L) engineRef.closeSyncConnection(handle)
        }
    }

    companion object {
        /** Default per-exchange timeout when the caller passes 0. */
        const val DEFAULT_ACK_TIMEOUT_MS = 15_000
        private const val TAG = "NativeSyncTransport"
    }
}
