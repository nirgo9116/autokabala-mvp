package com.autokabala.listener

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
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
            showConfirmationNotification(paymentData)
        } else {
            if (packageName in setOf("com.bnhp.payments.paymentsapp", "com.payboxapp")) {
                Log.w("AutoKabalaNL", "Failed to parse notification from $packageName: $rawText")
            }
        }
    }

    private fun showConfirmationNotification(paymentData: PaymentData) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, "new_payment_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Payment Detected")
            .setContentText("From: ${paymentData.senderName}, Amount: ${paymentData.amount}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    override fun onListenerConnected() {
        Log.d("AutoKabalaNL", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.d("AutoKabalaNL", "Notification listener disconnected")
    }
}
