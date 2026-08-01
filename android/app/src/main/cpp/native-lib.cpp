/**
 * JNI transport for the LAN sync engine.
 *
 * This file is the boundary between the JVM and the transport layer. It owns no protocol
 * knowledge of its own: framing lives in transport/frame_codec.h, socket behaviour in
 * transport/socket_stream.h, and the session semantics stay in Kotlin. What changed
 * relative to the original implementation, and why:
 *
 *  - The listener no longer handles connections itself. Handling one means calling into
 *    Kotlin to show a confirmation dialog and waiting up to a minute for the user; doing
 *    that on the accept thread meant every other device on the LAN silently timed out.
 *    Connections now go to a bounded worker pool, and a saturated server answers BUSY.
 *
 *  - Length prefixes are no longer trusted. A checksummed 32 byte header is validated
 *    before anything is allocated, so a corrupted or hostile frame is rejected instead of
 *    triggering a multi-megabyte allocation and an indefinite blocking read.
 *
 *  - Every socket operation has a deadline and survives EINTR/EAGAIN, so a signal or a
 *    momentary stall no longer aborts a sync, and a stalled peer cannot pin a thread.
 *
 *  - The client retries transient failures with jittered exponential backoff, and reports
 *    a structured error code instead of a bare null.
 *
 *  - v1 peers still work: the first four bytes tell the two dialects apart, and the client
 *    falls back automatically and remembers the result per peer.
 */

#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#include "protocol/sync_protocol.h"
#include "transport/frame_codec.h"
#include "transport/retry_policy.h"
#include "transport/socket_stream.h"
#include "transport/thread_pool.h"

#define TAG "NativeSyncEngine-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace homemoney::sync;

