package com.chronie.homemoney.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun clearUsername() {
        prefs.edit().remove(KEY_USERNAME).apply()
    }

    fun isLoggedIn(): Boolean {
        return !getUsername().isNullOrEmpty()
    }

    companion object {
        private const val PREFS_NAME = "home_money_prefs"
        private const val KEY_USERNAME = "username"
    }
}
