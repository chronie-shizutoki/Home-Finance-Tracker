package com.chronie.homemoney.data.sync.auth

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mutual proof of a shared pairing code, without the code ever touching the wire.
 *
 * ### What this replaces
 *
 * v1 had no authentication at all. Any process on the same Wi-Fi could open the sync port,
 * send a well-formed request and receive the user's entire ledger; the only barrier was a
 * dialog on the responder, and the initiator had no way whatsoever to tell whether it was
 * talking to the user's other phone or to a stranger's laptop advertising the same device
 * name. Both directions are covered here.
 *
 * ### The exchange
 *
 * ```
 *   A -> B  HELLO      (capabilities include PAIRING)
 *   B -> A  HELLO_ACK  server_nonce
 *   A -> B  AUTH       client_nonce, clientProof(code, cn, sn, sessionId)
 *   B -> A  AUTH_ACK   server_nonce, serverProof(code, cn, sn, sessionId)
 * ```
 *
 * ### Three details that are load bearing
 *
 *  1. **Direction tags.** The two proofs are computed under different tags. Without that
 *     they would be byte-identical, and an attacker could take A's proof and reflect it
 *     straight back as B's counter-proof, authenticating itself with no knowledge of the
 *     code at all.
 *  2. **Length-prefixed nonces.** Concatenating raw nonces makes `("AB", "C")` and
 *     `("A", "BC")` hash identically, which lets an attacker who controls one nonce shift
 *     the boundary and reuse a captured proof.
 *  3. **Session binding.** The session id is part of the MAC, so a proof captured from one
 *     session cannot be replayed into another.
 *
 * This is a pairing check, not a secure channel: the payloads themselves are still plaintext
 * on the LAN. It stops impersonation and casual snooping of the sync port, which is the
 * threat that actually applies to a home network. Encrypting the body is deliberately out of
 * scope here and tracked separately.
 *
 * The object is pure and stateless, so every property below is testable without a device.
 */
object SyncPairing {

    /** Nonce length in bytes. 128 bits of randomness per attempt. */
    const val NONCE_SIZE = 16

    /** HMAC-SHA256 output length. */
    const val PROOF_SIZE = 32

    private const val HMAC_ALGORITHM = "HmacSHA256"

    private val CLIENT_TAG = "HFS1 client proof".toByteArray(Charsets.UTF_8)
    private val SERVER_TAG = "HFS1 server proof".toByteArray(Charsets.UTF_8)

    private val SECURE_RANDOM by lazy { SecureRandom() }

    /** Fresh nonce for one pairing attempt. Never reuse one across sessions. */
    fun newNonce(random: SecureRandom = SECURE_RANDOM): ByteArray =
        ByteArray(NONCE_SIZE).also(random::nextBytes)

    /**
     * Normalizes a human-typed pairing code.
     *
     * Users read the code off another screen and retype it, so spaces, dashes and case
     * differences are entirely expected and must not cause a mismatch. Both devices
     * normalize before hashing, so the rule has to be identical on both - it lives here and
     * nowhere else.
     */
    fun normalizeCode(raw: String): String =
        raw.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    /** True when [raw] can actually be used as a key; an empty code is not a secret. */
    fun isUsableCode(raw: String?): Boolean = !raw.isNullOrBlank() && normalizeCode(raw).isNotEmpty()

    /** Proof the initiator sends in `AuthPayload.proof`. */
    fun clientProof(
        pairingCode: String,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        sessionId: Long
    ): ByteArray = hmac(pairingCode, CLIENT_TAG, clientNonce, serverNonce, sessionId)

    /** Counter-proof the responder returns in `AuthAckPayload.proof`. */
    fun serverProof(
        pairingCode: String,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        sessionId: Long
    ): ByteArray = hmac(pairingCode, SERVER_TAG, clientNonce, serverNonce, sessionId)

    /** Responder side: is the initiator's proof valid? */
    fun verifyClientProof(
        pairingCode: String,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        sessionId: Long,
        presented: ByteArray
    ): Boolean = constantTimeEquals(
        clientProof(pairingCode, clientNonce, serverNonce, sessionId),
        presented
    )

    /** Initiator side: is the responder's counter-proof valid? */
    fun verifyServerProof(
        pairingCode: String,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        sessionId: Long,
        presented: ByteArray
    ): Boolean = constantTimeEquals(
        serverProof(pairingCode, clientNonce, serverNonce, sessionId),
        presented
    )

    /**
     * Length-independent comparison.
     *
     * `ByteArray.contentEquals` returns on the first differing byte, which leaks how much of
     * a guessed proof was correct. Over a LAN the timing signal is noisy, but it is free to
     * remove, and the cost here is a handful of XORs.
     */
    fun constantTimeEquals(expected: ByteArray, actual: ByteArray): Boolean {
        if (expected.size != actual.size) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].toInt() xor actual[i].toInt())
        }
        return diff == 0
    }

    private fun hmac(
        pairingCode: String,
        tag: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        sessionId: Long
    ): ByteArray {
        val key = normalizeCode(pairingCode)
        // Mac.init rejects an empty key with an IllegalArgumentException from deep inside
        // the JCE; failing here instead gives a message a maintainer can act on.
        require(key.isNotEmpty()) { "pairing code is empty after normalisation" }
        require(clientNonce.isNotEmpty()) { "client nonce is empty" }
        require(serverNonce.isNotEmpty()) { "server nonce is empty" }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        mac.update(tag)
        mac.update(lengthPrefix(clientNonce.size))
        mac.update(clientNonce)
        mac.update(lengthPrefix(serverNonce.size))
        mac.update(serverNonce)
        mac.update(bigEndian(sessionId))
        return mac.doFinal()
    }

    private fun lengthPrefix(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun bigEndian(value: Long): ByteArray = ByteArray(8) { i ->
        (value ushr (56 - 8 * i)).toByte()
    }
}
