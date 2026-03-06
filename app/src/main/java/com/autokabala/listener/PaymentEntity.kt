package com.autokabala.listener

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_payments",
    indices = [Index(value = ["source", "senderName", "amount", "timestamp"], unique = true)]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val source: String,
    val senderName: String,
    val amount: Double,
    val isConfirmed: Boolean,
    val timestamp: Long,
    val status: String = "pending", // e.g., "pending", "processed", "ignored"
    val clientId: String? = null,   // linked iCount client ID
    val clientName: String? = null, // snapshot of client name at receipt time
    val docNum: String? = null,     // iCount receipt number
    val docUrl: String? = null      // iCount receipt PDF URL
)
