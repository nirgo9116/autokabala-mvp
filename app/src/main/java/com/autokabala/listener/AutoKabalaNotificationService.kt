package com.autokabala.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AutoKabalaNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras ?: return

        // Get the timestamp from the notification object
        val timestamp = sbn.postTime

        // Safely extract text fields, providing an empty string as a default to prevent null pointer crashes.
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val rawText = listOf(title, text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" | ")

        if (rawText.isBlank()) return

        // Use the new parser to process the notification, now with the timestamp
        val paymentData = PaymentParser.parse(packageName, rawText, timestamp)

        if (paymentData != null) {
            Log.d("AutoKabalaNL", "Successfully parsed payment: $paymentData")
            ListenerManager.onPaymentParsed(paymentData)
        } else {
            if (packageName in setOf("com.bnhp.payments.paymentsapp", "com.payboxapp")) {
                Log.w("AutoKabalaNL", "Failed to parse notification from $packageName: $rawText")
            }
        }
    }

    override fun onListenerConnected() {
        Log.d("AutoKabalaNL", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.d("AutoKabalaNL", "Notification listener disconnected")
    }
}
