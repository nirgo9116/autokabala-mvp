package com.autokabala.listener

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    sealed class UiEvent {
        data class ShowMessage(val message: String) : UiEvent()
        data class ShowError(val message: String) : UiEvent()
        object NavigateBack : UiEvent()
    }

    private val _uiEvent = MutableSharedFlow<Any>()
    val uiEvent = _uiEvent.asSharedFlow()
    val currentScreen = MutableStateFlow(Screen.MAIN)
    val selectedClient = MutableStateFlow<ClientEntity?>(null)
    val selectedTabIndex = MutableStateFlow(0)
    val clientPayments = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val allClients = MutableStateFlow<List<ClientEntity>>(emptyList())
    val overdueClients = MutableStateFlow<List<OverdueClient>>(emptyList())
    val pendingNewClients = MutableStateFlow<Map<Int, PendingNewClient>>(emptyMap())
    val pendingSessionLinks = MutableStateFlow<Map<Int, ScheduledPaymentEntity>>(emptyMap())
    val justIssuedCards = MutableStateFlow<Map<Int, IssuedReceiptInfo>>(emptyMap())
    val paymentHistory = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val scheduledPayments = MutableStateFlow<List<ScheduledPaymentEntity>>(emptyList())
    val paymentProcessingStates = MutableStateFlow<List<PaymentProcessingState>>(emptyList())
    val overdueFilterDays = MutableStateFlow(30)
    val isEnabled = MutableStateFlow(false)
    val isProcessingShare = MutableStateFlow(false)
    val calendarEvents = MutableStateFlow<List<CalendarEventEntity>>(emptyList())

    fun onShareIntentReceived(uri: Uri) {}
    fun onTabSelected(tab: Int) {}
    fun onSyncCalendarClicked() {}
    fun onSyncClientsClicked() {}
    fun onEnableDisableClicked() {}
    fun onDeletePaymentClicked(payment: Any) {}
    fun onDismissIssuedCard(paymentId: Any) {}
    fun onDismissSessionLink(paymentId: Any) {}
    fun onFakeIssueReceiptClicked(payment: Any) {}
    fun onIssueReceiptForClientClicked(payment: Any, client: Any, description: Any) {}
    fun onOpenClientDetail(client: Any) {}
    fun onSendEmailFromIssuedCard(docNum: Any) {}
    fun onUpdateClientContact(client: Any, phone: String?, email: String?) {}
    fun onUpdateClientWhatsAppMessage(client: Any, msg: Any) {}
    fun onConfirmNewClient(paymentId: Any, fullName: Any, phone: String?, email: String?) {}
    fun onConfirmSessionLink(payment: Any, client: Any, session: Any) {}
    fun onBackToMain() {}
    fun onAddFakePaymentClicked() {}
    fun onAddFakeOverduePaymentClicked() {}
    fun onCalendarEventClicked(event: Any) {}
    fun insertScheduledPayment(it: Any) {}
    fun deleteScheduledPayment(it: Any) {}
}
