package com.autokabala.listener

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ListenerManager {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // Holds structured PaymentData now, not a raw string.
    private val _lastPayment = MutableStateFlow<PaymentData?>(null)
    val lastPayment: StateFlow<PaymentData?> = _lastPayment.asStateFlow()

    fun enable() {
        _enabled.value = true
    }

    fun disable() {
        _enabled.value = false
    }

    /**
     * Called by the service when a notification has been successfully parsed.
     */
    fun onPaymentParsed(payment: PaymentData) {
        if (!enabled.value) return
        _lastPayment.value = payment
    }

    /**
     * Clears the last processed payment event.
     */
    fun clearLastPayment() {
        _lastPayment.value = null
    }
}
