package com.chronie.homemoney.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the backend base URL.
 *
 * The address is user-configurable at runtime, which rules out the usual `DataStore` approach:
 * [com.chronie.homemoney.data.remote.interceptor.ServerUrlInterceptor] runs on OkHttp's blocking
 * call stack and cannot suspend to await a `Flow`. Backing the value with [SharedPreferences]
 * gives the interceptor a race-free synchronous read at cold start, while [baseUrl] still exposes
 * a [StateFlow] so settings UI can react to changes.
 *
 * Stored values are always normalized by [normalize] — scheme present, trailing slash guaranteed —
 * so callers may treat [currentBaseUrl] as a valid Retrofit base URL without re-validating.
 */
@Singleton
class ServerConfigManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(
        prefs.getString(KEY_BASE_URL, null)?.let { normalize(it) } ?: DEFAULT_BASE_URL
    )

    /** Emits the active base URL, starting with the persisted value. */
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    /**
     * The active base URL, readable from any thread without suspending.
     *
     * Intended for the OkHttp interceptor; UI should collect [baseUrl] instead.
     */
    val currentBaseUrl: String
        get() = _baseUrl.value

    /** True when the user has pointed the app at something other than [DEFAULT_BASE_URL]. */
    val isUsingCustomServer: Boolean
        get() = _baseUrl.value != DEFAULT_BASE_URL

    /**
     * Validates, normalizes and persists [rawUrl].
     *
     * @return the normalized URL that was stored, or [ServerUrlError] describing why it was
     *   rejected. The stored value is left untouched on failure.
     */
    fun setBaseUrl(rawUrl: String): Result<String> {
        val normalized = normalize(rawUrl)
            ?: return Result.failure(ServerUrlError(rawUrl))

        prefs.edit { putString(KEY_BASE_URL, normalized) }
        _baseUrl.value = normalized
        return Result.success(normalized)
    }

    /** Restores the compiled-in default address. */
    fun resetToDefault() {
        prefs.edit { remove(KEY_BASE_URL) }
        _baseUrl.value = DEFAULT_BASE_URL
    }

    /** Raised when user input cannot be parsed into a usable HTTP(S) URL. */
    class ServerUrlError(val input: String) : IllegalArgumentException("Invalid server URL: $input")

    companion object {
        /** Fallback used until the user configures their own server. */
        const val DEFAULT_BASE_URL: String = "http://192.168.10.9:3010/"

        private const val PREFS_NAME = "server_config"
        private const val KEY_BASE_URL = "base_url"

        /**
         * Coerces user input into a canonical base URL.
         *
         * Accepts bare hosts (`192.168.1.5:3010`) by assuming `http://`, and always returns a URL
         * ending in `/` so that OkHttp path-segment appending resolves correctly against it.
         *
         * @return the canonical form, or `null` when [raw] is blank or unparseable.
         */
        fun normalize(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                // Reject other schemes outright rather than silently prefixing http://
                trimmed.contains("://") -> return null
                else -> "http://$trimmed"
            }

            val parsed = withScheme.toHttpUrlOrNull() ?: return null
            if (parsed.host.isBlank()) return null

            val asString = parsed.toString()
            return if (asString.endsWith("/")) asString else "$asString/"
        }
    }
}
