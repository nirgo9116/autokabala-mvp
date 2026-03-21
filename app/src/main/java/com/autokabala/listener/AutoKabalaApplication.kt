package com.autokabala.listener

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AutoKabalaApplication : Application() {

    // Create a custom application-level CoroutineScope
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and repository are only created when they're first needed.
    val database by lazy { AppDatabase.getDatabase(this) }
    val receiptRepository by lazy { ReceiptRepository(database.paymentDao(), database.clientDao()) }
    val calendarRepository by lazy { CalendarRepository(this, database.calendarEventDao()) }
    val scheduledPaymentDao by lazy { database.scheduledPaymentDao() }

    override fun onCreate() {
        super.onCreate()
        // Start listening for payment events in a safe, application-wide scope.
        receiptRepository.startListeningForPayments(applicationScope)

        // Also, sync the clients on startup
        applicationScope.launch {
            receiptRepository.syncClients()
        }

        // Schedule periodic payment reminders (every 6 hours, requires network)
        val reminderWork = PeriodicWorkRequestBuilder<PaymentReminderWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "payment_reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderWork
            )

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
