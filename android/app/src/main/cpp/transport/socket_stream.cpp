/**
 * Socket stream implementation — the only file in the transport layer that performs
 * actual system calls.
 *
 * Every byte sent or received by the sync engine passes through this file. The rest of
 * the transport (frame_codec, retry_policy) is pure C++ and testable at compile time;
 * this file is where the real-world POSIX complexity lives: non-blocking connect with a
 * deadline, TCP_NODELAY for low-latency request/response, aggressive keepalive for
 * detecting peers that walk out of Wi-Fi range, and Android-specific network binding
 * so the socket uses the Wi-Fi interface even when cellular is the system default.
 *
 * Error handling philosophy: every system call result is checked. EINTR is retried.
 * EAGAIN/EWOULDBLOCK defers to poll() with a deadline. The old implementation treated
 * all three as "failure" and aborted the sync; this one treats them as "try again"
 * until the deadline expires.
 */

#include "transport/socket_stream.h"

#include <arpa/inet.h>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <sys/socket.h>
#include <ctime>
#include <unistd.h>
#include <android/log.h>
#include <android/multinetwork.h>

#ifndef ALOGE
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HomeMoneySync", __VA_ARGS__)
#endif
#ifndef ALOGI
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, "HomeMoneySync", __VA_ARGS__)
#endif

