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
                    timestamp = paymentData.timestamp
                )
                paymentDao.insertPayment(paymentEntity)
            }
            .launchIn(scope)
    }

    suspend fun issueReceiptForClient(payment: PaymentEntity, client: ClientEntity): String? {
        val paymentData = PaymentData(
            source = payment.source,
            senderName = payment.senderName,
            amount = payment.amount,
            isConfirmed = payment.isConfirmed,
            timestamp = payment.timestamp
        )
        val email = if (client.autoSend) client.email else null
        val docUrl = ReceiptApiClient.issueReceipt(paymentData, client.id, email)
        if (docUrl != null) {
            paymentDao.updatePaymentStatus(payment.id, "processed")
        }
        return docUrl
    }

    suspend fun toggleAutoSend(client: ClientEntity) {
        clientDao.updateAutoSend(client.id, !client.autoSend)
    }

    suspend fun fetchAndCachePhone(clientId: String): String? {
        val mobile = ReceiptApiClient.getClientMobile(clientId) ?: return null
        clientDao.updatePhone(clientId, mobile)
        return mobile
    }

    suspend fun createClientAndIssueReceipt(payment: PaymentEntity, newClientName: String): String? {
        Log.d("Repository", "Attempting to create client '$newClientName' and issue receipt.")

        when (val result = ReceiptApiClient.createClient(newClientName)) {
            is ApiResult.Success -> {
                val newClientId = result.data
                Log.d("Repository", "Client created successfully with ID: $newClientId. Now issuing receipt.")
                val newClient = ClientEntity(id = newClientId.toString(), name = newClientName, email = null, phone = null)
                return issueReceiptForClient(payment, newClient)
            }
            is ApiResult.Failure -> {
                Log.e("Repository", "Failed to create new client: ${result.reason}. Aborting receipt issuance.")
                return null
            }
        }
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment.id)
    }

    // --- Test Function ---
    suspend fun addFakePayment() {
        val names = listOf("Danny", "Moshe", "Yossi", "ניר", "סמדר בדיקה", "Elad", "בלהבלה", "ניר")
        val randomName = names.random()
        val fakePayment = PaymentEntity(
            source = "bit",
            senderName = randomName,
            amount = 1.0, // Set to 1 NIS for easier testing
            isConfirmed = true,
            timestamp = System.currentTimeMillis()
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
                    phone = clientData.phone ?: clientData.mobile
                )
            }
            // Use the new, atomic sync function
            clientDao.syncAll(clientEntities)
            Log.d("SyncClients", "Database updated successfully using atomic transaction.")
        } else {
            Log.e("SyncClients", "Failed to fetch clients from API.")
        }
    }
}
