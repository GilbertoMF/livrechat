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
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(callsChannel)
        }
    }

    fun showMessageNotification(
        context: Context,
        conversationId: String,
        senderName: String,
        content: String
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

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(senderName)
                .setContentText(content)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
        } catch (e: Exception) {
            // Se permissão POST_NOTIFICATIONS não concedida ou erro
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        callerId: String,
        callerDisplayName: String,
        callerUsername: String
    ) {
        try {
            val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Chamada de Voz LivreChat")
                .setContentText("$callerDisplayName (@$callerUsername) está ligando...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(ringtoneUri)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Falha silenciosa se permissão ausente
        }
    }

    fun cancelCallNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION_ID)
        } catch (e: Exception) {}
    }
}
