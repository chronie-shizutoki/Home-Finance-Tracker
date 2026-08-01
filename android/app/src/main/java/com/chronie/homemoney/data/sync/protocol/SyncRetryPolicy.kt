package com.chronie.homemoney.data.sync.protocol

/**
 * Kotlin half of the retry and backoff policy.
 *
 * The native client already retries at the transport level; this mirror exists because the
 * Kotlin scheduler also retries - a whole session, not a single connection - and the two
 * must agree on the curve. If they disagree, one device gives up while the other is still
 * waiting to redial, and the user sees "sync failed" on one phone and a spinner on the
 * other.
 *
 * Both implementations are pinned to `app/src/main/cpp/protocol/retry_vectors.txt`:
 *  - C++ asserts the vectors at compile time via `retry_vectors_generated.h`,
 *  - Kotlin asserts them in `SyncRetryPolicyTest`, which parses that same file.
 *
 * Regenerate with `python tools/gen_frame_vectors.py` after changing anything here.
 *
 * Arithmetic uses [UInt] deliberately. The C++ side is `uint32_t` throughout, and doing the
 * shifts and the modulo on a signed [Int] is precisely how a mirror picks up an off-by-one
 * that only shows on large values.
 */
data class SyncRetryPolicy(
    /** Total attempts including the first one. 1 means "no retry". */
    val maxAttempts: UInt = 4u,
    /** Delay ceiling for the first retry, doubled on each subsequent retry. */
    val baseDelayMs: UInt = 250u,
    /** Hard cap so a long outage does not push the next attempt minutes away. */
    val maxDelayMs: UInt = 8000u
) {

    /**
     * Unjittered delay ceiling for a zero-based retry index.
     *
     * The shift is clamped before it is applied so an absurd attempt number yields
     * [maxDelayMs] instead of a shift overflow, which in C++ would be undefined behaviour
     * and in Kotlin would silently wrap modulo 32.
     */
    fun ceilingMs(retryIndex: UInt): UInt {
        if (baseDelayMs == 0u) return 0u
        val shift = if (retryIndex > MAX_BACKOFF_SHIFT) MAX_BACKOFF_SHIFT else retryIndex
        val scaled = baseDelayMs.toULong() shl shift.toInt()
        return if (scaled >= maxDelayMs.toULong()) maxDelayMs else scaled.toUInt()
    }

    /**
     * Equal-jitter delay: half the ceiling plus a random share of the other half.
     *
     * Keeping a guaranteed lower bound matters on a flapping link - a pure random delay
     * can return ~0 repeatedly and hammer a peer that has not recovered yet. Spreading the
     * upper half stops two phones on the same access point from reconnecting in lockstep
     * after the AP drops them together, which is the common case, not the rare one.
     *
     * @param randomValue any 32-bit value. The caller owns the randomness so this function
     *   stays pure and can be pinned to golden vectors.
     */
    fun jitteredDelayMs(retryIndex: UInt, randomValue: UInt): UInt {
        val ceiling = ceilingMs(retryIndex)
        if (ceiling == 0u) return 0u
        val half = ceiling / 2u
        val span = ceiling - half // covers the odd-ceiling case exactly
        return half + (randomValue % (span + 1u))
    }

    /** [jitteredDelayMs] as a [Long] suitable for `kotlinx.coroutines.delay`. */
    fun delayMillis(retryIndex: Int, randomValue: UInt): Long =
        jitteredDelayMs(retryIndex.coerceAtLeast(0).toUInt(), randomValue).toLong()

    /**
     * Whether another attempt should be made.
     *
     * Only transient failures are retried. Burning a four-attempt budget on a protocol
     * mismatch delays the real diagnosis by tens of seconds and tells the user nothing;
     * the classification lives in [SyncErrorCode.retryable] so there is one table, shared
     * with the native side.
     *
     * @param attemptsMade attempts already completed, including the one that just failed.
     */
    fun shouldRetry(error: SyncErrorCode, attemptsMade: UInt): Boolean {
        if (error == SyncErrorCode.OK) return false
        if (attemptsMade >= maxAttempts) return false
        return error.retryable
    }

    companion object {
        /** Largest shift applied when computing the ceiling; beyond this the cap dominates. */
        val MAX_BACKOFF_SHIFT: UInt = 16u

        /** Mirrors `homemoney::sync::RetryPolicy`'s defaults. */
        val DEFAULT = SyncRetryPolicy()

        /**
         * Small deterministic PRNG for jitter.
         *
         * `java.util.Random` is synchronised and `SecureRandom` is far more than jitter
         * needs; xorshift32 is three operations, is pure, and - most usefully - produces
         * the same chain as the native side so a single golden vector covers both.
         */
        fun xorshift32(state: UInt): UInt {
            var s = state
            s = s xor (s shl 13)
            s = s xor (s shr 17)
            s = s xor (s shl 5)
            return s
        }
    }
}

/**
 * Mutable jitter source.
 *
 * Zero is a fixed point of xorshift32, so a caller that seeds from a clock that happens to
 * read zero would get a constant delay forever. Guarding once here is safer than trusting
 * every call site to remember.
 */
class JitterSource(seed: UInt) {

    private var state: UInt = if (seed == 0u) GOLDEN_RATIO_SEED else seed

    fun next(): UInt {
        if (state == 0u) state = GOLDEN_RATIO_SEED
        state = SyncRetryPolicy.xorshift32(state)
        return state
    }

    companion object {
        /** 2^32 / phi, the conventional non-zero fallback seed. */
        val GOLDEN_RATIO_SEED: UInt = 0x9E3779B9u

        /** Seeds from the monotonic clock, which never legitimately reads zero twice. */
        fun fromClock(): JitterSource = JitterSource(System.nanoTime().toUInt())
    }
}
