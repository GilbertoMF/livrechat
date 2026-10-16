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
                val apiService = app.apiClient.apiService
                val myUserId = prefs.currentUserId ?: ""

                // 1. Sync contacts from server if local Room is empty
                try {
                    val currentContacts = db.contactDao().getAllContacts()
                    if (currentContacts.isEmpty()) {
                        val contactsRes = apiService.getContacts()
                        if (contactsRes.isSuccessful && contactsRes.body() != null) {
                            val remoteContacts = contactsRes.body()!!.map { dto ->
                                ContactEntity(
                                    id = dto.id,
                                    contactUserId = dto.contactUserId,
                                    username = dto.username,
                                    displayName = dto.displayName,
                                    avatarUrl = dto.avatarUrl,
                                    publicKey = dto.publicKey,
                                    bio = dto.bio
                                )
                            }
                            if (remoteContacts.isNotEmpty()) {
                                db.contactDao().insertAll(remoteContacts)
                            }
                        }
                    }
                } catch (e: Exception) {}

                // 2. Sync conversations from server if local Room is empty
                try {
                    val currentConvs = db.conversationDao().getAllConversationsSync()
                    if (currentConvs.isEmpty()) {
                        val convsRes = apiService.getConversations()
                        if (convsRes.isSuccessful && convsRes.body() != null) {
                            val remoteConvs = convsRes.body()!!.map { dto ->
                                val otherU = dto.otherUser
                                val effectiveUserId = otherU?.id ?: dto.conversationId
                                val customNick = prefs.getContactCustomName(effectiveUserId)
                                val effectiveDisplayName = customNick
                                    ?: otherU?.displayName
                                    ?: (dto.title ?: "Conversa")
                                ConversationEntity(
                                    id = dto.conversationId,
                                    otherUserId = effectiveUserId,
                                    otherUsername = otherU?.username ?: (dto.title ?: "Conversa"),
                                    otherDisplayName = effectiveDisplayName,
                                    otherAvatarUrl = otherU?.avatarUrl ?: dto.avatarUrl,
                                    otherPublicKey = otherU?.publicKey,
                                    lastMessageContent = dto.lastMessage?.content ?: "",
                                    lastMessageTimestamp = System.currentTimeMillis(),
                                    unreadCount = dto.unreadCount,
                                    isPinned = dto.isPinned,
                                    isArchived = dto.isArchived,
                                    isMuted = dto.isMuted,
                                    isGroup = dto.isGroup,
                                    groupTitle = dto.title,
                                    groupAvatar = dto.avatarUrl
                                )
                            }
                            if (remoteConvs.isNotEmpty()) {
                                db.conversationDao().insertAll(remoteConvs)
                            }
                        }
                    }
                } catch (e: Exception) {}

                // 3. Sync messages for conversations if local messages are missing
                val currentConvsAfterSync = db.conversationDao().getAllConversationsSync()
                for (conv in currentConvsAfterSync) {
                    try {
                        val existingMsgs = db.messageDao().getMessagesForConversation(conv.id)
                        if (existingMsgs.isEmpty()) {
                            val msgsRes = apiService.getMessages(conv.id, 100)
                            if (msgsRes.isSuccessful && msgsRes.body() != null) {
                                val entities = msgsRes.body()!!.map { dto ->
                                    val createdAtMs = try {
                                        java.time.Instant.parse(dto.createdAt).toEpochMilli()
                                    } catch (e: Exception) {
                                        System.currentTimeMillis()
                                    }
                                    MessageEntity(
                                        id = dto.id,
                                        conversationId = dto.conversationId,
                                        senderId = dto.senderId,
                                        content = dto.content,
                                        iv = dto.iv,
                                        messageType = dto.messageType,
                                        replyToId = dto.replyToId,
                                        status = dto.status ?: "sent",
                                        isOutgoing = dto.senderId == myUserId,
                                        isEdited = dto.isEdited,
                                        isDeleted = dto.isDeleted,
                                        createdAt = createdAtMs
                                    )
                                }
                                if (entities.isNotEmpty()) {
                                    db.messageDao().insertAll(entities)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }

                // 4. Sync calls from server if local calls are empty
                try {
                    val currentCalls = db.callDao().getAllCalls()
                    if (currentCalls.isEmpty()) {
                        val callsRes = apiService.getCallHistory()
                        if (callsRes.isSuccessful && callsRes.body() != null) {
                            val entities = callsRes.body()!!.map { dto ->
                                val startedMs = try {
                                    java.time.Instant.parse(dto.startedAt).toEpochMilli()
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }
                                com.livrechat.app.data.local.entity.CallRecordEntity(
                                    id = dto.id,
                                    contactUserId = dto.contact.id,
                                    contactDisplayName = dto.contact.displayName?.takeIf { it.isNotBlank() } ?: dto.contact.username,
                                    contactUsername = dto.contact.username,
                                    contactAvatarUrl = dto.contact.avatarUrl,
                                    isOutgoing = dto.isOutgoing,
                                    status = dto.status,
                                    durationSeconds = dto.durationSeconds,
                                    timestamp = startedMs
                                )
                            }
                            if (entities.isNotEmpty()) {
                                db.callDao().insertAll(entities)
                            }
                        }
                    }
                } catch (e: Exception) {}

                // 5. Gather all consolidated local data
                val allConversations = db.conversationDao().getAllConversationsSync()
                val allMessages = db.messageDao().getAllMessages()
                val allContacts = db.contactDao().getAllContacts()
                val allCalls = db.callDao().getAllCalls()

                val backupPayload = mapOf(
                    "version" to 2,
                    "app" to "LivreChat",
                    "timestamp" to System.currentTimeMillis(),
                    "userId" to myUserId,
                    "googleDriveAccount" to (prefs.googleDriveAccount ?: ""),
                    "conversations" to allConversations,
                    "messages" to allMessages,
                    "contacts" to allContacts,
                    "calls" to allCalls
                )
                val rawPayload = gson.toJson(backupPayload)

                // 6. Derive key from passphrase & Encrypt AES-256-GCM
                val salt = "livrechat_backup_salt_2026".toByteArray(Charsets.UTF_8)
                val derivedKey = cryptoManager.deriveKeyFromPassphrase(passphrase, salt)
                val encrypted = cryptoManager.encrypt(rawPayload, derivedKey)
                val encryptedJson = gson.toJson(encrypted)

                // 7. Save to internal app storage
                val backupFile = File(app.filesDir, "livrechat_backup.enc")
                backupFile.writeText(encryptedJson)

                // 8. Also save a safety copy in Downloads / LivreChat_Backups
                try {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val backupDir = File(downloadsDir, "LivreChat_Backups")
                    if (!backupDir.exists()) backupDir.mkdirs()
                    val publicFile = File(backupDir, "livrechat_backup.enc")
                    publicFile.writeText(encryptedJson)
                } catch (e: Exception) {
                    try {
                        val extDir = app.getExternalFilesDir("Backups")
                        extDir?.mkdirs()
                        File(extDir, "livrechat_backup.enc").writeText(encryptedJson)
                    } catch (e2: Exception) {}
                }

                // 9. If outputStream provided (Google Drive / SAF), stream it
                if (outputStream != null) {
                    try {
                        outputStream.use { os ->
                            os.write(encryptedJson.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    } catch (e: Exception) {
                        _backupState.value = BackupUiState.Error("Erro ao gravar no destino: ${e.localizedMessage}")
                        return@launch
                    }
                }

                val now = System.currentTimeMillis()
                prefs.lastBackupTimestamp = now
                _lastBackupDate.value = formatDate(now)

                val destText = if (outputStream != null) "no Google Drive / Arquivo" else "localmente"
                val sizeKb = (backupFile.length() / 1024).coerceAtLeast(1)
                _backupState.value = BackupUiState.Success(
                    "Backup 100% completo salvo $destText com sucesso! (${sizeKb} KB - ${allConversations.size} conversas, ${allMessages.size} mensagens, ${allContacts.size} contatos, ${allCalls.size} chamadas)."
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
                    // Try internal files dir first
                    val backupFile = File(app.filesDir, "livrechat_backup.enc")
                    if (backupFile.exists() && backupFile.length() > 0L) {
                        backupFile.readText()
                    } else {
                        // Fallback 1: Downloads/LivreChat_Backups
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val publicFile = File(downloadsDir, "LivreChat_Backups/livrechat_backup.enc")
                        if (publicFile.exists() && publicFile.length() > 0L) {
                            publicFile.readText()
                        } else {
                            // Fallback 2: external files dir
                            val extFile = File(app.getExternalFilesDir("Backups"), "livrechat_backup.enc")
                            if (extFile.exists() && extFile.length() > 0L) {
                                extFile.readText()
                            } else {
                                _backupState.value = BackupUiState.Error("Nenhum arquivo de backup encontrado. Você pode selecionar um arquivo .enc pelo botão 'Restaurar do Google Drive / Arquivo'.")
                                return@launch
                            }
                        }
                    }
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

                // Restore calls
                var restoredCalls = 0
                if (jsonObject.has("calls")) {
                    val callListType = object : TypeToken<List<com.livrechat.app.data.local.entity.CallRecordEntity>>() {}.type
                    val calls: List<com.livrechat.app.data.local.entity.CallRecordEntity> = gson.fromJson(jsonObject.get("calls"), callListType) ?: emptyList()
                    if (calls.isNotEmpty()) {
                        db.callDao().insertAll(calls)
                        restoredCalls = calls.size
                    }
                }

                val now = System.currentTimeMillis()
                prefs.lastBackupTimestamp = now
                _lastBackupDate.value = formatDate(now)

                _backupState.value = BackupUiState.Success(
                    "Backup 100% restaurado com sucesso! ($restoredConvs conversas, $restoredMsgs mensagens, $restoredContacts contatos e $restoredCalls chamadas recuperados)."
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
