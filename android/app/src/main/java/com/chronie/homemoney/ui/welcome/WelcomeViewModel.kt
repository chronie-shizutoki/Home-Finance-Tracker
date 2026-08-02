package com.chronie.homemoney.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.data.local.PreferencesManager
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Welcome / Login screen.
 *
 * Manages the login flow: checking existing login state, validating
 * username input, executing the login use case, and handling skip-login.
 *
 * The UI observes [uiState] to determine which screen to render and
 * [skipLoginEvent] to navigate to the main screen after skipping login.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    /** Current UI state for rendering the welcome screen. */
    private val _uiState = MutableStateFlow<WelcomeUiState>(WelcomeUiState.CheckingLogin)
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    /** Username text field input (two-way binding). */
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    /** One-shot event to navigate to the main screen after skipping login. */
    private val _skipLoginEvent = MutableSharedFlow<Unit>()
    val skipLoginEvent: SharedFlow<Unit> = _skipLoginEvent.asSharedFlow()

    init {
        checkLoginStatus()
    }

    /**
     * Checks whether the user previously logged in.
     * If yes, transitions to [WelcomeUiState.LoggedIn] to auto-navigate.
     * If no, shows the login form via [WelcomeUiState.NotLoggedIn].
     */
    fun checkLoginStatus() {
        viewModelScope.launch {
            val shouldSkipWelcome = checkLoginStatusUseCase()
            if (shouldSkipWelcome) {
                val username = checkLoginStatusUseCase.getUsername()
                if (!username.isNullOrEmpty()) {
                    _uiState.value = WelcomeUiState.LoggedIn(username)
                } else {
                    _skipLoginEvent.emit(Unit)
                }
            } else {
                _uiState.value = WelcomeUiState.NotLoggedIn
            }
        }
    }

    /** Updates the username as the user types in the text field. */
    fun onUsernameChange(newUsername: String) {
        _username.value = newUsername
    }

    /**
     * Attempts to log in with the current username.
     * Sets the UI state to [WelcomeUiState.Loading] during the request,
     * then transitions to [WelcomeUiState.LoggedIn] or [WelcomeUiState.Error].
     */
    fun login() {
        if (_username.value.isBlank()) {
            _uiState.value = WelcomeUiState.Error("Username cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = WelcomeUiState.Loading

            loginUseCase(_username.value.trim())
                .onSuccess { member ->
                    _uiState.value = WelcomeUiState.LoggedIn(
                        username = member.username
                    )
                }
                .onFailure { error ->
                    _uiState.value = WelcomeUiState.Error(
                        error.message ?: "Login failed"
                    )
                }
        }
    }

    /**
     * Skips the login process entirely.
     * Persists the skip preference and emits a one-shot navigation event.
     */
    fun skipLogin() {
        preferencesManager.setSkippedLogin(true)
        viewModelScope.launch {
            _skipLoginEvent.emit(Unit)
        }
    }

    /** Clears the error state and returns to the login form. */
    fun clearError() {
        if (_uiState.value is WelcomeUiState.Error) {
            _uiState.value = WelcomeUiState.NotLoggedIn
        }
    }
}

/**
 * Sealed class representing all possible states of the welcome/login screen.
 */
sealed class WelcomeUiState {
    /** Initial state while checking if the user is already logged in. */
    object CheckingLogin : WelcomeUiState()
    /** User is not logged in — show the login form. */
    object NotLoggedIn : WelcomeUiState()
    /** Login request is in progress — show a loading indicator. */
    object Loading : WelcomeUiState()
    /** Login was successful — navigate to the main screen. */
    data class LoggedIn(val username: String) : WelcomeUiState()
    /** Login failed — show an error message. */
    data class Error(val message: String) : WelcomeUiState()
}
