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
        _messagesFlow.value = db.messageDao().getMessagesForConversationFlow(conversationId)

        viewModelScope.launch(Dispatchers.IO) {
            db.conversationDao().clearUnreadCount(conversationId)
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
                        ConversationEntity(
                            id = dto.conversationId,
                            otherUserId = dto.otherUser.id,
                            otherUsername = dto.otherUser.username,
                            otherDisplayName = dto.otherUser.displayName,
                            otherAvatarUrl = dto.otherUser.avatarUrl,
                            otherPublicKey = dto.otherUser.publicKey,
                            lastMessageContent = dto.lastMessage?.content,
                            lastMessageTimestamp = System.currentTimeMillis(),
                            unreadCount = dto.unreadCount,
                            isPinned = dto.isPinned,
                            isArchived = dto.isArchived,
                            isMuted = dto.isMuted
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

                        MessageEntity(
                            id = dto.id,
                            conversationId = dto.conversationId,
                            senderId = dto.senderId,
                            content = dto.content,
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

    fun sendAudioMessage(conversationId: String, audioBase64: String, durationSeconds: Int, targetUserId: String? = null) {
        if (audioBase64.isBlank()) return

        val myId = prefs.currentUserId ?: return
        val target = targetUserId ?: _currentOtherUserId.value
        val clientMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val localMessage = MessageEntity(
            id = clientMessageId,
            conversationId = conversationId,
            senderId = myId,
            content = audioBase64,
            messageType = "audio",
            replyToId = null,
            status = "sending",
            isOutgoing = true,
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insertOrUpdate(localMessage)
            db.conversationDao().updateLastMessage(conversationId, "🎤 Áudio (${durationSeconds}s)", now)

            val wsSent = connectionManager.getClient().sendChatMessage(
                conversationId = conversationId,
                content = audioBase64,
                iv = null,
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
        connectionManager.setActiveConversation(null)
        connectionManager.unregisterListener(this)
    }
}
