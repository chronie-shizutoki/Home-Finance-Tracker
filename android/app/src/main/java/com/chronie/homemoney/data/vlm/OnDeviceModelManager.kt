package com.chronie.homemoney.data.vlm

import android.content.Context
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and manages the on-device multimodal model
 * (MNN/Qwen3-VL-8B-Instruct-MNN, ~5.45 GB) from ModelScope.
 *
 * Behaviour:
 *  * The complete file manifest (name + size + sha256) is baked in so the
 *    download can run offline of the ModelScope file-listing API; files are
 *    fetched from `.../resolve/master/<path>` which redirects to the CDN.
 *  * Every file is downloaded to `<name>.part` with HTTP Range resume, then
 *    verified (size + SHA-256) and atomically renamed into place.
 *  * A safety margin over the model size is required on the data partition
 *    before the download starts, so a full disk never bricks mid-install.
 *  * [deleteModel] wipes the whole model directory.
 */
@Singleton
class OnDeviceModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "OnDeviceModelManager"

        private const val MODEL_HOST = "https://www.modelscope.cn"
        private const val MODEL_REPO = "MNN/Qwen3-VL-8B-Instruct-MNN"

        /** Manifest of files that make up a runnable model (from ModelScope). */
        private val MODEL_FILES = listOf(
            ModelFile("config.json", 605L, "1ed5c6e65459fdc4b0c33319715b763005013ba8580dd3c687bd2651546ca2a4"),
            ModelFile("configuration.json", 46L, "56c362f108bb9f17fc0e7b490b7e8927c03dc4478631b4f771c37728019beaa2"),
            ModelFile("embeddings_int4.bin", 388_956_160L, "311cd44e48dcec950bcc5cffb465ae0e2fec8154247f2ff545b7fd1240960bf4"),
            ModelFile("llm.mnn", 591_728L, "61f0fe3b5d0447518ae5b26ab19c0b8a7f07c51f598be5f52fa0aee7405972e3"),
            ModelFile("llm.mnn.json", 1_245_313L, "785b31ac0e0b4def1a9e707cd0876fc1e072f957db61d56868c89e0f8219aa2a"),
            ModelFile("llm.mnn.weight", 4_732_532_162L, "a52971cb29c0bef35ab336b370db00d676223e6d721b99e3bf0b9c70f2d9bcfe"),
            ModelFile("llm_config.json", 6_436L, "ce8f3a6532832d37eff85cf45ddb24b4d21567d293cfc8c64a67fa0fdca93df9"),
            ModelFile("tokenizer.txt", 3_193_555L, "7119de4966cc6a8ae87d7f083e65b315282d06c3122fdd41ce783fdd2d3c1ca2"),
            ModelFile("visual.mnn", 562_048L, "8f1653348a94c29e56d47529dd461215c80d5380263136adee7945809f407bf7"),
            ModelFile("visual.mnn.weight", 326_728_744L, "6d8b1a4886cca9ab8b8c86979f4e5291e126ac930124e9a2361833e7b11301e6")
        )

        /** Total bytes to download (weight files dominate). */
        val TOTAL_BYTES: Long = MODEL_FILES.sumOf { it.size }

        /** Extra head-room required on disk beyond the model itself. */
        private const val FREE_SPACE_MARGIN_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

        /**
         * Progress tick granularity. 4 MB balances responsiveness with
         * overhead — at 50 MB/s that's ~12 ticks/s.
         */
        private const val DOWNLOAD_CHUNK_BYTES = 4L * 1024 * 1024

        /**
         * I/O buffer size for both download and SHA-256 verification.
         * 256 KB amortises system-call overhead and reduces the chance
         * of a partial write on connection abort.
         */
        private const val BUFFER_SIZE = 256 * 1024

        /**
         * Maximum per-file download attempts before giving up.
         * Large LFS files (>4 GB) on ModelScope's CDN can close the
         * connection prematurely; retrying from the last byte avoids
         * restarting the whole download.
         */
        private const val MAX_FILE_RETRIES = 10

        /** Read timeout long enough for cold CDN connections on slow links. */
        private const val READ_TIMEOUT_SECONDS = 180L

        /** Connect timeout for establishing TCP + TLS. */
        private const val CONNECT_TIMEOUT_SECONDS = 45L

        /** Write timeout — prevents stalls when the OS buffer is full. */
        private const val WRITE_TIMEOUT_SECONDS = 60L

        /**
         * Window size for the speed-tracker sliding window.
         * We keep the last [SPEED_WINDOW_SIZE] progress samples to compute
         * a smoothed instantaneous speed.
         */
        private const val SPEED_WINDOW_SIZE = 8

        /**
         * Base delay (ms) for exponential backoff between retries.
         * Sequence: 1s, 2s, 4s, 8s, 16s, 30s, 60s, 60s, …
         */
        private const val RETRY_BACKOFF_BASE_MS = 1000L

        /** Cap for the backoff delay. */
        private const val RETRY_BACKOFF_CAP_MS = 60_000L
    }

    /** One file of the model package. */
    private data class ModelFile(val name: String, val size: Long, val sha256: String)

    /** Lifecycle of the on-device model, surfaced to the UI. */
    sealed interface ModelState {
        /** No usable model on disk (may still contain partial downloads). */
        data object NotDownloaded : ModelState

        /** Actively fetching files; [progress] is 0..1 across the whole package. */
        data class Downloading(
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val currentFile: String,
            /** Instantaneous download speed in bytes/sec (0 until first sample). */
            val speedBps: Long = 0L,
            /** Estimated seconds remaining (-1 = unknown / still calculating). */
            val etaSeconds: Long = -1L
        ) : ModelState

        /** All files present and verified. */
        data class Ready(val totalBytes: Long) : ModelState

        /** Terminal failure; [reason] is user-presentable. */
        data class Failed(val reason: String) : ModelState
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Guards download/delete against concurrent invocations. */
    private val downloadMutex = Mutex()

    @Volatile
    private var cancelled = false

    /**
     * Simple sliding-window speed tracker.
     * Stores the last [SPEED_WINDOW_SIZE] (timestamp, cumulative-bytes)
     * samples; speed = deltaBytes / deltaSeconds over the window.
     */
    private val speedSamples = ArrayDeque<Pair<Long, Long>>(SPEED_WINDOW_SIZE + 1)

    /** Current speed in B/s (0 until enough samples are collected). */
    private var currentSpeedBps: Long = 0L

    /** Records a progress sample and updates [currentSpeedBps]. */
    @Synchronized
    private fun recordSpeedSample(bytesSoFar: Long) {
        val now = System.nanoTime()
        speedSamples.addLast(now to bytesSoFar)
        if (speedSamples.size > SPEED_WINDOW_SIZE) speedSamples.removeFirst()
        if (speedSamples.size >= 2) {
            val oldest = speedSamples.first()
            val elapsedNs = now - oldest.first
            if (elapsedNs > 0) {
                currentSpeedBps = (bytesSoFar - oldest.second) * 1_000_000_000L / elapsedNs
            }
        }
    }

    /** Resets the speed tracker (call at download start). */
    @Synchronized
    private fun resetSpeedTracker() {
        speedSamples.clear()
        currentSpeedBps = 0L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()

    /** Directory where the model package lives (app-private storage). */
    fun modelDir(): File = File(context.filesDir, "models/$MODEL_REPO")

    /**
     * True when every manifest file exists with the expected size.
     * Cheap check used to gate recognition before touching native code.
     */
    fun isModelReady(): Boolean = missingFiles().isEmpty()

    /** Bytes already downloaded (verified files + current .part). */
    fun downloadedBytes(): Long {
        var total = 0L
        MODEL_FILES.forEach { mf ->
            val done = File(modelDir(), mf.name)
            if (done.exists() && done.length() == mf.size) {
                total += mf.size
            } else {
                val part = File(modelDir(), "${mf.name}.part")
                if (part.exists()) total += part.length()
            }
        }
        return total
    }

    /** Returns the manifest files that are absent or truncated. */
    private fun missingFiles(): List<ModelFile> {
        val dir = modelDir()
        return MODEL_FILES.filter { mf ->
            val f = File(dir, mf.name)
            !f.exists() || f.length() != mf.size
        }
    }

    /**
     * Computes the backoff delay in milliseconds for [attemptNumber]
     * (1-based). Sequence: 1s, 2s, 4s, 8s, 16s, 30s, 60s, 60s, …
     */
    private fun backoffMs(attemptNumber: Int): Long {
        if (attemptNumber <= 0) return 0L
        val exponential = RETRY_BACKOFF_BASE_MS * (1L shl (attemptNumber - 1))
        return exponential.coerceAtMost(RETRY_BACKOFF_CAP_MS)
    }

    /**
     * Starts (or resumes) downloading the model.
     * Safe to call repeatedly: an in-flight download is a no-op, and a
     * completed download transitions straight to Ready.
     */
    suspend fun downloadModel(): Result<Unit> = downloadMutex.withLock {
        cancelled = false
        resetSpeedTracker()
        val dir = modelDir()

        // Fast path: everything already on disk.
        if (missingFiles().isEmpty()) {
            _state.value = ModelState.Ready(TOTAL_BYTES)
            return Result.success(Unit)
        }

        // Pre-flight storage check on the partition that hosts filesDir.
        val stat = StatFs(context.filesDir.absolutePath)
        val freeBytes = stat.availableBytes
        if (freeBytes < TOTAL_BYTES + FREE_SPACE_MARGIN_BYTES - downloadedBytes()) {
            val reason = "Insufficient storage: need ~${(TOTAL_BYTES + FREE_SPACE_MARGIN_BYTES) / (1024L * 1024 * 1024)} GB free, only ${freeBytes / (1024L * 1024 * 1024)} GB available"
            Log.e(TAG, reason)
            _state.value = ModelState.Failed(reason)
            return Result.failure(IOException(reason))
        }

        if (!dir.exists()) dir.mkdirs()

        return try {
            withContext(Dispatchers.IO) {
                var downloaded = MODEL_FILES.sumOf { mf ->
                    val f = File(dir, mf.name)
                    if (f.exists() && f.length() == mf.size) mf.size else 0L
                }

                for (mf in MODEL_FILES) {
                    if (cancelled) {
                        _state.value = ModelState.NotDownloaded
                        return@withContext Result.failure(IOException("Download cancelled"))
                    }

                    val finalFile = File(dir, mf.name)
                    if (finalFile.exists() && finalFile.length() == mf.size) continue

                    val partFile = File(dir, "${mf.name}.part")
                    val startByte = if (partFile.exists()) partFile.length() else 0L

                    // If .part is larger than expected, trim it (corrupt).
                    if (startByte > mf.size) {
                        Log.w(TAG, "Part file ${mf.name}.part (${startByte}B) exceeds " +
                                "expected ${mf.size}B — truncating")
                        partFile.delete()
                    }

                    downloadFileWithResume(mf, partFile, if (startByte > mf.size) 0L else startByte) { bytesSoFar ->
                        val overall = downloaded + bytesSoFar
                        recordSpeedSample(overall)
                        val remaining = TOTAL_BYTES - overall
                        val eta = if (currentSpeedBps > 0) remaining / currentSpeedBps else -1L
                        _state.value = ModelState.Downloading(
                            progress = overall.toFloat() / TOTAL_BYTES,
                            downloadedBytes = overall,
                            totalBytes = TOTAL_BYTES,
                            currentFile = mf.name,
                            speedBps = currentSpeedBps,
                            etaSeconds = eta
                        )
                    }

                    // Verify then atomically promote the .part file.
                    verifyFile(partFile, mf)
                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        throw IOException("Failed to finalize ${mf.name}")
                    }
                    downloaded += mf.size
                }

                _state.value = ModelState.Ready(TOTAL_BYTES)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            // Keep partial data so a retry can resume; notify the UI.
            _state.value = ModelState.Failed(e.message ?: "Download failed")
            Result.failure(e)
        }
    }

    /** Asks an in-flight download to stop at the next chunk boundary. */
    fun cancelDownload() {
        cancelled = true
    }

    /** Removes the model from disk entirely. */
    suspend fun deleteModel(): Result<Unit> = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = modelDir()
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
            _state.value = ModelState.NotDownloaded
            Result.success(Unit)
        }
    }

    /** Refreshes [state] from disk (call on app start / settings entry). */
    fun refreshState() {
        _state.value = if (missingFiles().isEmpty()) {
            ModelState.Ready(TOTAL_BYTES)
        } else {
            ModelState.NotDownloaded
        }
    }

    /**
     * Streams one file with HTTP Range resume and automatic retries.
     * Large LFS files (>4 GB) on ModelScope's CDN sometimes close the
     * connection before all bytes are delivered (premature EOF) or the
     * OS aborts the socket ("software caused connection abort"). When
     * that happens we retry from the last written byte with exponential
     * backoff rather than restarting the whole download.
     *
     * `onProgress` receives the byte count including the resumed prefix,
     * so callers can report a package-wide figure.
     */
    private fun downloadFileWithResume(
        mf: ModelFile,
        partFile: File,
        startByte: Long,
        onProgress: (bytesSoFar: Long) -> Unit
    ) {
        var attempt = 0
        var currentStart = startByte
        while (attempt < MAX_FILE_RETRIES) {
            attempt++
            try {
                downloadFileOnce(mf, partFile, currentStart, onProgress)
                return // success
            } catch (e: IOException) {
                val partialSize = partFile.length()

                // If the part file has the full size, the download actually
                // succeeded — the IOException might be from the closing
                // handshake.  Let verifyFile do its job.
                if (partialSize == mf.size) {
                    Log.i(TAG, "Part file ${mf.name}.part already at full size " +
                            "despite exception (${e.message}), proceeding to verify")
                    return
                }

                if (attempt < MAX_FILE_RETRIES && partialSize < mf.size && partialSize >= currentStart) {
                    // CDN dropped the connection early — resume from what we have.
                    val delayMs = backoffMs(attempt)
                    Log.w(TAG, "Retry $attempt/$MAX_FILE_RETRIES for ${mf.name}: " +
                            "got ${partialSize}/${mf.size} bytes (${e.message}). " +
                            "Waiting ${delayMs}ms before resume from byte $partialSize")
                    if (delayMs > 0) {
                        try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
                    }
                    currentStart = partialSize
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Single attempt at streaming one file. Throws [IOException] if the
     * response ends before [mf.size] bytes have been received.
     *
     * When the server returns 200 (full body) instead of 206 (partial),
     * we skip the already-downloaded prefix over the network (read and
     * discard) then append new bytes to the part file. This avoids
     * deleting the partial file and re-downloading from scratch.
     */
    private fun downloadFileOnce(
        mf: ModelFile,
        partFile: File,
        startByte: Long,
        onProgress: (bytesSoFar: Long) -> Unit
    ) {
        val url = "$MODEL_HOST/models/$MODEL_REPO/resolve/master/${mf.name}"

        val requestBuilder = Request.Builder().url(url)
        if (startByte > 0) {
            requestBuilder.header("Range", "bytes=$startByte-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val serverIgnoredRange = startByte > 0 && response.code == 200
            val expectedCode = if (startByte > 0 && !serverIgnoredRange) 206 else 200

            if (response.code != expectedCode && !serverIgnoredRange) {
                throw IOException("HTTP ${response.code} while downloading ${mf.name}")
            }

            val body = response.body ?: throw IOException("Empty body for ${mf.name}")

            // Validate Content-Length when available for early mismatch detection.
            val contentLength = body.contentLength()
            if (contentLength > 0) {
                if (serverIgnoredRange) {
                    // Full body — should match mf.size
                    if (contentLength != mf.size) {
                        Log.w(TAG, "Content-Length $contentLength != expected ${mf.size} " +
                                "for ${mf.name} (full body)")
                    }
                } else if (startByte == 0L) {
                    if (contentLength != mf.size) {
                        Log.w(TAG, "Content-Length $contentLength != expected ${mf.size} " +
                                "for ${mf.name}")
                    }
                }
                // For 206, Content-Length is the remaining bytes — harder to validate.
            }

            var bytesSoFar = startByte
            var lastTick = bytesSoFar

            body.byteStream().use { input ->
                if (serverIgnoredRange) {
                    // Server sent the full body — skip bytes already on disk.
                    Log.w(TAG, "Server returned 200 (not 206) for ${mf.name}, " +
                            "skipping $startByte already-downloaded bytes")
                    var toSkip = startByte
                    while (toSkip > 0) {
                        val skipped = input.skip(toSkip)
                        if (skipped <= 0) {
                            throw IOException("Stream ended while skipping $toSkip " +
                                    "bytes for ${mf.name}")
                        }
                        toSkip -= skipped
                    }
                }

                java.io.FileOutputStream(partFile, startByte > 0).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancelled) throw IOException("Download cancelled")
                        val read = try {
                            input.read(buffer)
                        } catch (e: SocketException) {
                            // "Software caused connection abort" — treat as
                            // premature EOF so the outer retry loop can resume.
                            Log.w(TAG, "SocketException reading ${mf.name}: ${e.message}")
                            -1
                        }
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesSoFar += read
                        if (bytesSoFar - lastTick >= DOWNLOAD_CHUNK_BYTES) {
                            lastTick = bytesSoFar
                            onProgress(bytesSoFar)
                        }
                    }
                    output.fd.sync()
                }
            }
            onProgress(bytesSoFar)

            if (bytesSoFar != mf.size) {
                throw IOException("Size mismatch for ${mf.name}: " +
                        "got $bytesSoFar, expected ${mf.size}")
            }
        }
    }

    /** Checks size and SHA-256 of a staged .part file before promotion. */
    private fun verifyFile(file: File, mf: ModelFile) {
        if (file.length() != mf.size) {
            throw IOException("Verification failed for ${mf.name}: " +
                    "size ${file.length()} != ${mf.size}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!hash.equals(mf.sha256, ignoreCase = true)) {
            // Corrupt chunk: delete so the next attempt re-fetches from zero.
            file.delete()
            throw IOException("SHA-256 mismatch for ${mf.name}")
        }
    }

    /** Convenience for the AI screen: model dir usable by the VLM engine. */
    fun readyModelDir(): File? = if (isModelReady()) modelDir() else null
}
