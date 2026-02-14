package com.autokabala.listener

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autokabala.listener.ui.theme.AutoKabalaListenerTheme
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. You can now show notifications.
        } else {
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AutoKabalaNL", "MAIN ACTIVITY CREATED")

        requestNotificationPermission()

        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = viewModelFactory { MainViewModel(application) })

            AutoKabalaListenerTheme {
                MainScreen(
                    viewModel = mainViewModel,
                    onOpenSettingsClicked = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

// The stable, albeit less conventional, factory that was working before.
inline fun <reified T : MainViewModel> ComponentActivity.viewModelFactory(crossinline creator: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return creator() as T
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettingsClicked: () -> Unit
) {
    val isEnabled by viewModel.isEnabled.collectAsState()
    val paymentStates by viewModel.paymentProcessingStates.collectAsState()
    val context = LocalContext.current

    var showClientSelectionDialogFor by remember { mutableStateOf<PaymentProcessingState?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is MainViewModel.UiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(message = event.message)
                }
                is MainViewModel.UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // --- Dialogs ---
    showClientSelectionDialogFor?.let { state ->
        if (state.matchResult is MatchResult.MultipleMatches) {
            ClientSelectionDialog(
                clients = state.matchResult.clients,
                onDismiss = { showClientSelectionDialogFor = null },
                onClientSelected = {
                    viewModel.onIssueReceiptForClientClicked(state.payment, it.id)
                    showClientSelectionDialogFor = null
                }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            SectionTitle(text = "AutoKabala MVP")
            ControlSection(isEnabled, true, onOpenSettingsClicked) { viewModel.onEnableDisableClicked() } // Always pass true
            TestSection(onSyncClients = { viewModel.onSyncClientsClicked() }, onAddFakePayment = { viewModel.onAddFakePaymentClicked() })
            PaymentsSection(
                paymentStates = paymentStates,
                viewModel = viewModel,
                onSelectClientClicked = { showClientSelectionDialogFor = it },
                onCreateClientClicked = { viewModel.onCreateClientAndIssueReceiptClicked(it) }
            )
        }
    }
}

@Composable
fun ControlSection(isEnabled: Boolean, hasPermission: Boolean, onOpenSettings: () -> Unit, onToggle: () -> Unit) {
    Column {
        SectionTitle(text = "1. Controls")
        Text("Permission Granted: $hasPermission", color = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text("Listener Status: ${if (isEnabled) "Active" else "Inactive"}")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenSettings) { Text("Permissions") }
            Button(onClick = onToggle) { Text(if (isEnabled) "Disable" else "Enable") }
        }
    }
}

@Composable
fun TestSection(onSyncClients: () -> Unit, onAddFakePayment: () -> Unit) {
    Column {
        SectionTitle(text = "2. Testing")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSyncClients) { Text("Sync Clients") }
            OutlinedButton(onClick = onAddFakePayment) { Text("Add Fake Payment") }
        }
    }
}


@Composable
fun PaymentsSection(paymentStates: List<PaymentProcessingState>, viewModel: MainViewModel, onSelectClientClicked: (PaymentProcessingState) -> Unit, onCreateClientClicked: (PaymentProcessingState) -> Unit) {
    Column {
        SectionTitle(text = "3. Pending Payments")
        if (paymentStates.isEmpty()) {
            Text("No pending payments.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(paymentStates, key = { it.payment.id }) { state ->
                    PaymentCard(state = state, viewModel = viewModel, onSelectClientClicked = onSelectClientClicked, onCreateClientClicked = onCreateClientClicked)
                }
            }
        }
    }
}

@Composable
fun PaymentCard(state: PaymentProcessingState, viewModel: MainViewModel, onSelectClientClicked: (PaymentProcessingState) -> Unit, onCreateClientClicked: (PaymentProcessingState) -> Unit) {
    val payment = state.payment
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            ParsedPaymentInfo(payment, state.matchResult)
            Spacer(modifier = Modifier.height(16.dp))
            SmartActionArea(state, viewModel, onSelectClientClicked, onCreateClientClicked)
        }
    }
}

@Composable
fun SmartActionArea(state: PaymentProcessingState, viewModel: MainViewModel, onSelectClientClicked: (PaymentProcessingState) -> Unit, onCreateClientClicked: (PaymentProcessingState) -> Unit) {
    when (val matchResult = state.matchResult) {
        is MatchResult.SingleMatch -> {
            Column(horizontalAlignment = Alignment.Start) {
                Text("Suggestion: Issue receipt for client ${matchResult.client.name}?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.onIssueReceiptForClientClicked(state.payment, matchResult.client.id) }) {
                        Text("Yes, Issue for ${matchResult.client.name}")
                    }
                    OutlinedButton(onClick = { viewModel.onDeletePaymentClicked(state.payment) }) {
                        Text("Ignore (Friend)")
                    }
                }
            }
        }
        is MatchResult.MultipleMatches -> {
            Column(horizontalAlignment = Alignment.Start) {
                Text("${matchResult.clients.size} matching clients found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSelectClientClicked(state) }) { Text("Select Client & Issue") }
                    OutlinedButton(onClick = { viewModel.onDeletePaymentClicked(state.payment) }) { Text("Ignore") }
                }
            }
        }
        is MatchResult.NoMatch -> {
            Column(horizontalAlignment = Alignment.Start) {
                Text("No matching client found in iCount.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCreateClientClicked(state) }) { Text("Create Client & Issue") }
                    OutlinedButton(onClick = { viewModel.onDeletePaymentClicked(state.payment) }) { Text("Ignore (Friend)") }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Column {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun ParsedPaymentInfo(payment: PaymentEntity, matchResult: MatchResult) {
    val formattedDate = remember(payment.timestamp) { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(payment.timestamp)) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = "Source", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Source: ${payment.source.replaceFirstChar { it.titlecase() }} | Date: $formattedDate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("From: ${payment.senderName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text("Amount: ${payment.amount} ILS", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        if (matchResult is MatchResult.SingleMatch) {
            Text("Matched iCount Client: ${matchResult.client.name}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
