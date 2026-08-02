package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a household member in the local database.
 *
 * Note: This table only stores the currently logged-in user's information
 * (single-row design). Multiple members are managed on the server side.
 */
@Entity(tableName = "members")
data class MemberEntity(
    /** Primary key — unique member identifier, matching the server-side ID. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    /** Display name of the member. */
    @ColumnInfo(name = "username")
    val username: String,
    
    /** Whether this member is the currently active/logged-in user. */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    
    /** Epoch millis timestamp when the member record was created. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    /** Epoch millis timestamp of the last profile update. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
