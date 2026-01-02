package com.autokabala.listener

import kotlinx.serialization.Serializable

/**
 * Represents the structured data extracted from a payment notification.
 * Added @Serializable to allow converting this object to JSON for API calls.
 */
@Serializable
data class PaymentData(
    val source: String,
    val senderName: String,
    val amount: Double,
    val isConfirmed: Boolean,
    val timestamp: Long,
    val rawText: String
)
