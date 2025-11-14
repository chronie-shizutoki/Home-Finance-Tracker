package com.chronie.homemoney.ui.settings

import androidx.lifecycle.ViewModel
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.core.common.LanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LanguageSettingsViewModel @Inject constructor(
    private val languageManager: LanguageManager
) : ViewModel() {

    val currentLanguage: StateFlow<Language> = languageManager.currentLanguage

    fun setLanguage(language: Language) {
        languageManager.setLanguage(language)
    }
}
