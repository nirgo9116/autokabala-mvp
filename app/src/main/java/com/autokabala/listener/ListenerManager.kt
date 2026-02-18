package com.autokabala.listener

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the state of the notification listener service and broadcasts new payment events.
 */
object ListenerManager {

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // This is now a SharedFlow. It emits events to any collectors without holding state.
    // This is perfect for one-time events like a new notification.
    private val _newPaymentEvent = MutableSharedFlow<PaymentData>()
    val newPaymentEvent: SharedFlow<PaymentData> = _newPaymentEvent.asSharedFlow()

    fun enable() {
        _enabled.value = true
    }

    fun disable() {
        _enabled.value = false
    }

    /**
     * Called by the service when a notification has been successfully parsed.
     * It emits an event to any active collectors (like the Repository).
     */
    suspend fun onPaymentParsed(payment: PaymentData) {
        if (!enabled.value) return
        _newPaymentEvent.emit(payment)
    }
}