namespace {

// ------------------------------------------------------------------------- tunables

/// Concurrent inbound syncs. Small on purpose: each one may hold a database transaction.
constexpr std::size_t kServerWorkerThreads = 3;
/// Connections allowed to wait for a worker before the server starts answering BUSY.
constexpr std::size_t kServerQueueCapacity = 4;
/// Accept poll slice; also the worst case latency of stopServer().
constexpr int kAcceptPollMs = 200;
constexpr int kListenBacklog = 8;

/// A peer that connects and then says nothing is not worth a worker slot for long.
constexpr int kFirstFrameTimeoutMs = 15000;
/// Budget for the upper layer, which may be waiting on a user confirmation dialog.
constexpr int kHandlerTimeoutMs = 150000;
/// Idle time on an established connection before the server hangs up. Keepalive PINGs
/// refresh it, so a healthy but quiet link stays open across a weak-network stall.
constexpr int kIdleTimeoutMs = 90000;
/// Time allowed to push a reply back out.
constexpr int kReplyTimeoutMs = 20000;

constexpr int kDefaultConnectTimeoutMs = 8000;
constexpr int kDefaultIoTimeoutMs = 30000;
constexpr std::uint32_t kDefaultMaxAttempts = 4;

// --------------------------------------------------------------------------- globals

JavaVM* g_jvm = nullptr;

/// Global refs to the Kotlin engine and its class, plus the cached method ids. Resolving
/// them once at startServer keeps the per-frame path free of JNI lookups.
jobject g_engine_obj = nullptr;
jclass g_engine_cls = nullptr;
jmethodID g_mid_handle_frame = nullptr;
jmethodID g_mid_handle_legacy = nullptr;
std::mutex g_engine_mutex;

/// JNIEnv for the current worker thread, set by the pool's start hook.
thread_local JNIEnv* t_env = nullptr;
/// Last transport error on this thread, surfaced to Kotlin via lastErrorCode().
thread_local std::int32_t t_last_error = static_cast<std::int32_t>(SyncErrorCode::kOk);

std::atomic<int> g_connect_timeout_ms{kDefaultConnectTimeoutMs};
std::atomic<int> g_io_timeout_ms{kDefaultIoTimeoutMs};
std::atomic<std::uint32_t> g_max_attempts{kDefaultMaxAttempts};

/// Counters for the observability requirement. Cheap, lock free, dumped via transportStats.
struct Metrics {
    std::atomic<std::uint64_t> connectionsAccepted{0};
    std::atomic<std::uint64_t> connectionsRejectedBusy{0};
    std::atomic<std::uint64_t> framesIn{0};
    std::atomic<std::uint64_t> framesOut{0};
    std::atomic<std::uint64_t> bytesIn{0};
    std::atomic<std::uint64_t> bytesOut{0};
    std::atomic<std::uint64_t> crcErrors{0};
    std::atomic<std::uint64_t> protocolErrors{0};
    std::atomic<std::uint64_t> timeouts{0};
    std::atomic<std::uint64_t> clientRetries{0};
    std::atomic<std::uint64_t> legacySessions{0};
    std::atomic<std::uint64_t> v2Sessions{0};
};

Metrics g_metrics;

/// Remembers which dialect each peer speaks so the fallback probe happens at most once.
std::mutex g_peer_mutex;
std::unordered_map<std::string, std::uint8_t> g_peer_version;

// ----------------------------------------------------------------------- small utils

const char* errorName(SyncErrorCode code) {
    switch (code) {
        case SyncErrorCode::kOk: return "OK";
        case SyncErrorCode::kProtocolMismatch: return "PROTOCOL_MISMATCH";
        case SyncErrorCode::kAuthRejected: return "AUTH_REJECTED";
        case SyncErrorCode::kAuthTimeout: return "AUTH_TIMEOUT";
        case SyncErrorCode::kNetworkUnreachable: return "NETWORK_UNREACHABLE";
        case SyncErrorCode::kConnectTimeout: return "CONNECT_TIMEOUT";
        case SyncErrorCode::kIoTimeout: return "IO_TIMEOUT";
        case SyncErrorCode::kPeerClosed: return "PEER_CLOSED";
        case SyncErrorCode::kCrcMismatch: return "CRC_MISMATCH";
        case SyncErrorCode::kPayloadTooLarge: return "PAYLOAD_TOO_LARGE";
        case SyncErrorCode::kParseError: return "PARSE_ERROR";
        case SyncErrorCode::kApplyError: return "APPLY_ERROR";
        case SyncErrorCode::kBusy: return "BUSY";
        case SyncErrorCode::kCancelled: return "CANCELLED";
        case SyncErrorCode::kInternal: return "INTERNAL";
        case SyncErrorCode::kBadMagic: return "BAD_MAGIC";
        case SyncErrorCode::kUnknownOpcode: return "UNKNOWN_OPCODE";
    }
    return "UNKNOWN";
}

void recordError(SyncErrorCode code) {
    t_last_error = static_cast<std::int32_t>(code);
    switch (code) {
        case SyncErrorCode::kCrcMismatch:
            g_metrics.crcErrors.fetch_add(1, std::memory_order_relaxed);
            break;
        case SyncErrorCode::kIoTimeout:
        case SyncErrorCode::kConnectTimeout:
            g_metrics.timeouts.fetch_add(1, std::memory_order_relaxed);
            break;
        case SyncErrorCode::kBadMagic:
        case SyncErrorCode::kProtocolMismatch:
        case SyncErrorCode::kUnknownOpcode:
        case SyncErrorCode::kPayloadTooLarge:
            g_metrics.protocolErrors.fetch_add(1, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

/// Four byte big endian error body, so the peer can act on the code without a parser.
std::vector<std::uint8_t> errorPayload(SyncErrorCode code) {
    const std::uint32_t value = static_cast<std::uint32_t>(static_cast<std::int32_t>(code));
    return {
            static_cast<std::uint8_t>((value >> 24) & 0xFFu),
            static_cast<std::uint8_t>((value >> 16) & 0xFFu),
            static_cast<std::uint8_t>((value >> 8) & 0xFFu),
            static_cast<std::uint8_t>(value & 0xFFu),
    };
}

/// Session ids only need to be unlikely to collide between two phones on one LAN.
std::uint64_t newSessionId() {
    static std::atomic<std::uint64_t> counter{0};
    const std::uint64_t seq = counter.fetch_add(1, std::memory_order_relaxed);
    const std::uint64_t now = static_cast<std::uint64_t>(
            std::chrono::duration_cast<std::chrono::nanoseconds>(
                    std::chrono::steady_clock::now().time_since_epoch())
                    .count());
    return (now << 12) ^ (seq * 0x9E3779B97F4A7C15ULL);
}

RetryPolicy currentRetryPolicy() {
    RetryPolicy policy;
    policy.maxAttempts = g_max_attempts.load(std::memory_order_relaxed);
    policy.baseDelayMs = 250;
    policy.maxDelayMs = 8000;
    return policy;
}

std::uint8_t knownPeerVersion(const std::string& key) {
    std::lock_guard<std::mutex> lock(g_peer_mutex);
    const auto it = g_peer_version.find(key);
    return it == g_peer_version.end() ? 0 : it->second;
}

void rememberPeerVersion(const std::string& key, std::uint8_t version) {
    std::lock_guard<std::mutex> lock(g_peer_mutex);
    // A phone can be reinstalled with a newer build, so this is a hint, not a contract:
    // it only decides which dialect to try first.
    g_peer_version[key] = version;
}

// -------------------------------------------------------------- Kotlin upcall helpers

/// Converts a Kotlin byte[] result into a vector, or reports that it was null.
bool takeByteArray(JNIEnv* env, jbyteArray array, std::vector<std::uint8_t>& out) {
    if (array == nullptr) {
        return false;
    }
    const jsize length = env->GetArrayLength(array);
    out.resize(static_cast<std::size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte*>(out.data()));
    }
    return true;
}

/**
 * Hands a v2 frame to Kotlin.
 *
 * Returns false when Kotlin declined (null) or threw. The transport turns that into a
 * structured ERROR frame rather than dropping the connection silently, which is what made
 * "sync failed" impossible to diagnose before.
 */
bool callKotlinFrame(JNIEnv* env,
                     const std::string& peer,
                     const Frame& frame,
                     std::vector<std::uint8_t>& out) {
    jobject engine = nullptr;
    jmethodID mid = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        engine = g_engine_obj;
        mid = g_mid_handle_frame;
    }
    if (engine == nullptr || mid == nullptr) {
        LOGE("callKotlinFrame: engine not bound");
        return false;
    }

    jstring jpeer = env->NewStringUTF(peer.c_str());
    jbyteArray jpayload = env->NewByteArray(static_cast<jsize>(frame.payload.size()));
    if (jpeer == nullptr || jpayload == nullptr) {
        env->ExceptionClear();
        if (jpeer != nullptr) env->DeleteLocalRef(jpeer);
        if (jpayload != nullptr) env->DeleteLocalRef(jpayload);
        LOGE("callKotlinFrame: out of memory allocating JNI arguments");
        return false;
    }
    if (!frame.payload.empty()) {
        env->SetByteArrayRegion(jpayload, 0, static_cast<jsize>(frame.payload.size()),
                                reinterpret_cast<const jbyte*>(frame.payload.data()));
    }

    auto response = static_cast<jbyteArray>(env->CallObjectMethod(
            engine, mid, jpeer,
            static_cast<jint>(static_cast<std::uint8_t>(frame.header.opcode)),
            static_cast<jlong>(frame.header.sessionId),
            static_cast<jint>(frame.header.seq), jpayload));

    bool ok = false;
    if (env->ExceptionCheck()) {
        LOGE("callKotlinFrame: Kotlin threw while handling opcode 0x%02X",
             static_cast<unsigned>(frame.header.opcode));
        env->ExceptionDescribe();
        env->ExceptionClear();
    } else {
        ok = takeByteArray(env, response, out);
    }

    if (response != nullptr) {
        env->DeleteLocalRef(response);
    }
    env->DeleteLocalRef(jpayload);
    env->DeleteLocalRef(jpeer);
    return ok;
}

/// Legacy v1 upcall, kept byte-for-byte compatible with the old contract.
bool callKotlinLegacy(JNIEnv* env,
                      const std::string& peer,
                      const std::vector<std::uint8_t>& data,
                      std::vector<std::uint8_t>& out) {
    jobject engine = nullptr;
    jmethodID mid = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        engine = g_engine_obj;
        mid = g_mid_handle_legacy;
    }
    if (engine == nullptr || mid == nullptr) {
        LOGE("callKotlinLegacy: engine not bound");
        return false;
    }

    jstring jid = env->NewStringUTF(peer.c_str());
    jstring jname = env->NewStringUTF("Remote Device");
    jbyteArray jdata = env->NewByteArray(static_cast<jsize>(data.size()));
    if (jid == nullptr || jname == nullptr || jdata == nullptr) {
        env->ExceptionClear();
        if (jid != nullptr) env->DeleteLocalRef(jid);
        if (jname != nullptr) env->DeleteLocalRef(jname);
        if (jdata != nullptr) env->DeleteLocalRef(jdata);
        return false;
    }
    if (!data.empty()) {
        env->SetByteArrayRegion(jdata, 0, static_cast<jsize>(data.size()),
                                reinterpret_cast<const jbyte*>(data.data()));
    }

    auto response = static_cast<jbyteArray>(
            env->CallObjectMethod(engine, mid, jid, jname, jdata));

    bool ok = false;
    if (env->ExceptionCheck()) {
        LOGE("callKotlinLegacy: Kotlin threw");
        env->ExceptionDescribe();
        env->ExceptionClear();
    } else {
        ok = takeByteArray(env, response, out);
    }

    if (response != nullptr) {
        env->DeleteLocalRef(response);
    }
    env->DeleteLocalRef(jdata);
    env->DeleteLocalRef(jname);
    env->DeleteLocalRef(jid);
    return ok;
}

// ------------------------------------------------------------------ server instance

/**
 * One running server. Held by shared_ptr so that stopServer can hand the (possibly slow)
 * join off to a detached thread: a worker may be blocked waiting for the user to answer a
 * confirmation dialog, and blocking the caller on that would be an ANR.
 */
struct ServerInstance {
    std::atomic<bool> running{true};
    std::atomic<int> listenFd{-1};
    std::mutex connMutex;
    std::vector<int> activeConns;
    std::unique_ptr<ThreadPool> pool;
    std::thread acceptThread;

