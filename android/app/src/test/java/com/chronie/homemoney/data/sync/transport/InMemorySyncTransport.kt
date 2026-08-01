package com.chronie.homemoney.data.sync.transport

import com.chronie.homemoney.data.sync.engine.SyncResponder
import com.chronie.homemoney.data.sync.protocol.SyncOpcode

/**
 * Test double: drives a [SyncResponder] in-process instead of over a socket.
 *
 * It mirrors what native does between two real devices - it calls the responder's
 * [com.chronie.homemoney.data.sync.transport.SyncFrameHandler.handleFrame] and wraps the
 * reply under the matching ack opcode ([SyncOpcode.ackOpcode]) - so the full initiator
 * handshake runs on the JVM with no socket and no second device.
 *
 * A `null` from the responder (it wants to close the connection) becomes a `null` reply,
 * exactly as a dropped socket would on the native path.
 */
class InMemorySyncTransport(
    private val responder: SyncResponder,
    private val peerAddress: String = "in-memory"
) : SyncTransport {

    private var closed = false

    override fun exchange(
        opcode: SyncOpcode,
        sessionId: Long,
        seq: Int,
        payload: ByteArray,
        timeoutMs: Int
    ): TransportReply? {
        if (closed) return null
        val replyBytes = responder.handleFrame(peerAddress, opcode, sessionId, seq, payload) ?: return null
        return TransportReply(opcode.ackOpcode(), sessionId, seq, replyBytes)
    }

    override fun close() {
        closed = true
    }
}
