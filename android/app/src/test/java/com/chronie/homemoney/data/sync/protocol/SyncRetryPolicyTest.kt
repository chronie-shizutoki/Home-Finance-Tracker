package com.chronie.homemoney.data.sync.protocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-language conformance for the backoff curve.
 *
 * Same arrangement as [ProtocolConformanceTest]: one shared golden file
 * (`app/src/main/cpp/protocol/retry_vectors.txt`) produced by a third, independent
 * implementation in `tools/gen_frame_vectors.py`, asserted here by Kotlin and at compile
 * time by C++ in `transport/transport_conformance.cpp`.
 *
 * Pinning the *curve* rather than just the code matters because the two ends retry at
 * different layers - native per connection, Kotlin per session. A divergence would not be
 * a crash, it would be one device abandoning a sync tens of seconds before the other stops
 * waiting, which is close to impossible to diagnose from a bug report.
 */
class SyncRetryPolicyTest {

    private data class BackoffVector(
        val name: String,
        val baseDelayMs: UInt,
        val maxDelayMs: UInt,
        val retryIndex: UInt,
        val randomValue: UInt,
        val expectedCeilingMs: UInt,
        val expectedDelayMs: UInt
    )

    private data class XorshiftVector(
        val seed: UInt,
        val after: List<UInt>
    )

