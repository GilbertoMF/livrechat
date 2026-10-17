package com.livrechat.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.gson.JsonObject
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.notification.LivreChatNotificationManager
import com.livrechat.app.util.ChatSoundManager
import com.livrechat.app.util.MediaFileManager
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

    // Map of conversationId -> displayName/username of who is typing
    private val _typingUserByConv = MutableStateFlow<Map<String, String?>>(emptyMap())
    val typingUserByConv: StateFlow<Map<String, String?>> = _typingUserByConv.asStateFlow()

    // Map of conversationId -> isRecordingAudio
    private val _recordingAudioStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val recordingAudioStatus: StateFlow<Map<String, Boolean>> = _recordingAudioStatus.asStateFlow()

    // Map of conversationId -> displayName/username of who is recording audio
    private val _recordingUserByConv = MutableStateFlow<Map<String, String?>>(emptyMap())
    val recordingUserByConv: StateFlow<Map<String, String?>> = _recordingUserByConv.asStateFlow()

    private val preferencesManager by lazy { com.livrechat.app.data.local.PreferencesManager(context) }

    // Map of userId -> isOnline
    private val _onlineUsers = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val onlineUsers: StateFlow<Map<String, Boolean>> = _onlineUsers.asStateFlow()

    // Map of userId -> lastSeenTimestamp
    private val _lastSeenUsers = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeenUsers: StateFlow<Map<String, Long>> = _lastSeenUsers.asStateFlow()

    fun isUserOnline(userId: String): Boolean = _onlineUsers.value[userId] ?: false
    fun getLastSeen(userId: String): Long? = _lastSeenUsers.value[userId] ?: preferencesManager.getContactLastSeen(userId)
    fun queryUserStatus(userId: String) = webSocketClient.queryUserStatus(userId)

    init {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val prefs = LivreChatApp.instance.preferencesManager
                    if (prefs.isLoggedIn() && LivreChatApp.instance.isAppInForeground) {
                        Log.d(TAG, "Rede de internet disponível e app em primeiro plano. Conectando WebSocket e descarregando outbox...")
                        connect()
                        flushOutbox()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Conexão de internet perdida.")
                    _isConnected.value = false
                    try {
                        webSocketClient.disconnect()
                    } catch (e: Exception) {}
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar NetworkCallback", e)
        }
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

    fun flushOutbox() {
        val prefs = LivreChatApp.instance.preferencesManager
        if (!prefs.isLoggedIn()) return
        scope.launch {
            try {
                val db = LivreChatApp.instance.database
                val pending = db.messageDao().getPendingOutboxMessages()
                if (pending.isEmpty()) return@launch
                Log.d(TAG, "Reenviando ${pending.size} mensagens pendentes na caixa de saída...")

                for (msg in pending) {
                    var sent = false
                    if (_isConnected.value) {
                        sent = webSocketClient.sendChatMessage(
                            conversationId = msg.conversationId,
                            content = msg.content,
                            iv = msg.iv,
                            messageType = msg.messageType,
                            replyToId = msg.replyToId,
                            clientMessageId = msg.id,
                            targetUserId = null
                        )
                    }
                    if (!sent) {
                        try {
                            val res = LivreChatApp.instance.apiClient.apiService.sendMessage(
                                com.livrechat.app.data.remote.model.SendMessageRequest(
                                    conversationId = msg.conversationId,
                                    content = msg.content,
                                    iv = msg.iv,
                                    messageType = msg.messageType,
                                    replyToId = msg.replyToId,
                                    clientMessageId = msg.id
                                )
                            )
                            if (res.isSuccessful) {
                                db.messageDao().updateStatus(msg.id, "sent")
                            }
                        } catch (e: Exception) {
                            // Erro de rede: permanece como 'sending'
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao processar caixa de saída: ${e.message}")
            }
        }
    }

    override fun onConnected() {
        _isConnected.value = true
        flushOutbox()
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
                message.messageType == "sticker" -> "sticker"
                message.messageType == "image" || message.content.startsWith("data:image") -> "image"
                message.messageType == "video" || message.content.startsWith("data:video") -> "video"
                message.messageType == "file" || message.content.startsWith("data:application") -> "file"
                message.messageType == "audio" || message.content.startsWith("data:audio") || message.content.contains("base64,") -> "audio"
                else -> message.messageType
            }

            // Save incoming media to local storage so SQLite never stores heavy base64 blobs
            var processedMessage = message.copy(messageType = actualType)
            if (actualType in listOf("image", "video", "file", "audio", "sticker") || message.content.startsWith("data:") || message.content.length > 50000) {
                if (!MediaFileManager.isLocalFilePath(message.content)) {
                    val ext = when (actualType) {
                        "image" -> "jpg"
                        "video" -> "mp4"
                        "audio" -> "m4a"
                        "sticker" -> "png"
                        else -> {
                            val fn = message.iv?.split("|")?.firstOrNull() ?: ""
                            fn.substringAfterLast(".", "bin")
                        }
                    }
                    val localFile = MediaFileManager.saveBase64ToLocalFile(context, message.content, "received_${message.id}.$ext")
                    if (localFile != null) {
                        processedMessage = processedMessage.copy(content = localFile.absolutePath)
                    } else if (message.content.length > 250000) {
                        processedMessage = processedMessage.copy(content = "[Arquivo transferido]")
                    }
                }
            }

            val previewContent = when (actualType) {
                "audio" -> "🎤 Mensagem de voz"
                "image" -> "📷 Foto"
                "video" -> "🎥 Vídeo"
                "file" -> "📄 Documento/Arquivo"
                "sticker" -> "📑 Figurinha"
                else -> processedMessage.content
            }

            var conv = db.conversationDao().getConversationById(processedMessage.conversationId)
            if (conv == null) {
                val newConv = ConversationEntity(
                    id = processedMessage.conversationId,
                    otherUserId = processedMessage.senderId,
                    otherUsername = "Contato",
                    otherDisplayName = "Contato",
                    otherAvatarUrl = null,
                    otherPublicKey = null,
                    lastMessageContent = previewContent,
                    lastMessageTimestamp = processedMessage.createdAt,
                    unreadCount = if (isCurrentOpen) 0 else 1
                )
                db.conversationDao().insertOrUpdate(newConv)
                conv = newConv

                launch {
                    try {
                        val userRes = app.apiClient.apiService.getUserProfile(processedMessage.senderId)
                        if (userRes.isSuccessful && userRes.body() != null) {
                            val userDto = userRes.body()!!
                            val localCustom = app.preferencesManager.getContactCustomName(processedMessage.senderId)
                            val savedName = db.contactDao().getContactByUserId(processedMessage.senderId)?.displayName?.takeIf { it.isNotBlank() }
                            val effectiveDisplayName = localCustom ?: savedName ?: (userDto.displayName?.takeIf { it.isNotBlank() && it != processedMessage.senderId && !it.startsWith("ID ") }) ?: userDto.username
                            db.conversationDao().insertOrUpdate(
                                newConv.copy(
                                    otherUsername = userDto.username,
                                    otherDisplayName = effectiveDisplayName,
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

            val shouldSendReadReceipt = app.preferencesManager.readReceiptsEnabled
            if (isCurrentOpen) {
                // Mensagem lida imediatamente
                val readMsg = processedMessage.copy(status = if (shouldSendReadReceipt) "read" else "delivered")
                db.messageDao().insertOrUpdate(readMsg)
                db.conversationDao().clearUnreadCount(processedMessage.conversationId)
                db.conversationDao().updateLastMessage(processedMessage.conversationId, previewContent, processedMessage.createdAt)
                if (shouldSendReadReceipt) {
                    webSocketClient.sendReceipt(processedMessage.id, "read")
                    try { app.apiClient.apiService.markMessageRead(processedMessage.id) } catch (e: Exception) {}
                } else {
                    webSocketClient.sendReceipt(processedMessage.id, "delivered")
                }
            } else {
                // Mensagem entregue
                val deliveredMsg = processedMessage.copy(status = "delivered")
                db.messageDao().insertOrUpdate(deliveredMsg)
                db.conversationDao().recalculateUnreadCount(processedMessage.conversationId)
                db.conversationDao().updateLastMessage(processedMessage.conversationId, previewContent, processedMessage.createdAt)
                webSocketClient.sendReceipt(processedMessage.id, "delivered")

                // Obter nome para notificação usando nome salvo localmente
                val localCustom = app.preferencesManager.getContactCustomName(processedMessage.senderId)
                val savedName = db.contactDao().getContactByUserId(processedMessage.senderId)?.displayName?.takeIf { it.isNotBlank() }
                val convTitle = conv.otherDisplayName.takeIf { it.isNotBlank() && it != processedMessage.senderId && !it.startsWith("ID ") }
                val senderTitle = localCustom ?: savedName ?: convTitle ?: conv.otherDisplayName.ifBlank { conv.otherUsername.ifBlank { "LivreChat" } }

                LivreChatNotificationManager.showMessageNotification(
                    context = context,
                    conversationId = processedMessage.conversationId,
                    senderName = senderTitle,
                    content = previewContent,
                    messageId = processedMessage.id,
                    messageType = actualType
                )
            }

            // Clear typing state immediately when message arrives from sender
            typingTimeoutJobs[processedMessage.conversationId]?.cancel()
            val updatedTyping = _typingStatus.value.toMutableMap()
            if (updatedTyping[processedMessage.conversationId] == true) {
                updatedTyping[processedMessage.conversationId] = false
                _typingStatus.value = updatedTyping
                synchronized(listeners) {
                    listeners.forEach { it.onTyping(processedMessage.conversationId, processedMessage.senderId, "", false) }
                }
            }

            // Clear recording audio state immediately when message arrives from sender
            recordingAudioTimeoutJobs[processedMessage.conversationId]?.cancel()
            val updatedAudio = _recordingAudioStatus.value.toMutableMap()
            if (updatedAudio[processedMessage.conversationId] == true) {
                updatedAudio[processedMessage.conversationId] = false
                _recordingAudioStatus.value = updatedAudio
                synchronized(listeners) {
                    listeners.forEach { it.onRecordingAudio(processedMessage.conversationId, processedMessage.senderId, "", false) }
                }
            }

            // Sender is actively online
            val onlMap = _onlineUsers.value.toMutableMap()
            onlMap[processedMessage.senderId] = true
            _onlineUsers.value = onlMap

            synchronized(listeners) {
                listeners.forEach { it.onMessageReceived(processedMessage) }
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

            // If message is read, mark all previous messages in that conversation as read too
            if (status == "read") {
                val msg = db.messageDao().getMessageById(messageId)
                if (msg != null) {
                    db.messageDao().markOutgoingAsReadUpTo(msg.conversationId, msg.createdAt)
                }
            }

            synchronized(listeners) {
                listeners.forEach { it.onMessageReceipt(messageId, status, userId) }
            }
        }
    }

    override fun onConversationRead(conversationId: String, userId: String) {
        val myId = LivreChatApp.instance.preferencesManager.currentUserId
        if (!myId.isNullOrBlank() && userId == myId) {
            // Não marcar as nossas próprias mensagens enviadas como lidas se o evento veio de nós mesmos
            return
        }
        scope.launch {
            val db = LivreChatApp.instance.database
            db.messageDao().markAllOutgoingAsRead(conversationId)

            synchronized(listeners) {
                listeners.forEach { it.onConversationRead(conversationId, userId) }
            }
        }
    }

    private val typingTimeoutJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean, displayName: String?) {
        typingTimeoutJobs[conversationId]?.cancel()

        val current = _typingStatus.value.toMutableMap()
        current[conversationId] = isTyping
        _typingStatus.value = current

        val userMap = _typingUserByConv.value.toMutableMap()
        if (isTyping) {
            val prefs = LivreChatApp.instance.preferencesManager
            val effectiveName = prefs.getContactCustomName(userId)
                ?: displayName?.takeIf { it.isNotBlank() }
                ?: username.ifBlank { "Alguém" }
            userMap[conversationId] = effectiveName
            _typingUserByConv.value = userMap

            val onlMap = _onlineUsers.value.toMutableMap()
            onlMap[userId] = true
            _onlineUsers.value = onlMap

            typingTimeoutJobs[conversationId] = scope.launch {
                kotlinx.coroutines.delay(2500)
                val updated = _typingStatus.value.toMutableMap()
                updated[conversationId] = false
                _typingStatus.value = updated

                val uMap = _typingUserByConv.value.toMutableMap()
                uMap.remove(conversationId)
                _typingUserByConv.value = uMap

                synchronized(listeners) {
                    listeners.forEach { it.onTyping(conversationId, userId, username, false, displayName) }
                }
            }
        } else {
            userMap.remove(conversationId)
            _typingUserByConv.value = userMap
        }

        synchronized(listeners) {
            listeners.forEach { it.onTyping(conversationId, userId, username, isTyping, displayName) }
        }
    }

    private val recordingAudioTimeoutJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    override fun onRecordingAudio(conversationId: String, userId: String, username: String, isRecording: Boolean, displayName: String?) {
        recordingAudioTimeoutJobs[conversationId]?.cancel()

        val current = _recordingAudioStatus.value.toMutableMap()
        current[conversationId] = isRecording
        _recordingAudioStatus.value = current

        val recMap = _recordingUserByConv.value.toMutableMap()
        if (isRecording) {
            val prefs = LivreChatApp.instance.preferencesManager
            val effectiveName = prefs.getContactCustomName(userId)
                ?: displayName?.takeIf { it.isNotBlank() }
                ?: username.ifBlank { "Alguém" }
            recMap[conversationId] = effectiveName
            _recordingUserByConv.value = recMap

            val onlMap = _onlineUsers.value.toMutableMap()
            onlMap[userId] = true
            _onlineUsers.value = onlMap

            recordingAudioTimeoutJobs[conversationId] = scope.launch {
                kotlinx.coroutines.delay(6000)
                val updated = _recordingAudioStatus.value.toMutableMap()
                updated[conversationId] = false
                _recordingAudioStatus.value = updated

                val rMap = _recordingUserByConv.value.toMutableMap()
                rMap.remove(conversationId)
                _recordingUserByConv.value = rMap

                synchronized(listeners) {
                    listeners.forEach { it.onRecordingAudio(conversationId, userId, username, false, displayName) }
                }
            }
        } else {
            recMap.remove(conversationId)
            _recordingUserByConv.value = recMap
        }

        synchronized(listeners) {
            listeners.forEach { it.onRecordingAudio(conversationId, userId, username, isRecording, displayName) }
        }
    }

    override fun onUserStatus(userId: String, isOnline: Boolean, lastSeenTimestamp: Long?) {
        val map = _onlineUsers.value.toMutableMap()
        map[userId] = isOnline
        _onlineUsers.value = map

        if (!isOnline) {
            val validTimestamp = if (lastSeenTimestamp != null && lastSeenTimestamp > 0L) {
                lastSeenTimestamp
            } else {
                _lastSeenUsers.value[userId] ?: preferencesManager.getContactLastSeen(userId)
            }
            if (validTimestamp != null && validTimestamp > 0L) {
                val lsMap = _lastSeenUsers.value.toMutableMap()
                lsMap[userId] = validTimestamp
                _lastSeenUsers.value = lsMap
                preferencesManager.setContactLastSeen(userId, validTimestamp)
            }
        }

        synchronized(listeners) {
            listeners.forEach { it.onUserStatus(userId, isOnline, lastSeenTimestamp) }
        }
    }

    private var lastIncomingCallerInfo: Triple<String, String, String>? = null // id, username, displayName

    override fun onIncomingCall(
        callerId: String,
        callerUsername: String,
        callerDisplayName: String,
        callerAvatarUrl: String?,
        sdp: String
    ) {
        lastIncomingCallerInfo = Triple(callerId, callerUsername, callerDisplayName)
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
        onCallAnswered(calleeId, sdp, 0L)
    }

    override fun onCallAnswered(calleeId: String, sdp: String, startedAt: Long) {
        lastIncomingCallerInfo = null
        LivreChatNotificationManager.cancelCallNotification(context)
        synchronized(listeners) {
            listeners.forEach { it.onCallAnswered(calleeId, sdp, startedAt) }
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
        lastIncomingCallerInfo = null
        LivreChatNotificationManager.cancelCallNotification(context)
        synchronized(listeners) {
            listeners.forEach { it.onCallRejected(fromUserId, reason) }
        }
    }

    override fun onCallEnded(fromUserId: String) {
        LivreChatNotificationManager.cancelCallNotification(context)
        val incoming = lastIncomingCallerInfo
        if (incoming != null && incoming.first == fromUserId) {
            val (cId, cUser, cName) = incoming
            lastIncomingCallerInfo = null
            scope.launch {
                val db = LivreChatApp.instance.database
                val record = com.livrechat.app.data.local.entity.CallRecordEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    contactUserId = cId,
                    contactDisplayName = cName,
                    contactUsername = cUser,
                    contactAvatarUrl = null,
                    isOutgoing = false,
                    status = "missed",
                    durationSeconds = 0,
                    timestamp = System.currentTimeMillis()
                )
                db.callDao().insertCall(record)
                LivreChatNotificationManager.showMissedCallNotification(
                    context = context,
                    callerName = cName,
                    callerUserId = cId
                )
            }
        }
        synchronized(listeners) {
            listeners.forEach { it.onCallEnded(fromUserId) }
        }
    }

    override fun onMessageDeleted(messageId: String, conversationId: String) {
        scope.launch {
            val db = LivreChatApp.instance.database
            db.messageDao().softDeleteMessage(messageId)
            val lastMsg = db.messageDao().getMessagesForConversation(conversationId).lastOrNull()
            if (lastMsg != null && lastMsg.id == messageId) {
                db.conversationDao().updateLastMessage(conversationId, "🚫 Esta mensagem foi apagada", lastMsg.createdAt)
            }
            synchronized(listeners) {
                listeners.forEach { it.onMessageDeleted(messageId, conversationId) }
            }
        }
    }

    override fun onMessageReaction(messageId: String, conversationId: String, userId: String, emoji: String, reactions: String?) {
        scope.launch {
            val db = LivreChatApp.instance.database
            if (reactions != null) {
                db.messageDao().updateReactions(messageId, reactions)
            } else {
                val msg = db.messageDao().getMessageById(messageId)
                if (msg != null) {
                    val current = com.livrechat.app.data.local.model.ReactionUtils.parseReactions(msg.reactions).toMutableList()
                    val idx = current.indexOfFirst { it.userId == userId }
                    if (idx >= 0) {
                        if (current[idx].emoji == emoji) {
                            current.removeAt(idx)
                        } else {
                            current[idx] = current[idx].copy(emoji = emoji)
                        }
                    } else {
                        current.add(com.livrechat.app.data.local.model.MessageReaction(emoji, userId))
                    }
                    db.messageDao().updateReactions(messageId, com.livrechat.app.data.local.model.ReactionUtils.serializeReactions(current))
                }
            }
            synchronized(listeners) {
                listeners.forEach { it.onMessageReaction(messageId, conversationId, userId, emoji, reactions) }
            }
        }
    }

    override fun onStatusReaction(statusId: String, fromUserId: String, fromUsername: String, emoji: String, displayName: String?, avatarUrl: String?) {
        synchronized(listeners) {
            listeners.forEach { it.onStatusReaction(statusId, fromUserId, fromUsername, emoji, displayName, avatarUrl) }
        }
    }

    override fun onStatusView(statusId: String, fromUserId: String, fromUsername: String, displayName: String?, avatarUrl: String?) {
        synchronized(listeners) {
            listeners.forEach { it.onStatusView(statusId, fromUserId, fromUsername, displayName, avatarUrl) }
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

    override fun onStatusUpdated() {
        synchronized(listeners) {
            listeners.forEach { it.onStatusUpdated() }
        }
    }

    override fun onVersionStatus(isUpdateAvailable: Boolean, isMandatory: Boolean, latestVersion: String, latestVersionCode: Int, downloadUrl: String, message: String) {
        synchronized(listeners) {
            listeners.forEach { it.onVersionStatus(isUpdateAvailable, isMandatory, latestVersion, latestVersionCode, downloadUrl, message) }
        }
    }

    override fun onChatError(code: String, message: String, clientMessageId: String?) {
        synchronized(listeners) {
            listeners.forEach { it.onChatError(code, message, clientMessageId) }
        }
    }
}
