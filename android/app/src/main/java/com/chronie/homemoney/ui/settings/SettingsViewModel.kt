package com.chronie.homemoney.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.core.common.DeveloperMode
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.core.common.LanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageManager: LanguageManager,
    private val developerMode: DeveloperMode
) : ViewModel() {

    val currentLanguage: StateFlow<Language> = languageManager.currentLanguage
    
    val isDeveloperMode: Flow<Boolean> = developerMode.isDeveloperModeEnabled

    fun setLanguage(language: Language) {
        languageManager.setLanguage(language)
    }
    
    fun toggleDeveloperMode() {
        viewModelScope.launch {
            developerMode.toggleDeveloperMode()
        }
    }
}
