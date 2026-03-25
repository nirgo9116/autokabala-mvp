package com.autokabala.listener

import android.app.AppOpsManager
import android.app.Notification
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AutoKabalaNotificationService : NotificationListenerService() {

    companion object {
        private val PAYMENT_PACKAGES = setOf(
            "com.bnhp.payments.paymentsapp",  // Bit
            "com.payboxapp"                    // Paybox
        )
        private const val POLL_INTERVAL_MS = 1500L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPkg: String? = null

    private val foregroundPoller = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onListenerConnected() {
        Log.d("AutoKabalaNL", "Notification listener connected")
        handler.post(foregroundPoller)
    }

    override fun onListenerDisconnected() {
        Log.d("AutoKabalaNL", "Notification listener disconnected")
        handler.removeCallbacks(foregroundPoller)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(foregroundPoller)
        serviceScope.cancel()
    }

    private fun checkForegroundApp() {
        if (!hasUsageStatsPermission()) return
        if (!Settings.canDrawOverlays(this)) return

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - POLL_INTERVAL_MS * 2, now)
        val event = UsageEvents.Event()
        var current: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                current = event.packageName
            }
        }

        if (current in PAYMENT_PACKAGES) {
            // Always re-show: covers fast app-switcher round-trips where lastForegroundPkg
            // never changed but the system hid the overlay while in the recents screen.
            if (current != lastForegroundPkg) {
                Log.d("AutoKabalaNL", "Payment app opened: $current — showing bubble")
            }
            lastForegroundPkg = current
            BubbleService.show(this)
        } else {
            if (current == lastForegroundPkg) return
            lastForegroundPkg = current
            if (current != null) {
                // User switched to a different app — hide the bubble
                BubbleService.hide(this)
            }
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

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
            serviceScope.launch {
                ListenerManager.onPaymentParsed(paymentData)
            }
            Log.d("AutoKabalaNL", "Attempting to show confirmation notification...")
            showConfirmationNotification(paymentData)
        } else {
            if (packageName in PAYMENT_PACKAGES) {
                Log.w("AutoKabalaNL", "Failed to parse notification from $packageName: $rawText")
            }
        }
    }

    private fun showConfirmationNotification(paymentData: PaymentData) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            val notificationId = (paymentData.timestamp % Int.MAX_VALUE).toInt()
            notify(notificationId, builder.build())
        }
    }
}
