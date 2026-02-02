package com.autokabala.listener

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Get the repository from the application class.
    private val receiptRepository = (application as AutoKabalaApplication).receiptRepository

    // --- UI State ---
    val pendingPayments: StateFlow<List<PaymentEntity>> = receiptRepository.pendingPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isEnabled: StateFlow<Boolean> = ListenerManager.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- One-time Events ---
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent: Flow<UiEvent> = _uiEvent.receiveAsFlow()

    sealed class UiEvent {
        data class ShowError(val message: String) : UiEvent()
    }

    // --- Event Handlers ---

    fun onEnableDisableClicked() {
        if (isEnabled.value) {
            ListenerManager.disable()
        } else {
            ListenerManager.enable()
        }
    }

    fun onIssueReceiptClicked(payment: PaymentEntity) {
        viewModelScope.launch {
            val wasSuccessful = receiptRepository.issueReceipt(payment)
            if (!wasSuccessful) {
                _uiEvent.send(UiEvent.ShowError("Failed to issue receipt. Please check internet connection and try again."))
            }
        }
    }

    fun onDeletePaymentClicked(payment: PaymentEntity) {
        viewModelScope.launch {
            receiptRepository.deletePayment(payment)
        }
    }

    fun onSyncClientsClicked() {
        viewModelScope.launch {
            receiptRepository.syncClients()
        }
    }
}
