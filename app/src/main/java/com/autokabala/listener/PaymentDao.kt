package com.autokabala.listener

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    /**
     * Inserts a new payment into the table.
     * If a payment with the same details already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)

    /**
     * Retrieves all payments that are currently in 'pending' status.
     * It returns a Flow, so the UI can observe changes automatically.
     */
    @Query("SELECT * FROM pending_payments WHERE status = 'pending' ORDER BY timestamp DESC")
    fun getPendingPayments(): Flow<List<PaymentEntity>>

    /**
     * Deletes a specific payment from the table, for example after it's been processed or ignored.
     */
    @Query("DELETE FROM pending_payments WHERE id = :paymentId")
    suspend fun deletePayment(paymentId: Int)

    /**
     * Updates the status of a payment (e.g., from 'pending' to 'processed').
     */
    @Query("UPDATE pending_payments SET status = :newStatus WHERE id = :paymentId")
    suspend fun updatePaymentStatus(paymentId: Int, newStatus: String)
}
