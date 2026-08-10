package com.chronie.homemoney.service

import com.chronie.homemoney.data.local.ServerConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Probes a candidate server address before the user commits to it.
 *
 * Deliberately builds its own [OkHttpClient] instead of reusing the injected one: the shared client
 * carries [com.chronie.homemoney.data.remote.interceptor.ServerUrlInterceptor], which would rewrite
 * the probe to point at the *currently saved* server and make every test report success.
 */
@Singleton
class ServerConnectionTester @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Issues a single health request against [rawUrl].
     *
     * @param rawUrl user input; normalized through [ServerConfigManager.normalize] first, so bare
     *   hosts like `192.168.1.5:3010` are accepted.
     */
    suspend fun test(rawUrl: String): ServerTestResult = withContext(Dispatchers.IO) {
        val normalized = ServerConfigManager.normalize(rawUrl)
            ?: return@withContext ServerTestResult.InvalidUrl

        val probeUrl = normalized.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addEncodedPathSegments(HEALTH_PATH)
            ?.build()
            ?: return@withContext ServerTestResult.InvalidUrl

        val request = Request.Builder().url(probeUrl).get().build()

        try {
            var result: ServerTestResult
            val elapsed = measureTimeMillis {
                result = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        ServerTestResult.Success(normalized, 0L)
                    } else {
                        ServerTestResult.BadResponse(response.code)
                    }
                }
            }
            // Latency is only meaningful for a successful round trip.
            if (result is ServerTestResult.Success) {
                ServerTestResult.Success(normalized, elapsed)
            } else {
                result
            }
        } catch (e: IOException) {
            ServerTestResult.Unreachable(e.message ?: e::class.java.simpleName)
        } catch (e: IllegalStateException) {
            ServerTestResult.Unreachable(e.message ?: e::class.java.simpleName)
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 4L
        const val HEALTH_PATH = "api/health/lite"
    }
}

/** Outcome of a [ServerConnectionTester.test] probe. */
sealed interface ServerTestResult {

    /** Server answered the health endpoint. [normalizedUrl] is safe to persist as-is. */
    data class Success(val normalizedUrl: String, val latencyMs: Long) : ServerTestResult

    /** Input could not be parsed into an HTTP(S) URL. */
    data object InvalidUrl : ServerTestResult

    /** Host was reachable but rejected the health request. */
    data class BadResponse(val code: Int) : ServerTestResult

    /** Connection failed outright — wrong host/port, server down, or no route. */
    data class Unreachable(val reason: String) : ServerTestResult
}
