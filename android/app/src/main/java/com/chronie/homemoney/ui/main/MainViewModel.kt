package com.chronie.homemoney.ui.main

import androidx.lifecycle.ViewModel
import com.chronie.homemoney.core.common.DeveloperMode
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Main Screen ViewModel
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val developerMode: DeveloperMode,
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase
) : ViewModel() {

    /**
     * Developer mode status
     */
    val isDeveloperMode: Flow<Boolean> = developerMode.isDeveloperModeEnabled

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        checkAccess()
    }

    fun checkAccess() {
        _isLoggedIn.value = checkLoginStatusUseCase()
    }
}
