package com.autokabala.listener

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledPaymentDao {
    @Query("SELECT * FROM scheduled_payments WHERE completed = 0 ORDER BY scheduledDate ASC")
    fun getAllScheduledPayments(): Flow<List<ScheduledPaymentEntity>>

    @Query("SELECT * FROM scheduled_payments WHERE id = :id")
    suspend fun getScheduledPaymentById(id: Int): ScheduledPaymentEntity?

    @Insert
    suspend fun insertScheduledPayment(payment: ScheduledPaymentEntity): Long

    @Update
    suspend fun updateScheduledPayment(payment: ScheduledPaymentEntity)

    @Delete
    suspend fun deleteScheduledPayment(payment: ScheduledPaymentEntity)

    @Query("UPDATE scheduled_payments SET completed = 1 WHERE id = :id")
    suspend fun markAsCompleted(id: Int)

    @Query("UPDATE scheduled_payments SET reminderSent = 1 WHERE id = :id")
    suspend fun markReminderSent(id: Int)

    @Query("""
        SELECT * FROM scheduled_payments
        WHERE completed = 0
        AND autoReminderEnabled = 1
        AND serviceCompletedTime IS NOT NULL
        AND reminderSent = 0
    """)
    suspend fun getPaymentsNeedingReminderCandidates(): List<ScheduledPaymentEntity>

    @Query("""
        SELECT * FROM scheduled_payments
        WHERE scheduledDate >= :startTime
        AND scheduledDate <= :endTime
    """)
    suspend fun getSessionsInRange(startTime: Long, endTime: Long): List<ScheduledPaymentEntity>

    @Query("""
        SELECT * FROM scheduled_payments
        WHERE clientId = :clientId
        AND scheduledDate >= :startTime
        AND scheduledDate <= :endTime
        LIMIT 1
    """)
    suspend fun findScheduledPaymentInRange(clientId: String, startTime: Long, endTime: Long): ScheduledPaymentEntity?

    @Query("SELECT * FROM scheduled_payments WHERE seriesId = :seriesId")
    suspend fun getBySeriesId(seriesId: String): List<ScheduledPaymentEntity>

    @Query("DELETE FROM scheduled_payments WHERE seriesId = :seriesId")
    suspend fun deleteBySeriesId(seriesId: String)
}
