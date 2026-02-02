package com.autokabala.listener

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The Repository is the single source of truth for all data operations.
 * It now takes both DAOs as dependencies to interact with the database.
 */
class ReceiptRepository(private val paymentDao: PaymentDao, private val clientDao: ClientDao) {

    // --- Pending Payments ---
    val pendingPayments: Flow<List<PaymentEntity>> = paymentDao.getPendingPayments()

    /**
     * Starts listening for new payment events from the ListenerManager.
     * This should be called only once from the Application class.
     */
    fun startListeningForPayments(scope: CoroutineScope) {
        ListenerManager.newPaymentEvent
            .onEach { paymentData ->
                Log.d("Repository", "New payment event received for ${paymentData.senderName}. Saving to DB...")
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
            .launchIn(scope) // Launch the collection in the provided application scope
    }

    suspend fun issueReceipt(payment: PaymentEntity): Boolean {
        val paymentData = PaymentData(
            source = payment.source,
            senderName = payment.senderName,
            amount = payment.amount,
            isConfirmed = payment.isConfirmed,
            timestamp = payment.timestamp,
            rawText = payment.rawText
        )
        val wasSuccessful = ReceiptApiClient.issueReceipt(paymentData)
        if (wasSuccessful) {
            paymentDao.updatePaymentStatus(payment.id, "processed")
        }
        return wasSuccessful
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment.id)
    }

    // --- Clients ---
    val allClients: Flow<List<ClientEntity>> = clientDao.getAllClients()

    /**
     * Fetches the client list from the API and replaces the local data with the new list.
     */
    suspend fun syncClients() {
        Log.d("SyncClients", "Starting client sync...")
        val clientsFromApi = ReceiptApiClient.getClients()

        if (clientsFromApi != null) {
            Log.d("SyncClients", "Successfully fetched ${clientsFromApi.size} clients. Updating database...")
            val clientEntities = clientsFromApi.map { clientData ->
                ClientEntity(
                    id = clientData.id,
                    name = clientData.name,
                    email = clientData.email,
                    phone = clientData.phone
                )
            }
            clientDao.deleteAll()
            clientDao.insertAll(clientEntities)
            Log.d("SyncClients", "Database updated successfully.")
        } else {
            Log.e("SyncClients", "Failed to fetch clients from API.")
        }
    }
}
