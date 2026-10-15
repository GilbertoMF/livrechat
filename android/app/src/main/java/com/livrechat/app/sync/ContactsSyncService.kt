package com.livrechat.app.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ContactsSyncService : Service() {
    companion object {
        private var syncAdapter: ContactsSyncAdapter? = null
        private val lock = Any()
    }

    override fun onCreate() {
        super.onCreate()
        synchronized(lock) {
            if (syncAdapter == null) {
                syncAdapter = ContactsSyncAdapter(applicationContext, true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return syncAdapter?.syncAdapterBinder
    }
}
