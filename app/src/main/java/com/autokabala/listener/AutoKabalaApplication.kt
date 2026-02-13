package com.autokabala.listener

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoKabalaApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this) }
    val receiptRepository by lazy { ReceiptRepository(database.paymentDao(), database.clientDao()) } // Removed context

    override fun onCreate() {
        super.onCreate()
        receiptRepository.startListeningForPayments(applicationScope)

        applicationScope.launch {
            receiptRepository.syncClients()
        }

        // Create notification channel
        val name = "New Payments"
        val descriptionText = "Notifications for newly detected payments"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("new_payment_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager:
                NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
