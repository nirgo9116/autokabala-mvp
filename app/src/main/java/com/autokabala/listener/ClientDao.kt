package com.autokabala.listener

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    /**
     * Inserts a list of clients into the database.
     * If a client with the same ID already exists, it will be replaced.
     * This is used to sync the local database with the remote server.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<ClientEntity>)

    /**
     * Retrieves all clients from the database, ordered by name.
     */
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<ClientEntity>>

    /**
     * Deletes all clients from the table. Used before a full sync.
     */
    @Query("DELETE FROM clients")
    suspend fun deleteAll()
}
