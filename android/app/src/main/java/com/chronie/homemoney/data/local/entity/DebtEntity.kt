package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 债务记录实体
 * 对应后端的 Debt 表
 */
@Entity(
    tableName = "debts",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["is_repaid"]),
        Index(value = ["is_synced"])
    ]
)
data class DebtEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "type")
    val type: String, // "lend" or "borrow"
    
    @ColumnInfo(name = "person")
    val person: String,
    
    @ColumnInfo(name = "amount")
    val amount: Double,
    
    @ColumnInfo(name = "date")
    val date: Long,
    
    @ColumnInfo(name = "is_repaid")
    val isRepaid: Boolean = false,
    
    @ColumnInfo(name = "remark")
    val remark: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "server_id")
    val serverId: Int? = null
)
