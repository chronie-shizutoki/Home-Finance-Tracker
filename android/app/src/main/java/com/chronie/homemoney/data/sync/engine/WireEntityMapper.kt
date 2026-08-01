package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.sync.generated.SyncEntityV2
import com.chronie.homemoney.data.sync.generated.SyncOperation

/**
 * Converts a wire revision into a database row, rejecting anything self-contradictory.
 *
 * v1 pushed each record through `gson.fromJson(entity.data, ExpenseEntity::class.java)`,
 * which happily produced an object with a null id, a zero timestamp or a missing amount and
 * left the caller to notice. Gson also cannot distinguish "field absent" from "field set to
 * its default", so a malformed payload was indistinguishable from a legitimate one. Here the
 * structure is fixed by protobuf and the remaining semantic contradictions are named and
 * rejected explicitly.
 */
object WireEntityMapper {

    /** Why a wire revision could not be turned into a row. */
    enum class RejectReason {
        /** Not an entity type this build knows how to store. */
        UNSUPPORTED_TYPE,

        /** No typed body inside the oneof. */
        MISSING_PAYLOAD,

        /** Neither the envelope nor the body carries an id. */
        MISSING_ID,

        /** `operation` and `deleted_at` disagree about whether the record is a tombstone. */
        INCONSISTENT_TOMBSTONE,

        /** `updated_at` is absent or negative, so the record cannot take part in a merge. */
        INVALID_TIMESTAMP,

        /** The recomputed fingerprint does not match `entity_hash`. */
        HASH_MISMATCH
    }

    sealed interface MapResult {
        data class Mapped(val row: ExpenseEntity) : MapResult
        data class Rejected(val reason: RejectReason, val entityId: String) : MapResult
    }

    /**
     * Maps and integrity-checks one revision.
     *
     * The fingerprint check is the part worth keeping: per-frame CRCs prove the bytes
     * survived the network, but they say nothing about whether the two devices agree on what
     * those bytes *mean*. Recomputing [EntityFingerprint.hash] over the reconstructed row and
     * comparing it with the sender's value verifies the canonical encoding end to end, which
     * is what catches a peer built against a different field set before it corrupts the
     * local database.
     */
    fun map(wire: SyncEntityV2): MapResult {
        val id = wire.entityId.ifEmpty { if (wire.hasExpense()) wire.expense.id else "" }

        if (wire.entityType.isNotEmpty() && wire.entityType != EntityFingerprint.ENTITY_TYPE_EXPENSE) {
            return MapResult.Rejected(RejectReason.UNSUPPORTED_TYPE, id)
        }
        if (!wire.hasExpense()) {
            return MapResult.Rejected(RejectReason.MISSING_PAYLOAD, id)
        }
        if (id.isEmpty()) {
            return MapResult.Rejected(RejectReason.MISSING_ID, id)
        }
        if (wire.updatedAt <= 0L) {
            return MapResult.Rejected(RejectReason.INVALID_TIMESTAMP, id)
        }

        val deletedAt = wire.deletedAt.takeIf { it > 0L }
        val saysDeleted = wire.operation == SyncOperation.SYNC_OPERATION_DELETE
        // Strict rather than forgiving: guessing a tombstone timestamp would change the
        // canonical bytes and make the fingerprint check below fail with a misleading
        // reason, and silently dropping the tombstone flag would resurrect a deleted record.
        if (saysDeleted != (deletedAt != null)) {
            return MapResult.Rejected(RejectReason.INCONSISTENT_TOMBSTONE, id)
        }

        val body = wire.expense
        val row = ExpenseEntity(
            id = id,
            type = body.type,
            // proto3 cannot express null, so the sender normalises null to "". Keeping the
            // empty string is deliberate: EntityFingerprint applies the same normalisation,
            // so the row's fingerprint is stable across a round trip and the record does not
            // look modified every time it crosses the wire.
            remark = body.remark,
            amount = body.amount,
            date = body.date,
            version = wire.version,
            updatedAt = wire.updatedAt,
            deletedAt = deletedAt,
            // Adopted from a peer, so it still has to be reconciled with the backend.
            isSynced = false
        )

        val recomputed = EntityFingerprint.hash(row)
        if (recomputed != wire.entityHash) {
            return MapResult.Rejected(RejectReason.HASH_MISMATCH, id)
        }

        return MapResult.Mapped(row)
    }
}
