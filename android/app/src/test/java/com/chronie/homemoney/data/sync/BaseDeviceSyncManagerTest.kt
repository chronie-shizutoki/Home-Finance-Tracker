package com.chronie.homemoney.data.sync

import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.chronie.homemoney.domain.sync.SyncEntity
import com.chronie.homemoney.domain.sync.SyncProgressInfo
import com.chronie.homemoney.domain.sync.SyncRequestCallback
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioural coverage for the receive path of [BaseDeviceSyncManager].
 *
 * Beyond the pure merge rules (see ExpenseMergerTest) this pins down the batch level
 * guarantees the LAN sync relies on:
 *  - a retried or resumed transfer that repeats the same entity id must be idempotent,
 *  - the result must not depend on the order revisions arrive in,
 *  - tombstones must both propagate and resist resurrection,
 *  - a malformed payload must not abort the rest of the batch,
 *  - every accepted revision must be written in a single transaction.
 */
class BaseDeviceSyncManagerTest {

    private val localDevice = "android_aaaa1111"
    private val remoteDevice = "android_bbbb2222"

    private lateinit var dao: ExpenseDao
    private lateinit var manager: TestSyncManager
    private lateinit var writtenBatches: MutableList<List<ExpenseEntity>>

    private val gson = Gson()

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        writtenBatches = mutableListOf()
        coEvery { dao.insertExpenses(capture(writtenBatches)) } returns Unit
        coEvery { dao.getExpenseByIdForSync(any()) } returns null
        manager = TestSyncManager(dao, gson, localDevice)
    }

    // ---------------------------------------------------------------- helpers

    private fun expense(
        id: String = "exp-1",
        type: String = "food",
        amount: Double = 10.0,
        date: String = "2026-01-01",
        remark: String? = null,
        version: Int = 1,
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null,
        isSynced: Boolean = true
    ) = ExpenseEntity(
        id = id,
        type = type,
        remark = remark,
        amount = amount,
        date = date,
        version = version,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        isSynced = isSynced
    )

    private fun wire(
        entity: ExpenseEntity,
        operation: String = if (entity.deletedAt != null) {
            BaseDeviceSyncManager.OP_DELETE
        } else {
            BaseDeviceSyncManager.OP_UPSERT
        },
        entityType: String = BaseDeviceSyncManager.ENTITY_TYPE_EXPENSE,
        payload: String = gson.toJson(entity),
        timestamp: Long = entity.updatedAt
    ) = SyncEntity(
        entityType = entityType,
        entityId = entity.id,
        operation = operation,
        data = payload,
        timestamp = timestamp
    )

    private fun payload(vararg entities: SyncEntity) = DeviceSyncData(
        deviceId = remoteDevice,
        deviceName = "Peer",
        syncTimestamp = 9_999L,
        entities = entities.toList()
    )

    /** All rows handed to the DAO across every write in the run. */
    private fun allWrites(): List<ExpenseEntity> = writtenBatches.flatten()

    private fun writeFor(id: String): ExpenseEntity? = allWrites().firstOrNull { it.id == id }

    // ------------------------------------------------------- batch idempotency

    @Test
    fun `duplicate revisions of the same id collapse to a single write`() = runTest {
        val older = expense(version = 1, updatedAt = 1_000L, amount = 10.0)
        val newer = expense(version = 2, updatedAt = 2_000L, amount = 25.0)

        val result = manager.process(payload(wire(older), wire(newer), wire(older)))

        // Three envelopes were received but they describe one entity.
        assertEquals(3, result.totalItems)
        assertEquals(1, result.newItems)
        assertEquals(0, result.updatedItems)

        assertEquals(1, allWrites().size)
        assertEquals(25.0, writeFor("exp-1")!!.amount, 0.0)
        coVerify(exactly = 1) { dao.insertExpenses(any()) }
    }

    @Test
    fun `batch result does not depend on arrival order`() = runTest {
        val older = expense(version = 1, updatedAt = 1_000L, amount = 10.0)
        val newer = expense(version = 2, updatedAt = 2_000L, amount = 25.0)

        manager.process(payload(wire(newer), wire(older)))
        val newestFirst = writeFor("exp-1")

        setUp()
        manager.process(payload(wire(older), wire(newer)))
        val oldestFirst = writeFor("exp-1")

        assertNotNull(newestFirst)
        assertEquals(newestFirst, oldestFirst)
        assertEquals(25.0, oldestFirst!!.amount, 0.0)
    }

    @Test
    fun `replaying an already applied batch writes nothing the second time`() = runTest {
        val remote = expense(version = 3, updatedAt = 5_000L)

        // Second run sees the record already persisted with the exact same revision.
        coEvery { dao.getExpenseByIdForSync("exp-1") } returns remote

        val result = manager.process(payload(wire(remote)))

        assertEquals(1, result.totalItems)
        assertEquals(0, result.newItems)
        assertEquals(0, result.updatedItems)
        assertTrue(result.conflicts.isEmpty())
        assertTrue(allWrites().isEmpty())
        coVerify(exactly = 0) { dao.insertExpenses(any()) }
    }

    // --------------------------------------------------------- merge behaviour

    @Test
    fun `newer remote revision overwrites the local one`() = runTest {
        coEvery { dao.getExpenseByIdForSync("exp-1") } returns
                expense(version = 1, updatedAt = 1_000L, amount = 10.0)

        val result = manager.process(
            payload(wire(expense(version = 2, updatedAt = 2_000L, amount = 42.0)))
        )

        assertEquals(0, result.newItems)
        assertEquals(1, result.updatedItems)
        assertEquals(42.0, writeFor("exp-1")!!.amount, 0.0)
    }

    @Test
    fun `stale remote revision never clobbers a newer local one`() = runTest {
        coEvery { dao.getExpenseByIdForSync("exp-1") } returns
                expense(version = 5, updatedAt = 9_000L, amount = 99.0)

        val result = manager.process(
            payload(wire(expense(version = 2, updatedAt = 2_000L, amount = 42.0)))
        )

        assertEquals(0, result.newItems)
        assertEquals(0, result.updatedItems)
        assertTrue(allWrites().isEmpty())
        assertEquals(1, result.conflicts.size)
    }

    @Test
    fun `adopted revisions are marked unsynced so the backend still receives them`() = runTest {
        manager.process(payload(wire(expense(isSynced = true))))

        assertFalse(writeFor("exp-1")!!.isSynced)
    }

    // -------------------------------------------------------------- tombstones

    @Test
    fun `remote tombstone deletes the local live record`() = runTest {
        coEvery { dao.getExpenseByIdForSync("exp-1") } returns
                expense(version = 1, updatedAt = 1_000L)

        val result = manager.process(
            payload(wire(expense(version = 1, updatedAt = 3_000L, deletedAt = 3_000L)))
        )

        assertEquals(1, result.updatedItems)
        assertEquals(3_000L, writeFor("exp-1")!!.deletedAt)
    }

    @Test
    fun `a stale remote edit cannot resurrect a local tombstone`() = runTest {
        coEvery { dao.getExpenseByIdForSync("exp-1") } returns
                expense(version = 2, updatedAt = 4_000L, deletedAt = 4_000L)

        val result = manager.process(
            payload(wire(expense(version = 9, updatedAt = 9_000L, amount = 77.0)))
        )

        assertTrue(allWrites().isEmpty())
        assertEquals(0, result.updatedItems)
    }

    @Test
    fun `DELETE operation is honoured even when the payload forgot deletedAt`() = runTest {
        val liveLookingPayload = expense(version = 2, updatedAt = 7_000L, deletedAt = null)
        coEvery { dao.getExpenseByIdForSync("exp-1") } returns expense(version = 1, updatedAt = 1_000L)

        manager.process(
            payload(wire(liveLookingPayload, operation = BaseDeviceSyncManager.OP_DELETE))
        )

        assertEquals(7_000L, writeFor("exp-1")!!.deletedAt)
    }

    // ----------------------------------------------------------- robustness

    @Test
    fun `a malformed payload is counted as failed but does not abort the batch`() = runTest {
        val good = expense(id = "exp-good")
        val broken = SyncEntity(
            entityType = BaseDeviceSyncManager.ENTITY_TYPE_EXPENSE,
            entityId = "exp-broken",
            operation = BaseDeviceSyncManager.OP_UPSERT,
            data = "{not json",
            timestamp = 1_000L
        )

        val result = manager.process(payload(broken, wire(good)))

        assertEquals(2, result.totalItems)
        assertEquals(1, result.newItems)
        assertNotNull(writeFor("exp-good"))
        assertNull(writeFor("exp-broken"))
    }

    @Test
    fun `unsupported entity types are skipped`() = runTest {
        val result = manager.process(
            payload(wire(expense(), entityType = "budget"))
        )

        assertEquals(1, result.totalItems)
        assertEquals(0, result.newItems)
        assertTrue(allWrites().isEmpty())
    }

    @Test
    fun `an empty payload performs no write at all`() = runTest {
        val result = manager.process(payload())

        assertEquals(0, result.totalItems)
        coVerify(exactly = 0) { dao.insertExpenses(any()) }
    }

    @Test
    fun `all accepted revisions are written in one transaction`() = runTest {
        val entities = (1..5).map { wire(expense(id = "exp-$it")) }.toTypedArray()

        val result = manager.process(payload(*entities))

        assertEquals(5, result.newItems)
        assertEquals(1, writtenBatches.size)
        assertEquals(5, writtenBatches.first().size)
    }

    // -------------------------------------------------------------- send path

    @Test
    fun `prepared payload carries tombstones and real modification times`() = runTest {
        coEvery { dao.getAllExpensesForSync() } returns listOf(
            expense(id = "exp-live", updatedAt = 1_111L),
            expense(id = "exp-dead", updatedAt = 2_222L, deletedAt = 2_222L)
        )

        val data = manager.prepare()

        assertEquals(localDevice, data.deviceId)
        assertEquals(2, data.entities.size)

        val live = data.entities.first { it.entityId == "exp-live" }
        assertEquals(BaseDeviceSyncManager.OP_UPSERT, live.operation)
        // The envelope must carry the record's own updatedAt, not "now".
        assertEquals(1_111L, live.timestamp)

        val dead = data.entities.first { it.entityId == "exp-dead" }
        assertEquals(BaseDeviceSyncManager.OP_DELETE, dead.operation)
        assertEquals(2_222L, dead.timestamp)
    }

    @Test
    fun `a prepared payload round-trips through the receive path unchanged`() = runTest {
        val rows = listOf(
            expense(id = "exp-live", version = 3, updatedAt = 1_111L, amount = 12.5),
            expense(id = "exp-dead", version = 2, updatedAt = 2_222L, deletedAt = 2_222L)
        )
        coEvery { dao.getAllExpensesForSync() } returns rows

        val onTheWire = manager.prepare()

        // Feed the sender's own payload into a fresh peer with an empty database.
        setUp()
        val result = manager.process(onTheWire.copy(deviceId = remoteDevice))

        assertEquals(2, result.newItems)
        assertEquals(12.5, writeFor("exp-live")!!.amount, 0.0)
        assertEquals(3, writeFor("exp-live")!!.version)
        assertEquals(2_222L, writeFor("exp-dead")!!.deletedAt)
    }

    /**
     * Minimal concrete manager: the base class only leaves the UI-facing progress and
     * pairing callbacks unimplemented, which the tests do not exercise.
     */
    private class TestSyncManager(
        dao: ExpenseDao,
        gson: Gson,
        override val localDeviceId: String,
        override val localDeviceName: String = "Test Device"
    ) : BaseDeviceSyncManager(dao, gson) {

        private val progress = MutableStateFlow(SyncProgressInfo())
        override val syncProgress: StateFlow<SyncProgressInfo> = progress

        override fun updateSyncProgress(progress: Float, message: String, isActive: Boolean) {
            this.progress.value = SyncProgressInfo(progress, message, isActive)
        }

        override fun clearSyncProgress() {
            progress.value = SyncProgressInfo()
        }

        override fun setSyncRequestCallback(callback: SyncRequestCallback?) = Unit

        override fun respondToSyncRequest(accepted: Boolean) = Unit

        suspend fun process(data: DeviceSyncData) = processDeviceData(data)

        suspend fun prepare() = prepareLocalData()
    }
}
