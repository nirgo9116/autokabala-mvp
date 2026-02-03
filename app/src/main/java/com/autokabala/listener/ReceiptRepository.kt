package com.autokabala.listener

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ReceiptRepository(private val paymentDao: PaymentDao, private val clientDao: ClientDao) {

    val pendingPayments: Flow<List<PaymentEntity>> = paymentDao.getPendingPayments()

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
            .launchIn(scope)
    }

    suspend fun issueReceiptForClient(payment: PaymentEntity, clientId: String): Boolean {
        val paymentData = PaymentData(
            source = payment.source,
            senderName = payment.senderName,
            amount = payment.amount,
            isConfirmed = payment.isConfirmed,
            timestamp = payment.timestamp,
            rawText = payment.rawText
        )
        val wasSuccessful = ReceiptApiClient.issueReceipt(paymentData, clientId)
        if (wasSuccessful) {
            paymentDao.updatePaymentStatus(payment.id, "processed")
        }
        return wasSuccessful
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment.id)
    }

    // --- Test Function ---
    suspend fun addFakePayment() {
        val names = listOf("Danny", "Moshe", "Yossi", "ניר", "סמדר בדיקה", "Elad")
        val randomName = names.random()
        val fakePayment = PaymentEntity(
            source = "bit",
            senderName = randomName,
            amount = 1.0, // Set to 1 NIS for easier testing
            isConfirmed = true,
            timestamp = System.currentTimeMillis(),
            rawText = "Fake payment for testing"
        )
        paymentDao.insertPayment(fakePayment)
        Log.d("Repository", "Added fake payment for $randomName")
    }

    // --- Clients ---
    val allClients: Flow<List<ClientEntity>> = clientDao.getAllClients()

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
