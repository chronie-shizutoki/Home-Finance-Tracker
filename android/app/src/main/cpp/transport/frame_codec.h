#ifndef HOMEMONEY_TRANSPORT_FRAME_CODEC_H
#define HOMEMONEY_TRANSPORT_FRAME_CODEC_H

#include <cstddef>
#include <cstdint>
#include <vector>

#include "protocol/sync_protocol.h"
#include "transport/byte_stream.h"
#include "transport/io_result.h"

/**
 * Frame level read / write on top of any ByteReader / ByteWriter.
 *
 * What this fixes compared to the previous transport:
 *
 *  - Bounded allocation. The old server read a bare 4 byte length and immediately did
 *    `std::vector<uint8_t> buffer(len)` for anything up to 10 MB, with the length coming
 *    straight off the wire and no checksum behind it. One corrupted byte was enough to
 *    make the receiver allocate megabytes and then block forever waiting for data that
 *    would never arrive. Here the header is checksummed first, the length is validated
 *    against kMaxPayloadSize by decodeFrameHeader, and only then is memory reserved.
 *
 *  - Partial transfers are normal, not errors. readExact / writeExact loop until the
 *    request is satisfied, treating EINTR and EAGAIN as "try again" rather than "give up".
 *
 *  - Integrity is verified end to end. The payload CRC is checked before the frame is
 *    handed upstairs, so a silently corrupted body is reported as kCrcMismatch (retryable)
 *    instead of surfacing later as an unparseable protobuf (not retryable).
 */
