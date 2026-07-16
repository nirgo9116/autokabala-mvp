package com.autokabala.listener

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PaymentReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!TermsGate.isAccepted(context)) return Result.success()

        val db = AppDatabase.getDatabase(context)
        val scheduledPaymentDao = db.scheduledPaymentDao()
        val paymentDao = db.paymentDao()
        val clientDao = db.clientDao()

        val now = System.currentTimeMillis()
        val candidates = scheduledPaymentDao.getPaymentsNeedingReminderCandidates()
        Log.d("PaymentReminderWorker", "Candidates: ${candidates.size}")

        for (scheduled in candidates) {
            val serviceTime = scheduled.serviceCompletedTime ?: continue
            val reminderDelayMs = scheduled.reminderHoursAfter * 3_600_000L
            if ((now - serviceTime) < reminderDelayMs) continue

            val window = 86_400_000L
            val existing = paymentDao.getPaymentByClientInRange(
                clientId  = scheduled.clientId,
                startTime = scheduled.scheduledDate - window,
                endTime   = scheduled.scheduledDate + window
            )

            if (existing != null) {
                scheduledPaymentDao.markAsCompleted(scheduled.id)
                Log.d("PaymentReminderWorker", "Completed: ${scheduled.clientName}")
            } else {
                val phone = clientDao.getClientById(scheduled.clientId)?.phone
                if (phone != null) {
                    sendWhatsAppReminder(phone, scheduled)
                    if (scheduled.reminderRecurrenceDays > 0) {
                        val nextServiceTime = now +
                            (scheduled.reminderRecurrenceDays.toLong() * 86_400_000L) -
                            reminderDelayMs
                        scheduledPaymentDao.updateScheduledPayment(
                            scheduled.copy(reminderSent = false, serviceCompletedTime = nextServiceTime)
                        )
                        Log.d("PaymentReminderWorker", "Recurring — next in ${scheduled.reminderRecurrenceDays}d")
                    } else {
                        scheduledPaymentDao.markReminderSent(scheduled.id)
                    }
                } else {
                    Log.w("PaymentReminderWorker", "No phone for ${scheduled.clientName}")
                }
            }
        }
        return Result.success()
    }

    private fun sendWhatsAppReminder(phone: String, payment: ScheduledPaymentEntity) {
        val message = """
            שלום ${payment.clientName},
            רציתי להזכיר לגבי התשלום עבור ${payment.description} שלנו.
            סכום: ₪${payment.amount.toInt()}

            תודה רבה! 🙏
        """.trimIndent()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PaymentReminderWorker", "Failed to open WhatsApp for ${payment.clientName}", e)
        }
    }
}
