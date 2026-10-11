package com.livrechat.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "livrechat_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("livrechat_prefs_fallback", Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "https://livrechat-server.onrender.com") ?: "https://livrechat-server.onrender.com"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var currentUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var currentUsername: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var currentDisplayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    var backupPassphrase: String?
        get() = prefs.getString(KEY_BACKUP_PASSPHRASE, null)
        set(value) = prefs.edit().putString(KEY_BACKUP_PASSPHRASE, value).apply()

    var isAutoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()

    var isBackupWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKUP_WIFI_ONLY, value).apply()

    var lastBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    private val _themeFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString(KEY_APP_THEME, "system") ?: "system")
    val themeFlow: kotlinx.coroutines.flow.StateFlow<String> = _themeFlow

    var appTheme: String
        get() = prefs.getString(KEY_APP_THEME, "system") ?: "system"
        set(value) {
            prefs.edit().putString(KEY_APP_THEME, value).apply()
            _themeFlow.value = value
        }

    fun isLoggedIn(): Boolean {
        return !accessToken.isNullOrEmpty() && !currentUserId.isNullOrEmpty()
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_BACKUP_PASSPHRASE = "backup_passphrase"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_BACKUP_WIFI_ONLY = "backup_wifi_only"
        private const val KEY_LAST_BACKUP = "last_backup"
        private const val KEY_APP_THEME = "app_theme"
    }
}
