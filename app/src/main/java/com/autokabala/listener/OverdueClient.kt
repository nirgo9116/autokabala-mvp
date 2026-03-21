package com.autokabala.listener

data class OverdueClient(val client: ClientEntity, val daysSinceLastPayment: Int)
