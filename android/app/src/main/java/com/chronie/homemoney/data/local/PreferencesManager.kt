package com.chronie.homemoney.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Singleton manager for simple key-value preferences stored in SharedPreferences.
 *
 * Handles local user state that does not belong in the encrypted Room database:
 * - Username (for login persistence)
 * - Avatar data (base64-encoded)
 * - Skip-login flag
 *
 * For more complex data, use Room via the corresponding DAOs.
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /** Persists the logged-in username. */
    fun saveUsername(username: String) {
        prefs.edit { putString(KEY_USERNAME, username) }
    }

    /** Retrieves the persisted username, or null if not logged in. */
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    /** Clears the persisted username (used during logout). */
    fun clearUsername() {
        prefs.edit { remove(KEY_USERNAME) }
    }

    /** Checks whether a username is persisted. */
    fun isLoggedIn(): Boolean {
        return !getUsername().isNullOrEmpty()
    }

    /** Sets whether the user chose to skip login. */
    fun setSkippedLogin(skipped: Boolean) {
        prefs.edit { putBoolean(KEY_SKIPPED_LOGIN, skipped) }
    }

    /** Returns true if login was previously skipped. */
    fun hasSkippedLogin(): Boolean {
        return prefs.getBoolean(KEY_SKIPPED_LOGIN, false)
    }

    /**
     * Decides whether the welcome screen should be skipped.
     * True if the user is logged in OR has explicitly chosen to skip login.
     */
    fun shouldSkipWelcome(): Boolean {
        return isLoggedIn() || hasSkippedLogin()
    }

    /** Persists the base64-encoded avatar image. */
    fun saveAvatar(avatar: String) {
        prefs.edit { putString(KEY_AVATAR, avatar) }
    }

    /** Retrieves the persisted avatar, or null. */
    fun getAvatar(): String? {
        return prefs.getString(KEY_AVATAR, null)
    }

    /** Clears the persisted avatar. */
    fun clearAvatar() {
        prefs.edit { remove(KEY_AVATAR) }
    }

    companion object {
        private const val PREFS_NAME = "home_money_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_SKIPPED_LOGIN = "skipped_login"
    }
}
