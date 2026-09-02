package com.chronie.homemoney.data.sync.engine

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.sync.auth.SyncAuthorizer
import com.chronie.homemoney.data.sync.generated.SyncCapability
import com.chronie.homemoney.data.sync.protocol.SyncWireProtocol
import com.chronie.homemoney.data.sync.session.SyncSessionRegistry
import com.chronie.homemoney.data.sync.transport.InMemorySyncTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The initiator end-to-end, against a real [SyncResponder] reached through
 * [InMemorySyncTransport] - the JVM twin of the on-device path the user reported broken.
 *
 * Before the v2 handshake was implemented on the client, the native `exchangeV2` only ever
 * emitted a bare COMMIT frame. The responder therefore never opened a session, never showed
 * its confirmation dialog, and the sync died at "10% / 100% failed". These tests pin down the
 * fix: the initiator now drives HELLO -> AUTH -> MANIFEST -> CHUNK(s) -> COMMIT and then pulls
 * the peer's delta, so both ledgers converge.
 */
class SyncInitiatorTest {

    private val initiatorId = "initiator-device"
    private val responderId = "peer-device-id"

    @Test
    fun `a full bidirectional sync converges both ledgers`() = runBlocking {
        val aStore = FakeStore()
        aStore.seed(
            expense("a", amount = 10.0, updatedAt = 1_000),
            expense("b", amount = 20.0, updatedAt = 2_000)
        )
        val bStore = FakeStore()
        bStore.seed(
            expense("x", amount = 5.0, updatedAt = 900),
            expense("y", amount = 7.0, updatedAt = 1_500)
        )

        val responder = newResponder(bStore)
        val transport = InMemorySyncTransport(responder)
        val initiator = newInitiator(aStore)

        val outcome = initiator.sync(transport, "test")

        assertTrue("sync must succeed: ${outcome.errorMessage}", outcome.success)
        assertEquals(2, outcome.uploadedEntities)
        assertEquals(2, outcome.downloadedEntities)

        // A's push landed on B.
        assertEquals(setOf("a", "b", "x", "y"), bStore.rows.keys)
        // B's delta landed on A.
        assertEquals(setOf("a", "b", "x", "y"), aStore.rows.keys)
        assertEquals(10.0, aStore.rows.getValue("a").amount, 0.0)
        assertEquals(7.0, aStore.rows.getValue("y").amount, 0.0)
    }

    @Test
    fun `two pushes of the same data are idempotent on both sides`() = runBlocking {
        val aStore = FakeStore()
        aStore.seed(expense("a", amount = 10.0, updatedAt = 1_000))
        val bStore = FakeStore()

        // The responder (and its idempotency guard) is the same instance across both syncs,
        // exactly as it is on a real device, so a replayed push is recognised and skipped.
        val responder = newResponder(bStore)
        val first = newInitiator(aStore).sync(InMemorySyncTransport(responder), "test")
        val second = newInitiator(aStore).sync(InMemorySyncTransport(responder), "test")

        assertTrue(first.success)
        assertTrue("a replayed sync must also succeed: ${second.errorMessage}", second.success)
        // B saw exactly one applied write per entity regardless of how many times A synced.
        assertEquals(1, bStore.writeCalls)
        assertEquals(setOf("a"), bStore.rows.keys)
    }

    @Test
    fun `an empty ledger on both sides still completes cleanly`() = runBlocking {
        val aStore = FakeStore()
        val bStore = FakeStore()
        val responder = newResponder(bStore)

        val outcome = newInitiator(aStore).sync(InMemorySyncTransport(responder), "test")

        assertTrue("empty sync must succeed: ${outcome.errorMessage}", outcome.success)
        assertEquals(0, outcome.uploadedEntities)
        assertEquals(0, outcome.downloadedEntities)
        assertTrue(aStore.rows.isEmpty())
        assertTrue(bStore.rows.isEmpty())
    }

    @Test
    fun `a pairing code authenticates both directions`() = runBlocking {
        val code = "AB-12 cd"
        val aStore = FakeStore()
        aStore.seed(expense("a", amount = 10.0, updatedAt = 1_000))
        val bStore = FakeStore()
        bStore.seed(expense("x", amount = 5.0, updatedAt = 900))

        // Responder requires the proof (it has the code) and does not trust the initiator yet.
        val responder = SyncResponder(
            store = bStore,
            identity = { SyncIdentity(responderId, "Peer Phone") },
            authorizer = FakeAuthorizer(code = code, trusted = mutableSetOf()),
            registry = SyncSessionRegistry(),
            guard = IdempotencyGuard(),
            observer = SyncResponderObserver.NONE
        )
        // Initiator knows the same code, so it advertises PAIRING and proves it.
        val initiator = SyncInitiator(
            store = aStore,
            identity = { SyncIdentity(initiatorId, "My Phone") },
            authorizer = FixedCodeAuthorizer(code)
        )

        val outcome = initiator.sync(InMemorySyncTransport(responder), "test")

        assertTrue("paired sync must succeed: ${outcome.errorMessage}", outcome.success)
        assertEquals(setOf("a", "x"), bStore.rows.keys)
        assertEquals(setOf("a", "x"), aStore.rows.keys)
    }