    void registerConn(int fd) {
        std::lock_guard<std::mutex> lock(connMutex);
        activeConns.push_back(fd);
    }

    void unregisterConn(int fd) {
        std::lock_guard<std::mutex> lock(connMutex);
        for (std::size_t i = 0; i < activeConns.size(); ++i) {
            if (activeConns[i] == fd) {
                activeConns.erase(activeConns.begin() + static_cast<std::ptrdiff_t>(i));
                return;
            }
        }
    }

    /// Unblocks every in-flight read/write so shutdown does not wait on a stalled peer.
    void shutdownAllConns() {
        std::lock_guard<std::mutex> lock(connMutex);
        for (const int fd : activeConns) {
            shutdownQuietly(fd);
        }
    }
};

std::mutex g_server_mutex;
std::shared_ptr<ServerInstance> g_server;

/// Sends an ERROR frame on a best-effort basis; the connection is going away regardless.
void sendErrorFrame(FdStream& stream, std::uint64_t sessionId, std::uint32_t seq,
                    SyncErrorCode code) {
    FrameHeader header{};
    header.opcode = Opcode::kError;
    header.sessionId = sessionId;
    header.seq = seq;
    const std::vector<std::uint8_t> body = errorPayload(code);
    stream.setDeadline(Deadline::afterMs(kReplyTimeoutMs));
    if (writeFrame(stream, header, body.data(), body.size()) == SyncErrorCode::kOk) {
        g_metrics.framesOut.fetch_add(1, std::memory_order_relaxed);
    }
}

/**
 * Serves one v2 connection.
 *
 * The loop is deliberately long lived: keeping the socket open across several frames is
 * what makes keepalive and, later, resumable chunk transfer possible. PING is answered
 * here rather than in Kotlin so a quiet link does not wake the JVM every few seconds.
 */
void serveV2(FdStream& stream,
             const std::string& peer,
             const std::uint8_t (&firstPrefix)[4],
             JNIEnv* env) {
    g_metrics.v2Sessions.fetch_add(1, std::memory_order_relaxed);

    std::uint8_t prefix[4] = {firstPrefix[0], firstPrefix[1], firstPrefix[2], firstPrefix[3]};
    bool havePrefix = true;

    for (;;) {
        if (!havePrefix) {
            stream.setDeadline(Deadline::afterMs(kIdleTimeoutMs));
            const SyncErrorCode next = readPrefix(stream, prefix);
            if (next == SyncErrorCode::kPeerClosed) {
                LOGD("[%s] peer closed the connection", peer.c_str());
                return;
            }
            if (next != SyncErrorCode::kOk) {
                LOGW("[%s] idle read failed: %s", peer.c_str(), errorName(next));
                recordError(next);
                return;
            }
        }
        havePrefix = false;

        Frame frame;
        stream.setDeadline(Deadline::afterMs(kFirstFrameTimeoutMs));
        const SyncErrorCode read = readFrameAfterPrefix(stream, prefix, frame);
        if (read != SyncErrorCode::kOk) {
            recordError(read);
            if (read == SyncErrorCode::kPeerClosed) {
                LOGD("[%s] peer closed mid frame", peer.c_str());
                return;
            }
            LOGW("[%s] frame rejected: %s", peer.c_str(), errorName(read));
            // Tell the peer why. A silent close is what made the old failures unreadable.
            sendErrorFrame(stream, 0, 0, read);
            return;
        }

        g_metrics.framesIn.fetch_add(1, std::memory_order_relaxed);
        g_metrics.bytesIn.fetch_add(kFrameHeaderSize + frame.payload.size(),
                                    std::memory_order_relaxed);
        LOGD("[%s] rx opcode=0x%02X seq=%u len=%u", peer.c_str(),
             static_cast<unsigned>(frame.header.opcode), frame.header.seq,
             frame.header.payloadLen);

        if (frame.header.opcode == Opcode::kBye) {
            LOGD("[%s] graceful bye", peer.c_str());
            return;
        }

        if (frame.header.opcode == Opcode::kPing) {
            FrameHeader pong{};
            pong.opcode = Opcode::kPong;
            pong.sessionId = frame.header.sessionId;
            pong.seq = frame.header.seq;
            stream.setDeadline(Deadline::afterMs(kReplyTimeoutMs));
            const SyncErrorCode written = writeFrame(stream, pong);
            if (written != SyncErrorCode::kOk) {
                recordError(written);
                return;
            }
            g_metrics.framesOut.fetch_add(1, std::memory_order_relaxed);
            continue;
        }

        if (!requiresUpperLayer(frame.header.opcode)) {
            // An ack or a pong arriving at the server means the peer's state machine is
            // confused; say so instead of quietly ignoring it.
            LOGW("[%s] unexpected opcode 0x%02X from peer", peer.c_str(),
                 static_cast<unsigned>(frame.header.opcode));
            sendErrorFrame(stream, frame.header.sessionId, frame.header.seq,
                           SyncErrorCode::kUnknownOpcode);
            return;
        }

        // The upper layer may block here for as long as the user takes to answer.
        std::vector<std::uint8_t> response;
        stream.setDeadline(Deadline::afterMs(kHandlerTimeoutMs));
        const bool accepted = callKotlinFrame(env, peer, frame, response);
        if (!accepted) {
            LOGI("[%s] upper layer declined opcode 0x%02X", peer.c_str(),
                 static_cast<unsigned>(frame.header.opcode));
            sendErrorFrame(stream, frame.header.sessionId, frame.header.seq,
                           SyncErrorCode::kCancelled);
            return;
        }
        if (response.size() > kMaxPayloadSize) {
            LOGE("[%s] upper layer produced %zu bytes, over the frame cap", peer.c_str(),
                 response.size());
            sendErrorFrame(stream, frame.header.sessionId, frame.header.seq,
                           SyncErrorCode::kPayloadTooLarge);
            return;
        }

        FrameHeader ack{};
        ack.opcode = ackOpcodeFor(frame.header.opcode);
        ack.sessionId = frame.header.sessionId;
        ack.seq = frame.header.seq;
        stream.setDeadline(Deadline::afterMs(kReplyTimeoutMs));
        const SyncErrorCode written =
                writeFrame(stream, ack, response.data(), response.size());
        if (written != SyncErrorCode::kOk) {
            LOGW("[%s] failed to send ack: %s", peer.c_str(), errorName(written));
            recordError(written);
            return;
        }
        g_metrics.framesOut.fetch_add(1, std::memory_order_relaxed);
        g_metrics.bytesOut.fetch_add(kFrameHeaderSize + response.size(),
                                     std::memory_order_relaxed);
        LOGD("[%s] tx opcode=0x%02X len=%zu", peer.c_str(),
             static_cast<unsigned>(ack.opcode), response.size());
    }
}

/// Serves one legacy v1 connection: a single length-prefixed request and response.
void serveV1(FdStream& stream,
             const std::string& peer,
             const std::uint8_t (&prefix)[4],
             JNIEnv* env) {
    g_metrics.legacySessions.fetch_add(1, std::memory_order_relaxed);
    LOGI("[%s] legacy v1 peer", peer.c_str());

    std::vector<std::uint8_t> request;
    stream.setDeadline(Deadline::afterMs(kFirstFrameTimeoutMs));
    const SyncErrorCode read = readLegacyBody(stream, prefix, request);
    if (read != SyncErrorCode::kOk) {
        LOGW("[%s] legacy read failed: %s", peer.c_str(), errorName(read));
        recordError(read);
        return;
    }
    g_metrics.bytesIn.fetch_add(4 + request.size(), std::memory_order_relaxed);

    std::vector<std::uint8_t> response;
    stream.setDeadline(Deadline::afterMs(kHandlerTimeoutMs));
    const bool accepted = callKotlinLegacy(env, peer, request, response);

    stream.setDeadline(Deadline::afterMs(kReplyTimeoutMs));
    if (!accepted) {
        // The old protocol has no error frame; a zero length reply is the only "no" it
        // understands, and the old client already handles it.
        LOGI("[%s] legacy request declined", peer.c_str());
        writeLegacyMessage(stream, nullptr, 0);
        return;
    }
    const SyncErrorCode written =
            writeLegacyMessage(stream, response.data(), response.size());
    if (written != SyncErrorCode::kOk) {
        LOGW("[%s] legacy reply failed: %s", peer.c_str(), errorName(written));
        recordError(written);
        return;
    }
    g_metrics.bytesOut.fetch_add(4 + response.size(), std::memory_order_relaxed);
}

/// Full lifecycle of one accepted connection, run on a pool worker.
void handleConnection(const std::shared_ptr<ServerInstance>& instance, int fd,
                      std::string peer) {
    JNIEnv* env = t_env;
    if (env == nullptr) {
        LOGE("[%s] worker has no JNIEnv, dropping connection", peer.c_str());
        closeQuietly(fd);
        return;
    }

    instance->registerConn(fd);
    FdStream stream(fd, Deadline::afterMs(kFirstFrameTimeoutMs));

    std::uint8_t prefix[4] = {};
    const SyncErrorCode prefixResult = readPrefix(stream, prefix);
    if (prefixResult != SyncErrorCode::kOk) {
        LOGW("[%s] no usable prefix: %s", peer.c_str(), errorName(prefixResult));
        recordError(prefixResult);
    } else if (looksLikeV2Frame(prefix)) {
        serveV2(stream, peer, prefix, env);
    } else {
        serveV1(stream, peer, prefix, env);
    }

    instance->unregisterConn(fd);
    shutdownQuietly(fd);
    closeQuietly(fd);
}

/// Answers a connection we have no capacity for, so the client can back off and retry.
void rejectBusy(int fd) {
    FdStream stream(fd, Deadline::afterMs(2000));
    sendErrorFrame(stream, 0, 0, SyncErrorCode::kBusy);
    shutdownQuietly(fd);
    closeQuietly(fd);
}

void acceptLoop(std::shared_ptr<ServerInstance> instance, int port) {
    SyncErrorCode error = SyncErrorCode::kOk;
    const int listenFd = createListeningSocket(static_cast<std::uint16_t>(port),
                                               kListenBacklog, error);
    if (listenFd < 0) {
        LOGE("failed to listen on %d: %s", port, errorName(error));
        instance->running = false;
        return;
    }
    instance->listenFd = listenFd;
    LOGI("sync server listening on %d (workers=%zu queue=%zu)", port, kServerWorkerThreads,
         kServerQueueCapacity);

    while (instance->running.load(std::memory_order_relaxed)) {
        std::string peer;
        const int clientFd = acceptWithTimeout(listenFd, kAcceptPollMs, peer);
        if (clientFd == -1) {
            continue;  // timeout or a client that vanished; check the flag and go again
        }
        if (clientFd == -2) {
            if (instance->running.load(std::memory_order_relaxed)) {
                LOGE("accept failed fatally, stopping the listener");
            }
            break;
        }
        if (!instance->running.load(std::memory_order_relaxed)) {
            closeQuietly(clientFd);
            break;
        }

        g_metrics.connectionsAccepted.fetch_add(1, std::memory_order_relaxed);
        LOGD("accepted %s", peer.c_str());

        std::shared_ptr<ServerInstance> captured = instance;
        const bool posted = instance->pool->tryPost(
                [captured, clientFd, peer]() { handleConnection(captured, clientFd, peer); });
        if (!posted) {
            // Saturated. An explicit BUSY is retryable and diagnosable; the old code just
            // let the connection sit in the backlog until the client gave up.
            g_metrics.connectionsRejectedBusy.fetch_add(1, std::memory_order_relaxed);
            LOGW("rejecting %s: worker pool saturated", peer.c_str());
            rejectBusy(clientFd);
        }
    }

    const int fd = instance->listenFd.exchange(-1);
    closeQuietly(fd);
    LOGI("sync server accept loop exited");
}

// ------------------------------------------------------------------- client transport

struct ClientOutcome {
    SyncErrorCode error = SyncErrorCode::kOk;
    std::vector<std::uint8_t> response;
};

/// One v2 request/response exchange over an already connected socket.
SyncErrorCode exchangeV2(FdStream& stream,
                         const std::vector<std::uint8_t>& request,
                         std::vector<std::uint8_t>& response) {
    FrameHeader header{};
    header.opcode = Opcode::kCommit;  // whole-payload exchange; chunking lands in phase 4
    header.sessionId = newSessionId();
    header.seq = 1;
    header.flags = kFlagRequireAck;

    const SyncErrorCode written =
            writeFrame(stream, header, request.data(), request.size());
    if (written != SyncErrorCode::kOk) {
        return written;
    }
    g_metrics.framesOut.fetch_add(1, std::memory_order_relaxed);
    g_metrics.bytesOut.fetch_add(kFrameHeaderSize + request.size(),
                                 std::memory_order_relaxed);

    Frame reply;
    const SyncErrorCode read = readFrame(stream, reply);
    if (read != SyncErrorCode::kOk) {
        return read;
    }
    g_metrics.framesIn.fetch_add(1, std::memory_order_relaxed);
    g_metrics.bytesIn.fetch_add(kFrameHeaderSize + reply.payload.size(),
                                std::memory_order_relaxed);

    if (reply.header.opcode == Opcode::kError) {
        // The peer told us why it failed; propagate that instead of a generic failure.
        SyncErrorCode remote = SyncErrorCode::kInternal;
        if (reply.payload.size() >= 4) {
            const std::uint32_t raw = (static_cast<std::uint32_t>(reply.payload[0]) << 24) |
                                      (static_cast<std::uint32_t>(reply.payload[1]) << 16) |
                                      (static_cast<std::uint32_t>(reply.payload[2]) << 8) |
                                      static_cast<std::uint32_t>(reply.payload[3]);
            remote = static_cast<SyncErrorCode>(static_cast<std::int32_t>(raw));
        }
        return remote;
    }
    if (reply.header.opcode != Opcode::kCommitAck) {
        return SyncErrorCode::kProtocolMismatch;
    }
    if (reply.header.sessionId != header.sessionId) {
        // A reply for a different session means frames are being crossed somewhere.
        return SyncErrorCode::kProtocolMismatch;
    }

    response = std::move(reply.payload);
    return SyncErrorCode::kOk;
}

/// One legacy v1 exchange over an already connected socket.
SyncErrorCode exchangeV1(FdStream& stream,
                         const std::vector<std::uint8_t>& request,
                         std::vector<std::uint8_t>& response) {
    const SyncErrorCode written =
            writeLegacyMessage(stream, request.data(), request.size());
    if (written != SyncErrorCode::kOk) {
        return written;
    }
    g_metrics.bytesOut.fetch_add(4 + request.size(), std::memory_order_relaxed);

    std::uint8_t prefix[4] = {};
    const SyncErrorCode prefixResult = readPrefix(stream, prefix);
    if (prefixResult != SyncErrorCode::kOk) {
        return prefixResult;
    }
    const SyncErrorCode read = readLegacyBody(stream, prefix, response);
    if (read != SyncErrorCode::kOk) {
        return read;
    }
    g_metrics.bytesIn.fetch_add(4 + response.size(), std::memory_order_relaxed);
    if (response.empty()) {
        // The v1 "no" - the remote user declined. Not retryable.
        return SyncErrorCode::kCancelled;
    }
    return SyncErrorCode::kOk;
}

/// Connects and performs one exchange in the requested dialect.
SyncErrorCode attemptOnce(const std::string& address,
                          int port,
                          std::uint8_t dialect,
                          const std::vector<std::uint8_t>& request,
                          std::vector<std::uint8_t>& response) {
    SyncErrorCode error = SyncErrorCode::kOk;
    const int fd = connectWithTimeout(address.c_str(), static_cast<std::uint16_t>(port),
                                      g_connect_timeout_ms.load(std::memory_order_relaxed),
                                      error);
    if (fd < 0) {
        return error;
    }

    FdStream stream(fd, Deadline::afterMs(g_io_timeout_ms.load(std::memory_order_relaxed)));
    const SyncErrorCode result = dialect == 1 ? exchangeV1(stream, request, response)
                                              : exchangeV2(stream, request, response);
    shutdownQuietly(fd);
    closeQuietly(fd);
    return result;
}

/**
 * Full client call: dialect negotiation plus retry with jittered backoff.
 *
 * The negotiation is cheap because the two framings are distinguishable at byte zero. A v1
 * server reads our magic as a 1.2 GB length, refuses it and closes, so the very first read
 * fails with kPeerClosed or kBadMagic - which is the signal to drop to v1 and remember it.
 */
ClientOutcome performSyncWithRetry(const std::string& address,
                                   int port,
                                   const std::vector<std::uint8_t>& request) {
    ClientOutcome outcome;
    const RetryPolicy policy = currentRetryPolicy();
    const std::string peerKey = address + ":" + std::to_string(port);

    std::uint8_t dialect = knownPeerVersion(peerKey);
    if (dialect == 0) {
        dialect = kProtocolVersion;
    }
    bool triedFallback = false;
    std::uint32_t randomState =
            static_cast<std::uint32_t>(monotonicNowMs()) ^ 0xA5A5A5A5u;

    for (std::uint32_t attempt = 1;; ++attempt) {
        outcome.response.clear();
        const SyncErrorCode result =
                attemptOnce(address, port, dialect, request, outcome.response);
        if (result == SyncErrorCode::kOk) {
            rememberPeerVersion(peerKey, dialect);
            outcome.error = SyncErrorCode::kOk;
            return outcome;
        }

        LOGW("sync attempt %u to %s failed: %s (dialect v%u)", attempt, peerKey.c_str(),
             errorName(result), static_cast<unsigned>(dialect));
        outcome.error = result;

        // Dialect probe: only worth doing once, and only for the failures a legacy server
        // actually produces. Anything else is a genuine transport problem.
        const bool looksLikeLegacyServer = result == SyncErrorCode::kBadMagic ||
                                           result == SyncErrorCode::kPeerClosed ||
                                           result == SyncErrorCode::kProtocolMismatch;
        if (dialect != 1 && !triedFallback && looksLikeLegacyServer) {
            LOGI("%s does not speak v2, falling back to legacy framing", peerKey.c_str());
            triedFallback = true;
            dialect = 1;
            rememberPeerVersion(peerKey, 1);
            continue;  // immediate retry, no backoff: this is a negotiation, not a failure
        }
        // A v1 peer that was upgraded will start rejecting legacy frames; re-probe v2 once.
        if (dialect == 1 && !triedFallback && result == SyncErrorCode::kPayloadTooLarge) {
            LOGI("%s appears upgraded, retrying with v2 framing", peerKey.c_str());
            triedFallback = true;
            dialect = kProtocolVersion;
            rememberPeerVersion(peerKey, kProtocolVersion);
            continue;
        }

        if (!shouldRetry(policy, result, attempt)) {
            return outcome;
        }

        const std::uint32_t delay =
                jitteredDelayMs(policy, attempt - 1, nextRandom(randomState));
        g_metrics.clientRetries.fetch_add(1, std::memory_order_relaxed);
        LOGI("retrying %s in %u ms (attempt %u/%u)", peerKey.c_str(), delay, attempt + 1,
             policy.maxAttempts);
        std::this_thread::sleep_for(std::chrono::milliseconds(delay));
    }
}

// ------------------------------------------------------- persistent client connections

/**
 * A client socket that outlives a single frame.
 *
 * performSync above is a one-shot: connect, send one frame, read one reply, hang up. That
 * is enough for a legacy v1 peer but it cannot express the v2 handshake, which is five
 * request/response pairs that must share one session - and one socket, because the session
 * id only means anything for as long as the responder keeps the session open.
 *
 * The session semantics live in Kotlin (that is where the protobuf schema is; the NDK
 * build deliberately links no protobuf runtime), so what native owes it is exactly this:
 * a connection it can hold open and push individual frames through. Nothing here knows
 * what an opcode means.
 */
struct ClientConnection {
    int fd = -1;
    std::string peerKey;
    /// Serialises exchanges. The protocol is strictly request/response, so two concurrent
    /// exchanges on one socket would interleave frames and desynchronise both ends.
    std::mutex io;
    /// Set once the link is known to be unusable, so later calls fail fast instead of
    /// blocking on a socket that will never answer.
    bool broken = false;
};

std::mutex g_client_mutex;
std::unordered_map<std::int64_t, std::shared_ptr<ClientConnection>> g_client_conns;
/// Handles start at 1 so that 0 can mean "no connection" on the Kotlin side.
std::atomic<std::int64_t> g_next_client_handle{1};

std::shared_ptr<ClientConnection> findClientConnection(std::int64_t handle) {
    std::lock_guard<std::mutex> lock(g_client_mutex);
    const auto it = g_client_conns.find(handle);
    return it == g_client_conns.end() ? nullptr : it->second;
}

/**
 * One request/response over an already open client connection.
 *
 * Framing only: the opcode, flags, session id and body all come from the caller, and the
 * reply is handed back exactly as it arrived. The deadline is per exchange rather than per
 * connection because the AUTH round trip legitimately blocks for as long as the user on
 * the other phone takes to tap "accept", while a CHUNK that stalls for a minute is dead.
 */
SyncErrorCode exchangeOnConnection(ClientConnection& conn,
                                   Opcode opcode,
                                   std::uint16_t flags,
                                   std::uint64_t sessionId,
                                   std::uint32_t seq,
                                   const std::uint8_t* payload,
                                   std::size_t payloadLen,
                                   int timeoutMs,
                                   Frame& reply) {
    FdStream stream(conn.fd, Deadline::afterMs(timeoutMs));

    FrameHeader header{};
    header.opcode = opcode;
    header.flags = flags;
    header.sessionId = sessionId;
    header.seq = seq;

    const SyncErrorCode written = writeFrame(stream, header, payload, payloadLen);
    if (written != SyncErrorCode::kOk) {
        return written;
    }
    g_metrics.framesOut.fetch_add(1, std::memory_order_relaxed);
    g_metrics.bytesOut.fetch_add(kFrameHeaderSize + payloadLen, std::memory_order_relaxed);

    const SyncErrorCode read = readFrame(stream, reply);
    if (read != SyncErrorCode::kOk) {
        return read;
    }
    g_metrics.framesIn.fetch_add(1, std::memory_order_relaxed);
    g_metrics.bytesIn.fetch_add(kFrameHeaderSize + reply.payload.size(),
                                std::memory_order_relaxed);
    return SyncErrorCode::kOk;
}

/// Serialises a frame into the flat `header || payload` form Kotlin decodes.
std::vector<std::uint8_t> flattenFrame(const FrameHeader& header,
                                       const std::uint8_t* payload,
                                       std::size_t payloadLen) {
    FrameHeader copy = header;
    copy.version = kProtocolVersion;
    copy.payloadLen = static_cast<std::uint32_t>(payloadLen);
    copy.payloadCrc32 = payloadLen == 0 ? 0u : crc32c(payload, payloadLen);

    const FrameHeaderBytes encoded = encodeFrameHeader(copy);
    std::vector<std::uint8_t> out;
    out.reserve(kFrameHeaderSize + payloadLen);
    out.insert(out.end(), encoded.begin(), encoded.end());
    if (payloadLen > 0) {
        out.insert(out.end(), payload, payload + payloadLen);
    }
    return out;
}

/**
 * A locally generated ERROR frame.
 *
 * A transport failure is reported to Kotlin as a frame rather than as null so that the
 * caller has exactly one shape to handle. It reads the four byte code out of an ERROR
 * body the same way whether the peer sent it or the socket died on the way there, which
 * is what stops "sync failed" from collapsing back into an untyped null.
 */
std::vector<std::uint8_t> localErrorFrame(SyncErrorCode code,
                                          std::uint64_t sessionId,
                                          std::uint32_t seq) {
    const std::vector<std::uint8_t> body = errorPayload(code);
    FrameHeader header{};
    header.opcode = Opcode::kError;
    header.sessionId = sessionId;
    header.seq = seq;
    return flattenFrame(header, body.data(), body.size());
}

}  // namespace

