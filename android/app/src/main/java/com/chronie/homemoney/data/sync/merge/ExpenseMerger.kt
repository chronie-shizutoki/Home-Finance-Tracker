package com.chronie.homemoney.data.sync.merge

import com.chronie.homemoney.data.local.entity.ExpenseEntity

/**
 * Outcome of merging a remote expense record against the local one.
 */
enum class MergeOutcome {
    /** No local record exists, the remote record must be inserted. */
    INSERT_NEW,

    /** The remote record wins and must overwrite the local one. */
    APPLY_REMOTE,

    /** The local record wins, the remote record is discarded. */
    KEEP_LOCAL,

    /** Both sides already hold the same revision, nothing to do. */
    IDENTICAL
}

/**
 * Why a particular [MergeOutcome] was chosen. Surfaced in logs and metrics so that a
 * surprising merge result can be traced back to the exact rule that produced it.
 */
enum class MergeReason {
    NO_LOCAL_RECORD,
    SAME_REVISION,
    TOMBSTONE_PRIORITY,
    HIGHER_VERSION,
    NEWER_TIMESTAMP,
    DEVICE_ID_TIE_BREAK
}

data class MergeDecision(
    val outcome: MergeOutcome,
    val reason: MergeReason
) {
    /** True when the remote revision must be written to the local database. */
    val shouldWrite: Boolean
        get() = outcome == MergeOutcome.INSERT_NEW || outcome == MergeOutcome.APPLY_REMOTE

    /**
     * True when the two sides genuinely diverged and one revision had to be dropped.
     * Only these cases are worth reporting to the user as conflicts.
     */
    val isConflict: Boolean
        get() = outcome == MergeOutcome.KEEP_LOCAL ||
                (outcome == MergeOutcome.APPLY_REMOTE && reason != MergeReason.NEWER_TIMESTAMP)
}

/**
 * Deterministic last-writer-wins merge strategy shared by both peers of a LAN sync.
 *
 * The rules are evaluated in a fixed order and are *symmetric*: given the same pair of
 * records, the initiator and the responder always elect the same winner. This is what
 * guarantees the two databases converge instead of ping-ponging updates forever.
 *
 * Priority order:
 *  1. Tombstone priority  - a deletion always beats a live record, so a deleted entry can
 *                           never be resurrected by a stale edit from the other device.
 *  2. Higher version      - the explicit revision counter is the strongest signal.
 *  3. Newer updatedAt     - falls back to wall-clock modification time.
 *  4. Device id ordering  - final deterministic tie-break; the lexicographically greater
 *                           device id wins so that both sides compute the same answer.
 */
object ExpenseMerger {

    /**
     * Decide what to do with [remote] given the current [local] record.
     *
     * @param local the local revision, or null when the record is unknown locally.
     * @param remote the revision received from the peer.
     * @param localDeviceId stable id of this device, used only for tie-breaking.
     * @param remoteDeviceId stable id of the peer device, used only for tie-breaking.
     */
    fun decide(
        local: ExpenseEntity?,
        remote: ExpenseEntity,
        localDeviceId: String,
        remoteDeviceId: String
    ): MergeDecision {
        if (local == null) {
            return MergeDecision(MergeOutcome.INSERT_NEW, MergeReason.NO_LOCAL_RECORD)
        }

        if (isSameRevision(local, remote)) {
            return MergeDecision(MergeOutcome.IDENTICAL, MergeReason.SAME_REVISION)
        }

        // Rule 1: tombstone priority.
        val localDeleted = local.deletedAt != null
        val remoteDeleted = remote.deletedAt != null
        if (localDeleted != remoteDeleted) {
            return if (remoteDeleted) {
                MergeDecision(MergeOutcome.APPLY_REMOTE, MergeReason.TOMBSTONE_PRIORITY)
            } else {
                MergeDecision(MergeOutcome.KEEP_LOCAL, MergeReason.TOMBSTONE_PRIORITY)
            }
        }

        // Rule 2: explicit revision counter.
        if (remote.version != local.version) {
            return if (remote.version > local.version) {
                MergeDecision(MergeOutcome.APPLY_REMOTE, MergeReason.HIGHER_VERSION)
            } else {
                MergeDecision(MergeOutcome.KEEP_LOCAL, MergeReason.HIGHER_VERSION)
            }
        }

        // Rule 3: wall-clock modification time.
        if (remote.updatedAt != local.updatedAt) {
            return if (remote.updatedAt > local.updatedAt) {
                MergeDecision(MergeOutcome.APPLY_REMOTE, MergeReason.NEWER_TIMESTAMP)
            } else {
                MergeDecision(MergeOutcome.KEEP_LOCAL, MergeReason.NEWER_TIMESTAMP)
            }
        }

        // Rule 4: deterministic tie-break so both peers converge on the same winner.
        return if (remoteDeviceId > localDeviceId) {
            MergeDecision(MergeOutcome.APPLY_REMOTE, MergeReason.DEVICE_ID_TIE_BREAK)
        } else {
            MergeDecision(MergeOutcome.KEEP_LOCAL, MergeReason.DEVICE_ID_TIE_BREAK)
        }
    }

    /**
     * Two revisions are considered identical when every field that participates in the
     * merge decision matches. Content fields are compared as well so that an equal
     * version/timestamp pair carrying different data is still treated as a divergence.
     */
    private fun isSameRevision(local: ExpenseEntity, remote: ExpenseEntity): Boolean {
        return local.version == remote.version &&
                local.updatedAt == remote.updatedAt &&
                local.deletedAt == remote.deletedAt &&
                local.type == remote.type &&
                local.amount == remote.amount &&
                local.date == remote.date &&
                local.remark == remote.remark
    }
}
