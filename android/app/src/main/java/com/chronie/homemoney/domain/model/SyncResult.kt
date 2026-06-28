package com.chronie.homemoney.domain.model

/**
 * Sync Result Domain Model
 */
data class SyncResult(
    val success: Boolean,
    val uploadResult: UploadResult,
    val downloadResult: DownloadResult,
    val conflicts: List<SyncConflict> = emptyList(),
    val error: String? = null
)

/**
 * Upload Result Domain Model
 */
data class UploadResult(
    val totalItems: Int,
    val successCount: Int,
    val failedCount: Int,
    val failedItems: List<FailedSyncItem> = emptyList()
)

/**
 * Download Result Domain Model
 */
data class DownloadResult(
    val totalItems: Int,
    val newItems: Int,
    val updatedItems: Int,
    val conflicts: List<SyncConflict> = emptyList()
)

/**
 * Sync Conflict Domain Model
 */
data class SyncConflict(
    val entityType: String,
    val entityId: String,
    val conflictType: ConflictType,
    val localTimestamp: Long,
    val serverTimestamp: Long,
    val resolution: ConflictResolution
)

/**
 * Conflict Type Enum
 */
enum class ConflictType {
    UPDATE_CONFLICT,  // Local Conflict with Server
    DELETE_CONFLICT   // Delete Conflict with Local
}

/**
 * Conflict Resolution Enum
 */
enum class ConflictResolution {
    USE_LOCAL,        // Use Local Version
    USE_SERVER,       // Use Server Version
    MERGE             // Merge(If Possible)
}

/**
 * Failed Sync Item Domain Model
 */
data class FailedSyncItem(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val error: String
)

/**
 * Sync Status
 */
enum class SyncStatus {
    IDLE,             // Idle
    SYNCING,          // Syncing
    SUCCESS,          // Success
    FAILED,           // Failed
    CONFLICT          // Conflict
}
