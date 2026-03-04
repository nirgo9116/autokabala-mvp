package com.autokabala.listener

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onOpenSettingsClicked: () -> Unit) {
    val isEnabled by viewModel.isEnabled.collectAsState()
    val paymentStates by viewModel.paymentProcessingStates.collectAsState()
    val allClients by viewModel.allClients.collectAsState()
    val isProcessingShare by viewModel.isProcessingShare.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by remember { mutableStateOf(0) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        )
    }
    // Map of paymentId → explicitly chosen client (overrides MatchResult in the UI)
    var selectedClientsMap by remember { mutableStateOf<Map<Int, ClientEntity>>(emptyMap()) }
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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is MainViewModel.UiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is MainViewModel.UiEvent.ReceiptIssued -> {
                    val actionLabel = if (event.docUrl != null) "שלח בוואטסאפ" else null
                    val result = snackbarHostState.showSnackbar(
                        message = "קבלה הופקה בהצלחה ✓",
                        actionLabel = actionLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed && event.docUrl != null) {
                        launchWhatsApp(context, event.docUrl, event.clientPhone)
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
            // Selecting from the sheet ONLY updates the chip — receipt is issued via button
            onClientSelected = { client ->
                selectedClientsMap = selectedClientsMap + (state.payment.id to client)
                clientSheetFor = null
            },
            // Creating a new client issues immediately (create + issue in one step)
            onCreateClient = { firstName, lastName ->
                val fullName = if (lastName.isBlank()) firstName else "$firstName $lastName"
                viewModel.onCreateClientAndIssueReceiptClicked(state.payment, fullName)
                selectedClientsMap = selectedClientsMap - state.payment.id
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
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "תשלומים") },
                    label = { Text("תשלומים") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "הגדרות") },
                    label = { Text("הגדרות") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> PaymentsTab(
                modifier = Modifier.padding(innerPadding),
                paymentStates = paymentStates,
                selectedClientsMap = selectedClientsMap,
                isProcessingShare = isProcessingShare,
                onIssueReceipt = { payment, client ->
                    viewModel.onIssueReceiptForClientClicked(payment, client)
                    selectedClientsMap = selectedClientsMap - payment.id
                },
                onDeletePayment = { state ->
                    viewModel.onDeletePaymentClicked(state.payment)
                    selectedClientsMap = selectedClientsMap - state.payment.id
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
                onAddFakePayment = { viewModel.onAddFakePaymentClicked() }
            )
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
    selectedClientsMap: Map<Int, ClientEntity>,
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
                    PaymentCard(
                        state = state,
                        selectedClient = selectedClientsMap[state.payment.id],
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

            // ── Auto-send email toggle ────────────────────────────────────────
            effectiveClient?.let { client ->
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
                            tint = if (client.email != null) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "שלח קבלה במייל",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (client.email != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        Switch(
                            checked = client.autoSend && client.email != null,
                            onCheckedChange = { if (client.email != null) onToggleAutoSend(client) },
                            enabled = client.email != null
                        )
                        if (client.email == null) {
                            Box(modifier = Modifier.matchParentSize().clickable { onNoEmailTapped() })
                        }
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
    onAddFakePayment: () -> Unit
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WhatsApp sharing utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun launchWhatsApp(context: android.content.Context, docUrl: String, clientPhone: String?) {
    val text = "קבלה עבורך 🧾\n$docUrl"
    val intent = if (clientPhone != null) {
        val normalized = normalizeIsraeliPhone(clientPhone)
        Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$normalized&text=${Uri.encode(text)}"))
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
    }
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
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
