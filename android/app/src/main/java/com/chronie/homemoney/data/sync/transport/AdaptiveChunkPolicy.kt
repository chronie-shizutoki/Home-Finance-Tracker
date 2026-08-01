package com.chronie.homemoney.data.sync.transport

/**
 * Decides how big the next chunk should be and how many may be in flight.
 *
 * A fixed chunk size cannot be right for both links this app runs on. 64 KiB over a clean
 * 5 GHz link wastes most of the round trip waiting; the same 64 KiB over a congested 2.4 GHz
 * link at the edge of range takes long enough to hit the socket timeout, and every timeout
 * costs the whole chunk. So the size follows the link.
 *
 * Two rules, and the asymmetry between them is the point:
 *
 *  - **Success adjusts gradually.** The target is a chunk that takes about
 *    [targetChunkDurationMs] to acknowledge, computed from observed throughput. Changes are
 *    damped to at most a factor of two per step, because a single outlier sample — one GC
 *    pause, one retransmit — would otherwise swing the size across its whole range and the
 *    link would spend its time oscillating instead of transferring.
 *  - **Timeout reacts immediately.** Halve the chunk and halve the window, at once. A timeout
 *    is not a noisy measurement, it is evidence the current size does not fit. Backing off
 *    slowly here means several more full-chunk losses before reaching a size that works.
 *
 * Not thread-safe: one instance belongs to one session, driven by that session's transfer
 * loop.
 */
