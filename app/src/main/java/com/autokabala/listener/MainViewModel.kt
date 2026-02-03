package com.autokabala.listener

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Represents the result of matching a payment to existing clients
sealed class MatchResult {
    object NoMatch : MatchResult()
    data class SingleMatch(val client: ClientEntity) : MatchResult()
    data class MultipleMatches(val clients: List<ClientEntity>) : MatchResult()
}

// Holds the combined state for a single payment, ready for the UI
data class PaymentProcessingState(
    val payment: PaymentEntity,
    val matchResult: MatchResult
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val receiptRepository = (application as AutoKabalaApplication).receiptRepository

    // --- UI State ---
    private val pendingPayments: StateFlow<List<PaymentEntity>> = receiptRepository.pendingPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allClients: StateFlow<List<ClientEntity>> = receiptRepository.allClients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentProcessingStates: StateFlow<List<PaymentProcessingState>> = combine(
        pendingPayments, allClients
    ) { payments, clients ->
        payments.map { payment ->
            val senderFirstName = payment.senderName.trim().split(" ").firstOrNull() ?: ""
            val matchingClients = if (senderFirstName.isBlank()) emptyList() else {
                clients.filter { client ->
                    client.name.contains(senderFirstName, ignoreCase = true)
                }
            }

            val matchResult = when {
                matchingClients.isEmpty() -> MatchResult.NoMatch
                matchingClients.size == 1 -> MatchResult.SingleMatch(matchingClients.first())
                else -> MatchResult.MultipleMatches(matchingClients)
            }

            PaymentProcessingState(payment, matchResult)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


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

    fun onIssueReceiptForClientClicked(payment: PaymentEntity, clientId: String) {
        viewModelScope.launch {
            val wasSuccessful = receiptRepository.issueReceiptForClient(payment, clientId)
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

    fun onAddFakePaymentClicked() {
        viewModelScope.launch {
            receiptRepository.addFakePayment()
        }
    }
}
