package com.autokabala.listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailySessionReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "daily_session_reminder"
        const val NOTIF_ID   = 2001
        const val WORK_NAME  = "daily_session_check"

        fun schedule(context: Context) {
            val now    = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelay = target.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<DailySessionReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Log.d("DailySessionWorker", "Scheduled — fires in ${initialDelay / 60_000} min")
        }
    }

    override suspend fun doWork(): Result {
        val db         = AppDatabase.getDatabase(context)
        val dao        = db.scheduledPaymentDao()
        val paymentDao = db.paymentDao()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + 86_400_000L

        val todaysSessions = dao.getSessionsInRange(startOfDay, endOfDay)
        val unpaid = todaysSessions.filter { session ->
            !session.completed && paymentDao.getPaymentByClientInRange(
                clientId  = session.clientId,
                startTime = session.scheduledDate - 86_400_000L,
                endTime   = session.scheduledDate + 86_400_000L
            ) == null
        }

        Log.d("DailySessionWorker", "Unpaid today: ${unpaid.size}")
        if (unpaid.isNotEmpty()) showNotification(unpaid.size)
        return Result.success()
    }

    private fun showNotification(count: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "תזכורת פגישות יומית", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { putExtra("open_tab", "scheduled") }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (count == 1) "פגישה אחת מהיום ללא תשלום — לסמן?" else "$count פגישות מהיום ללא תשלום — לסמן?"
        manager.notify(NOTIF_ID, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💰 AutoKabala")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build())
    }
}
