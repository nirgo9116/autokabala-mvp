package com.autokabala.listener

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Runs nightly at 3:00 AM.
 * Marks clients as active/inactive based on whether they have a receipt
 * in the last 6 months in iCount. This keeps the matching and reminders
 * tabs clean without slowing down the manual sync button.
 */
class ActiveClientsWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "active_clients_nightly"

        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelay = target.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<ActiveClientsWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Log.d("ActiveClientsWorker", "Scheduled — fires in ${initialDelay / 60_000} min")
        }
    }

    override suspend fun doWork(): Result {
        if (!TermsGate.isAccepted(context)) return Result.success()
        if (!ReceiptApiClient.isConfigured()) {
            Log.d("ActiveClientsWorker", "iCount not configured, skipping")
            return Result.success()
        }

        Log.i("ActiveClientsWorker", "Starting nightly active-clients update")
        val fromDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(System.currentTimeMillis() - 180L * 24 * 3600 * 1000))

        val activeIds = ReceiptApiClient.getActiveClientIds(fromDate)
        if (activeIds == null) {
            Log.w("ActiveClientsWorker", "Failed to fetch active IDs, retrying later")
            return Result.retry()
        }

        val db = AppDatabase.getDatabase(context)
        val clientDao = db.clientDao()
        val allClients = clientDao.getAllClientsSnapshot()

        var updated = 0
        allClients.forEach { client ->
            val lastReceipt = activeIds[client.id]
            val shouldBeActive = lastReceipt != null
            if (client.isActive != shouldBeActive) {
                clientDao.updateIsActive(client.id, shouldBeActive)
                updated++
            }
            if (lastReceipt != null && lastReceipt != client.lastReceiptDate) {
                clientDao.updateLastReceiptDate(client.id, lastReceipt)
                updated++
            }
        }
        Log.i("ActiveClientsWorker", "Done — ${activeIds.size} active, $updated fields updated")
        return Result.success()
    }
}
