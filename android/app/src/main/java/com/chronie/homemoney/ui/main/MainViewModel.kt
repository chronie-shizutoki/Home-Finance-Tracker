package com.chronie.homemoney.ui.main

import androidx.lifecycle.ViewModel
import com.chronie.homemoney.core.common.DeveloperMode
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Main Screen ViewModel
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    developerMode: DeveloperMode,
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)

    init {
        checkAccess()
    }

    fun checkAccess() {
        _isLoggedIn.value = checkLoginStatusUseCase()
    }
}
