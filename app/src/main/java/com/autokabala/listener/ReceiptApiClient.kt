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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
data class CreateDocumentWithClientIdRequest(
    @SerialName("sid") val sid: String = "",
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("doctype") val docType: String,
    @SerialName("client_id") val clientId: Int,
    @SerialName("currency_code") val currencyCode: String? = "ILS",
    @SerialName("doc_date") val docDate: String? = null,
    @SerialName("items") val items: List<DocumentItem>,
    @SerialName("cash") val cash: CashPayment
)

data class IssuedDocument(val docUrl: String, val docNum: String)

@Serializable
data class SendEmailRequest(
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("doctype") val docType: String,
    @SerialName("docnum") val docNum: Int,
    @SerialName("email_to_client") val emailToClient: Boolean
)

@Serializable
data class SendEmailResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("error") val error: String? = null
)

@Serializable
data class CreateDocumentResponse(
    @SerialName("status")       val status: Boolean,
    @SerialName("error")        val error: String? = null,
    @SerialName("docnum")       val docNum: String? = null,
    @SerialName("doc_url")      val docUrl: String? = null,
    @SerialName("doc_copy_url") val docCopyUrl: String? = null
)

// --- Data classes for Client Creation ---
@Serializable
data class CreateClientRequest(
    @SerialName("sid") val sid: String = "",
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("phone")  val phone:  String? = null,
    @SerialName("mobile") val mobile: String? = null,
    @SerialName("email")  val email:  String? = null
)

@Serializable
data class CreateClientResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("client_id") val clientId: Int? = null,
    @SerialName("data") val data: ClientIdWrapper? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class ClientIdWrapper(
    @SerialName("client_id") val clientId: Int
)

// --- Data classes for Client List ---

@Serializable
data class GetClientsRequest(
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("sid") val sid: String = "",
    @SerialName("list_type") val listType: String = "array",
    @SerialName("detail_level") val detailLevel: Int = 10,
    @SerialName("client_id") val clientId: Int? = null
)

@Serializable
data class ClientData(
    @SerialName("client_id") val id: String,
    @SerialName("client_name") val name: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("mobile") val mobile: String? = null
)

@Serializable
data class GetClientsResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("clients") val clients: Map<String, ClientData>? = null,
    @SerialName("error") val error: String? = null
)

// --- Data classes for Document List ---

@Serializable
data class DocSearchRequest(
    @SerialName("cid") val cid: String,
    @SerialName("user") val user: String,
    @SerialName("pass") val pass: String,
    @SerialName("sid") val sid: String = "",
    @SerialName("doctype") val docType: String = "receipt",
    @SerialName("start_date") val startDate: String,
    @SerialName("detail_level") val detailLevel: Int = 1,
    @SerialName("limit") val limit: Int = 100,
    @SerialName("offset") val offset: Int = 0
)

@Serializable
data class DocSearchItem(
    @SerialName("client_id") val clientId: Int? = null
)

@Serializable
data class DocSearchResponse(
    @SerialName("status") val status: Boolean,
    @SerialName("docs") val docs: Map<String, DocSearchItem>? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("results_count") val resultsCount: Int? = null
)

// --- Data classes for Client Info ---

@Serializable
data class ClientInfoData(
    @SerialName("mobile") val mobile: String? = null,
    @SerialName("phone")  val phone:  String? = null
)

@Serializable
data class ClientInfoResponse(
    @SerialName("status")      val status:     Boolean,
    @SerialName("client_info") val clientInfo: ClientInfoData? = null
)

// API RESULT WRAPPER
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Failure(val reason: String) : ApiResult<Nothing>()
}

object ReceiptApiClient {

    private const val BASE_URL = "https://api.icount.co.il/api/v3.php"

    // SharedPreferences keys (same prefs file as BubbleService)
    const val KEY_CID  = "icount_cid"
    const val KEY_USER = "icount_user"
    const val KEY_PASS = "icount_pass"

    lateinit var appContext: android.content.Context

