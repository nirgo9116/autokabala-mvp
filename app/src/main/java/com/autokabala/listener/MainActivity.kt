package com.autokabala.listener

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val factory = remember { MainViewModelFactory(application) }
            val mainViewModel: MainViewModel = viewModel(factory = factory)
            AutoKabalaListenerTheme(dynamicColor = false) {
                MainScreen(
                    viewModel = mainViewModel,
                    onOpenSettingsClicked = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND &&
            (intent.type?.startsWith("image/") == true || intent.type == "*/*")) {
            @Suppress("DEPRECATION")
            // Some apps (e.g. WhatsApp) use EXTRA_STREAM; others (e.g. Paybox) use ClipData.
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.clipData?.getItemAt(0)?.uri
            if (uri != null) {
                val viewModel = ViewModelProvider(
                    this, MainViewModelFactory(application)
                )[MainViewModel::class.java]
                viewModel.onShareIntentReceived(uri)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chip style constants
// ─────────────────────────────────────────────────────────────────────────────

private val ChipGreenBg     = Color(0xFFE8F5E9)
private val ChipGreenText   = Color(0xFF1B5E20)
private val ChipGreenBorder = Color(0xFF4CAF50)

private val ChipAmberBg     = Color(0xFFFFF8E1)
private val ChipAmberText   = Color(0xFFBF360C)
private val ChipAmberBorder = Color(0xFFFF9800)

private val ChipGrayBg      = Color(0xFFF5F5F5)
private val ChipGrayText    = Color(0xFF616161)
private val ChipGrayBorder  = Color(0xFFBDBDBD)

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MainScreen(viewModel: MainViewModel, onOpenSettingsClicked: () -> Unit) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    BackHandler(enabled = currentScreen == Screen.CLIENT_DETAIL) {
        viewModel.onBackToMain()
    }

    MainTabsScreen(
        viewModel = viewModel,
        context = LocalContext.current,
        onOpenSettingsClicked = onOpenSettingsClicked
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabsScreen(
    viewModel: MainViewModel,
    context: android.content.Context,
    onOpenSettingsClicked: () -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val selectedClient by viewModel.selectedClient.collectAsState()
    val clientPayments by viewModel.clientPayments.collectAsState()
    val allClients by viewModel.allClients.collectAsState()
    val isEnabled by viewModel.isEnabled.collectAsState()
    val paymentStates by viewModel.paymentProcessingStates.collectAsState()
    val isProcessingShare by viewModel.isProcessingShare.collectAsState()
    val paymentHistory by viewModel.paymentHistory.collectAsState()
    val selectedTab by viewModel.selectedTabIndex.collectAsState()
    val overdueClients by viewModel.overdueClients.collectAsState()
    val overdueFilterDays by viewModel.overdueFilterDays.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        )
    }
    // Map of paymentId → chosen client ID (store ID only so effectiveClient always reflects DB)
    var selectedClientIdsMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    // Which payment's sheet is open
    var clientSheetFor by remember { mutableStateOf<PaymentProcessingState?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = NotificationManagerCompat
                    .getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Clean up stale map entries when payments disappear from DB
    val currentPaymentIds = remember(paymentStates) { paymentStates.map { it.payment.id }.toSet() }
    LaunchedEffect(currentPaymentIds) {
        selectedClientIdsMap = selectedClientIdsMap.filterKeys { it in currentPaymentIds }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is MainViewModel.UiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is MainViewModel.UiEvent.ReceiptIssued -> {
                    val message = if (event.emailSent) "קבלה הופקה ונשלחה במייל ✓" else "קבלה הופקה בהצלחה ✓"
                    val actionLabel = if (event.docUrl != null) "שלח בוואטסאפ" else null
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed && event.docUrl != null) {
                        launchWhatsApp(context, docUrl = event.docUrl, clientPhone = event.clientPhone)
                    }
                }
            }
        }
    }

    // ── Bottom Sheet ─────────────────────────────────────────────────────────
    clientSheetFor?.let { state ->
        ClientSelectionSheet(
            state = state,
            allClients = allClients,
            onDismiss = { clientSheetFor = null },
            onClientSelected = { client ->
                selectedClientIdsMap = selectedClientIdsMap + (state.payment.id to client.id)
                clientSheetFor = null
            },
            onCreateClient = { firstName, lastName ->
                val fullName = if (lastName.isBlank()) firstName else "$firstName $lastName"
                viewModel.onCreateClientAndIssueReceiptClicked(state.payment, fullName)
                selectedClientIdsMap = selectedClientIdsMap - state.payment.id
                clientSheetFor = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    icon = { Icon(Icons.Default.List, contentDescription = "תשלומים") },
                    label = { Text("תשלומים") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "הגדרות") },
                    label = { Text("הגדרות") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.onTabSelected(2) },
                    icon = { Icon(Icons.Outlined.History, contentDescription = "היסטוריה") },
                    label = { Text("היסטוריה") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.onTabSelected(3) },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = "תזכורות") },
                    label = { Text("תזכורות") }
                )
            }
        }
    ) { innerPadding ->
        if (currentScreen == Screen.CLIENT_DETAIL) {
            val client = selectedClient ?: run { viewModel.onBackToMain(); return@Scaffold }
            ClientDetailScreen(
                modifier = Modifier.padding(innerPadding),
                client = client,
                payments = clientPayments,
                allClients = allClients,
                onBack = { viewModel.onBackToMain() },
                onWhatsApp = { phone ->
                    launchWhatsApp(
                        context,
                        clientPhone = phone,
                        text = "שלום ${client.name}, רצינו להזכירך לגבי תשלום. תודה! 🙏"
                    )
                }
            )
        } else {
            when (selectedTab) {
                0 -> PaymentsTab(
                    modifier = Modifier.padding(innerPadding),
                    paymentStates = paymentStates,
                    allClients = allClients,
                    selectedClientIdsMap = selectedClientIdsMap,
                    isProcessingShare = isProcessingShare,
                    onIssueReceipt = { payment, client ->
                        viewModel.onIssueReceiptForClientClicked(payment, client)
                    },
                    onDeletePayment = { state ->
                        viewModel.onDeletePaymentClicked(state.payment)
                    },
                    onOpenClientSheet = { clientSheetFor = it },
                    onToggleAutoSend = { client -> viewModel.onToggleAutoSend(client) },
                    onNoEmailTapped = { viewModel.onNoEmailForAutoSend() }
                )
                1 -> SettingsTab(
                    modifier = Modifier.padding(innerPadding),
                    isEnabled = isEnabled,
                    hasPermission = hasNotificationPermission,
                    onToggleListener = { viewModel.onEnableDisableClicked() },
                    onOpenSettings = onOpenSettingsClicked,
                    onSyncClients = { viewModel.onSyncClientsClicked() },
                    onAddFakePayment = { viewModel.onAddFakePaymentClicked() },
                    onAddFakeOverduePayment = { viewModel.onAddFakeOverduePaymentClicked() }
                )
                2 -> HistoryScreen(
                    modifier = Modifier.padding(innerPadding),
                    payments = paymentHistory,
                    allClients = allClients,
                    onOpenClientDetail = { client -> viewModel.onOpenClientDetail(client) }
                )
                3 -> OverdueClientsScreen(
                    modifier = Modifier.padding(innerPadding),
                    overdueClients = overdueClients,
                    filterDays = overdueFilterDays,
                    onFilterChanged = { viewModel.onOverdueFilterChanged(it) },
                    onSendReminder = { client ->
                        launchWhatsApp(
                            context,
                            clientPhone = client.phone,
                            text = "שלום ${client.name}, רצינו להזכירך לגבי תשלום. תודה! 🙏"
                        )
                    },
                    onOpenClientDetail = { client -> viewModel.onOpenClientDetail(client) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payments tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PaymentsTab(
    modifier: Modifier,
    paymentStates: List<PaymentProcessingState>,
    allClients: List<ClientEntity>,
    selectedClientIdsMap: Map<Int, String>,
    isProcessingShare: Boolean,
    onIssueReceipt: (PaymentEntity, ClientEntity) -> Unit,
    onDeletePayment: (PaymentProcessingState) -> Unit,
    onOpenClientSheet: (PaymentProcessingState) -> Unit,
    onToggleAutoSend: (ClientEntity) -> Unit,
    onNoEmailTapped: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("תשלומים ממתינים", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (isProcessingShare) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("קורא את תמונת התשלום...")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (paymentStates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "אין תשלומים ממתינים",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "תשלומים יופיעו כאן לאחר קבלת התראת ביט\nאו שיתוף תמונת אישור תשלום",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(paymentStates, key = { it.payment.id }) { state ->
                    // Derive selectedClient from the reactive allClients (not a stale snapshot)
                    val selectedClient = selectedClientIdsMap[state.payment.id]
                        ?.let { id -> allClients.find { it.id == id } }
                    PaymentCard(
                        state = state,
                        selectedClient = selectedClient,
                        onIssueReceipt = { client -> onIssueReceipt(state.payment, client) },
                        onDelete = { onDeletePayment(state) },
                        onOpenSheet = { onOpenClientSheet(state) },
                        onToggleAutoSend = onToggleAutoSend,
                        onNoEmailTapped = onNoEmailTapped
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PaymentCard(
    state: PaymentProcessingState,
    selectedClient: ClientEntity?,          // explicitly chosen via bottom sheet
    onIssueReceipt: (ClientEntity) -> Unit,
    onDelete: () -> Unit,
    onOpenSheet: () -> Unit,
    onToggleAutoSend: (ClientEntity) -> Unit,
    onNoEmailTapped: () -> Unit
) {
    val payment = state.payment
    val dateStr = remember(payment.timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(payment.timestamp))
    }
    val amountStr = if (payment.amount % 1.0 == 0.0) "₪${payment.amount.toInt()}"
                    else "₪${"%.2f".format(payment.amount)}"
    val sourceStr = when (payment.source) {
        "bit_share" -> "Bit (שיתוף)"
        "bit"       -> "Bit (התראה)"
        else        -> payment.source
    }

    // The client that will be used when "הפק קבלה" is pressed:
    //  • user picked one from the sheet  → selectedClient
    //  • auto strong/weak single match   → matchResult.client
    //  • multiple / no match             → null (button stays disabled)
    val effectiveClient: ClientEntity? = selectedClient
        ?: (state.matchResult as? MatchResult.SingleMatch)?.client

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Receipt-style fields ──────────────────────────────────────────
            PaymentField("שם", payment.senderName)
            PaymentField("סכום", amountStr)
            PaymentField("תאריך", dateStr)
            PaymentField("מקור", sourceStr)

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // ── Client selection field ────────────────────────────────────────
            ClientChipField(
                state = state,
                selectedClient = selectedClient,
                onClick = onOpenSheet
            )
            Text(
                text = "לחץ לשנות לקוח או ליצור לקוח חדש",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Auto-send email toggle (always visible for design consistency) ─
            val hasEmail = !effectiveClient?.email.isNullOrBlank()
            val toggleEnabled = effectiveClient != null && hasEmail
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (toggleEnabled) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "שלח קבלה במייל",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (toggleEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    Switch(
                        checked = effectiveClient?.autoSend == true && hasEmail,
                        onCheckedChange = { if (toggleEnabled) onToggleAutoSend(effectiveClient!!) },
                        enabled = toggleEnabled
                    )
                    if (!toggleEnabled) {
                        Box(modifier = Modifier.matchParentSize().clickable {
                            if (effectiveClient != null) onNoEmailTapped()
                        })
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { effectiveClient?.let { onIssueReceipt(it) } },
                    enabled = effectiveClient != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("הפק קבלה")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("מחק")
                }
            }
        }
    }
}

@Composable
private fun PaymentField(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Client chip / selection field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClientChipField(
    state: PaymentProcessingState,
    selectedClient: ClientEntity?,
    onClick: () -> Unit
) {
    // Resolve what to show in the chip
    val isFromSheet = selectedClient != null
    val mr = state.matchResult

    val displayClient: ClientEntity?
    val chipBg: Color
    val chipText: Color
    val chipBorder: Color
    val leadingIcon: ImageVector

    when {
        isFromSheet -> {
            // User explicitly confirmed a client → always green
            displayClient = selectedClient
            chipBg = ChipGreenBg; chipText = ChipGreenText; chipBorder = ChipGreenBorder
            leadingIcon = Icons.Default.CheckCircle
        }
        mr is MatchResult.SingleMatch && mr.isStrong -> {
            displayClient = mr.client
            chipBg = ChipGreenBg; chipText = ChipGreenText; chipBorder = ChipGreenBorder
            leadingIcon = Icons.Default.CheckCircle
        }
        mr is MatchResult.SingleMatch && !mr.isStrong -> {
            displayClient = mr.client
            chipBg = ChipAmberBg; chipText = ChipAmberText; chipBorder = ChipAmberBorder
            leadingIcon = Icons.Default.HelpOutline
        }
        mr is MatchResult.MultipleMatches -> {
            // Show first matched client as a suggestion (amber = needs confirmation)
            displayClient = mr.clients.first()
            chipBg = ChipAmberBg; chipText = ChipAmberText; chipBorder = ChipAmberBorder
            leadingIcon = Icons.Default.HelpOutline
        }
        else -> {
            displayClient = null
            chipBg = ChipGrayBg; chipText = ChipGrayText; chipBorder = ChipGrayBorder
            leadingIcon = Icons.Default.Add
        }
    }

    val chipLabel = displayClient?.name ?: "לחץ לבחור לקוח"

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = chipBg,
        border = BorderStroke(1.dp, chipBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = chipText,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = chipLabel,
                    color = chipText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "בחר לקוח",
                tint = chipText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Client selection bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

private enum class SheetView { QUICK, FULL_SEARCH, CREATE_FORM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSelectionSheet(
    state: PaymentProcessingState,
    allClients: List<ClientEntity>,
    onDismiss: () -> Unit,
    onClientSelected: (ClientEntity) -> Unit,
    onCreateClient: (firstName: String, lastName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Matched clients (non-empty for SingleMatch / MultipleMatches)
    val matchedClients: List<ClientEntity> = when (val mr = state.matchResult) {
        is MatchResult.SingleMatch     -> listOf(mr.client)
        is MatchResult.MultipleMatches -> mr.clients
        is MatchResult.NoMatch         -> emptyList()
    }

    val senderFirst = remember(state) {
        state.payment.senderName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.firstOrNull() ?: ""
    }
    val senderWords = remember(state) {
        state.payment.senderName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    // Start in QUICK mode when there are matches; go straight to FULL_SEARCH for no match.
    // Always pre-populate searchQuery with senderFirst so that when the user opens full search
    // (either from NoMatch or via "חיפוש ברשימת הלקוחות"), the closest names appear first.
    var view by remember {
        mutableStateOf(if (matchedClients.isNotEmpty()) SheetView.QUICK else SheetView.FULL_SEARCH)
    }
    var searchQuery by remember { mutableStateOf(senderFirst) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (view) {
            SheetView.QUICK -> QuickMatchContent(
                matchedClients = matchedClients,
                onClientSelected = onClientSelected,
                onCreateNew = { view = SheetView.CREATE_FORM },
                onSearchAll = { view = SheetView.FULL_SEARCH }
            )
            SheetView.FULL_SEARCH -> FullSearchContent(
                allClients = allClients,
                searchQuery = searchQuery,
                hasQuickBack = matchedClients.isNotEmpty(),
                onSearchChange = { searchQuery = it },
                onClientSelected = onClientSelected,
                onCreateNew = { view = SheetView.CREATE_FORM },
                onBack = { view = SheetView.QUICK }
            )
            SheetView.CREATE_FORM -> CreateClientForm(
                initialFirstName = senderWords.firstOrNull() ?: "",
                initialLastName = senderWords.drop(1).joinToString(" "),
                onBack = { view = if (matchedClients.isNotEmpty()) SheetView.QUICK else SheetView.FULL_SEARCH },
                onSubmit = { firstName, lastName -> onCreateClient(firstName, lastName) }
            )
        }
    }
}

/** Quick mode: shows only the matched clients + create new + search all */
@Composable
private fun QuickMatchContent(
    matchedClients: List<ClientEntity>,
    onClientSelected: (ClientEntity) -> Unit,
    onCreateNew: () -> Unit,
    onSearchAll: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("התאמות שנמצאו", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        matchedClients.forEach { client ->
            TextButton(
                onClick = { onClientSelected(client) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ChipGreenText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(8.dp))
        // Create new client
        TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("צור לקוח חדש", fontWeight = FontWeight.Medium)
            }
        }
        HorizontalDivider()
        // Search all clients
        OutlinedButton(
            onClick = onSearchAll,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("חיפוש ברשימת הלקוחות")
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Full search: all clients with search field */
@Composable
private fun FullSearchContent(
    allClients: List<ClientEntity>,
    searchQuery: String,
    hasQuickBack: Boolean,
    onSearchChange: (String) -> Unit,
    onClientSelected: (ClientEntity) -> Unit,
    onCreateNew: () -> Unit,
    onBack: () -> Unit
) {
    val filtered = remember(allClients, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            allClients.sortedBy { it.name }
        } else {
            allClients
                .filter { client ->
                    client.name.contains(q, ignoreCase = true) ||
                    client.name.trim().split(Regex("\\s+")).any { word ->
                        word.startsWith(q, ignoreCase = true) || q.startsWith(word, ignoreCase = true)
                    }
                }
                .sortedWith(
                    compareByDescending<ClientEntity> { client ->
                        val words = client.name.trim().split(Regex("\\s+"))
                        when {
                            // Exact word match — highest relevance
                            words.any { it.equals(q, ignoreCase = true) }       -> 3
                            // Word starts with query — e.g. "ניר" matches "ניר אברהם"
                            words.any { it.startsWith(q, ignoreCase = true) }   -> 2
                            // Name starts with query
                            client.name.startsWith(q, ignoreCase = true)        -> 1
                            else                                                 -> 0
                        }
                    }.thenBy { it.name }  // alphabetical within the same relevance score
                )
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (hasQuickBack) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזור", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("חזור להתאמות")
            }
        }
        Text("חיפוש לקוח", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("חפש לקוח...") },
            singleLine = true
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn {
            items(filtered, key = { it.id }) { client ->
                TextButton(
                    onClick = { onClientSelected(client) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = client.name,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                HorizontalDivider()
            }
            item {
                TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("צור לקוח חדש", fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CreateClientForm(
    initialFirstName: String,
    initialLastName: String,
    onBack: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "חזור",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("חזור לרשימה")
        }
        Text("לקוח חדש", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("שם פרטי") },
            singleLine = true
        )
        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("שם משפחה (אופציונלי)") },
            singleLine = true
        )
        Button(
            onClick = { onSubmit(firstName, lastName) },
            modifier = Modifier.fillMaxWidth(),
            enabled = firstName.isNotBlank()
        ) {
            Text("צור לקוח והפק קבלה")
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    payments: List<PaymentEntity>,
    allClients: List<ClientEntity>,
    onOpenClientDetail: (ClientEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredClients = remember(allClients, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) emptyList()
        else allClients
            .filter { it.name.contains(q, ignoreCase = true) }
            .sortedBy { it.name }
    }

    // Group payments by month with totals, preserving DESC order from DB
    val groupedPayments = remember(payments) {
        val cal = java.util.Calendar.getInstance()
        val map = LinkedHashMap<Pair<Int, Int>, MutableList<PaymentEntity>>()
        payments.forEach { p ->
            cal.timeInMillis = p.timestamp
            val key = cal.get(java.util.Calendar.YEAR) to cal.get(java.util.Calendar.MONTH)
            map.getOrPut(key) { mutableListOf() }.add(p)
        }
        map.entries.map { (key, list) -> Triple(key, list.toList(), list.sumOf { it.amount }) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(28.dp))
        Text(
            "היסטוריית תשלומים",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))

        // Search bar — oval shape
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("חפש לקוח...") },
            singleLine = true,
            shape = RoundedCornerShape(50),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "נקה") } }
            } else null
        )
        Spacer(Modifier.height(8.dp))

        if (searchQuery.isNotBlank()) {
            // ── Client search results ─────────────────────────────────────
            if (filteredClients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("לא נמצאו לקוחות", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(filteredClients, key = { it.id }) { client ->
                        ClientSearchRow(client = client, onClick = { onOpenClientDetail(client) })
                    }
                }
            }
        } else {
            // ── Payment history grouped by month ──────────────────────────
            if (payments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "אין פעילות ב-14 הימים האחרונים",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    groupedPayments.forEach { (yearMonth, groupPayments, monthTotal) ->
                        item(key = "header_${yearMonth.first}_${yearMonth.second}") {
                            MonthHeader(year = yearMonth.first, month = yearMonth.second, total = monthTotal)
                        }
                        items(groupPayments, key = { it.id }) { payment ->
                            HistoryRow(payment, allClients, onOpenClientDetail)
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private val HEBREW_MONTHS = arrayOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
)

@Composable
private fun MonthHeader(year: Int, month: Int, total: Double? = null) {
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val label = if (year == currentYear) HEBREW_MONTHS[month] else "${HEBREW_MONTHS[month]} $year"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (total != null) {
            val totalStr = if (total % 1.0 == 0.0) "₪${total.toInt()}" else "₪${"%.2f".format(total)}"
            Text(
                text = totalStr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ClientSearchRow(client: ClientEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(client.name, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun HistoryRow(
    payment: PaymentEntity,
    allClients: List<ClientEntity>,
    onOpenClientDetail: (ClientEntity) -> Unit
) {
    val displayName = payment.clientName ?: payment.senderName
    val amountStr = if (payment.amount % 1.0 == 0.0) "₪${payment.amount.toInt()}"
                    else "₪${"%.2f".format(payment.amount)}"
    val dateStr = remember(payment.timestamp) {
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(payment.timestamp))
    }
    val sourceLabel = when {
        payment.source.startsWith("bit")    -> "ביט"
        payment.source.startsWith("paybox") -> "פייבוקס"
        else -> payment.source
    }
    val dotColor = when {
        payment.source.startsWith("bit")    -> Color(0xFF90CAF9)
        payment.source.startsWith("paybox") -> Color(0xFFCE93D8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val client = if (payment.clientId != null) allClients.find { it.id == payment.clientId } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (client != null) Modifier.clickable { onOpenClientDetail(client) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Colored source dot
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(sourceLabel, style = MaterialTheme.typography.bodySmall, color = dotColor)
                if (payment.docNum != null) {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "קבלה #${payment.docNum}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF81C784)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amountStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Client detail screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ClientDetailScreen(
    modifier: Modifier = Modifier,
    client: ClientEntity,
    payments: List<PaymentEntity>,
    allClients: List<ClientEntity>,
    onBack: () -> Unit,
    onWhatsApp: (String) -> Unit
) {
    val groupedPayments = remember(payments) {
        val cal = java.util.Calendar.getInstance()
        val map = LinkedHashMap<Pair<Int, Int>, MutableList<PaymentEntity>>()
        payments.forEach { p ->
            cal.timeInMillis = p.timestamp
            val key = cal.get(java.util.Calendar.YEAR) to cal.get(java.util.Calendar.MONTH)
            map.getOrPut(key) { mutableListOf() }.add(p)
        }
        map.entries.map { (key, list) -> Triple(key, list.toList(), list.sumOf { it.amount }) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזור")
            }
            Text(
                text = client.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider()

        // Payment list
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (payments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("אין תשלומים", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                groupedPayments.forEach { (yearMonth, groupPayments, monthTotal) ->
                    item(key = "header_${yearMonth.first}_${yearMonth.second}") {
                        MonthHeader(year = yearMonth.first, month = yearMonth.second, total = monthTotal)
                    }
                    items(groupPayments, key = { it.id }) { payment ->
                        HistoryRow(payment = payment, allClients = allClients, onOpenClientDetail = {})
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        // WhatsApp button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = { client.phone?.let { onWhatsApp(it) } },
                enabled = client.phone != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
            ) {
                Text("שלח תזכורת בוואטסאפ")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overdue clients tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverdueClientsScreen(
    modifier: Modifier = Modifier,
    overdueClients: List<OverdueClient>,
    filterDays: Int,
    onFilterChanged: (Int) -> Unit,
    onSendReminder: (ClientEntity) -> Unit,
    onOpenClientDetail: (ClientEntity) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        Text(
            "תזכורות",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterDays == 7,
                onClick = { onFilterChanged(7) },
                label = { Text("מעל שבוע") }
            )
            FilterChip(
                selected = filterDays == 30,
                onClick = { onFilterChanged(30) },
                label = { Text("מעל חודש") }
            )
        }
        Spacer(Modifier.height(8.dp))
        if (overdueClients.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "כל הלקוחות שילמו לאחרונה 👍",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(overdueClients, key = { it.client.id }) { overdueClient ->
                    OverdueClientRow(
                        overdueClient = overdueClient,
                        onSendReminder = { onSendReminder(overdueClient.client) },
                        onOpenDetail = { onOpenClientDetail(overdueClient.client) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun OverdueClientRow(
    overdueClient: OverdueClient,
    onSendReminder: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val daysText = "${overdueClient.daysSinceLastPayment} ימים ללא תשלום"
    val urgencyColor = when {
        overdueClient.daysSinceLastPayment >= 30 -> Color(0xFFEF9A9A)  // light red for 30+
        overdueClient.daysSinceLastPayment >= 14 -> Color(0xFFFFCC80)  // light orange for 14+
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                overdueClient.client.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(daysText, style = MaterialTheme.typography.labelSmall, color = urgencyColor)
        }
        TextButton(
            onClick = onSendReminder,
            enabled = overdueClient.client.phone != null,
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                contentColor = Color(0xFF25D366),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("שלח", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsTab(
    modifier: Modifier,
    isEnabled: Boolean,
    hasPermission: Boolean,
    onToggleListener: () -> Unit,
    onOpenSettings: () -> Unit,
    onSyncClients: () -> Unit,
    onAddFakePayment: () -> Unit,
    onAddFakeOverduePayment: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("הגדרות", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // Listener card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "האזנה להתראות",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "סטטוס: ${if (isEnabled) "פעיל" else "לא פעיל"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "הרשאה: ${if (hasPermission) "מאושרת" else "לא מאושרת"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasPermission) ChipGreenText else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(checked = isEnabled, onCheckedChange = { onToggleListener() })
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("הגדרות הרשאות")
                }
            }
        }

        Button(onClick = onSyncClients, modifier = Modifier.fillMaxWidth()) {
            Text("סנכרן לקוחות")
        }

        // Dev tools separator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                "כלי פיתוח",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        OutlinedButton(onClick = onAddFakePayment, modifier = Modifier.fillMaxWidth()) {
            Text("הוסף תשלום לדוגמה")
        }
        OutlinedButton(onClick = onAddFakeOverduePayment, modifier = Modifier.fillMaxWidth()) {
            Text("הוסף תשלום ישן (בדיקת תזכורות)")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WhatsApp sharing utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun launchWhatsApp(
    context: android.content.Context,
    docUrl: String? = null,
    clientPhone: String?,
    text: String? = null
) {
    val messageText = text ?: "קבלה עבורך 🧾\n${docUrl ?: ""}"
    val intent = if (clientPhone != null) {
        val normalized = normalizeIsraeliPhone(clientPhone)
        Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$normalized&text=${Uri.encode(messageText)}"))
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, messageText)
            setPackage("com.whatsapp")
        }
    }
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, messageText)
        }
        context.startActivity(Intent.createChooser(fallback, null))
    }
}

private fun normalizeIsraeliPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.startsWith("972") -> digits
        digits.startsWith("0")   -> "972${digits.drop(1)}"
        else                     -> "972$digits"
    }
}
