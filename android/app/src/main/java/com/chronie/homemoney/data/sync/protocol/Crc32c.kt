package com.chronie.homemoney.data.sync.protocol

/**
 * CRC-32C (Castagnoli), the integrity primitive shared with the native side.
 *
 * Mirrors `app/src/main/cpp/protocol/crc32c.h` exactly:
 *   polynomial  0x1EDC6F41, applied in its reflected form 0x82F63B78
 *   init        0xFFFFFFFF
 *   reflect     input and output
 *   final xor   0xFFFFFFFF
 *   check       crc32c("123456789") == 0xE3069283
 *
 * Values are carried as raw [Int] bit patterns rather than [Long]: the wire field is 32
 * bits, and keeping the JVM representation identical to the C++ `uint32_t` avoids a whole
 * class of sign-extension bugs when the checksum is written into a frame header.
 */
object Crc32c {

    /**
     * Reflected CRC-32C polynomial 0x82F63B78, written as its signed 32-bit equivalent
     * because Kotlin types the plain hex literal as a [Long].
     */
    private const val POLYNOMIAL: Int = -0x7D09C488

    /** Seed for an incremental computation. */
    const val INITIAL: Int = -1 // 0xFFFFFFFF

    private val TABLE = IntArray(256) { index ->
        var crc = index
        repeat(8) {
            crc = if (crc and 1 != 0) (crc ushr 1) xor POLYNOMIAL else crc ushr 1
        }
        crc
    }

    /**
     * Continue a running checksum over another block.
     *
     * @param crc the running value, [INITIAL] for the first block.
     */
    fun update(crc: Int, data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "range $offset..${offset + length} is outside a ${data.size} byte array"
        }
        var running = crc
        for (i in offset until offset + length) {
            running = TABLE[(running xor data[i].toInt()) and 0xFF] xor (running ushr 8)
        }
        return running
    }

    /** Apply the final xor to a running value. */
    fun finish(crc: Int): Int = crc.inv()

    /** One-shot checksum over a buffer. */
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int =
        finish(update(INITIAL, data, offset, length))
}
