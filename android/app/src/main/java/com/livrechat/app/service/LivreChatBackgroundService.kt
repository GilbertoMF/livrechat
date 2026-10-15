package com.livrechat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.livrechat.app.LivreChatApp
import com.livrechat.app.MainActivity
import com.livrechat.app.R

class LivreChatBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LivreChat:BackgroundSyncCpu")?.apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (e: Exception) {}
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Ensure WebSocket is connected and listening
        val prefs = LivreChatApp.instance.preferencesManager
        if (prefs.isLoggedIn()) {
            LivreChatApp.instance.connectionManager.connect()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}
        super.onDestroy()
        val prefs = LivreChatApp.instance.preferencesManager
        if (prefs.isLoggedIn()) {
            // Auto-restart service to ensure continuous message delivery
            start(applicationContext)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conexão em Segundo Plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o LivreChat conectado para receber mensagens instantâneas e chamadas"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LivreChat")
            .setContentText("Conectado para mensagens e chamadas")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "livrechat_background_sync_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_BG"
        const val ACTION_STOP = "ACTION_STOP_BG"

        fun start(context: Context) {
            val intent = Intent(context, LivreChatBackgroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore if background restrictions apply
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LivreChatBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                context.stopService(intent)
            } catch (e: Exception) {}
        }
    }
}
