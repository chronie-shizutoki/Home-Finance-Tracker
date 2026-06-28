package com.chronie.homemoney.data.sync

import android.content.Context
import android.net.wifi.WifiManager
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.domain.sync.DeviceSyncManager
import com.google.gson.Gson

/**
 * Device Sync Manager Factory
 * Factory class for creating device sync managers
 * Only LAN (Local Area Network) sync is supported
 */
class DeviceSyncManagerFactory(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val gson: Gson,
    private val wifiManager: WifiManager
) {

    // Singleton instance, ensuring server remains running
    private val lanDeviceSyncManager: LanDeviceSyncManager by lazy {
        LanDeviceSyncManager(context, expenseDao, gson, wifiManager).apply {
            // Start sync server to listen for device connection requests
            startSyncServer()
        }
    }

    /**
     * Create Device Sync Manager Instance
     * Only LAN (Local Area Network) sync is supported
     * Returns singleton instance, ensuring server remains running
     */
    fun createDeviceSyncManager(): DeviceSyncManager {
        return lanDeviceSyncManager
    }
}
