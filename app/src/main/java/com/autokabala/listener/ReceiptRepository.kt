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

    data class ReceiptOutcome(val docUrl: String?, val emailSent: Boolean, val docNum: String? = null)

    suspend fun issueReceiptForClient(payment: PaymentEntity, client: ClientEntity, description: String = ""): ReceiptOutcome? {
        val paymentData = PaymentData(
            source = payment.source,
            senderName = payment.senderName,
            amount = payment.amount,
            isConfirmed = payment.isConfirmed,
            timestamp = payment.timestamp
        )
        val doc = ReceiptApiClient.issueReceipt(paymentData, client.id, description) ?: return null
        paymentDao.updatePaymentStatus(payment.id, "processed")
        paymentDao.updatePaymentReceipt(
            id           = payment.id,
            clientId     = client.id,
            clientName   = client.name,
            docNum       = doc.docNum.ifBlank { null },
            docUrl       = doc.docUrl.ifBlank { null },
            issuedAmount = payment.amount
        )
        val emailSent = if (client.autoSend && doc.docNum.isNotBlank()) {
            ReceiptApiClient.sendDocumentByEmail(doc.docNum)
        } else false
        return ReceiptOutcome(docUrl = doc.docUrl.ifBlank { null }, emailSent = emailSent, docNum = doc.docNum.ifBlank { null })
    }

    suspend fun toggleAutoSend(client: ClientEntity) {
        clientDao.updateAutoSend(client.id, !client.autoSend)
    }

    suspend fun fetchAndCachePhone(clientId: String): String? {
        val mobile = ReceiptApiClient.getClientMobile(clientId) ?: return null
        clientDao.updatePhone(clientId, mobile)
        return mobile
    }

    suspend fun createClientAndIssueReceipt(
        payment: PaymentEntity,
        newClientName: String,
        phone: String? = null,
        email: String? = null,
        description: String = ""
    ): String? {
        Log.d("Repository", "Attempting to create client '$newClientName' and issue receipt.")

        when (val result = ReceiptApiClient.createClient(newClientName, phone, email)) {
            is ApiResult.Success -> {
                val newClientId = result.data
                Log.d("Repository", "Client created successfully with ID: $newClientId. Now issuing receipt.")
                val newClient = ClientEntity(id = newClientId.toString(), name = newClientName, email = email, phone = phone)
                clientDao.insertAll(listOf(newClient)) // save locally so history row is clickable immediately
                return issueReceiptForClient(payment, newClient, description)?.docUrl
            }
            is ApiResult.Failure -> {
                Log.e("Repository", "Failed to create new client: ${result.reason}. Aborting receipt issuance.")
                return null
            }
        }
    }

    suspend fun updateClientContact(clientId: String, phone: String?, email: String?) {
        if (phone != null) clientDao.updatePhone(clientId, phone)
        if (email != null) clientDao.updateEmail(clientId, email)
    }

    suspend fun updateClientWhatsAppMessage(clientId: String, message: String) {
        clientDao.updateWhatsAppMessage(clientId, message)
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment.id)
    }

    // --- Test / Debug Functions ---
    suspend fun addFakeReceipt(payment: PaymentEntity): IssuedReceiptInfo {
        val fakeDocNum = "TEST-${(1000..9999).random()}"
        paymentDao.updatePaymentStatus(payment.id, "processed")
        paymentDao.updatePaymentReceipt(
            id           = payment.id,
            clientId     = "fake",
            clientName   = payment.senderName,
            docNum       = fakeDocNum,
            docUrl       = null,
            issuedAmount = payment.amount
        )
        Log.d("Repository", "addFakeReceipt: payment.id=${payment.id} docNum=$fakeDocNum")
        return IssuedReceiptInfo(docUrl = null, clientPhone = null, docNum = fakeDocNum)
    }

    suspend fun addFakePayment() {
        val names = listOf("Danny", "Moshe", "Yossi", "ניר", "סמדר בדיקה", "Elad", "בלהבלה", "ניר")
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

    suspend fun addFakeOverduePayment() {
        val clients = clientDao.getAllClientsSnapshot()
        if (clients.isEmpty()) {
            Log.w("Repository", "No clients — cannot add fake overdue payment")
            return
        }
        val client = clients.random()
        val daysAgo = listOf(8, 12, 20, 36, 50).random()
        val fakePayment = PaymentEntity(
            source = listOf("bit", "paybox").random(),
            senderName = client.name,
            amount = listOf(150.0, 200.0, 250.0, 300.0, 350.0).random(),
            isConfirmed = true,
            timestamp = System.currentTimeMillis() - daysAgo.toLong() * 24 * 3600 * 1000,
            status = "processed",
            clientId = client.id,
            clientName = client.name,
            docNum = "TEST-${(1000..9999).random()}"
        )
        paymentDao.insertPayment(fakePayment)
        Log.d("Repository", "Added fake overdue payment: ${client.name}, $daysAgo days ago")
    }

    // --- Payment History ---
    fun getRecentPayments(since: Long): Flow<List<PaymentEntity>> = paymentDao.getRecentPayments(since)
    fun getPaymentsByClientId(clientId: String): Flow<List<PaymentEntity>> = paymentDao.getPaymentsByClientId(clientId)
    fun getLastPaymentPerClient(): Flow<List<ClientLastPayment>> = paymentDao.getLastPaymentPerClient()

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
