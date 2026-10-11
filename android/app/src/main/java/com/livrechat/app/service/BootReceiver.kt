package com.livrechat.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.livrechat.app.LivreChatApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = LivreChatApp.instance.preferencesManager
            if (prefs.isLoggedIn()) {
                LivreChatBackgroundService.start(context)
            }
        }
    }
}
