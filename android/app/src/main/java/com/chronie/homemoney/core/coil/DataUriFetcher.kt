package com.chronie.homemoney.core.coil

import android.util.Base64
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

class DataUriFetcher(
    private val data: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val base64 = data.substringAfter("base64,")
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val source = ImageSource(
            source = Buffer().write(bytes),
            fileSystem = FileSystem.SYSTEM,
            metadata = null,
        )
        val mimeType = data.substringAfter("data:").substringBefore(";").takeIf { it.isNotEmpty() }
        return SourceFetchResult(
            source = source,
            mimeType = mimeType,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.startsWith("data:")) {
                DataUriFetcher(data, options)
            } else {
                null
            }
        }
    }
}
