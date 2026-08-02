package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.data.local.PreferencesManager
import javax.inject.Inject

/**
 * Checks whether the user is currently logged in and can skip the welcome screen.
 *
 * Also provides access to the stored username for display purposes.
 *
 * @param preferencesManager Local preferences store for reading login state.
 */
class CheckLoginStatusUseCase @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    /**
     * Returns true if the user has previously logged in and the welcome
     * screen should be skipped.
     *
     * @return True if the user is considered logged in.
     */
    operator fun invoke(): Boolean {
        return preferencesManager.shouldSkipWelcome()
    }

    /**
     * Returns the stored username of the currently logged-in user,
     * or null if no user is logged in.
     *
     * @return The username string, or null.
     */
    fun getUsername(): String? {
        return preferencesManager.getUsername()
    }
}
