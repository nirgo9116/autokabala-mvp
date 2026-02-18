package com.autokabala.listener

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val source: String,
    val senderName: String,
    val amount: Double,
    val isConfirmed: Boolean,
    val timestamp: Long,
    val status: String = "pending" // e.g., "pending", "processed", "ignored"
)
