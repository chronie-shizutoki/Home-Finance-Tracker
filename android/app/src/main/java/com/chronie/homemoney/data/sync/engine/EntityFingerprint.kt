package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.sync.protocol.Crc32c
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Content hashing for sync entities.
 *
 * Two hashes with different jobs:
 *
 *  - [hash] is a per-entity CRC-32C that rides in `SyncEntityV2.entity_hash`. Together with
 *    `(entity_id, updated_at)` it forms the idempotency key, so a replayed revision is
 *    recognized and skipped.
 *  - [aggregate] is an SHA-256 over the whole ordered delta, carried in
 *    `ManifestPayload.content_hash`. Per-frame CRCs cannot catch a transfer that lost or
 *    reordered an entire chunk - every surviving frame still checksums correctly - so the
 *    receiver needs one value covering the set as a whole.
 *
 * ### Why a handwritten encoding instead of the protobuf bytes
 *
 * It is tempting to hash `SyncEntityV2.toByteArray()`. Protobuf serialization is not
 * canonical: field ordering, default-value omission and varint padding may all legally
 * differ between runtimes and versions. Two devices would then compute different hashes for
 * identical data and every sync would look corrupted. The encoding below is fully specified
 * here, so any language that follows it reproduces the same bytes.
 *
 * ### Normalisation
 *
 * `remark` is nullable in Room but a plain `string` in proto3, which cannot represent null.
 * A null is therefore normalized to an empty string *before* hashing, so a record keeps the
 * same fingerprint after a round trip through the wire. Hashing the un-normalized form
 * would make every entity with a null remark appear to change on arrival.
 */
object EntityFingerprint {

    /** Matches `SyncOperation` in sync_v2.proto. */
    const val OP_UPSERT: Byte = 1
    const val OP_DELETE: Byte = 2

    /**
     * Canonical byte encoding of one entity revision.
     *
     * Layout, all integers big endian:
     * ```
     *   u32  length of entityType, then its UTF-8 bytes
     *   u32  length of entityId,   then its UTF-8 bytes
     *   u8   operation (1 upsert, 2 delete)
     *   u32  version
     *   i64  updatedAt
     *   i64  deletedAt (0 when live)
     *   u32  length of type,   then its UTF-8 bytes
     *   u32  length of remark, then its UTF-8 bytes (null normalised to empty)
     *   u64  IEEE-754 bits of amount
     *   u32  length of date,   then its UTF-8 bytes
     * ```
     * Length prefixes rather than separators, so a field containing the separator cannot
     * forge a different record with the same encoding.
     */
    fun canonicalBytes(entity: ExpenseEntity, entityType: String = ENTITY_TYPE_EXPENSE): ByteArray {
        val out = ByteArrayOutputStream(128)
        out.writeString(entityType)
        out.writeString(entity.id)
        out.write(if (entity.deletedAt != null) OP_DELETE.toInt() else OP_UPSERT.toInt())
        out.writeInt(entity.version)
        out.writeLong(entity.updatedAt)
        out.writeLong(entity.deletedAt ?: 0L)
        out.writeString(entity.type)
        out.writeString(entity.remark ?: "")
        out.writeLong(java.lang.Double.doubleToLongBits(entity.amount))
        out.writeString(entity.date)
        return out.toByteArray()
    }

    /** CRC-32C of [canonicalBytes]; the value carried in `SyncEntityV2.entity_hash`. */
    fun hash(entity: ExpenseEntity, entityType: String = ENTITY_TYPE_EXPENSE): Int =
        Crc32c.compute(canonicalBytes(entity, entityType))

    /**
     * Ordered aggregate over a delta set.
     *
     * Order sensitive on purpose: the sender and receiver must agree on the sequence, so a
     * reordered transfer is a mismatch rather than a silent pass. Each element contributes
     * its index too, which is what makes reordering detectable at all - a plain XOR or sum
     * of hashes would be permutation invariant.
     */
    fun aggregate(hashes: List<Int>): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val scratch = ByteArray(8)
        for ((index, h) in hashes.withIndex()) {
            scratch.putInt(0, index)
            scratch.putInt(4, h)
            digest.update(scratch)
        }
        return digest.digest()
    }

    const val ENTITY_TYPE_EXPENSE = "expense"

    // -------------------------------------------------------------- encoding

    private fun ByteArrayOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes, 0, bytes.size)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLong(value: Long) {
        for (i in 0 until 8) {
            write(((value ushr (56 - 8 * i)) and 0xFF).toInt())
        }
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }
}
