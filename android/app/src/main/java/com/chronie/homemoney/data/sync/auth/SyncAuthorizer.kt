package com.chronie.homemoney.data.sync.auth

/**
 * Everything the responder needs in order to decide whether a peer may sync.
 *
 * Pulled out into an interface for two reasons. It keeps the frame handler free of Android
 * types - the production implementation shows a dialog and reads SharedPreferences, neither
 * of which belongs anywhere near protocol code - and it makes the authorisation rules
 * testable, since a fake implementation can answer instantly instead of waiting on a human.
 *
 * ### Two independent gates
 *
 * Authorisation is not one decision but two, and conflating them is what left v1 with no
 * real protection at all:
 *
 *  - **Pairing** proves the peer holds the shared code. It is cryptographic, automatic, and
 *    it is the only thing that stops a stranger on the same Wi-Fi from impersonating the
 *    user's other phone.
 *  - **Confirmation** is the human saying yes. It stops an unexpected sync, but it cannot
 *    tell one device from another, because a device name is self-declared.
 *
 * A peer must clear both, unless it has been remembered by [isTrusted], in which case the
 * prompt is skipped but the pairing proof is still required.
 */
interface SyncAuthorizer {

    /** How the user answered, or that they never did. */
    enum class Decision {
        ACCEPTED,
        REJECTED,

        /**
         * Nobody answered before the deadline. Distinguished from [REJECTED] because the
         * peer should be told to try again later rather than that it was refused - most
         * often the app was simply not in the foreground.
         */
        TIMED_OUT
    }

    /** Details shown to the user when asking. */
    data class Request(
        val deviceId: String,
        val deviceName: String,
        val peerAddress: String,
        val trustedBefore: Boolean
    )

    /**
     * The pairing code both devices share, or null when the user has not set one.
     *
     * Returning null disables the proof exchange entirely, which is the out-of-the-box
     * behaviour and matches what v1 did. Once a code exists it becomes mandatory: a peer
     * that cannot present a proof is refused rather than quietly downgraded, because a
     * security control that silently turns itself off is worse than none.
     */
    fun pairingCode(): String?

    /** True when [deviceId] has synced successfully before and may skip the prompt. */
    fun isTrusted(deviceId: String): Boolean

    /**
     * Asks the user, blocking the calling thread until they answer or [timeoutMs] elapses.
     *
     * Called from a native transport thread, so the implementation is responsible for
     * hopping to the main thread and back. [timeoutMs] is always well inside the native
     * handler deadline, so returning [Decision.TIMED_OUT] is guaranteed to reach the peer as
     * a proper error frame instead of the connection simply dying.
     */
    fun confirm(request: Request, timeoutMs: Long): Decision

    /** Records [deviceId] as trusted after a sync completes, so the next one is quieter. */
    fun remember(deviceId: String, deviceName: String)

    companion object {
        /**
         * Authorizer that refuses everything.
         *
         * Used as the default so that a responder wired up without an authorizer fails
         * closed. The alternative - defaulting to "accept" - would mean a missed injection
         * silently reopens the v1 hole where any peer could read the whole ledger.
         */
        val DENY_ALL: SyncAuthorizer = object : SyncAuthorizer {
            override fun pairingCode(): String? = null
            override fun isTrusted(deviceId: String): Boolean = false
            override fun confirm(request: Request, timeoutMs: Long): Decision = Decision.REJECTED
            override fun remember(deviceId: String, deviceName: String) = Unit
        }
    }
}
