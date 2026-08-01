#ifndef HOMEMONEY_SYNC_PROTOCOL_H
#define HOMEMONEY_SYNC_PROTOCOL_H

#include <array>
#include <cstddef>
#include <cstdint>

#include "crc32c.h"

/**
 * Single source of truth for the Home Finance LAN sync wire protocol (C++ side).
 *
 * The Kotlin mirror lives in
 *   app/src/main/java/com/chronie/homemoney/data/sync/protocol/SyncWireProtocol.kt
 * and MUST stay byte-for-byte identical. Both sides are pinned to the shared golden
 * vectors in protocol/frame_vectors.txt:
 *   - C++  validates them at compile time in protocol_conformance.cpp,
 *   - Kotlin validates them in ProtocolConformanceTest.
 * Changing a constant here without regenerating the vectors breaks the build, which is
 * exactly the guard rail we want against the two ends drifting apart again.
 *
 * Frame layout (32 byte header, big endian / network byte order, then the payload):
 *
 *   offset size field           notes
 *   0      4    magic           0x48465331 ("HFS1")
 *   4      1    version         current protocol version
 *   5      1    opcode          see Opcode
 *   6      2    flags           see FrameFlag bitmask
 *   8      8    session_id      unique per session
 *   16     4    seq             monotonically increasing within a session
 *   20     4    payload_len     payload byte count, capped at kMaxPayloadSize
 *   24     4    payload_crc32   CRC-32C of the payload
 *   28     4    header_crc32    CRC-32C of bytes 0..27
 *
 * A fixed size, self-checksumming header lets either side detect a corrupted stream and
 * resynchronise on a frame boundary instead of blindly trusting a bare length prefix -
 * which is what the previous implementation did, and why a single bad length could make
 * it allocate 10 MB or hang forever.
 */
