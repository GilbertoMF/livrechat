package com.livrechat.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
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
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupContainer(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val contacts: List<ContactEntity>,
    val checksum: String = ""
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

    val googleDriveAccount: StateFlow<String?> = prefs.googleDriveAccountFlow

    fun setGoogleDriveAccount(account: String?) {
        prefs.googleDriveAccount = account?.trim()?.ifBlank { null }
    }

    fun clearBackupState() {
        _backupState.value = BackupUiState.Idle
    }

    fun createEncryptedBackup(passphrase: String, outputStream: OutputStream? = null) {
        if (passphrase.isBlank()) {
            _backupState.value = BackupUiState.Error("A senha de backup não pode estar vazia.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _backupState.value = BackupUiState.InProgress
            try {
                // 1. Gather all real data from Room database
                val allConversations = db.conversationDao().getAllConversationsSync()
                val allMessages = db.messageDao().getAllMessages()
                val allContacts = db.contactDao().getAllContacts()

                val backupPayload = mapOf(
                    "version" to 1,
                    "app" to "LivreChat",
                    "timestamp" to System.currentTimeMillis(),
                    "userId" to (prefs.currentUserId ?: ""),
                    "googleDriveAccount" to (prefs.googleDriveAccount ?: ""),
                    "conversations" to allConversations,
                    "messages" to allMessages,
                    "contacts" to allContacts
                )
                val rawPayload = gson.toJson(backupPayload)

                // 2. Derive key from passphrase
                val salt = "livrechat_backup_salt_2026".toByteArray(Charsets.UTF_8)
                val derivedKey = cryptoManager.deriveKeyFromPassphrase(passphrase, salt)

                // 3. Encrypt AES-256-GCM
                val encrypted = cryptoManager.encrypt(rawPayload, derivedKey)
                val encryptedJson = gson.toJson(encrypted)

                // 4. Save to local app backup folder
                val backupFile = File(app.filesDir, "livrechat_backup.enc")
                backupFile.writeText(encryptedJson)

                // 5. If outputStream provided (Google Drive / SAF), stream it
                if (outputStream != null) {
                    try {
                        outputStream.use { os ->
                            os.write(encryptedJson.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    } catch (e: Exception) {
                        _backupState.value = BackupUiState.Error("Erro ao gravar no Google Drive: ${e.localizedMessage}")
                        return@launch
                    }
                }

                val now = System.currentTimeMillis()
                prefs.lastBackupTimestamp = now
                _lastBackupDate.value = formatDate(now)

                val destText = if (outputStream != null) "no Google Drive / Armazenamento" else "localmente"
                val sizeKb = (backupFile.length() / 1024).coerceAtLeast(1)
                _backupState.value = BackupUiState.Success(
                    "Backup criptografado salvo $destText com sucesso! (${sizeKb} KB - ${allConversations.size} conversas, ${allMessages.size} mensagens, ${allContacts.size} contatos)."
                )
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error("Falha ao gerar backup: ${e.localizedMessage}")
            }
        }
    }

    fun restoreEncryptedBackup(passphrase: String, inputStream: InputStream? = null) {
        if (passphrase.isBlank()) {
            _backupState.value = BackupUiState.Error("Informe a senha de recuperação do backup.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _backupState.value = BackupUiState.InProgress
            try {
                val fileContent: String = if (inputStream != null) {
                    try {
                        inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } catch (e: Exception) {
                        _backupState.value = BackupUiState.Error("Falha ao ler arquivo de backup: ${e.localizedMessage}")
                        return@launch
                    }
                } else {
                    val backupFile = File(app.filesDir, "livrechat_backup.enc")
                    if (!backupFile.exists()) {
                        _backupState.value = BackupUiState.Error("Nenhum arquivo de backup encontrado no aparelho.")
                        return@launch
                    }
                    backupFile.readText()
                }

                val salt = "livrechat_backup_salt_2026".toByteArray(Charsets.UTF_8)
                val derivedKey = cryptoManager.deriveKeyFromPassphrase(passphrase, salt)

                val payloadMap = gson.fromJson(fileContent, Map::class.java)
                val cipher = payloadMap["ciphertext"] as? String
                val iv = payloadMap["iv"] as? String

                if (cipher == null || iv == null) {
                    _backupState.value = BackupUiState.Error("Formato de backup inválido ou arquivo danificado.")
                    return@launch
                }

                val decrypted = cryptoManager.decrypt(cipher, iv, derivedKey)
                if (!decrypted.contains("LivreChat")) {
                    _backupState.value = BackupUiState.Error("Integridade do arquivo de backup corrompida ou senha incorreta.")
                    return@launch
                }

                // Parse json payload
                val jsonObject = JsonParser.parseString(decrypted).asJsonObject

                // Restore conversations
                var restoredConvs = 0
                if (jsonObject.has("conversations")) {
                    val convListType = object : TypeToken<List<ConversationEntity>>() {}.type
                    val convs: List<ConversationEntity> = gson.fromJson(jsonObject.get("conversations"), convListType) ?: emptyList()
                    if (convs.isNotEmpty()) {
                        db.conversationDao().insertAll(convs)
                        restoredConvs = convs.size
                    }
                }

                // Restore messages
                var restoredMsgs = 0
                if (jsonObject.has("messages")) {
                    val msgListType = object : TypeToken<List<MessageEntity>>() {}.type
                    val msgs: List<MessageEntity> = gson.fromJson(jsonObject.get("messages"), msgListType) ?: emptyList()
                    if (msgs.isNotEmpty()) {
                        db.messageDao().insertAll(msgs)
                        restoredMsgs = msgs.size
                    }
                }

                // Restore contacts
                var restoredContacts = 0
                if (jsonObject.has("contacts")) {
                    val contactListType = object : TypeToken<List<ContactEntity>>() {}.type
                    val contacts: List<ContactEntity> = gson.fromJson(jsonObject.get("contacts"), contactListType) ?: emptyList()
                    if (contacts.isNotEmpty()) {
                        db.contactDao().insertAll(contacts)
                        restoredContacts = contacts.size
                    }
                }

                _backupState.value = BackupUiState.Success(
                    "Backup restaurado com sucesso! ($restoredConvs conversas, $restoredMsgs mensagens e $restoredContacts contatos recuperados)."
                )
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error("Senha incorreta ou arquivo de backup inválido.")
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
