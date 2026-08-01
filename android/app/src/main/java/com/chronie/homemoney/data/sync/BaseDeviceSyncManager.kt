package com.chronie.homemoney.data.sync

import android.util.Log
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.domain.model.ConflictResolution
import com.chronie.homemoney.domain.model.ConflictType
import com.chronie.homemoney.domain.model.DownloadResult
import com.chronie.homemoney.domain.model.SyncConflict
import com.chronie.homemoney.domain.model.SyncResult
import com.chronie.homemoney.domain.model.UploadResult
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.chronie.homemoney.domain.sync.DeviceSyncManager
import com.chronie.homemoney.domain.sync.SyncEntity
import com.chronie.homemoney.data.sync.merge.ExpenseMerger
import com.chronie.homemoney.data.sync.merge.MergeOutcome
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Base Device Sync Manager Abstract Class
 * Provides common sync logic for device-to-device communication
 */
abstract class BaseDeviceSyncManager(
    protected val expenseDao: ExpenseDao,
    protected val gson: Gson
) : DeviceSyncManager {
    
    protected val tag: String = this::class.java.simpleName
    protected var isConnected = false
    protected var currentDevice: DeviceInfo? = null

    companion object {
        const val ENTITY_TYPE_EXPENSE = "expense"

        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"

        /** Legacy operation value still emitted by v1 peers. */
        const val OP_CREATE = "CREATE"
    }

    /**
     * Stable identifier of this device. Used both as the sync envelope sender id and as
     * the deterministic tie-breaker in [ExpenseMerger]. Subclasses that own a persisted
     * device id must override this.
     */
    protected open val localDeviceId: String get() = "local_device"

    /** Human readable name of this device, shown to the peer. */
    protected open val localDeviceName: String get() = "Local Android Device"
    
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        // Default implementation, subclass must override this
        Log.d(tag, "Default searchDevices implementation")
    }
    
    override suspend fun connect(device: DeviceInfo): Boolean {
        Log.d(tag, "Connecting to device: ${device.deviceName}")
        isConnected = true
        currentDevice = device
        return true
    }
    
    override suspend fun disconnect(): Boolean {
        Log.d(tag, "Disconnecting from device")
        isConnected = false
        currentDevice = null
        return true
    }
    
    override suspend fun sendData(data: DeviceSyncData): Boolean {
        Log.d(tag, "Sending data to device: ${data.deviceName}")
        return true
    }
    
    override suspend fun receiveData(): DeviceSyncData? {
        Log.d(tag, "Receiving data from device")
        return null
    }
    
    override suspend fun syncWithDevice(device: DeviceInfo): SyncResult {
        Log.d(tag, "Starting sync with device: ${device.deviceName}")
        
        return try {
            // 1. Connect to device
            if (!connect(device)) {
                return createFailedSyncResult("Failed to connect to device")
            }
            
            // 2. Prepare local data for sync
            val localData = prepareLocalData()
            
            // 3. Send local data to device
            if (!sendData(localData)) {
                disconnect()
                return createFailedSyncResult("Failed to send data to device")
            }
            
            // 4. Receive device data
            val deviceData = receiveData()
            if (deviceData == null) {
                disconnect()
                return createFailedSyncResult("Failed to receive data from device")
            }
            
            // 5. Process device data
            val downloadResult = processDeviceData(deviceData)
            
            // 6. Disconnect from device
            disconnect()
            
            // 7. Return sync result
            SyncResult(
                success = true,
                uploadResult = UploadResult(
                    totalItems = localData.entities.size,
                    successCount = localData.entities.size,
                    failedCount = 0
                ),
                downloadResult = downloadResult,
                conflicts = downloadResult.conflicts
            )
        } catch (e: Exception) {
            Log.e(tag, "Sync with device failed", e)
            disconnect()
            createFailedSyncResult(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Prepare local data for sync.
     *
     * Two things differ from a naive snapshot and both are required for convergence:
     *  - Soft-deleted rows are included as tombstones, otherwise deletions never reach
     *    the peer and the databases drift apart forever.
     *  - The envelope timestamp carries the record's real modification time instead of
     *    "now", so that the receiver can actually compare revisions.
     */
    protected open suspend fun prepareLocalData(): DeviceSyncData {
        val allExpenses = expenseDao.getAllExpensesForSync()
        val entities = ArrayList<SyncEntity>(allExpenses.size)

        for (expense in allExpenses) {
            entities.add(
                SyncEntity(
                    entityType = ENTITY_TYPE_EXPENSE,
                    entityId = expense.id,
                    operation = if (expense.deletedAt != null) OP_DELETE else OP_UPSERT,
                    data = gson.toJson(expense),
                    timestamp = expense.updatedAt
                )
            )
        }

        Log.d(
            tag,
            "Prepared ${entities.size} entities for sync " +
                    "(${entities.count { it.operation == OP_DELETE }} tombstones)"
        )

        return DeviceSyncData(
            deviceId = localDeviceId,
            deviceName = localDeviceName,
            syncTimestamp = System.currentTimeMillis(),
            entities = entities
        )
    }
    
    /**
     * Process data received from a peer device.
     *
     * The incoming batch is first de-duplicated (a payload may legitimately contain the
     * same entity id more than once after a retry), then every surviving revision is run
     * through [ExpenseMerger] against the local row - including local tombstones - and
     * the winners are written in a single transaction.
     */
    protected suspend fun processDeviceData(deviceData: DeviceSyncData): DownloadResult {
        val totalEntities = deviceData.entities.size
        Log.d(tag, "Processing $totalEntities entities from device ${deviceData.deviceName}")

        val remoteDeviceId = deviceData.deviceId.ifEmpty { "unknown_device" }

        // Pass 1: collapse the batch to one winning revision per entity id. A retried or
        // resumed transfer can legitimately carry the same id twice; folding first keeps
        // the result independent of arrival order and avoids double counting.
        val winners = LinkedHashMap<String, ExpenseEntity>()
        var failedItems = 0

        for (entity in deviceData.entities) {
            if (entity.entityType != ENTITY_TYPE_EXPENSE) {
                Log.w(tag, "Skipping unsupported entity type: ${entity.entityType}")
                continue
            }

            val remote = parseRemoteExpense(entity)
            if (remote == null) {
                failedItems++
                continue
            }

            val queued = winners[remote.id]
            if (queued == null) {
                winners[remote.id] = remote
                continue
            }

            // Both revisions originate from the same peer, so the tie-breaker device id
            // is identical on both sides of the comparison.
            val intraBatch = ExpenseMerger.decide(
                local = queued,
                remote = remote,
                localDeviceId = remoteDeviceId,
                remoteDeviceId = remoteDeviceId
            )
            if (intraBatch.shouldWrite) {
                winners[remote.id] = remote
            }
        }

        // Pass 2: merge each winning revision against the local database.
        val conflicts = mutableListOf<SyncConflict>()
        val pendingWrites = ArrayList<ExpenseEntity>(winners.size)
        var newItems = 0
        var updatedItems = 0
        var processedCount = 0

        for (remote in winners.values) {
            processedCount++

            try {
                // Deliberately uses the tombstone-aware lookup: a locally deleted row must
                // not look absent, otherwise a stale remote revision would resurrect it.
                val local = expenseDao.getExpenseByIdForSync(remote.id)

                val decision = ExpenseMerger.decide(
                    local = local,
                    remote = remote,
                    localDeviceId = localDeviceId,
                    remoteDeviceId = remoteDeviceId
                )

                if (decision.shouldWrite) {
                    // Records adopted from a peer are marked unsynced so this device still
                    // reconciles them with the backend on the next server sync.
                    pendingWrites.add(remote.copy(isSynced = false))
                    if (decision.outcome == MergeOutcome.INSERT_NEW) newItems++ else updatedItems++
                }

                if (decision.isConflict && local != null) {
                    conflicts.add(
                        SyncConflict(
                            entityType = ENTITY_TYPE_EXPENSE,
                            entityId = remote.id,
                            conflictType = ConflictType.UPDATE_CONFLICT,
                            localTimestamp = local.updatedAt,
                            serverTimestamp = remote.updatedAt,
                            resolution = if (decision.outcome == MergeOutcome.KEEP_LOCAL) {
                                ConflictResolution.USE_LOCAL
                            } else {
                                ConflictResolution.USE_SERVER
                            }
                        )
                    )
                    Log.d(
                        tag,
                        "Conflict on ${remote.id}: ${decision.outcome} by ${decision.reason} " +
                                "(local=${local.updatedAt}/v${local.version}, " +
                                "remote=${remote.updatedAt}/v${remote.version})"
                    )
                }
            } catch (e: Exception) {
                failedItems++
                Log.e(tag, "Failed to process expense entity ${remote.id}", e)
            }

            if (processedCount % 100 == 0 || processedCount == winners.size) {
                val progress = 0.3f + (processedCount.toFloat() / winners.size * 0.5f)
                updateSyncProgress(progress, "Processing data... ($processedCount/${winners.size})", true)
            }
        }

        // Single batched write. Room runs a list @Insert inside one transaction, so a
        // failure rolls the whole batch back instead of leaving a half-applied state.
        if (pendingWrites.isNotEmpty()) {
            expenseDao.insertExpenses(pendingWrites)
        }

        Log.d(
            tag,
            "Applied ${pendingWrites.size} revisions from $remoteDeviceId " +
                    "(received=$totalEntities, unique=${winners.size}, new=$newItems, " +
                    "updated=$updatedItems, conflicts=${conflicts.size}, failed=$failedItems)"
        )

        return DownloadResult(
            totalItems = totalEntities,
            newItems = newItems,
            updatedItems = updatedItems,
            conflicts = conflicts
        )
    }

    /**
     * Decode a wire entity into an [ExpenseEntity].
     *
     * v1 peers overwrite the envelope timestamp with their local "now", so the embedded
     * payload's own updatedAt is the more trustworthy source. The envelope value is only
     * used as a fallback when the payload carries no timestamp at all.
     */
    private fun parseRemoteExpense(entity: SyncEntity): ExpenseEntity? {
        val parsed = try {
            gson.fromJson(entity.data, ExpenseEntity::class.java)
        } catch (e: Exception) {
            Log.e(tag, "Malformed payload for entity ${entity.entityId}", e)
            return null
        }

        if (parsed == null || parsed.id.isEmpty()) {
            Log.e(tag, "Payload for entity ${entity.entityId} decoded to an unusable record")
            return null
        }

        val effectiveUpdatedAt = if (parsed.updatedAt > 0L) parsed.updatedAt else entity.timestamp

        // Honour an explicit DELETE operation even if the payload forgot to set deletedAt.
        val effectiveDeletedAt = when {
            parsed.deletedAt != null -> parsed.deletedAt
            entity.operation == OP_DELETE -> effectiveUpdatedAt
            else -> null
        }

        return parsed.copy(
            updatedAt = effectiveUpdatedAt,
            deletedAt = effectiveDeletedAt
        )
    }
    
    /**
     * Create a failed sync result
     */
    protected fun createFailedSyncResult(error: String): SyncResult {
        return SyncResult(
            success = false,
            uploadResult = UploadResult(0, 0, 0),
            downloadResult = DownloadResult(0, 0, 0),
            error = error
        )
    }
}