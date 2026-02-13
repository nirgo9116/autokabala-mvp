package com.autokabala.listener

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autokabala.listener.ui.theme.AutoKabalaListenerTheme
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("AutoKabala", "Notification permission granted: $isGranted")
        // The UI will update its state automatically when the activity resumes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AutoKabalaNL", "MAIN ACTIVITY CREATED")

        askNotificationPermissionIfNeeded()

        enableEdgeToEdge()
        setContent {
            val factory = remember { MainViewModelFactory(application) }
            val mainViewModel: MainViewModel = viewModel(factory = factory)

            AutoKabalaListenerTheme {
                MainScreen(
                    viewModel = mainViewModel,
                    onOpenListenerSettingsClicked = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(permission)
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenListenerSettingsClicked: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasListenerPermission by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    var hasPostPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
            else ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // When the app comes back to the foreground, re-check both permissions
                hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasPostPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            if (event is MainViewModel.UiEvent.ShowError) {
                snackbarHostState.showSnackbar(message = event.message)
            }
        }
    }

    var showClientSelectionDialogFor by remember { mutableStateOf<PaymentProcessingState?>(null) }
    var showCreateClientDialogFor by remember { mutableStateOf<PaymentProcessingState?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            SectionTitle(text = "AutoKabala MVP")
            ControlSection(
                hasListenerPermission = hasListenerPermission,
                hasPostPermission = hasPostPermission,
                onOpenListenerSettings = onOpenListenerSettingsClicked
            )
            TestSection(
                onSyncClients = { viewModel.onSyncClientsClicked() },
                onAddFakePayment = { viewModel.onAddFakePaymentClicked() }
            )
            PaymentsSection(
                paymentStates = viewModel.paymentProcessingStates.collectAsState().value,
                viewModel = viewModel,
                onSelectClientClicked = { showClientSelectionDialogFor = it },
                onCreateClientClicked = { showCreateClientDialogFor = it }
            )
        }
    }

    showClientSelectionDialogFor?.let { state ->
        if (state.matchResult is MatchResult.MultipleMatches) {
            ClientSelectionDialog(
                clients = state.matchResult.clients,
                onDismiss = { showClientSelectionDialogFor = null },
                onClientSelected = { client ->
                    viewModel.onIssueReceiptForClientClicked(state.payment, client.id)
                    showClientSelectionDialogFor = null
                }
            )
        }
    }

    showCreateClientDialogFor?.let { state ->
        CreateNewClientDialog(
            initialName = state.payment.senderName,
            onDismiss = { showCreateClientDialogFor = null },
            onCreateConfirm = { newName ->
                viewModel.onCreateClientAndIssueReceiptClicked(state.payment, newName)
                showCreateClientDialogFor = null
            }
        )
    }
}

@Composable
fun ControlSection(
    hasListenerPermission: Boolean,
    hasPostPermission: Boolean,
    onOpenListenerSettings: () -> Unit
) {
    val context = LocalContext.current
    Column {
        SectionTitle(text = "1. Controls")
        Text("Listener Permission: $hasListenerPermission", color = if (hasListenerPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text("Post Permission: $hasPostPermission", color = if (hasPostPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text("Listener Status: Active (Default)")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenListenerSettings) { Text("Listener Settings") }
            if (!hasPostPermission) {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }) {
                    Text("Grant Push Permission")
                }
            }
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
            ParsedPaymentInfo(payment)
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
fun ParsedPaymentInfo(payment: PaymentEntity) {
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
    }
}
