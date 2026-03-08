package com.autokabala.listener

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ClientLastPayment(
    @ColumnInfo(name = "clientId") val clientId: String,
    @ColumnInfo(name = "lastPaymentTime") val lastPaymentTime: Long
)

@Dao
interface PaymentDao {

    /**
     * Inserts a new payment into the table.
     * If a payment with the same unique details already exists, skip the insert
     * so that a previously-processed receipt is never overwritten.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

    @Query("UPDATE pending_payments SET clientId=:clientId, clientName=:clientName, docNum=:docNum, docUrl=:docUrl WHERE id=:id")
    suspend fun updatePaymentReceipt(id: Int, clientId: String, clientName: String, docNum: String?, docUrl: String?)

    @Query("SELECT * FROM pending_payments WHERE status != 'ignored' AND timestamp >= :since ORDER BY timestamp DESC")
    fun getRecentPayments(since: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM pending_payments WHERE clientId = :clientId ORDER BY timestamp DESC")
    fun getPaymentsByClientId(clientId: String): Flow<List<PaymentEntity>>

    @Query("SELECT clientId, MAX(timestamp) AS lastPaymentTime FROM pending_payments WHERE clientId IS NOT NULL AND status != 'ignored' GROUP BY clientId")
    fun getLastPaymentPerClient(): Flow<List<ClientLastPayment>>
}
