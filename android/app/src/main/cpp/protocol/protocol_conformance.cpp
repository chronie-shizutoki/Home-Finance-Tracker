/**
 * Compile time conformance checks for the C++ side of the sync wire protocol.
 *
 * Every assertion in this file is a static_assert, so the native build itself fails if a
 * change to sync_protocol.h alters a single byte on the wire. There is deliberately no
 * runtime test framework here: a native unit test would need a device or an emulator to
 * run, whereas these checks run on every developer machine and every CI build for free.
 *
 * The vectors come from protocol/frame_vectors.txt via the generated mirror. The Kotlin
 * side asserts the exact same vectors in ProtocolConformanceTest, which is what pins the
 * two implementations to each other rather than merely to themselves.
 */

#include "frame_vectors_generated.h"
#include "sync_protocol.h"

namespace homemoney::sync::conformance {

namespace {

using vectors::FrameVector;
using vectors::kFrameVectorCount;
using vectors::kFrameVectors;

/// Element-wise comparison; spelled out so the checks work regardless of the standard
/// library's constexpr support for std::array's comparison operators.
constexpr bool bytesEqual(const FrameHeaderBytes& lhs, const FrameHeaderBytes& rhs) {
    for (std::size_t i = 0; i < kFrameHeaderSize; ++i) {
        if (lhs[i] != rhs[i]) {
            return false;
        }
    }
    return true;
}

constexpr FrameHeader toHeader(const FrameVector& v) {
    FrameHeader header{};
    header.version = v.version;
    header.opcode = static_cast<Opcode>(v.opcode);
    header.flags = v.flags;
    header.sessionId = v.sessionId;
    header.seq = v.seq;
    header.payloadLen = v.payloadLen;
    header.payloadCrc32 = v.payloadCrc32;
    return header;
}

/// Encoding a vector must reproduce the golden bytes exactly.
constexpr bool encodesToGoldenBytes(const FrameVector& v) {
    return bytesEqual(encodeFrameHeader(toHeader(v)), v.expected);
}

/// Decoding the golden bytes must reproduce every field of the vector.
constexpr bool decodesToOriginalFields(const FrameVector& v) {
    const FrameHeaderResult result = decodeFrameHeader(v.expected.data());
    if (!result.ok()) {
        return false;
    }
    const FrameHeader& h = result.header;
    return h.version == v.version && static_cast<std::uint8_t>(h.opcode) == v.opcode &&
           h.flags == v.flags && h.sessionId == v.sessionId && h.seq == v.seq &&
           h.payloadLen == v.payloadLen && h.payloadCrc32 == v.payloadCrc32;
}

constexpr bool allVectorsRoundTrip() {
    for (std::size_t i = 0; i < kFrameVectorCount; ++i) {
        if (!encodesToGoldenBytes(kFrameVectors[i])) {
            return false;
        }
        if (!decodesToOriginalFields(kFrameVectors[i])) {
            return false;
        }
    }
    return true;
}

// ------------------------------------------------------------- rejection checks

/// Corrupts a single byte of a golden frame header by flipping bits with @p delta.
/// Only golden vector #1 (index 1) is used because it has a representative set of field
/// values and the checks are about the codec's detection of corruption, not about the
/// specific values in any one vector.
///
/// Returns the first golden frame with the byte at @p offset flipped by @p delta.
constexpr FrameHeaderBytes corrupt(std::size_t offset, std::uint8_t delta) {
    FrameHeaderBytes bytes = kFrameVectors[1].expected;
    bytes[offset] = static_cast<std::uint8_t>(bytes[offset] ^ delta);
    return bytes;
}

/// Decodes a corrupted header and returns the resulting error code.
/// Used by the compile-time assertions below to verify that every type of corruption
/// produces the expected error.
constexpr SyncErrorCode errorFor(const FrameHeaderBytes& bytes) {
    return decodeFrameHeader(bytes.data()).error;
}

/// Rebuilds a header with an arbitrary field value and a *valid* header checksum, so the
/// semantic validation is exercised rather than the checksum path.
constexpr FrameHeaderBytes withField(std::size_t offset, std::uint8_t value) {
    FrameHeaderBytes bytes = kFrameVectors[1].expected;
    bytes[offset] = value;
    // Recompute the trailing header CRC over the first 28 bytes.
    const std::uint32_t crc = crc32c(bytes.data(), kFrameHeaderSize - 4);
    bytes[28] = static_cast<std::uint8_t>((crc >> 24) & 0xFFu);
    bytes[29] = static_cast<std::uint8_t>((crc >> 16) & 0xFFu);
    bytes[30] = static_cast<std::uint8_t>((crc >> 8) & 0xFFu);
    bytes[31] = static_cast<std::uint8_t>(crc & 0xFFu);
    return bytes;
}

constexpr FrameHeaderBytes withPayloadLen(std::uint32_t length) {
    FrameHeaderBytes bytes = kFrameVectors[1].expected;
    bytes[20] = static_cast<std::uint8_t>((length >> 24) & 0xFFu);
    bytes[21] = static_cast<std::uint8_t>((length >> 16) & 0xFFu);
    bytes[22] = static_cast<std::uint8_t>((length >> 8) & 0xFFu);
    bytes[23] = static_cast<std::uint8_t>(length & 0xFFu);
    const std::uint32_t crc = crc32c(bytes.data(), kFrameHeaderSize - 4);
    bytes[28] = static_cast<std::uint8_t>((crc >> 24) & 0xFFu);
    bytes[29] = static_cast<std::uint8_t>((crc >> 16) & 0xFFu);
    bytes[30] = static_cast<std::uint8_t>((crc >> 8) & 0xFFu);
    bytes[31] = static_cast<std::uint8_t>(crc & 0xFFu);
    return bytes;
}

constexpr std::uint8_t kV1LengthPrefix[4] = {0x00, 0x00, 0x04, 0x00};

}  // namespace

// ---------------------------------------------------------------- the assertions

// ---- Round-trip sanity: every golden vector must encode and decode identically ----
static_assert(kFrameVectorCount >= 10, "the vector set should cover every field boundary");
static_assert(allVectorsRoundTrip(),
              "C++ frame codec no longer matches protocol/frame_vectors.txt - "
              "regenerate the vectors or fix the codec");

// ---- Header integrity: a single flipped bit anywhere in the header must be caught ----
// The magic bytes are checked first (cheapest and catches a v1 peer); every other byte
// is covered by the header CRC. A failure here means the codec would silently accept a
// corrupted frame — exactly the bug that caused the old server to allocate 10 MB.
static_assert(errorFor(corrupt(4, 0x01)) == SyncErrorCode::kCrcMismatch, "version bit flip");
static_assert(errorFor(corrupt(5, 0x01)) == SyncErrorCode::kCrcMismatch, "opcode bit flip");
static_assert(errorFor(corrupt(7, 0x01)) == SyncErrorCode::kCrcMismatch, "flags bit flip");
static_assert(errorFor(corrupt(15, 0x01)) == SyncErrorCode::kCrcMismatch, "session bit flip");
static_assert(errorFor(corrupt(19, 0x01)) == SyncErrorCode::kCrcMismatch, "seq bit flip");
static_assert(errorFor(corrupt(23, 0x01)) == SyncErrorCode::kCrcMismatch, "length bit flip");
static_assert(errorFor(corrupt(27, 0x01)) == SyncErrorCode::kCrcMismatch, "payload crc flip");
static_assert(errorFor(corrupt(31, 0x01)) == SyncErrorCode::kCrcMismatch, "header crc flip");

// A wrong magic is reported as such, not as a checksum failure: the server relies on this
// distinction to fall back to the legacy v1 framing instead of dropping the connection.
static_assert(errorFor(corrupt(0, 0xFF)) == SyncErrorCode::kBadMagic, "magic must be checked first");

// ---- Semantic validation: fields that are individually wrong but with an intact checksum ----
// These tests rebuild the header CRC so the codec passes the integrity check and reaches
// the semantic checks. If any of these fail, a deliberately malformed but checksum-correct
// frame would be accepted.
static_assert(errorFor(withField(4, 1)) == SyncErrorCode::kProtocolMismatch, "version too old");
static_assert(errorFor(withField(4, 99)) == SyncErrorCode::kProtocolMismatch, "version too new");
static_assert(errorFor(withField(5, 0x7F)) == SyncErrorCode::kUnknownOpcode, "unknown opcode");
static_assert(errorFor(withPayloadLen(kMaxPayloadSize)) == SyncErrorCode::kOk,
              "the cap itself must still be accepted");
static_assert(errorFor(withPayloadLen(kMaxPayloadSize + 1)) == SyncErrorCode::kPayloadTooLarge,
              "an oversized payload must be rejected before anything is allocated");
static_assert(errorFor(withPayloadLen(0xFFFFFFFFu)) == SyncErrorCode::kPayloadTooLarge,
              "the 10 MB allocation bug must stay fixed");

// ---- Version negotiation: a legacy v1 length prefix must never be mistaken for a v2 frame ----
// This is critical for the dual-dialect server: the first 4 bytes are all it reads before
// deciding which framing to use. A false positive here would send v1 raw bytes through the
// v2 codec, producing an unreadable error instead of a clean fallback.
static_assert(!looksLikeV2Frame(kV1LengthPrefix), "v1 stream misdetected as v2");
static_assert(looksLikeV2Frame(kFrameVectors[0].expected.data()), "v2 stream misdetected as v1");

// ---- Retry classification: the Kotlin backoff policy depends on these being correct ----
// isRetryable is a single source of truth shared by both peers. If a transient error is
// classified as terminal, a flaky Wi-Fi link makes sync permanently fail. If a terminal
// error is classified as transient, the client burns its retry budget and the user waits
// tens of seconds before seeing the real error message.
static_assert(isRetryable(SyncErrorCode::kIoTimeout), "io timeout must be retryable");
static_assert(isRetryable(SyncErrorCode::kPeerClosed), "peer closed must be retryable");
static_assert(isRetryable(SyncErrorCode::kCrcMismatch), "crc mismatch must be retryable");
static_assert(!isRetryable(SyncErrorCode::kProtocolMismatch), "protocol mismatch is terminal");
static_assert(!isRetryable(SyncErrorCode::kAuthRejected), "auth rejection is terminal");
static_assert(!isRetryable(SyncErrorCode::kParseError), "parse error is terminal");
static_assert(!isRetryable(SyncErrorCode::kCancelled), "cancellation is terminal");

}  // namespace homemoney::sync::conformance
