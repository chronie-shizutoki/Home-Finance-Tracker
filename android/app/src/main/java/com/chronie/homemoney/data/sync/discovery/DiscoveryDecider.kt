package com.chronie.homemoney.data.sync.discovery

/** Who we are on the wire. Read per-send so a rename takes effect without a restart. */
data class DiscoveryIdentity(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    /** TCP port our sync server listens on. Goes into every packet we emit. */
    val syncPort: Int,
    val capabilities: Int = DiscoveryCapability.DEFAULT
)

/** What to do about an inbound datagram. */
sealed interface DiscoveryAction {

    data class Ignore(val reason: IgnoreReason, val detail: String = "") : DiscoveryAction

    /**
     * Answer a query, unicast, at [to]:[port] — the querier's *ephemeral* port, not the
     * discovery port. That single detail is what stops the v1 amplification loop: a reply
     * never lands on the port everyone else is listening to, so it cannot be mistaken for a
     * fresh query and re-answered.
     *
     * [querier] is the device that asked. A query carries the same identity fields as an
     * announcement, so both sides learn about each other from one round trip instead of two.
     */
    data class Reply(
        val to: String,
        val port: Int,
        val nonce: Long,
        val querier: DiscoveredDevice
    ) : DiscoveryAction

    data class Record(val device: DiscoveredDevice) : DiscoveryAction
}

/**
 * Why a datagram was dropped. Enumerated rather than logged as free text so the counts mean
 * something: a spike in [SELF_ADDRESS] is a NIC-selection bug, a spike in [MALFORMED] is
 * someone else's traffic on our port, and they need different responses from us.
 */
enum class IgnoreReason {
    /** Empty or negative length. */
    EMPTY,

    /** Source address is one of ours — our own broadcast came back. */
    SELF_ADDRESS,

    /** Payload claims our device id. Catches the case where the address check missed. */
    SELF_DEVICE_ID,

    /** Neither a v2 frame nor a v1 line. */
    MALFORMED,

    /** A v1 line arrived while legacy acceptance is off. */
    LEGACY_DISABLED,

    /** An announcement correlated to a search round that has already finished. */
    STALE_NONCE,

    /** A query arrived on the search socket. Queries belong on the discovery port. */
    UNEXPECTED_QUERY
}

/**
 * Decides what a received datagram means. Pure: no sockets, no clock, no logging.
 *
 * All the discovery bugs worth fixing live in this decision — who is me, is this reply mine,
 * do I answer this — and none of them need a socket to reproduce. Keeping the decision here
 * means they can be tested directly instead of by standing up two devices on a real LAN.
 */
class DiscoveryDecider(
    private val self: DiscoveryIdentity,
    private val localAddresses: Set<String>
) {

    /**
     * @param expectedNonce the nonce of the search round in progress, or `null` when called
     *   from the responder (which is not searching and answers anything legitimate).
     */
    fun decide(
        data: ByteArray,
        length: Int,
        senderIp: String,
        senderPort: Int,
        expectedNonce: Long?
    ): DiscoveryAction {
        if (length <= 0) return DiscoveryAction.Ignore(IgnoreReason.EMPTY)

        // Cheapest check first, and the one that fires most often on a multi-NIC phone.
        if (senderIp.isNotEmpty() && senderIp in localAddresses) {
            return DiscoveryAction.Ignore(IgnoreReason.SELF_ADDRESS, senderIp)
        }

        val packet = when (val parsed = DiscoveryWire.parse(data, length)) {
            is DiscoveryParse.Ok -> parsed.packet
            is DiscoveryParse.Rejected -> {
                return DiscoveryAction.Ignore(IgnoreReason.MALFORMED, parsed.reason.name)
            }
        }

        // The address check is not enough on its own: a device behind a second NIC can see
        // its own broadcast arrive with a source address it does not recognise as local.
        if (packet.deviceId == self.deviceId) {
            return DiscoveryAction.Ignore(IgnoreReason.SELF_DEVICE_ID, packet.deviceId)
        }

        // Trust the socket's source address over the one in the payload. v1 put a
        // self-reported IP in the message and it was wrong exactly when it mattered — on the
        // multi-homed devices where the sender guessed its own address incorrectly.
        val device = DiscoveredDevice(
            deviceId = packet.deviceId,
            deviceName = packet.deviceName,
            deviceType = packet.deviceType,
            address = senderIp,
            syncPort = packet.syncPort,
            capabilities = packet.capabilities,
            protocolVersion = packet.version
        )

        return when (packet.type) {
            DiscoveryType.QUERY ->
                if (expectedNonce != null) {
                    DiscoveryAction.Ignore(IgnoreReason.UNEXPECTED_QUERY, packet.deviceId)
                } else {
                    DiscoveryAction.Reply(senderIp, senderPort, packet.nonce, querier = device)
                }

            DiscoveryType.ANNOUNCE ->
                // nonce 0 means unsolicited presence, which is always welcome. A non-zero
                // nonce that is not ours is a straggler from a previous round; letting it in
                // would repopulate a list the user just cleared.
                if (expectedNonce != null && packet.nonce != 0L && packet.nonce != expectedNonce) {
                    DiscoveryAction.Ignore(IgnoreReason.STALE_NONCE, packet.nonce.toString())
                } else {
                    DiscoveryAction.Record(device)
                }
        }
    }
}
