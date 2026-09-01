package com.chronie.homemoney.data.sync.transport

import com.chronie.homemoney.data.sync.protocol.SyncOpcode

/**
 * The Kotlin end of the native v2 frame contract.
 *
 * `serveV2` in native-lib.cpp owns the framing: it validates the magic, both checksums, the
 * version and the payload cap, answers PING itself, and then hands anything that carries
 * application data to this interface. The division of labor is worth stating precisely,
 * because getting it wrong is how the two layers previously ended up each assuming the other
 * had validated the input:
 *
 *  - **Native guarantees**, before this is called: the frame is intact, the opcode is one
 *    that `requiresUpperLayer` accepts, and `payload` is exactly `payload_len` bytes that
 *    matched `payload_crc32`.
 *  - **Kotlin returns** the *payload* of the reply and nothing else. Native selects the
 *    reply opcode via `ackOpcodeFor` and rebuilds the header, so an implementation cannot
 *    accidentally answer a MANIFEST with a COMMIT_ACK.
 *  - **Returning null** means "close this connection". Native answers with a structured
 *    ERROR(CANCELLED) frame rather than a silent disconnect, so the peer learns why.
 *  - **Throwing** is treated the same as null, but is a bug: it means an unhandled exception
 *    escaped the handler. Implementations catch their own failures and answer with a typed
 *    error payload instead, because that carries a reason the peer can act on.
 */
interface SyncFrameHandler {

    /**
     * Handles one inbound frame.
     *
     * Called on a native thread pool thread, several at a time. Implementations must be
     * thread safe. Blocking is expected and allowed - the responder waits here while the
     * user decides whether to accept the sync - but the native side abandons the connection
     * after its handler deadline, so a handler must never wait indefinitely.
     *
     * @param peerAddress `ip:port` of the connection. Diagnostics only; the peer's identity
     *   comes from HELLO and, when pairing is on, from the AUTH proof.
     * @param sessionId session id from the frame header, echoed by native onto the reply.
     * @param seq sequence number from the frame header; raw 32-bit pattern, may be negative.
     * @return the reply payload, or null to close the connection.
     */
    fun handleFrame(
        peerAddress: String,
        opcode: SyncOpcode,
        sessionId: Long,
        seq: Int,
        payload: ByteArray
    ): ByteArray?
}
