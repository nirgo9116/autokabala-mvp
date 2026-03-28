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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
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
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autokabala.listener.ui.theme.AutoKabalaListenerTheme
import com.autokabala.listener.BuildConfig
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
            val prefs = remember { getSharedPreferences("autokabala_prefs", android.content.Context.MODE_PRIVATE) }
            var showTutorial by remember { mutableStateOf(!prefs.getBoolean("tutorial_shown", false)) }
            AutoKabalaListenerTheme(dynamicColor = false) {
                val ocrDebugInfo by mainViewModel.ocrDebugInfo.collectAsState()

                // Gallery delete — kept at top level so it's always in composition
                val pendingGalleryDelete by mainViewModel.pendingGalleryDelete.collectAsState()
                val deleteRequestLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { mainViewModel.clearPendingGalleryDelete() }
                LaunchedEffect(pendingGalleryDelete) {
                    val uri = pendingGalleryDelete ?: return@LaunchedEffect
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pi = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                            deleteRequestLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                contentResolver.delete(uri, null, null)
                                mainViewModel.clearPendingGalleryDelete()
                            } catch (e: android.app.RecoverableSecurityException) {
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                                )
                            }
                        } else {
                            mainViewModel.clearPendingGalleryDelete()
                        }
                    } catch (_: Exception) {
                        mainViewModel.clearPendingGalleryDelete()
                    }
                }

                if (showTutorial) {
                    TutorialScreen(onDone = {
                        prefs.edit().putBoolean("tutorial_shown", true).apply()
                        showTutorial = false
                    })
                } else if (BuildConfig.DEBUG && ocrDebugInfo != null) {
                    val capturedInfo = ocrDebugInfo!!
                    OcrCheckScreen(
                        info = capturedInfo,
                        onResult = { isSuccess -> mainViewModel.onTestResultSubmitted(isSuccess) },
                        onDismiss = { mainViewModel.dismissOcrDebug() },
                        onSendToDeveloper = { launchDeveloperFeedback(this@MainActivity, capturedInfo) }
                    )
                } else {
                    MainScreen(
                        viewModel = mainViewModel,
                        onOpenSettingsClicked = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onShowTutorial = { showTutorial = true }
                    )
                }
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
fun MainScreen(viewModel: MainViewModel, onOpenSettingsClicked: () -> Unit, onShowTutorial: () -> Unit) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    BackHandler(enabled = currentScreen == Screen.CLIENT_DETAIL) {
        viewModel.onBackToMain()
    }

    MainTabsScreen(
        viewModel = viewModel,
        context = LocalContext.current,
        onOpenSettingsClicked = onOpenSettingsClicked,
        onShowTutorial = onShowTutorial
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabsScreen(
    viewModel: MainViewModel,
    context: android.content.Context,
    onOpenSettingsClicked: () -> Unit,
    onShowTutorial: () -> Unit
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
    val testResults by viewModel.parseTestResults.collectAsState()
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
                is MainViewModel.UiEvent.ShowError   -> snackbarHostState.showSnackbar(event.message)
                is MainViewModel.UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
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
                    onSelectClient = { paymentId, client ->
                        selectedClientIdsMap = selectedClientIdsMap + (paymentId to client.id)
                    },
                    onDismissIssuedCard = { paymentId -> viewModel.onDismissIssuedCard(paymentId) },
                    onFakeIssueReceipt = { payment -> viewModel.onFakeIssueReceiptClicked(payment) },
                    onSendWhatsAppFromCard = { info ->
                        launchWhatsApp(context, docUrl = info.docUrl, clientPhone = info.clientPhone)
                    },
                    onSendEmailFromCard = { info ->
                        info.docNum?.let { viewModel.onSendEmailFromIssuedCard(it) }
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
                    onAddFakeOverduePayment = { viewModel.onAddFakeOverduePaymentClicked() },
                    onShowTutorial = onShowTutorial,
                    testResults = testResults,
                    onSendFailure = { result -> launchDeveloperFeedbackFromResult(context, result) },
                    onClearResults = { viewModel.clearTestResults() }
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
    onSelectClient: (paymentId: Int, ClientEntity) -> Unit,
    onDismissIssuedCard: (Int) -> Unit,
    onFakeIssueReceipt: (PaymentEntity) -> Unit,
    onSendWhatsAppFromCard: (IssuedReceiptInfo) -> Unit,
    onSendEmailFromCard: (IssuedReceiptInfo) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
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
                        selectedClient = selectedClient,
                        onIssueReceipt = { client, amount, ts, description ->
                            onIssueReceipt(state.payment.copy(amount = amount, timestamp = ts), client, description)
                        },
                        onDelete = { onDeletePayment(state) },
                        onOpenSheet = { onOpenClientSheet(state) },
                        onSelectClient = { client ->
                            onSelectClient(state.payment.id, client)
                        },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentCard(
    state: PaymentProcessingState,
    selectedClient: ClientEntity?,
    onIssueReceipt: (ClientEntity, Double, Long, String) -> Unit,
    onDelete: () -> Unit,
    onOpenSheet: () -> Unit,
    onSelectClient: (ClientEntity) -> Unit = {},
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
    val amountMissing = payment.amount == 0.0
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val focusManager = LocalFocusManager.current

    val isBit = payment.source.startsWith("bit")
    val sourceColor = if (isBit) Color(0xFF90CAF9) else Color(0xFFCE93D8)
    val sourceName  = if (isBit) "ביט" else "פייבוקס"

    val effectiveClient: ClientEntity? = selectedClient
        ?: (state.matchResult as? MatchResult.SingleMatch)?.client

    val heroGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF1B1B2F), Color(0xFF101020)))
    else
        Brush.verticalGradient(listOf(Color(0xFFEDE7FF), Color(0xFFF5F0FF)))
    val onHeroColor    = if (isDark) Color.White else Color(0xFF1A1A1A)
    val onHeroSubColor = onHeroColor.copy(alpha = 0.6f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {

            // ── HERO ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroGradient)
                    .padding(horizontal = 20.dp, vertical = 13.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .background(sourceColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .border(1.dp, sourceColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(sourceName, color = sourceColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "איש קשר",
                                style = MaterialTheme.typography.labelSmall,
                                color = onHeroSubColor
                            )
                            Text(
                                payment.senderName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onHeroColor
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text("₪", color = if (amountMissing) Color(0xFFFF6B6B) else sourceColor,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Spacer(Modifier.width(4.dp))
                            BasicTextField(
                                value = editedAmountStr,
                                onValueChange = { editedAmountStr = it },
                                singleLine = true,
                                modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 24.dp),
                                textStyle = MaterialTheme.typography.displaySmall.copy(
                                    color = if (amountMissing) Color(0xFFFF6B6B) else onHeroColor,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                cursorBrush = SolidColor(if (amountMissing) Color(0xFFFF6B6B) else onHeroColor)
                            )
                        }
                    }
                    if (amountMissing) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "אופס משהו השתבש — הזן סכום ידנית",
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // ── RECEIPT FORM BODY ────────────────────────────────────────────
            val paperBg    = Color(0xFFF4F1EB)
            val divColor   = Color(0xFFDDD8CC)
            val lblColor   = Color(0xFF888888)
            val underlineC = Color(0xFFBBBBBB)
            val valueC     = Color(0xFF222222)
            val amtC       = if (isBit) Color(0xFF1A3A8A) else Color(0xFF4A1270)
            val hdrGradient = if (isBit)
                Brush.horizontalGradient(listOf(Color(0xFF12357A), Color(0xFF1A55C4)))
            else
                Brush.horizontalGradient(listOf(Color(0xFF4A1270), Color(0xFF8A2BE2)))

            val parsedAmt = editedAmountStr.replace(",", "").toDoubleOrNull() ?: payment.amount
            val parsedTs  = runCatching { dateFmt.parse(editedDateStr)?.time }.getOrNull()
            val totalStr  = "₪${"%.2f".format(parsedAmt)}"

            Column(modifier = Modifier.background(paperBg)) {
                // ── Header stripe ─────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(hdrGradient)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("העסק שלי", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("קבלה", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                }

                // ── Client row ("לכבוד") ──────────────────────────────────────
                val mr = state.matchResult
                val showBubbles = selectedClient == null
                    && mr is MatchResult.MultipleMatches
                    && mr.clients.size in 2..3
                val bubbleC = if (isBit) Color(0xFF1565C0) else Color(0xFF6A1B9A)

                if (showBubbles) {
                    val bubbleClients = (mr as MatchResult.MultipleMatches).clients
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                    ) {
                        Text("לכבוד — בחר לקוח:", color = lblColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            bubbleClients.forEach { client ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(1.5.dp, bubbleC.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                        .background(bubbleC.copy(alpha = 0.08f))
                                        .clickable { onSelectClient(client) }
                                        .padding(horizontal = 18.dp, vertical = 10.dp)
                                ) {
                                    Text(client.name, color = bubbleC, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = divColor)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("לכבוד", color = lblColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (effectiveClient != null) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF43A047).copy(alpha = 0.14f))
                                    .border(1.5.dp, Color(0xFF43A047).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                    .clickable { onOpenSheet() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("✓", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
                                Text(effectiveClient.name, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("▼", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .border(1.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(20.dp))
                                    .clickable { onOpenSheet() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("חפש לקוח...", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                    HorizontalDivider(color = divColor)
                }

                // ── Date row ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("תאריך", color = lblColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .drawBehind {
                                val sw = 1.5.dp.toPx()
                                drawLine(underlineC, Offset(0f, size.height), Offset(size.width, size.height), sw)
                            }
                            .padding(bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicTextField(
                            value = editedDateStr, onValueChange = { editedDateStr = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = valueC, fontWeight = FontWeight.SemiBold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            cursorBrush = SolidColor(valueC),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp), tint = underlineC)
                    }
                }
                HorizontalDivider(color = divColor)

                // ── Table header ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEDE9E0))
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("תיאור השירות", color = Color(0xFF999999), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("סכום", color = Color(0xFF999999), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                // ── Item row ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Description field
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .drawBehind {
                                val sw = 1.5.dp.toPx()
                                drawLine(underlineC, Offset(0f, size.height), Offset(size.width, size.height), sw)
                            }
                            .padding(bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicTextField(
                            value = editedDescription, onValueChange = { editedDescription = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = valueC, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            cursorBrush = SolidColor(valueC),
                            decorationBox = { inner ->
                                Box {
                                    if (editedDescription.isEmpty()) {
                                        Text("הזן פרטים...", style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFBBBBBB), fontStyle = FontStyle.Italic))
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp), tint = underlineC)
                    }
                    // Amount field
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val amtFieldColor = if (amountMissing) Color(0xFFFF6B6B) else amtC
                        Row(
                            modifier = Modifier
                                .drawBehind {
                                    val sw = 1.5.dp.toPx()
                                    drawLine(if (amountMissing) Color(0xFFFF6B6B) else underlineC,
                                        Offset(0f, size.height), Offset(size.width, size.height), sw)
                                }
                                .padding(bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("₪", color = amtFieldColor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = editedAmountStr, onValueChange = { editedAmountStr = it },
                                singleLine = true,
                                modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 32.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = amtFieldColor, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                cursorBrush = SolidColor(amtFieldColor)
                            )
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp),
                                tint = if (amountMissing) Color(0xFFFF6B6B) else underlineC)
                        }
                    }
                }
                HorizontalDivider(color = divColor)

                // ── Payment method ────────────────────────────────────────────
                val pmDotC = if (isBit) Color(0xFF1565C0) else Color(0xFF6A1B9A)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(8.dp).background(pmDotC, CircleShape))
                    Text("שולם באמצעות $sourceName", color = Color(0xFF777777), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = divColor)

                // ── Total row ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE5E0D5))
                        .drawBehind {
                            drawLine(Color(0xFFC8C2B2), Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
                        }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("סה״כ", color = Color(0xFF222222), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(totalStr, color = Color(0xFF12357A), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // ── iCount footer ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEDE9E0))
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        "יופק אוטומטית ע״י iCount",
                        color = Color(0xFF555555),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── ACTION BUTTONS ────────────────────────────────────────────────
            val issueGrad = if (isBit)
                Brush.horizontalGradient(listOf(Color(0xFF1A55C4), Color(0xFF90CAF9)))
            else
                Brush.horizontalGradient(listOf(Color(0xFF7B61FF), Color(0xFFCE93D8)))

            val btnEnabled = effectiveClient != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .alpha(if (btnEnabled) 1f else 0.45f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(issueGrad)
                        .clickable(enabled = btnEnabled) {
                            effectiveClient?.let { c ->
                                onIssueReceipt(c, parsedAmt, parsedTs ?: payment.timestamp, editedDescription)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("הפק קבלה", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(14.dp))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗑", fontSize = 22.sp)
                }
            }

            if (BuildConfig.DEBUG) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF121212))) {
                    TextButton(onClick = onFakeIssueReceipt, modifier = Modifier.fillMaxWidth()) {
                        Text("🔬 הדמיית הפקת קבלה", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    // ── Colors ────────────────────────────────────────────────────────────────
    val cardBg       = if (isDark) Color(0xFF161622) else Color(0xFFF4F0FF)
    val circleRingBg = if (isDark) Color(0xFF6650A4).copy(alpha = 0.15f) else Color(0xFFEDE7FF)
    val circleRingBorder = if (isDark) Color(0xFF6650A4).copy(alpha = 0.5f) else Color(0xFF7B61FF).copy(alpha = 0.4f)
    val checkmarkColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF7B61FF)
    val titleColor   = if (isDark) Color.White else Color(0xFF1A1A1A)
    val docNumColor  = if (isDark) Color(0xFFD0BCFF).copy(alpha = 0.8f) else Color(0xFF7B61FF)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE0E0E0)
    val sendLabelColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF555555)
    val dismissColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF888888)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── D-style circle + checkmark ────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(circleRingBg, RoundedCornerShape(percent = 50))
                    .border(2.dp, circleRingBorder, RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = MaterialTheme.typography.headlineLarge, color = checkmarkColor, fontWeight = FontWeight.Bold)
            }
            Text("קבלה הופקה!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = titleColor)
            if (info.docNum != null) {
                Text("מספר קבלה: ${info.docNum}", style = MaterialTheme.typography.bodyMedium, color = docNumColor)
            }

            // ── B-style receipt rows ──────────────────────────────────────────
            if (info.clientName != null || amountStr != null || info.timestamp != null) {
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
                        val hasDate = info.timestamp != null
                        if (amountStr != null) {
                            ReceiptSlipRow("סכום", amountStr,
                                if (hasDate) dividerColor else Color.Transparent,
                                if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32))
                        }
                        if (info.timestamp != null) {
                            val dateStr = remember(info.timestamp) { dateFmt.format(Date(info.timestamp)) }
                            ReceiptSlipRow("תאריך", dateStr, Color.Transparent, titleColor)
                        }
                    }
                }
            }

            HorizontalDivider(color = dividerColor)

            // ── Send buttons ──────────────────────────────────────────────────
            Text("שלח קבלה ללקוח", style = MaterialTheme.typography.titleSmall, color = sendLabelColor)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSendWhatsApp,
                    enabled = true,
                    modifier = Modifier.weight(1f).height(68.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF81C784)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painter = painterResource(R.drawable.ic_whatsapp), contentDescription = null, modifier = Modifier.size(22.dp))
                        Text("וואטסאפ", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Button(
                    onClick = onSendEmail,
                    enabled = info.docNum != null,
                    modifier = Modifier.weight(1f).height(68.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7B61FF),
                        disabledContainerColor = Color(0xFF7B61FF).copy(alpha = 0.3f)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(22.dp))
                        Text("מייל", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("סגור", style = MaterialTheme.typography.bodyLarge, color = dismissColor)
            }
        }
    }
}

@Composable
private fun ReceiptSlipRow(key: String, value: String, dividerColor: Color, valueColor: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
        }
        if (dividerColor != Color.Transparent) {
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 14.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Client chip — tapping opens the bottom sheet (original behavior)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClientChipField(
    state: PaymentProcessingState,
    selectedClient: ClientEntity?,
    effectiveClient: ClientEntity?,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val mr = state.matchResult

    val chipBg: Color; val chipText: Color; val chipBorder: Color
    val subtitle: String

    when {
        selectedClient != null -> {
            chipBg = ChipGreenBg; chipText = ChipGreenText; chipBorder = ChipGreenBorder
            subtitle = "לקוח קיים"
        }
        mr is MatchResult.SingleMatch && mr.isStrong -> {
            chipBg = ChipGreenBg; chipText = ChipGreenText; chipBorder = ChipGreenBorder
            subtitle = "התאמה מלאה"
        }
        mr is MatchResult.SingleMatch && !mr.isStrong -> {
            chipBg = ChipAmberBg; chipText = ChipAmberText; chipBorder = ChipAmberBorder
            subtitle = "התאמה חלקית"
        }
        mr is MatchResult.MultipleMatches -> {
            chipBg = ChipAmberBg; chipText = ChipAmberText; chipBorder = ChipAmberBorder
            subtitle = "מספר התאמות"
        }
        else -> {
            chipBg = ChipGrayBg; chipText = ChipGrayText; chipBorder = ChipGrayBorder
            subtitle = ""
        }
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = chipBg,
        border = BorderStroke(1.dp, chipBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (effectiveClient != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(effectiveClient.name, color = chipText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, color = chipText.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Icon(Icons.Default.Add, null, tint = chipText, modifier = Modifier.size(18.dp))
                Text("לחץ לבחור לקוח", color = chipText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
            }
            Icon(Icons.Default.KeyboardArrowDown, null, tint = chipText, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card-style editable fields matching Design 1 / Design 3
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaymentInfoField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isDark: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    val bgColor  = if (isDark) Color(0xFF1E1E1E) else Color.White
    val bdrColor = if (isDark) Color(0xFF333333) else Color(0xFFF0F0F0)
    val lblColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF888888)
    val txtColor = if (isDark) Color.White else Color(0xFF7B61FF)

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = bgColor, border = BorderStroke(1.dp, bdrColor)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(label, color = lblColor, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value, onValueChange = onValueChange, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = txtColor, fontWeight = FontWeight.Medium),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    cursorBrush = SolidColor(txtColor),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = lblColor)
            }
        }
    }
}

@Composable
private fun PaymentDescField(
    value: String,
    onValueChange: (String) -> Unit,
    isDark: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    val bgColor  = if (isDark) Color(0xFF1E1E1E) else Color.White
    val bdrColor = if (isDark) Color(0xFF383838) else Color(0xFFD9D0F5)
    val lblColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF999999)
    val txtColor = if (isDark) Color.White else Color(0xFF444444)
    val phColor  = if (isDark) Color(0xFF999999) else Color(0xFFBBBBBB)

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = bgColor, border = BorderStroke(1.dp, bdrColor)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text("פרטים / תיאור שירות", color = lblColor, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(5.dp))
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = txtColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                cursorBrush = SolidColor(txtColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text("הוסף תיאור לקבלה...", style = MaterialTheme.typography.bodyLarge.copy(color = phColor))
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
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
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsTab(
    modifier: Modifier,
    isEnabled: Boolean,
    hasPermission: Boolean,
    onToggleListener: () -> Unit,
    onOpenSettings: () -> Unit,
    onSyncClients: () -> Unit,
    onAddFakePayment: () -> Unit,
    onAddFakeOverduePayment: () -> Unit,
    onShowTutorial: () -> Unit = {},
    testResults: List<ParseTestResult> = emptyList(),
    onSendFailure: (ParseTestResult) -> Unit = {},
    onClearResults: () -> Unit = {}
) {
    var showTestResults by remember { mutableStateOf(false) }

    if (showTestResults) {
        ModalBottomSheet(onDismissRequest = { showTestResults = false }) {
            TestResultsSheet(
                results = testResults,
                onSendFailure = onSendFailure,
                onClear = { onClearResults(); showTestResults = false }
            )
        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

        OutlinedButton(onClick = onShowTutorial, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("הצג מדריך למשתמש")
        }

        if (BuildConfig.DEBUG && testResults.isNotEmpty()) {
            val ok = testResults.count { it.isSuccess }
            val fail = testResults.count { !it.isSuccess }
            OutlinedButton(
                onClick = { showTestResults = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("תוצאות בדיקה: ✓$ok  ✗$fail")
            }
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
// OCR check screen (תקין / לא תקין)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OcrCheckScreen(
    info: OcrDebugInfo,
    onResult: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSendToDeveloper: () -> Unit = {}
) {
    val sourceLabel = if (info.source == "paybox") "Paybox" else "ביט"
    val dateStr = info.parsedTimestamp?.let {
        java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(java.util.Date(it))
    }
    val nameOk   = !info.parsedName.isNullOrBlank()
    val amountOk = (info.parsedAmount ?: 0.0) > 0.0
    var markedFailed by remember { mutableStateOf(false) }
    var markedOk    by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, info.imageUri) {
        value = try {
            context.contentResolver.openInputStream(info.imageUri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) { null }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("בדיקת פענוח — $sourceLabel", style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                modifier = Modifier.height(48.dp)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Screenshot image — takes all available space above the card
            bitmap?.let { bmp ->
                Image(
                    painter = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = "תמונת התשלום",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
            } ?: Spacer(Modifier.weight(1f))
            // Parsed data card
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("נתוני הפענוח", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (nameOk) "✓" else "✗", color = if (nameOk) Color(0xFF4CAF50) else Color(0xFFF44336))
                        Column {
                            Text("שם", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(info.parsedName ?: "לא זוהה", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (amountOk) "✓" else "✗", color = if (amountOk) Color(0xFF4CAF50) else Color(0xFFF44336))
                        Column {
                            Text("סכום", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (amountOk) "₪${info.parsedAmount}" else "לא זוהה", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    dateStr?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = Color(0xFF4CAF50))
                            Column {
                                Text("תאריך", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            // Fixed bottom action area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 6.dp, bottom = 4.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("האם הנתונים תואמים לתשלום בביט?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { markedFailed = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) { Text("✗  לא תקין") }
                    Button(
                        onClick = { markedOk = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("✓  תקין") }
                }
                if (markedOk) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                onResult(true)
                                try {
                                    context.startActivity(
                                        android.content.Intent().apply {
                                            setClassName("com.bnhp.payments.paymentsapp", "com.payments.bitapp.base.activity.MainActivity")
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("חזור לביט") }
                        OutlinedButton(
                            onClick = {
                                onResult(true)
                                context.packageManager.getLaunchIntentForPackage("com.payboxapp")
                                    ?.let { context.startActivity(it) }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("חזור לפייבוקס") }
                    }
                }
                if (markedFailed) {
                    Button(
                        onClick = { onSendToDeveloper(); onResult(false) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("📤  שלח למפתח") }
                    TextButton(
                        onClick = { onResult(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("המשך ללא שליחה", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Test results bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TestResultsSheet(
    results: List<ParseTestResult>,
    onSendFailure: (ParseTestResult) -> Unit,
    onClear: () -> Unit
) {
    val fmt = remember { java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("תוצאות בדיקה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClear) { Text("נקה הכל", color = MaterialTheme.colorScheme.error) }
        }
        val ok = results.count { it.isSuccess }
        val fail = results.count { !it.isSuccess }
        Text("✓ $ok תקין   ✗ $fail לא תקין", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        results.reversed().forEach { result ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (result.isSuccess) "✓" else "✗",
                        color = if (result.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text("#${result.id}  ${result.parsedName ?: "?"}  ${result.parsedAmount?.let { "₪$it" } ?: ""}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(fmt.format(java.util.Date(result.timestamp)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!result.isSuccess) {
                    TextButton(onClick = { onSendFailure(result) }) { Text("שלח") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Developer feedback sharing
// ─────────────────────────────────────────────────────────────────────────────

private fun launchDeveloperFeedbackFromResult(context: android.content.Context, result: ParseTestResult) {
    val text = buildString {
        append("🔍 AutoKabala OCR Report #${result.id}\n")
        append("מקור: ${if (result.source == "bit") "ביט" else "פייבוקס"}\n")
        append("שם שזוהה: ${result.parsedName ?: "לא זוהה"}\n")
        append("סכום שזוהה: ${result.parsedAmount?.let { "₪$it" } ?: "לא זוהה"}\n\n")
        append("--- Tesseract ---\n${result.tesseractText}\n\n")
        append("--- ML Kit ---\n${result.mlKitText}")
    }
    val imageUri = result.imagePath?.let { path ->
        try {
            val file = java.io.File(path)
            if (file.exists()) androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            ) else null
        } catch (_: Exception) { null }
    }
    val intent = if (imageUri != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
    } else {
        Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse("whatsapp://send?phone=972506818414&text=${android.net.Uri.encode(text)}"))
    }
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(fallback, null))
    }
}

private fun launchDeveloperFeedback(context: android.content.Context, info: OcrDebugInfo) {
    val text = buildString {
        append("🔍 AutoKabala OCR Report\n")
        append("מקור: ${if (info.source == "bit") "ביט" else "פייבוקס"}\n")
        append("שם שזוהה: ${info.parsedName ?: "לא זוהה"}\n")
        append("סכום שזוהה: ${info.parsedAmount?.let { "₪$it" } ?: "לא זוהה"}\n\n")
        append("--- Tesseract ---\n${info.tesseractText}\n\n")
        append("--- ML Kit ---\n${info.mlKitText}")
    }
    val waWithImage = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, info.imageUri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage("com.whatsapp")
    }
    try {
        context.startActivity(waWithImage)
    } catch (_: android.content.ActivityNotFoundException) {
        val waText = Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse("whatsapp://send?phone=972506818414&text=${android.net.Uri.encode(text)}"))
        try {
            context.startActivity(waText)
        } catch (_: android.content.ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(fallback, null))
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

@Composable
private fun OcrDebugScreen(
    info: OcrDebugInfo,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val sourceLabel = if (info.source == "paybox") "Paybox" else "Bit"
    val nameOk      = !info.parsedName.isNullOrBlank()
    val amountOk    = (info.parsedAmount ?: 0.0) > 0.0
    val dateOk      = (info.parsedTimestamp ?: 0L) > 0L
    val dateStr     = info.parsedTimestamp?.let {
        SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(it))
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("בדיקת OCR — $sourceLabel") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ביטול")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("בטל")
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    enabled = nameOk && amountOk
                ) {
                    Text("המשך להפקת קבלה")
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // ── תוצאה ──
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("תוצאת פענוח", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (nameOk) "✓" else "✗", color = if (nameOk) Color(0xFF4CAF50) else Color(0xFFF44336))
                            Text("שם: ${info.parsedName ?: "לא זוהה"}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (amountOk) "✓" else "✗", color = if (amountOk) Color(0xFF4CAF50) else Color(0xFFF44336))
                            Text("סכום: ${if (amountOk) "₪${info.parsedAmount}" else "לא זוהה"}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (dateOk) "✓" else "✗", color = if (dateOk) Color(0xFF4CAF50) else Color(0xFFF44336))
                            Text("תאריך: ${dateStr ?: "לא זוהה"}")
                        }
                    }
                }
            }
            item {
                // ── Tesseract raw ──
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tesseract (עברית)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = info.tesseractText.ifBlank { "(ריק)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
            item {
                // ── ML Kit raw ──
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ML Kit (Latin)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = info.mlKitText.ifBlank { "(ריק)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
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
