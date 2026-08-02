package com.chronie.homemoney.domain.model

/**
 * Aggregate result of a synchronization operation between the local device
 * and the remote server.
 *
 * Contains detailed breakdowns for both upload and download phases,
 * as well as any conflicts that were detected and resolved.
 *
 * @property success True if the overall sync completed without fatal errors.
 * @property uploadResult Summary of local-to-server upload activity.
 * @property downloadResult Summary of server-to-local download activity.
 * @property conflicts List of conflicts that were detected during sync.
 * @property error Error message if the sync failed; null on success.
 */
data class SyncResult(
    val success: Boolean,
    val uploadResult: UploadResult,
    val downloadResult: DownloadResult,
    val conflicts: List<SyncConflict> = emptyList(),
    val error: String? = null
)

/**
 * Detailed result of the upload phase of a sync operation.
 *
 * @property totalItems Total number of local items queued for upload.
 * @property successCount Number of items successfully uploaded.
 * @property failedCount Number of items that failed to upload.
 * @property failedItems Detailed list of each failed upload with error info.
 */
data class UploadResult(
    val totalItems: Int,
    val successCount: Int,
    val failedCount: Int,
    val failedItems: List<FailedSyncItem> = emptyList()
)

/**
 * Detailed result of the download phase of a sync operation.
 *
 * @property totalItems Total number of items fetched from the server.
 * @property newItems Number of previously unseen items added locally.
 * @property updatedItems Number of existing items updated with newer server data.
 * @property conflicts Conflicts detected between local and server versions.
 */
data class DownloadResult(
    val totalItems: Int,
    val newItems: Int,
    val updatedItems: Int,
    val conflicts: List<SyncConflict> = emptyList()
)

/**
 * Represents a data conflict between the local and server versions of an entity.
 *
 * Conflicts occur when both the local device and the server have modified
 * the same entity since the last sync.
 *
 * @property entityType The type of entity in conflict (e.g., "expense").
 * @property entityId The unique ID of the conflicting entity.
 * @property conflictType The nature of the conflict.
 * @property localTimestamp Epoch millis of the local version's last update.
 * @property serverTimestamp Epoch millis of the server version's last update.
 * @property resolution The strategy used to resolve this conflict.
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
 * Types of sync conflicts that can occur.
 */
enum class ConflictType {
    /** Both local and server versions have been modified since last sync. */
    UPDATE_CONFLICT,
}

/**
 * Strategies for resolving a sync conflict.
 */
enum class ConflictResolution {
    /** Keep the local version, discarding the server's changes. */
    USE_LOCAL,
    /** Overwrite the local version with the server's version. */
    USE_SERVER,
    /** Attempt to intelligently merge both versions (when supported by the entity type). */
    MERGE
}

/**
 * Information about a single item that failed to sync.
 *
 * @property entityType The type of entity that failed.
 * @property entityId The unique ID of the failed entity.
 * @property operation The sync operation attempted ("upload" or "download").
 * @property error A human-readable error description.
 */
data class FailedSyncItem(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val error: String
)

/**
 * High-level sync operation status for UI display.
 */
enum class SyncStatus {
    /** No sync is currently running and no results are pending. */
    IDLE,
    /** A sync operation is actively in progress. */
    SYNCING,
    /** The most recent sync completed successfully. */
    SUCCESS,
    /** The most recent sync failed with errors. */
    FAILED,
    /** The most recent sync encountered conflicts that may need manual resolution. */
    CONFLICT
}
