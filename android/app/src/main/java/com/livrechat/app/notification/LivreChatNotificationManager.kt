package com.livrechat.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.livrechat.app.MainActivity
import com.livrechat.app.R

object LivreChatNotificationManager {

    const val CHANNEL_MESSAGES = "livrechat_messages"
    const val CHANNEL_CALLS = "livrechat_calls"
    private const val CALL_NOTIFICATION_ID = 9999

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            // Canal para mensagens instantâneas com som e vibração
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Mensagens",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de novas mensagens recebidas"
                enableVibration(true)
                setShowBadge(true)
            }

            // Canal para chamadas de voz WebRTC de prioridade máxima
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Chamadas de Voz",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de chamadas de voz recebidas"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(callsChannel)
        }
    }

    fun showMessageNotification(
        context: Context,
        conversationId: String,
        senderName: String,
        content: String,
        messageId: String? = null,
        messageType: String = "text",
        isPlayingAudio: Boolean = false
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("conversationId", conversationId)
                putExtra("senderName", senderName)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                conversationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val customNotifUri = com.livrechat.app.LivreChatApp.instance.preferencesManager.notificationRingtoneUri
            val soundUri = if (!customNotifUri.isNullOrBlank()) {
                android.net.Uri.parse(customNotifUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            // Direct Reply via RemoteInput
            val remoteInput = androidx.core.app.RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
                .setLabel("Responder...")
                .build()

            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra(NotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_NAME, senderName)
                putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, messageId)
            }

            val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                (conversationId + "_reply").hashCode(),
                replyIntent,
                replyFlags
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Responder",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

            val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(senderName)
                .setContentText(content)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .addAction(replyAction)

            // Suporte para ouvir áudio diretamente na notificação
            if (messageType == "audio" && messageId != null) {
                if (isPlayingAudio) {
                    val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_STOP_AUDIO
                        putExtra(NotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
                        putExtra(NotificationActionReceiver.EXTRA_SENDER_NAME, senderName)
                        putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, messageId)
                    }
                    val stopPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (conversationId + "_stop").hashCode(),
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val stopAction = NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "⏹ Parar Áudio",
                        stopPendingIntent
                    ).build()
                    builder.addAction(stopAction)
                } else {
                    val playIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_PLAY_AUDIO
                        putExtra(NotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
                        putExtra(NotificationActionReceiver.EXTRA_SENDER_NAME, senderName)
                        putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, messageId)
                    }
                    val playPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (conversationId + "_play").hashCode(),
                        playIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val playAction = NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_play,
                        "▶ Ouvir Áudio",
                        playPendingIntent
                    ).build()
                    builder.addAction(playAction)
                }
            }

            val notification = builder.build()
            NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
        } catch (e: Exception) {
            // Se permissão POST_NOTIFICATIONS não concedida ou erro
        }
    }

    private var activeRingtonePlayer: android.media.Ringtone? = null

    fun playIncomingRingtone(context: Context) {
        try {
            stopIncomingRingtone()
            val customCallUri = com.livrechat.app.LivreChatApp.instance.preferencesManager.callRingtoneUri
            val soundUri = if (!customCallUri.isNullOrBlank()) {
                android.net.Uri.parse(customCallUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            val r = RingtoneManager.getRingtone(context, soundUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                r.isLooping = true
            }
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.audioAttributes = audioAttributes
            r.play()
            activeRingtonePlayer = r
        } catch (e: Exception) {}
    }

    fun stopIncomingRingtone() {
        try {
            activeRingtonePlayer?.stop()
            activeRingtonePlayer = null
        } catch (e: Exception) {}
    }

    fun showIncomingCallNotification(
        context: Context,
        callerId: String,
        callerDisplayName: String,
        callerUsername: String
    ) {
        try {
            playIncomingRingtone(context)

            val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("incomingCall", true)
                putExtra("callerId", callerId)
                putExtra("callerName", callerDisplayName)
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                CALL_NOTIFICATION_ID,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Answer action
            val answerIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("incomingCall", true)
                putExtra("autoAnswer", true)
                putExtra("callerId", callerId)
                putExtra("callerName", callerDisplayName)
            }
            val answerPendingIntent = PendingIntent.getActivity(
                context,
                CALL_NOTIFICATION_ID + 1,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Reject action
            val rejectIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "com.livrechat.app.ACTION_REJECT_CALL"
                putExtra("callerId", callerId)
            }
            val rejectPendingIntent = PendingIntent.getBroadcast(
                context,
                CALL_NOTIFICATION_ID + 2,
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val customCallUri = com.livrechat.app.LivreChatApp.instance.preferencesManager.callRingtoneUri
            val ringtoneUri = if (!customCallUri.isNullOrBlank()) {
                android.net.Uri.parse(customCallUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Chamada de Voz LivreChat")
                .setContentText("$callerDisplayName (@$callerUsername) está chamando...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setSound(ringtoneUri)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .addAction(android.R.drawable.sym_action_call, "Atender", answerPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Recusar", rejectPendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Falha silenciosa se permissão ausente
        }
    }

    fun cancelCallNotification(context: Context) {
        try {
            stopIncomingRingtone()
            NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION_ID)
        } catch (e: Exception) {}
    }

    fun showVirtualSmsNotification(
        context: Context,
        virtualNumber: String,
        code: String
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                7777,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SMS LivreChat • $virtualNumber")
                .setContentText("Seu código de confirmação é $code. Não compartilhe com ninguém.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Seu código de confirmação é $code. Digite este código no LivreChat para confirmar seu número virtual de 8 dígitos."))
                .setAutoCancel(true)
                .setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(7777, notification)
        } catch (e: Exception) {}
    }
}
