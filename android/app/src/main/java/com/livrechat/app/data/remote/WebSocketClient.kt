package com.livrechat.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.livrechat.app.data.local.PreferencesManager
import com.livrechat.app.data.local.entity.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.livrechat.app.util.TimeUtils
import java.util.concurrent.TimeUnit

interface WebSocketEventsListener {
    fun onConnected()
    fun onDisconnected()
    fun onMessageReceived(message: MessageEntity)
    fun onMessageAck(messageId: String, clientMessageId: String?, status: String)
    fun onMessageReceipt(messageId: String, status: String, userId: String)
    fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean, displayName: String? = null) {}
    fun onRecordingAudio(conversationId: String, userId: String, username: String, isRecording: Boolean, displayName: String? = null) {}
    // WebRTC Signaling
    fun onIncomingCall(callerId: String, callerUsername: String, callerDisplayName: String, callerAvatarUrl: String?, sdp: String)
    fun onCallAnswered(calleeId: String, sdp: String)
    fun onCallAnswered(calleeId: String, sdp: String, startedAt: Long) {
        onCallAnswered(calleeId, sdp)
    }
    fun onIceCandidateReceived(fromUserId: String, candidate: JsonObject)
    fun onCallRinging(fromUserId: String)
    fun onCallRejected(fromUserId: String, reason: String)
    fun onCallEnded(fromUserId: String) {}
    fun onMessageDeleted(messageId: String, conversationId: String) {}
    fun onChatCleared(conversationId: String) {}
    fun onStatusUpdated() {}
    fun onMessageReaction(messageId: String, conversationId: String, userId: String, emoji: String, reactions: String?) {}
    fun onStatusReaction(statusId: String, fromUserId: String, fromUsername: String, emoji: String, displayName: String? = null, avatarUrl: String? = null) {}
    fun onStatusView(statusId: String, fromUserId: String, fromUsername: String, displayName: String? = null, avatarUrl: String? = null) {}
    fun onConversationRead(conversationId: String, userId: String) {}
    fun onUserStatus(userId: String, isOnline: Boolean, lastSeenTimestamp: Long? = null) {}
    fun onVersionStatus(isUpdateAvailable: Boolean, isMandatory: Boolean, latestVersion: String, latestVersionCode: Int, downloadUrl: String, message: String) {}
    fun onChatError(code: String, message: String, clientMessageId: String? = null) {}
}

