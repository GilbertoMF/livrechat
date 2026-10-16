package com.livrechat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.gson.JsonObject
import com.livrechat.app.data.local.AppDatabase
import com.livrechat.app.data.local.PreferencesManager
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.data.remote.ApiClient
import com.livrechat.app.data.remote.ConnectionManager
import com.livrechat.app.data.remote.WebSocketClient
import com.livrechat.app.data.remote.WebSocketEventsListener
import com.livrechat.app.notification.LivreChatNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LivreChatApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var connectionManager: ConnectionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        apiClient = ApiClient(preferencesManager)

        LivreChatNotificationManager.initChannels(this)

        val dummyListener = object : WebSocketEventsListener {
            override fun onConnected() {}
            override fun onDisconnected() {}
            override fun onMessageReceived(message: MessageEntity) {}
            override fun onMessageAck(messageId: String, clientMessageId: String?, status: String) {}
            override fun onMessageReceipt(messageId: String, status: String, userId: String) {}
            override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean, displayName: String?) {}
            override fun onIncomingCall(callerId: String, callerUsername: String, callerDisplayName: String, callerAvatarUrl: String?, sdp: String) {}
            override fun onCallAnswered(calleeId: String, sdp: String) {}
            override fun onIceCandidateReceived(fromUserId: String, candidate: JsonObject) {}
            override fun onCallRinging(fromUserId: String) {}
            override fun onCallRejected(fromUserId: String, reason: String) {}
            override fun onCallEnded(fromUserId: String) {}
        }
        val ws = WebSocketClient(preferencesManager, dummyListener)
        connectionManager = ConnectionManager(this, ws)
        ws.setListener(connectionManager)

        if (preferencesManager.isLoggedIn()) {
            com.livrechat.app.service.LivreChatBackgroundService.start(this)
            connectionManager.connect()
        }

        // Non-blocking background initialization on Dispatchers.IO
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE messages SET content = '[Arquivo transferido / salvo]' WHERE length(content) > 250000 AND (content NOT LIKE 'file://%' AND content NOT LIKE '/%')"
                )
            } catch (e: Exception) {}
            try {
                val now = System.currentTimeMillis()
                // Fix any messages saved with corrupted future timestamps (> now + 30s)
                // due to the previous UTC without timezone parsing bug (+3h / 10,800,000ms)
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE messages SET createdAt = CASE WHEN (createdAt - 10800000) > $now THEN ($now - 1000) ELSE (createdAt - 10800000) END WHERE createdAt > ${now + 30000}"
                )
            } catch (e: Exception) {}
        }
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
