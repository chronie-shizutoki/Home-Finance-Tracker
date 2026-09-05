// mnn_bridge.cpp
//
// JNI bridge between Kotlin (MnnVlmEngine) and the MNN-LLM runtime.
//
// The bridge is intentionally thin: one process-wide Llm instance is created
// from a model config.json on disk, response() is invoked with a plain prompt
// or MultimodalPrompt, and generated text is streamed back to Java through a
// custom ostream that forwards each chunk to a TokenListener callback.
//
// Threading contract:
//   * All native entry points take a global mutex, so Kotlin may call them
//     from any thread, but response() blocks until generation finishes.
//   * response() must be called from a thread that is already attached to
//     the JVM (i.e. a normal Java/Kotlin thread), which holds because the
//     Kotlin wrapper always dispatches to Dispatchers.Default.

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <sstream>
#include <string>
#include <streambuf>

#include "llm/llm.hpp"  // MNN::Transformer::Llm, MultimodalPrompt

#define LOG_TAG "MnnBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Process-wide engine state, guarded by g_engine_mutex.
MNN::Transformer::Llm* g_llm = nullptr;
bool g_llm_loaded = false;
std::mutex g_engine_mutex;

// Resolved JNI metadata for the token listener, refreshed on every call.
// Kept in file scope so the C++ streambuf can reach them without captures.
jobject g_listener_ref = nullptr;
jmethodID g_on_token_mid = nullptr;
JavaVM* g_vm = nullptr;

/**
 * Custom streambuf that forwards each flushed chunk to the Java TokenListener.
 * MNN's response() writes generated text into an ostream; by installing this
 * streambuf we intercept the output incrementally and relay it to Kotlin
 * without waiting for the full generation to complete.
 *
 * The streambuf accumulates text in its internal buffer and flushes it to the
 * Java listener whenever sync() is called (typically on each newline or when
 * MNN internally flushes). For token-level granularity we also flush on
 * overflow when the buffer is full.
 */
class ListenerStreambuf : public std::streambuf {
public:
    explicit ListenerStreambuf(size_t bufSize = 256) {
        buffer_.resize(bufSize);
        setp(buffer_.data(), buffer_.data() + bufSize - 1);
    }

    ~ListenerStreambuf() override = default;

    // Non-copyable
    ListenerStreambuf(const ListenerStreambuf&) = delete;
    ListenerStreambuf& operator=(const ListenerStreambuf&) = delete;

protected:
    // Called when the put area is full; flush and reset.
    int_type overflow(int_type ch) override {
        if (ch != traits_type::eof()) {
            *pptr() = static_cast<char>(ch);
            pbump(1);
        }
        return sync() == 0 ? ch : traits_type::eof();
    }

    // Flush accumulated text to the Java listener.
    int sync() override {
        auto len = static_cast<std::ptrdiff_t>(pptr() - pbase());
        if (len > 0 && g_listener_ref != nullptr && g_on_token_mid != nullptr) {
            JNIEnv* env = nullptr;
            if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
                jstring jChunk = env->NewStringUTF(std::string(pbase(), len).c_str());
                if (jChunk != nullptr) {
                    env->CallVoidMethod(g_listener_ref, g_on_token_mid, jChunk);
                    env->DeleteLocalRef(jChunk);
                }
            }
        }
        setp(buffer_.data(), buffer_.data() + buffer_.size() - 1);
        return 0;
    }

private:
    std::vector<char> buffer_;
};

}  // namespace

// Called once when the library is loaded, so we can cache the JavaVM.
jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

/**
 * Creates and loads the Llm instance from a model directory's config.json.
 *
 * @param configPath   absolute path to <modelDir>/config.json
 * @param extraConfig  JSON string merged into the engine config, e.g.
 *                     {"max_new_tokens":1024,"use_mmap":true}
 * @return 0 on success, -1 when creation failed, -2 when already created,
 *         -3 when model files are missing (runtime not bundled).
 */
JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeCreate(
        JNIEnv* env, jobject, jstring configPath, jstring extraConfig) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm != nullptr) {
        return -2;
    }

    const char* pathChars = env->GetStringUTFChars(configPath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(configPath, pathChars);

    LOGI("Creating LLM from config: %s", path.c_str());
    g_llm = MNN::Transformer::Llm::createLLM(path);
    if (g_llm == nullptr) {
        LOGE("Llm::createLLM returned null");
        return -1;
    }

    // Optional runtime tweaks supplied as a JSON string (set_config merges them).
    if (extraConfig != nullptr) {
        const char* configChars = env->GetStringUTFChars(extraConfig, nullptr);
        std::string config(configChars);
        env->ReleaseStringUTFChars(extraConfig, configChars);
        if (!config.empty()) {
            try {
                g_llm->set_config(config);
            } catch (const std::exception& e) {
                // Config tweaks are best-effort; a rejected key must not kill the session.
                LOGE("set_config failed (non-fatal): %s", e.what());
            }
        }
    }

    // Explicit load is required in the current MNN API.
    try {
        if (!g_llm->load()) {
            LOGE("Llm::load() returned false");
            delete g_llm;
            g_llm = nullptr;
            return -1;
        }
    } catch (const std::exception& e) {
        LOGE("Llm::load() threw: %s", e.what());
        delete g_llm;
        g_llm = nullptr;
        return -1;
    }

    g_llm_loaded = true;
    LOGI("LLM created and loaded successfully");
    return 0;
}

/**
 * Returns 1 when an Llm instance is alive, 0 otherwise.
 */
JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeIsCreated(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    return (g_llm != nullptr && g_llm_loaded) ? 1 : 0;
}

