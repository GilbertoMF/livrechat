package com.livrechat.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.livrechat.app.LivreChatApp

class BackgroundAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BackgroundAlarmReceiver", "Watchdog cancelado: FCM gerencia push notifications.")
        cancelWatchdog(context)
    }

    companion object {
        const val ACTION_RESTART = "com.livrechat.app.action.RESTART_BACKGROUND"

        fun scheduleImmediate(context: Context) {
            cancelWatchdog(context)
        }

        fun scheduleWatchdog(context: Context, delayMs: Long) {
            // No-op: FCM push substitui o alarme periódico para economia de bateria
            cancelWatchdog(context)
        }

        fun cancelWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, BackgroundAlarmReceiver::class.java).apply {
                    action = ACTION_RESTART
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1004,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            } catch (e: Exception) {
                Log.w("BackgroundAlarmReceiver", "Erro ao cancelar watchdog: ${e.message}")
            }
        }
    }
}
