package com.chronie.homemoney.ui.membership

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.data.local.PreferencesManager
import com.chronie.homemoney.domain.model.Member
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Membership / Profile screen.
 *
 * Displays the current user's member information (username, avatar, etc.)
 * and provides logout functionality.
 */
@HiltViewModel
class MembershipViewModel @Inject constructor(
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase,
    private val preferencesManager: PreferencesManager,
    private val memberRepository: com.chronie.homemoney.domain.repository.MemberRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MembershipUiState>(MembershipUiState.Loading)
    val uiState: StateFlow<MembershipUiState> = _uiState.asStateFlow()

    init {
        loadMembershipData()
    }

    /**
     * Loads the current member's data from the repository.
     * Requires the user to be logged in; shows an error state otherwise.
     */
    fun loadMembershipData() {
        viewModelScope.launch {
            _uiState.value = MembershipUiState.Loading

            try {
                val username = checkLoginStatusUseCase.getUsername()
                if (username.isNullOrEmpty()) {
                    _uiState.value = MembershipUiState.Error("Not logged in")
                    return@launch
                }

                // Fetch member info from the server
                val memberResult = memberRepository.getMemberInfo(username)
                if (memberResult.isSuccess) {
                    _uiState.value = MembershipUiState.Success(
                        member = memberResult.getOrNull()
                    )
                } else {
                    _uiState.value = MembershipUiState.Error(
                        memberResult.exceptionOrNull()?.message ?: "Failed to get member info"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = MembershipUiState.Error(
                    e.message ?: "Loading failed"
                )
            }
        }
    }

    /**
     * Updates the user's avatar on the server.
     *
     * @param avatar Base64-encoded avatar image data.
     */
    fun updateAvatar(avatar: String) {
        viewModelScope.launch {
            try {
                val username = checkLoginStatusUseCase.getUsername()
                if (username.isNullOrEmpty()) {
                    _uiState.value = MembershipUiState.Error("Not logged in")
                    return@launch
                }

                val result = memberRepository.updateAvatar(username, avatar)
                if (result.isSuccess) {
                    val currentState = _uiState.value
                    if (currentState is MembershipUiState.Success) {
                        _uiState.value = currentState.copy(member = result.getOrNull())
                    }
                } else {
                    _uiState.value = MembershipUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to update avatar"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = MembershipUiState.Error(
                    e.message ?: "Failed to update avatar"
                )
            }
        }
    }

    /**
     * Logs the user out by clearing local preferences.
     *
     * @param onLogout Callback to navigate back to the welcome screen.
     */
    fun logout(onLogout: () -> Unit) {
        preferencesManager.clearUsername()
        onLogout()
    }
}

/**
 * Sealed class representing the membership screen UI states.
 */
sealed class MembershipUiState {
    /** Loading member data from the server. */
    object Loading : MembershipUiState()
    /** Member data loaded successfully. */
    data class Success(
        val member: Member?
    ) : MembershipUiState()
    /** Failed to load member data. */
    data class Error(val message: String) : MembershipUiState()
}
