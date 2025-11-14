package com.chronie.homemoney.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.developerDataStore: DataStore<Preferences> by preferencesDataStore(name = "developer_settings")

/**
 * 开发者模式管理器
 * 用于控制开发者功能的显示和隐藏
 */
@Singleton
class DeveloperMode @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val developerModeKey = booleanPreferencesKey("developer_mode_enabled")
    
    /**
     * 开发者模式是否启用
     */
    val isDeveloperModeEnabled: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[developerModeKey] ?: false
        }
    
    /**
     * 启用开发者模式
     */
    suspend fun enableDeveloperMode() {
        context.developerDataStore.edit { preferences ->
            preferences[developerModeKey] = true
        }
    }
    
    /**
     * 禁用开发者模式
     */
    suspend fun disableDeveloperMode() {
        context.developerDataStore.edit { preferences ->
            preferences[developerModeKey] = false
        }
    }
    
    /**
     * 切换开发者模式
     */
    suspend fun toggleDeveloperMode() {
        context.developerDataStore.edit { preferences ->
            val current = preferences[developerModeKey] ?: false
            preferences[developerModeKey] = !current
        }
    }
}
