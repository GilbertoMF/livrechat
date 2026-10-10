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

    private val _messagesFlow = MutableStateFlow<Flow<List<MessageEntity>>>(emptyFlow())
    val messagesFlow: StateFlow<Flow<List<MessageEntity>>> = _messagesFlow.asStateFlow()

    private val _isContactTyping = MutableStateFlow(false)
    val isContactTyping: StateFlow<Boolean> = _isContactTyping.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var webSocketClient: WebSocketClient? = null

    init {
        initWebSocket()
        syncConversations()
    }

    private fun initWebSocket() {
        if (prefs.isLoggedIn()) {
            webSocketClient = WebSocketClient(prefs, this)
            webSocketClient?.connect()
        }
    }

    fun selectConversation(conversationId: String) {
        _currentConversationId.value = conversationId
        _messagesFlow.value = db.messageDao().getMessagesForConversationFlow(conversationId)

        viewModelScope.launch(Dispatchers.IO) {
            db.conversationDao().clearUnreadCount(conversationId)
            syncMessagesForConversation(conversationId)
        }
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
                    val entities = res.body()!!.map { dto ->
                        MessageEntity(
                            id = dto.id,
                            conversationId = dto.conversationId,
                            senderId = dto.senderId,
                            content = dto.content,
                            iv = dto.iv,
                            messageType = dto.messageType,
                            replyToId = dto.replyToId,
                            status = if (dto.senderId == myId) "sent" else "read",
                            isOutgoing = dto.senderId == myId,
                            isEdited = dto.isEdited,
                            isDeleted = dto.isDeleted,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                    db.messageDao().insertAll(entities)
                }
            } catch (e: Exception) {
                // Offline fallback
            }
        }
    }

    fun sendMessage(conversationId: String, text: String, replyToId: String? = null) {
        if (text.isBlank()) return

        val myId = prefs.currentUserId ?: return
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
            val wsSent = webSocketClient?.sendChatMessage(
                conversationId = conversationId,
                content = text,
                iv = null,
                messageType = "text",
                replyToId = replyToId,
                clientMessageId = clientMessageId
            ) ?: false

            if (!wsSent) {
                // Fallback to REST API
                try {
                    val res = apiService.sendMessage(
                        SendMessageRequest(
                            conversationId = conversationId,
                            content = text,
                            clientMessageId = clientMessageId,
                            replyToId = replyToId
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

    fun sendTyping(isTyping: Boolean) {
        val convId = _currentConversationId.value ?: return
        webSocketClient?.sendTyping(convId, isTyping)
    }

    fun markMessageRead(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            webSocketClient?.sendReceipt(messageId, "read")
            apiService.markMessageRead(messageId)
        }
    }

    // WebSocketEventsListener implementations
    override fun onConnected() {
        _isConnected.value = true
    }

    override fun onDisconnected() {
        _isConnected.value = false
    }

    override fun onMessageReceived(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insertOrUpdate(message)
            db.conversationDao().updateLastMessage(message.conversationId, message.content, message.createdAt)
            // Auto mark read if conversation is open
            if (_currentConversationId.value == message.conversationId) {
                markMessageRead(message.id)
            }
        }
    }

    override fun onMessageAck(messageId: String, clientMessageId: String?, status: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetId = clientMessageId ?: messageId
            db.messageDao().updateStatus(targetId, status)
        }
    }

    override fun onMessageReceipt(messageId: String, status: String, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().updateStatus(messageId, status)
        }
    }

    override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean) {
        if (_currentConversationId.value == conversationId) {
            _isContactTyping.value = isTyping
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
        webSocketClient?.disconnect()
    }
}
