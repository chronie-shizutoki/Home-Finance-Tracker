package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the sync outbox queue.
 *
 * Each entry represents a pending CRUD operation (CREATE/UPDATE/DELETE)
 * that must be pushed to the server. Entries are processed in FIFO order
 * and removed upon successful sync.
 */
@Dao
interface SyncQueueDao {
    
    /** Observes all pending sync queue entries reactively, oldest first. */
    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    fun getAllSyncQueue(): Flow<List<SyncQueueEntity>>
    
    /** Fetches all pending entries for a specific entity type. */
    @Query("SELECT * FROM sync_queue WHERE entity_type = :entityType ORDER BY created_at ASC")
    suspend fun getSyncQueueByType(entityType: String): List<SyncQueueEntity>
    
    /** Retrieves the next batch of items to sync, up to the given limit. */
    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC LIMIT :limit")
    suspend fun getNextSyncItems(limit: Int): List<SyncQueueEntity>
    
    /** Enqueues a new sync operation. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(item: SyncQueueEntity)
    
    /** Updates a sync queue entry (e.g., incrementing retry count). */
    @Update
    suspend fun updateSyncItem(item: SyncQueueEntity)
    
    /** Removes a specific entry after successful sync. */
    @Delete
    suspend fun deleteSyncItem(item: SyncQueueEntity)
    
    /** Removes an entry by its auto-generated queue ID. */
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteSyncItemById(id: Long)
    
    /** Removes all queue entries for a specific entity (deduplication). */
    @Query("DELETE FROM sync_queue WHERE entity_id = :entityId AND entity_type = :entityType")
    suspend fun deleteSyncItemsByEntity(entityId: String, entityType: String)
    
    /** Purges the entire sync queue. */
    @Query("DELETE FROM sync_queue")
    suspend fun deleteAllSyncQueue()
    
    /** Returns the total number of pending sync operations. */
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getSyncQueueCount(): Int
}
