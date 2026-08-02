package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Room entity representing an expense record in the local SQLite database.
 *
 * The table is indexed on [date], [type], [isSynced], and [updatedAt] for
 * efficient filtering, sorting, and sync status queries.
 *
 * Note: The [type] field stores the Chinese display name as a string,
 * not the enum value, to maintain compatibility with the server API.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["is_synced"]),
        Index(value = ["updated_at"])
    ]
)
data class ExpenseEntity(
    /** Primary key — unique identifier for the expense record. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    @SerializedName("id")
    val id: String,
    
    /** Expense category stored as Chinese display name (e.g., "食品", "交通出行"). */
    @ColumnInfo(name = "type")
    @SerializedName("type")
    val type: String,
    
    /** Optional user-provided description or note. */
    @ColumnInfo(name = "remark")
    @SerializedName("remark")
    val remark: String?,
    
    /** Monetary amount of the expense. */
    @ColumnInfo(name = "amount")
    @SerializedName("amount")
    val amount: Double,
    
    /** Transaction date in "YYYY-MM-DD" format. */
    @ColumnInfo(name = "date")
    @SerializedName("date")
    val date: String,
    
    /** Optimistic locking version for sync conflict resolution. */
    @ColumnInfo(name = "version")
    @SerializedName("version")
    val version: Int = 1,
    
    /** Epoch millis timestamp of the last modification. */
    @ColumnInfo(name = "updated_at")
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** Epoch millis timestamp when soft-deleted; null if active. */
    @ColumnInfo(name = "deleted_at")
    @SerializedName("deletedAt")
    val deletedAt: Long? = null,
    
    /** Whether this record has been synchronized with the server. */
    @ColumnInfo(name = "is_synced")
    @SerializedName("is_synced")
    val isSynced: Boolean = false
)
