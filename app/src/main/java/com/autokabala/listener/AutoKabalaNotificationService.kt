package com.autokabala.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AutoKabalaNotificationService : NotificationListenerService() {

    // MODE A: Bit only (plus emulator test package)
    private val allowedPackages = setOf(
        "il.co.poalim.bit",
        "com.android.shell" // allows ADB test notifications in emulator
    )

    private var lastFingerprint: String? = null
    private var lastTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SERVICE CREATED")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "LISTENER CONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            if (pkg !in allowedPackages) return

            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()

            val full = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString(" | ")
            if (full.isBlank()) return

            // MODE A detection (simple and robust for Hebrew)
            val isPayment = full.contains("התקבלה") || full.contains("קיבלת") || full.contains("תשלום")
            if (!isPayment) {
                Log.d(TAG, "IGNORED (not payment): pkg=$pkg full=$full")
                return
            }

            // Debounce duplicates (same content within 1.5s)
            val fingerprint = "$pkg|$full"
            val now = System.currentTimeMillis()
            if (fingerprint == lastFingerprint && (now - lastTimestamp) < 1500) return
            lastFingerprint = fingerprint
            lastTimestamp = now

            Log.d(TAG, "✅ BIT PAYMENT DETECTED: pkg=$pkg | $full")

            // NEXT STEP (later): parse amount + name and POST to webhook

        } catch (e: Exception) {
            Log.e(TAG, "ERROR in onNotificationPosted: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "AutoKabalaNL"
    }
}
