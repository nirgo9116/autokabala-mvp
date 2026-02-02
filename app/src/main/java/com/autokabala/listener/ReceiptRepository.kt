package com.autokabala.listener

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The Repository is the single source of truth for all data operations.
 * It abstracts the data sources (network, database, etc.) from the rest of the app.
 * It now takes the PaymentDao as a dependency to interact with the database.
 */
class ReceiptRepository(private val paymentDao: PaymentDao) {

    // Expose the flow of pending payments directly from the DAO.
    val pendingPayments: Flow<List<PaymentEntity>> = paymentDao.getPendingPayments()

    init {
        // Listen for new payment events from the ListenerManager and insert them into the database.
        CoroutineScope(Dispatchers.IO).launch {
            ListenerManager.newPaymentEvent.collect { paymentData ->
                val paymentEntity = PaymentEntity(
                    source = paymentData.source,
                    senderName = paymentData.senderName,
                    amount = paymentData.amount,
                    isConfirmed = paymentData.isConfirmed,
                    timestamp = paymentData.timestamp,
                    rawText = paymentData.rawText
                )
                paymentDao.insertPayment(paymentEntity)
            }
        }
    }

    /**
     * Issues a receipt by calling the remote API client.
     * After a successful API call, it should update or delete the payment from the database.
     */
    suspend fun issueReceipt(payment: PaymentEntity) {
        val paymentData = PaymentData(
            source = payment.source,
            senderName = payment.senderName,
            amount = payment.amount,
            isConfirmed = payment.isConfirmed,
            timestamp = payment.timestamp,
            rawText = payment.rawText
        )

        // This part remains the same, for now.
        ReceiptApiClient.issueReceipt(paymentData)

        // After issuing, we update the status to "processed".
        paymentDao.updatePaymentStatus(payment.id, "processed")
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment.id)
    }
}
