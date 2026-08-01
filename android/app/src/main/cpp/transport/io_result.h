#ifndef HOMEMONEY_TRANSPORT_IO_RESULT_H
#define HOMEMONEY_TRANSPORT_IO_RESULT_H

#include <cstddef>
#include <cstdint>

#include "protocol/sync_protocol.h"

/**
 * Result vocabulary shared by every byte stream in the transport layer.
 *
 * The old implementation collapsed all of this into `bool`, which is why a socket that
 * was merely interrupted by a signal looked exactly like a peer that had hung up: both
 * returned false, both aborted the sync, and the user saw "sync failed" with no way to
 * tell a transient hiccup from a real protocol break. Separating the transient cases
 * (kInterrupted, kWouldBlock) from the terminal ones (kClosed, kError) is what makes a
 * retry policy possible at all.
 */
namespace homemoney::sync {

/// Outcome of a single, possibly partial, read or write attempt.
enum class IoStatus : std::uint8_t {
    /// Made progress. `transferred` says how much; it may be less than requested.
    kOk = 0,
    /// EAGAIN / EWOULDBLOCK. Nothing transferred, the caller should retry.
    kWouldBlock,
    /// EINTR. Nothing transferred, retry immediately.
    kInterrupted,
    /// The deadline expired before the socket became ready.
    kTimeout,
    /// Orderly shutdown by the peer (read returned 0), or the stream ran out of data.
    kClosed,
    /// Unrecoverable failure; the fd must be discarded.
    kError,
};

/// Outcome plus the number of bytes actually moved.
struct IoResult {
    IoStatus status = IoStatus::kOk;
    std::size_t transferred = 0;

    constexpr bool ok() const { return status == IoStatus::kOk; }

    /// True for statuses where waiting and trying again is the correct response.
    constexpr bool transient() const {
        return status == IoStatus::kWouldBlock || status == IoStatus::kInterrupted;
    }
};

/// Maps a transport level status onto the protocol error code reported to the peer / UI.
constexpr SyncErrorCode toSyncError(IoStatus status) {
    switch (status) {
        case IoStatus::kOk:
            return SyncErrorCode::kOk;
        case IoStatus::kWouldBlock:
        case IoStatus::kInterrupted:
        case IoStatus::kTimeout:
            return SyncErrorCode::kIoTimeout;
        case IoStatus::kClosed:
            return SyncErrorCode::kPeerClosed;
        case IoStatus::kError:
            break;
    }
    return SyncErrorCode::kInternal;
}

}  // namespace homemoney::sync

#endif  // HOMEMONEY_TRANSPORT_IO_RESULT_H