// ------------------------------------------------------------------------ JNI surface

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_startServer(JNIEnv* env,
                                                                  jobject obj,
                                                                  jint port) {
    {
        std::lock_guard<std::mutex> lock(g_server_mutex);
        if (g_server && g_server->running.load(std::memory_order_relaxed)) {
            return JNI_TRUE;
        }
    }

    // Bind the Kotlin engine and resolve the upcalls once, here, where we still have a
    // guaranteed-valid JNIEnv and class loader. Worker threads attached later cannot see
    // application classes through FindClass, which is a classic JNI trap.
    {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        if (g_engine_obj != nullptr) {
            env->DeleteGlobalRef(g_engine_obj);
            g_engine_obj = nullptr;
        }
        if (g_engine_cls != nullptr) {
            env->DeleteGlobalRef(g_engine_cls);
            g_engine_cls = nullptr;
        }

        jclass localCls = env->GetObjectClass(obj);
        if (localCls == nullptr) {
            LOGE("startServer: cannot resolve the engine class");
            return JNI_FALSE;
        }
        g_engine_cls = static_cast<jclass>(env->NewGlobalRef(localCls));
        env->DeleteLocalRef(localCls);
        g_engine_obj = env->NewGlobalRef(obj);

        g_mid_handle_frame = env->GetMethodID(g_engine_cls, "handleIncomingFrame",
                                              "(Ljava/lang/String;IJI[B)[B");
        if (g_mid_handle_frame == nullptr) {
            env->ExceptionClear();
            LOGE("startServer: handleIncomingFrame is missing");
        }
        g_mid_handle_legacy = env->GetMethodID(g_engine_cls, "handleIncomingSyncRequest",
                                               "(Ljava/lang/String;Ljava/lang/String;[B)[B");
        if (g_mid_handle_legacy == nullptr) {
            env->ExceptionClear();
            LOGE("startServer: handleIncomingSyncRequest is missing");
        }
        if (g_mid_handle_frame == nullptr && g_mid_handle_legacy == nullptr) {
            return JNI_FALSE;
        }
    }

    auto instance = std::make_shared<ServerInstance>();
    instance->pool = std::make_unique<ThreadPool>(
            kServerWorkerThreads, kServerQueueCapacity,
            []() {
                JNIEnv* threadEnv = nullptr;
                JavaVMAttachArgs args{JNI_VERSION_1_6, "SyncWorker", nullptr};
                if (g_jvm->AttachCurrentThread(&threadEnv, &args) == JNI_OK) {
                    t_env = threadEnv;
                } else {
                    LOGE("worker failed to attach to the JVM");
                    t_env = nullptr;
                }
            },
            []() {
                if (t_env != nullptr) {
                    g_jvm->DetachCurrentThread();
                    t_env = nullptr;
                }
            });

    instance->acceptThread = std::thread(acceptLoop, instance, static_cast<int>(port));

    {
        std::lock_guard<std::mutex> lock(g_server_mutex);
        g_server = instance;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_stopServer(JNIEnv* /*env*/,
                                                                 jobject /*obj*/) {
    std::shared_ptr<ServerInstance> instance;
    {
        std::lock_guard<std::mutex> lock(g_server_mutex);
        instance = g_server;
        g_server.reset();
    }
    if (!instance) {
        return;
    }

    instance->running = false;
    const int fd = instance->listenFd.exchange(-1);
    shutdownQuietly(fd);
    closeQuietly(fd);
    // Unblock any worker sitting in a read so it notices the shutdown immediately.
    instance->shutdownAllConns();

    // Join off the calling thread: a worker may still be waiting on a user confirmation
    // dialog, and blocking the UI thread on that for a minute would be an ANR.
    std::thread([instance]() mutable {
        if (instance->acceptThread.joinable()) {
            instance->acceptThread.join();
        }
        instance->pool.reset();
        LOGI("sync server fully stopped");
    }).detach();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_performSync(JNIEnv* env,
                                                                  jobject /*obj*/,
                                                                  jstring address,
                                                                  jint port,
                                                                  jbyteArray data) {
    t_last_error = static_cast<std::int32_t>(SyncErrorCode::kOk);

    if (address == nullptr || data == nullptr) {
        recordError(SyncErrorCode::kInternal);
        return nullptr;
    }

    const char* nativeAddress = env->GetStringUTFChars(address, nullptr);
    if (nativeAddress == nullptr) {
        recordError(SyncErrorCode::kInternal);
        return nullptr;
    }
    const std::string addressCopy(nativeAddress);
    env->ReleaseStringUTFChars(address, nativeAddress);

    const jsize dataLen = env->GetArrayLength(data);
    std::vector<std::uint8_t> request(static_cast<std::size_t>(dataLen));
    if (dataLen > 0) {
        env->GetByteArrayRegion(data, 0, dataLen, reinterpret_cast<jbyte*>(request.data()));
    }
    if (request.size() > kMaxPayloadSize) {
        LOGE("performSync: payload of %zu bytes exceeds the frame cap", request.size());
        recordError(SyncErrorCode::kPayloadTooLarge);
        return nullptr;
    }

    const ClientOutcome outcome = performSyncWithRetry(addressCopy, port, request);
    if (outcome.error != SyncErrorCode::kOk) {
        recordError(outcome.error);
        LOGE("performSync to %s:%d failed: %s", addressCopy.c_str(), port,
             errorName(outcome.error));
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(outcome.response.size()));
    if (result == nullptr) {
        env->ExceptionClear();
        recordError(SyncErrorCode::kInternal);
        return nullptr;
    }
    if (!outcome.response.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(outcome.response.size()),
                                reinterpret_cast<const jbyte*>(outcome.response.data()));
    }
    return result;
}

/**
 * Opens a client connection and returns an opaque handle, or 0.
 *
 * Kotlin owns the lifetime from here: every handle must be given back to
 * closeSyncConnection, including on the failure paths, or the fd leaks for the life of
 * the process.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_openSyncConnection(
        JNIEnv* env, jobject /*obj*/, jstring address, jint port, jint connectTimeoutMs) {
    t_last_error = static_cast<std::int32_t>(SyncErrorCode::kOk);

    if (address == nullptr) {
        recordError(SyncErrorCode::kInternal);
        return 0;
    }
    const char* nativeAddress = env->GetStringUTFChars(address, nullptr);
    if (nativeAddress == nullptr) {
        recordError(SyncErrorCode::kInternal);
        return 0;
    }
    const std::string addressCopy(nativeAddress);
    env->ReleaseStringUTFChars(address, nativeAddress);

    const int configured = g_connect_timeout_ms.load(std::memory_order_relaxed);
    // A non-positive value means "use whatever configureTransport set"; anything else is
    // clamped for the same reason configureTransport clamps.
    const int timeout = connectTimeoutMs <= 0 ? configured
                        : (connectTimeoutMs < 500 ? 500
                                                  : (connectTimeoutMs > 60000 ? 60000
                                                                              : connectTimeoutMs));

    SyncErrorCode error = SyncErrorCode::kOk;
    const int fd = connectWithTimeout(addressCopy.c_str(), static_cast<std::uint16_t>(port),
                                      timeout, error);
    if (fd < 0) {
        recordError(error);
        LOGE("openSyncConnection to %s:%d failed: %s", addressCopy.c_str(), port,
             errorName(error));
        return 0;
    }

    auto conn = std::make_shared<ClientConnection>();
    conn->fd = fd;
    conn->peerKey = addressCopy + ":" + std::to_string(port);

    const std::int64_t handle = g_next_client_handle.fetch_add(1, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(g_client_mutex);
        g_client_conns.emplace(handle, std::move(conn));
    }
    g_metrics.v2Sessions.fetch_add(1, std::memory_order_relaxed);
    LOGD("openSyncConnection %s -> handle %lld", addressCopy.c_str(),
         static_cast<long long>(handle));
    return static_cast<jlong>(handle);
}

/**
 * Sends one frame on an open connection and returns the reply as `header || payload`.
 *
 * Returns null only when the call itself is malformed - an unknown handle, an unknown
 * opcode, an oversized body. Every transport failure comes back as a locally generated
 * ERROR frame instead, so the caller decodes one shape and reads the reason out of it.
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_syncExchange(
        JNIEnv* env, jobject /*obj*/, jlong handle, jint opcode, jint flags, jlong sessionId,
        jint seq, jbyteArray payload, jint timeoutMs) {
    t_last_error = static_cast<std::int32_t>(SyncErrorCode::kOk);

    const auto rawOpcode = static_cast<std::uint8_t>(opcode & 0xFF);
    if (!isKnownOpcode(rawOpcode)) {
        recordError(SyncErrorCode::kUnknownOpcode);
        LOGE("syncExchange: opcode 0x%02X is not part of the protocol",
             static_cast<unsigned>(rawOpcode));
        return nullptr;
    }

    std::vector<std::uint8_t> body;
    if (payload != nullptr) {
        const jsize length = env->GetArrayLength(payload);
        body.resize(static_cast<std::size_t>(length));
        if (length > 0) {
            env->GetByteArrayRegion(payload, 0, length, reinterpret_cast<jbyte*>(body.data()));
        }
    }
    if (body.size() > kMaxPayloadSize) {
        recordError(SyncErrorCode::kPayloadTooLarge);
        LOGE("syncExchange: body of %zu bytes exceeds the frame cap", body.size());
        return nullptr;
    }

    const std::shared_ptr<ClientConnection> conn = findClientConnection(handle);
    if (!conn) {
        recordError(SyncErrorCode::kInternal);
        LOGE("syncExchange: handle %lld is not open", static_cast<long long>(handle));
        return nullptr;
    }

    const int configured = g_io_timeout_ms.load(std::memory_order_relaxed);
    const int deadlineMs = timeoutMs <= 0 ? configured
                           : (timeoutMs < 1000 ? 1000
                                               : (timeoutMs > 300000 ? 300000 : timeoutMs));

    std::vector<std::uint8_t> flat;
    {
        std::lock_guard<std::mutex> lock(conn->io);
        if (conn->broken) {
            flat = localErrorFrame(SyncErrorCode::kPeerClosed,
                                   static_cast<std::uint64_t>(sessionId),
                                   static_cast<std::uint32_t>(seq));
        } else {
            Frame reply;
            const SyncErrorCode result = exchangeOnConnection(
                    *conn, static_cast<Opcode>(rawOpcode),
                    static_cast<std::uint16_t>(flags & 0xFFFF),
                    static_cast<std::uint64_t>(sessionId), static_cast<std::uint32_t>(seq),
                    body.empty() ? nullptr : body.data(), body.size(), deadlineMs, reply);
            if (result == SyncErrorCode::kOk) {
                flat = flattenFrame(reply.header, reply.payload.data(), reply.payload.size());
            } else {
                // The stream is out of step once an exchange fails mid frame, so the
                // connection is retired rather than reused for the next opcode.
                conn->broken = true;
                recordError(result);
                LOGW("syncExchange %s op=0x%02X failed: %s", conn->peerKey.c_str(),
                     static_cast<unsigned>(rawOpcode), errorName(result));
                flat = localErrorFrame(result, static_cast<std::uint64_t>(sessionId),
                                       static_cast<std::uint32_t>(seq));
            }
        }
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(flat.size()));
    if (result == nullptr) {
        env->ExceptionClear();
        recordError(SyncErrorCode::kInternal);
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(flat.size()),
                            reinterpret_cast<const jbyte*>(flat.data()));
    return result;
}

