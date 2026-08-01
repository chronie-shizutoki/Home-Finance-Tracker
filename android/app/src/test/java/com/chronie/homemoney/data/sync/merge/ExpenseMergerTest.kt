package com.chronie.homemoney.data.sync.merge

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the deterministic LAN merge strategy.
 *
 * These tests pin down the bugs found during the sync refactor:
 *  - F1: a remote update of an existing record must actually be applied. The previous
 *        implementation compared the remote timestamp against System.currentTimeMillis(),
 *        which made the comparison always false and silently dropped every remote edit.
 *  - F3: deletions must propagate through tombstones instead of being invisible.
 *  - F4: a newer local record must never be clobbered by a stale remote one.
 */
class ExpenseMergerTest {

    private val deviceA = "android_aaaa1111"
    private val deviceB = "android_bbbb2222"

    private fun expense(
        id: String = "exp-1",
        type: String = "food",
        amount: Double = 10.0,
        date: String = "2026-01-01",
        remark: String? = null,
        version: Int = 1,
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null
    ) = ExpenseEntity(
        id = id,
        type = type,
        remark = remark,
        amount = amount,
        date = date,
        version = version,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        isSynced = false
    )

    private fun decideOnA(local: ExpenseEntity?, remote: ExpenseEntity) =
        ExpenseMerger.decide(local, remote, localDeviceId = deviceA, remoteDeviceId = deviceB)

    private fun decideOnB(local: ExpenseEntity?, remote: ExpenseEntity) =
        ExpenseMerger.decide(local, remote, localDeviceId = deviceB, remoteDeviceId = deviceA)

    @Test
    fun `unknown record is inserted`() {
        val decision = decideOnA(local = null, remote = expense())

        assertEquals(MergeOutcome.INSERT_NEW, decision.outcome)
        assertEquals(MergeReason.NO_LOCAL_RECORD, decision.reason)
        assertTrue(decision.shouldWrite)
        assertFalse(decision.isConflict)
    }

    @Test
    fun `F1 regression - newer remote revision overwrites the local one`() {
        val local = expense(updatedAt = 1_000L, amount = 10.0)
        val remote = expense(updatedAt = 2_000L, amount = 42.0)

        val decision = decideOnA(local, remote)

        assertEquals(MergeOutcome.APPLY_REMOTE, decision.outcome)
        assertEquals(MergeReason.NEWER_TIMESTAMP, decision.reason)
        assertTrue(decision.shouldWrite)
    }

    @Test
    fun `F4 regression - stale remote revision never clobbers a newer local one`() {
        val local = expense(updatedAt = 5_000L, amount = 99.0)
        val remote = expense(updatedAt = 2_000L, amount = 42.0)

        val decision = decideOnA(local, remote)

        assertEquals(MergeOutcome.KEEP_LOCAL, decision.outcome)
        assertFalse(decision.shouldWrite)
        assertTrue(decision.isConflict)
    }

    @Test
    fun `identical revisions produce no write`() {
        val local = expense(updatedAt = 1_000L, version = 3)
        val remote = expense(updatedAt = 1_000L, version = 3)

        val decision = decideOnA(local, remote)

        assertEquals(MergeOutcome.IDENTICAL, decision.outcome)
        assertFalse(decision.shouldWrite)
        assertFalse(decision.isConflict)
    }

    @Test
    fun `explicit version beats wall clock time`() {
        // Remote has an older clock but a higher revision counter: version must win so a
        // device with a skewed clock cannot silently lose an edit.
        val local = expense(version = 1, updatedAt = 9_000L)
        val remote = expense(version = 2, updatedAt = 1_000L)

        val decision = decideOnA(local, remote)

        assertEquals(MergeOutcome.APPLY_REMOTE, decision.outcome)
        assertEquals(MergeReason.HIGHER_VERSION, decision.reason)
    }

    @Test
    fun `F3 regression - remote tombstone deletes the local live record`() {
        val local = expense(updatedAt = 9_000L, deletedAt = null)
        val remote = expense(updatedAt = 1_000L, deletedAt = 1_000L)

        val decision = decideOnA(local, remote)

        assertEquals(MergeOutcome.APPLY_REMOTE, decision.outcome)
        assertEquals(MergeReason.TOMBSTONE_PRIORITY, decision.reason)
        assertTrue(decision.shouldWrite)
    }

    @Test
    fun `F3 regression - a stale remote edit cannot resurrect a local tombstone`() {
        val local = expense(updatedAt = 1_000L, deletedAt = 1_000L)
        val remote = expense(updatedAt = 9_000L, deletedAt = null)

        val decision = decideOnA(local, remote)

        assertEquals(MergeOutcome.KEEP_LOCAL, decision.outcome)
        assertEquals(MergeReason.TOMBSTONE_PRIORITY, decision.reason)
        assertFalse(decision.shouldWrite)
    }

    @Test
    fun `tie break is symmetric so both peers converge`() {
        // Same version, same timestamp, different content. Exactly one side must yield.
        val onA = expense(updatedAt = 1_000L, version = 1, amount = 10.0)
        val onB = expense(updatedAt = 1_000L, version = 1, amount = 20.0)

        val decisionOnA = decideOnA(local = onA, remote = onB)
        val decisionOnB = decideOnB(local = onB, remote = onA)

        assertEquals(MergeReason.DEVICE_ID_TIE_BREAK, decisionOnA.reason)
        assertEquals(MergeReason.DEVICE_ID_TIE_BREAK, decisionOnB.reason)

        // deviceB > deviceA lexicographically, so B's revision wins on both sides.
        assertEquals(MergeOutcome.APPLY_REMOTE, decisionOnA.outcome)
        assertEquals(MergeOutcome.KEEP_LOCAL, decisionOnB.outcome)

        // Exactly one of the two peers performs a write - that is what convergence means.
        assertTrue(decisionOnA.shouldWrite != decisionOnB.shouldWrite)
    }

    @Test
    fun `both sides elect the same winner for a normal divergence`() {
        val older = expense(updatedAt = 1_000L, amount = 10.0)
        val newer = expense(updatedAt = 2_000L, amount = 20.0)

        // A holds the older revision, B holds the newer one.
        val decisionOnA = decideOnA(local = older, remote = newer)
        val decisionOnB = decideOnB(local = newer, remote = older)

        assertEquals(MergeOutcome.APPLY_REMOTE, decisionOnA.outcome)
        assertEquals(MergeOutcome.KEEP_LOCAL, decisionOnB.outcome)
    }

    @Test
    fun `equal timestamp and version with equal content is not a conflict`() {
        val local = expense(updatedAt = 1_000L, version = 1, amount = 10.0, remark = "x")
        val remote = expense(updatedAt = 1_000L, version = 1, amount = 10.0, remark = "x")

        assertEquals(MergeOutcome.IDENTICAL, decideOnA(local, remote).outcome)
    }

    @Test
    fun `both sides deleted is treated as identical when revisions match`() {
        val local = expense(updatedAt = 3_000L, deletedAt = 3_000L)
        val remote = expense(updatedAt = 3_000L, deletedAt = 3_000L)

        assertEquals(MergeOutcome.IDENTICAL, decideOnA(local, remote).outcome)
    }

    @Test
    fun `later deletion wins over earlier deletion`() {
        val local = expense(updatedAt = 1_000L, deletedAt = 1_000L)
        val remote = expense(updatedAt = 4_000L, deletedAt = 4_000L)

        val decision = decideOnA(local, remote)

        assertEquals(MergeOutcome.APPLY_REMOTE, decision.outcome)
        assertEquals(MergeReason.NEWER_TIMESTAMP, decision.reason)
    }
}
