package com.autokabala.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AutoKabalaNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val rawText = listOfNotNull(title, text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" | ")

        if (rawText.isNotBlank()) {
            Log.d("AutoKabalaNL", "NOTIF pkg=${sbn.packageName} | $rawText")
            ListenerManager.onNotificationReceived(rawText)
        }
    }

    override fun onListenerConnected() {
        Log.d("AutoKabalaNL", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.d("AutoKabalaNL", "Notification listener disconnected")
    }
}
