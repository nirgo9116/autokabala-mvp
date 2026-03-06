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

    /**
     * Deletes all clients from the table.
     */
    @Query("DELETE FROM clients")
    suspend fun deleteAll()

    @Query("SELECT * FROM clients")
    suspend fun getAllClientsSnapshot(): List<ClientEntity>

    /**
     * Safely replaces all clients in the database with a new list,
     * preserving the autoSend flag and locally-cached phone for existing clients.
     */
    @Transaction
    suspend fun syncAll(clients: List<ClientEntity>) {
        val existing = getAllClientsSnapshot()
        val autoSendIds = existing.filter { it.autoSend }.map { it.id }.toSet()
        val phoneMap = existing.filter { it.phone != null }.associate { it.id to it.phone!! }
        deleteAll()
        insertAll(clients.map { client ->
            client.copy(
                autoSend = client.id in autoSendIds,
                phone = client.phone ?: phoneMap[client.id]
            )
        })
    }
}