    private val lines: List<List<String>> by lazy {
        locateVectorFile().readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split("|") }
    }

    private val backoffVectors: List<BackoffVector> by lazy {
        lines.filter { it.size == 7 }.map { parts ->
            BackoffVector(
                name = parts[0],
                baseDelayMs = parts[1].toUInt(),
                maxDelayMs = parts[2].toUInt(),
                retryIndex = parts[3].toUInt(),
                randomValue = parts[4].toUInt(),
                expectedCeilingMs = parts[5].toUInt(),
                expectedDelayMs = parts[6].toUInt()
            )
        }
    }

    private val xorshiftVectors: List<XorshiftVector> by lazy {
        lines.filter { it.size == 5 && it[0] == "xorshift" }.map { parts ->
            XorshiftVector(
                seed = parts[1].toUInt(),
                after = parts.drop(2).map(String::toUInt)
            )
        }
    }

    // ------------------------------------------------------------------- tests

    @Test
    fun `golden vector file is present and non trivial`() {
        assertTrue(
            "expected at least 10 backoff vectors, found ${backoffVectors.size}. " +
                    "Run: python tools/gen_frame_vectors.py",
            backoffVectors.size >= 10
        )
        assertTrue(
            "expected at least 5 xorshift chains, found ${xorshiftVectors.size}",
            xorshiftVectors.size >= 5
        )
    }

    @Test
    fun `every ceiling matches the golden value`() {
        for (v in backoffVectors) {
            val policy = SyncRetryPolicy(baseDelayMs = v.baseDelayMs, maxDelayMs = v.maxDelayMs)
            assertEquals(
                "vector '${v.name}' ceiling",
                v.expectedCeilingMs,
                policy.ceilingMs(v.retryIndex)
            )
        }
    }

    @Test
    fun `every jittered delay matches the golden value`() {
        for (v in backoffVectors) {
            val policy = SyncRetryPolicy(baseDelayMs = v.baseDelayMs, maxDelayMs = v.maxDelayMs)
            assertEquals(
                "vector '${v.name}' jittered delay",
                v.expectedDelayMs,
                policy.jitteredDelayMs(v.retryIndex, v.randomValue)
            )
        }
    }

    @Test
    fun `xorshift chain matches the native generator`() {
        for (v in xorshiftVectors) {
            var state = v.seed
            for ((step, expected) in v.after.withIndex()) {
                state = SyncRetryPolicy.xorshift32(state)
                assertEquals(
                    "seed ${v.seed} step ${step + 1}",
                    expected,
                    state
                )
            }
        }
    }

    // ------------------------------------------------- properties, not vectors

    @Test
    fun `jitter never leaves the half to full ceiling band`() {
        val policy = SyncRetryPolicy.DEFAULT
        for (retry in 0u..12u) {
            val ceiling = policy.ceilingMs(retry)
            // Sampling the extremes plus a spread is enough: the function is a modulo of a
            // contiguous range, so the endpoints are where an off-by-one would surface.
            for (random in listOf(0u, 1u, 12345u, UInt.MAX_VALUE / 2u, UInt.MAX_VALUE)) {
                val delay = policy.jitteredDelayMs(retry, random)
                assertTrue(
                    "retry=$retry random=$random delay=$delay below half of $ceiling",
                    delay >= ceiling / 2u
                )
                assertTrue(
                    "retry=$retry random=$random delay=$delay above ceiling $ceiling",
                    delay <= ceiling
                )
            }
        }
    }

    @Test
    fun `ceiling grows monotonically and then stays capped`() {
        val policy = SyncRetryPolicy.DEFAULT
        var previous = 0u
        for (retry in 0u..40u) {
            val ceiling = policy.ceilingMs(retry)
            assertTrue("ceiling went backwards at retry=$retry", ceiling >= previous)
            assertTrue("ceiling exceeded the cap at retry=$retry", ceiling <= policy.maxDelayMs)
            previous = ceiling
        }
        // A wildly out-of-range index must clamp rather than wrap the shift.
        assertEquals(policy.maxDelayMs, policy.ceilingMs(UInt.MAX_VALUE))
    }

    @Test
    fun `zero base delay disables waiting entirely`() {
        val policy = SyncRetryPolicy(baseDelayMs = 0u)
        for (retry in 0u..5u) {
            assertEquals(0u, policy.ceilingMs(retry))
            assertEquals(0u, policy.jitteredDelayMs(retry, 987654u))
        }
    }

    @Test
    fun `only transient errors consume the retry budget`() {
        val policy = SyncRetryPolicy(maxAttempts = 4u)

        // Deterministic failures must be reported immediately, not after four rounds of
        // backoff that cannot possibly change the outcome.
        for (fatal in listOf(
            SyncErrorCode.PROTOCOL_MISMATCH,
            SyncErrorCode.AUTH_REJECTED,
            SyncErrorCode.PAYLOAD_TOO_LARGE,
            SyncErrorCode.PARSE_ERROR,
            SyncErrorCode.BAD_MAGIC,
            SyncErrorCode.UNKNOWN_OPCODE,
            SyncErrorCode.CANCELLED
        )) {
            assertFalse("$fatal must not be retried", policy.shouldRetry(fatal, 1u))
        }

        for (transient in listOf(
            SyncErrorCode.NETWORK_UNREACHABLE,
            SyncErrorCode.CONNECT_TIMEOUT,
            SyncErrorCode.IO_TIMEOUT,
            SyncErrorCode.PEER_CLOSED,
            SyncErrorCode.CRC_MISMATCH,
            SyncErrorCode.BUSY
        )) {
            assertTrue("$transient must be retried", policy.shouldRetry(transient, 1u))
        }

        // Success never retries, and the budget is inclusive of the first attempt.
        assertFalse(policy.shouldRetry(SyncErrorCode.OK, 0u))
        assertTrue(policy.shouldRetry(SyncErrorCode.IO_TIMEOUT, 3u))
        assertFalse(policy.shouldRetry(SyncErrorCode.IO_TIMEOUT, 4u))
        assertFalse(policy.shouldRetry(SyncErrorCode.IO_TIMEOUT, 99u))
    }

    @Test
    fun `jitter source recovers from a zero seed`() {
        // Zero is a fixed point of xorshift32; without the guard every retry would wait an
        // identical amount and the whole point of jitter would be lost.
        val source = JitterSource(0u)
        val first = source.next()
        val second = source.next()
        assertTrue("zero seed produced a zero output", first != 0u)
        assertTrue("generator is stuck", first != second)
    }

    @Test
    fun `jitter source is deterministic for a given seed`() {
        val a = JitterSource(42u)
        val b = JitterSource(42u)
        repeat(16) {
            assertEquals(a.next(), b.next())
        }
    }

    // ----------------------------------------------------------------- loading

    private fun locateVectorFile(): File {
        val relative = "app/src/main/cpp/protocol/retry_vectors.txt"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, relative), File(dir, relative.removePrefix("app/")))) {
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError(
            "could not locate $relative from ${File(".").absolutePath}. " +
                    "Run: python tools/gen_frame_vectors.py"
        )
    }
}
