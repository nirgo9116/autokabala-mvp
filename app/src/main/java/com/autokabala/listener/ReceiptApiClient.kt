package com.autokabala.listener

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ReceiptApiClient {

    // 1. Configure the HTTP client
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // Important for working with external APIs
            })
        }
    }

    /**
     * Simulates issuing a receipt by sending the payment data to an external service.
     * For now, it just logs the action.
     */
    suspend fun issueReceipt(paymentData: PaymentData) {
        Log.d("AutoKabalaNL-Action", "Preparing to issue receipt via API for: $paymentData")

        // In the future, the actual network call will be here.
        // For example:
        // client.post("https://api.icount.co.il/v1/receipts") {
        //     contentType(ContentType.Application.Json)
        //     setBody(paymentData) // This requires PaymentData to be @Serializable
        // }

        // Simulate network delay
        kotlinx.coroutines.delay(1000)

        Log.d("AutoKabalaNL-Action", "API call simulation finished for: ${paymentData.senderName}")
    }
}
