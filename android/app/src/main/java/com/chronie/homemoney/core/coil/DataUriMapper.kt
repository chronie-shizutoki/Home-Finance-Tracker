package com.chronie.homemoney.core.coil

import android.util.Base64
import coil.map.Mapper
import coil.request.Options

/**
 * Custom Coil Mapper to handle data: URIs (e.g., data:image/png;base64,...).
 * Maps the data: URI string to a ByteArray, which Coil natively supports.
 */
class DataUriMapper : Mapper<String, ByteArray> {

    override fun map(data: String, options: Options): ByteArray? {
        if (!data.startsWith("data:")) return null
        val base64 = data.substringAfter("base64,")
        return Base64.decode(base64, Base64.DEFAULT)
    }
}