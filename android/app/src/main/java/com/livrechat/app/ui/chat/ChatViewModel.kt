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

    private val connectionManager = app.connectionManager
    val isConnected: StateFlow<Boolean> = connectionManager.isConnected
    val typingStatus: StateFlow<Map<String, Boolean>> = connectionManager.typingStatus

    init {
        connectionManager.registerListener(this)
        syncConversations()
    }

    fun selectConversation(conversationId: String, otherUserId: String? = null) {
        _currentConversationId.value = conversationId
        _currentOtherUserId.value = otherUserId
        connectionManager.setActiveConversation(conversationId)
        if (otherUserId != null) {
            connectionManager.queryUserStatus(otherUserId)
        }
        _messagesFlow.value = db.messageDao().getMessagesForConversationFlow(conversationId)

        viewModelScope.launch(Dispatchers.IO) {
            db.conversationDao().clearUnreadCount(conversationId)
            try {
                apiService.markConversationRead(conversationId)
            } catch (e: Exception) {}
            // Marcar todas as mensagens recebidas como lidas e notificar remetente com 2 ticks azuis
            val unreadList = db.messageDao().getUnreadIncomingMessages(conversationId)
            db.messageDao().markAllIncomingAsRead(conversationId)
            for (msg in unreadList) {
                connectionManager.getClient().sendReceipt(msg.id, "read")
                try { apiService.markMessageRead(msg.id) } catch (e: Exception) {}
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
                            groupAvatar = dto.avatarUrl
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
                            when {
                                existing?.status == "read" -> "read"
                                dto.status == "read" -> "read"
                                dto.receipts?.any { it.status == "read" } == true -> "read"
                                existing?.status == "delivered" -> "delivered"
                                dto.status == "delivered" -> "delivered"
                                dto.receipts?.any { it.status == "delivered" } == true -> "delivered"
                                else -> existing?.status ?: "sent"
                            }
                        } else {
                            "read"
                        }

                        // Prevent SQLiteBlobTooBigException: never store raw Base64 media directly into SQLite
                        var safeContent = dto.content
                        if (existing != null && MediaFileManager.isLocalFilePath(existing.content)) {
                            safeContent = existing.content
                        } else if (dto.messageType in listOf("image", "video", "file", "audio") || dto.content.startsWith("data:") || dto.content.length > 50000) {
                            if (!MediaFileManager.isLocalFilePath(dto.content)) {
                                val ext = when (dto.messageType) {
                                    "image" -> "jpg"
                                    "video" -> "mp4"
                                    "audio" -> "m4a"
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

                        MessageEntity(
                            id = dto.id,
                            conversationId = dto.conversationId,
                            senderId = dto.senderId,
                            content = safeContent,
                            iv = dto.iv,
                            messageType = dto.messageType,
                            replyToId = dto.replyToId,
                            status = calculatedStatus,
                            isOutgoing = dto.senderId == myId,
                            isEdited = dto.isEdited,
                            isDeleted = dto.isDeleted,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                    db.messageDao().insertAll(entities)

                    // For every incoming message that wasn't previously marked read, send read receipts!
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
            } catch (e: Exception) {
                // Offline fallback
            }
        }
    }

    fun sendMessage(conversationId: String, text: String, replyToId: String? = null, targetUserId: String? = null) {
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
                            targetUserId = target
                        )
                    )
                    if (res.isSuccessful) {
                        db.messageDao().updateStatus(clientMessageId, "sent")
                    } else {
                        db.messageDao().updateStatus(clientMessageId, "failed")
                    }
                } catch (e: Exception) {
                    db.messageDao().updateStatus(clientMessageId, "failed")
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

            val wsSent = connectionManager.getClient().sendChatMessage(
                conversationId = conversationId,
                content = audioBase64,
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
                            content = audioBase64,
                            iv = waveform,
                            clientMessageId = clientMessageId,
                            messageType = "audio",
                            targetUserId = target
                        )
                    )
                    if (res.isSuccessful) {
                        db.messageDao().updateStatus(clientMessageId, "sent")
                    } else {
                        db.messageDao().updateStatus(clientMessageId, "failed")
                    }
                } catch (e: Exception) {
                    db.messageDao().updateStatus(clientMessageId, "failed")
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
        networkPayload: String? = null
    ) {
        if (content.isBlank()) return

        val myId = prefs.currentUserId ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        val clientMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val metaIv = when (messageType) {
            "file" -> "${fileName ?: "documento"}|${fileSize ?: 0}|${caption ?: ""}"
            "video" -> "${fileName ?: "video"}|${fileSize ?: 0}|${caption ?: ""}"
            "image" -> caption
            else -> caption
        }

        val previewText = when (messageType) {
            "image" -> if (!caption.isNullOrBlank()) "📷 $caption" else "📷 Foto"
            "video" -> if (!caption.isNullOrBlank()) "🎥 $caption" else "🎥 Vídeo"
            "file" -> "📄 ${fileName ?: "Documento/Arquivo"}"
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
            messageType = messageType,
            iv = metaIv,
            replyToId = null,
            status = "sending",
            isOutgoing = true,
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insertOrUpdate(localMessage)
            db.conversationDao().updateLastMessage(conversationId, previewText, now)

            val wsSent = connectionManager.getClient().sendChatMessage(
                conversationId = conversationId,
                content = payloadToSend ?: "",
                iv = metaIv,
                messageType = messageType,
                replyToId = null,
                clientMessageId = clientMessageId,
                targetUserId = target
            )

            if (!wsSent) {
                try {
                    val res = apiService.sendMessage(
                        SendMessageRequest(
                            conversationId = conversationId,
                            content = payloadToSend ?: "",
                            iv = metaIv,
                            clientMessageId = clientMessageId,
                            messageType = messageType,
                            targetUserId = target
                        )
                    )
                    if (res.isSuccessful) {
                        db.messageDao().updateStatus(clientMessageId, "sent")
                    } else {
                        db.messageDao().updateStatus(clientMessageId, "failed")
                    }
                } catch (e: Exception) {
                    db.messageDao().updateStatus(clientMessageId, "failed")
                }
            }
        }
    }

    fun sendTyping(isTyping: Boolean, targetUserId: String? = null) {
        val convId = _currentConversationId.value ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        connectionManager.getClient().sendTyping(convId, isTyping, target)
    }

    fun markMessageRead(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            connectionManager.getClient().sendReceipt(messageId, "read")
            apiService.markMessageRead(messageId)
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

    fun sendStatusReaction(statusId: String, authorId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            connectionManager.getClient().sendStatusReaction(statusId, authorId, emoji)
            val myId = prefs.currentUserId
            if (myId != null && authorId.isNotBlank() && myId != authorId) {
                val convId = if (myId < authorId) "conv_${myId}_${authorId}" else "conv_${authorId}_${myId}"
                sendMessage(
                    conversationId = convId,
                    text = "Reagiu ao seu Flash: $emoji",
                    targetUserId = authorId
                )
            }
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

    override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean) {
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

    override fun onIncomingCall(callerId: String, callerUsername: String, callerDisplayName: String, callerAvatarUrl: String?, sdp: String) {}
    override fun onCallAnswered(calleeId: String, sdp: String) {}
    override fun onIceCandidateReceived(fromUserId: String, candidate: JsonObject) {}
    override fun onCallRinging(fromUserId: String) {}
    override fun onCallRejected(fromUserId: String, reason: String) {}
    override fun onCallEnded(fromUserId: String) {}

    override fun onCleared() {
        super.onCleared()
        ChatSoundManager.stopAll()
        connectionManager.setActiveConversation(null)
        connectionManager.unregisterListener(this)
    }
}