namespace homemoney::sync {
namespace {

// ------------------------------------------------------------- keepalive constants

/// Idle time before the first keepalive probe. Short, because a phone that leaves the
/// network gives no FIN and we would otherwise hold the socket until the app is killed.
/// The kernel default is 7200 seconds (2 hours), which on a phone that changes networks
/// several times an hour means the socket is essentially never cleaned up by the OS.
constexpr int kKeepAliveIdleSec = 15;
/// Interval between successive probes once keepalive is triggered.
constexpr int kKeepAliveIntervalSec = 5;
/// Number of unacknowledged probes before the kernel declares the connection dead.
constexpr int kKeepAliveProbes = 3;

// ------------------------------------------------------------- polling primitives

/// Waits for the socket to become ready for the requested operation.
///
/// poll() is the only portable way to do timed I/O readiness checks on a non-blocking
/// socket. select() has an fd_set size limit; epoll is overkill for a handful of fds.
///
/// @return  1  the fd is ready for @p events
///          0  timeout expired with no readiness
///         -1  poll() was interrupted by a signal (EINTR), caller should retry
///         -2  unrecoverable error (EBADF, ENOMEM, etc.)
int waitReady(int fd, short events, int timeoutMs) {
    struct pollfd pfd{};
    pfd.fd = fd;
    pfd.events = events;
    pfd.revents = 0;

    const int rc = ::poll(&pfd, 1, timeoutMs);
    if (rc > 0) {
        return 1;
    }
    if (rc == 0) {
        return 0;
    }
    return errno == EINTR ? -1 : -2;
}

/// Polls for readiness in bounded slices, respecting an absolute deadline.
///
/// The deadline is absolute rather than per-call so that a peer dribbling one byte every
/// few seconds cannot keep a worker alive indefinitely. Each poll slice is capped at 250ms
/// so that even an infinite deadline yields the CPU periodically — the caller can then
/// check its own shutdown flag.
///
/// @return kOk when ready, kWouldBlock when not ready but the deadline hasn't expired,
///         kTimeout when the deadline has passed, kInterrupted/kError on failure.
IoStatus awaitReadiness(int fd, short events, const Deadline& deadline) {
    // A bounded deadline that has already passed must not be turned into an infinite poll.
    const int remaining = deadline.remainingMs();
    if (!deadline.infinite() && remaining <= 0) {
        return IoStatus::kTimeout;
    }
    // Cap a single poll so an infinite deadline still lets the caller observe shutdown.
    const int slice = deadline.infinite() ? 250 : (remaining > 250 ? 250 : remaining);
    switch (waitReady(fd, events, slice)) {
        case 1:
            return IoStatus::kOk;
        case 0:
            return deadline.infinite() || !deadline.expired() ? IoStatus::kWouldBlock
                                                              : IoStatus::kTimeout;
        case -1:
            return IoStatus::kInterrupted;
        default:
            return IoStatus::kError;
    }
}

}  // namespace

// ------------------------------------------------------------------------------ clock

/// Returns the current value of CLOCK_MONOTONIC in milliseconds.
///
/// CLOCK_MONOTONIC is used instead of gettimeofday() or System.currentTimeMillis() because
/// it is unaffected by the user changing the device clock or by NTP adjustments. A sync
/// that started at 3:00 PM must not have its deadline jump forward by an hour if the user
/// manually corrects the time. Returns 0 on the (vanishingly unlikely) failure path.
std::int64_t monotonicNowMs() {
    struct timespec ts{};
    if (::clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return static_cast<std::int64_t>(ts.tv_sec) * 1000 +
           static_cast<std::int64_t>(ts.tv_nsec) / 1000000;
}

/// Creates a deadline that expires after @p millis milliseconds from now.
/// Negative values are clamped to 0 (immediate expiry) rather than producing a deadline
/// in the past that would confuse the polling loop.
Deadline Deadline::afterMs(std::int64_t millis) {
    return Deadline{monotonicNowMs() + (millis < 0 ? 0 : millis)};
}

/// Returns true when the deadline has passed. An infinite deadline (bounded_ == false)
/// never expires — it represents "wait forever" and is used by the server's idle read
/// where we want to hold the connection open until data arrives or the peer disconnects.
bool Deadline::expired() const {
    if (!bounded_) {
        return false;
    }
    return monotonicNowMs() >= at_;
}

/// Milliseconds remaining, clamped to [0, INT32_MAX].
///
/// Returns -1 for an infinite deadline so that callers can distinguish "no deadline" from
/// "deadline just expired". The upper clamp prevents overflow when converting to the int
/// that poll() expects — a multi-week deadline would otherwise wrap to a negative number
/// and be treated as "wait forever".
int Deadline::remainingMs() const {
    if (!bounded_) {
        return -1;
    }
    const std::int64_t left = at_ - monotonicNowMs();
    if (left <= 0) {
        return 0;
    }
    // Clamp; poll takes an int and a multi-week deadline would overflow it.
    return left > 0x7FFFFFFF ? 0x7FFFFFFF : static_cast<int>(left);
}

// -------------------------------------------------------------------------- FdStream

// -------------------------------------------------------------------------- FdStream

/// Reads up to @p size bytes from the non-blocking socket.
///
/// Handles every errno the kernel can return on a TCP socket:
///   - EINTR: signal interrupted the syscall; reported as kInterrupted so the caller retries.
///   - EAGAIN/EWOULDBLOCK: no data available yet; defers to awaitReadiness with the deadline.
///   - ECONNRESET/EPIPE: peer hung up; reported as kClosed so the caller stops cleanly.
///   - recv() returns 0: peer performed an orderly shutdown; also kClosed.
///
/// A return of kWouldBlock with 0 bytes transferred means the socket was not ready within
/// the poll slice but the deadline has not expired yet. The caller (readExact) loops.
IoResult FdStream::readSome(std::uint8_t* dst, std::size_t size) {
    if (fd_ < 0) {
        return IoResult{IoStatus::kError, 0};
    }
    if (size == 0) {
        return IoResult{IoStatus::kOk, 0};
    }

    const ssize_t n = ::recv(fd_, dst, size, 0);
    if (n > 0) {
        return IoResult{IoStatus::kOk, static_cast<std::size_t>(n)};
    }
    if (n == 0) {
        // Orderly shutdown. Distinct from an error: a peer that finished sending and
        // closed its half is a normal end of stream, not a failure to report to the user.
        return IoResult{IoStatus::kClosed, 0};
    }
    if (errno == EINTR) {
        return IoResult{IoStatus::kInterrupted, 0};
    }
    if (errno == EAGAIN || errno == EWOULDBLOCK) {
        const IoStatus status = awaitReadiness(fd_, POLLIN, deadline_);
        return IoResult{status == IoStatus::kOk ? IoStatus::kWouldBlock : status, 0};
    }
    if (errno == ECONNRESET || errno == EPIPE) {
        return IoResult{IoStatus::kClosed, 0};
    }
    return IoResult{IoStatus::kError, 0};
}

/// Writes up to @p size bytes to the non-blocking socket.
///
/// Uses MSG_NOSIGNAL to prevent SIGPIPE from killing the process when the peer has already
/// closed its end. On Android, a SIGPIPE that is not explicitly handled by a signal handler
/// terminates the entire app with no chance to log or unwind — it looks like a random crash.
///
/// The errno handling mirrors readSome: EINTR → retry, EAGAIN → poll with deadline,
/// EPIPE/ECONNRESET → kClosed. A send() that returns 0 (kernel buffer full) maps to
/// kWouldBlock so the caller polls for writability.
IoResult FdStream::writeSome(const std::uint8_t* src, std::size_t size) {
    if (fd_ < 0) {
        return IoResult{IoStatus::kError, 0};
    }
    if (size == 0) {
        return IoResult{IoStatus::kOk, 0};
    }

    // MSG_NOSIGNAL: without it a write to a socket the peer already closed raises SIGPIPE
    // and kills the whole process, which on Android looks like an unexplained app crash.
    const ssize_t n = ::send(fd_, src, size, MSG_NOSIGNAL);
    if (n > 0) {
        return IoResult{IoStatus::kOk, static_cast<std::size_t>(n)};
    }
    if (n == 0) {
        return IoResult{IoStatus::kWouldBlock, 0};
    }
    if (errno == EINTR) {
        return IoResult{IoStatus::kInterrupted, 0};
    }
    if (errno == EAGAIN || errno == EWOULDBLOCK) {
        const IoStatus status = awaitReadiness(fd_, POLLOUT, deadline_);
        return IoResult{status == IoStatus::kOk ? IoStatus::kWouldBlock : status, 0};
    }
    if (errno == EPIPE || errno == ECONNRESET) {
        return IoResult{IoStatus::kClosed, 0};
    }
    return IoResult{IoStatus::kError, 0};
}

// -------------------------------------------------------------------- socket helpers

/// Switches the fd to non-blocking mode. Every socket created by this transport must be
/// non-blocking so that connect(), readSome() and writeSome() can enforce deadlines via
/// poll() rather than blocking indefinitely inside a kernel call.
///
/// Returns false if fcntl() fails, which typically means the fd is invalid — the caller
/// should treat the socket as unusable.
bool setNonBlocking(int fd) {
    const int flags = ::fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return false;
    }
    return ::fcntl(fd, F_SETFL, flags | O_NONBLOCK) == 0;
}

/// Applies TCP options tuned for a phone-to-phone LAN sync:
///   1. TCP_NODELAY disables Nagle's algorithm. Our frames are small (~64 KiB chunks) and
///      request/response shaped, so Nagle would add up to 40ms of latency per exchange
///      with no benefit from batching. On a 5-round-trip handshake, that is 200ms of
///      unnecessary delay.
///   2. SO_KEEPALIVE with aggressive idle/interval/probe counts means a peer that walks
///      out of Wi-Fi range is detected within ~30 seconds instead of the kernel default
///      of 2+ hours. Without this, a socket to a departed phone stays ESTABLISHED until
///      the app is killed, consuming a worker thread the whole time.
void configureTcpSocket(int fd) {
    int on = 1;
    ::setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &on, sizeof(on));
    ::setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on));

    int idle = kKeepAliveIdleSec;
    int interval = kKeepAliveIntervalSec;
    int probes = kKeepAliveProbes;
    ::setsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, &idle, sizeof(idle));
    ::setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &interval, sizeof(interval));
    ::setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &probes, sizeof(probes));
}

