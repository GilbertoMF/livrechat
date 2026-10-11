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
            override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean) {}
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
            connectionManager.connect()
        }

        // Cancel any lingering persistent foreground notification and delete its channel
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(1001)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm?.deleteNotificationChannel("livrechat_background_sync_channel")
            }
            com.livrechat.app.service.LivreChatBackgroundService.stop(this)
        } catch (e: Exception) {}

        // Rescue existing SQLite database from SQLiteBlobTooBigException crashes:
        // Truncate any raw Base64 blobs exceeding 250KB in the messages table
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE messages SET content = '[Arquivo transferido / salvo]' WHERE length(content) > 250000 AND (content NOT LIKE 'file://%' AND content NOT LIKE '/%')"
                )
            } catch (e: Exception) {
                android.util.Log.e("LivreChatApp", "Falha ao sanitizar mensagens grandes do SQLite", e)
            }
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
