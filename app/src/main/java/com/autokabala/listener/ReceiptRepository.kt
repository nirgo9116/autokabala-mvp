package com.autokabala.listener

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

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
                    timestamp = paymentData.timestamp
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
            timestamp = payment.timestamp
        )
        return withContext(Dispatchers.IO) {
            val wasSuccessful = ReceiptApiClient.issueReceipt(paymentData, clientId)
            if (wasSuccessful) {
                paymentDao.updatePaymentStatus(payment.id, "processed")
            }
            wasSuccessful
        }
    }

    suspend fun createClientAndIssueReceipt(payment: PaymentEntity): Boolean {
        Log.d("CreateAndIssue", "Starting process for payment from: ${payment.senderName}")
        return withContext(Dispatchers.IO) {
            val newClientId = ReceiptApiClient.createClient(payment.senderName)
            if (newClientId != null) {
                Log.i("CreateAndIssue", "Successfully created new client with ID: $newClientId")
                val issueSuccess = issueReceiptForClient(payment, newClientId)
                Log.d("CreateAndIssue", "Receipt issue success: $issueSuccess")
                if(issueSuccess) {
                    syncClients()
                }
                issueSuccess
            } else {
                Log.e("CreateAndIssue", "Failed to create new client for: ${payment.senderName}")
                false
            }
        }
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment.id)
    }

    suspend fun addFakePayment() {
        val names = listOf("Danny", "Moshe", "Yossi", "ניר", "סמדר בדיקה", "Elad")
        val randomName = names.random()
        val fakePayment = PaymentEntity(
            source = "bit",
            senderName = randomName,
            amount = 1.0,
            isConfirmed = true,
            timestamp = System.currentTimeMillis()
        )
        paymentDao.insertPayment(fakePayment)
        Log.d("Repository", "Added fake payment for $randomName")
    }

    val allClients: Flow<List<ClientEntity>> = clientDao.getAllClients()

    suspend fun syncClients(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SyncClients", "Starting client sync on IO thread...")
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
                    true
                } else {
                    Log.e("SyncClients", "Failed to fetch clients from API. Check AutoKabalaAPI logs for details.")
                    false
                }
            } catch (e: Exception) {
                Log.e("SyncClients", "An exception occurred during client sync", e)
                false
            }
        }
    }
}