/// Performs a TCP connect with a hard deadline.
///
/// A blocking connect() on Linux ignores SO_SNDTIMEO and can sit in SYN retransmission
/// for over two minutes — on a phone this looks like the app has frozen. The solution is
/// three-phase:
///
///   Phase 1 — Parse and validate: inet_pton() rejects malformed addresses before the
///   first system call. A stray space in the IP string is logged with the exact byte
///   and length so it can be distinguished from a real routing failure.
///
///   Phase 2 — Non-blocking connect: the socket is set to O_NONBLOCK before connect().
///   A successful immediate connect returns the fd; EINPROGRESS means the handshake is
///   in flight and we proceed to phase 3.
///
///   Phase 3 — Poll with deadline: poll() on POLLOUT waits for the handshake to complete
///   or the deadline to expire. On success, getsockopt(SO_ERROR) reads the pending error
///   because POLLOUT alone does not distinguish "connected" from "refused".
///
/// @param netHandle Android network handle from ConnectivityManager, or 0 for the system
///        default. The socket is bound to this network *before* connect() so the routing
///        table for that interface is used. Without this, a phone whose Wi-Fi has no
///        internet keeps cellular as the default network, and every LAN connect fails with
///        ENETUNREACH because the cellular interface has no route to 192.168.x.x.
/// @param outError set to the specific SyncErrorCode on failure so the caller can decide
///        whether to retry.
/// @return a connected, non-blocking fd, or -1 on failure.
int connectWithTimeout(const char* ipv4, std::uint16_t port, int timeoutMs, SyncErrorCode& outError,
                       std::uint64_t netHandle) {
    outError = SyncErrorCode::kOk;

    // ---- Phase 1: validate the address before touching the network ----
    struct sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    if (::inet_pton(AF_INET, ipv4, &addr.sin_addr) != 1) {
        // This branch never touches the network, yet it reports the same code as a real
        // routing failure. Logging the raw string and its length is what separates "the peer
        // is unreachable" from "the peer's address arrived with a stray space in it".
        ALOGE("inet_pton rejected address '%s' (len=%zu) for port %u - not an IPv4 literal",
              ipv4, ipv4 == nullptr ? 0u : strlen(ipv4), port);
        outError = SyncErrorCode::kNetworkUnreachable;
        return -1;
    }

    // ---- Phase 2: create and configure the socket ----
    const int fd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        outError = SyncErrorCode::kInternal;
        return -1;
    }
    if (!setNonBlocking(fd)) {
        closeQuietly(fd);
        outError = SyncErrorCode::kInternal;
        return -1;
    }
    configureTcpSocket(fd);

    // Bind the socket to the caller's network *before* connect: the routing decision is made
    // when the SYN is sent, so binding afterwards is too late. Without this the fd carries
    // the app's default-network mark, and a phone that keeps cellular as the default because
    // the Wi-Fi has no internet will fail every LAN connect with ENETUNREACH.
    if (netHandle != 0) {
        if (::android_setsocknetwork(static_cast<net_handle_t>(netHandle), fd) != 0) {
            // Best effort: a stale handle should degrade to the old behaviour, not abort the
            // sync. The log is what tells us which of the two failed on a real device.
            ALOGE("android_setsocknetwork(%llu) failed for %s:%u: errno=%d (%s)",
                  static_cast<unsigned long long>(netHandle), ipv4, port, errno, strerror(errno));
        } else {
            ALOGI("socket for %s:%u bound to network %llu", ipv4, port,
                  static_cast<unsigned long long>(netHandle));
        }
    } else {
        ALOGI("socket for %s:%u uses the default network (no handle supplied)", ipv4, port);
    }

    // ---- Phase 3: non-blocking connect with a poll-based deadline ----
    // A blocking connect() ignores SO_SNDTIMEO on Linux and can sit in SYN retransmit for
    // over two minutes. Non-blocking + poll is the only way to bound it.
    int rc = ::connect(fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr));
    while (rc < 0 && errno == EINTR) {
        rc = ::connect(fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr));
    }
    if (rc == 0) {
        return fd;
    }
    if (errno != EINPROGRESS && errno != EALREADY) {
        ALOGE("connect(%s:%u) immediate failure: errno=%d (%s)", ipv4, port, errno,
              strerror(errno));
        closeQuietly(fd);
        outError = errno == ETIMEDOUT ? SyncErrorCode::kConnectTimeout
                                      : SyncErrorCode::kNetworkUnreachable;
        return -1;
    }

    const Deadline deadline = Deadline::afterMs(timeoutMs);
    for (;;) {
        const int remaining = deadline.remainingMs();
        if (remaining <= 0) {
            closeQuietly(fd);
            outError = SyncErrorCode::kConnectTimeout;
            return -1;
        }
        const int ready = waitReady(fd, POLLOUT, remaining);
        if (ready == -1) {
            continue;  // EINTR
        }
        if (ready == 0) {
            closeQuietly(fd);
            outError = SyncErrorCode::kConnectTimeout;
            return -1;
        }
        if (ready < 0) {
            ALOGE("connect(%s:%u) poll error: errno=%d (%s)", ipv4, port, errno, strerror(errno));
            closeQuietly(fd);
            outError = SyncErrorCode::kNetworkUnreachable;
            return -1;
        }
        break;
    }

    // POLLOUT alone does not mean success: a refused connection is also "writable". The
    // pending error has to be read out explicitly.
    int soError = 0;
    socklen_t len = sizeof(soError);
    if (::getsockopt(fd, SOL_SOCKET, SO_ERROR, &soError, &len) != 0 || soError != 0) {
        ALOGE("connect(%s:%u) completed with error: soError=%d (%s)", ipv4, port, soError,
              soError != 0 ? strerror(soError) : strerror(errno));
        closeQuietly(fd);
        outError = soError == ETIMEDOUT ? SyncErrorCode::kConnectTimeout
                                        : SyncErrorCode::kNetworkUnreachable;
        return -1;
    }
    return fd;
}

