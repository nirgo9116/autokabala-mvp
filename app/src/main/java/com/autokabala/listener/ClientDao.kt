package com.autokabala.listener

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    /**
     * Inserts a list of clients into the database.
     * If a client with the same ID already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<ClientEntity>)

    /**
     * Retrieves all clients from the database, ordered by name.
     */
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<ClientEntity>>

    @Query("UPDATE clients SET autoSend = :autoSend WHERE id = :clientId")
    suspend fun updateAutoSend(clientId: String, autoSend: Boolean)

    @Query("UPDATE clients SET phone = :phone WHERE id = :clientId")
    suspend fun updatePhone(clientId: String, phone: String)

    @Query("UPDATE clients SET email = :email WHERE id = :clientId")
    suspend fun updateEmail(clientId: String, email: String)

    @Query("UPDATE clients SET whatsappMessage = :msg WHERE id = :clientId")
    suspend fun updateWhatsAppMessage(clientId: String, msg: String)

    @Query("UPDATE clients SET isActive = :isActive WHERE id = :clientId")
    suspend fun updateIsActive(clientId: String, isActive: Boolean)

    @Query("UPDATE clients SET lastReceiptDate = :lastReceiptDate WHERE id = :clientId")
    suspend fun updateLastReceiptDate(clientId: String, lastReceiptDate: Long)

    /**
     * Deletes all clients from the table.
     */
    @Query("DELETE FROM clients")
    suspend fun deleteAll()

    @Query("SELECT * FROM clients")
    suspend fun getAllClientsSnapshot(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :clientId LIMIT 1")
    suspend fun getClientById(clientId: String): ClientEntity?

    /**
     * Safely replaces all clients in the database with a new list,
     * preserving the autoSend flag and locally-cached phone for existing clients.
     */
    @Transaction
    suspend fun syncAll(clients: List<ClientEntity>) {
        val existing = getAllClientsSnapshot()
        val autoSendIds = existing.filter { it.autoSend }.map { it.id }.toSet()
        val phoneMap = existing.filter { it.phone != null }.associate { it.id to it.phone!! }
        val whatsappMsgMap = existing.filter { it.whatsappMessage != null }.associate { it.id to it.whatsappMessage!! }
        deleteAll()
        insertAll(clients.map { client ->
            client.copy(
                autoSend = client.id in autoSendIds,
                phone = client.phone ?: phoneMap[client.id],
                whatsappMessage = whatsappMsgMap[client.id]
            )
        })
    }
}