class AdaptiveChunkPolicy(
    val minChunkBytes: Int = MIN_CHUNK_BYTES,
    val maxChunkBytes: Int = MAX_CHUNK_BYTES,
    initialChunkBytes: Int = DEFAULT_CHUNK_BYTES,
    val minWindow: Int = MIN_WINDOW,
    val maxWindow: Int = MAX_WINDOW,
    initialWindow: Int = DEFAULT_WINDOW,
    /** How many recent acknowledgements feed a decision. */
    private val sampleSize: Int = DEFAULT_SAMPLE_SIZE,
    /** The acknowledgement latency the sizing aims for. */
    val targetChunkDurationMs: Long = DEFAULT_TARGET_MS
) {

    init {
        require(minChunkBytes > 0) { "minChunkBytes must be positive" }
        require(maxChunkBytes >= minChunkBytes) { "maxChunkBytes < minChunkBytes" }
        require(minWindow >= 1) { "minWindow must be at least 1" }
        require(maxWindow >= minWindow) { "maxWindow < minWindow" }
        require(sampleSize >= 1) { "sampleSize must be at least 1" }
        require(targetChunkDurationMs > 0) { "targetChunkDurationMs must be positive" }
    }

    private data class Sample(val bytes: Int, val roundTripMs: Long)

    private val samples = ArrayDeque<Sample>(sampleSize)

    var chunkBytes: Int = initialChunkBytes.coerceIn(minChunkBytes, maxChunkBytes)
        private set

    var windowSize: Int = initialWindow.coerceIn(minWindow, maxWindow)
        private set

    /** Timeouts since the last success. Exposed so the caller can give up rather than loop. */
    var consecutiveTimeouts: Int = 0
        private set

    /** True once shrinking has bottomed out — the link cannot carry even the smallest chunk. */
    val isAtFloor: Boolean
        get() = chunkBytes == minChunkBytes && windowSize == minWindow

    /**
     * A chunk was acknowledged. [roundTripMs] is send-to-ack for that chunk.
     *
     * A round trip of zero is normal on a fast LAN where the clock granularity is coarser
     * than the transfer, so it is floored at 1ms rather than rejected — rejecting it would
     * mean the fastest links never adapt at all.
     */
    fun onChunkAcknowledged(bytes: Int, roundTripMs: Long) {
        if (bytes <= 0) return
        consecutiveTimeouts = 0

        samples.addLast(Sample(bytes, roundTripMs.coerceAtLeast(1)))
        while (samples.size > sampleSize) samples.removeFirst()

        // Decide only on a full window. Reacting to one sample is how oscillation starts.
        if (samples.size < sampleSize) return

        chunkBytes = damp(chunkBytes, idealChunkBytes()).coerceIn(minChunkBytes, maxChunkBytes)

        // Grow the window one step at a time, and only while the link is comfortably inside
        // the latency target. Growing on any success would refill the pipe right after the
        // congestion that emptied it.
        if (averageRoundTripMs() <= targetChunkDurationMs) {
            windowSize = (windowSize + 1).coerceAtMost(maxWindow)
        }
    }

    /**
     * A chunk was not acknowledged in time.
     *
     * Samples are discarded: they describe a link that no longer exists. Keeping them would
     * let pre-congestion throughput argue for a large chunk on the very next decision.
     */
    fun onTimeout() {
        consecutiveTimeouts++
        samples.clear()
        chunkBytes = (chunkBytes / 2).coerceAtLeast(minChunkBytes)
        windowSize = (windowSize / 2).coerceAtLeast(minWindow)
    }

    /**
     * The peer asked for a smaller chunk, or a payload was refused as too large. Honoured
     * directly rather than treated as congestion — the peer knows its own limit.
     */
    fun onPeerLimit(peerMaxChunkBytes: Int) {
        if (peerMaxChunkBytes <= 0) return
        chunkBytes = chunkBytes.coerceAtMost(peerMaxChunkBytes).coerceAtLeast(minChunkBytes)
    }

    /** Back to the starting point, for a resumed or restarted session. */
    fun reset() {
        samples.clear()
        consecutiveTimeouts = 0
        chunkBytes = DEFAULT_CHUNK_BYTES.coerceIn(minChunkBytes, maxChunkBytes)
        windowSize = DEFAULT_WINDOW.coerceIn(minWindow, maxWindow)
    }

    /** How many chunks a payload of [totalBytes] needs at the current size. */
    fun chunkCountFor(totalBytes: Long): Int {
        if (totalBytes <= 0) return 0
        return ((totalBytes + chunkBytes - 1) / chunkBytes)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun averageRoundTripMs(): Long =
        if (samples.isEmpty()) 0 else samples.sumOf { it.roundTripMs } / samples.size

    /** Observed throughput in bytes per millisecond; 0 when there is nothing to go on. */
    fun throughputBytesPerMs(): Double {
        if (samples.isEmpty()) return 0.0
        val bytes = samples.sumOf { it.bytes.toLong() }
        val millis = samples.sumOf { it.roundTripMs }
        return if (millis <= 0) 0.0 else bytes.toDouble() / millis.toDouble()
    }

    /** Size that would take [targetChunkDurationMs] at the observed throughput. */
    private fun idealChunkBytes(): Int {
        val throughput = throughputBytesPerMs()
        if (throughput <= 0.0) return chunkBytes
        val ideal = throughput * targetChunkDurationMs
        return ideal.coerceIn(minChunkBytes.toDouble(), maxChunkBytes.toDouble()).toInt()
    }

    /**
     * Move [current] towards [target] by at most a factor of two, then round down to a 4 KiB
     * boundary so a handful of near-identical measurements do not each produce a new size.
     */
    private fun damp(current: Int, target: Int): Int {
        val bounded = when {
            target > current -> target.coerceAtMost(saturatingDouble(current))
            target < current -> target.coerceAtLeast(current / 2)
            else -> current
        }
        val aligned = (bounded / ALIGNMENT_BYTES) * ALIGNMENT_BYTES
        return if (aligned < minChunkBytes) minChunkBytes else aligned
    }

    private fun saturatingDouble(value: Int): Int =
        if (value > Int.MAX_VALUE / 2) Int.MAX_VALUE else value * 2

    companion object {
        const val MIN_CHUNK_BYTES = 16 * 1024
        const val MAX_CHUNK_BYTES = 256 * 1024
        const val DEFAULT_CHUNK_BYTES = 64 * 1024
        const val MIN_WINDOW = 1
        const val MAX_WINDOW = 8
        const val DEFAULT_WINDOW = 4
        const val DEFAULT_SAMPLE_SIZE = 3
        const val DEFAULT_TARGET_MS = 800L
        private const val ALIGNMENT_BYTES = 4 * 1024
    }
}
