package com.chronie.homemoney.data.sync.discovery

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * The v2 discovery datagram.
 *
 * v1 broadcast `"DISCOVERY|<id>|<name>|<ip>|<ts>"` as plain text. Three things were wrong
 * with that, and all three are the kind that only hurt in the field:
 *
 *  1. **No magic, no version.** Any UDP noise that happened to contain pipes parsed as a
 *     device. Port 12345 is a popular scratch port; a single stray packet put a phantom
 *     entry in the user's device list.
 *  2. **No port.** The receiver had to assume the peer listens on the same hardcoded port
 *     it does. Two devices with different builds could see each other and then fail to
 *     connect, which surfaces to the user as "found it, but sync doesn't work".
 *  3. **Request and response were byte-identical**, told apart only by which socket they
 *     arrived on. A device that received its own broadcast (which happens the moment there
 *     is more than one NIC) answered itself, and the answer looked exactly like a fresh
 *     query to everyone else.
 *
 * So v2 is a binary frame with a magic, an explicit type, a real port, and a nonce.
 *
 * ## Forward compatibility
 *
 * A parser accepts any `version >= MIN_COMPATIBLE_VERSION` and **ignores trailing bytes**.
 * That is the extension point: a future v3 may append fields after the three strings and
 * this parser will still discover it. The contract a future version must honour is narrow
 * but absolute:
 *
 *  - the 24-byte fixed header keeps its layout and meaning;
 *  - the three length-prefixed strings keep their order;
 *  - new data goes strictly after them.
 *
 * Break any of those and old builds stop seeing new ones, which is exactly the silent
 * split-brain this format exists to prevent.
 */
data class DiscoveryPacket(
    val type: DiscoveryType,
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    /** TCP port the sender's sync server is listening on. Removes the hardcoded-port guess. */
    val syncPort: Int,
    val capabilities: Int = DiscoveryCapability.DEFAULT,
    /**
     * Correlates an [DiscoveryType.ANNOUNCE] with the [DiscoveryType.QUERY] that caused it.
     * A searcher ignores announcements carrying a nonce it did not issue, so a late reply
     * from a previous search round cannot repopulate a list the user just cleared.
     */
    val nonce: Long = 0L,
    val version: Int = DiscoveryWire.VERSION,
    val minSupportedVersion: Int = DiscoveryWire.MIN_COMPATIBLE_VERSION
)

enum class DiscoveryType(val value: Int) {
    /** "Who is out there?" — broadcast. */
    QUERY(1),

    /** "I am here." — unicast reply, or an unsolicited presence broadcast. */
    ANNOUNCE(2);

