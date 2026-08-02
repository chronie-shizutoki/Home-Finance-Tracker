/**
 * Compile time conformance tests for the transport layer.
 *
 * There is no host C++ toolchain on this project's build machines, so a conventional gtest
 * binary cannot be produced. Instead every piece of transport logic that can be made pure
 * is written against the ByteReader / ByteWriter concepts, and this file drives it with
 * in-memory streams inside static_asserts. The NDK compiler therefore *executes* the
 * partial-read, EINTR, EAGAIN, truncation and corruption paths on every build; a
 * regression fails the build with "static assertion failed" instead of shipping.
 *
 * The frame vectors and retry vectors come from tools/gen_frame_vectors.py, an independent
 * third implementation, so a bug shared by the C++ and Kotlin codecs cannot bless itself.
 *
 * This translation unit deliberately emits no code.
 */

#include <array>
#include <cstddef>
#include <cstdint>
#include <vector>

#include "protocol/frame_vectors_generated.h"
#include "protocol/retry_vectors_generated.h"
#include "protocol/sync_protocol.h"
#include "transport/byte_stream.h"
#include "transport/frame_codec.h"
#include "transport/io_result.h"
#include "transport/retry_policy.h"

namespace homemoney::sync::conformance {
namespace {

constexpr std::size_t kMaxVectorPayload = 256;
constexpr std::size_t kMaxWireImage = kFrameHeaderSize + kMaxVectorPayload;

using Writer = MemoryWriter<kMaxWireImage>;

/// The complete on-the-wire bytes of a golden vector: header followed by payload.
struct WireImage {
    std::array<std::uint8_t, kMaxWireImage> bytes{};
    std::size_t size = 0;
};

constexpr WireImage wireImageOf(const vectors::FrameVector& vector) {
    WireImage image{};
    for (std::size_t i = 0; i < kFrameHeaderSize; ++i) {
        image.bytes[i] = vector.expected[i];
    }
    for (std::size_t i = 0; i < vector.payloadLen; ++i) {
        image.bytes[kFrameHeaderSize + i] = vector.payload[i];
    }
    image.size = kFrameHeaderSize + vector.payloadLen;
    return image;
}

constexpr FrameHeader headerOf(const vectors::FrameVector& vector) {
    FrameHeader header{};
    header.version = vector.version;
    header.opcode = static_cast<Opcode>(vector.opcode);
    header.flags = vector.flags;
    header.sessionId = vector.sessionId;
    header.seq = vector.seq;
    return header;
}

// ------------------------------------------------------- writeFrame matches the vectors

/// Verifies that writeFrame reproduces the exact bytes from a golden vector.
/// @param maxChunk    artificial write limit per call (0 = unlimited, 1 = one byte at a time)
/// @param transientEvery  inject EAGAIN/EINTR every N calls to exercise the retry loop
constexpr bool writeMatchesGolden(const vectors::FrameVector& vector,
                                  std::size_t maxChunk,
                                  std::uint32_t transientEvery) {
    Writer writer(maxChunk, transientEvery);
    if (writeFrame(writer, headerOf(vector), vector.payload.data(), vector.payloadLen) !=
        SyncErrorCode::kOk) {
        return false;
    }
    const WireImage image = wireImageOf(vector);
    return writer.equals(image.bytes.data(), image.size);
}

constexpr bool allWritesMatchGolden(std::size_t maxChunk, std::uint32_t transientEvery) {
    for (std::size_t i = 0; i < vectors::kFrameVectorCount; ++i) {
        if (!writeMatchesGolden(vectors::kFrameVectors[i], maxChunk, transientEvery)) {
            return false;
        }
    }
    return true;
}

// ---- writeFrame round-trip: every golden vector must be reproducible ----
// A failure here means the C++ codec produces bytes that differ from the independently
// generated golden vectors. The Kotlin side validates against the same vectors, so this
// assertion is what keeps the two implementations byte-for-byte identical.
static_assert(allWritesMatchGolden(0, 0), "writeFrame does not match the golden vectors");
// A socket that accepts one byte per call must still produce identical bytes.
static_assert(allWritesMatchGolden(1, 0), "writeExact mishandles short writes");
static_assert(allWritesMatchGolden(3, 0), "writeExact mishandles short writes");
// ... and must survive EAGAIN / EINTR interleaved with those short writes.
static_assert(allWritesMatchGolden(1, 3), "writeExact mishandles EAGAIN/EINTR");
static_assert(allWritesMatchGolden(0, 2), "writeExact mishandles EAGAIN/EINTR");

// ------------------------------------------------------------------ readFrame recovery

/// Verifies that readFrame correctly parses a golden vector's wire image back into
/// the original fields and payload. The maxChunk and transientEvery parameters inject
/// the same hostile conditions as the write side: single-byte reads, split headers,
/// and interleaved EAGAIN/EINTR.
constexpr bool readMatchesGolden(const vectors::FrameVector& vector,
                                 std::size_t maxChunk,
                                 std::uint32_t transientEvery) {
    const WireImage image = wireImageOf(vector);
    MemoryReader reader(image.bytes.data(), image.size, maxChunk, transientEvery);
    Frame frame{};
    if (readFrame(reader, frame) != SyncErrorCode::kOk) {
        return false;
    }
    if (frame.header.version != vector.version) {
        return false;
    }
    if (frame.header.opcode != static_cast<Opcode>(vector.opcode)) {
        return false;
    }
    if (frame.header.flags != vector.flags) {
        return false;
    }
    if (frame.header.sessionId != vector.sessionId) {
        return false;
    }
    if (frame.header.seq != vector.seq) {
        return false;
    }
    if (frame.header.payloadLen != vector.payloadLen) {
        return false;
    }
    if (frame.header.payloadCrc32 != vector.payloadCrc32) {
        return false;
    }
    if (frame.payload.size() != vector.payloadLen) {
        return false;
    }
    for (std::size_t i = 0; i < vector.payloadLen; ++i) {
        if (frame.payload[i] != vector.payload[i]) {
            return false;
        }
    }
    return true;
}

constexpr bool allReadsMatchGolden(std::size_t maxChunk, std::uint32_t transientEvery) {
    for (std::size_t i = 0; i < vectors::kFrameVectorCount; ++i) {
        if (!readMatchesGolden(vectors::kFrameVectors[i], maxChunk, transientEvery)) {
            return false;
        }
    }
    return true;
}

// ---- readFrame must accept every golden vector, even under hostile network conditions ----
static_assert(allReadsMatchGolden(0, 0), "readFrame does not accept the golden vectors");
// One byte per read is the worst case a lossy Wi-Fi link produces; it must be transparent.
static_assert(allReadsMatchGolden(1, 0), "readExact mishandles single byte reads");
static_assert(allReadsMatchGolden(5, 0), "readExact mishandles short reads");
static_assert(allReadsMatchGolden(31, 0), "readExact mishandles a split header");
static_assert(allReadsMatchGolden(33, 0), "readExact mishandles a header plus partial body");
static_assert(allReadsMatchGolden(1, 3), "readExact mishandles EAGAIN/EINTR");
static_assert(allReadsMatchGolden(0, 2), "readExact mishandles EAGAIN/EINTR");
static_assert(allReadsMatchGolden(7, 5), "readExact mishandles EAGAIN/EINTR");

// ------------------------------------------------------------------------- truncation

/// Reads a truncated version of a golden vector (only the first @p keep bytes are available)
/// and verifies the error code. A peer that vanishes mid-frame must be reported as kPeerClosed.
constexpr SyncErrorCode readTruncated(const vectors::FrameVector& vector, std::size_t keep) {
    const WireImage image = wireImageOf(vector);
    MemoryReader reader(image.bytes.data(), keep);
    Frame frame{};
    return readFrame(reader, frame);
}

/// Index of a vector that carries a body, so the truncated-payload case is meaningful.
constexpr std::size_t indexOfVectorWithPayload() {
    for (std::size_t i = 0; i < vectors::kFrameVectorCount; ++i) {
        if (vectors::kFrameVectors[i].payloadLen > 8) {
            return i;
        }
    }
    return 0;
}

constexpr std::size_t kBodyVector = indexOfVectorWithPayload();

static_assert(vectors::kFrameVectors[kBodyVector].payloadLen > 8,
              "expected at least one golden vector with a body");

// ---- Truncation must always be reported as kPeerClosed, never as success ----
// The old code returned false for all three truncation cases and the caller couldn't tell
// them apart. kPeerClosed is distinct from kCrcMismatch (corruption) and kIoTimeout (stall),
// which lets the retry policy treat each case differently.
static_assert(readTruncated(vectors::kFrameVectors[kBodyVector], 0) == SyncErrorCode::kPeerClosed,
              "empty stream must be kPeerClosed");
static_assert(readTruncated(vectors::kFrameVectors[kBodyVector], 3) == SyncErrorCode::kPeerClosed,
              "truncated prefix must be kPeerClosed");
static_assert(readTruncated(vectors::kFrameVectors[kBodyVector], 31) == SyncErrorCode::kPeerClosed,
              "truncated header must be kPeerClosed");
static_assert(readTruncated(vectors::kFrameVectors[kBodyVector], kFrameHeaderSize) ==
                      SyncErrorCode::kPeerClosed,
              "missing body must be kPeerClosed");
static_assert(readTruncated(vectors::kFrameVectors[kBodyVector], kFrameHeaderSize + 4) ==
                      SyncErrorCode::kPeerClosed,
              "partial body must be kPeerClosed");

// ------------------------------------------------------------------------- corruption

/// Returns the error from reading a corrupted golden vector where byte @p index was
/// flipped by @p mask. Used to verify that every byte of the frame is protected by
/// either the magic check or the CRC.
constexpr SyncErrorCode readCorrupted(const vectors::FrameVector& vector,
                                      std::size_t index,
                                      std::uint8_t mask) {
    WireImage image = wireImageOf(vector);
    image.bytes[index] = static_cast<std::uint8_t>(image.bytes[index] ^ mask);
    MemoryReader reader(image.bytes.data(), image.size);
    Frame frame{};
    return readFrame(reader, frame);
}

/// Any damage to the magic is reported as kBadMagic; anything else in the header as CRC.
constexpr bool everyHeaderByteIsProtected(const vectors::FrameVector& vector) {
    for (std::size_t i = 0; i < 4; ++i) {
        if (readCorrupted(vector, i, 0x01) != SyncErrorCode::kBadMagic) {
            return false;
        }
    }
    for (std::size_t i = 4; i < kFrameHeaderSize; ++i) {
        if (readCorrupted(vector, i, 0x01) != SyncErrorCode::kCrcMismatch) {
            return false;
        }
        if (readCorrupted(vector, i, 0x80) != SyncErrorCode::kCrcMismatch) {
            return false;
        }
    }
    return true;
}

// ---- Every byte of the header and body must be integrity-checked ----
static_assert(everyHeaderByteIsProtected(vectors::kFrameVectors[kBodyVector]),
              "a single bit flip in the header must never be accepted");

/// Body corruption must surface as kCrcMismatch - retryable - rather than as a parse error
/// three layers up, which is what happened before the payload was checksummed.
constexpr bool everyBodyByteIsProtected(const vectors::FrameVector& vector) {
    for (std::size_t i = 0; i < vector.payloadLen; ++i) {
        if (readCorrupted(vector, kFrameHeaderSize + i, 0x01) != SyncErrorCode::kCrcMismatch) {
            return false;
        }
    }
    return true;
}

static_assert(everyBodyByteIsProtected(vectors::kFrameVectors[kBodyVector]),
              "a single bit flip in the body must be caught by the payload CRC");

// ------------------------------------------------------------- hostile header values

/// Builds a well-formed, correctly checksummed header that claims an absurd body size.
/// Used to verify that the payload cap is enforced *before* any allocation, which is the
/// bug the old server had: it would call vector::resize(claimedLen) before checking the cap.
constexpr SyncErrorCode readWithClaimedLength(std::uint32_t claimedLen,
                                              std::uint32_t claimedCrc = 0) {
    FrameHeader header{};
    header.opcode = Opcode::kChunk;
    header.payloadLen = claimedLen;
    header.payloadCrc32 = claimedCrc;
    const FrameHeaderBytes encoded = encodeFrameHeader(header);
    MemoryReader reader(encoded.data(), encoded.size());
    Frame frame{};
    return readFrame(reader, frame);
}

// ---- The payload size cap must reject oversized claims before allocating ----
// The header checksum is valid, so this simulates a peer (or attacker) asking the
// receiver to allocate an absurd amount of memory. The cap must reject it before any
// allocation happens.
static_assert(readWithClaimedLength(kMaxPayloadSize + 1) == SyncErrorCode::kPayloadTooLarge,
              "a body one byte over the cap must be rejected");
static_assert(readWithClaimedLength(0xFFFFFFFFu) == SyncErrorCode::kPayloadTooLarge,
              "a 4 GiB body claim must be rejected before allocating");
static_assert(readWithClaimedLength(0x80000000u) == SyncErrorCode::kPayloadTooLarge,
              "a 2 GiB body claim must be rejected before allocating");

// An empty body with a non-zero checksum is internally inconsistent and must be rejected
// rather than silently treated as an empty payload.
static_assert(readWithClaimedLength(0, 0x12345678u) == SyncErrorCode::kCrcMismatch,
              "empty payload with a non-zero CRC must be rejected");
static_assert(readWithClaimedLength(0, 0) == SyncErrorCode::kOk,
              "a genuinely empty payload must be accepted");

/// writeFrame must refuse an oversized body before it touches the pointer at all, which is
/// why passing nullptr here is safe and is itself part of the assertion.
constexpr SyncErrorCode writeOversizedBody() {
    Writer writer;
    FrameHeader header{};
    header.opcode = Opcode::kChunk;
    return writeFrame(writer, header, nullptr, static_cast<std::size_t>(kMaxPayloadSize) + 1);
}

static_assert(writeOversizedBody() == SyncErrorCode::kPayloadTooLarge,
              "writeFrame must reject an oversized body before dereferencing the payload");

// --------------------------------------------------------------------- version checks

/// Builds a header with custom version and opcode bytes, re-checksumming so the codec
/// reaches the semantic checks rather than failing at the CRC stage. Used to verify that
/// version/opcode boundaries are enforced correctly regardless of checksum validity.
constexpr SyncErrorCode readWithRawHeader(std::uint8_t versionByte, std::uint8_t opcodeByte) {
    FrameHeader header{};
    header.opcode = Opcode::kPing;
    FrameHeaderBytes encoded = encodeFrameHeader(header);
    encoded[4] = versionByte;
    encoded[5] = opcodeByte;
    // Re-checksum so the version / opcode check is what rejects it, not the header CRC.
    const std::uint32_t crc = crc32c(encoded.data(), kFrameHeaderSize - 4);
    encoded[28] = static_cast<std::uint8_t>((crc >> 24) & 0xFFu);
    encoded[29] = static_cast<std::uint8_t>((crc >> 16) & 0xFFu);
    encoded[30] = static_cast<std::uint8_t>((crc >> 8) & 0xFFu);
    encoded[31] = static_cast<std::uint8_t>(crc & 0xFFu);

    MemoryReader reader(encoded.data(), encoded.size());
    Frame frame{};
    return readFrame(reader, frame);
}

static_assert(readWithRawHeader(1, 0x30) == SyncErrorCode::kProtocolMismatch,
              "a v1 version byte must be reported as a protocol mismatch");
static_assert(readWithRawHeader(3, 0x30) == SyncErrorCode::kProtocolMismatch,
              "a future version must be reported as a protocol mismatch");
static_assert(readWithRawHeader(2, 0x7F) == SyncErrorCode::kUnknownOpcode,
              "an unknown opcode must be reported as such, not as a parse error");
static_assert(readWithRawHeader(2, 0x30) == SyncErrorCode::kOk,
              "the control case must still pass");

// ------------------------------------------------------------------ transient spinning

/// A stream that is permanently transient must eventually give up — not spin a worker
/// thread forever. readExact has a safety counter (kMaxConsecutiveTransients = 4096)
/// that breaks out of the retry loop when the stream keeps returning EAGAIN/EINTR.
constexpr SyncErrorCode readFromPermanentlyBlockedStream() {
    const std::uint8_t data[8] = {};
    MemoryReader reader(data, sizeof(data), 0, 1);  // transientEvery == 1: never delivers
    std::uint8_t sink[4] = {};
    return readExact(reader, sink, sizeof(sink));
}

static_assert(readFromPermanentlyBlockedStream() == SyncErrorCode::kIoTimeout,
              "readExact must give up instead of spinning forever");

// ------------------------------------------------------------------ v1 / v2 dispatch

constexpr bool v1AndV2AreDistinguishable() {
    // Any legacy length below 16 MiB has a zero top byte, so it can never be mistaken for
    // the magic. This is what lets one listener serve both dialects.
    const std::uint8_t legacy[4] = {0x00, 0x00, 0x10, 0x00};
    if (looksLikeV2Frame(legacy)) {
        return false;
    }
    if (legacyLengthFromPrefix(legacy) != 4096u) {
        return false;
    }
    const std::uint8_t modern[4] = {0x48, 0x46, 0x53, 0x31};
    if (!looksLikeV2Frame(modern)) {
        return false;
    }
    // A legacy length large enough to collide with the magic would have to exceed the
    // legacy cap, so it is rejected either way.
    return legacyLengthFromPrefix(modern) > kMaxLegacyPayloadSize;
}

static_assert(v1AndV2AreDistinguishable(), "v1 and v2 framing must be distinguishable");

constexpr bool legacyRoundTrips() {
    const std::uint8_t body[5] = {0xDE, 0xAD, 0xBE, 0xEF, 0x01};
    Writer writer(1);  // one byte per write, the hostile case
    if (writeLegacyMessage(writer, body, sizeof(body)) != SyncErrorCode::kOk) {
        return false;
    }
    if (writer.size() != 4 + sizeof(body)) {
        return false;
    }
    MemoryReader reader(writer.data(), writer.size(), 1);
    std::uint8_t prefix[4] = {};
    if (readPrefix(reader, prefix) != SyncErrorCode::kOk) {
        return false;
    }
    if (looksLikeV2Frame(prefix)) {
        return false;
    }
    std::vector<std::uint8_t> out;
    if (readLegacyBody(reader, prefix, out) != SyncErrorCode::kOk) {
        return false;
    }
    if (out.size() != sizeof(body)) {
        return false;
    }
    for (std::size_t i = 0; i < sizeof(body); ++i) {
        if (out[i] != body[i]) {
            return false;
        }
    }
    return true;
}

static_assert(legacyRoundTrips(), "legacy v1 framing must still round trip");

constexpr SyncErrorCode legacyOversized() {
    const std::uint8_t prefix[4] = {0x7F, 0xFF, 0xFF, 0xFF};  // ~2 GiB
    const std::uint8_t nothing[1] = {};
    MemoryReader reader(nothing, 0);
    std::vector<std::uint8_t> out;
    return readLegacyBody(reader, prefix, out);
}

static_assert(legacyOversized() == SyncErrorCode::kPayloadTooLarge,
              "the legacy path must also cap the length before allocating");

// ------------------------------------------------------------------- ack dispatch table

static_assert(ackOpcodeFor(Opcode::kHello) == Opcode::kHelloAck);
static_assert(ackOpcodeFor(Opcode::kAuth) == Opcode::kAuthAck);
static_assert(ackOpcodeFor(Opcode::kManifest) == Opcode::kManifestAck);
static_assert(ackOpcodeFor(Opcode::kChunk) == Opcode::kChunkAck);
static_assert(ackOpcodeFor(Opcode::kPull) == Opcode::kPullAck);
static_assert(ackOpcodeFor(Opcode::kCommit) == Opcode::kCommitAck);
static_assert(ackOpcodeFor(Opcode::kPing) == Opcode::kPong);
// Frames that are already terminal have no ack; replying to them would loop forever.
static_assert(ackOpcodeFor(Opcode::kPong) == Opcode::kError);
static_assert(ackOpcodeFor(Opcode::kBye) == Opcode::kError);
static_assert(ackOpcodeFor(Opcode::kError) == Opcode::kError);
static_assert(ackOpcodeFor(Opcode::kCommitAck) == Opcode::kError);

static_assert(requiresUpperLayer(Opcode::kCommit));
static_assert(requiresUpperLayer(Opcode::kHello));
// A pull is answered from the database, so it has to reach the upper layer like any other
// data frame; only the direction of travel differs.
static_assert(requiresUpperLayer(Opcode::kPull));
// Keepalive must be answered natively; waking the JVM every few seconds would defeat it.
static_assert(!requiresUpperLayer(Opcode::kPing));
static_assert(!requiresUpperLayer(Opcode::kBye));
// An ack arriving at the server means the peer's state machine is confused; it must be
// reported, never handed upstairs as if it were a request.
static_assert(!requiresUpperLayer(Opcode::kPullAck));
static_assert(isKnownOpcode(static_cast<std::uint8_t>(Opcode::kPull)));
static_assert(isKnownOpcode(static_cast<std::uint8_t>(Opcode::kPullAck)));

// ------------------------------------------------------------------------ retry policy

/// Verifies that the C++ backoff implementation matches the independently generated
/// golden vectors from retry_vectors.txt. Each vector encodes a specific (baseDelayMs,
/// retryIndex, randomValue) tuple and the expected jittered delay.
constexpr bool retryVectorsMatch() {
    for (std::size_t i = 0; i < vectors::kRetryVectorCount; ++i) {
        const vectors::RetryVector& v = vectors::kRetryVectors[i];
        const RetryPolicy policy{4, v.baseDelayMs, v.maxDelayMs};
        if (backoffCeilingMs(policy, v.retryIndex) != v.ceilingMs) {
            return false;
        }
        if (jitteredDelayMs(policy, v.retryIndex, v.randomValue) != v.delayMs) {
            return false;
        }
    }
    return true;
}

static_assert(retryVectorsMatch(), "the backoff curve drifted from the golden vectors");

constexpr bool xorshiftVectorsMatch() {
    for (std::size_t i = 0; i < vectors::kXorshiftVectorCount; ++i) {
        const vectors::XorshiftVector& v = vectors::kXorshiftVectors[i];
        const std::uint32_t a = xorshift32(v.seed);
        const std::uint32_t b = xorshift32(a);
        const std::uint32_t c = xorshift32(b);
        if (a != v.after1 || b != v.after2 || c != v.after3) {
            return false;
        }
    }
    return true;
}

static_assert(xorshiftVectorsMatch(), "the jitter PRNG drifted from the golden vectors");

/// The jittered delay must always sit inside [ceiling/2, ceiling] for every random input.
constexpr bool jitterStaysInBounds() {
    const RetryPolicy policy{5, 250, 8000};
    std::uint32_t state = 0x12345678u;
    for (std::uint32_t retry = 0; retry < 8; ++retry) {
        const std::uint32_t ceiling = backoffCeilingMs(policy, retry);
        for (int i = 0; i < 64; ++i) {
            const std::uint32_t delay = jitteredDelayMs(policy, retry, nextRandom(state));
            if (delay < ceiling / 2 || delay > ceiling) {
                return false;
            }
        }
    }
    return true;
}

static_assert(jitterStaysInBounds(), "equal jitter must stay within [ceiling/2, ceiling]");

/// The ceiling must grow monotonically and then stay pinned at the cap.
constexpr bool ceilingIsMonotonicAndCapped() {
    const RetryPolicy policy{10, 250, 8000};
    std::uint32_t previous = 0;
    for (std::uint32_t retry = 0; retry < 40; ++retry) {
        const std::uint32_t ceiling = backoffCeilingMs(policy, retry);
        if (ceiling < previous) {
            return false;
        }
        if (ceiling > policy.maxDelayMs) {
            return false;
        }
        previous = ceiling;
    }
    return previous == policy.maxDelayMs;
}

static_assert(ceilingIsMonotonicAndCapped(), "backoff must be monotonic and capped");

/// A zero seed is a fixed point of xorshift32; nextRandom must not get stuck on it.
constexpr bool zeroSeedRecovers() {
    std::uint32_t state = 0;
    const std::uint32_t first = nextRandom(state);
    const std::uint32_t second = nextRandom(state);
    return first != 0 && second != 0 && first != second;
}

static_assert(zeroSeedRecovers(), "the jitter PRNG must not stall on a zero seed");

constexpr bool retryClassificationIsSane() {
    const RetryPolicy policy{3, 250, 8000};
    // Success is never retried.
    if (shouldRetry(policy, SyncErrorCode::kOk, 0)) {
        return false;
    }
    // Transient transport failures are retried while budget remains.
    if (!shouldRetry(policy, SyncErrorCode::kIoTimeout, 1)) {
        return false;
    }
    if (!shouldRetry(policy, SyncErrorCode::kCrcMismatch, 2)) {
        return false;
    }
    if (!shouldRetry(policy, SyncErrorCode::kBusy, 1)) {
        return false;
    }
    // Budget exhausted.
    if (shouldRetry(policy, SyncErrorCode::kIoTimeout, 3)) {
        return false;
    }
    if (shouldRetry(policy, SyncErrorCode::kIoTimeout, 4)) {
        return false;
    }
    // Deterministic failures must fail fast; retrying them only delays the real message.
    if (shouldRetry(policy, SyncErrorCode::kProtocolMismatch, 0)) {
        return false;
    }
    if (shouldRetry(policy, SyncErrorCode::kAuthRejected, 0)) {
        return false;
    }
    if (shouldRetry(policy, SyncErrorCode::kPayloadTooLarge, 0)) {
        return false;
    }
    if (shouldRetry(policy, SyncErrorCode::kParseError, 0)) {
        return false;
    }
    // maxAttempts == 1 means "try once".
    const RetryPolicy once{1, 250, 8000};
    return !shouldRetry(once, SyncErrorCode::kIoTimeout, 1);
}

static_assert(retryClassificationIsSane(), "retry classification is wrong");

}  // namespace
}  // namespace homemoney::sync::conformance
