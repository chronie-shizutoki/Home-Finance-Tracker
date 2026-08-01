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

/// Idle time before the first keepalive probe. Short, because a phone that leaves the
/// network gives no FIN and we would otherwise hold the socket until the app is killed.
constexpr int kKeepAliveIdleSec = 15;
constexpr int kKeepAliveIntervalSec = 5;
constexpr int kKeepAliveProbes = 3;

/// Waits for readiness. Returns >0 ready, 0 timeout, -1 interrupted, -2 error.
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

/// Deadline-aware readiness wait shared by read and write.
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

std::int64_t monotonicNowMs() {
    struct timespec ts{};
    if (::clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return static_cast<std::int64_t>(ts.tv_sec) * 1000 +
           static_cast<std::int64_t>(ts.tv_nsec) / 1000000;
}

Deadline Deadline::afterMs(std::int64_t millis) {
    return Deadline{monotonicNowMs() + (millis < 0 ? 0 : millis)};
}

bool Deadline::expired() const {
    if (!bounded_) {
        return false;
    }
    return monotonicNowMs() >= at_;
}

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
        return IoResult{awaitReadiness(fd_, POLLIN, deadline_), 0};
    }
    if (errno == ECONNRESET || errno == EPIPE) {
        return IoResult{IoStatus::kClosed, 0};
    }
    return IoResult{IoStatus::kError, 0};
}

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
        return IoResult{awaitReadiness(fd_, POLLOUT, deadline_), 0};
    }
    if (errno == EPIPE || errno == ECONNRESET) {
        return IoResult{IoStatus::kClosed, 0};
    }
    return IoResult{IoStatus::kError, 0};
}

// -------------------------------------------------------------------- socket helpers

bool setNonBlocking(int fd) {
    const int flags = ::fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return false;
    }
    return ::fcntl(fd, F_SETFL, flags | O_NONBLOCK) == 0;
}

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

int connectWithTimeout(const char* ipv4, std::uint16_t port, int timeoutMs, SyncErrorCode& outError,
                       std::uint64_t netHandle) {
    outError = SyncErrorCode::kOk;

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

    // Pin the socket to the caller's network *before* connect: the routing decision is made
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

int createListeningSocket(std::uint16_t port, int backlog, SyncErrorCode& outError) {
    outError = SyncErrorCode::kOk;

    const int fd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        outError = SyncErrorCode::kInternal;
        return -1;
    }

    int on = 1;
    ::setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));

    struct sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port);

    if (::bind(fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) < 0) {
        ALOGE("listen socket bind(%u) failed: errno=%d (%s)", port, errno, strerror(errno));
        closeQuietly(fd);
        outError = SyncErrorCode::kNetworkUnreachable;
        return -1;
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

void closeQuietly(int fd) {
    if (fd < 0) {
        return;
    }
    while (::close(fd) != 0 && errno == EINTR) {
        // Retry. On Linux close() never actually leaves the fd open after EINTR, but the
        // loop costs nothing and keeps the intent obvious.
    }
}

void shutdownQuietly(int fd) {
    if (fd >= 0) {
        ::shutdown(fd, SHUT_RDWR);
    }
}

}  // namespace homemoney::sync
