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
                            "deviceName" to Build.MODEL
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

        // WhatsApp Pattern: Executa a persistência e ack de entrega de forma garantida antes da CPU dormir
        runBlocking(Dispatchers.IO) {
            try {
                val db = LivreChatApp.instance.database
                val existing = db.messageDao().getMessageById(messageId)
                if (existing == null) {
                    val entity = MessageEntity(
                        id = messageId,
                        conversationId = conversationId,
                        senderId = senderId,
                        content = content,
                        messageType = messageType,
                        status = "delivered",
                        isOutgoing = false,
                        createdAt = createdAt
                    )
                    db.messageDao().insertOrUpdate(entity)

                    // Atualiza a conversa localmente para que já apareça no topo da lista
                    val conv = db.conversationDao().getConversationById(conversationId)
                    if (conv != null) {
                        db.conversationDao().updateLastMessage(conversationId, content, createdAt)
                        db.conversationDao().recalculateUnreadCount(conversationId)
                    } else {
                        val newConv = ConversationEntity(
                            id = conversationId,
                            otherUserId = senderId,
                            otherUsername = senderName,
                            otherDisplayName = senderName,
                            lastMessageContent = content,
                            lastMessageTimestamp = createdAt,
                            unreadCount = 1
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

        // 3. Dispara a notificação heads-up personalizada
        LivreChatNotificationManager.showMessageNotification(
            context = applicationContext,
            conversationId = conversationId,
            senderName = senderName,
            content = content,
            messageId = messageId,
            messageType = messageType
        )
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        val callerId = data["callerId"] ?: return
        val callerUsername = data["callerUsername"] ?: "Usuário"
        val callerDisplayName = data["callerDisplayName"] ?: callerUsername

        LivreChatNotificationManager.showIncomingCallNotification(
            context = applicationContext,
            callerId = callerId,
            callerDisplayName = callerDisplayName,
            callerUsername = callerUsername
        )
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
                                                "deviceName" to Build.MODEL
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
