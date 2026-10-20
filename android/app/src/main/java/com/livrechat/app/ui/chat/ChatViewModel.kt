package com.livrechat.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.livrechat.app.LivreChatApp
import com.livrechat.app.crypto.CryptoManager
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.data.remote.WebSocketClient
import com.livrechat.app.data.remote.WebSocketEventsListener
import com.livrechat.app.data.remote.model.SendMessageRequest
import com.livrechat.app.util.ChatSoundManager
import com.livrechat.app.util.MediaFileManager
import com.livrechat.app.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.UUID
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel : ViewModel(), WebSocketEventsListener {
    private val app = LivreChatApp.instance
    private val db = app.database
    private val apiService = app.apiClient.apiService
    private val prefs = app.preferencesManager
    private val cryptoManager = CryptoManager()

    val conversationsFlow: Flow<List<ConversationEntity>> = db.conversationDao().getAllConversationsFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _currentOtherUserId = MutableStateFlow<String?>(null)
    val currentOtherUserId: StateFlow<String?> = _currentOtherUserId.asStateFlow()

    private val _messagesFlow = MutableStateFlow<Flow<List<MessageEntity>>>(emptyFlow())
    val messagesFlow: StateFlow<Flow<List<MessageEntity>>> = _messagesFlow.asStateFlow()

    private val _isContactTyping = MutableStateFlow(false)
    val isContactTyping: StateFlow<Boolean> = _isContactTyping.asStateFlow()

    private val _isContactRecordingAudio = MutableStateFlow(false)
    val isContactRecordingAudio: StateFlow<Boolean> = _isContactRecordingAudio.asStateFlow()

    private val connectionManager = app.connectionManager
    val isConnected: StateFlow<Boolean> = connectionManager.isConnected
    val typingStatus: StateFlow<Map<String, Boolean>> = connectionManager.typingStatus
    val recordingAudioStatus: StateFlow<Map<String, Boolean>> = connectionManager.recordingAudioStatus

    private val _chatErrorMessage = MutableStateFlow<String?>(null)
    val chatErrorMessage: StateFlow<String?> = _chatErrorMessage.asStateFlow()

    fun clearChatError() {
        _chatErrorMessage.value = null
    }

    init {
        connectionManager.registerListener(this)
        syncConversations()
    }

    fun selectConversation(conversationId: String, otherUserId: String? = null) {
        _currentConversationId.value = conversationId
        _currentOtherUserId.value = otherUserId
        _isContactRecordingAudio.value = false
        contactRecordingAudioJob?.cancel()
        _isContactTyping.value = false
        contactTypingJob?.cancel()
        connectionManager.setActiveConversation(conversationId)
        if (otherUserId != null) {
            connectionManager.queryUserStatus(otherUserId)
        }
        _messagesFlow.value = db.messageDao().getMessagesForConversationFlow(conversationId)

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            try {
                val futureList = db.messageDao().getFutureMessages(conversationId, now + 30_000L)
                for (m in futureList) {
                    val corrected = m.createdAt - 10_800_000L
                    val safeTime = if (corrected > now) now - 1000L else corrected
                    db.messageDao().updateCreatedAt(m.id, safeTime)
                }
            } catch (e: Exception) {}

            db.conversationDao().clearUnreadCount(conversationId)
            // Marcar todas as mensagens recebidas como lidas e notificar remetente com 2 ticks azuis (se ativado)
            val unreadList = db.messageDao().getUnreadIncomingMessages(conversationId)
            db.messageDao().markAllIncomingAsRead(conversationId)
            if (prefs.readReceiptsEnabled) {
                try {
                    apiService.markConversationRead(conversationId)
                } catch (e: Exception) {}
                for (msg in unreadList) {
                    connectionManager.getClient().sendReceipt(msg.id, "read")
                    try { apiService.markMessageRead(msg.id) } catch (e: Exception) {}
                }
            }
            syncMessagesForConversation(conversationId)
        }
    }

    fun unselectConversation() {
        val convId = _currentConversationId.value
        if (convId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                db.conversationDao().clearUnreadCount(convId)
                db.messageDao().markAllIncomingAsRead(convId)
            }
        }
        _currentConversationId.value = null
        _currentOtherUserId.value = null
        connectionManager.setActiveConversation(null)
    }

    fun syncConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = apiService.getConversations()
                if (res.isSuccessful && res.body() != null) {
                    val entities = res.body()!!.map { dto ->
                        val localExisting = db.conversationDao().getConversationById(dto.conversationId)
                        val localUnread = db.messageDao().getUnreadCount(dto.conversationId)
                        val calculatedUnread = if (localExisting != null && localExisting.unreadCount == 0 && localUnread == 0) {
                            0
                        } else if (localUnread > 0) {
                            localUnread
                        } else {
                            dto.unreadCount
                        }
                        val otherU = dto.otherUser
                        val preview = when (dto.lastMessage?.messageType) {
                            "image" -> "📷 Foto"
                            "audio" -> "🎵 Áudio"
                            "video" -> "🎥 Vídeo"
                            "file" -> "📄 Documento"
                            else -> {
                                val c = dto.lastMessage?.content
                                when {
                                    c == null -> localExisting?.lastMessageContent
                                    c.startsWith("data:image") -> "📷 Foto"
                                    c.startsWith("data:audio") || c.endsWith(".m4a") || c.endsWith(".mp3") || c.contains("voice_") -> "🎵 Áudio"
                                    c.startsWith("data:video") || c.endsWith(".mp4") -> "🎥 Vídeo"
                                    c.length > 100 -> c.take(100) + "..."
                                    else -> c
                                }
                            }
                        }
                        val effectiveUserId = otherU?.id ?: dto.conversationId
                        val customNick = prefs.getContactCustomName(effectiveUserId)
                        val effectiveDisplayName = customNick
                            ?: localExisting?.otherDisplayName?.takeIf { !it.startsWith("ID ") && it.isNotBlank() }
                            ?: otherU?.displayName
                            ?: (dto.title ?: "Grupo")

                        ConversationEntity(
                            id = dto.conversationId,
                            otherUserId = effectiveUserId,
                            otherUsername = otherU?.username ?: (dto.title ?: "Grupo"),
                            otherDisplayName = effectiveDisplayName,
                            otherAvatarUrl = otherU?.avatarUrl ?: dto.avatarUrl,
                            otherPublicKey = otherU?.publicKey,
                            lastMessageContent = preview,
                            lastMessageTimestamp = localExisting?.lastMessageTimestamp ?: System.currentTimeMillis(),
                            unreadCount = calculatedUnread,
                            isPinned = dto.isPinned,
                            isArchived = dto.isArchived,
                            isMuted = dto.isMuted,
                            isGroup = dto.isGroup,
                            groupTitle = dto.title,
                            groupAvatar = dto.avatarUrl,
                            adminOnlyMessage = dto.adminOnlyMessage ?: localExisting?.adminOnlyMessage ?: false,
                            adminOnlyEditInfo = dto.adminOnlyEditInfo ?: localExisting?.adminOnlyEditInfo ?: false,
                            adminOnlyAddMembers = dto.adminOnlyAddMembers ?: localExisting?.adminOnlyAddMembers ?: false
                        )
                    }
                    db.conversationDao().insertAll(entities)
                }
            } catch (e: Exception) {
                // Offline fallback - use Room cache
            }
        }
    }

    private fun syncMessagesForConversation(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = apiService.getMessages(conversationId)
                if (res.isSuccessful && res.body() != null) {
                    val myId = prefs.currentUserId ?: ""
                    val dtoList = res.body()!!
                    val existingMap = db.messageDao().getMessagesForConversation(conversationId).associateBy { it.id }

                    val entities = dtoList.map { dto ->
                        val existing = existingMap[dto.id]
                        val calculatedStatus: String = if (dto.senderId == myId) {
                            val otherReceipts = dto.receipts?.filter { it.userId != null && it.userId != myId }
                            when {
                                existing?.status == "read" -> "read"
                                dto.status == "read" -> "read"
                                otherReceipts?.any { it.status == "read" } == true -> "read"
                                existing?.status == "delivered" -> "delivered"
                                dto.status == "delivered" -> "delivered"
                                otherReceipts?.any { it.status == "delivered" } == true -> "delivered"
                                else -> existing?.status ?: "sent"
                            }
                        } else {
                            "read"
                        }

                        // Prevent SQLiteBlobTooBigException: never store raw Base64 media directly into SQLite
                        var safeContent = dto.content
                        val existingFile = if (existing != null && MediaFileManager.isLocalFilePath(existing.content)) {
                            MediaFileManager.getLocalFile(existing.content)
                        } else null

                        if (existingFile != null && existingFile.length() > 500L && existing != null) {
                            safeContent = existing.content
                        } else if (dto.content.startsWith("http://") || dto.content.startsWith("https://")) {
                            safeContent = dto.content
                        } else if (dto.messageType in listOf("image", "video", "file", "audio", "sticker") || dto.content.startsWith("data:") || dto.content.length > 50000) {
                            if (!MediaFileManager.isLocalFilePath(dto.content) && !dto.content.startsWith("p2p://")) {
                                val ext = when (dto.messageType) {
                                    "image" -> "jpg"
                                    "video" -> "mp4"
                                    "audio" -> "m4a"
                                    "sticker" -> "png"
                                    else -> {
                                        val fn = dto.iv?.split("|")?.firstOrNull() ?: ""
                                        fn.substringAfterLast(".", "bin")
                                    }
                                }
                                val localFile = MediaFileManager.saveBase64ToLocalFile(app, dto.content, "received_${dto.id}.$ext")
                                if (localFile != null) {
                                    safeContent = localFile.absolutePath
                                } else if (dto.content.length > 250000) {
                                    safeContent = "[Arquivo transferido]"
                                }
                            }
                        }

                        val parsedCreatedAt = TimeUtils.parseIsoTimestamp(dto.createdAt)
                        val msgCreatedAt = if (existing != null && Math.abs(existing.createdAt - parsedCreatedAt) < 5_000) {
                            existing.createdAt
                        } else {
                            parsedCreatedAt
                        }

                        val isViewOnce = dto.messageType == "view_once_image" ||
                                dto.messageType == "view_once_video" ||
                                dto.iv?.contains("view_once:true") == true ||
                                existingMap[dto.id]?.isViewOnce == true
                        val viewOnceOpened = existingMap[dto.id]?.viewOnceOpened == true || safeContent == "[Mídia expirada]"
                        val isPinned = existingMap[dto.id]?.isPinned == true

                        MessageEntity(
                            id = dto.id,
                            conversationId = dto.conversationId,
                            senderId = dto.senderId,
                            content = if (viewOnceOpened) "[Mídia expirada]" else safeContent,
                            iv = dto.iv,
                            messageType = dto.messageType,
                            replyToId = dto.replyToId,
                            replyToContent = dto.replyToContent ?: existingMap[dto.id]?.replyToContent,
                            status = calculatedStatus,
                            isOutgoing = dto.senderId == myId,
                            isEdited = dto.isEdited,
                            isDeleted = dto.isDeleted,
                            isViewOnce = isViewOnce,
                            viewOnceOpened = viewOnceOpened,
                            isPinned = isPinned,
                            createdAt = msgCreatedAt
                        )
                    }
                    db.messageDao().insertAll(entities)

                    // For every incoming message that wasn't previously marked read, send read receipts if enabled!
                    if (prefs.readReceiptsEnabled) {
                        for (dto in dtoList) {
                            if (dto.senderId != myId) {
                                val wasRead = existingMap[dto.id]?.status == "read" || dto.receipts?.any { it.status == "read" && it.userId == myId } == true
                                if (!wasRead) {
                                    connectionManager.getClient().sendReceipt(dto.id, "read")
                                    try { apiService.markMessageRead(dto.id) } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline fallback
            }
        }
    }

    fun sendMessage(
        conversationId: String,
        text: String,
        replyToId: String? = null,
        replyToContent: String? = null,
        targetUserId: String? = null
    ) {
        if (text.isBlank()) return

        val myId = prefs.currentUserId ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        val clientMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Local optimistic insert
        val localMessage = MessageEntity(
            id = clientMessageId,
            conversationId = conversationId,
            senderId = myId,
            content = text,
            messageType = "text",
            replyToId = replyToId,
            replyToContent = replyToContent,
            status = "sending",
            isOutgoing = true,
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insertOrUpdate(localMessage)
            db.conversationDao().updateLastMessage(conversationId, text, now)

            // Try sending via WebSocket
            val wsSent = connectionManager.getClient().sendChatMessage(
                conversationId = conversationId,
                content = text,
                iv = null,
                messageType = "text",
                replyToId = replyToId,
                replyToContent = replyToContent,
                clientMessageId = clientMessageId,
                targetUserId = target
            )

            if (!wsSent) {
                // Fallback to REST API
                try {
                    val res = apiService.sendMessage(
                        SendMessageRequest(
                            conversationId = conversationId,
                            content = text,
                            clientMessageId = clientMessageId,
                            replyToId = replyToId,
                            replyToContent = replyToContent,
                            targetUserId = target
                        )
                    )
                    if (res.isSuccessful) {
                        db.messageDao().updateStatus(clientMessageId, "sent")
                    } else if (res.code() in 400..499) {
                        db.messageDao().updateStatus(clientMessageId, "failed")
                    }
                } catch (e: Exception) {
                    // Erro de rede ou sem conexão: mantém 'sending' na caixa de saída (ícone de relógio)
                }
            }
        }
    }

    fun sendAudioMessage(
        conversationId: String,
        audioBase64: String,
        durationSeconds: Int,
        targetUserId: String? = null,
        waveform: String? = null,
        localAudioFilePath: String? = null
    ) {
        if (audioBase64.isBlank()) return

        val myId = prefs.currentUserId ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        val clientMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Store local file path in SQLite to keep database rows tiny
        val safeLocalContent = localAudioFilePath ?: run {
            val localFile = MediaFileManager.saveBase64ToLocalFile(app, audioBase64, "voice_${clientMessageId}.m4a")
            localFile?.absolutePath ?: audioBase64
        }

        val localMessage = MessageEntity(
            id = clientMessageId,
            conversationId = conversationId,
            senderId = myId,
            content = safeLocalContent,
            messageType = "audio",
            iv = waveform,
            replyToId = null,
            status = "sending",
            isOutgoing = true,
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insertOrUpdate(localMessage)
            db.conversationDao().updateLastMessage(conversationId, "🎤 Mensagem de voz (${durationSeconds}s)", now)

            var payloadToSend = audioBase64
            try {
                val audioFile = if (MediaFileManager.isLocalFilePath(safeLocalContent)) {
                    java.io.File(safeLocalContent.removePrefix("file://"))
                } else null

                if (audioFile != null && audioFile.exists()) {
                    val requestFile = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
                    val folderBody = "audio".toRequestBody("text/plain".toMediaTypeOrNull())
                    val uploadRes = apiService.uploadMedia(body, folderBody)
                    if (uploadRes.isSuccessful && !uploadRes.body()?.url.isNullOrBlank()) {
                        payloadToSend = uploadRes.body()!!.url
                        Log.d("ChatViewModel", "Áudio enviado ao R2: $payloadToSend")
                        db.messageDao().updateContent(clientMessageId, payloadToSend)
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Upload áudio R2 falhou: ${e.message}")
            }

            val wsSent = connectionManager.getClient().sendChatMessage(
                conversationId = conversationId,
                content = payloadToSend,
                iv = waveform,
                messageType = "audio",
                replyToId = null,
                clientMessageId = clientMessageId,
                targetUserId = target
            )

            if (!wsSent) {
                try {
                    val res = apiService.sendMessage(
                        SendMessageRequest(
                            conversationId = conversationId,
                            content = payloadToSend,
                            iv = waveform,
                            clientMessageId = clientMessageId,
                            messageType = "audio",
                            targetUserId = target
                        )
                    )
                    if (res.isSuccessful) {
                        db.messageDao().updateStatus(clientMessageId, "sent")
                    } else if (res.code() in 400..499) {
                        db.messageDao().updateStatus(clientMessageId, "failed")
                    }
                } catch (e: Exception) {
                    // Erro de rede ou sem conexão: mantém 'sending' na caixa de saída (ícone de relógio)
                }
            }
        }
    }

    fun sendMediaMessage(
        conversationId: String,
        content: String,
        messageType: String,
        caption: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        targetUserId: String? = null,
        networkPayload: String? = null,
        isViewOnce: Boolean = false
    ) {
        if (content.isBlank()) return

        val myId = prefs.currentUserId ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        val clientMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val actualMessageType = if (isViewOnce) {
            if (messageType == "video") "view_once_video" else "view_once_image"
        } else messageType

        val metaIv = when (messageType) {
            "file" -> "${fileName ?: "documento"}|${fileSize ?: 0}|${caption ?: ""}"
            "video" -> "${fileName ?: "video"}|${fileSize ?: 0}|${caption ?: ""}${if (isViewOnce) "|view_once:true" else ""}"
            "image" -> if (isViewOnce) "${caption ?: ""}|view_once:true" else caption
            else -> caption
        }

        val previewText = when {
            isViewOnce && messageType == "video" -> "① Vídeo (Visualização única)"
            isViewOnce -> "① Foto (Visualização única)"
            messageType == "image" -> if (!caption.isNullOrBlank()) "📷 $caption" else "📷 Foto"
            messageType == "video" -> if (!caption.isNullOrBlank()) "🎥 $caption" else "🎥 Vídeo"
            messageType == "file" -> "📄 ${fileName ?: "Documento/Arquivo"}"
            else -> "📎 Arquivo"
        }

        // Determine local content (file path) for SQLite vs network payload
        var localContent = content
        var payloadToSend = networkPayload

        if (payloadToSend == null) {
            if (MediaFileManager.isLocalFilePath(content)) {
                // Content is local path; construct network payload from file
                try {
                    val file = java.io.File(content.removePrefix("file://"))
                    val bytes = file.readBytes()
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val mime = when (messageType) {
                        "image" -> "image/jpeg"
                        "video" -> "video/mp4"
                        "audio" -> "audio/mp3"
                        "sticker" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    payloadToSend = "data:$mime;base64,$b64"
                } catch (e: Exception) {
                    payloadToSend = content
                }
            } else {
                // Content is raw Base64; save to disk so SQLite never stores > 2MB!
                val ext = when (messageType) {
                    "image" -> "jpg"
                    "video" -> "mp4"
                    "audio" -> "m4a"
                    "sticker" -> "png"
                    else -> (fileName ?: "doc").substringAfterLast(".", "bin")
                }
                val localFile = MediaFileManager.saveBase64ToLocalFile(app, content, "sent_${clientMessageId}.$ext")
                if (localFile != null) {
                    localContent = localFile.absolutePath
                }
                payloadToSend = content
            }
        }

        val localMessage = MessageEntity(
            id = clientMessageId,
            conversationId = conversationId,
            senderId = myId,
            content = localContent,
            messageType = actualMessageType,
            iv = metaIv,
            replyToId = null,
            status = "sending",
            isOutgoing = true,
            isViewOnce = isViewOnce,
            viewOnceOpened = false,
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insertOrUpdate(localMessage)
            db.conversationDao().updateLastMessage(conversationId, previewText, now)

            var finalPayload = payloadToSend
            val fileToUpload = if (MediaFileManager.isLocalFilePath(localContent)) {
                java.io.File(localContent.removePrefix("file://"))
            } else null

            try {
                if (fileToUpload != null && fileToUpload.exists()) {
                    val mime = when (messageType) {
                        "image" -> "image/jpeg"
                        "video" -> "video/mp4"
                        "audio" -> "audio/mp4"
                        "sticker" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    val requestFile = fileToUpload.asRequestBody(mime.toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", fileToUpload.name, requestFile)
                    val folderBody = (if (messageType == "sticker") "stickers" else (messageType + "s")).toRequestBody("text/plain".toMediaTypeOrNull())
                    val uploadRes = apiService.uploadMedia(body, folderBody)
                    if (uploadRes.isSuccessful && !uploadRes.body()?.url.isNullOrBlank()) {
                        finalPayload = uploadRes.body()!!.url
                        Log.d("ChatViewModel", "Mídia enviada ao R2: $finalPayload")
                        db.messageDao().updateContent(clientMessageId, finalPayload)
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Upload R2 falhou: ${e.message}")
            }

            // Ensure finalPayload is never a raw local file path across the network
            if (finalPayload == null || MediaFileManager.isLocalFilePath(finalPayload)) {
                try {
                    val f = if (fileToUpload != null && fileToUpload.exists()) fileToUpload else java.io.File(localContent.removePrefix("file://"))
                    if (f.exists() && f.length() <= 8 * 1024 * 1024) {
                        val bytes = f.readBytes()
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val mime = when (messageType) {
                            "image" -> "image/jpeg"
                            "video" -> "video/mp4"
                            "audio" -> "audio/mp4"
                            "sticker" -> "image/png"
                            else -> "application/octet-stream"
                        }
                        finalPayload = "data:$mime;base64,$b64"
                    }
                } catch (e: Throwable) {
                    Log.e("ChatViewModel", "Erro ao converter mídia para Base64 fallback", e)
                }
            }

            val wsSent = connectionManager.getClient().sendChatMessage(
                conversationId = conversationId,
                content = finalPayload ?: "",
                iv = metaIv,
                messageType = actualMessageType,
                replyToId = null,
                clientMessageId = clientMessageId,
                targetUserId = target
            )

            if (!wsSent) {
                try {
                    val res = apiService.sendMessage(
                        SendMessageRequest(
                            conversationId = conversationId,
                            content = finalPayload ?: "",
                            iv = metaIv,
                            clientMessageId = clientMessageId,
                            messageType = actualMessageType,
                            targetUserId = target
                        )
                    )
                    if (res.isSuccessful) {
                        db.messageDao().updateStatus(clientMessageId, "sent")
                    } else if (res.code() in 400..499) {
                        db.messageDao().updateStatus(clientMessageId, "failed")
                    }
                } catch (e: Exception) {
                    // Erro de rede ou sem conexão: mantém 'sending' na caixa de saída (ícone de relógio)
                }
            }
        }
    }

    fun markViewOnceOpened(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = db.messageDao().getMessageById(messageId) ?: return@launch
            if (MediaFileManager.isLocalFilePath(msg.content)) {
                try {
                    val f = java.io.File(msg.content.removePrefix("file://"))
                    if (f.exists()) f.delete()
                } catch (e: Exception) {}
            }
            db.messageDao().markViewOnceOpened(messageId)
        }
    }

    fun pinMessage(conversationId: String, message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().unpinAllForConversation(conversationId)
            db.messageDao().setPinned(message.id, true)
            val snippet = when {
                message.isViewOnce -> "① Mídia de visualização única"
                message.messageType == "image" || message.messageType == "view_once_image" -> "📷 Foto"
                message.messageType == "video" || message.messageType == "view_once_video" -> "🎥 Vídeo"
                message.messageType == "video_note" -> "⭕ Mensagem de vídeo"
                message.messageType == "audio" -> "🎤 Mensagem de voz"
                message.messageType == "sticker" -> "📑 Figurinha"
                message.messageType == "file" -> "📄 Documento"
                else -> message.content.take(80)
            }
            db.conversationDao().updatePinnedMessage(conversationId, message.id, snippet)
        }
    }

    fun unpinMessage(conversationId: String, messageId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (messageId != null) {
                db.messageDao().setPinned(messageId, false)
            } else {
                db.messageDao().unpinAllForConversation(conversationId)
            }
            db.conversationDao().updatePinnedMessage(conversationId, null, null)
        }
    }

    fun sendVideoNote(
        conversationId: String,
        videoFile: java.io.File,
        targetUserId: String? = null
    ) {
        if (!videoFile.exists() || videoFile.length() == 0L) return

        val myId = prefs.currentUserId ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        val clientMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val localMessage = MessageEntity(
            id = clientMessageId,
            conversationId = conversationId,
            senderId = myId,
            content = videoFile.absolutePath,
            messageType = "video_note",
            iv = "video_note|${videoFile.length()}|",
            replyToId = null,
            status = "sending",
            isOutgoing = true,
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insertOrUpdate(localMessage)
            db.conversationDao().updateLastMessage(conversationId, "⭕ Mensagem de vídeo", now)

            val bytes = videoFile.readBytes()
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            var payloadToSend = "data:video/mp4;base64,$b64"

            try {
                val requestFile = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", "video_note_${clientMessageId}.mp4", requestFile)
                val folderBody = "videos".toRequestBody("text/plain".toMediaTypeOrNull())
                val uploadRes = apiService.uploadMedia(body, folderBody)
                if (uploadRes.isSuccessful && !uploadRes.body()?.url.isNullOrBlank()) {
                    payloadToSend = uploadRes.body()!!.url
                    db.messageDao().updateContent(clientMessageId, payloadToSend)
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Upload video_note R2 falhou: ${e.message}")
            }

            val wsSent = connectionManager.getClient().sendChatMessage(
                conversationId = conversationId,
                content = payloadToSend,
                iv = "video_note|${videoFile.length()}|",
                messageType = "video_note",
                replyToId = null,
                clientMessageId = clientMessageId,
                targetUserId = target
            )

            if (!wsSent) {
                try {
                    val res = apiService.sendMessage(
                        SendMessageRequest(
                            conversationId = conversationId,
                            content = payloadToSend,
                            iv = "video_note|${videoFile.length()}|",
                            clientMessageId = clientMessageId,
                            messageType = "video_note",
                            targetUserId = target
                        )
                    )
                    if (res.isSuccessful) {
                        db.messageDao().updateStatus(clientMessageId, "sent")
                    } else if (res.code() in 400..499) {
                        db.messageDao().updateStatus(clientMessageId, "failed")
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun sendTyping(isTyping: Boolean, targetUserId: String? = null) {
        val convId = _currentConversationId.value ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        connectionManager.getClient().sendTyping(convId, isTyping, target)
    }

    fun sendRecordingAudio(isRecording: Boolean, targetUserId: String? = null) {
        val convId = _currentConversationId.value ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        connectionManager.getClient().sendRecordingAudio(convId, isRecording, target)
    }

    fun markMessageRead(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (prefs.readReceiptsEnabled) {
                connectionManager.getClient().sendReceipt(messageId, "read")
                try { apiService.markMessageRead(messageId) } catch (e: Exception) {}
            }
        }
    }

    fun deleteMessage(messageId: String, conversationId: String, deleteForEveryone: Boolean, targetUserId: String? = null) {
        val target = targetUserId ?: _currentOtherUserId.value
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteForEveryone) {
                db.messageDao().softDeleteMessage(messageId)
                val lastMsg = db.messageDao().getMessagesForConversation(conversationId).lastOrNull()
                if (lastMsg != null && lastMsg.id == messageId) {
                    db.conversationDao().updateLastMessage(conversationId, "🚫 Esta mensagem foi apagada", lastMsg.createdAt)
                }
                connectionManager.getClient().sendDeleteMessage(messageId, conversationId, target)
                try {
                    apiService.deleteMessage(messageId)
                } catch (e: Exception) {}
            } else {
                db.messageDao().deletePermanently(messageId)
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val convId = _currentConversationId.value ?: return
        val myId = prefs.currentUserId ?: return
        val target = _currentOtherUserId.value
        viewModelScope.launch(Dispatchers.IO) {
            val msg = db.messageDao().getMessageById(messageId) ?: return@launch
            val current = com.livrechat.app.data.local.model.ReactionUtils.parseReactions(msg.reactions).toMutableList()
            val idx = current.indexOfFirst { it.userId == myId }
            if (idx >= 0) {
                if (current[idx].emoji == emoji) {
                    current.removeAt(idx)
                } else {
                    current[idx] = current[idx].copy(emoji = emoji)
                }
            } else {
                current.add(com.livrechat.app.data.local.model.MessageReaction(emoji, myId, prefs.currentDisplayName ?: prefs.currentUsername ?: "Você"))
            }
            val updatedJson = com.livrechat.app.data.local.model.ReactionUtils.serializeReactions(current)
            db.messageDao().updateReactions(messageId, updatedJson)
            connectionManager.getClient().sendReaction(messageId, convId, emoji, target)
        }
    }

    fun sendSticker(conversationId: String, stickerContent: String, targetUserId: String? = null) {
        sendMediaMessage(
            conversationId = conversationId,
            content = stickerContent,
            messageType = "sticker",
            caption = "Figurinha",
            targetUserId = targetUserId
        )
    }

    fun sendStickerMessage(conversationId: String, stickerContent: String, targetUserId: String? = null) {
        sendSticker(conversationId, stickerContent, targetUserId)
    }

    fun sendStatusReaction(statusId: String, authorId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            connectionManager.getClient().sendStatusReaction(statusId, authorId, emoji)
        }
    }

    fun sendStatusView(statusId: String, authorId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            connectionManager.getClient().sendStatusView(statusId, authorId)
        }
    }

    fun clearConversation(conversationId: String, targetUserId: String? = null) {
        val target = targetUserId ?: _currentOtherUserId.value
        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().clearConversationMessages(conversationId)
            db.conversationDao().updateLastMessage(conversationId, "", System.currentTimeMillis())
            connectionManager.getClient().sendClearChat(conversationId, target)
            try {
                apiService.clearConversation(conversationId)
            } catch (e: Exception) {}
        }
    }

    private var contactTypingJob: Job? = null

    // WebSocketEventsListener implementations
    override fun onConnected() {
        syncConversations()
        val currentConv = _currentConversationId.value
        if (!currentConv.isNullOrEmpty()) {
            syncMessagesForConversation(currentConv)
        }
    }
    override fun onDisconnected() {}

    override fun onMessageReceived(message: MessageEntity) {
        // Handled centrally by ConnectionManager for Room & Notifications
    }

    override fun onMessageAck(messageId: String, clientMessageId: String?, status: String) {
        // Handled centrally by ConnectionManager
    }

    override fun onMessageReceipt(messageId: String, status: String, userId: String) {
        // Handled centrally by ConnectionManager
    }

    private var contactRecordingAudioJob: Job? = null

    override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean, displayName: String?) {
        if (_currentConversationId.value == conversationId) {
            contactTypingJob?.cancel()
            _isContactTyping.value = isTyping
            if (isTyping) {
                contactTypingJob = viewModelScope.launch {
                    delay(3500)
                    _isContactTyping.value = false
                }
            }
        }
    }

    override fun onRecordingAudio(conversationId: String, userId: String, username: String, isRecording: Boolean, displayName: String?) {
        if (_currentConversationId.value == conversationId) {
            contactRecordingAudioJob?.cancel()
            _isContactRecordingAudio.value = isRecording
            if (isRecording) {
                contactRecordingAudioJob = viewModelScope.launch {
                    delay(6000)
                    _isContactRecordingAudio.value = false
                }
            }
        }
    }

    override fun onIncomingCall(callerId: String, callerUsername: String, callerDisplayName: String, callerAvatarUrl: String?, sdp: String) {}
    override fun onCallAnswered(calleeId: String, sdp: String) {}
    override fun onIceCandidateReceived(fromUserId: String, candidate: JsonObject) {}
    override fun onCallRinging(fromUserId: String) {}
    override fun onCallRejected(fromUserId: String, reason: String) {}
    override fun onCallEnded(fromUserId: String) {}

    override fun onChatError(code: String, message: String, clientMessageId: String?) {
        _chatErrorMessage.value = message
    }

    data class PartySession(
        val videoId: String = "",
        val videoUrl: String = "",
        val videoTitle: String = "LivreParty",
        val isPlaying: Boolean = false,
        val currentTime: Long = 0L,
        val updatedBy: String = ""
    )

    private val _activePartySession = MutableStateFlow<PartySession?>(null)
    val activePartySession: StateFlow<PartySession?> = _activePartySession.asStateFlow()

    fun startPartySession(videoId: String, videoUrl: String, title: String) {
        val convId = _currentConversationId.value ?: return
        _activePartySession.value = PartySession(
            videoId = videoId,
            videoUrl = videoUrl,
            videoTitle = title,
            isPlaying = true,
            currentTime = 0L,
            updatedBy = "Você"
        )
        connectionManager.getClient().sendPartySync(
            conversationId = convId,
            action = "PLAY",
            currentTime = 0L,
            videoId = videoId,
            videoUrl = videoUrl,
            videoTitle = title
        )
    }

    fun syncPartyAction(action: String, currentTime: Long) {
        val convId = _currentConversationId.value ?: return
        val current = _activePartySession.value ?: return
        _activePartySession.value = current.copy(
            isPlaying = action == "PLAY",
            currentTime = currentTime,
            updatedBy = "Você"
        )
        connectionManager.getClient().sendPartySync(
            conversationId = convId,
            action = action,
            currentTime = currentTime,
            videoId = current.videoId,
            videoUrl = current.videoUrl,
            videoTitle = current.videoTitle
        )
    }

    fun closePartySession() {
        val convId = _currentConversationId.value
        val current = _activePartySession.value
        if (convId != null && current != null) {
            connectionManager.getClient().sendPartySync(
                conversationId = convId,
                action = "CLOSE",
                currentTime = 0L,
                videoId = current.videoId,
                videoUrl = current.videoUrl,
                videoTitle = current.videoTitle
            )
        }
        _activePartySession.value = null
    }

    override fun onPartySync(
        conversationId: String,
        action: String,
        currentTime: Long,
        videoId: String,
        videoUrl: String,
        videoTitle: String,
        senderName: String
    ) {
        if (_currentConversationId.value == conversationId) {
            if (action == "CLOSE") {
                _activePartySession.value = null
            } else {
                _activePartySession.value = PartySession(
                    videoId = videoId,
                    videoUrl = videoUrl,
                    videoTitle = videoTitle,
                    isPlaying = action == "PLAY",
                    currentTime = currentTime,
                    updatedBy = senderName
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ChatSoundManager.stopAll()
        connectionManager.setActiveConversation(null)
        connectionManager.unregisterListener(this)
    }
}
