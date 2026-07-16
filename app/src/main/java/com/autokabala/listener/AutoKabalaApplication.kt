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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
        ReceiptApiClient.appContext = this
        publishShareShortcut()
        receiptRepository.startListeningForPayments(applicationScope)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel("new_payment_channel", "New Payments", NotificationManager.IMPORTANCE_DEFAULT)
        )

        if (TermsGate.isAccepted(this)) {
            startPostTermsWork()
        }
    }

    /**
     * Kicks off client sync + reminder/active-clients scheduling. Runs at process start if
     * terms were already accepted, and again immediately from [TermsScreen]'s onAccept so the
     * user doesn't have to wait for the next process restart.
     */
    fun startPostTermsWork() {
        applicationScope.launch {
            receiptRepository.syncClients()
        }

        val reminderWork = PeriodicWorkRequestBuilder<PaymentReminderWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("payment_reminders", ExistingPeriodicWorkPolicy.KEEP, reminderWork)

        DailySessionReminderWorker.schedule(this)
        ActiveClientsWorker.schedule(this)

        // Run once immediately to fix isActive flags (especially after first install or stale data)
        val immediateWork = OneTimeWorkRequestBuilder<ActiveClientsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniqueWork("active_clients_once", ExistingWorkPolicy.KEEP, immediateWork)
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
