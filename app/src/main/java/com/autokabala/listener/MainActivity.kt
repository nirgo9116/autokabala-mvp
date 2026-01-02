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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.autokabala.listener.ui.theme.AutoKabalaListenerTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AutoKabalaNL", "MAIN ACTIVITY CREATED")

        enableEdgeToEdge()
        setContent {
            AutoKabalaListenerTheme {
                val isEnabled by ListenerManager.enabled.collectAsState()
                val lastPayment by ListenerManager.lastPayment.collectAsState()
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val coroutineScope = rememberCoroutineScope()

                var hasNotificationPermission by remember {
                    mutableStateOf(isNotificationPermissionGranted(context))
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasNotificationPermission = isNotificationPermissionGranted(context)
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

                        SectionTitle("1. Grant Permission")
                        Text(
                            "Permission Granted: $hasNotificationPermission",
                            color = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) {
                            Text("Open Notification Settings")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        SectionTitle("2. Control Listener")
                        Text("Status: ${if (isEnabled) "Active" else "Inactive"}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            if (isEnabled) ListenerManager.disable() else ListenerManager.enable()
                        }) {
                            Text(if (isEnabled) "Disable Listener" else "Enable Listener")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        SectionTitle("3. Last Parsed Payment")
                        ParsedPaymentInfo(lastPayment)

                        val currentPayment = lastPayment
                        if (currentPayment != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { ListenerManager.clearLastPayment() }) {
                                    Text("Clear")
                                }
                                Button(
                                    onClick = {
                                        // Launch a coroutine to call the API without blocking the UI
                                        coroutineScope.launch {
                                            ReceiptApiClient.issueReceipt(currentPayment)
                                        }
                                    },
                                    enabled = !currentPayment.isConfirmed
                                ) {
                                    Text("Issue Receipt")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isNotificationPermissionGranted(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
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
fun ParsedPaymentInfo(payment: PaymentData?) {
    if (payment == null) {
        Text("No payment events processed yet.", modifier = Modifier.fillMaxWidth())
    } else {
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
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    AutoKabalaListenerTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            val samplePayment = PaymentData(
                source = "bit",
                senderName = "Danny",
                amount = 75.0,
                isConfirmed = false,
                timestamp = System.currentTimeMillis(),
                rawText = "...",
            )
            ParsedPaymentInfo(samplePayment)
        }
    }
}
