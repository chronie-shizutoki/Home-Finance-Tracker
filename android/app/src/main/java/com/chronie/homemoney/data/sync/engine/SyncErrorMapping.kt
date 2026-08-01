package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.sync.generated.SyncError
import com.chronie.homemoney.data.sync.protocol.SyncErrorCode

/**
 * Bridges the two representations of the same error table.
 *
 * There are three copies of this enumeration by necessity - `homemoney::sync::SyncErrorCode`
 * in C++, [SyncErrorCode] in Kotlin and [SyncError] in the proto - because each layer needs
 * it in a form its own tooling understands. The numeric values are identical by contract, so
 * the conversion is a lookup rather than a translation, and doing it in exactly one place is
 * what stops the copies from silently diverging when a code is appended.
 *
 * The proto enum is used on the wire rather than a bare integer purely for readability in a
 * packet capture; nothing depends on it beyond that.
 */
object SyncErrorMapping {

    /** Kotlin error code as the proto enum. Falls back to `INTERNAL` for an unmapped value. */
    fun toProto(code: SyncErrorCode): SyncError =
        SyncError.forNumber(code.code) ?: SyncError.SYNC_ERROR_INTERNAL

    /**
     * Proto enum back to the Kotlin code.
     *
     * `UNRECOGNIZED` has a number of -1 and is what a peer built against a newer schema
     * produces. It maps to `INTERNAL`, which is deliberately non-retryable: an error this
     * build cannot interpret must not be retried on the assumption that it is transient.
     */
    fun fromProto(error: SyncError): SyncErrorCode =
        if (error == SyncError.UNRECOGNIZED) {
            SyncErrorCode.INTERNAL
        } else {
            SyncErrorCode.fromCode(error.number)
        }
}