namespace homemoney::sync {

// ---------------------------------------------------------------- wire constants

/// "HFS1" in ASCII. Chosen so that a v2 frame is distinguishable from a v1 frame at the
/// very first byte: v1 starts with a 4 byte big endian length, whose top byte is 0x00 for
/// any sane payload, whereas a v2 frame always starts with 0x48.
inline constexpr std::uint32_t kFrameMagic = 0x48465331u;

/// Protocol version implemented by this build.
inline constexpr std::uint8_t kProtocolVersion = 2;

/// Oldest version this build can still talk to over the v2 framing.
inline constexpr std::uint8_t kMinSupportedVersion = 2;

/// Size of the fixed frame header in bytes.
inline constexpr std::size_t kFrameHeaderSize = 32;

/// Hard upper bound for a single frame payload (1 MiB). A frame that claims more is
/// rejected before a single byte is allocated.
inline constexpr std::uint32_t kMaxPayloadSize = 1u << 20;

/// Default chunk payload size; adapts at runtime between kMinChunkSize and kMaxChunkSize.
inline constexpr std::uint32_t kDefaultChunkSize = 64u * 1024u;
inline constexpr std::uint32_t kMinChunkSize = 16u * 1024u;
inline constexpr std::uint32_t kMaxChunkSize = 256u * 1024u;

// ---------------------------------------------------------------------- opcodes

/// Frame types. Values are frozen; never renumber, only append.
enum class Opcode : std::uint8_t {
    kHello = 0x01,        ///< A->B: version, device info, capabilities.
    kHelloAck = 0x02,     ///< B->A: negotiation result, whether confirmation is needed.
    kAuth = 0x03,         ///< A->B: pairing proof.
    kAuthAck = 0x04,      ///< B->A: pairing verdict.
    kManifest = 0x10,     ///< Delta manifest: entity count, byte count, chunk count.
    kManifestAck = 0x11,  ///< Receiver reports the checkpoint it already holds.
    kChunk = 0x12,        ///< Data chunk.
    kChunkAck = 0x13,     ///< Cumulative chunk acknowledgement.
    /// A->B: send me your delta from this watermark, starting at this chunk index.
    ///
    /// The transport is strictly request/response - the responder never speaks first - so
    /// without an explicit pull the peer's own changes could never travel back on the same
    /// connection. v1 hid this by stuffing B's entire database into the single response;
    /// v2 makes the reverse direction an ordinary, resumable, individually retryable
    /// exchange instead.
    kPull = 0x14,
    kPullAck = 0x15,      ///< B->A: one chunk of B's delta, plus its manifest metadata.
    kCommit = 0x20,       ///< Request to apply the received data.
    kCommitAck = 0x21,    ///< Apply result plus conflict summary.
    kPing = 0x30,         ///< Keepalive probe.
    kPong = 0x31,         ///< Keepalive response.
    kError = 0x40,        ///< Structured error, carries a retryable hint.
    kBye = 0x41           ///< Graceful shutdown.
};

/// True when the byte maps to an opcode this build understands.
constexpr bool isKnownOpcode(std::uint8_t value) {
    switch (static_cast<Opcode>(value)) {
        case Opcode::kHello:
        case Opcode::kHelloAck:
        case Opcode::kAuth:
        case Opcode::kAuthAck:
        case Opcode::kManifest:
        case Opcode::kManifestAck:
        case Opcode::kChunk:
        case Opcode::kChunkAck:
        case Opcode::kPull:
        case Opcode::kPullAck:
        case Opcode::kCommit:
        case Opcode::kCommitAck:
        case Opcode::kPing:
        case Opcode::kPong:
        case Opcode::kError:
        case Opcode::kBye:
            return true;
        default:
            return false;
    }
}

// ------------------------------------------------------------------------ flags

/// Frame flag bitmask.
enum FrameFlag : std::uint16_t {
    kFlagNone = 0x0000,
    kFlagCompressed = 0x0001,  ///< Payload is compressed.
    kFlagLastChunk = 0x0002,   ///< Final chunk of a sequence.
    kFlagResumed = 0x0004,     ///< Session was resumed from a checkpoint.
    kFlagRequireAck = 0x0008   ///< Sender expects an explicit acknowledgement.
};

/// Every flag bit defined so far; anything outside this mask comes from a newer peer.
inline constexpr std::uint16_t kKnownFlags =
        kFlagCompressed | kFlagLastChunk | kFlagResumed | kFlagRequireAck;

// ------------------------------------------------------------------ error codes

/// Error codes shared by both ends. Values are frozen; never renumber, only append.
enum class SyncErrorCode : std::int32_t {
    kOk = 0,
    kProtocolMismatch = 1,
    kAuthRejected = 2,
    kAuthTimeout = 3,
    kNetworkUnreachable = 4,
    kConnectTimeout = 5,
    kIoTimeout = 6,
    kPeerClosed = 7,
    kCrcMismatch = 8,
    kPayloadTooLarge = 9,
    kParseError = 10,
    kApplyError = 11,
    kBusy = 12,
    kCancelled = 13,
    kInternal = 14,
    kBadMagic = 15,
    kUnknownOpcode = 16
};

/**
 * Whether retrying the operation could plausibly succeed.
 *
 * Only transient transport failures are retryable. Protocol, auth and parse failures are
 * deterministic: retrying them just burns the retry budget and delays the real error.
 */
constexpr bool isRetryable(SyncErrorCode code) {
    switch (code) {
        case SyncErrorCode::kNetworkUnreachable:
        case SyncErrorCode::kConnectTimeout:
        case SyncErrorCode::kIoTimeout:
        case SyncErrorCode::kPeerClosed:
        case SyncErrorCode::kCrcMismatch:
        case SyncErrorCode::kBusy:
            return true;
        default:
            return false;
    }
}

// ----------------------------------------------------------------- header codec

/// Decoded frame header. Plain data, no ownership, cheap to copy.
struct FrameHeader {
    std::uint8_t version = kProtocolVersion;
    Opcode opcode = Opcode::kPing;
    std::uint16_t flags = kFlagNone;
    std::uint64_t sessionId = 0;
    std::uint32_t seq = 0;
    std::uint32_t payloadLen = 0;
    std::uint32_t payloadCrc32 = 0;

    constexpr bool hasFlag(FrameFlag flag) const { return (flags & flag) != 0; }
};

/// Result of decoding a header: either a header or the reason it was rejected.
struct FrameHeaderResult {
    SyncErrorCode error = SyncErrorCode::kOk;
    FrameHeader header{};

