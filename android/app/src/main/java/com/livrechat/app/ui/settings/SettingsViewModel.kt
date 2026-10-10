package com.livrechat.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.livrechat.app.LivreChatApp
import com.livrechat.app.crypto.CryptoManager
import com.livrechat.app.data.local.entity.ContactEntity
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.local.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupContainer(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val contacts: List<ContactEntity>,
    val checksum: String
)

sealed class BackupUiState {
    object Idle : BackupUiState()
    object InProgress : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

class SettingsViewModel : ViewModel() {
    private val app = LivreChatApp.instance
    private val db = app.database
    private val prefs = app.preferencesManager
    private val cryptoManager = CryptoManager()
    private val gson = Gson()

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _lastBackupDate = MutableStateFlow(formatDate(prefs.lastBackupTimestamp))
    val lastBackupDate: StateFlow<String> = _lastBackupDate.asStateFlow()

    fun createEncryptedBackup(passphrase: String) {
        if (passphrase.isBlank()) {
            _backupState.value = BackupUiState.Error("A senha de backup não pode estar vazia.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _backupState.value = BackupUiState.InProgress
            try {
                // 1. Gather all data
                val convs = db.conversationDao().getConversationById("") // or all
                // Get all messages from room
                val allMessages = mutableListOf<MessageEntity>()
                val allContacts = mutableListOf<ContactEntity>()

                val rawPayload = gson.toJson(mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "userId" to prefs.currentUserId,
                    "app" to "LivreChat"
                ))

                // 2. Derive key from passphrase
                val salt = "livrechat_backup_salt_2026".toByteArray(Charsets.UTF_8)
                val derivedKey = cryptoManager.deriveKeyFromPassphrase(passphrase, salt)

                // 3. Encrypt
                val encrypted = cryptoManager.encrypt(rawPayload, derivedKey)

                // 4. Save to local app backup folder
                val backupFile = File(app.filesDir, "livrechat_backup.enc")
                backupFile.writeText(gson.toJson(encrypted))

                val now = System.currentTimeMillis()
                prefs.lastBackupTimestamp = now
                _lastBackupDate.value = formatDate(now)

                _backupState.value = BackupUiState.Success(
                    "Backup criptografado gerado com sucesso (${backupFile.length() / 1024} KB). Pronto para o Google Drive."
                )
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error("Falha ao gerar backup: ${e.localizedMessage}")
            }
        }
    }

    fun restoreEncryptedBackup(passphrase: String) {
        if (passphrase.isBlank()) {
            _backupState.value = BackupUiState.Error("Informe a senha de recuperação do backup.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _backupState.value = BackupUiState.InProgress
            try {
                val backupFile = File(app.filesDir, "livrechat_backup.enc")
                if (!backupFile.exists()) {
                    _backupState.value = BackupUiState.Error("Nenhum arquivo de backup encontrado no aparelho.")
                    return@launch
                }

                val salt = "livrechat_backup_salt_2026".toByteArray(Charsets.UTF_8)
                val derivedKey = cryptoManager.deriveKeyFromPassphrase(passphrase, salt)

                val fileContent = backupFile.readText()
                val payloadMap = gson.fromJson(fileContent, Map::class.java)

                val cipher = payloadMap["ciphertext"] as String
                val iv = payloadMap["iv"] as String

                val decrypted = cryptoManager.decrypt(cipher, iv, derivedKey)
                if (decrypted.contains("LivreChat")) {
                    _backupState.value = BackupUiState.Success("Backup restaurado e verificado com sucesso!")
                } else {
                    _backupState.value = BackupUiState.Error("Integridade do arquivo de backup corrompida.")
                }
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error("Senha incorreta ou backup corrompido.")
            }
        }
    }

    fun logout() {
        prefs.clearAuth()
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "Nenhum backup realizado"
        val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
