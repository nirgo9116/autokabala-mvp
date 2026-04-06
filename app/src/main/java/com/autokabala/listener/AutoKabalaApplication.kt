package com.autokabala.listener

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AutoKabalaApplication : Application() {

    // Carries captured screen URIs from BubbleService → MainActivity
    val pendingCaptureFlow = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this) }
    val receiptRepository by lazy { ReceiptRepository(database.paymentDao(), database.clientDao()) }
    val calendarRepository by lazy { CalendarRepository(this, database.calendarEventDao()) }
    val scheduledPaymentDao by lazy { database.scheduledPaymentDao() }

    override fun onCreate() {
        super.onCreate()
        publishShareShortcut()
        ReceiptApiClient.configure(this)
        receiptRepository.startListeningForPayments(applicationScope)

        if (ReceiptApiClient.hasCredentials()) {
            applicationScope.launch {
                receiptRepository.syncClients()
            }
        }

        val reminderWork = PeriodicWorkRequestBuilder<PaymentReminderWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("payment_reminders", ExistingPeriodicWorkPolicy.KEEP, reminderWork)

        DailySessionReminderWorker.schedule(this)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel("new_payment_channel", "New Payments", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /**
     * Publishes a Direct Share shortcut so AutoKabala appears in the top row of the
     * system share sheet when sharing images from Bit / Paybox.
     * The shortcut is long-lived so the system can promote it even after app restart.
     */
    private fun publishShareShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, "autokabala_share_target")
            .setShortLabel("AutoKabala")
            .setLongLabel("הפק קבלה")
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setPerson(Person.Builder().setName("AutoKabala").build())
            .setIntent(Intent(this, ShareHandlerActivity::class.java).apply {
                action = Intent.ACTION_SEND
            })
            .setCategories(setOf("com.autokabala.listener.SHARE_TARGET"))
            .setLongLived(true)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }
}
