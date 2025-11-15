package com.chronie.homemoney.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.domain.usecase.GetMembershipStatusUseCase
import com.chronie.homemoney.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase,
    private val getMembershipStatusUseCase: GetMembershipStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<WelcomeUiState>(WelcomeUiState.CheckingLogin)
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    init {
        checkLoginStatus()
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            val isLoggedIn = checkLoginStatusUseCase()
            if (isLoggedIn) {
                val username = checkLoginStatusUseCase.getUsername() ?: ""
                
                // 获取会员状态
                val membershipResult = getMembershipStatusUseCase(username, forceRefresh = false)
                val isMember = membershipResult.getOrNull()?.isActive ?: false
                
                _uiState.value = WelcomeUiState.LoggedIn(username, isMember)
            } else {
                _uiState.value = WelcomeUiState.NotLoggedIn
            }
        }
    }

    fun onUsernameChange(newUsername: String) {
        _username.value = newUsername
    }

    fun login() {
        if (_username.value.isBlank()) {
            _uiState.value = WelcomeUiState.Error("用户名不能为空")
            return
        }

        viewModelScope.launch {
            _uiState.value = WelcomeUiState.Loading
            
            // 1. 执行登录
            loginUseCase(_username.value.trim())
                .onSuccess { member ->
                    // 2. 获取会员状态
                    val membershipResult = getMembershipStatusUseCase(
                        username = member.username,
                        forceRefresh = true
                    )
                    
                    val isMember = membershipResult.getOrNull()?.isActive ?: false
                    _uiState.value = WelcomeUiState.LoggedIn(
                        username = member.username,
                        isMember = isMember
                    )
                }
                .onFailure { error ->
                    _uiState.value = WelcomeUiState.Error(
                        error.message ?: "登录失败"
                    )
                }
        }
    }

    fun clearError() {
        if (_uiState.value is WelcomeUiState.Error) {
            _uiState.value = WelcomeUiState.NotLoggedIn
        }
    }
}

sealed class WelcomeUiState {
    object CheckingLogin : WelcomeUiState()
    object NotLoggedIn : WelcomeUiState()
    object Loading : WelcomeUiState()
    data class LoggedIn(val username: String, val isMember: Boolean) : WelcomeUiState()
    data class Error(val message: String) : WelcomeUiState()
}
