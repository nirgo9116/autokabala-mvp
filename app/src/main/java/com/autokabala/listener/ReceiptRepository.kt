package com.autokabala.listener

/**
 * The Repository is the single source of truth for all data operations.
 * It abstracts the data sources (network, database, etc.) from the rest of the app.
 */
class ReceiptRepository {

    /**
     * Issues a receipt by calling the remote API client.
     * In the future, this could also handle caching or offline storage.
     */
    suspend fun issueReceipt(paymentData: PaymentData) {
        // For now, it directly calls the ApiClient.
        // This abstraction is useful for future expansion.
        ReceiptApiClient.issueReceipt(paymentData)
    }
}
