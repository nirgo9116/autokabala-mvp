package com.autokabala.listener

data class IssuedReceiptInfo(
    val docUrl: String? = null,
    val clientPhone: String? = null,
    val docNum: String? = null,
    val amount: Double = 0.0,
    val timestamp: Long = 0L,
    val clientName: String? = null
)
