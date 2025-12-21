package com.autokabala.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AutoKabalaNotificationService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("AutoKabalaNL", "SERVICE CREATED")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("AutoKabalaNL", "LISTENER CONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")
        val text = extras.getCharSequence("android.text")

        Log.d(
            "AutoKabalaNL",
            "NOTIF pkg=$pkg | title=$title | text=$text"
        )
    }
}
