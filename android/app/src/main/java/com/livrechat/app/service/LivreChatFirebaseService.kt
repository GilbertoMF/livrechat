package com.livrechat.app.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.notification.LivreChatNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LivreChatFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Novo token FCM gerado: $token")
        val prefs = LivreChatApp.instance.preferencesManager
        prefs.fcmToken = token

        if (prefs.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LivreChatApp.instance.apiService.registerFcmToken(
                        mapOf(
                            "token" to token,
                            "deviceName" to Build.MODEL,
                            "versionCode" to com.livrechat.app.BuildConfig.VERSION_CODE.toString(),
                            "versionName" to com.livrechat.app.BuildConfig.VERSION_NAME
                        )
                    )
                    Log.d(TAG, "Token FCM sincronizado com o servidor com sucesso.")
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao enviar token FCM para o servidor", e)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 1. WhatsApp Pattern: Segura um WakeLock explícito para o processador não dormir durante o processamento
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LivreChat:PushWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(10000L) // 10 segundos de garantia de CPU ativa
        }

        try {
            val data = remoteMessage.data
            if (data.isEmpty()) {
                Log.d(TAG, "FCM recebido sem payload de dados.")
                return
            }

            Log.d(TAG, "FCM High-Priority Data recebido: $data")
            val type = data["type"] ?: "new_message"

            when (type) {
                "new_message" -> handleNewMessage(data)
                "incoming_call" -> handleIncomingCall(data)
                else -> Log.d(TAG, "Tipo de evento FCM desconhecido: $type")
            }
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {}
        }
    }

    private fun handleNewMessage(data: Map<String, String>) {
        val messageId = data["messageId"] ?: return
        val conversationId = data["conversationId"] ?: return
        val senderId = data["senderId"] ?: return
        val senderName = data["senderName"] ?: "LivreChat"
        val content = data["content"] ?: ""
        val messageType = data["messageType"] ?: "text"
        val createdAt = data["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()

        val prefs = LivreChatApp.instance.preferencesManager
        val myUserId = prefs.currentUserId

        // Não notificar mensagens enviadas pelo próprio usuário
        if (myUserId != null && myUserId == senderId) {
            return
        }

        var resolvedSenderName = senderName

        // WhatsApp Pattern: Executa a persistência e ack de entrega de forma garantida antes da CPU dormir
        runBlocking(Dispatchers.IO) {
            try {
                val db = LivreChatApp.instance.database
                val localCustomName = prefs.getContactCustomName(senderId)
                val savedContactName = db.contactDao().getContactByUserId(senderId)?.displayName?.takeIf { it.isNotBlank() }
                val existingConv = db.conversationDao().getConversationById(conversationId)
                val existingConvName = existingConv?.otherDisplayName?.takeIf { it.isNotBlank() && it != senderId && !it.startsWith("ID ") }

                resolvedSenderName = localCustomName
                    ?: savedContactName
                    ?: existingConvName
                    ?: (if (senderName.isNotBlank() && senderName != senderId && !senderName.startsWith("ID ")) senderName else null)
                    ?: "Contato"

                val existing = db.messageDao().getMessageById(messageId)
                if (existing == null) {
                    val isCurrentOpen = conversationId == LivreChatNotificationManager.activeConversationId
                    val entity = MessageEntity(
                        id = messageId,
                        conversationId = conversationId,
                        senderId = senderId,
                        content = content,
                        messageType = messageType,
                        status = if (isCurrentOpen) "read" else "delivered",
                        isOutgoing = false,
                        createdAt = createdAt
                    )
                    db.messageDao().insertOrUpdate(entity)

                    // Atualiza a conversa localmente para que já apareça no topo da lista
                    if (existingConv != null) {
                        db.conversationDao().updateLastMessage(conversationId, content, createdAt)
                        if (isCurrentOpen) {
                            db.conversationDao().clearUnreadCount(conversationId)
                        } else {
                            db.conversationDao().recalculateUnreadCount(conversationId)
                        }
                        if (existingConv.otherDisplayName.isBlank() || existingConv.otherDisplayName == senderId || existingConv.otherDisplayName.startsWith("ID ")) {
                            db.conversationDao().insertOrUpdate(existingConv.copy(otherDisplayName = resolvedSenderName))
                        }
                    } else {
                        val newConv = ConversationEntity(
                            id = conversationId,
                            otherUserId = senderId,
                            otherUsername = senderName,
                            otherDisplayName = resolvedSenderName,
                            lastMessageContent = content,
                            lastMessageTimestamp = createdAt,
                            unreadCount = if (isCurrentOpen) 0 else 1
                        )
                        db.conversationDao().insertOrUpdate(newConv)
                    }
                }

                // WhatsApp Pattern: Envia o ACK de entrega (Delivered Receipt) de volta ao servidor
                // Isso faz a mensagem mudar de 1 tick para 2 ticks cinzas no celular do remetente na hora!
                if (prefs.isLoggedIn()) {
                    try {
                        LivreChatApp.instance.apiService.markMessageDelivered(messageId)
                        Log.d(TAG, "ACK de entrega enviado ao servidor para mensagem: $messageId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Falha ao enviar ACK de entrega (offline)", e)
                    }
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar mensagem FCM no Room", e)
            }
        }

        // 3. Dispara a notificação heads-up personalizada com o nome salvo localmente
        LivreChatNotificationManager.showMessageNotification(
            context = applicationContext,
            conversationId = conversationId,
            senderName = resolvedSenderName,
            content = content,
            messageId = messageId,
            messageType = messageType
        )
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        val callerId = data["callerId"] ?: return
        val callerUsername = data["callerUsername"] ?: "Usuário"
        val rawCallerDisplayName = data["callerDisplayName"] ?: callerUsername

        val prefs = LivreChatApp.instance.preferencesManager
        val localCustomName = prefs.getContactCustomName(callerId)
        val savedContactName = try {
            LivreChatApp.instance.database.contactDao().getContactByUserIdBlocking(callerId)?.displayName?.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }

        val resolvedCallerName = localCustomName
            ?: savedContactName
            ?: (if (rawCallerDisplayName.isNotBlank() && rawCallerDisplayName != callerId && !rawCallerDisplayName.startsWith("ID ")) rawCallerDisplayName else null)
            ?: callerUsername

        val sdp = data["sdp"]

        LivreChatNotificationManager.showIncomingCallNotification(
            context = applicationContext,
            callerId = callerId,
            callerDisplayName = resolvedCallerName,
            callerUsername = callerUsername,
            sdp = sdp
        )

        // Se a ViewModel de chamada já estiver ativa, pré-carrega os dados da chamada recebida
        com.livrechat.app.ui.call.CallViewModel.activeInstance?.prepareIncomingCall(
            callerId = callerId,
            callerDisplayName = resolvedCallerName,
            callerUsername = callerUsername,
            sdp = sdp
        )

        // WhatsApp Pattern: Notifica o servidor imediatamente que o aparelho está tocando!
        // O servidor repassa na mesma hora para quem ligou, mudando a tela de "Ligando..." para "Chamando..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LivreChatApp.instance.apiService.notifyCallRinging(
                    mapOf(
                        "callerId" to callerId,
                        "calleeId" to (prefs.currentUserId ?: "")
                    )
                )
                Log.d(TAG, "Notificado servidor: chamada tocando fisicamente (Chamando...) para $callerId")
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao enviar ACK de ringing via HTTP", e)
            }
        }

        // Conecta o WebSocket para agilizar o WebRTC caso o usuário atenda
        if (prefs.isLoggedIn()) {
            LivreChatApp.instance.connectionManager.connect()
        }
    }

    companion object {
        private const val TAG = "LivreChatFCM"

        fun syncToken(context: android.content.Context) {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            val token = task.result
                            val prefs = LivreChatApp.instance.preferencesManager
                            prefs.fcmToken = token
                            if (prefs.isLoggedIn()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        LivreChatApp.instance.apiService.registerFcmToken(
                                            mapOf(
                                                "token" to token,
                                                "deviceName" to Build.MODEL,
                                                "versionCode" to com.livrechat.app.BuildConfig.VERSION_CODE.toString(),
                                                "versionName" to com.livrechat.app.BuildConfig.VERSION_NAME
                                            )
                                        )
                                        Log.d(TAG, "Token FCM sincronizado com sucesso no arranque do app.")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Erro ao sincronizar token FCM no arranque", e)
                                    }
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao consultar token FCM", e)
            }
        }
    }
}