/// Closes a client connection. Safe to call twice and safe to call with an unknown handle.
extern "C" JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_closeSyncConnection(JNIEnv* /*env*/,
                                                                          jobject /*obj*/,
                                                                          jlong handle) {
    std::shared_ptr<ClientConnection> conn;
    {
        std::lock_guard<std::mutex> lock(g_client_mutex);
        const auto it = g_client_conns.find(handle);
        if (it == g_client_conns.end()) {
            return;
        }
        conn = it->second;
        g_client_conns.erase(it);
    }

    // Taking the io lock means a close that races an in-flight exchange waits for it
    // rather than pulling the fd out from under a blocking read.
    std::lock_guard<std::mutex> lock(conn->io);
    conn->broken = true;
    const int fd = conn->fd;
    conn->fd = -1;
    shutdownQuietly(fd);
    closeQuietly(fd);
    LOGD("closeSyncConnection %lld", static_cast<long long>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_configureTransport(
        JNIEnv* /*env*/, jobject /*obj*/, jint connectTimeoutMs, jint ioTimeoutMs,
        jint maxAttempts) {
    // Clamp rather than trust: a zero timeout would busy-spin and a huge one would hang.
    const int connect = connectTimeoutMs < 500 ? 500
                        : (connectTimeoutMs > 60000 ? 60000 : connectTimeoutMs);
    const int io = ioTimeoutMs < 1000 ? 1000 : (ioTimeoutMs > 300000 ? 300000 : ioTimeoutMs);
    const int attempts = maxAttempts < 1 ? 1 : (maxAttempts > 10 ? 10 : maxAttempts);

    g_connect_timeout_ms.store(connect, std::memory_order_relaxed);
    g_io_timeout_ms.store(io, std::memory_order_relaxed);
    g_max_attempts.store(static_cast<std::uint32_t>(attempts), std::memory_order_relaxed);
    LOGI("transport configured: connect=%dms io=%dms attempts=%d", connect, io, attempts);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_lastErrorCode(JNIEnv* /*env*/,
                                                                    jobject /*obj*/) {
    return static_cast<jint>(t_last_error);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_transportStats(JNIEnv* env,
                                                                     jobject /*obj*/) {
    // Flat JSON so it can go straight into a log line or a debug screen.
    std::string json = "{";
    const auto append = [&json](const char* key, std::uint64_t value, bool last = false) {
        json += "\"";
        json += key;
        json += "\":";
        json += std::to_string(value);
        if (!last) {
            json += ",";
        }
    };
    append("connectionsAccepted", g_metrics.connectionsAccepted.load());
    append("connectionsRejectedBusy", g_metrics.connectionsRejectedBusy.load());
    append("framesIn", g_metrics.framesIn.load());
    append("framesOut", g_metrics.framesOut.load());
    append("bytesIn", g_metrics.bytesIn.load());
    append("bytesOut", g_metrics.bytesOut.load());
    append("crcErrors", g_metrics.crcErrors.load());
    append("protocolErrors", g_metrics.protocolErrors.load());
    append("timeouts", g_metrics.timeouts.load());
    append("clientRetries", g_metrics.clientRetries.load());
    append("legacySessions", g_metrics.legacySessions.load());
    append("v2Sessions", g_metrics.v2Sessions.load(), true);
    json += "}";
    return env->NewStringUTF(json.c_str());
}
