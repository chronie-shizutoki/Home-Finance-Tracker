package com.chronie.homemoney.core.coil

import android.util.Base64
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

/**
 * A [Fetcher] that handles `data:` URI scheme images for Coil.
 * Decodes the Base64-encoded payload embedded in the URI and returns an in-memory [ImageSource].
 */
class DataUriFetcher(
    private val data: Uri
) : Fetcher {

    /**
     * Decodes the Base64 body of the `data:` URI and returns a [SourceFetchResult]
     * with the decoded bytes and the extracted MIME type.
     */
    override suspend fun fetch(): FetchResult {
        val uriString = data.toString()
        val base64 = uriString.substringAfter("base64,")
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val source = ImageSource(
            source = Buffer().write(bytes),
            fileSystem = FileSystem.SYSTEM,
            metadata = null,
        )
        val mimeType = uriString.substringAfter("data:").substringBefore(";").takeIf { it.isNotEmpty() }
        return SourceFetchResult(
            source = source,
            mimeType = mimeType,
            dataSource = DataSource.MEMORY,
        )
    }

    /**
     * [Fetcher.Factory] implementation that creates a [DataUriFetcher] when the input
     * is a [Uri] with the `data` scheme.
     */
    class Factory : Fetcher.Factory<Any> {
        /**
         * Returns a [DataUriFetcher] if [data] is a [Uri] whose scheme is `"data"`,
         * or `null` otherwise.
         */
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data is Uri && data.scheme == "data") {
                DataUriFetcher(data)
            } else {
                null
            }
        }
    }
}
