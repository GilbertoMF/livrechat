package com.livrechat.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livrechat.app.LivreChatApp
import com.livrechat.app.MainActivity
import com.livrechat.app.R
import com.livrechat.app.notification.LivreChatNotificationManager
import java.io.File

class LivreChatBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var activeAudioPlayer: MediaPlayer? = null
    private var currentPlayingMsgId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP || action == ACTION_START || action == null) {
            stopAudio()
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (e: Exception) {}
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_PLAY_AUDIO) {
            val convId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
            val sender = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "LivreChat"
            val msgId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: ""
            val audioPath = intent.getStringExtra(EXTRA_AUDIO_PATH)
            playNotificationAudio(convId, sender, msgId, audioPath)
            return START_NOT_STICKY
        } else if (action == ACTION_STOP_AUDIO) {
            val convId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
            val sender = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "LivreChat"
            val msgId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: ""
            stopNotificationAudio(convId, sender, msgId)
            stopSelf()
            return START_NOT_STICKY
        }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun playNotificationAudio(convId: String, senderName: String, messageId: String, audioPath: String?) {
        stopAudio()

        if (audioPath.isNullOrBlank()) return
        val file = File(audioPath)
        if (!file.exists() || file.length() == 0L) return

        LivreChatNotificationManager.showMessageNotification(
            context = applicationContext,
            conversationId = convId,
            senderName = senderName,
            content = "▶ Reproduzindo áudio...",
            messageId = messageId,
            messageType = "audio",
            isPlayingAudio = true
        )

        try {
            val mp = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopNotificationAudio(convId, senderName, messageId)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("LivreChatBgService", "Erro no MediaPlayer de notificação: what=$what, extra=$extra")
                    stopNotificationAudio(convId, senderName, messageId)
                    true
                }
                prepare()
                start()
            }
            activeAudioPlayer = mp
            currentPlayingMsgId = messageId
        } catch (e: Exception) {
            Log.e("LivreChatBgService", "Erro ao iniciar MediaPlayer de notificação: ${e.message}")
            stopNotificationAudio(convId, senderName, messageId)
        }
    }

    private fun stopNotificationAudio(convId: String, senderName: String, messageId: String) {
        stopAudio()
        LivreChatNotificationManager.showMessageNotification(
            context = applicationContext,
            conversationId = convId,
            senderName = senderName,
            content = "🎤 Mensagem de voz",
            messageId = messageId,
            messageType = "audio",
            isPlayingAudio = false
        )
    }

    private fun stopAudio() {
        try {
            activeAudioPlayer?.stop()
            activeAudioPlayer?.release()
        } catch (e: Exception) {}
        activeAudioPlayer = null
        currentPlayingMsgId = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            LivreChatApp.instance.connectionManager.disconnect()
        } catch (e: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopAudio()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conexão em Segundo Plano",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Notificação de áudio e conexões pontuais"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "livrechat_background_sync_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_BG"
        const val ACTION_STOP = "ACTION_STOP_BG"
        const val ACTION_PLAY_AUDIO = "com.livrechat.app.ACTION_BG_PLAY_AUDIO"
        const val ACTION_STOP_AUDIO = "com.livrechat.app.ACTION_BG_STOP_AUDIO"
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_MESSAGE_ID = "extra_message_id"

        fun start(context: Context) {
            // No-op: Persistent foreground service has been retired in favor of Google FCM high priority push.
            // Cancels any lingering notification from prior versions.
            stop(context)
        }

        fun stop(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                val intent = Intent(context, LivreChatBackgroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            } catch (e: Exception) {}
        }
    }
}
