package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sync Queue Entity Class
 * Used to store operations to be synchronized to the server
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entity_type"]),
        Index(value = ["created_at"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "entity_type")
    val entityType: String, // "expense", "member", etc.
    
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    
    @ColumnInfo(name = "operation")
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    
    @ColumnInfo(name = "data")
    val data: String, // JSON-formatted data
    
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
