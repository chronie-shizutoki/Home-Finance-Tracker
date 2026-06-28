package com.chronie.homemoney.domain.sync

import com.chronie.homemoney.domain.model.SyncConflict
import com.chronie.homemoney.domain.model.SyncResult
import com.chronie.homemoney.domain.model.UploadResult
import com.chronie.homemoney.domain.model.DownloadResult
import kotlinx.coroutines.flow.Flow

/**
 * Sync Manager Interface
 */
interface SyncManager {
    
    /**
     * Perform Full Sync
     */
    suspend fun performFullSync(): Result<SyncResult>
    
    /**
     * Upload Local Changes
     */
    suspend fun uploadLocalChanges(): Result<UploadResult>
    
    /**
     * Download Server Updates
     */
    suspend fun downloadServerUpdates(): Result<DownloadResult>
    
    /**
     * Resolve Sync Conflicts
     */
    suspend fun resolveConflicts(conflicts: List<SyncConflict>): Result<Unit>
    
    /**
     * Get Last Sync Time
     */
    fun getLastSyncTime(): Long?
    
    /**
     * Set Last Sync Time
     */
    suspend fun setLastSyncTime(timestamp: Long)
    
    /**
     * Get Pending Sync Count
     */
    suspend fun getPendingSyncCount(): Int
    
    /**
     * Observe Sync Status
     */
    fun observeSyncStatus(): Flow<com.chronie.homemoney.domain.model.SyncStatus>
    
    /**
     * Get Device Sync Manager (Only Local Sync Supported)
     */
    fun getDeviceSyncManager(): DeviceSyncManager
}