    @Test
    fun `a wrong pairing code is refused before any data moves`() = runBlocking {
        val aStore = FakeStore()
        aStore.seed(expense("a", amount = 10.0, updatedAt = 1_000))
        val bStore = FakeStore()
        bStore.seed(expense("x", amount = 5.0, updatedAt = 900))

        val responder = SyncResponder(
            store = bStore,
            identity = { SyncIdentity(responderId, "Peer Phone") },
            authorizer = FakeAuthorizer(code = "RIGHT-CODE", trusted = mutableSetOf()),
            registry = SyncSessionRegistry(),
            guard = IdempotencyGuard(),
            observer = SyncResponderObserver.NONE
        )
        val initiator = SyncInitiator(
            store = aStore,
            identity = { SyncIdentity(initiatorId, "My Phone") },
            authorizer = FixedCodeAuthorizer("WRONG-CODE")
        )

        val outcome = initiator.sync(InMemorySyncTransport(responder), "test")

        assertFalse("mismatched pairing code must fail", outcome.success)
        // Nothing should have been exchanged.
        assertEquals(setOf("a"), aStore.rows.keys)
        assertEquals(setOf("x"), bStore.rows.keys)
    }

    // ================================================================ helpers

    private fun newResponder(store: FakeStore): SyncResponder =
        SyncResponder(
            store = store,
            identity = { SyncIdentity(responderId, "Peer Phone") },
            authorizer = FakeAuthorizer(trusted = mutableSetOf()),
            registry = SyncSessionRegistry(),
            guard = IdempotencyGuard(),
            observer = SyncResponderObserver.NONE
        )

    private fun newInitiator(store: FakeStore): SyncInitiator =
        SyncInitiator(
            store = store,
            identity = { SyncIdentity(initiatorId, "My Phone") },
            authorizer = SyncAuthorizer.DENY_ALL
        )

    /** In-memory [SyncEntityStore]. Tombstone-aware, exactly like the Room implementation. */
    private class FakeStore : SyncEntityStore {
        val rows = LinkedHashMap<String, ExpenseEntity>()
        var writeCalls = 0
        var failNextWrite = false

        fun seed(vararg entities: ExpenseEntity) {
            entities.forEach { rows[it.id] = it }
        }

        override suspend fun load(entityId: String): ExpenseEntity? = rows[entityId]

        override suspend fun writeAll(rows: List<ExpenseEntity>) {
            if (failNextWrite) {
                failNextWrite = false
                throw IllegalStateException("simulated write failure")
            }
            writeCalls++
            rows.forEach { this.rows[it.id] = it }
        }

        override suspend fun changedSince(watermark: Long): List<ExpenseEntity> =
            if (watermark <= 0L) snapshot() else snapshot().filter { it.updatedAt > watermark }

        override suspend fun snapshot(): List<ExpenseEntity> =
            rows.values.sortedWith(compareBy({ it.updatedAt }, { it.id }))
    }

    private class FakeAuthorizer(
        private val code: String? = null,
        private val trusted: MutableSet<String> = mutableSetOf(),
        private val decision: SyncAuthorizer.Decision = SyncAuthorizer.Decision.ACCEPTED
    ) : SyncAuthorizer {
        override fun pairingCode(): String? = code
        override fun isTrusted(deviceId: String): Boolean = deviceId in trusted
        override fun confirm(request: SyncAuthorizer.Request, timeoutMs: Long): SyncAuthorizer.Decision = decision
        override fun remember(deviceId: String, deviceName: String) {
            trusted += deviceId
        }
    }

    /** An authorizer that only knows a pairing code; used to drive the initiator's proof. */
    private class FixedCodeAuthorizer(private val code: String) : SyncAuthorizer {
        override fun pairingCode(): String? = code
        override fun isTrusted(deviceId: String): Boolean = false
        override fun confirm(request: SyncAuthorizer.Request, timeoutMs: Long): SyncAuthorizer.Decision =
            SyncAuthorizer.Decision.ACCEPTED

        override fun remember(deviceId: String, deviceName: String) = Unit
    }

    private fun expense(
        id: String,
        amount: Double = 12.5,
        updatedAt: Long = 1_700_000_000_000L,
        version: Int = 1,
        deletedAt: Long? = null,
        remark: String? = "lunch",
        type: String = "FOOD",
        date: String = "2026-08-01"
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
}
