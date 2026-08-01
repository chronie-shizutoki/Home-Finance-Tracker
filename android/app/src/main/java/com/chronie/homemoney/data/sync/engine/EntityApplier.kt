package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.sync.generated.ConflictSummary
import com.chronie.homemoney.data.sync.generated.SyncEntityV2
import com.chronie.homemoney.data.sync.merge.ExpenseMerger
import com.chronie.homemoney.data.sync.merge.MergeOutcome

/**
 * Applies a received delta to the local database.
 *
 * This is the v2 counterpart of `BaseDeviceSyncManager.processDeviceData`. The merge rules
 * are unchanged - [ExpenseMerger] stays the single arbiter, so both protocol versions
 * converge on the same winner - but three things around them are new:
 *
 *  1. **Integrity is checked before anything is written.** Every revision is reconstructed
 *     and its fingerprint recomputed ([WireEntityMapper]); a record that does not match what
 *     the sender hashed never reaches the database.
 *  2. **Replays are filtered.** With retries and resume now part of the transport, the same
 *     revision legitimately arrives more than once. [IdempotencyGuard] recognises it.
 *  3. **Conflicts are reported on the wire.** v1 computed a conflict list and then discarded
 *     it, so the peer never learned that its revision had lost. The summaries produced here
 *     travel back inside COMMIT_ACK.
 *
 * The class is a plain suspend API over [SyncEntityStore]; nothing here touches the network,
 * so the whole merge-and-apply path is testable with a fake store.
 */
class EntityApplier(
    private val store: SyncEntityStore,
    private val guard: IdempotencyGuard,
    private val localDeviceId: String
) {

    /**
     * @param received how many revisions arrived.
     * @param inserted records that did not exist locally.
     * @param updated records overwritten because the remote revision won.
     * @param skipped valid revisions that were deliberately not written: an exact replay, an
     *   identical revision, or a merge the local record won.
     * @param rejected revisions that failed validation, keyed by reason in [rejections].
     * @param conflicts genuine divergences, for the peer and for the UI.
     */
    data class ApplyReport(
        val received: Int,
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val rejected: Int,
        val conflicts: List<ConflictSummary>,
        val rejections: Map<WireEntityMapper.RejectReason, Int>
    ) {
        val written: Int get() = inserted + updated

        fun toLogString(): String =
            "received=$received inserted=$inserted updated=$updated skipped=$skipped " +
                    "rejected=$rejected conflicts=${conflicts.size}" +
                    if (rejections.isEmpty()) "" else " reasons=$rejections"
    }

    /**
     * Merges [entities] into the local store and writes the winners in one transaction.
     *
     * @param remoteDeviceId the peer's stable id, used only as the deterministic tie-break
     *   in [ExpenseMerger]. It must be the value from HELLO, not the transport address,
     *   otherwise the two devices compute different tie-breaks and never converge.
     */
    suspend fun apply(entities: List<SyncEntityV2>, remoteDeviceId: String): ApplyReport {
        val rejections = HashMap<WireEntityMapper.RejectReason, Int>()
        var rejected = 0
        var skipped = 0

        // ---- Pass 1: validate, then collapse to one revision per id.
        //
        // Folding before touching the database matters for correctness, not just speed: a
        // resumed transfer can carry the same id in two different chunks, and merging each
        // occurrence against the database in arrival order would make the result depend on
        // which chunk happened to land first.
        val winners = LinkedHashMap<String, ExpenseEntity>()
        val fingerprints = HashMap<String, Int>()

        for (wire in entities) {
            when (val mapped = WireEntityMapper.map(wire)) {
                is WireEntityMapper.MapResult.Rejected -> {
                    rejected++
                    rejections[mapped.reason] = (rejections[mapped.reason] ?: 0) + 1
                }

                is WireEntityMapper.MapResult.Mapped -> {
                    val row = mapped.row
                    val queued = winners[row.id]
                    if (queued == null) {
                        winners[row.id] = row
                        fingerprints[row.id] = wire.entityHash
                    } else {
                        // Both revisions come from the same peer, so the tie-break id is the
                        // same on both sides of the comparison and the rule degenerates to
                        // "keep what is already queued", which is deterministic.
                        val intraBatch = ExpenseMerger.decide(
                            local = queued,
                            remote = row,
                            localDeviceId = remoteDeviceId,
                            remoteDeviceId = remoteDeviceId
                        )
                        if (intraBatch.shouldWrite) {
                            winners[row.id] = row
                            fingerprints[row.id] = wire.entityHash
                        }
                        skipped++
                    }
                }
            }
        }

        // ---- Pass 2: merge each winner against the local row.
        val conflicts = ArrayList<ConflictSummary>()
        val pendingWrites = ArrayList<ExpenseEntity>(winners.size)
        val pendingKeys = ArrayList<Triple<String, Long, Int>>(winners.size)
        var inserted = 0
        var updated = 0

        for (remote in winners.values) {
            val hash = fingerprints[remote.id] ?: 0

            // Checked, not claimed. claimEntity would mark the revision applied before the
            // transaction runs, so a failed write would leave it permanently suppressed. The
            // race it protects against is harmless here anyway: two connections applying the
            // same revision produce byte-identical rows, because the merge is deterministic.
            if (!guard.shouldApplyEntity(remote.id, remote.updatedAt, hash)) {
                skipped++
                continue
            }

            val local = store.load(remote.id)
            val decision = ExpenseMerger.decide(
                local = local,
                remote = remote,
                localDeviceId = localDeviceId,
                remoteDeviceId = remoteDeviceId
            )

            if (decision.shouldWrite) {
                pendingWrites.add(remote)
                pendingKeys.add(Triple(remote.id, remote.updatedAt, hash))
                if (decision.outcome == MergeOutcome.INSERT_NEW) inserted++ else updated++
            } else {
                skipped++
            }

            if (decision.isConflict && local != null) {
                conflicts.add(
                    ConflictSummary.newBuilder()
                        .setEntityType(EntityFingerprint.ENTITY_TYPE_EXPENSE)
                        .setEntityId(remote.id)
                        .setReason(decision.reason.name)
                        .setKeptLocal(decision.outcome == MergeOutcome.KEEP_LOCAL)
                        .setLocalUpdatedAt(local.updatedAt)
                        .setRemoteUpdatedAt(remote.updatedAt)
                        .build()
                )
            }
        }

        // ---- Commit, then remember. Order matters: recording first and failing second
        // would suppress a revision that never made it to disk.
        //
        // The empty case is skipped rather than passed through: a sync where the peer sent
        // nothing new is the common case once two devices are in step, and opening a write
        // transaction to store zero rows would wake Room's invalidation tracker - and every
        // Flow observing the ledger - for no reason.
        if (pendingWrites.isNotEmpty()) {
            store.writeAll(pendingWrites)
        }
        for ((id, updatedAt, hash) in pendingKeys) {
            guard.recordEntityApplied(id, updatedAt, hash)
        }

        return ApplyReport(
            received = entities.size,
            inserted = inserted,
            updated = updated,
            skipped = skipped,
            rejected = rejected,
            conflicts = conflicts,
            rejections = rejections
        )
    }
}
