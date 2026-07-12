package com.chronie.homemoney.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveUsername(username: String) {
        prefs.edit { putString(KEY_USERNAME, username) }
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun clearUsername() {
        prefs.edit { remove(KEY_USERNAME) }
    }

    fun isLoggedIn(): Boolean {
        return !getUsername().isNullOrEmpty()
    }

    fun setSkippedLogin(skipped: Boolean) {
        prefs.edit { putBoolean(KEY_SKIPPED_LOGIN, skipped) }
    }

    fun hasSkippedLogin(): Boolean {
        return prefs.getBoolean(KEY_SKIPPED_LOGIN, false)
    }

    fun shouldSkipWelcome(): Boolean {
        return isLoggedIn() || hasSkippedLogin()
    }

    // Avatar-related features
    fun saveAvatar(avatar: String) {
        prefs.edit { putString(KEY_AVATAR, avatar) }
    }

    fun getAvatar(): String? {
        return prefs.getString(KEY_AVATAR, null)
    }

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
