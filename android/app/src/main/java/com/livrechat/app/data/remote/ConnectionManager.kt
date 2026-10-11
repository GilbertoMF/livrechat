package com.livrechat.app.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.notification.LivreChatNotificationManager
import com.livrechat.app.util.ChatSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionManager(
    private val context: Context,
    private val webSocketClient: WebSocketClient
) : WebSocketEventsListener {

    private val TAG = "ConnectionManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableSetOf<WebSocketEventsListener>()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    // Map of conversationId -> isTyping
    private val _typingStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingStatus: StateFlow<Map<String, Boolean>> = _typingStatus.asStateFlow()

    init {
        // WebSocketClient constructor will be passed this listener
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun registerListener(listener: WebSocketEventsListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: WebSocketEventsListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun connect() {
        webSocketClient.connect()
    }

    fun disconnect() {
        webSocketClient.disconnect()
    }

    fun getClient(): WebSocketClient = webSocketClient

    override fun onConnected() {
        _isConnected.value = true
        synchronized(listeners) {
            listeners.forEach { it.onConnected() }
        }
    }

    override fun onDisconnected() {
        _isConnected.value = false
        synchronized(listeners) {
            listeners.forEach { it.onDisconnected() }
        }
    }

    override fun onMessageReceived(message: MessageEntity) {
        scope.launch {
            val app = LivreChatApp.instance
            val db = app.database

            // 1. De-duplication check: ignore if already received and stored
            val existingMsg = db.messageDao().getMessageById(message.id)
            if (existingMsg != null) {
                Log.d(TAG, "Mensagem ${message.id} já recebida anteriormente. Ignorando duplicata.")
                return@launch
            }

            val isCurrentOpen = _activeConversationId.value == message.conversationId

            val actualType = when {
                message.messageType == "audio" || message.content.startsWith("data:audio") || message.content.contains("base64,") -> "audio"
                message.messageType == "image" || message.content.startsWith("data:image") -> "image"
                else -> message.messageType
            }

            val previewContent = when (actualType) {
                "audio" -> "🎤 Mensagem de voz"
                "image" -> "📷 Foto"
                else -> message.content
            }

            var conv = db.conversationDao().getConversationById(message.conversationId)
            if (conv == null) {
                val newConv = ConversationEntity(
                    id = message.conversationId,
                    otherUserId = message.senderId,
                    otherUsername = "Contato",
                    otherDisplayName = "Contato",
                    otherAvatarUrl = null,
                    otherPublicKey = null,
                    lastMessageContent = previewContent,
                    lastMessageTimestamp = message.createdAt,
                    unreadCount = if (isCurrentOpen) 0 else 1
                )
                db.conversationDao().insertOrUpdate(newConv)
                conv = newConv

                launch {
                    try {
                        val userRes = app.apiClient.apiService.getUserProfile(message.senderId)
                        if (userRes.isSuccessful && userRes.body() != null) {
                            val userDto = userRes.body()!!
                            db.conversationDao().insertOrUpdate(
                                newConv.copy(
                                    otherUsername = userDto.username,
                                    otherDisplayName = if (userDto.displayName.isNotBlank()) userDto.displayName else userDto.username,
                                    otherAvatarUrl = userDto.avatarUrl,
                                    otherPublicKey = userDto.publicKey
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Falha ao obter perfil do remetente", e)
                    }
                }
            }

            if (isCurrentOpen) {
                // Mensagem lida imediatamente
                val readMsg = message.copy(status = "read")
                db.messageDao().insertOrUpdate(readMsg)
                db.conversationDao().clearUnreadCount(message.conversationId)
                db.conversationDao().updateLastMessage(message.conversationId, previewContent, message.createdAt)
                webSocketClient.sendReceipt(message.id, "read")
                try { app.apiClient.apiService.markMessageRead(message.id) } catch (e: Exception) {}
            } else {
                // Mensagem entregue
                val deliveredMsg = message.copy(status = "delivered")
                db.messageDao().insertOrUpdate(deliveredMsg)
                db.conversationDao().recalculateUnreadCount(message.conversationId)
                db.conversationDao().updateLastMessage(message.conversationId, previewContent, message.createdAt)
                webSocketClient.sendReceipt(message.id, "delivered")

                // Obter nome para notificação
                val senderTitle = conv.otherDisplayName.ifBlank { conv.otherUsername.ifBlank { "LivreChat" } }

                LivreChatNotificationManager.showMessageNotification(
                    context = context,
                    conversationId = message.conversationId,
                    senderName = senderTitle,
                    content = previewContent,
                    messageId = message.id,
                    messageType = actualType
                )
            }

            synchronized(listeners) {
                listeners.forEach { it.onMessageReceived(message) }
            }
        }
    }

    override fun onMessageAck(messageId: String, clientMessageId: String?, status: String) {
        scope.launch {
            val db = LivreChatApp.instance.database
            val targetId = clientMessageId ?: messageId
            db.messageDao().updateStatus(targetId, status)

            synchronized(listeners) {
                listeners.forEach { it.onMessageAck(messageId, clientMessageId, status) }
            }
        }
    }

    override fun onMessageReceipt(messageId: String, status: String, userId: String) {
        scope.launch {
            val db = LivreChatApp.instance.database
            db.messageDao().updateStatus(messageId, status)

            synchronized(listeners) {
                listeners.forEach { it.onMessageReceipt(messageId, status, userId) }
            }
        }
    }

    private val typingTimeoutJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean) {
        typingTimeoutJobs[conversationId]?.cancel()

        val current = _typingStatus.value.toMutableMap()
        current[conversationId] = isTyping
        _typingStatus.value = current

        if (isTyping) {
            typingTimeoutJobs[conversationId] = scope.launch {
                kotlinx.coroutines.delay(3500)
                val updated = _typingStatus.value.toMutableMap()
                updated[conversationId] = false
                _typingStatus.value = updated
                synchronized(listeners) {
                    listeners.forEach { it.onTyping(conversationId, userId, username, false) }
                }
            }
        }

        synchronized(listeners) {
            listeners.forEach { it.onTyping(conversationId, userId, username, isTyping) }
        }
    }

    override fun onIncomingCall(
        callerId: String,
        callerUsername: String,
        callerDisplayName: String,
        callerAvatarUrl: String?,
        sdp: String
    ) {
        LivreChatNotificationManager.showIncomingCallNotification(
            context = context,
            callerId = callerId,
            callerDisplayName = callerDisplayName,
            callerUsername = callerUsername
        )

        synchronized(listeners) {
            listeners.forEach { it.onIncomingCall(callerId, callerUsername, callerDisplayName, callerAvatarUrl, sdp) }
        }
    }

    override fun onCallAnswered(calleeId: String, sdp: String) {
        LivreChatNotificationManager.cancelCallNotification(context)
        synchronized(listeners) {
            listeners.forEach { it.onCallAnswered(calleeId, sdp) }
        }
    }

    override fun onIceCandidateReceived(fromUserId: String, candidate: JsonObject) {
        synchronized(listeners) {
            listeners.forEach { it.onIceCandidateReceived(fromUserId, candidate) }
        }
    }

    override fun onCallRinging(fromUserId: String) {
        synchronized(listeners) {
            listeners.forEach { it.onCallRinging(fromUserId) }
        }
    }

    override fun onCallRejected(fromUserId: String, reason: String) {
        LivreChatNotificationManager.cancelCallNotification(context)
        synchronized(listeners) {
            listeners.forEach { it.onCallRejected(fromUserId, reason) }
        }
    }

    override fun onCallEnded(fromUserId: String) {
        LivreChatNotificationManager.cancelCallNotification(context)
        synchronized(listeners) {
            listeners.forEach { it.onCallEnded(fromUserId) }
        }
    }

    override fun onMessageDeleted(messageId: String, conversationId: String) {
        scope.launch {
            val db = LivreChatApp.instance.database
            db.messageDao().softDeleteMessage(messageId)
            synchronized(listeners) {
                listeners.forEach { it.onMessageDeleted(messageId, conversationId) }
            }
        }
    }

    override fun onChatCleared(conversationId: String) {
        scope.launch {
            val db = LivreChatApp.instance.database
            db.messageDao().clearConversationMessages(conversationId)
            db.conversationDao().updateLastMessage(conversationId, "", System.currentTimeMillis())
            synchronized(listeners) {
                listeners.forEach { it.onChatCleared(conversationId) }
            }
        }
    }
}
