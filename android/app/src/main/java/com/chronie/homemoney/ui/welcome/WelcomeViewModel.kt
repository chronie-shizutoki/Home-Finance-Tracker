package com.chronie.homemoney.ui.welcome

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.R
import com.chronie.homemoney.data.local.PreferencesManager
import com.chronie.homemoney.data.local.ServerConfigManager
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.domain.usecase.LoginUseCase
import com.chronie.homemoney.service.ServerConnectionTester
import com.chronie.homemoney.service.ServerTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * username input, executing the login use case, handling skip-login,
 * and (as of v2) configuring the backend server URL before logging in.
 *
 * The UI observes [uiState] to determine which screen to render and
 * [skipLoginEvent] to navigate to the main screen after skipping login.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase,
    private val preferencesManager: PreferencesManager,
    private val serverConfigManager: ServerConfigManager,
    private val serverConnectionTester: ServerConnectionTester,
    @param:ApplicationContext private val context: Context
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

    // ===================== Server configuration ===========================

    /**
     * Current value of the server-URL text field inside the server config
     * dialog. Starts from the persisted [ServerConfigManager.currentBaseUrl]
     * so reopening the dialog always shows the value that will actually be
     * used for network calls.
     */
    private val _serverUrlInput = MutableStateFlow(serverConfigManager.currentBaseUrl)
    val serverUrlInput: StateFlow<String> = _serverUrlInput.asStateFlow()

    /** Probe result for the server URL currently being edited. */
    private val _serverTestState = MutableStateFlow<ServerTestUiState>(ServerTestUiState.Idle)
    val serverTestState: StateFlow<ServerTestUiState> = _serverTestState.asStateFlow()

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
            _uiState.value = WelcomeUiState.Error(
                context.getString(R.string.auth_username_empty)
            )
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
                        error.message ?: context.getString(R.string.auth_login_failed)
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

    // ------------------------------------------------------------------
    // Server config actions
    // ------------------------------------------------------------------

    /** Called whenever the user types inside the URL text field. */
    fun onServerUrlChange(rawUrl: String) {
        _serverUrlInput.value = rawUrl
        if (_serverTestState.value !is ServerTestUiState.Idle) {
            _serverTestState.value = ServerTestUiState.Idle
        }
    }

    /** Probes the currently-entered URL and exposes the outcome through [serverTestState]. */
    fun testServerConnection() {
        val input = _serverUrlInput.value
        if (input.isBlank()) {
            _serverTestState.value =
                ServerTestUiState.Failed(context.getString(R.string.server_url_invalid))
            return
        }
        viewModelScope.launch {
            _serverTestState.value = ServerTestUiState.Testing
            when (val result = serverConnectionTester.test(input)) {
                is ServerTestResult.Success ->
                    _serverTestState.value = ServerTestUiState.Reachable(result.latencyMs)
                is ServerTestResult.InvalidUrl ->
                    _serverTestState.value =
                        ServerTestUiState.Failed(context.getString(R.string.server_url_invalid))
                is ServerTestResult.BadResponse ->
                    _serverTestState.value =
                        ServerTestUiState.Failed(
                            context.getString(R.string.server_test_bad_response, result.code)
                        )
                is ServerTestResult.Unreachable ->
                    _serverTestState.value =
                        ServerTestUiState.Failed(
                            context.getString(R.string.server_test_unreachable, result.reason)
                        )
            }
        }
    }

    /**
     * Persists the currently-entered URL through [ServerConfigManager].
     *
     * @return true when the URL passed validation and was saved. On false
     *   the rejection reason is pushed into [serverTestState] so the dialog
     *   can show a localized error under the text field.
     */
    fun saveServerUrl(): Boolean {
        val result = serverConfigManager.setBaseUrl(_serverUrlInput.value)
        return if (result.isSuccess) {
            _serverTestState.value = ServerTestUiState.Idle
            true
        } else {
            val message = result.exceptionOrNull()?.message
                ?: context.getString(R.string.server_url_invalid)
            _serverTestState.value = ServerTestUiState.Failed(message)
            false
        }
    }

    /** Discards any custom server address and reverts to the built-in default. */
    fun resetServerUrl() {
        serverConfigManager.resetToDefault()
        _serverUrlInput.value = ServerConfigManager.DEFAULT_BASE_URL
        _serverTestState.value = ServerTestUiState.Idle
    }

    /** Clears transient test/failure feedback so the dialog opens cleanly next time. */
    fun clearServerTestState() {
        _serverTestState.value = ServerTestUiState.Idle
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

/**
 * UI representation of a server reachability probe (copy of the sealed
 * interface in SettingsViewModel — kept local to avoid coupling modules).
 */
sealed interface ServerTestUiState {
    /** No probe has run yet (or the result was dismissed). */
    data object Idle : ServerTestUiState
    /** A probe is in flight. */
    data object Testing : ServerTestUiState
    /** Candidate server answered the health endpoint within [latencyMs]. */
    data class Reachable(val latencyMs: Long) : ServerTestUiState
    /** Probe failed; [message] is already localized for display. */
    data class Failed(val message: String) : ServerTestUiState
}
