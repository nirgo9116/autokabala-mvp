package com.autokabala.listener

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ListenerManager {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lastEvent = MutableStateFlow<String?>(null)
    val lastEvent: StateFlow<String?> = _lastEvent.asStateFlow()

    fun enable() {
        _enabled.value = true
    }

    fun disable() {
        _enabled.value = false
    }

    fun onNotificationReceived(rawText: String) {
        if (!_enabled.value) return
        _lastEvent.value = rawText
    }

    fun clearLastEvent() {
        _lastEvent.value = null
    }
}
