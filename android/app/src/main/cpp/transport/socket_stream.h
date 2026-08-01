#ifndef HOMEMONEY_TRANSPORT_SOCKET_STREAM_H
#define HOMEMONEY_TRANSPORT_SOCKET_STREAM_H

#include <cstddef>
#include <cstdint>
#include <string>

#include "protocol/sync_protocol.h"
#include "transport/io_result.h"

/**
 * The one place in the codebase that talks to the kernel.
 *
 * Everything above this file is pure and therefore testable at compile time; everything
 * gnarly about POSIX sockets - EINTR, EAGAIN, partial transfers, connect() that returns
 * EINPROGRESS, SO_RCVTIMEO not applying to connect - is contained here.
 */
namespace homemoney::sync {

/// Monotonic milliseconds. Unaffected by the user changing the clock mid sync.
std::int64_t monotonicNowMs();

/**
 * An absolute point in time, or "never".
 *
 * Deadlines are absolute rather than per-call timeouts on purpose: a per-call timeout of
 * 10 s means a peer that dribbles one byte every 9 s keeps a worker thread alive forever,
 * which is exactly how the old server ended up wedged.
 */
class Deadline {
public:
    constexpr Deadline() = default;

    static Deadline afterMs(std::int64_t millis);
    static constexpr Deadline never() { return Deadline{}; }

    bool expired() const;

    /// Milliseconds left, clamped to >= 0. Returns -1 for an infinite deadline.
    int remainingMs() const;

    constexpr bool infinite() const { return !bounded_; }

private:
    constexpr explicit Deadline(std::int64_t at) : at_(at), bounded_(true) {}

    std::int64_t at_ = 0;
    bool bounded_ = false;
};

/**
 * ByteReader + ByteWriter over a non-blocking socket fd.
 *
 * Ownership stays with the caller; this is a view, not a handle, so it can be re-pointed
 * at a new deadline for each phase of a session without touching the fd.
 */
class FdStream {
public:
    FdStream(int fd, Deadline deadline) : fd_(fd), deadline_(deadline) {}

    IoResult readSome(std::uint8_t* dst, std::size_t size);
    IoResult writeSome(const std::uint8_t* src, std::size_t size);

    void setDeadline(Deadline deadline) { deadline_ = deadline; }
    int fd() const { return fd_; }

private:
    int fd_;
    Deadline deadline_;
};

// ------------------------------------------------------------------ socket utilities

/// Puts the fd into non-blocking mode. Returns false on failure.
bool setNonBlocking(int fd);

/**
 * Applies the options that matter on a phone LAN link:
 *  - TCP_NODELAY, because our frames are small and request/response shaped, so Nagle adds
 *    up to 40 ms of pure latency per exchange for no benefit.
 *  - SO_KEEPALIVE with an aggressive idle time, so a peer that walks out of Wi-Fi range is
 *    detected in seconds instead of the kernel default of two hours.
 */
void configureTcpSocket(int fd);

/**
 * Connects with a real timeout.
 *
 * @param outError set to the reason on failure.
 * @return a connected fd in non-blocking mode, or -1.
 */
int connectWithTimeout(const char* ipv4, std::uint16_t port, int timeoutMs, SyncErrorCode& outError);

/// Creates a listening socket bound to @p port. Returns -1 on failure.
int createListeningSocket(std::uint16_t port, int backlog, SyncErrorCode& outError);

/**
 * Waits for an incoming connection, giving up after @p timeoutMs so the caller can check
 * its shutdown flag. Returns the accepted fd, -1 on timeout, -2 on a fatal error.
 */
int acceptWithTimeout(int listenFd, int timeoutMs, std::string& outPeer);

/// close() that ignores EINTR and never touches an already-closed fd.
void closeQuietly(int fd);

/// Best effort half-close so the peer sees an orderly shutdown rather than a reset.
void shutdownQuietly(int fd);

}  // namespace homemoney::sync

#endif  // HOMEMONEY_TRANSPORT_SOCKET_STREAM_H