    constexpr bool ok() const { return error == SyncErrorCode::kOk; }
};

using FrameHeaderBytes = std::array<std::uint8_t, kFrameHeaderSize>;

namespace detail {

constexpr void putU16(FrameHeaderBytes& out, std::size_t offset, std::uint16_t value) {
    out[offset] = static_cast<std::uint8_t>((value >> 8) & 0xFFu);
    out[offset + 1] = static_cast<std::uint8_t>(value & 0xFFu);
}

constexpr void putU32(FrameHeaderBytes& out, std::size_t offset, std::uint32_t value) {
    out[offset] = static_cast<std::uint8_t>((value >> 24) & 0xFFu);
    out[offset + 1] = static_cast<std::uint8_t>((value >> 16) & 0xFFu);
    out[offset + 2] = static_cast<std::uint8_t>((value >> 8) & 0xFFu);
    out[offset + 3] = static_cast<std::uint8_t>(value & 0xFFu);
}

constexpr void putU64(FrameHeaderBytes& out, std::size_t offset, std::uint64_t value) {
    for (std::size_t i = 0; i < 8; ++i) {
        out[offset + i] = static_cast<std::uint8_t>((value >> (56 - 8 * i)) & 0xFFu);
    }
}

constexpr std::uint16_t readU16(const std::uint8_t* in, std::size_t offset) {
    return static_cast<std::uint16_t>((static_cast<std::uint16_t>(in[offset]) << 8) |
                                      static_cast<std::uint16_t>(in[offset + 1]));
}

constexpr std::uint32_t readU32(const std::uint8_t* in, std::size_t offset) {
    return (static_cast<std::uint32_t>(in[offset]) << 24) |
           (static_cast<std::uint32_t>(in[offset + 1]) << 16) |
           (static_cast<std::uint32_t>(in[offset + 2]) << 8) |
           static_cast<std::uint32_t>(in[offset + 3]);
}

constexpr std::uint64_t readU64(const std::uint8_t* in, std::size_t offset) {
    std::uint64_t value = 0;
    for (std::size_t i = 0; i < 8; ++i) {
        value = (value << 8) | static_cast<std::uint64_t>(in[offset + i]);
    }
    return value;
}

}  // namespace detail

/**
 * Serialise a header, filling in both checksums.
 *
 * The caller supplies the payload CRC in @p header because the payload is usually
 * streamed and hashed incrementally rather than held in one buffer.
 */
constexpr FrameHeaderBytes encodeFrameHeader(const FrameHeader& header) {
    FrameHeaderBytes out{};
    detail::putU32(out, 0, kFrameMagic);
    out[4] = header.version;
    out[5] = static_cast<std::uint8_t>(header.opcode);
    detail::putU16(out, 6, header.flags);
    detail::putU64(out, 8, header.sessionId);
    detail::putU32(out, 16, header.seq);
    detail::putU32(out, 20, header.payloadLen);
    detail::putU32(out, 24, header.payloadCrc32);
    detail::putU32(out, 28, crc32c(out.data(), kFrameHeaderSize - 4));
    return out;
}

/**
 * Parse and validate a header.
 *
 * Validation order matters: magic first (cheapest and catches a v1 peer), then the header
 * checksum (so every field below is known good), then the semantic checks. A caller may
 * therefore trust payloadLen only after this function returns ok().
 *
 * @param in pointer to at least kFrameHeaderSize readable bytes.
 */
constexpr FrameHeaderResult decodeFrameHeader(const std::uint8_t* in) {
    FrameHeaderResult result{};

    if (detail::readU32(in, 0) != kFrameMagic) {
        result.error = SyncErrorCode::kBadMagic;
        return result;
    }

    const std::uint32_t expectedHeaderCrc = detail::readU32(in, 28);
    if (crc32c(in, kFrameHeaderSize - 4) != expectedHeaderCrc) {
        result.error = SyncErrorCode::kCrcMismatch;
        return result;
    }

    const std::uint8_t version = in[4];
    if (version < kMinSupportedVersion || version > kProtocolVersion) {
        result.error = SyncErrorCode::kProtocolMismatch;
        return result;
    }

    const std::uint8_t opcode = in[5];
    if (!isKnownOpcode(opcode)) {
        result.error = SyncErrorCode::kUnknownOpcode;
        return result;
    }

    const std::uint32_t payloadLen = detail::readU32(in, 20);
    if (payloadLen > kMaxPayloadSize) {
        result.error = SyncErrorCode::kPayloadTooLarge;
        return result;
    }

    result.header.version = version;
    result.header.opcode = static_cast<Opcode>(opcode);
    result.header.flags = detail::readU16(in, 6);
    result.header.sessionId = detail::readU64(in, 8);
    result.header.seq = detail::readU32(in, 16);
    result.header.payloadLen = payloadLen;
    result.header.payloadCrc32 = detail::readU32(in, 24);
    return result;
}

/**
 * Distinguishes a legacy v1 stream from a v2 stream by looking at the first four bytes.
 *
 * v1 framing is a bare big endian length prefix, so its first byte is 0x00 for any
 * payload below 16 MiB. v2 always starts with the magic. This is what lets the server
 * accept both without a flag day.
 */
constexpr bool looksLikeV2Frame(const std::uint8_t* firstFourBytes) {
    return detail::readU32(firstFourBytes, 0) == kFrameMagic;
}

// ------------------------------------------------------------ layout guarantees

namespace detail {
/// The canonical CRC check string "123456789". Spelled out as bytes because a
/// reinterpret_cast from a string literal is not allowed in a constant expression.
inline constexpr std::uint8_t kCrcCheckInput[9] = {'1', '2', '3', '4', '5',
                                                   '6', '7', '8', '9'};
}  // namespace detail

static_assert(kFrameHeaderSize == 32, "frame header must stay 32 bytes");
static_assert(kMaxPayloadSize == 1048576u, "payload cap is part of the wire contract");
static_assert(kFrameMagic == 0x48465331u, "magic is part of the wire contract");
static_assert(crc32c(detail::kCrcCheckInput, 9) == 0xE3069283u,
              "CRC-32C check value mismatch: the polynomial or reflection is wrong");

}  // namespace homemoney::sync

#endif  // HOMEMONEY_SYNC_PROTOCOL_H
