package com.chronie.homemoney.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.DeveloperMode
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.core.common.LanguageManager
import com.chronie.homemoney.data.sync.SyncScheduler
import com.chronie.homemoney.domain.model.SyncStatus
import com.chronie.homemoney.domain.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageManager: LanguageManager,
    private val developerMode: DeveloperMode,
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val currentLanguage: StateFlow<Language> = languageManager.currentLanguage
    
    val isDeveloperMode: Flow<Boolean> = developerMode.isDeveloperModeEnabled
    
    private val _aiApiKey = MutableStateFlow("")
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()
    
    val syncStatus: StateFlow<SyncStatus> = syncManager.observeSyncStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncStatus.IDLE
        )
    
    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()
    
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()
    
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        loadSyncInfo()
        loadAIApiKey()
    }

    fun setLanguage(language: Language) {
        languageManager.setLanguage(language)
    }
    
    fun toggleDeveloperMode() {
        viewModelScope.launch {
            developerMode.toggleDeveloperMode()
        }
    }
    
    fun manualSync() {
        viewModelScope.launch {
            try {
                _syncMessage.value = null
                val result = syncScheduler.manualSync()
                
                if (result.isSuccess) {
                    val syncResult = result.getOrNull()
                    if (syncResult?.success == true) {
                        _syncMessage.value = "同步成功"
                        loadSyncInfo()
                    } else {
                        _syncMessage.value = "同步失败: ${syncResult?.error}"
                    }
                } else {
                    _syncMessage.value = "同步失败: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _syncMessage.value = "同步失败: ${e.message}"
            }
        }
    }
    
    fun clearSyncMessage() {
        _syncMessage.value = null
    }
    
    fun setAIApiKey(apiKey: String) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("siliconflow_api_key", apiKey).apply()
            _aiApiKey.value = apiKey
            _syncMessage.value = context.getString(R.string.settings_ai_api_key_saved)
        }
    }
    
    private fun loadAIApiKey() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
            _aiApiKey.value = prefs.getString("siliconflow_api_key", "") ?: ""
        }
    }
    
    private fun loadSyncInfo() {
        viewModelScope.launch {
            // 加载最后同步时间
            val lastSync = syncManager.getLastSyncTime()
            _lastSyncTime.value = if (lastSync != null) {
                formatTimestamp(lastSync)
            } else {
                null
            }
            
            // 加载待同步项数量
            _pendingSyncCount.value = syncManager.getPendingSyncCount()
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
