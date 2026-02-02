package com.autokabala.listener

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Get the repository from the application class.
    private val receiptRepository = (application as AutoKabalaApplication).receiptRepository

    // Expose the list of pending payments from the repository to the UI.
    val pendingPayments: StateFlow<List<PaymentEntity>> = receiptRepository.pendingPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isEnabled: StateFlow<Boolean> = ListenerManager.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
            receiptRepository.issueReceipt(payment)
        }
    }

    fun onDeletePaymentClicked(payment: PaymentEntity) {
        viewModelScope.launch {
            receiptRepository.deletePayment(payment)
        }
    }
}
