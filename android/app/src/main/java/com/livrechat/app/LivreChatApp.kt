package com.livrechat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.livrechat.app.data.local.AppDatabase
import com.livrechat.app.data.local.PreferencesManager
import com.livrechat.app.data.remote.ApiClient

class LivreChatApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var apiClient: ApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        apiClient = ApiClient(preferencesManager)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Message notifications channel
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Mensagens",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de novas mensagens recebidas"
                enableVibration(true)
            }

            // Call notifications channel
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Chamadas de Voz",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de chamadas de voz recebidas"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(callsChannel)
        }
    }

    companion object {
        lateinit var instance: LivreChatApp
            private set

        const val CHANNEL_MESSAGES = "livrechat_messages"
        const val CHANNEL_CALLS = "livrechat_calls"
    }
}
