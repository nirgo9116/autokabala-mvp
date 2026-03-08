package com.autokabala.listener

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey
    val id: String, // The ID from iCount
    val name: String,
    val email: String?,
    val phone: String?,
    val autoSend: Boolean = false,
    val whatsappMessage: String? = null
)
