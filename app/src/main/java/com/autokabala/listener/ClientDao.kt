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

    /**
     * Safely replaces all clients in the database with a new list.
     * The @Transaction annotation ensures this happens atomically.
     */
    @Transaction
    suspend fun syncAll(clients: List<ClientEntity>) {
        deleteAll()
        insertAll(clients)
    }
}
