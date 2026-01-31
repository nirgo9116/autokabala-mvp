package com.autokabala.listener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    // The ViewModel now depends on the Repository, not a specific ApiClient.
    private val receiptRepository = ReceiptRepository()

    // 1. Expose the state from ListenerManager to the UI
    val isEnabled: StateFlow<Boolean> = ListenerManager.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastPayment: StateFlow<PaymentData?> = ListenerManager.lastPayment
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 2. Expose functions to handle user events

    fun onEnableDisableClicked() {
        if (isEnabled.value) {
            ListenerManager.disable()
        } else {
            ListenerManager.enable()
        }
    }

    fun onClearLastPaymentClicked() {
        ListenerManager.clearLastPayment()
    }

    fun onIssueReceiptClicked() {
        val payment = lastPayment.value
        if (payment != null) {
            viewModelScope.launch {
                // The ViewModel calls the Repository, abstracting the data source.
                receiptRepository.issueReceipt(payment)
            }
        }
    }
}
