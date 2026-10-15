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
        val prefs = LivreChatApp.instance.preferencesManager
        if (!prefs.isLoggedIn()) return

        Log.d("BackgroundAlarmReceiver", "Watchdog disparado: garantindo serviço em segundo plano ativo...")

        var wakeLock: PowerManager.WakeLock? = null
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LivreChat:AlarmReceiverWake")?.apply {
                setReferenceCounted(false)
                acquire(10000L) // 10s max
            }
        } catch (e: Exception) {}

        try {
            LivreChatBackgroundService.start(context)
            if (!LivreChatApp.instance.connectionManager.isConnected.value) {
                LivreChatApp.instance.connectionManager.connect()
            }
        } catch (e: Exception) {
            Log.e("BackgroundAlarmReceiver", "Erro ao reiniciar serviço: ${e.message}")
        } finally {
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (e: Exception) {}
        }

        // Reagendar próximo watchdog para garantir continuidade
        scheduleWatchdog(context, 10 * 60 * 1000L)
    }

    companion object {
        const val ACTION_RESTART = "com.livrechat.app.action.RESTART_BACKGROUND"

        fun scheduleImmediate(context: Context) {
            scheduleWatchdog(context, 1000L)
        }

        fun scheduleWatchdog(context: Context, delayMs: Long) {
            val prefs = LivreChatApp.instance.preferencesManager
            if (!prefs.isLoggedIn()) return

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

                val triggerAt = System.currentTimeMillis() + delayMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                Log.w("BackgroundAlarmReceiver", "Não foi possível agendar watchdog: ${e.message}")
            }
        }
    }
}
