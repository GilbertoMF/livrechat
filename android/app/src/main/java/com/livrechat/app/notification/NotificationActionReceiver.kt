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
import com.livrechat.app.R
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.data.remote.model.SendMessageRequest
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
        stopAudioPlayback()

        // Immediate UI update to show playing status and stop button
        LivreChatNotificationManager.showMessageNotification(
            context = context,
            conversationId = conversationId,
            senderName = senderName,
            content = "▶ Reproduzindo áudio...",
            messageId = messageId,
            messageType = "audio",
            isPlayingAudio = true
        )

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

                withContext(Dispatchers.Main) {
                    try {
                        val mp = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            setDataSource(audioFile.absolutePath)
                            setOnCompletionListener {
                                stopAudioPlayback()
                                resetAudioNotification(context, conversationId, senderName, messageId)
                            }
                            setOnErrorListener { _, _, _ ->
                                stopAudioPlayback()
                                resetAudioNotification(context, conversationId, senderName, messageId)
                                true
                            }
                            prepare()
                            start()
                        }
                        activeMediaPlayer = mp
                        currentPlayingMessageId = messageId
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao iniciar MediaPlayer na notificação", e)
                        stopAudioPlayback()
                        resetAudioNotification(context, conversationId, senderName, messageId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar áudio para reprodução", e)
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
        stopAudioPlayback()
        resetAudioNotification(context, conversationId, senderName, messageId)
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
        val file = File(context.cacheDir, "audio_msg_$messageId.m4a")
        if (file.exists() && file.length() > 0) return file

        return try {
            val base64Data = if (content.contains(",")) {
                content.substringAfter(",")
            } else {
                content
            }
            val decoded = Base64.decode(base64Data, Base64.DEFAULT)
            FileOutputStream(file).use { it.write(decoded) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao decodificar áudio", e)
            null
        }
    }
}
