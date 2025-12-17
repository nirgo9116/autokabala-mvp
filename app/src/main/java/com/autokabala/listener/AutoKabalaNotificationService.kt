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
        Log.d("AutoKabalaNL", "NOTIFICATION POSTED FROM: ${sbn.packageName}")
    }
}
