package com.autokabala.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoKabalaNotificationService : NotificationListenerService() {

    // Create a coroutine scope for this service.
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras ?: return

        val timestamp = sbn.postTime

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val rawText = listOf(title, text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" | ")

        if (rawText.isBlank()) return

        val paymentData = PaymentParser.parse(packageName, rawText, timestamp)

        if (paymentData != null) {
            Log.d("AutoKabalaNL", "Successfully parsed payment: $paymentData")
            // Launch a coroutine to call the suspend function.
            serviceScope.launch {
                ListenerManager.onPaymentParsed(paymentData)
            }
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
