package com.autokabala.listener

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_ID = "new_payment_channel"

    fun sendNewPaymentNotification(context: Context, paymentData: PaymentData) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If permission is not granted, do not proceed.
            // The user needs to grant this from system settings on newer Android versions.
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a real icon
            .setContentTitle("New Payment Detected")
            .setContentText("Received ${paymentData.amount} ILS from ${paymentData.senderName}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            val notificationId = System.currentTimeMillis().toInt()
            notify(notificationId, builder.build())
        }
    }
}