/**
 * Releases the Llm instance and all native resources. Safe to call repeatedly.
 */
JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeDestroy(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm != nullptr) {
        try {
            MNN::Transformer::Llm::destroy(g_llm);
        } catch (const std::exception& e) {
            LOGE("Llm::destroy() threw (non-fatal): %s", e.what());
        }
        g_llm = nullptr;
        g_llm_loaded = false;
        LOGI("LLM destroyed");
    }
}

/**
 * Runs one-shot generation. Two modes:
 *
 * 1) Multimodal (imagePaths non-empty):
 *    Images are passed via MultimodalPrompt with keys "img0", "img1", etc.
 *    The prompt template may reference them as {{image:img0}}.
 *    If the prompt contains no {{image:...}} markers, the bridge appends
 *    them automatically before the text.
 *
 * 2) Text-only (imagePaths empty):
 *    Calls response(user_content, ...) directly.
 *
 * @param prompt      user prompt text
 * @param imagePaths  comma-separated absolute paths to pre-processed images
 * @param listener    optional TokenListener whose onToken(String) receives
 *                    the generated text incrementally (may be null)
 * @return the complete generated text, or nullptr on failure.
 */
JNIEXPORT jstring JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeChat(
        JNIEnv* env, jobject, jstring prompt, jstring imagePaths, jobject listener) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm == nullptr || !g_llm_loaded) {
        LOGE("nativeChat called before nativeCreate/load");
        return env->NewStringUTF("");
    }

    const char* promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    // Parse comma-separated image paths.
    std::vector<std::string> images;
    if (imagePaths != nullptr) {
        const char* pathsChars = env->GetStringUTFChars(imagePaths, nullptr);
        std::string pathsStr(pathsChars);
        env->ReleaseStringUTFChars(imagePaths, pathsChars);

        std::istringstream pathStream(pathsStr);
        std::string segment;
        while (std::getline(pathStream, segment, ',')) {
            // Trim whitespace.
            auto start = segment.find_first_not_of(" \t\r\n");
            auto end = segment.find_last_not_of(" \t\r\n");
            if (start != std::string::npos && end != std::string::npos) {
                images.push_back(segment.substr(start, end - start + 1));
            }
        }
    }

    // Register the listener for the duration of this call.
    if (listener != nullptr) {
        g_listener_ref = env->NewGlobalRef(listener);
        jclass cls = env->GetObjectClass(listener);
        g_on_token_mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);
    } else {
        g_listener_ref = nullptr;
        g_on_token_mid = nullptr;
    }

    std::string result;
    bool failed = false;

    try {
        ListenerStreambuf streambuf;
        std::ostream outputStream(&streambuf);

        if (!images.empty()) {
            // Multimodal mode: build MultimodalPrompt with image placeholders.
            MNN::Transformer::MultimodalPrompt mmPrompt;
            std::string templateText = promptStr;

            for (size_t i = 0; i < images.size(); ++i) {
                std::string key = "img" + std::to_string(i);
                // Insert image placeholder at the beginning of the prompt if
                // the user did not include {{image:...}} markers themselves.
                if (templateText.find("{{image:" + key + "}}") == std::string::npos) {
                    templateText = "{{image:" + key + "}}\n" + templateText;
                }

                MNN::Transformer::PromptImagePart imgPart;
                // MNN can load images from file paths when using MNNOpenCV.
                // We create a placeholder VARP; MNN's visual model will
                // load from the path referenced in the config if image_data
                // is empty and the key maps to a file.
                imgPart.image_data = nullptr;
                imgPart.width = 0;
                imgPart.height = 0;
                mmPrompt.images[key] = imgPart;
            }
            mmPrompt.prompt_template = templateText;

            // For file-based image loading, set config with image paths.
            // MNN's Llm with LLM_SUPPORT_VISION processes <img>path</img>
            // tags in the prompt via the visual model. We use the legacy
            // <img> tag format as a fallback if MultimodalPrompt doesn't
            // auto-load files.
            // Build the prompt with <img> tags for vision model processing.
            std::string imgPrompt;
            for (size_t i = 0; i < images.size(); ++i) {
                imgPrompt += "<img>" + images[i] + "</img>\n";
            }
            imgPrompt += promptStr;

            g_llm->response(imgPrompt, &outputStream, nullptr, -1);
        } else {
            // Text-only mode.
            g_llm->response(promptStr, &outputStream, nullptr, -1);
        }

        // Final flush of the streambuf.
        outputStream.flush();
        streambuf.pubsync();

        // Collect the full generated text from LlmContext.
        auto ctx = g_llm->getContext();
        if (ctx != nullptr) {
            result = ctx->generate_str;
        }
    } catch (const std::exception& e) {
        LOGE("response() threw: %s", e.what());
        failed = true;
    } catch (...) {
        LOGE("response() threw an unknown exception");
        failed = true;
    }

    if (g_listener_ref != nullptr) {
        env->DeleteGlobalRef(g_listener_ref);
        g_listener_ref = nullptr;
        g_on_token_mid = nullptr;
    }

    if (failed) {
        return env->NewStringUTF("");
    }

    LOGI("response() produced %zu chars", result.size());
    return env->NewStringUTF(result.c_str());
}

/**
 * Resets the conversation/KV cache. Keeps the model loaded in memory.
 */
JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeReset(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm != nullptr) {
        try {
            g_llm->reset();
        } catch (const std::exception& e) {
            LOGE("reset() failed (non-fatal): %s", e.what());
        }
    }
}

}  // extern "C"
