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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AutoKabalaNotificationService : NotificationListenerService() {

    companion object {
        private val PAYMENT_PACKAGES = setOf(
            "com.bnhp.payments.paymentsapp",  // Bit
            "com.payboxapp",                   // Paybox
            "com.paybox.android"               // Paybox (alternative package)
        )
        private const val POLL_INTERVAL_MS = 1500L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPkg: String? = null
    private val httpClient = OkHttpClient()

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
            if (current != lastForegroundPkg) {
                Log.d("AutoKabalaNL", "Payment app opened: $current — showing bubble")
            }
            lastForegroundPkg = current
            val bubbleEnabled = getSharedPreferences(BubbleService.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(BubbleService.KEY_BUBBLE_ENABLED, true)
            if (!bubbleEnabled) return
            BubbleService.show(this)
        } else {
            if (current == lastForegroundPkg) return
            lastForegroundPkg = current
            if (current != null) {
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
        if (packageName !in PAYMENT_PACKAGES) return

        val extras = sbn.notification.extras
        val rawText = extras.getString("android.text") ?: return
        val timestamp = sbn.postTime

        // Send raw text to backend for Claude-based extraction
        sendToBackend(rawText)

        // Also parse locally as fallback
        val paymentData = PaymentParser.parse(packageName, rawText, timestamp)
        if (paymentData != null) {
            Log.d("AutoKabalaNL", "Parsed payment locally: $paymentData")
            serviceScope.launch {
                ListenerManager.onPaymentParsed(paymentData)
            }
            showConfirmationNotification(paymentData)
        } else {
            Log.w("AutoKabalaNL", "Failed to parse notification from $packageName: $rawText")
        }
    }

    private fun sendToBackend(notificationText: String) {
        val json = "{\"text\":\"$notificationText\"}"
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.BACKEND_URL}/extract-payment")
            .post(requestBody)
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AutoKabalaNL", "Failed to send to backend", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.body?.close()
            }
        })
    }

    private fun showConfirmationNotification(paymentData: PaymentData) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, "new_payment_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("תשלום התקבל")
            .setContentText("מ-${paymentData.senderName}: ₪${paymentData.amount.toInt()}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(this)) {
            notify((paymentData.timestamp % Int.MAX_VALUE).toInt(), builder.build())
        }
    }
}
