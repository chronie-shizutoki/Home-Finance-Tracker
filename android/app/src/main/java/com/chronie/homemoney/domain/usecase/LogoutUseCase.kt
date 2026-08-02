package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.data.local.PreferencesManager
import javax.inject.Inject

/**
 * Handles user logout by clearing the locally stored session data.
 *
 * After logout, the user will be shown the welcome/login screen on the next app launch.
 *
 * @param preferencesManager Local preferences store for clearing login state.
 */
class LogoutUseCase @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    /**
     * Clears the persisted username, effectively logging the user out.
     */
    operator fun invoke() {
        preferencesManager.clearUsername()
    }
}
