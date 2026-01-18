package com.autokabala.listener

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// --- Data classes with explicit @SerialName for all fields ---

// Client Search (for the GET call)
@Serializable
data class ClientInfo(
    @SerialName("client_id") val clientId: Long,
    @SerialName("client_name") val clientName: String
)

@Serializable
data class SearchClientResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("records") val records: List<ClientInfo> = emptyList(),
    @SerialName("reason") val reason: String? = null
)

// Client Creation
@Serializable
data class CreateClientRequest(
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("client_name") val clientName: String
)

@Serializable
data class CreateClientResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("reason") val reason: String? = null,
    @SerialName("client_id") val clientId: Long? = null
)

// Document Creation
@Serializable
data class DocumentItem(
    @SerialName("description") val description: String,
    @SerialName("unitprice") val unitPrice: Double,
    @SerialName("quantity") val quantity: Int = 1
)

@Serializable
data class PaymentItem(
    @SerialName("type") val type: Int = 3,
    @SerialName("amount") val amount: Double
)

@Serializable
data class CreateDocumentRequest(
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("doctype") val docType: String = "rec",
    @SerialName("client_id") val clientId: Long,
    @SerialName("items") val items: List<DocumentItem>,
    @SerialName("pays") val pays: List<PaymentItem>
)

@Serializable
data class CreateDocumentResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("reason") val reason: String? = null,
    @SerialName("doc_number") val docNumber: String? = null
)

object ReceiptApiClient {

    private const val BASE_URL = "https://api.icount.co.il/api/v3.php"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { isLenient = true; ignoreUnknownKeys = true })
        }
    }

    private suspend fun getOrCreateClient(senderName: String): Long? {
        val experimentalName = senderName.trim().split(" ").firstOrNull()?.plus(" בדיקה") ?: (senderName.trim() + " בדיקה")
        Log.d("AutoKabalaNL-Action", "--- Starting Get/Create Client with name: '$experimentalName' (Robust Flow) ---")

        // --- Step 1: ALWAYS try to create the client first ---
        try {
            Log.d("AutoKabalaNL-Action", "[1/2] Attempting to create client...")
            val createResponse = client.post("$BASE_URL/client/create") {
                contentType(ContentType.Application.Json)
                setBody(CreateClientRequest(BuildConfig.ICOUNT_CID, BuildConfig.ICOUNT_USER, BuildConfig.ICOUNT_PASS, experimentalName))
            }.body<CreateClientResponse>()

            if (createResponse.status && createResponse.clientId != null) {
                Log.i("AutoKabalaNL-Action", "[1/2] Successfully created new client. ID: ${createResponse.clientId}")
                return createResponse.clientId
            }

            if (createResponse.reason == "client_already_exists") {
                Log.d("AutoKabalaNL-Action", "[1/2] Client already exists. Proceeding to fetch existing client ID.")
                // Fall through to step 2
            } else {
                Log.e("AutoKabalaNL-Action", "[1/2] Failed to create client for a new reason: ${createResponse.reason}. Aborting.")
                return null
            }
        } catch (e: Exception) {
            Log.e("AutoKabalaNL-Action", "[1/2] Exception during client creation. Aborting.", e)
            return null
        }

        // --- Step 2: If client already exists, FETCH it to get the ID ---
        try {
            Log.d("AutoKabalaNL-Action", "[2/2] Fetching existing client via GET...")
            // Using /client/get which is more standard for fetching by name
            val searchResponse = client.get("$BASE_URL/client/get") { // FIXED ENDPOINT
                url {
                    parameters.append("cid", BuildConfig.ICOUNT_CID)
                    parameters.append("user", BuildConfig.ICOUNT_USER)
                    parameters.append("pass", BuildConfig.ICOUNT_PASS)
                    parameters.append("search", experimentalName)
                }
            }.body<SearchClientResponse>()

            if (searchResponse.status && searchResponse.records.isNotEmpty()) {
                val bestMatch = searchResponse.records.first()
                Log.i("AutoKabalaNL-Action", "[2/2] Successfully fetched existing client. ID: ${bestMatch.clientId}")
                return bestMatch.clientId
            } else {
                Log.e("AutoKabalaNL-Action", "[2/2] Failed to fetch existing client after creation failed. Reason: ${searchResponse.reason}")
                return null
            }
        } catch (e: Exception) {
            Log.e("AutoKabalaNL-Action", "[2/2] Exception during client fetch. Aborting.", e)
            return null
        }
    }

    suspend fun issueReceipt(paymentData: PaymentData) {
        Log.i("AutoKabalaNL-Action", "--- Starting Document Issuance for: $paymentData ---")

        val clientId = getOrCreateClient(paymentData.senderName)
        if (clientId == null) {
            Log.e("AutoKabalaNL-Action", "Could not get or create client ID for sender: '${paymentData.senderName}'. See previous logs. Aborting.")
            return
        }

        try {
            val requestBody = CreateDocumentRequest(
                cid = BuildConfig.ICOUNT_CID,
                user = BuildConfig.ICOUNT_USER,
                pass = BuildConfig.ICOUNT_PASS,
                clientId = clientId,
                items = listOf(DocumentItem("קבלה עבור ${paymentData.senderName} דרך ${paymentData.source}", paymentData.amount)),
                pays = listOf(PaymentItem(amount = paymentData.amount))
            )

            Log.d("AutoKabalaNL-Action", "Sending request to /doc/create with body: $requestBody")
            val response = client.post("$BASE_URL/doc/create") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val createResponse = response.body<CreateDocumentResponse>()
            Log.d("AutoKabalaNL-Action", "Received response from /doc/create: $createResponse")

            if (createResponse.status) {
                Log.i("AutoKabalaNL-Action", "##### SUCCESS! Issued document number: ${createResponse.docNumber} for client ID: $clientId #####")
            } else {
                Log.e("AutoKabalaNL-Action", "##### FAILED to issue document: ${createResponse.reason} #####")
            }
        } catch (e: Exception) {
            Log.e("AutoKabalaNL-Action", "##### EXCEPTION during document creation #####", e)
        }
    }
}
