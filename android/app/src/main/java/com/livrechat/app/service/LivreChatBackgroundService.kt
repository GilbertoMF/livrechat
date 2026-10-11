package com.livrechat.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class LivreChatBackgroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { nm?.deleteNotificationChannel(CHANNEL_ID) } catch (e: Exception) {}
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "livrechat_background_sync_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_BG"
        const val ACTION_STOP = "ACTION_STOP_BG"

        fun start(context: Context) {
            // No-op: Do not start persistent foreground notification
            stop(context)
        }

        fun stop(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try { nm?.deleteNotificationChannel(CHANNEL_ID) } catch (e: Exception) {}
                }
                context.stopService(Intent(context, LivreChatBackgroundService::class.java))
            } catch (e: Exception) {}
        }
    }
}