/// Creates a listening TCP socket bound to INADDR_ANY:@p port.
///
/// If @p port is 0, the kernel assigns a random ephemeral port and the parameter is
/// updated in-place so the caller can advertise it. SO_REUSEADDR and SO_REUSEPORT are
/// both set: the former avoids "address in use" on restart, the latter allows multiple
/// processes (or multiple restarts of the same process in rapid succession) to bind the
/// same port without error.
///
/// The returned socket is non-blocking so that acceptWithTimeout() can poll with a
/// deadline rather than blocking indefinitely.
int createListeningSocket(std::uint16_t& port, int backlog, SyncErrorCode& outError) {
    outError = SyncErrorCode::kOk;

    const int fd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        outError = SyncErrorCode::kInternal;
        return -1;
    }

    int on = 1;
    ::setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));
    // SO_REUSEPORT allows multiple processes to bind to the same port, and is often
    // more robust in handling stale sockets on Android/Linux.
    ::setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &on, sizeof(on));

    struct sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port);

    if (::bind(fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) < 0) {
        ALOGE("listen socket bind(%u) failed: errno=%d (%s)", static_cast<unsigned>(port), errno, strerror(errno));
        closeQuietly(fd);
        outError = errno == EPERM ? SyncErrorCode::kInternal : SyncErrorCode::kNetworkUnreachable;
        return -1;
    }

    // If port 0 was requested, find out what the kernel actually assigned.
    if (port == 0) {
        struct sockaddr_in assigned{};
        socklen_t len = sizeof(assigned);
        if (::getsockname(fd, reinterpret_cast<struct sockaddr*>(&assigned), &len) == 0) {
            port = ntohs(assigned.sin_port);
            ALOGI("kernel assigned dynamic port %u", static_cast<unsigned>(port));
        }
    }

    if (::listen(fd, backlog) < 0) {
        closeQuietly(fd);
        outError = SyncErrorCode::kInternal;
        return -1;
    }
    if (!setNonBlocking(fd)) {
        closeQuietly(fd);
        outError = SyncErrorCode::kInternal;
        return -1;
    }
    return fd;
}

