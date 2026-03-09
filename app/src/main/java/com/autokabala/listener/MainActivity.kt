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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
        // Only process share intent on a fresh launch, not on Activity recreation
        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }
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
    val justIssuedCards by viewModel.justIssuedCards.collectAsState()
    val pendingNewClients by viewModel.pendingNewClients.collectAsState()
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
            onCreateClient = { firstName, lastName, phone, email ->
                val fullName = if (lastName.isBlank()) firstName else "$firstName $lastName"
                viewModel.onConfirmNewClient(state.payment.id, fullName, phone?.ifBlank { null }, email?.ifBlank { null })
                // Clear any existing explicit selection so the pending new client takes priority
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
                onWhatsApp = { phone, message ->
                    launchWhatsApp(context, clientPhone = phone, text = message)
                },
                onUpdateContact = { phone, email ->
                    viewModel.onUpdateClientContact(client, phone?.ifBlank { null }, email?.ifBlank { null })
                },
                onUpdateWhatsAppMessage = { msg ->
                    viewModel.onUpdateClientWhatsAppMessage(client, msg)
                }
            )
        } else {
            when (selectedTab) {
                0 -> PaymentsTab(
                    modifier = Modifier.padding(innerPadding),
                    paymentStates = paymentStates,
                    allClients = allClients,
                    selectedClientIdsMap = selectedClientIdsMap,
                    pendingNewClients = pendingNewClients,
                    justIssuedCards = justIssuedCards,
                    isProcessingShare = isProcessingShare,
                    onIssueReceipt = { payment, client, description ->
                        viewModel.onIssueReceiptForClientClicked(payment, client, description)
                    },
                    onDeletePayment = { state ->
                        viewModel.onDeletePaymentClicked(state.payment)
                    },
                    onOpenClientSheet = { clientSheetFor = it },
                    onSelectClientInline = { paymentId, clientId ->
                        selectedClientIdsMap = selectedClientIdsMap + (paymentId to clientId)
                    },
                    onDismissIssuedCard = { paymentId -> viewModel.onDismissIssuedCard(paymentId) },
                    onFakeIssueReceipt = { payment -> viewModel.onFakeIssueReceiptClicked(payment) },
                    onSendWhatsAppFromCard = { info ->
                        launchWhatsApp(context, docUrl = info.docUrl, clientPhone = info.clientPhone)
                    },
                    onSendEmailFromCard = { info ->
                        // email auto-send triggered separately; no-op for manual send without docNum
                    }
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
    pendingNewClients: Map<Int, PendingNewClient>,
    justIssuedCards: Map<Int, IssuedReceiptInfo>,
    isProcessingShare: Boolean,
    onIssueReceipt: (PaymentEntity, ClientEntity, String) -> Unit,
    onDeletePayment: (PaymentProcessingState) -> Unit,
    onOpenClientSheet: (PaymentProcessingState) -> Unit,
    onSelectClientInline: (Int, String) -> Unit,
    onDismissIssuedCard: (Int) -> Unit,
    onFakeIssueReceipt: (PaymentEntity) -> Unit,
    onSendWhatsAppFromCard: (IssuedReceiptInfo) -> Unit,
    onSendEmailFromCard: (IssuedReceiptInfo) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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

        if (paymentStates.isEmpty() && justIssuedCards.isEmpty()) {
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
                // Issued receipt cards shown above pending
                items(justIssuedCards.entries.toList(), key = { "issued_${it.key}" }) { (paymentId, info) ->
                    IssuedReceiptCard(
                        info = info,
                        onSendWhatsApp = { onSendWhatsAppFromCard(info) },
                        onSendEmail = { onSendEmailFromCard(info) },
                        onDismiss = { onDismissIssuedCard(paymentId) }
                    )
                }
                items(paymentStates, key = { it.payment.id }) { state ->
                    // Derive selectedClient: explicit selection OR pending new client
                    val selectedClient = selectedClientIdsMap[state.payment.id]
                        ?.let { id -> allClients.find { it.id == id } }
                        ?: pendingNewClients[state.payment.id]?.let { pending ->
                            ClientEntity(id = "new:${state.payment.id}", name = pending.name, email = pending.email, phone = pending.phone)
                        }
                    PaymentCard(
                        state = state,
                        allClients = allClients,
                        selectedClient = selectedClient,
                        onIssueReceipt = { client, amount, ts, description ->
                            onIssueReceipt(state.payment.copy(amount = amount, timestamp = ts), client, description)
                        },
                        onDelete = { onDeletePayment(state) },
                        onOpenSheet = { onOpenClientSheet(state) },
                        onSelectClient = { client -> onSelectClientInline(state.payment.id, client.id) },
                        onFakeIssueReceipt = { onFakeIssueReceipt(state.payment) }
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
    allClients: List<ClientEntity>,
    selectedClient: ClientEntity?,
    onIssueReceipt: (ClientEntity, Double, Long, String) -> Unit,
    onDelete: () -> Unit,
    onOpenSheet: () -> Unit,
    onSelectClient: (ClientEntity) -> Unit,
    onFakeIssueReceipt: () -> Unit = {}
) {
    val payment = state.payment
    val isDark = isSystemInDarkTheme()

    val initialAmountStr = remember(payment.amount) {
        if (payment.amount % 1.0 == 0.0) payment.amount.toInt().toString()
        else "%.2f".format(payment.amount)
    }
    val initialDateStr = remember(payment.timestamp) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(payment.timestamp))
    }
    var editedAmountStr   by remember(payment.id) { mutableStateOf(initialAmountStr) }
    var editedDateStr     by remember(payment.id) { mutableStateOf(initialDateStr) }
    var editedDescription by remember(payment.id) { mutableStateOf("") }
    var clientDropdownOpen by remember(payment.id) { mutableStateOf(false) }
    var clientSearchQuery  by remember(payment.id) { mutableStateOf("") }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val focusManager = LocalFocusManager.current

    val isBit = payment.source.startsWith("bit")
    val sourceColor = if (isBit) Color(0xFF90CAF9) else Color(0xFFCE93D8)
    val sourceName  = if (isBit) "ביט" else "פייבוקס"

    val effectiveClient: ClientEntity? = selectedClient
        ?: (state.matchResult as? MatchResult.SingleMatch)?.client

    val filteredClients = remember(clientSearchQuery, allClients) {
        if (clientSearchQuery.length < 2) emptyList()
        else allClients.filter { it.name.contains(clientSearchQuery, ignoreCase = true) }.take(5)
    }

    val heroGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF1B1B2F), Color(0xFF101020)))
    else
        Brush.verticalGradient(listOf(Color(0xFFEDE7FF), Color(0xFFF5F0FF)))
    val onHeroColor    = if (isDark) Color.White else Color(0xFF1A1A1A)
    val onHeroSubColor = onHeroColor.copy(alpha = 0.38f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {

            // ── HERO: gradient top with source badge + OCR name + editable amount ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroGradient)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(sourceColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .border(1.dp, sourceColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(sourceName, color = sourceColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "שם: ${payment.senderName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = onHeroSubColor
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "₪",
                            color = sourceColor,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 3.dp, bottom = 4.dp)
                        )
                        BasicTextField(
                            value = editedAmountStr,
                            onValueChange = { editedAmountStr = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.displaySmall.copy(
                                color = onHeroColor,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            cursorBrush = SolidColor(onHeroColor)
                        )
                    }
                }
            }

            // ── BODY ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClientChipWithDropdown(
                    state = state,
                    selectedClient = selectedClient,
                    effectiveClient = effectiveClient,
                    searchQuery = clientSearchQuery,
                    filteredClients = filteredClients,
                    isOpen = clientDropdownOpen,
                    onToggle = {
                        clientDropdownOpen = !clientDropdownOpen
                        if (clientDropdownOpen) {
                            clientSearchQuery = effectiveClient?.name ?: ""
                            focusManager.clearFocus()
                        }
                    },
                    onQueryChange = { clientSearchQuery = it },
                    onSelectClient = { client ->
                        onSelectClient(client)
                        clientDropdownOpen = false
                    },
                    onOpenSheet = {
                        clientDropdownOpen = false
                        onOpenSheet()
                    }
                )

                OutlinedTextField(
                    value = editedDateStr,
                    onValueChange = { editedDateStr = it },
                    label = { Text("תאריך") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )

                OutlinedTextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it },
                    label = { Text("פרטים") },
                    placeholder = { Text("תיאור שירות / מוצר...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                val parsedAmount = editedAmountStr.replace(",", "").toDoubleOrNull()
                val parsedTs    = runCatching { dateFmt.parse(editedDateStr)?.time }.getOrNull()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            effectiveClient?.let { client ->
                                onIssueReceipt(client, parsedAmount ?: payment.amount, parsedTs ?: payment.timestamp, editedDescription)
                            }
                        },
                        enabled = effectiveClient != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("הפק קבלה") }
                    OutlinedButton(onClick = onDelete) { Text("מחק") }
                }

                if (BuildConfig.DEBUG) {
                    TextButton(onClick = onFakeIssueReceipt, modifier = Modifier.fillMaxWidth()) {
                        Text("🔬 הדמיית הפקת קבלה", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Issued receipt card (replaces PaymentCard after receipt is issued)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun IssuedReceiptCard(
    info: IssuedReceiptInfo,
    onSendWhatsApp: () -> Unit,
    onSendEmail: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val amountStr = info.amount?.let {
        if (it % 1.0 == 0.0) "₪${it.toInt()}" else "₪${"%.2f".format(it)}"
    }

    // ── Colors ────────────────────────────────────────────────────────────────
    val cardBg       = if (isDark) Color(0xFF161622) else Color(0xFFF4F0FF)
    val circleRingBg = if (isDark) Color(0xFF6650A4).copy(alpha = 0.15f) else Color(0xFFEDE7FF)
    val circleRingBorder = if (isDark) Color(0xFF6650A4).copy(alpha = 0.5f) else Color(0xFF7B61FF).copy(alpha = 0.4f)
    val checkmarkColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF7B61FF)
    val titleColor   = if (isDark) Color.White else Color(0xFF1A1A1A)
    val docNumColor  = if (isDark) Color(0xFFD0BCFF).copy(alpha = 0.7f) else Color(0xFF7B61FF)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE0E0E0)
    val sendLabelColor = if (isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF888888)
    val dismissColor = if (isDark) Color.White.copy(alpha = 0.35f) else Color(0xFFAAAAAA)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── D-style circle + checkmark ────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(circleRingBg, RoundedCornerShape(percent = 50))
                    .border(2.dp, circleRingBorder, RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = MaterialTheme.typography.headlineMedium, color = checkmarkColor, fontWeight = FontWeight.Bold)
            }
            Text("קבלה הופקה!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = titleColor)
            if (info.docNum != null) {
                Text("מספר קבלה: ${info.docNum}", style = MaterialTheme.typography.bodySmall, color = docNumColor)
            }

            // ── B-style receipt rows ──────────────────────────────────────────
            if (info.clientName != null || amountStr != null) {
                // Receipt details card (white inner card in light mode)
                val rowsBg = if (isDark) Color.Transparent else Color.White
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = rowsBg
                ) {
                    Column {
                        if (info.clientName != null) {
                            ReceiptSlipRow("לקוח", info.clientName, dividerColor, titleColor)
                        }
                        if (amountStr != null) {
                            ReceiptSlipRow("סכום", amountStr, Color.Transparent,
                                if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32))
                        }
                    }
                }
            }

            HorizontalDivider(color = dividerColor)

            // ── Send buttons ──────────────────────────────────────────────────
            Text("שלח ללקוח", style = MaterialTheme.typography.labelMedium, color = sendLabelColor)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSendWhatsApp,
                    enabled = info.clientPhone != null,
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF43C768),
                        disabledContainerColor = Color(0xFF43C768).copy(alpha = 0.3f)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(painter = painterResource(R.drawable.ic_whatsapp), contentDescription = null, modifier = Modifier.size(22.dp))
                        Text("וואטסאפ", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = onSendEmail,
                    enabled = info.docNum != null,
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        disabledContainerColor = Color(0xFF2196F3).copy(alpha = 0.3f)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(22.dp))
                        Text("מייל", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("סגור", color = dismissColor)
            }
        }
    }
}

