package com.chronie.homemoney.data.remote.interceptor

import com.chronie.homemoney.data.local.ServerConfigManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Redirects every request to the server address currently configured by the user.
 *
 * Retrofit resolves each `@GET("api/...")` against the base URL baked in at construction time, and
 * a `Retrofit` instance is immutable once built. Rather than rebuilding the whole object graph
 * whenever the address changes, this interceptor swaps the origin on the outgoing request, so the
 * value in [ServerConfigManager] takes effect on the very next call.
 *
 * The configured URL may carry a path prefix (e.g. `https://example.com/finance/`) for setups
 * behind a reverse proxy. Appending the request path as encoded segments preserves that prefix —
 * `/finance/` + `api/expenses` resolves to `/finance/api/expenses`.
 */
class ServerUrlInterceptor @Inject constructor(
    private val serverConfigManager: ServerConfigManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Values are normalized on write, so a parse failure here means storage was tampered
        // with. Fall through to the compiled-in base URL rather than failing the call.
        val target = serverConfigManager.currentBaseUrl.toHttpUrlOrNull()
            ?: return chain.proceed(originalRequest)

        val originalUrl = originalRequest.url

        val rewrittenUrl = target.newBuilder()
            .addEncodedPathSegments(originalUrl.encodedPath.trimStart('/'))
            .encodedQuery(originalUrl.encodedQuery)
            .fragment(originalUrl.fragment)
            .build()

        if (rewrittenUrl == originalUrl) {
            return chain.proceed(originalRequest)
        }

        return chain.proceed(
            originalRequest.newBuilder()
                .url(rewrittenUrl)
                .build()
        )
    }
}