/// Accepts one connection with a poll-based timeout.
///
/// @return the accepted fd in non-blocking mode, -1 on timeout or EINTR (the caller should
///         check its shutdown flag and retry), -2 on a fatal error (the listener should
///         stop). ECONNABORTED is treated as -1 because it means the specific client
///         vanished between poll and accept — the listener itself is healthy.
///
/// @param outPeer filled with the remote address as "ip:port", or "unknown" if name
///        resolution fails. This string is used for logging and for the confirmation
///        dialog shown to the user.
int acceptWithTimeout(int listenFd, int timeoutMs, std::string& outPeer) {
    if (listenFd < 0) {
        return -2;
    }
    const int ready = waitReady(listenFd, POLLIN, timeoutMs);
    if (ready == 0 || ready == -1) {
        return -1;  // timeout or EINTR: both mean "check the flag and come back"
    }
    if (ready < 0) {
        return -2;
    }

    struct sockaddr_in peer{};
    socklen_t peerLen = sizeof(peer);
    int fd = ::accept(listenFd, reinterpret_cast<struct sockaddr*>(&peer), &peerLen);
    while (fd < 0 && errno == EINTR) {
        fd = ::accept(listenFd, reinterpret_cast<struct sockaddr*>(&peer), &peerLen);
    }
    if (fd < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return -1;
        }
        // ECONNABORTED means that particular client vanished between the poll and the
        // accept. The listener is still healthy, so do not tear the server down for it.
        return errno == ECONNABORTED ? -1 : -2;
    }

    char ip[INET_ADDRSTRLEN] = {};
    if (::inet_ntop(AF_INET, &peer.sin_addr, ip, sizeof(ip)) != nullptr) {
        outPeer.assign(ip);
        outPeer.push_back(':');
        outPeer.append(std::to_string(ntohs(peer.sin_port)));
    } else {
        outPeer.assign("unknown");
    }

    setNonBlocking(fd);
    configureTcpSocket(fd);
    return fd;
}

/// Closes a socket fd, retrying on EINTR.
///
/// On Linux, close() always releases the fd even when it returns EINTR — the fd is
/// invalid from that moment regardless of the return value. The retry loop exists only
/// because POSIX leaves the behaviour unspecified and the loop costs nothing. A negative
/// fd is silently ignored so callers can unconditionally pass "the fd we might have".
void closeQuietly(int fd) {
    if (fd < 0) {
        return;
    }
    while (::close(fd) != 0 && errno == EINTR) {
        // Retry. On Linux close() never actually leaves the fd open after EINTR, but the
        // loop costs nothing and keeps the intent obvious.
    }
}

/// Performs a bidirectional shutdown (SHUT_RDWR) so the peer sees an orderly close
/// rather than a TCP RST. Best-effort: failures are silently ignored because the socket
/// is being closed anyway.
void shutdownQuietly(int fd) {
    if (fd >= 0) {
        ::shutdown(fd, SHUT_RDWR);
    }
}

}  // namespace homemoney::sync