class WebSocketClient(
    private val preferencesManager: PreferencesManager,
    private var listener: WebSocketEventsListener
) {
    fun setListener(newListener: WebSocketEventsListener) {
        this.listener = newListener
    }
    private val TAG = "LivreChat_WS"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private var isIntentionalClose = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    @Volatile
    private var isConnected = false
    @Volatile
    private var isConnecting = false

    fun isConnected(): Boolean = isConnected

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        val token = preferencesManager.accessToken ?: return
        if (token.isBlank()) return

        // If already connected and socket is open, do not re-create
        if (isConnected && webSocket != null) {
            Log.d(TAG, "WebSocket já conectado, ignorando solicitação redundante.")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0

        var server = preferencesManager.serverUrl.trim()
        server = server.replace("http://", "ws://").replace("https://", "wss://")
        if (server.endsWith("/")) {
            server = server.substring(0, server.length - 1)
        }
        val vCode = com.livrechat.app.BuildConfig.VERSION_CODE
        val vName = com.livrechat.app.BuildConfig.VERSION_NAME
        val wsUrl = "$server/ws?token=$token&versionCode=$vCode&version=$vName"

        isIntentionalClose = false
        isConnecting = true

        try {
            webSocket?.cancel()
        } catch (e: Exception) {}

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-App-Version-Code", vCode.toString())
            .addHeader("X-App-Version", vName)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Conexão WebSocket estabelecida.")
                isConnected = true
                isConnecting = false
                reconnectAttempts = 0
                scope.launch(Dispatchers.Main) {
                    listener.onConnected()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket fechado: $code / $reason")
                isConnected = false
                isConnecting = false
                scope.launch(Dispatchers.Main) {
                    listener.onDisconnected()
                }
                if (!isIntentionalClose) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Falha no WebSocket: ${t.message}")
                isConnected = false
                isConnecting = false
                scope.launch(Dispatchers.Main) {
                    listener.onDisconnected()
                }
                if (!isIntentionalClose) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        isIntentionalClose = true
        isConnected = false
        isConnecting = false
        webSocket?.close(1000, "Desconexão solicitada")
        webSocket = null
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            val delayMs = (1000L * (1 shl Math.min(reconnectAttempts, 3))).coerceAtMost(10000L)
            Log.d(TAG, "Tentando reconectar em ${delayMs / 1000}s (tentativa $reconnectAttempts)...")
            delay(delayMs)
            if (!isIntentionalClose && isActive) {
                connect()
            }
        }
    }

    private fun JsonElement?.asStringOrNull(): String? {
        if (this == null || this.isJsonNull) return null
        return try { this.asString } catch (e: Exception) { null }
    }

    private fun JsonElement?.asStringOrDefault(default: String = ""): String {
        if (this == null || this.isJsonNull) return default
        return try { this.asString } catch (e: Exception) { default }
    }

    private fun JsonElement?.asBooleanOrDefault(default: Boolean = false): Boolean {
        if (this == null || this.isJsonNull) return default
        return try { this.asBoolean } catch (e: Exception) { default }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type").asStringOrNull() ?: return

            scope.launch(Dispatchers.Main) {
                try {
                    when (type) {
                        "connection:ack" -> {
                            if (json.has("versionStatus") && !json.get("versionStatus").isJsonNull) {
                                val vs = json.getAsJsonObject("versionStatus")
                                val isUpdateAvail = vs.get("isUpdateAvailable").asBooleanOrDefault(false)
                                val isMandatory = vs.get("isMandatory").asBooleanOrDefault(false)
                                val latestVer = vs.get("latestVersion").asStringOrDefault("")
                                val latestCode = if (vs.has("latestVersionCode") && !vs.get("latestVersionCode").isJsonNull) vs.get("latestVersionCode").asInt else 0
                                val dlUrl = vs.get("downloadUrl").asStringOrDefault("")
                                listener.onVersionStatus(isUpdateAvail, isMandatory, latestVer, latestCode, dlUrl, "Atualização disponível")
                            }
                        }
                        "system:version_status", "version:incompatible" -> {
                            val msg = json.get("message").asStringOrDefault("Versões diferentes não podem se comunicar por segurança. Atualize o aplicativo.")
                            val isMandatory = json.get("isMandatory").asBooleanOrDefault(true)
                            val latestVer = json.get("latestVersion").asStringOrDefault("")
                            val latestCode = if (json.has("latestVersionCode") && !json.get("latestVersionCode").isJsonNull) json.get("latestVersionCode").asInt else 0
                            val dlUrl = json.get("downloadUrl").asStringOrDefault("")
                            listener.onVersionStatus(true, isMandatory, latestVer, latestCode, dlUrl, msg)
                        }
                        "chat:error" -> {
                            val code = json.get("code").asStringOrDefault("ERROR")
                            val msg = json.get("message").asStringOrDefault("Erro ao processar mensagem.")
                            val clientMsgId = json.get("clientMessageId")?.asStringOrNull()
                            listener.onChatError(code, msg, clientMsgId)
                        }
                        "chat:message" -> {
                            val m = json.getAsJsonObject("message") ?: return@launch
                            val currentUserId = preferencesManager.currentUserId ?: ""
                            val senderId = m.get("sender_id").asStringOrDefault()
                            if (preferencesManager.isUserBlocked(senderId)) {
                                Log.d(TAG, "Mensagem descartada de usuário bloqueado: $senderId")
                                return@launch
                            }
                            val createdAtStr = m.get("created_at")?.asStringOrNull()
                            val msgCreatedAt = if (!createdAtStr.isNullOrBlank()) {
                                TimeUtils.parseIsoTimestamp(createdAtStr)
                            } else {
                                System.currentTimeMillis()
                            }
                            val rawMsgType = m.get("message_type").asStringOrDefault("text")
                            val isViewOnce = m.get("is_view_once").asBooleanOrDefault(false) ||
                                    rawMsgType == "view_once_image" ||
                                    rawMsgType == "view_once_video" ||
                                    m.get("iv")?.asStringOrNull()?.contains("view_once:true") == true
                            val viewOnceOpened = m.get("view_once_opened").asBooleanOrDefault(false) || m.get("content").asStringOrDefault() == "[Mídia expirada]"
                            val isPinned = m.get("is_pinned").asBooleanOrDefault(false)
                            val entity = MessageEntity(
                                id = m.get("id").asStringOrDefault(),
                                conversationId = m.get("conversation_id").asStringOrDefault(),
                                senderId = senderId,
                                content = m.get("content").asStringOrDefault(),
                                iv = m.get("iv").asStringOrNull(),
                                messageType = rawMsgType,
                                replyToId = m.get("reply_to_id").asStringOrNull(),
                                status = "delivered",
                                isOutgoing = senderId == currentUserId,
                                isEdited = m.get("is_edited").asBooleanOrDefault(false),
                                isDeleted = m.get("is_deleted").asBooleanOrDefault(false),
                                isViewOnce = isViewOnce,
                                viewOnceOpened = viewOnceOpened,
                                isPinned = isPinned,
                                createdAt = msgCreatedAt
                            )
                            listener.onMessageReceived(entity)
                        }
                        "chat:ack" -> {
                            val messageId = json.get("messageId").asStringOrDefault()
                            val clientMsgId = json.get("clientMessageId").asStringOrNull()
                            val status = json.get("status").asStringOrDefault()
                            listener.onMessageAck(messageId, clientMsgId, status)
                        }
                        "chat:receipt" -> {
                            val messageId = json.get("messageId").asStringOrDefault()
                            val status = json.get("status").asStringOrDefault()
                            val userId = json.get("userId").asStringOrDefault()
                            listener.onMessageReceipt(messageId, status, userId)
                        }
                        "chat:typing" -> {
                            val convId = json.get("conversationId").asStringOrDefault()
                            val userId = json.get("userId").asStringOrDefault()
                            if (preferencesManager.isUserBlocked(userId)) return@launch
                            val username = json.get("username").asStringOrDefault()
                            val displayName = json.get("displayName")?.asStringOrNull()
                            val isTyping = json.get("isTyping").asBooleanOrDefault(false)
                            listener.onTyping(convId, userId, username, isTyping, displayName)
                        }
                        "chat:recording_audio" -> {
                            val convId = json.get("conversationId").asStringOrDefault()
                            val userId = json.get("userId").asStringOrDefault()
                            if (preferencesManager.isUserBlocked(userId)) return@launch
                            val username = json.get("username").asStringOrDefault()
                            val displayName = json.get("displayName")?.asStringOrNull()
                            val isRecording = json.get("isRecording").asBooleanOrDefault(false)
                            listener.onRecordingAudio(convId, userId, username, isRecording, displayName)
                        }
                        "call:incoming" -> {
                            val callerId = json.get("callerId").asStringOrDefault()
                            if (preferencesManager.isUserBlocked(callerId)) {
                                Log.d(TAG, "Chamada ignorada de contato bloqueado: $callerId")
                                return@launch
                            }
                            val callerUsername = json.get("callerUsername").asStringOrDefault()
                            val callerDisplayName = json.get("callerDisplayName").asStringOrDefault()
                            val callerAvatarUrl = json.get("callerAvatarUrl").asStringOrNull()
                            val sdp = json.get("sdp").asStringOrDefault()
                            listener.onIncomingCall(callerId, callerUsername, callerDisplayName, callerAvatarUrl, sdp)
                        }
                        "call:answered" -> {
                            val calleeId = json.get("calleeId").asStringOrDefault()
                            val sdp = json.get("sdp").asStringOrDefault()
                            val startedAt = if (json.has("startedAt") && !json.get("startedAt").isJsonNull) json.get("startedAt").asLong else 0L
                            listener.onCallAnswered(calleeId, sdp, startedAt)
                        }
                        "call:ice_candidate" -> {
                            val fromUserId = json.get("fromUserId").asStringOrDefault()
                            val candidate = json.getAsJsonObject("candidate")
                            if (candidate != null) {
                                listener.onIceCandidateReceived(fromUserId, candidate)
                            }
                        }
                        "call:ringing" -> {
                            val fromUserId = json.get("fromUserId").asStringOrDefault()
                            listener.onCallRinging(fromUserId)
                        }
                        "call:rejected" -> {
                            val fromUserId = json.get("fromUserId").asStringOrDefault()
                            val reason = json.get("reason").asStringOrDefault("Chamada recusada")
                            listener.onCallRejected(fromUserId, reason)
                        }
                        "call:ended" -> {
                            val fromUserId = json.get("fromUserId").asStringOrDefault()
                            listener.onCallEnded(fromUserId)
                        }
                        "chat:delete", "chat:deleted" -> {
                            val messageId = json.get("messageId").asStringOrDefault()
                            val convId = json.get("conversationId").asStringOrDefault()
                            listener.onMessageDeleted(messageId, convId)
                        }
                        "chat:reaction" -> {
                            val messageId = json.get("messageId").asStringOrDefault()
                            val convId = json.get("conversationId").asStringOrDefault()
                            val userId = json.get("userId").asStringOrDefault()
                            val emoji = json.get("emoji").asStringOrDefault()
                            val reactions = json.get("reactions").asStringOrNull()
                            listener.onMessageReaction(messageId, convId, userId, emoji, reactions)
                        }
                        "status:reaction" -> {
                            val statusId = json.get("statusId").asStringOrDefault()
                            val fromUserId = json.get("fromUserId").asStringOrDefault()
                            val fromUsername = json.get("fromUsername").asStringOrDefault()
                            val emoji = json.get("emoji").asStringOrDefault()
                            val displayName = json.get("displayName")?.asStringOrNull()
                            val avatarUrl = json.get("avatarUrl")?.asStringOrNull()
                            listener.onStatusReaction(statusId, fromUserId, fromUsername, emoji, displayName, avatarUrl)
                        }
                        "status:view" -> {
                            val statusId = json.get("statusId").asStringOrDefault()
                            val fromUserId = json.get("fromUserId").asStringOrDefault()
                            val fromUsername = json.get("fromUsername").asStringOrDefault()
                            val displayName = json.get("displayName")?.asStringOrNull()
                            val avatarUrl = json.get("avatarUrl")?.asStringOrNull()
                            listener.onStatusView(statusId, fromUserId, fromUsername, displayName, avatarUrl)
                        }
                        "chat:cleared" -> {
                            val convId = json.get("conversationId").asStringOrDefault()
                            listener.onChatCleared(convId)
                        }
                        "chat:conversation_read" -> {
                            val convId = json.get("conversationId").asStringOrDefault()
                            val userId = json.get("userId").asStringOrDefault()
                            listener.onConversationRead(convId, userId)
                        }
                        "status:new", "status:deleted" -> {
                            listener.onStatusUpdated()
                        }
                        "user:status" -> {
                            val uid = json.get("userId").asStringOrNull()
                            val status = json.get("status").asStringOrDefault()
                            val timestampStr = json.get("timestamp").asStringOrNull()
                            val ts = TimeUtils.parseIsoTimestamp(timestampStr)
                            if (uid != null) {
                                val isOnline = (status == "online") && !preferencesManager.isUserBlocked(uid)
                                listener.onUserStatus(uid, isOnline, ts)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Erro ao despachar evento WebSocket na main thread", t)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar mensagem do websocket", e)
        }
    }

    // Outgoing payloads
    fun sendChatMessage(
        conversationId: String,
        content: String,
        iv: String?,
        messageType: String = "text",
        replyToId: String? = null,
        clientMessageId: String,
        targetUserId: String? = null
    ): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "chat:send")
            addProperty("conversationId", conversationId)
            addProperty("content", content)
            iv?.let { addProperty("iv", it) }
            addProperty("messageType", messageType)
            replyToId?.let { addProperty("replyToId", it) }
            addProperty("clientMessageId", clientMessageId)
            targetUserId?.let { addProperty("targetUserId", it) }
        }
        return sendPayload(payload)
    }

    fun sendReceipt(messageId: String, status: String): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "chat:receipt")
            addProperty("messageId", messageId)
            addProperty("status", status)
        }
        return sendPayload(payload)
    }

    fun sendTyping(conversationId: String, isTyping: Boolean, targetUserId: String? = null): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "chat:typing")
            addProperty("conversationId", conversationId)
            addProperty("isTyping", isTyping)
            targetUserId?.let { addProperty("targetUserId", it) }
        }
        return sendPayload(payload)
    }

    fun sendRecordingAudio(conversationId: String, isRecording: Boolean, targetUserId: String? = null): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "chat:recording_audio")
            addProperty("conversationId", conversationId)
            addProperty("isRecording", isRecording)
            targetUserId?.let { addProperty("targetUserId", it) }
        }
        return sendPayload(payload)
    }

    fun sendCallOffer(calleeId: String, sdp: String): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "call:offer")
            addProperty("calleeId", calleeId)
            addProperty("sdp", sdp)
        }
        return sendPayload(payload)
    }

    fun sendCallAnswer(callerId: String, sdp: String, startedAt: Long = System.currentTimeMillis()): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "call:answer")
            addProperty("callerId", callerId)
            addProperty("sdp", sdp)
            addProperty("startedAt", startedAt)
        }
        return sendPayload(payload)
    }

    fun sendIceCandidate(targetUserId: String, candidate: JsonObject): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "call:ice_candidate")
            addProperty("targetUserId", targetUserId)
            add("candidate", candidate)
        }
        return sendPayload(payload)
    }

    fun sendCallRinging(callerId: String): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "call:ringing")
            addProperty("callerId", callerId)
        }
        return sendPayload(payload)
    }

    fun sendCallReject(callerId: String, reason: String = "Chamada recusada"): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "call:reject")
            addProperty("callerId", callerId)
            addProperty("reason", reason)
        }
        return sendPayload(payload)
    }

    fun sendCallHangup(targetUserId: String, durationSeconds: Int): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "call:hangup")
            addProperty("targetUserId", targetUserId)
            addProperty("durationSeconds", durationSeconds)
        }
        return sendPayload(payload)
    }

    fun sendDeleteMessage(messageId: String, conversationId: String, targetUserId: String? = null): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "chat:delete")
            addProperty("messageId", messageId)
            addProperty("conversationId", conversationId)
            addProperty("deleteForEveryone", true)
            targetUserId?.let { addProperty("targetUserId", it) }
        }
        return sendPayload(payload)
    }

    fun sendReaction(messageId: String, conversationId: String, emoji: String, targetUserId: String? = null): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "chat:reaction")
            addProperty("messageId", messageId)
            addProperty("conversationId", conversationId)
            addProperty("emoji", emoji)
            targetUserId?.let { addProperty("targetUserId", it) }
        }
        return sendPayload(payload)
    }

    fun sendStatusReaction(statusId: String, authorId: String, emoji: String): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "status:reaction")
            addProperty("statusId", statusId)
            addProperty("authorId", authorId)
            addProperty("emoji", emoji)
        }
        return sendPayload(payload)
    }

    fun sendStatusView(statusId: String, authorId: String): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "status:view")
            addProperty("statusId", statusId)
            addProperty("authorId", authorId)
        }
        return sendPayload(payload)
    }

    fun sendClearChat(conversationId: String, targetUserId: String? = null): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "chat:clear")
            addProperty("conversationId", conversationId)
            targetUserId?.let { addProperty("targetUserId", it) }
        }
        return sendPayload(payload)
    }

    fun queryUserStatus(targetUserId: String): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "user:query_status")
            addProperty("targetUserId", targetUserId)
        }
        return sendPayload(payload)
    }

    private fun sendPayload(payload: JsonObject): Boolean {
        val ws = webSocket ?: return false
        val text = gson.toJson(payload)
        return ws.send(text)
    }
}
