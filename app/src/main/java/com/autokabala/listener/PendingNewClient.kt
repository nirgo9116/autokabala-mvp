package com.autokabala.listener

data class PendingNewClient(
    val id: Int = 0,
    val name: String = "",
    val email: String? = null,
    val phone: String? = null
)
