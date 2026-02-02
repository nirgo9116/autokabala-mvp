package com.autokabala.listener

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autokabala.listener.ui.theme.AutoKabalaListenerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AutoKabalaNL", "MAIN ACTIVITY CREATED")

        enableEdgeToEdge()
        setContent {
            // The ViewModel is now retrieved from the activity, which gets it from the Application.
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
}

// A simple factory to provide the Application context to the ViewModel
inline fun <reified T : MainViewModel> ComponentActivity.viewModelFactory(crossinline creator: () -> T): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
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
    // Get the list of pending payments from the ViewModel
    val pendingPayments by viewModel.pendingPayments.collectAsState()

    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "AutoKabala MVP",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Permission and Listener Control (remains the same) ---
            SectionTitle("1. Grant Permission")
            Text(
                "Permission Granted: $hasNotificationPermission",
                color = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenSettingsClicked) {
                Text("Open Notification Settings")
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("2. Control Listener")
            Text("Status: ${if (isEnabled) "Active" else "Inactive"}")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.onEnableDisableClicked() }) {
                Text(if (isEnabled) "Disable Listener" else "Enable Listener")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- List of Pending Payments ---
            SectionTitle("3. Pending Payments")
            if (pendingPayments.isEmpty()) {
                Text("No pending payments.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pendingPayments) { payment ->
                        PaymentCard(payment = payment, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentCard(payment: PaymentEntity, viewModel: MainViewModel) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            ParsedPaymentInfo(payment)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.onDeletePaymentClicked(payment) }) {
                    Text("Ignore")
                }
                Button(onClick = { viewModel.onIssueReceiptClicked(payment) }) {
                    Text("Issue Receipt")
                }
            }
        }
    }
}


@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun ParsedPaymentInfo(payment: PaymentEntity) { // Updated to take PaymentEntity
    val formattedDate = remember(payment.timestamp) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date(payment.timestamp))
    }

    Column {
        Text("Source: ${payment.source}", fontWeight = FontWeight.Bold)
        Text("Sender: ${payment.senderName}")
        Text("Amount: ${payment.amount} ILS")
        Text("Date: $formattedDate")
        Text(
            "Status: ${if (payment.isConfirmed) "Confirmed Payment" else "Payment Request"}",
            color = if (payment.isConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("Raw: ${payment.rawText}", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    AutoKabalaListenerTheme {
        val samplePayment = PaymentEntity(
            id = 1,
            source = "bit",
            senderName = "Danny",
            amount = 75.0,
            isConfirmed = false,
            timestamp = System.currentTimeMillis(),
            rawText = "...",
        )
        // This preview will need a mock ViewModel to work correctly now.
        // For simplicity, we just show the card.
        PaymentCard(payment = samplePayment, viewModel = viewModel())
    }
}