    private val prefs get() = appContext.getSharedPreferences(BubbleService.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    val cid:  String get() = prefs.getString(KEY_CID,  "") ?.takeIf { it.isNotBlank() } ?: ""
    val user: String get() = prefs.getString(KEY_USER, "") ?.takeIf { it.isNotBlank() } ?: ""
    val pass: String get() = prefs.getString(KEY_PASS, "") ?.takeIf { it.isNotBlank() } ?: ""

    fun saveCredentials(cid: String, user: String, pass: String) {
        prefs.edit().putString(KEY_CID, cid).putString(KEY_USER, user).putString(KEY_PASS, pass).apply()
    }

    fun isConfigured(): Boolean = cid.isNotBlank() && user.isNotBlank() && pass.isNotBlank()

    /** Returns null on success, or an error message string on failure. */
    suspend fun testConnection(): String? {
        if (!isConfigured()) return "יש להזין פרטי iCount בהגדרות"
        return try {
            val requestBody = GetClientsRequest(cid = cid, user = user, pass = pass)
            val response = client.post("$BASE_URL/client/get_list") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val parsed = Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromString<GetClientsResponse>(response.body<String>())
            if (parsed.status) null else "שגיאה מ-iCount: ${parsed.error ?: "תגובה לא תקינה"}"
        } catch (e: Exception) {
            "בעיית חיבור: ${e.message}"
        }
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { isLenient = true; ignoreUnknownKeys = true })
        }
    }

    suspend fun issueReceipt(paymentData: PaymentData, clientId: String, description: String = ""): IssuedDocument? {
        Log.i("AutoKabalaAPI", "--- Starting Document Issuance for client ID: $clientId ---")
        return try {
            val requestBody = CreateDocumentWithClientIdRequest(
                cid = cid,
                user = user,
                pass = pass,
                docType = "receipt",
                clientId = clientId.toInt(),
                currencyCode = "ILS",
                items = listOf(DocumentItem(
                    description.ifBlank { "קבלה עבור ${paymentData.senderName}" },
                    paymentData.amount
                )),
                cash = CashPayment(sum = paymentData.amount)
            )
            val response = client.post("$BASE_URL/doc/create") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val createResponse = response.body<CreateDocumentResponse>()
            if (createResponse.status) {
                Log.i("AutoKabalaAPI", "##### SUCCESS! doc=${createResponse.docNum} url=${createResponse.docUrl} #####")
                IssuedDocument(docUrl = createResponse.docUrl ?: "", docNum = createResponse.docNum ?: "")
            } else {
                Log.e("AutoKabalaAPI", "##### FAILED to issue document: ${createResponse.error} #####")
                null
            }
        } catch (e: Exception) {
            Log.e("AutoKabalaAPI", "##### EXCEPTION during document creation: ${e.message} #####", e)
            null
        }
    }

    suspend fun sendDocumentByEmail(docNum: String): Boolean {
        val num = docNum.toIntOrNull() ?: return false
        return try {
            val requestBody = SendEmailRequest(
                cid = cid,
                user = user,
                pass = pass,
                docType = "receipt",
                docNum = num,
                emailToClient = true
            )
            val response = client.post("$BASE_URL/doc/email") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val rawBody = response.body<String>()
            val result = Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromString<SendEmailResponse>(rawBody)
            Log.i("AutoKabalaAPI", "sendDocumentByEmail($docNum) status=${result.status} error=${result.error}")
            result.status
        } catch (e: Exception) {
            Log.w("AutoKabalaAPI", "sendDocumentByEmail failed", e)
            false
        }
    }

    suspend fun getClients(): List<ClientData>? {
        Log.i("AutoKabalaAPI", "--- Fetching client list ---")
        return try {
            val requestBody = GetClientsRequest(
                cid = cid,
                user = user,
                pass = pass
            )

            val response = client.post("$BASE_URL/client/get_list") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val rawBody = response.body<String>()

            val parsed = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString<GetClientsResponse>(rawBody)

            if (parsed.status && parsed.clients != null) {
                val clients = parsed.clients.values.toList()
                Log.i("AutoKabalaAPI", "Fetched ${clients.size} clients successfully.")

                clients
            } else {
                Log.e("AutoKabalaAPI", "API returned error: ${parsed.error}")
                null
            }
        } catch (e: Exception) {
            Log.e(
                "AutoKabalaAPI",
                "getClients failed BEFORE business logic. This is a parsing or transport issue.",
                e
            )
            null
        }
    }

    suspend fun createClient(clientName: String, phone: String? = null, email: String? = null): ApiResult<Int> {
        Log.i("AutoKabalaAPI", "--- Creating new client: $clientName ---")
        return try {
            val requestBody = CreateClientRequest(
                cid = cid,
                user = user,
                pass = pass,
                clientName = clientName,
                phone = phone,
                email = email
            )
            val response = client.post("$BASE_URL/client/create") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val createResponse = response.body<CreateClientResponse>()

            if (createResponse.status) {
                val newId = createResponse.clientId ?: createResponse.data?.clientId
                if (newId != null) {
                    Log.i("AutoKabalaAPI", "SUCCESS! Created new client. ID: $newId")
                    ApiResult.Success(newId)
                } else {
                    val errorMsg = "Failed to create client: Status was true but no client_id was returned."
                    Log.e("AutoKabalaAPI", errorMsg)
                    ApiResult.Failure(errorMsg)
                }
            } else {
                val errorMsg = createResponse.error ?: "Unknown error"
                Log.e("AutoKabalaAPI", "FAILED to create client: $errorMsg")
                ApiResult.Failure(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Exception during client creation"
            Log.e("AutoKabalaAPI", "EXCEPTION during client creation: $errorMsg", e)
            ApiResult.Failure(errorMsg)
        }
    }

    suspend fun getActiveClientIds(fromDate: String): Set<String>? {
        Log.i("AutoKabalaAPI", "--- Fetching active client IDs since $fromDate ---")
        return try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val allIds = mutableSetOf<String>()
            var offset = 0
            val pageSize = 50
            while (true) {
                val requestBody = DocSearchRequest(
                    cid = cid,
                    user = user,
                    pass = pass,
                    startDate = fromDate,
                    limit = pageSize,
                    offset = offset
                )
                val response = client.post("$BASE_URL/doc/search") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                val rawBody = response.body<String>()
                val parsed = json.decodeFromString<DocSearchResponse>(rawBody)
                if (!parsed.status || parsed.docs == null) {
                    Log.w("AutoKabalaAPI", "getActiveClientIds page $offset: reason=${parsed.reason} resultsCount=${parsed.resultsCount}")
                    break
                }
                val pageIds = parsed.docs.values.mapNotNull { it.clientId?.toString() }
                allIds.addAll(pageIds)
                Log.d("AutoKabalaAPI", "getActiveClientIds page $offset: ${pageIds.size} docs, ${allIds.size} unique clients so far")
                if (parsed.docs.size < pageSize) break
                offset += pageSize
            }
            Log.i("AutoKabalaAPI", "Active client IDs total: ${allIds.size} unique clients")
            allIds.ifEmpty { null }
        } catch (e: Exception) {
            Log.w("AutoKabalaAPI", "getActiveClientIds failed", e)
            null
        }
    }

    suspend fun getClientMobile(clientId: String): String? {
        return try {
            val response = client.post("$BASE_URL/client/info") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("cid",          cid)
                    put("user",         user)
                    put("pass",         pass)
                    put("client_id",    clientId.toInt())
                    put("get_contacts", true)
                })
            }
            val parsed = Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromString<ClientInfoResponse>(response.body<String>())
            val mobile = parsed.clientInfo?.mobile?.ifBlank { null }
                ?: parsed.clientInfo?.phone?.ifBlank { null }
            mobile
        } catch (e: Exception) {
            Log.w("AutoKabalaAPI", "getClientMobile failed", e)
            null
        }
    }
}
