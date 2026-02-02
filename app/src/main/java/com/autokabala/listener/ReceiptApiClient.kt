package com.autokabala.listener

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// --- Data classes for Document Creation ---

@Serializable
data class DocumentItem(
    @SerialName("description") val description: String,
    @SerialName("unitprice") val unitPrice: Double,
    @SerialName("quantity") val quantity: Int = 1
)

@Serializable
data class CashPayment(
    @SerialName("sum") val sum: Double
)

@Serializable
data class CreateDocumentRequest(
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("doctype") val docType: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("email") val email: String? = null,
    @SerialName("currency_code") val currencyCode: String? = "ILS",
    @SerialName("items") val items: List<DocumentItem>,
    @SerialName("cash") val cash: CashPayment
)

@Serializable
data class CreateDocumentResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("reason") val reason: String? = null,
    @SerialName("doc_number") val docNumber: String? = null
)

// --- Data classes for Client List (Corrected based on JSON structure) ---

@Serializable
data class GetClientsRequest(
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("sid") val sid: String = "",
    @SerialName("list_type") val listType: String = "array"
)

@Serializable
data class ClientData(
    @SerialName("client_id") val id: String,
    @SerialName("client_name") val name: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null
)

@Serializable
data class GetClientsResponse(
    @SerialName("status") val status: Boolean,
    // The API returns an object (Map) of clients, not a direct list.
    @SerialName("clients") val clients: Map<String, ClientData>? = null,
    @SerialName("error") val error: String? = null
)

object ReceiptApiClient {

    private const val BASE_URL = "https://api.icount.co.il/api/v3.php"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { isLenient = true; ignoreUnknownKeys = true })
        }
    }

    suspend fun issueReceipt(paymentData: PaymentData): Boolean {
        // This function is temporarily out of focus, but remains for future use.
        return true // Placeholder
    }

    /**
     * Fetches the client list from the API, correctly parsing the Map structure, and returns a List.
     */
    suspend fun getClients(): List<ClientData>? {
        Log.i("AutoKabalaAPI", "--- Fetching client list ---")
        return try {
            val requestBody = GetClientsRequest(
                cid = BuildConfig.ICOUNT_CID,
                user = BuildConfig.ICOUNT_USER,
                pass = BuildConfig.ICOUNT_PASS
            )
            val response = client.post("$BASE_URL/client/get_list") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val getClientsResponse = response.body<GetClientsResponse>()

            if (getClientsResponse.status && getClientsResponse.clients != null) {
                // Convert the map's values to a list, which is what the app expects.
                val clientList = getClientsResponse.clients.values.toList()
                Log.i("AutoKabalaAPI", "Successfully fetched and parsed ${clientList.size} clients.")
                clientList
            } else {
                Log.e("AutoKabalaAPI", "Failed to fetch clients: ${getClientsResponse.error}")
                null
            }
        } catch (e: Exception) {
            Log.e("AutoKabalaAPI", "Exception while fetching clients", e)
            null
        }
    }
}
