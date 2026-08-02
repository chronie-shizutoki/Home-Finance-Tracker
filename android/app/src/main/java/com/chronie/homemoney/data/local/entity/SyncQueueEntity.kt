package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the sync outbox — stores pending changes that need to
 * be sent to the server during the next synchronization cycle.
 *
 * Each entry represents a single CRUD operation (CREATE, UPDATE, or DELETE)
 * on a specific entity, with retry tracking for failed sync attempts.
 *
 * Indexed on [entityType] and [createdAt] for efficient queue processing.
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entity_type"]),
        Index(value = ["created_at"])
    ]
)
data class SyncQueueEntity(
    /** Auto-generated primary key for the queue entry. */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    /** The type of entity being synced (e.g., "expense", "member"). */
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    
    /** The unique ID of the entity being synced. */
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    
    /** The CRUD operation type: "CREATE", "UPDATE", or "DELETE". */
    @ColumnInfo(name = "operation")
    val operation: String,
    
    /** JSON-serialized entity data for the sync payload. */
    @ColumnInfo(name = "data")
    val data: String,
    
    /** Number of times this entry has been retried after a failed sync. */
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    
    /** Epoch millis timestamp when this queue entry was created. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