@Composable
private fun ReceiptSlipRow(key: String, value: String, dividerColor: Color, valueColor: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
        }
        if (dividerColor != Color.Transparent) {
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 14.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Client chip with inline dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClientChipWithDropdown(
    state: PaymentProcessingState,
    selectedClient: ClientEntity?,
    effectiveClient: ClientEntity?,
    searchQuery: String,
    filteredClients: List<ClientEntity>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectClient: (ClientEntity) -> Unit,
    onOpenSheet: () -> Unit
) {
    val mr = state.matchResult

    val chipBg: Color
    val chipText: Color
    val chipBorder: Color
    val leadingIcon: ImageVector
    val chipLabel: String

    when {
        selectedClient != null -> {
            chipBg = ChipGreenBg; chipText = ChipGreenText; chipBorder = ChipGreenBorder
            leadingIcon = Icons.Default.CheckCircle; chipLabel = selectedClient.name
        }
        mr is MatchResult.SingleMatch && mr.isStrong -> {
            chipBg = ChipGreenBg; chipText = ChipGreenText; chipBorder = ChipGreenBorder
            leadingIcon = Icons.Default.CheckCircle; chipLabel = mr.client.name
        }
        mr is MatchResult.SingleMatch && !mr.isStrong -> {
            chipBg = ChipAmberBg; chipText = ChipAmberText; chipBorder = ChipAmberBorder
            leadingIcon = Icons.Default.HelpOutline; chipLabel = mr.client.name
        }
        mr is MatchResult.MultipleMatches -> {
            chipBg = ChipAmberBg; chipText = ChipAmberText; chipBorder = ChipAmberBorder
            leadingIcon = Icons.Default.HelpOutline; chipLabel = mr.clients.first().name
        }
        else -> {
            chipBg = ChipGrayBg; chipText = ChipGrayText; chipBorder = ChipGrayBorder
            leadingIcon = Icons.Default.Add; chipLabel = "לחץ לבחור לקוח"
        }
    }

    Column {
        // ── Chip row ──
        Surface(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            shape = if (isOpen) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    else RoundedCornerShape(12.dp),
            color = chipBg,
            border = BorderStroke(1.dp, chipBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(leadingIcon, null, tint = chipText, modifier = Modifier.size(18.dp))
                    Text(chipLabel, color = chipText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Icon(
                    if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = chipText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Inline dropdown ──
        AnimatedVisibility(visible = isOpen, enter = fadeIn() + expandVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("חיפוש לקוח...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search)
                )
                filteredClients.forEach { client ->
                    Surface(
                        onClick = { onSelectClient(client) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            client.name,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSheet, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("חיפוש")
                    }
                    Button(onClick = onOpenSheet, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("לקוח חדש")
                    }
                }
            }
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
    onCreateClient: (firstName: String, lastName: String, phone: String?, email: String?) -> Unit
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
                onSubmit = { firstName, lastName, phone, email -> onCreateClient(firstName, lastName, phone, email) }
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
    onSubmit: (firstName: String, lastName: String, phone: String?, email: String?) -> Unit
) {
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName  by remember { mutableStateOf(initialLastName) }
    var phone     by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }

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
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("טלפון (אופציונלי)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("אימייל (אופציונלי)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Button(
            onClick = { onSubmit(firstName, lastName, phone.ifBlank { null }, email.ifBlank { null }) },
            modifier = Modifier.fillMaxWidth(),
            enabled = firstName.isNotBlank()
        ) {
            Text("אישור")
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
            .filter { client ->
                // Match only if a word in the name starts with the query (not just contains)
                client.name.split(" ").any { word -> word.startsWith(q, ignoreCase = true) }
            }
            .sortedWith(compareBy(
                { !it.name.startsWith(q, ignoreCase = true) }, // full name starts with query first
                { it.name }                                    // then alphabetical
            ))
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
        map.entries.map { (key, list) -> Triple(key, list.toList(), list.sumOf { it.issuedAmount ?: it.amount }) }
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
    val displayAmount = payment.issuedAmount ?: payment.amount
    val wasAmountEdited = payment.issuedAmount != null && payment.issuedAmount != payment.amount
    val amountStr = if (displayAmount % 1.0 == 0.0) "₪${displayAmount.toInt()}"
                    else "₪${"%.2f".format(displayAmount)}"
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(amountStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                if (wasAmountEdited) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "סכום עודכן",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    onWhatsApp: (phone: String, message: String) -> Unit,
    onUpdateContact: (phone: String?, email: String?) -> Unit,
    onUpdateWhatsAppMessage: (message: String) -> Unit
) {
    val groupedPayments = remember(payments) {
        val cal = java.util.Calendar.getInstance()
        val map = LinkedHashMap<Pair<Int, Int>, MutableList<PaymentEntity>>()
        payments.forEach { p ->
            cal.timeInMillis = p.timestamp
            val key = cal.get(java.util.Calendar.YEAR) to cal.get(java.util.Calendar.MONTH)
            map.getOrPut(key) { mutableListOf() }.add(p)
        }
        map.entries.map { (key, list) -> Triple(key, list.toList(), list.sumOf { it.issuedAmount ?: it.amount }) }
    }

    val defaultWaMsg = "שלום ${client.name}, רצינו להזכירך לגבי תשלום. תודה! 🙏"
    var editPhone   by remember(client.id) { mutableStateOf(client.phone ?: "") }
    var editEmail   by remember(client.id) { mutableStateOf(client.email ?: "") }
    var editWaMsg   by remember(client.id) { mutableStateOf(client.whatsappMessage ?: defaultWaMsg) }
    var waMsgEdited by remember(client.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
    ) {
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

        LazyColumn(modifier = Modifier.weight(1f)) {
            // Contact edit section
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("פרטי קשר", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("טלפון") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() })
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("אימייל") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onUpdateContact(editPhone.ifBlank { null }, editEmail.ifBlank { null })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("שמור פרטי קשר")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("הודעת תזכורת בוואטסאפ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editWaMsg,
                        onValueChange = { editWaMsg = it; waMsgEdited = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && waMsgEdited) {
                                    onUpdateWhatsAppMessage(editWaMsg)
                                    waMsgEdited = false
                                }
                            },
                        label = { Text("הודעת וואטסאפ") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        keyboardActions = KeyboardActions(onAny = { focusManager.clearFocus() })
                    )
                }
                HorizontalDivider()
            }

            // Payment list
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    val phone = editPhone.ifBlank { client.phone }
                    phone?.let { onWhatsApp(it, editWaMsg) }
                },
                enabled = client.phone != null || editPhone.isNotBlank(),
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
