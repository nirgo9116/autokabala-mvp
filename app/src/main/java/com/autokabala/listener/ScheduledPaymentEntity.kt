package com.autokabala.listener

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_payments")
data class ScheduledPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: String,
    val clientName: String,
    val amount: Double,
    val scheduledDate: Long, // timestamp
    val description: String,
    val reminderSent: Boolean = false,
    val completed: Boolean = false, // true when receipt issued
    val createdAt: Long = System.currentTimeMillis(),
    val autoReminderEnabled: Boolean = true,
    val serviceCompletedTime: Long? = null,
    val reminderHoursAfter: Int = 24,
    val reminderRecurrenceDays: Int = 0,
    val tookPlace: Boolean? = null,  // null = not answered, true = happened, false = didn't happen
    val receiptIssued: Boolean = false
)