    companion object {
        fun fromValue(value: Int): DiscoveryType? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Capability bits. Advertised so a peer knows what to expect *before* connecting, instead
 * of finding out by getting a frame it cannot parse.
 *
 * Bits are append-only. Never reuse a retired bit: an old build would read the new meaning
 * with the old assumption.
 */
object DiscoveryCapability {
    /** Speaks the v2 32-byte frame protocol (not just the v1 length-prefixed blob). */
    const val FRAME_V2 = 1 shl 0

    /** Requires/offers the HMAC pairing proof. */
    const val PAIRING_AUTH = 1 shl 1

    /** Accepts compressed payloads. */
    const val COMPRESSION = 1 shl 2

    /** Can resume an interrupted transfer from a checkpoint. */
    const val RESUME = 1 shl 3

    const val DEFAULT = FRAME_V2 or PAIRING_AUTH or RESUME

    fun has(capabilities: Int, bit: Int): Boolean = (capabilities and bit) == bit
}

/** Outcome of parsing a datagram. Rejections are typed so they can be counted, not just logged. */
sealed interface DiscoveryParse {
    data class Ok(val packet: DiscoveryPacket) : DiscoveryParse
    data class Rejected(val reason: Reason, val detail: String = "") : DiscoveryParse

    enum class Reason {
        TOO_SHORT,
        TOO_LONG,
        BAD_MAGIC,
        UNSUPPORTED_VERSION,
        UNKNOWN_TYPE,
        TRUNCATED_FIELD,
        FIELD_TOO_LONG,
        EMPTY_DEVICE_ID,
        BAD_PORT
    }
}

object DiscoveryWire {

    /** "HFSD" — Home Finance Sync Discovery. Distinct from the TCP frame magic "HFS1". */
    const val MAGIC = 0x48465344

    const val VERSION = 2

    /** Anything below this is v1 plain text and must go through [encodeLegacy]/[parseLegacy]. */
    const val MIN_COMPATIBLE_VERSION = 2

    /** magic(4) + version(1) + type(1) + flags(2) + port(2) + minVersion(2) + caps(4) + nonce(8) */
    const val HEADER_SIZE = 24

    /**
     * Hard ceiling on a datagram. Well under the 1500-byte Ethernet MTU so discovery never
     * fragments — a fragmented UDP datagram is all-or-nothing, and losing one fragment on a
     * congested Wi-Fi link would silently drop the whole announcement.
     */
    const val MAX_PACKET_SIZE = 512

    const val MAX_DEVICE_ID_BYTES = 64
    const val MAX_DEVICE_NAME_BYTES = 96
    const val MAX_DEVICE_TYPE_BYTES = 16

    const val LEGACY_PREFIX = "DISCOVERY"

    fun encode(packet: DiscoveryPacket): ByteArray {
        val id = packet.deviceId.toByteArray(StandardCharsets.UTF_8)
        val name = packet.deviceName.toByteArray(StandardCharsets.UTF_8)
        val type = packet.deviceType.toByteArray(StandardCharsets.UTF_8)

        require(id.isNotEmpty()) { "deviceId must not be empty" }
        require(id.size <= MAX_DEVICE_ID_BYTES) { "deviceId exceeds $MAX_DEVICE_ID_BYTES bytes" }
        require(name.size <= MAX_DEVICE_NAME_BYTES) { "deviceName exceeds $MAX_DEVICE_NAME_BYTES bytes" }
        require(type.size <= MAX_DEVICE_TYPE_BYTES) { "deviceType exceeds $MAX_DEVICE_TYPE_BYTES bytes" }
        require(packet.syncPort in 1..65535) { "syncPort out of range: ${packet.syncPort}" }

        val size = HEADER_SIZE + 2 + id.size + 2 + name.size + 2 + type.size
        require(size <= MAX_PACKET_SIZE) { "discovery packet would be $size bytes, max $MAX_PACKET_SIZE" }

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.put(packet.version.toByte())
        buffer.put(packet.type.value.toByte())
        buffer.putShort(0)                                  // flags, reserved
        buffer.putShort(packet.syncPort.toShort())
        buffer.putShort(packet.minSupportedVersion.toShort())
        buffer.putInt(packet.capabilities)
        buffer.putLong(packet.nonce)
        putLengthPrefixed(buffer, id)
        putLengthPrefixed(buffer, name)
        putLengthPrefixed(buffer, type)
        return buffer.array()
    }

    fun parse(data: ByteArray, length: Int = data.size): DiscoveryParse {
        if (length < HEADER_SIZE + 6) {
            return DiscoveryParse.Rejected(DiscoveryParse.Reason.TOO_SHORT, "$length bytes")
        }
        if (length > MAX_PACKET_SIZE) {
            return DiscoveryParse.Rejected(DiscoveryParse.Reason.TOO_LONG, "$length bytes")
        }

        val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.int
        if (magic != MAGIC) {
            return DiscoveryParse.Rejected(
                DiscoveryParse.Reason.BAD_MAGIC,
                "0x%08X".format(magic)
            )
        }

        val version = buffer.get().toInt() and 0xFF
        if (version < MIN_COMPATIBLE_VERSION) {
            return DiscoveryParse.Rejected(DiscoveryParse.Reason.UNSUPPORTED_VERSION, "v$version")
        }

        val rawType = buffer.get().toInt() and 0xFF
        val type = DiscoveryType.fromValue(rawType)
            ?: return DiscoveryParse.Rejected(DiscoveryParse.Reason.UNKNOWN_TYPE, "type=$rawType")

        buffer.short                                        // flags, reserved
        val syncPort = buffer.short.toInt() and 0xFFFF
        if (syncPort == 0) {
            return DiscoveryParse.Rejected(DiscoveryParse.Reason.BAD_PORT, "0")
        }
        val minSupported = buffer.short.toInt() and 0xFFFF
        val capabilities = buffer.int
        val nonce = buffer.long

        val id: ByteArray
        val name: ByteArray
        val deviceType: ByteArray
        try {
            id = readLengthPrefixed(buffer, MAX_DEVICE_ID_BYTES)
                ?: return DiscoveryParse.Rejected(DiscoveryParse.Reason.FIELD_TOO_LONG, "deviceId")
            name = readLengthPrefixed(buffer, MAX_DEVICE_NAME_BYTES)
                ?: return DiscoveryParse.Rejected(DiscoveryParse.Reason.FIELD_TOO_LONG, "deviceName")
            deviceType = readLengthPrefixed(buffer, MAX_DEVICE_TYPE_BYTES)
                ?: return DiscoveryParse.Rejected(DiscoveryParse.Reason.FIELD_TOO_LONG, "deviceType")
        } catch (_: BufferUnderflowException) {
            return DiscoveryParse.Rejected(DiscoveryParse.Reason.TRUNCATED_FIELD)
        }

        if (id.isEmpty()) {
            return DiscoveryParse.Rejected(DiscoveryParse.Reason.EMPTY_DEVICE_ID)
        }

        // Trailing bytes are deliberately not an error: that is how v3 adds fields without
        // becoming invisible to this build.
        return DiscoveryParse.Ok(
            DiscoveryPacket(
                type = type,
                deviceId = String(id, StandardCharsets.UTF_8),
                deviceName = String(name, StandardCharsets.UTF_8),
                deviceType = String(deviceType, StandardCharsets.UTF_8),
                syncPort = syncPort,
                capabilities = capabilities,
                nonce = nonce,
                version = version,
                minSupportedVersion = minSupported
            )
        )
    }

    /**
     * The v1 plain-text form, kept so this build stays discoverable by installs that predate
     * v2. Per the migration plan both forms go out on the wire for two releases; this is the
     * half that is scheduled for deletion, not the half worth improving.
     */
    fun encodeLegacy(deviceId: String, deviceName: String, ip: String, timestampMs: Long): ByteArray =
        "$LEGACY_PREFIX|$deviceId|$deviceName|$ip|$timestampMs".toByteArray(StandardCharsets.UTF_8)

    /**
     * Parses the v1 form. Returns null rather than a typed rejection: v1 has no way to tell
     * "malformed" from "not ours", so every failure is the same non-event.
     *
     * [defaultSyncPort] is the caller's own port — v1 carried none, and assuming the peer
     * matches us is the best guess available. That guess is the entire reason v2 has a port
     * field.
     */
    fun parseLegacy(data: ByteArray, length: Int = data.size, defaultSyncPort: Int): DiscoveryPacket? {
        if (length <= 0 || length > MAX_PACKET_SIZE) return null
        val text = String(data, 0, length, StandardCharsets.UTF_8)
        val parts = text.split("|")
        if (parts.size < 4 || parts[0] != LEGACY_PREFIX) return null
        val id = parts[1]
        if (id.isEmpty() || id.toByteArray(StandardCharsets.UTF_8).size > MAX_DEVICE_ID_BYTES) return null
        return DiscoveryPacket(
            type = DiscoveryType.ANNOUNCE,
            deviceId = id,
            deviceName = parts[2],
            deviceType = "ANDROID",
            syncPort = defaultSyncPort,
            capabilities = 0,                               // v1 advertises nothing
            nonce = 0L,
            version = 1,
            minSupportedVersion = 1
        )
    }

    private fun putLengthPrefixed(buffer: ByteBuffer, value: ByteArray) {
        buffer.putShort(value.size.toShort())
        buffer.put(value)
    }

    /** Returns null when the declared length exceeds [max]; throws on truncation. */
    private fun readLengthPrefixed(buffer: ByteBuffer, max: Int): ByteArray? {
        val declared = buffer.short.toInt() and 0xFFFF
        if (declared > max) return null
        if (declared > buffer.remaining()) throw BufferUnderflowException()
        val out = ByteArray(declared)
        buffer.get(out)
        return out
    }
}
