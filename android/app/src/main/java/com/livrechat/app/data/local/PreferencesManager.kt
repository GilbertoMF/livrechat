package com.livrechat.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(private val context: Context) {
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
    private val _paletteFlow = kotlinx.coroutines.flow.MutableStateFlow(colorPalette)
    val paletteFlow: kotlinx.coroutines.flow.StateFlow<String> = _paletteFlow

    var colorPalette: String
        get() = prefs.getString(KEY_COLOR_PALETTE, "default") ?: "default"
        set(value) {
            prefs.edit().putString(KEY_COLOR_PALETTE, value).apply()
            _paletteFlow.value = value
        }

    val themeFlow: kotlinx.coroutines.flow.StateFlow<String> = _themeFlow

    var appTheme: String
        get() = prefs.getString(KEY_APP_THEME, "system") ?: "system"
        set(value) {
            prefs.edit().putString(KEY_APP_THEME, value).apply()
            _themeFlow.value = value
        }

    var isBiometricEnabled: Boolean
        get() {
            val uid = currentUserId
            if (uid.isNullOrBlank()) return false
            return prefs.getBoolean("biometric_enabled_$uid", false)
        }
        set(value) {
            val uid = currentUserId ?: return
            prefs.edit().putBoolean("biometric_enabled_$uid", value).apply()
        }

    var isPinLockEnabled: Boolean
        get() {
            val uid = currentUserId
            if (uid.isNullOrBlank()) return false
            return prefs.getBoolean("pin_lock_enabled_$uid", false)
        }
        set(value) {
            val uid = currentUserId ?: return
            prefs.edit().putBoolean("pin_lock_enabled_$uid", value).apply()
        }

    var appLockPin: String?
        get() {
            val uid = currentUserId
            if (uid.isNullOrBlank()) return null
            return prefs.getString("app_lock_pin_$uid", null)
        }
        set(value) {
            val uid = currentUserId ?: return
            prefs.edit().putString("app_lock_pin_$uid", value).apply()
        }

    var selfDestructTimer: String
        get() = prefs.getString(KEY_SELF_DESTRUCT_TIMER, "off") ?: "off"
        set(value) = prefs.edit().putString(KEY_SELF_DESTRUCT_TIMER, value).apply()

    private val _blockScreenshotsFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, false))
    val blockScreenshotsFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _blockScreenshotsFlow

    var blockScreenshots: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BLOCK_SCREENSHOTS, value).apply()
            _blockScreenshotsFlow.value = value
        }

    var readReceiptsEnabled: Boolean
        get() = prefs.getBoolean(KEY_READ_RECEIPTS, true)
        set(value) = prefs.edit().putBoolean(KEY_READ_RECEIPTS, value).apply()

    var lastSeenVisibility: String
        get() = prefs.getString(KEY_LAST_SEEN, "everyone") ?: "everyone"
        set(value) = prefs.edit().putString(KEY_LAST_SEEN, value).apply()

    var highPriorityNotifications: Boolean
        get() = prefs.getBoolean(KEY_HIGH_PRIORITY_NOTIFS, true)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_PRIORITY_NOTIFS, value).apply()

    var conversationSounds: Boolean
        get() = prefs.getBoolean(KEY_CONV_SOUNDS, true)
        set(value) = prefs.edit().putBoolean(KEY_CONV_SOUNDS, value).apply()

    var mediaAutoDownloadWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_MEDIA_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_MEDIA_WIFI_ONLY, value).apply()

    var hdMediaQuality: Boolean
        get() = prefs.getBoolean(KEY_HD_MEDIA, true)
        set(value) = prefs.edit().putBoolean(KEY_HD_MEDIA, value).apply()

    var userBio: String
        get() = prefs.getString(KEY_USER_BIO, "Disponível no LivreChat") ?: "Disponível no LivreChat"
        set(value) = prefs.edit().putString(KEY_USER_BIO, value).apply()

    var userAvatarUrl: String?
        get() = prefs.getString(KEY_USER_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_USER_AVATAR, value).apply()

    fun getLockedConversations(): Set<String> {
        return prefs.getStringSet(KEY_LOCKED_CONVERSATIONS, emptySet()) ?: emptySet()
    }

    fun isConversationLocked(conversationId: String): Boolean {
        return getLockedConversations().contains(conversationId)
    }

    fun setConversationLocked(conversationId: String, locked: Boolean) {
        val current = getLockedConversations().toMutableSet()
        if (locked) current.add(conversationId) else current.remove(conversationId)
        prefs.edit().putStringSet(KEY_LOCKED_CONVERSATIONS, current).apply()
    }

    var callRingtoneUri: String?
        get() = prefs.getString(KEY_CALL_RINGTONE_URI, null)
        set(value) = prefs.edit().putString(KEY_CALL_RINGTONE_URI, value).apply()

    var callRingtoneName: String
        get() = prefs.getString(KEY_CALL_RINGTONE_NAME, "Padrão do Sistema") ?: "Padrão do Sistema"
        set(value) = prefs.edit().putString(KEY_CALL_RINGTONE_NAME, value).apply()

    var notificationRingtoneUri: String?
        get() = prefs.getString(KEY_NOTIF_RINGTONE_URI, null)
        set(value) = prefs.edit().putString(KEY_NOTIF_RINGTONE_URI, value).apply()

    var notificationRingtoneName: String
        get() = prefs.getString(KEY_NOTIF_RINGTONE_NAME, "Padrão do Sistema") ?: "Padrão do Sistema"
        set(value) = prefs.edit().putString(KEY_NOTIF_RINGTONE_NAME, value).apply()

    fun isLoggedIn(): Boolean {
        return !accessToken.isNullOrEmpty() && !currentUserId.isNullOrEmpty()
    }

    fun clearAuth() {
        val uid = currentUserId
        val editor = prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_USER_BIO)
            .remove(KEY_USER_AVATAR)
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_PIN_LOCK_ENABLED)
            .remove(KEY_APP_LOCK_PIN)
        if (!uid.isNullOrBlank()) {
            editor.remove("biometric_enabled_$uid")
                .remove("pin_lock_enabled_$uid")
                .remove("app_lock_pin_$uid")
        }
        editor.apply()

        try {
            val appFilesDir = context.filesDir
            java.io.File(appFilesDir, "avatar_current.jpg").delete()
            appFilesDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("avatar_")) {
                    f.delete()
                }
            }
        } catch (e: Exception) {}
    }

    fun setContactCustomName(userId: String, customName: String) {
        val myId = currentUserId ?: "global"
        prefs.edit().putString("contact_name_${myId}_$userId", customName.trim()).apply()
    }

    fun getContactCustomName(userId: String): String? {
        val myId = currentUserId ?: "global"
        val name = prefs.getString("contact_name_${myId}_$userId", null)
        return if (name.isNullOrBlank()) null else name.trim()
    }

    fun getChatWallpaper(conversationId: String?): String? {
        if (!conversationId.isNullOrBlank()) {
            val convWp = prefs.getString("chat_wallpaper_$conversationId", null)
            if (!convWp.isNullOrBlank()) return convWp
        }
        return prefs.getString(KEY_GLOBAL_WALLPAPER, null)
    }

    fun setChatWallpaper(conversationId: String?, value: String?) {
        if (!conversationId.isNullOrBlank()) {
            if (value == null) {
                prefs.edit().remove("chat_wallpaper_$conversationId").apply()
            } else {
                prefs.edit().putString("chat_wallpaper_$conversationId", value).apply()
            }
        } else {
            if (value == null) {
                prefs.edit().remove(KEY_GLOBAL_WALLPAPER).apply()
            } else {
                prefs.edit().putString(KEY_GLOBAL_WALLPAPER, value).apply()
            }
        }
    }

    fun isUserBlocked(userId: String): Boolean {
        val currentBlocked = prefs.getStringSet("blocked_users_set", emptySet()) ?: emptySet()
        return currentBlocked.contains(userId)
    }

    fun setUserBlocked(userId: String, blocked: Boolean) {
        val currentBlocked = (prefs.getStringSet("blocked_users_set", emptySet()) ?: emptySet()).toMutableSet()
        if (blocked) {
            currentBlocked.add(userId)
        } else {
            currentBlocked.remove(userId)
        }
        prefs.edit().putStringSet("blocked_users_set", currentBlocked).apply()
    }

    fun getBlockedUserIds(): Set<String> {
        return prefs.getStringSet("blocked_users_set", emptySet()) ?: emptySet()
    }

    companion object {
        private const val KEY_GLOBAL_WALLPAPER = "chat_wallpaper_global"
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
        private const val KEY_COLOR_PALETTE = "color_palette"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_PIN_LOCK_ENABLED = "pin_lock_enabled"
        private const val KEY_APP_LOCK_PIN = "app_lock_pin"
        private const val KEY_SELF_DESTRUCT_TIMER = "self_destruct_timer"
        private const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"
        private const val KEY_READ_RECEIPTS = "read_receipts"
        private const val KEY_LAST_SEEN = "last_seen"
        private const val KEY_HIGH_PRIORITY_NOTIFS = "high_priority_notifs"
        private const val KEY_CONV_SOUNDS = "conv_sounds"
        private const val KEY_MEDIA_WIFI_ONLY = "media_wifi_only"
        private const val KEY_HD_MEDIA = "hd_media"
        private const val KEY_USER_BIO = "user_bio"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_LOCKED_CONVERSATIONS = "locked_conversations"
        private const val KEY_CALL_RINGTONE_URI = "call_ringtone_uri"
        private const val KEY_CALL_RINGTONE_NAME = "call_ringtone_name"
        private const val KEY_NOTIF_RINGTONE_URI = "notif_ringtone_uri"
        private const val KEY_NOTIF_RINGTONE_NAME = "notif_ringtone_name"
    }
}
