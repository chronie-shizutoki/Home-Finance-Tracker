package com.chronie.homemoney.data.sync.protocol

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the Kotlin CRC-32C to the same parameters as the native implementation.
 *
 * The published check value is the important one: it fixes the polynomial, both
 * reflections, the seed and the final xor in a single assertion. Everything else here
 * guards the incremental API, which the transport uses to hash a chunk while it streams.
 */
class Crc32cTest {

    @Test
    fun `matches the published CRC-32C check value`() {
        // The canonical vector for CRC-32C/Castagnoli. This single assertion pins the
        // polynomial, both reflections, the seed and the final xor at once.
        assertEquals(0xE3069283.toInt(), Crc32c.compute("123456789".toByteArray()))
    }

    @Test
    fun `empty input hashes to zero`() {
        assertEquals(0, Crc32c.compute(ByteArray(0)))
    }

    @Test
    fun `the checksum is order sensitive`() {
        assertNotEquals(Crc32c.compute(byteArrayOf(0)), Crc32c.compute(byteArrayOf(1)))
        assertNotEquals(Crc32c.compute(byteArrayOf(1, 2)), Crc32c.compute(byteArrayOf(2, 1)))
    }

    @Test
    fun `incremental hashing equals one-shot hashing`() {
        val data = ByteArray(10_000) { (it * 31).toByte() }
        val oneShot = Crc32c.compute(data)

        for (split in listOf(1, 7, 64, 4096, 9_999)) {
            var running = Crc32c.INITIAL
            var offset = 0
            while (offset < data.size) {
                val length = minOf(split, data.size - offset)
                running = Crc32c.update(running, data, offset, length)
                offset += length
            }
            assertEquals("split size $split", oneShot, Crc32c.finish(running))
        }
    }

    @Test
    fun `hashing a sub range ignores the surrounding bytes`() {
        val payload = ByteArray(256) { it.toByte() }
        val framed = ByteArray(32) { 0x5A } + payload + ByteArray(16) { 0x3C }

        assertEquals(Crc32c.compute(payload), Crc32c.compute(framed, 32, payload.size))
    }

    @Test
    fun `a single bit flip changes the checksum`() {
        val random = Random(20260801)
        val data = ByteArray(1024).also(random::nextBytes)
        val original = Crc32c.compute(data)

        repeat(200) {
            val index = random.nextInt(data.size)
            val bit = random.nextInt(8)
            val mutated = data.copyOf()
            mutated[index] = (mutated[index].toInt() xor (1 shl bit)).toByte()
            assertNotEquals(
                "flipping bit $bit of byte $index left the checksum unchanged",
                original,
                Crc32c.compute(mutated)
            )
        }
    }

    @Test
    fun `range checks reject an out of bounds request`() {
        val data = ByteArray(8)
        for (bad in listOf({ Crc32c.compute(data, -1, 4) }, { Crc32c.compute(data, 4, 8) })) {
            val error = runCatching(bad).exceptionOrNull()
            assertEquals(
                "expected IllegalArgumentException, got $error",
                IllegalArgumentException::class,
                error!!::class
            )
        }
    }
}
