package com.livrechat.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.livrechat.app.LivreChatApp
import android.os.PowerManager
import com.livrechat.app.R
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.data.remote.model.SendMessageRequest
import com.livrechat.app.util.MediaFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.livrechat.app.ACTION_NOTIFICATION_REPLY"
        const val ACTION_PLAY_AUDIO = "com.livrechat.app.ACTION_PLAY_AUDIO"
        const val ACTION_STOP_AUDIO = "com.livrechat.app.ACTION_STOP_AUDIO"

        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val KEY_TEXT_REPLY = "key_text_reply"

        private const val TAG = "NotifActionReceiver"
        private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private var activeMediaPlayer: MediaPlayer? = null
        private var currentPlayingMessageId: String? = null

        fun stopAudioPlayback() {
            try {
                activeMediaPlayer?.stop()
                activeMediaPlayer?.release()
            } catch (e: Exception) {}
            activeMediaPlayer = null
            currentPlayingMessageId = null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == "com.livrechat.app.ACTION_REJECT_CALL") {
            val callerId = intent.getStringExtra("callerId")
            LivreChatNotificationManager.cancelCallNotification(context)
            if (!callerId.isNullOrBlank()) {
                receiverScope.launch {
                    try {
                        LivreChatApp.instance.connectionManager.getClient().sendCallReject(callerId, "Chamada recusada")
                    } catch (e: Exception) {}
                }
            }
            return
        }

        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "LivreChat"
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)

        when (action) {
            ACTION_REPLY -> {
                handleDirectReply(context, intent, conversationId, senderName)
            }
            ACTION_PLAY_AUDIO -> {
                if (messageId != null) {
                    handlePlayAudio(context, conversationId, senderName, messageId)
                }
            }
            ACTION_STOP_AUDIO -> {
                handleStopAudio(context, conversationId, senderName, messageId)
            }
        }
    }

    private fun handleDirectReply(
        context: Context,
        intent: Intent,
        conversationId: String,
        senderName: String
    ) {
        val remoteInputBundle: Bundle? = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInputBundle?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()

        if (replyText.isNullOrBlank()) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val app = LivreChatApp.instance
                val db = app.database
                val myId = app.preferencesManager.currentUserId ?: return@launch
                val conv = db.conversationDao().getConversationById(conversationId)
                val targetUserId = conv?.otherUserId

                val clientMessageId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                val localMessage = MessageEntity(
                    id = clientMessageId,
                    conversationId = conversationId,
                    senderId = myId,
                    content = replyText,
                    iv = null,
                    messageType = "text",
                    replyToId = null,
                    status = "sending",
                    isOutgoing = true,
                    isEdited = false,
                    isDeleted = false,
                    createdAt = now
                )

                db.messageDao().insertOrUpdate(localMessage)
                db.conversationDao().updateLastMessage(conversationId, replyText, now)

                // Try WebSocket first
                val wsSent = app.connectionManager.getClient().sendChatMessage(
                    conversationId = conversationId,
                    content = replyText,
                    iv = null,
                    messageType = "text",
                    replyToId = null,
                    clientMessageId = clientMessageId,
                    targetUserId = targetUserId
                )

                if (!wsSent) {
                    try {
                        val res = app.apiClient.apiService.sendMessage(
                            SendMessageRequest(
                                conversationId = conversationId,
                                content = replyText,
                                iv = null,
                                clientMessageId = clientMessageId,
                                messageType = "text",
                                targetUserId = targetUserId
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

                // Update notification indicating reply was sent
                withContext(Dispatchers.Main) {
                    val repliedNotification = NotificationCompat.Builder(context, LivreChatNotificationManager.CHANNEL_MESSAGES)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(senderName)
                        .setContentText("Você: $replyText ✓")
                        .setAutoCancel(true)
                        .setTimeoutAfter(3000)
                        .build()

                    NotificationManagerCompat.from(context).notify(conversationId.hashCode(), repliedNotification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao responder mensagem via notificação", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handlePlayAudio(
        context: Context,
        conversationId: String,
        senderName: String,
        messageId: String
    ) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val db = LivreChatApp.instance.database
                val msg = db.messageDao().getMessageById(messageId)
                if (msg == null) {
                    resetAudioNotification(context, conversationId, senderName, messageId)
                    return@launch
                }

                val audioFile = getOrCreateAudioFile(context, messageId, msg.content)
                if (audioFile == null || !audioFile.exists()) {
                    resetAudioNotification(context, conversationId, senderName, messageId)
                    return@launch
                }

                val intent = Intent(context, com.livrechat.app.service.LivreChatBackgroundService::class.java).apply {
                    action = com.livrechat.app.service.LivreChatBackgroundService.ACTION_PLAY_AUDIO
                    putExtra(com.livrechat.app.service.LivreChatBackgroundService.EXTRA_CONVERSATION_ID, conversationId)
                    putExtra(com.livrechat.app.service.LivreChatBackgroundService.EXTRA_SENDER_NAME, senderName)
                    putExtra(com.livrechat.app.service.LivreChatBackgroundService.EXTRA_MESSAGE_ID, messageId)
                    putExtra(com.livrechat.app.service.LivreChatBackgroundService.EXTRA_AUDIO_PATH, audioFile.absolutePath)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao repassar áudio para LivreChatBackgroundService", e)
                resetAudioNotification(context, conversationId, senderName, messageId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleStopAudio(
        context: Context,
        conversationId: String,
        senderName: String,
        messageId: String?
    ) {
        val intent = Intent(context, com.livrechat.app.service.LivreChatBackgroundService::class.java).apply {
            action = com.livrechat.app.service.LivreChatBackgroundService.ACTION_STOP_AUDIO
            putExtra(com.livrechat.app.service.LivreChatBackgroundService.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(com.livrechat.app.service.LivreChatBackgroundService.EXTRA_SENDER_NAME, senderName)
            putExtra(com.livrechat.app.service.LivreChatBackgroundService.EXTRA_MESSAGE_ID, messageId ?: "")
        }
        context.startService(intent)
    }

    private fun resetAudioNotification(
        context: Context,
        conversationId: String,
        senderName: String,
        messageId: String?
    ) {
        LivreChatNotificationManager.showMessageNotification(
            context = context,
            conversationId = conversationId,
            senderName = senderName,
            content = "🎤 Mensagem de voz",
            messageId = messageId,
            messageType = "audio",
            isPlayingAudio = false
        )
    }

    private fun getOrCreateAudioFile(context: Context, messageId: String, content: String): File? {
        // 1. Direct local file check if content is a valid local file path
        if (MediaFileManager.isLocalFilePath(content)) {
            val local = MediaFileManager.getLocalFile(content)
            if (local != null && local.exists() && local.length() > 0) {
                return local
            }
            val rawFile = File(content)
            if (rawFile.exists() && rawFile.length() > 0) {
                return rawFile
            }
        }

        // 2. Check if cache file already exists
        val cacheFile = File(context.cacheDir, "audio_msg_$messageId.m4a")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }

        // 3. Search in app media directory for received or sent file matching messageId
        try {
            val mediaDir = MediaFileManager.getMediaDir(context)
            val matchedFile = mediaDir.listFiles { _, name ->
                name.contains(messageId)
            }?.firstOrNull { it.exists() && it.length() > 0 }
            if (matchedFile != null) {
                return matchedFile
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao buscar áudio no diretório de mídia: ${e.message}")
        }

        // 4. If content is a base64 string or data uri, decode and save to cache
        if (content.isNotBlank() && !content.startsWith("/")) {
            return try {
                val base64Data = if (content.contains(",")) {
                    content.substringAfter(",")
                } else {
                    content
                }
                val decoded = Base64.decode(base64Data, Base64.DEFAULT)
                if (decoded != null && decoded.isNotEmpty()) {
                    FileOutputStream(cacheFile).use { it.write(decoded) }
                    cacheFile
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao decodificar áudio base64 para mensagem $messageId", e)
                null
            }
        }

        return null
    }
}
