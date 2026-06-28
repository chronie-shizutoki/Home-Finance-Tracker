package com.chronie.homemoney.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.chronie.homemoney.core.error.ErrorReporterTest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.developerDataStore: DataStore<Preferences> by preferencesDataStore(name = "developer_settings")

/**
 * Developer Mode Manager
 * Manages developer mode settings and actions
 */
@Singleton
class DeveloperMode @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val errorReporterTest: ErrorReporterTest
) {
    private val developerModeKey = booleanPreferencesKey("developer_mode_enabled")
    
    /**
     * Is developer mode enabled
     */
    val isDeveloperModeEnabled: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[developerModeKey] ?: false
        }
    
    /**
     * Enable developer mode
     */
    suspend fun enableDeveloperMode() {
        context.developerDataStore.edit { preferences ->
            preferences[developerModeKey] = true
        }
    }
    
    /**
     * Disable developer mode
     */
    suspend fun disableDeveloperMode() {
        context.developerDataStore.edit { preferences ->
            preferences[developerModeKey] = false
        }
    }
    
    /**
     * Toggle developer mode
     */
    suspend fun toggleDeveloperMode() {
        context.developerDataStore.edit { preferences ->
            val current = preferences[developerModeKey] ?: false
            preferences[developerModeKey] = !current
        }
    }
    
    /**
     * Test error logging: Record a normal error
     */
    fun testErrorLogging() {
        errorReporterTest.testLogError()
    }
    
    /**
     * Test error logging: Record a network error
     */
    fun testNetworkErrorLogging() {
        errorReporterTest.testNetworkError()
    }
    
    /**
     * Test error logging: Trigger an uncaught exception
     * Warning: This method will crash the app, use with caution!
     */
    fun testUncaughtException() {
        errorReporterTest.testUncaughtException()
    }
}
