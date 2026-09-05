package com.chronie.homemoney.data.vlm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin facade for the on-device multimodal LLM (Qwen3-VL 8B, MNN runtime).
 *
 * The heavy lifting happens in native code (cpp/mnn/mnn_bridge.cpp) — this
 * class owns the lifecycle of the single process-wide engine instance and
 * exposes a coroutine-friendly [generate] API.
 *
 * Contract:
 *  * [ensureLoaded] must succeed before [generate]; it is idempotent and
 *    safe to call concurrently (a mutex serializes model loading, which can
 *    take tens of seconds for a ~5.5 GB mmap-ed model).
 *  * [generate] runs on Dispatchers.Default and blocks that thread until
 *    generation completes; call it from a coroutine that can be cancelled
 *    from the UI via structured concurrency.
 *  * Images are passed as a list of absolute file paths; the bridge embeds
 *    <img>path</img> markers before the text prompt, which MNN's visual
 *    model processes.
 */
@Singleton
class MnnVlmEngine @Inject constructor() {

    companion object {
        private const val TAG = "MnnVlmEngine"

        /** Loads libmnn_bridge.so (real bridge or stub) once per process. */
        private const val LIBRARY_NAME = "mnn_bridge"

        init {
            System.loadLibrary(LIBRARY_NAME)
        }

        /** nativeCreate result codes, mirrored from mnn_bridge.cpp. */
        private const val CREATE_OK = 0
        private const val CREATE_FAILED = -1
        private const val CREATE_ALREADY = -2
        private const val CREATE_RUNTIME_MISSING = -3

        /** Embeds an image file path as a multimodal marker in the prompt. */
        fun buildImageTag(imagePath: String): String = "<img>$imagePath</img>"
    }

    /** Receives incrementally generated text (called on the caller's thread). */
    fun interface TokenListener {
        fun onToken(token: String)
    }

    // Native entry points implemented in cpp/mnn/mnn_bridge(.cpp|_stub.cpp).
    private external fun nativeCreate(configPath: String, extraConfigJson: String): Int
    private external fun nativeIsCreated(): Int
    private external fun nativeDestroy()
    private external fun nativeChat(
        prompt: String,
        imagePaths: String?,  // comma-separated absolute paths (may be null)
        listener: TokenListener?
    ): String
    private external fun nativeReset()

    /** Serializes load/destroy so concurrent coroutines cannot interleave. */
    private val lifecycleMutex = Mutex()

    @Volatile
    private var isLoaded = false

    /** @return true when the native engine currently holds a live model. */
    fun isReady(): Boolean = isLoaded && nativeIsCreated() == 1

    /**
     * Loads the model if necessary.
     *
     * @param modelDir directory containing config.json + MNN weights
     * @return Result.success(Unit) when the engine is ready for inference.
     */
    suspend fun ensureLoaded(modelDir: File): Result<Unit> = lifecycleMutex.withLock {
        if (isReady()) return Result.success(Unit)

        val configFile = File(modelDir, "config.json")
        if (!configFile.exists()) {
            return Result.failure(IllegalStateException("Model config not found: $configFile"))
        }

        return withContext(Dispatchers.Default) {
            when (nativeCreate(configFile.absolutePath, buildRuntimeConfig())) {
                CREATE_OK, CREATE_ALREADY -> {
                    isLoaded = true
                    Log.i(TAG, "VLM engine loaded from $modelDir")
                    Result.success(Unit)
                }
                CREATE_RUNTIME_MISSING -> {
                    Log.e(TAG, "MNN runtime is not bundled in this build")
                    Result.failure(IllegalStateException(RuntimeMissingException.MESSAGE))
                }
                else -> {
                    Log.e(TAG, "nativeCreate failed (out of memory or corrupt weights?)")
                    Result.failure(IllegalStateException("Failed to initialize the on-device AI model"))
                }
            }
        }
    }

    /**
     * Runs one-shot generation with the given prompt and optional images.
     *
     * @param prompt prompt text
     * @param imagePaths optional list of absolute paths to pre-processed images
     * @param onToken optional incremental callback for progress UI
     * @return Result with the full generated text (empty output = failure).
     */
    suspend fun generate(
        prompt: String,
        imagePaths: List<String>? = null,
        onToken: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext Result.failure(IllegalStateException("Engine not loaded"))
        }

        // Comma-join image paths for the JNI bridge (null when no images).
        val imagePathsStr = if (!imagePaths.isNullOrEmpty()) {
            imagePaths.joinToString(",")
        } else {
            null
        }

        // Bridge the synchronous Java callback into the coroutine world.
        val listener = if (onToken != null) {
            TokenListener { token ->
                // The callback arrives on this same (Default dispatcher) thread.
                // Launch-free direct call keeps ordering and avoids allocation.
                kotlinx.coroutines.runBlocking { onToken(token) }
            }
        } else {
            null
        }

        val output = try {
            nativeChat(prompt, imagePathsStr, listener)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeChat threw", e)
            return@withContext Result.failure(e)
        }

        if (output.isBlank()) {
            Result.failure(IllegalStateException("The on-device model returned an empty response"))
        } else {
            Result.success(output)
        }
    }

    /** Releases the native model and frees its (mmap-ed) memory. */
    suspend fun release() = lifecycleMutex.withLock {
        withContext(Dispatchers.Default) {
            if (isLoaded) {
                nativeDestroy()
                isLoaded = false
                Log.i(TAG, "VLM engine released")
            }
        }
    }

    /**
     * Runtime tuning merged into the engine config after creation.
     * use_mmap keeps peak RSS manageable for the 4.4 GB weight file on
     * 12-16 GB devices; max_new_tokens bounds bill-generation latency.
     */
    private fun buildRuntimeConfig(): String {
        return """{"max_new_tokens":1024,"use_mmap":true}"""
    }

    /** Thrown when the APK was built without the MNN native runtime. */
    class RuntimeMissingException : IllegalStateException(MESSAGE) {
        companion object {
            const val MESSAGE =
                "On-device AI runtime is not available in this build. " +
                    "Rebuild the app after running scripts/build-mnn-android.ps1."
        }
    }
}