namespace homemoney::sync {

/// A fully received frame.
struct Frame {
    FrameHeader header{};
    std::vector<std::uint8_t> payload;
};

/**
 * Upper bound on consecutive transient results before a stream is declared dead.
 *
 * A real socket stream converts an expired deadline into kTimeout on its own, so this
 * counter never fires in production. It exists so that a misbehaving stream - or a test
 * double with a wrong script - fails the build or the request instead of spinning a
 * worker thread forever, which is a failure mode that is extremely hard to diagnose on a
 * phone.
 */
inline constexpr std::uint32_t kMaxConsecutiveTransients = 4096;

/// Largest body accepted from a legacy v1 peer. Deliberately smaller than the old 10 MB.
inline constexpr std::uint32_t kMaxLegacyPayloadSize = 8u * 1024u * 1024u;

// -------------------------------------------------------------------- exact transfers

/// Reads exactly @p size bytes, or returns why it could not.
template <ByteReader R>
constexpr SyncErrorCode readExact(R& reader, std::uint8_t* dst, std::size_t size) {
    std::size_t done = 0;
    std::uint32_t transients = 0;
    while (done < size) {
        const IoResult result = reader.readSome(dst + done, size - done);
        if (result.ok()) {
            if (result.transferred == 0) {
                // A stream that reports success without progress would spin forever.
                return SyncErrorCode::kInternal;
            }
            done += result.transferred;
            transients = 0;
            continue;
        }
        if (result.transient()) {
            if (++transients > kMaxConsecutiveTransients) {
                return SyncErrorCode::kIoTimeout;
            }
            continue;
        }
        return toSyncError(result.status);
    }
    return SyncErrorCode::kOk;
}

/// Writes exactly @p size bytes, or returns why it could not.
template <ByteWriter W>
constexpr SyncErrorCode writeExact(W& writer, const std::uint8_t* src, std::size_t size) {
    std::size_t done = 0;
    std::uint32_t transients = 0;
    while (done < size) {
        const IoResult result = writer.writeSome(src + done, size - done);
        if (result.ok()) {
            if (result.transferred == 0) {
                return SyncErrorCode::kInternal;
            }
            done += result.transferred;
            transients = 0;
            continue;
        }
        if (result.transient()) {
            if (++transients > kMaxConsecutiveTransients) {
                return SyncErrorCode::kIoTimeout;
            }
            continue;
        }
        return toSyncError(result.status);
    }
    return SyncErrorCode::kOk;
}

// ------------------------------------------------------------------------ frame write

/**
 * Serialises one frame. The payload checksum and length are computed here so a caller can
 * never emit a frame whose header disagrees with its body.
 */
template <ByteWriter W>
constexpr SyncErrorCode writeFrame(W& writer,
                                   FrameHeader header,
                                   const std::uint8_t* payload,
                                   std::size_t payloadLen) {
    if (payloadLen > kMaxPayloadSize) {
        return SyncErrorCode::kPayloadTooLarge;
    }
    header.version = kProtocolVersion;
    header.payloadLen = static_cast<std::uint32_t>(payloadLen);
    header.payloadCrc32 = payloadLen == 0 ? 0u : crc32c(payload, payloadLen);

    const FrameHeaderBytes encoded = encodeFrameHeader(header);
    const SyncErrorCode headerResult = writeExact(writer, encoded.data(), encoded.size());
    if (headerResult != SyncErrorCode::kOk) {
        return headerResult;
    }
    if (payloadLen == 0) {
        return SyncErrorCode::kOk;
    }
    return writeExact(writer, payload, payloadLen);
}

/// Convenience overload for a frame with no body (PING, PONG, BYE).
template <ByteWriter W>
constexpr SyncErrorCode writeFrame(W& writer, FrameHeader header) {
    return writeFrame(writer, header, nullptr, 0);
}

// ------------------------------------------------------------------------- frame read

/**
 * Reads the 4 byte stream prefix, which is what tells a v1 peer from a v2 peer.
 *
 * Both framings begin with 4 bytes, so the server can read them unconditionally and only
 * then decide which dialect it is speaking. Nothing else needs a "protocol probe".
 */
template <ByteReader R>
constexpr SyncErrorCode readPrefix(R& reader, std::uint8_t (&prefix)[4]) {
    return readExact(reader, prefix, 4);
}

/**
 * Reads the remaining 28 header bytes plus the body, given a prefix already consumed by
 * readPrefix. Splitting it this way keeps the version dispatch allocation free.
 */
template <ByteReader R>
constexpr SyncErrorCode readFrameAfterPrefix(R& reader,
                                             const std::uint8_t (&prefix)[4],
                                             Frame& out) {
    std::uint8_t headerBytes[kFrameHeaderSize] = {};
    for (std::size_t i = 0; i < 4; ++i) {
        headerBytes[i] = prefix[i];
    }
    const SyncErrorCode rest =
            readExact(reader, headerBytes + 4, kFrameHeaderSize - 4);
    if (rest != SyncErrorCode::kOk) {
        return rest;
    }

    // Validates magic, header CRC, version, opcode and the payload length cap. Only after
    // this returns ok() may payloadLen be trusted enough to size a buffer with it.
    const FrameHeaderResult decoded = decodeFrameHeader(headerBytes);
    if (!decoded.ok()) {
        return decoded.error;
    }

    out.header = decoded.header;
    out.payload.clear();
    if (decoded.header.payloadLen == 0) {
        return decoded.header.payloadCrc32 == 0 ? SyncErrorCode::kOk
                                                : SyncErrorCode::kCrcMismatch;
    }

    out.payload.resize(decoded.header.payloadLen);
    const SyncErrorCode body =
            readExact(reader, out.payload.data(), out.payload.size());
    if (body != SyncErrorCode::kOk) {
        out.payload.clear();
        return body;
    }
    if (crc32c(out.payload.data(), out.payload.size()) != decoded.header.payloadCrc32) {
        out.payload.clear();
        return SyncErrorCode::kCrcMismatch;
    }
    return SyncErrorCode::kOk;
}

/// Reads one complete v2 frame.
template <ByteReader R>
constexpr SyncErrorCode readFrame(R& reader, Frame& out) {
    std::uint8_t prefix[4] = {};
    const SyncErrorCode prefixResult = readPrefix(reader, prefix);
    if (prefixResult != SyncErrorCode::kOk) {
        return prefixResult;
    }
    return readFrameAfterPrefix(reader, prefix, out);
}

// ---------------------------------------------------------------- legacy v1 framing

/// Interprets a 4 byte prefix as the legacy big endian length.
constexpr std::uint32_t legacyLengthFromPrefix(const std::uint8_t (&prefix)[4]) {
    return (static_cast<std::uint32_t>(prefix[0]) << 24) |
           (static_cast<std::uint32_t>(prefix[1]) << 16) |
           (static_cast<std::uint32_t>(prefix[2]) << 8) |
           static_cast<std::uint32_t>(prefix[3]);
}

/**
 * Reads a legacy v1 body: a bare length prefix followed by that many bytes, no checksum.
 *
 * Kept only so that an old build on the other phone still syncs during the rollout. The
 * length is capped before allocating, which is the one thing the original code got wrong.
 */
template <ByteReader R>
constexpr SyncErrorCode readLegacyBody(R& reader,
                                       const std::uint8_t (&prefix)[4],
                                       std::vector<std::uint8_t>& out) {
    const std::uint32_t length = legacyLengthFromPrefix(prefix);
    if (length == 0) {
        out.clear();
        return SyncErrorCode::kOk;
    }
    if (length > kMaxLegacyPayloadSize) {
        return SyncErrorCode::kPayloadTooLarge;
    }
    out.resize(length);
    const SyncErrorCode body = readExact(reader, out.data(), out.size());
    if (body != SyncErrorCode::kOk) {
        out.clear();
    }
    return body;
}

/// Writes a legacy v1 message: big endian length prefix followed by the body.
template <ByteWriter W>
constexpr SyncErrorCode writeLegacyMessage(W& writer, const std::uint8_t* payload, std::size_t len) {
    if (len > kMaxLegacyPayloadSize) {
        return SyncErrorCode::kPayloadTooLarge;
    }
    const auto length = static_cast<std::uint32_t>(len);
    const std::uint8_t prefix[4] = {
            static_cast<std::uint8_t>((length >> 24) & 0xFFu),
            static_cast<std::uint8_t>((length >> 16) & 0xFFu),
            static_cast<std::uint8_t>((length >> 8) & 0xFFu),
            static_cast<std::uint8_t>(length & 0xFFu),
    };
    const SyncErrorCode head = writeExact(writer, prefix, 4);
    if (head != SyncErrorCode::kOk || length == 0) {
        return head;
    }
    return writeExact(writer, payload, len);
}

// --------------------------------------------------------------------- ack selection

/**
 * The reply opcode a request expects.
 *
 * Centralised so the two ends cannot disagree about which frame closes an exchange - a
 * mismatch there is exactly the kind of bug that presents as "one device hangs at 70%".
 */
constexpr Opcode ackOpcodeFor(Opcode request) {
    switch (request) {
        case Opcode::kHello:
            return Opcode::kHelloAck;
        case Opcode::kAuth:
            return Opcode::kAuthAck;
        case Opcode::kManifest:
            return Opcode::kManifestAck;
        case Opcode::kChunk:
            return Opcode::kChunkAck;
        case Opcode::kPull:
            return Opcode::kPullAck;
        case Opcode::kCommit:
            return Opcode::kCommitAck;
        case Opcode::kPing:
            return Opcode::kPong;
        default:
            break;
    }
    return Opcode::kError;
}

/// True when the frame carries application data that must be handed to the upper layer.
constexpr bool requiresUpperLayer(Opcode opcode) {
    switch (opcode) {
        case Opcode::kHello:
        case Opcode::kAuth:
        case Opcode::kManifest:
        case Opcode::kChunk:
        case Opcode::kPull:
        case Opcode::kCommit:
            return true;
        default:
            return false;
    }
}

}  // namespace homemoney::sync

#endif  // HOMEMONEY_TRANSPORT_FRAME_CODEC_H
