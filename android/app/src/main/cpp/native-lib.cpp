#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <thread>
#include <atomic>

#define TAG "NativeSyncEngine-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_jvm = nullptr;
static jobject g_engine_obj = nullptr;
static std::atomic<bool> g_server_running(false);
static std::atomic<int> g_server_fd(-1);

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// 辅助函数：确保读取完整的数据长度
bool read_all(int fd, void* buffer, size_t size) {
    size_t total_read = 0;
    char* ptr = (char*)buffer;
    while (total_read < size) {
        ssize_t n = read(fd, ptr + total_read, size - total_read);
        if (n <= 0) return false;
        total_read += n;
    }
    return true;
}

// 辅助函数：确保发送完整的数据包
bool write_all(int fd, const void* buffer, size_t size) {
    size_t total_sent = 0;
    const char* ptr = (const char*)buffer;
    while (total_sent < size) {
        ssize_t n = write(fd, ptr + total_sent, size - total_sent);
        if (n <= 0) return false;
        total_sent += n;
    }
    return true;
}

jbyteArray call_kotlin_sync(JNIEnv* env, const std::vector<uint8_t>& input_data) {
    if (!g_engine_obj) {
        LOGE("call_kotlin_sync: g_engine_obj is null");
        return nullptr;
    }

    jclass cls = env->GetObjectClass(g_engine_obj);
    if (!cls) {
        LOGE("call_kotlin_sync: Failed to get class of g_engine_obj");
        return nullptr;
    }

    jmethodID mid = env->GetMethodID(cls, "handleIncomingSyncRequest", "(Ljava/lang/String;Ljava/lang/String;[B)[B");
    if (!mid) {
        LOGE("call_kotlin_sync: Failed to find handleIncomingSyncRequest method");
        env->DeleteLocalRef(cls);
        return nullptr;
    }

    jstring jid = env->NewStringUTF("remote_device");
    jstring jname = env->NewStringUTF("Remote Device");

    jbyteArray jdata = env->NewByteArray(input_data.size());
    env->SetByteArrayRegion(jdata, 0, input_data.size(), (const jbyte*)input_data.data());

    LOGD("Calling handleIncomingSyncRequest in Kotlin...");
    auto response = (jbyteArray)env->CallObjectMethod(g_engine_obj, mid, jid, jname, jdata);

    if (env->ExceptionCheck()) {
        LOGE("Exception occurred in handleIncomingSyncRequest");
        env->ExceptionDescribe();
        env->ExceptionClear();
        response = nullptr;
    }

    env->DeleteLocalRef(jid);
    env->DeleteLocalRef(jname);
    env->DeleteLocalRef(jdata);
    env->DeleteLocalRef(cls);

    return response;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_startServer(JNIEnv* env, jobject obj, jint port) {
    if (g_server_running) return JNI_TRUE;

    // 清理旧的全局引用
    if (g_engine_obj) {
        env->DeleteGlobalRef(g_engine_obj);
    }
    g_engine_obj = env->NewGlobalRef(obj);
    g_server_running = true;

    std::thread([port]() {
        JNIEnv* env_thread = nullptr;
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "SyncServerThread", nullptr};

        // Android NDK AttachCurrentThread typically takes (JNIEnv**, void*)
        if (g_jvm->AttachCurrentThread((JNIEnv**)&env_thread, &args) != JNI_OK) {
            LOGE("Failed to attach server thread to JVM");
            return;
        }

        int server_fd = socket(AF_INET, SOCK_STREAM, 0);
        if (server_fd < 0) {
            g_jvm->DetachCurrentThread();
            return;
        }
        g_server_fd = server_fd;

        int opt = 1;
        setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        struct sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = INADDR_ANY;
        addr.sin_port = htons(port);

        if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
            LOGE("Server bind failed");
            close(server_fd);
            g_server_fd = -1;
            g_jvm->DetachCurrentThread();
            return;
        }

        listen(server_fd, 5);
        LOGD("Native High-Perf Server listening on port %d", port);

        while (g_server_running) {
            struct sockaddr_in client_addr{};
            socklen_t client_len = sizeof(client_addr);
            int client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &client_len);
            if (client_fd < 0) {
                if (g_server_running) continue;
                else break;
            }

            LOGD("Accepted connection from %s", inet_ntoa(client_addr.sin_addr));

            // 1. 读取数据包长度 (4字节)
            uint32_t len = 0;
            if (read_all(client_fd, &len, 4)) {
                len = ntohl(len);
                if (len > 10 * 1024 * 1024) { // 限制 10MB 防止 OOM
                    LOGE("Received packet too large: %u bytes", len);
                } else {
                    std::vector<uint8_t> buffer(len);
                    if (read_all(client_fd, buffer.data(), len)) {
                        LOGD("Received %u bytes sync data", len);

                        // 3. 回调 Kotlin
                        jbyteArray j_resp = call_kotlin_sync(env_thread, buffer);

                        if (j_resp != nullptr) {
                            jsize resp_len = env_thread->GetArrayLength(j_resp);
                            jbyte* resp_ptr = env_thread->GetByteArrayElements(j_resp, nullptr);

                            uint32_t n_resp_len = htonl((uint32_t)resp_len);
                            write_all(client_fd, &n_resp_len, 4);
                            write_all(client_fd, resp_ptr, resp_len);

                            env_thread->ReleaseByteArrayElements(j_resp, resp_ptr, JNI_ABORT);
                            env_thread->DeleteLocalRef(j_resp);
                        } else {
                            LOGD("Request rejected by user or internal error");
                            uint32_t zero = 0;
                            write_all(client_fd, &zero, 4);
                        }
                    }
                }
            }
            close(client_fd);
        }
        close(server_fd);
        g_server_fd = -1;
        g_jvm->DetachCurrentThread();
        LOGD("Native Server thread exiting");
    }).detach();

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_stopServer(JNIEnv* env, jobject /* this */) {
    g_server_running = false;
    int fd = g_server_fd.exchange(-1);
    if (fd != -1) {
        shutdown(fd, SHUT_RDWR);
        close(fd);
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_chronie_homemoney_data_sync_NativeSyncEngine_performSync(JNIEnv* env, jobject /* this */, jstring address, jint port, jbyteArray data) {
    const char* nativeAddress = env->GetStringUTFChars(address, nullptr);
    jbyte* buffer_ptr = env->GetByteArrayElements(data, nullptr);
    jsize data_len = env->GetArrayLength(data);

    LOGD("Native Connecting to %s:%d", nativeAddress, port);

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        env->ReleaseByteArrayElements(data, buffer_ptr, JNI_ABORT);
        env->ReleaseStringUTFChars(address, nativeAddress);
        return nullptr;
    }

    struct timeval timeout{};
    timeout.tv_sec = 10;
    timeout.tv_usec = 0;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    struct sockaddr_in server_addr{};
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    inet_pton(AF_INET, nativeAddress, &server_addr.sin_addr);

    jbyteArray result = nullptr;
    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) >= 0) {
        uint32_t n_len = htonl((uint32_t)data_len);
        if (write_all(sock, &n_len, 4) && write_all(sock, buffer_ptr, data_len)) {
            uint32_t resp_len = 0;
            if (read_all(sock, &resp_len, 4)) {
                resp_len = ntohl(resp_len);
                if (resp_len > 0) {
                    std::vector<uint8_t> resp_buf(resp_len);
                    if (read_all(sock, resp_buf.data(), resp_len)) {
                        result = env->NewByteArray(resp_len);
                        env->SetByteArrayRegion(result, 0, resp_len, (jbyte*)resp_buf.data());
                    }
                }
            }
        }
    } else {
        LOGE("Connection to %s:%d failed", nativeAddress, port);
    }

    close(sock);
    env->ReleaseByteArrayElements(data, buffer_ptr, JNI_ABORT);
    env->ReleaseStringUTFChars(address, nativeAddress);

    return result;
}
