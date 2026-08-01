package com.chronie.homemoney.data.sync.transport

import com.chronie.homemoney.data.sync.protocol.SyncOpcode

/**
 * One round of the v2 request/response exchange, abstracted away from the concrete carrier.
 *
 * The production implementation ([NativeSyncTransport]) hands each frame to the native
 * transport, which keeps a single TCP socket open for the whole session and rebuilds the
 * reply header from the request. A test implementation ([InMemorySyncTransport]) drives a
 * [com.chronie.homemoney.data.sync.engine.SyncResponder] in-process, which is what lets the
 * full handshake run on the JVM without a socket or a second device.
 *
 * The reply is returned already split into opcode and payload. A transport failure arrives as
 * an ERROR opcode carrying a [com.chronie.homemoney.data.sync.protocol.SyncErrorCode] in its
 * four-byte body, so there is exactly one error shape to handle; a `null` reply means the
 * connection cannot continue at all (open failed, retired, or a header that would not
 * decode) and the caller should treat it as fatal.
 */
interface SyncTransport {

    /**
     * Sends [payload] as [opcode] under [sessionId]/[seq] and returns the reply.
     *
     * @param timeoutMs per-exchange deadline in milliseconds. AUTH needs a long one because
     *   it blocks on the peer's user; a CHUNK does not.
     * @return the decoded reply, or null when the transport cannot continue.
     */
    fun exchange(
        opcode: SyncOpcode,
        sessionId: Long,
        seq: Int,
        payload: ByteArray,
        timeoutMs: Int
    ): TransportReply?

    /** Releases the underlying connection. Idempotent. */
    fun close()
}

/**
 * A decoded reply frame: the reply opcode plus its payload bytes.
 *
 * [sessionId] and [seq] are echoed from the request by the carrier; the initiator uses them
 * only for diagnostics. The payload is the raw protobuf bytes of the matching ack message
 * (HELLO_ACK, AUTH_ACK, ...).
 */
data class TransportReply(
    val opcode: SyncOpcode,
    val sessionId: Long,
    val seq: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TransportReply
        return opcode == other.opcode &&
            sessionId == other.sessionId &&
            seq == other.seq &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = opcode.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + seq
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
