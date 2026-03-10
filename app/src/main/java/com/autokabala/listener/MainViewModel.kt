package com.autokabala.listener

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

data class OverdueClient(val client: ClientEntity, val daysSinceLastPayment: Int)
data class IssuedReceiptInfo(
    val docUrl: String?,
    val clientPhone: String?,
    val docNum: String? = null,
    val clientName: String? = null,
    val amount: Double? = null,
    val timestamp: Long? = null
)
data class PendingNewClient(val name: String, val phone: String?, val email: String?)

// Represents the result of matching a payment to existing clients
sealed class MatchResult {
    object NoMatch : MatchResult()
    data class SingleMatch(val client: ClientEntity, val isStrong: Boolean = true) : MatchResult()
    data class MultipleMatches(val clients: List<ClientEntity>) : MatchResult()
}

// Holds the combined state for a single payment, ready for the UI
data class PaymentProcessingState(
    val payment: PaymentEntity,
    val matchResult: MatchResult
)

enum class Screen { MAIN, CLIENT_DETAIL }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val receiptRepository = (application as AutoKabalaApplication).receiptRepository

    // --- Navigation State ---
    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedClient = MutableStateFlow<ClientEntity?>(null)
    val selectedClient: StateFlow<ClientEntity?> = _selectedClient.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    fun onTabSelected(index: Int) {
        _selectedTabIndex.value = index
        _currentScreen.value = Screen.MAIN
    }

    private val _justIssuedCards = MutableStateFlow<Map<Int, IssuedReceiptInfo>>(emptyMap())
    val justIssuedCards: StateFlow<Map<Int, IssuedReceiptInfo>> = _justIssuedCards.asStateFlow()

    private val _pendingNewClients = MutableStateFlow<Map<Int, PendingNewClient>>(emptyMap())
    val pendingNewClients: StateFlow<Map<Int, PendingNewClient>> = _pendingNewClients.asStateFlow()

    // --- Payment History (last 90 days) ---
    private val historyWindowStart = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
    val paymentHistory: StateFlow<List<PaymentEntity>> =
        receiptRepository.getRecentPayments(since = historyWindowStart)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val clientPayments: StateFlow<List<PaymentEntity>> =
        _selectedClient.flatMapLatest { client ->
            if (client == null) flowOf(emptyList())
            else receiptRepository.getPaymentsByClientId(client.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI State ---
    private val pendingPayments: StateFlow<List<PaymentEntity>> = receiptRepository.pendingPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allClients: StateFlow<List<ClientEntity>> = receiptRepository.allClients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Overdue clients ---
    private val _overdueFilterDays = MutableStateFlow(7)
    val overdueFilterDays: StateFlow<Int> = _overdueFilterDays.asStateFlow()

    val overdueClients: StateFlow<List<OverdueClient>> = combine(
        receiptRepository.getLastPaymentPerClient(),
        allClients,
        _overdueFilterDays
    ) { lastPayments, clients, filterDays ->
        val now = System.currentTimeMillis()
        val filterMs = filterDays.toLong() * 24 * 3600 * 1000
        val clientMap = clients.associateBy { it.id }
        lastPayments
            .filter { (now - it.lastPaymentTime) > filterMs }
            .mapNotNull { lp ->
                val client = clientMap[lp.clientId] ?: return@mapNotNull null
                val days = ((now - lp.lastPaymentTime) / (24L * 3600 * 1000)).toInt()
                OverdueClient(client, days)
            }
            .sortedByDescending { it.daysSinceLastPayment }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onOverdueFilterChanged(days: Int) { _overdueFilterDays.value = days }

    val paymentProcessingStates: StateFlow<List<PaymentProcessingState>> = combine(
        pendingPayments, allClients
    ) { payments, clients ->
        payments.map { payment ->
            val senderWords = payment.senderName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val senderFirst = senderWords.firstOrNull() ?: ""

            // Step 1: clients where senderFirst matches any word in the client name
            val firstNameMatches = if (senderFirst.isBlank()) emptyList() else {
                clients.filter { client ->
                    val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    clientWords.any { wordsMatch(it, senderFirst) }
                }
            }

            // Step 2: among those, clients where ALL client-name words appear in senderWords.
            // Direction is intentionally reversed vs. the old logic: we check that every word
            // of the CLIENT's name is found somewhere in the (possibly noisy) sender word list,
            // rather than requiring every sender word to appear in the client name.
            // This handles bit_share OCR lines like "יריב באייר 126 קג טגנסקי" correctly:
            // client "יריב טגנסקי" matches because both its words exist in the sender list.
            val fullNameMatches = if (senderWords.size >= 2) {
                firstNameMatches.filter { client ->
                    val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    clientWords.size >= 2 && clientWords.all { cw ->
                        senderWords.any { sw -> wordsMatch(sw, cw) }
                    }
                }
            } else emptyList()

            val matchingClients = fullNameMatches.ifEmpty { firstNameMatches }
                .sortedByDescending { client ->
                    val clientFirst = client.name.trim().split(Regex("\\s+")).firstOrNull() ?: ""
                    if (wordsMatch(clientFirst, senderFirst)) 1 else 0
                }
            val isStrong = fullNameMatches.isNotEmpty() // green only when full name confirmed
            val matchResult = when {
                matchingClients.isEmpty() -> MatchResult.NoMatch
                matchingClients.size == 1 -> MatchResult.SingleMatch(matchingClients.first(), isStrong)
                else -> MatchResult.MultipleMatches(matchingClients)
            }

            PaymentProcessingState(payment, matchResult)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val isEnabled: StateFlow<Boolean> = ListenerManager.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isProcessingShare = MutableStateFlow(false)
    val isProcessingShare: StateFlow<Boolean> = _isProcessingShare.asStateFlow()

    // --- One-time Events ---
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent: Flow<UiEvent> = _uiEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            receiptRepository.duplicatePaymentEvent.collect {
                _uiEvent.send(UiEvent.ShowError("התשלום כבר קיים במערכת"))
            }
        }
    }

    sealed class UiEvent {
        data class ShowError(val message: String) : UiEvent()
        data class ShowMessage(val message: String) : UiEvent()
    }

    // --- Event Handlers ---

    fun onEnableDisableClicked() {
        if (isEnabled.value) {
            ListenerManager.disable()
        } else {
            ListenerManager.enable()
        }
    }

    fun onIssueReceiptForClientClicked(payment: PaymentEntity, client: ClientEntity, description: String = "") {
        viewModelScope.launch {
            if (client.id.startsWith("new:")) {
                val pending = _pendingNewClients.value[payment.id] ?: run {
                    _uiEvent.send(UiEvent.ShowError("לא נמצא לקוח חדש לתשלום זה."))
                    return@launch
                }
                val docUrl = receiptRepository.createClientAndIssueReceipt(
                    payment, pending.name, pending.phone, pending.email, description
                )
                if (docUrl != null) {
                    _justIssuedCards.value = _justIssuedCards.value + (payment.id to IssuedReceiptInfo(docUrl, pending.phone, clientName = pending.name, amount = payment.amount, timestamp = payment.timestamp))
                    _pendingNewClients.value = _pendingNewClients.value - payment.id
                } else {
                    _uiEvent.send(UiEvent.ShowError("שגיאה ביצירת לקוח או הפקת קבלה."))
                }
            } else {
                val outcome = receiptRepository.issueReceiptForClient(payment, client, description)
                if (outcome != null) {
                    val phone = client.phone ?: receiptRepository.fetchAndCachePhone(client.id)
                    _justIssuedCards.value = _justIssuedCards.value + (payment.id to IssuedReceiptInfo(outcome.docUrl, phone, outcome.docNum, clientName = client.name, amount = payment.amount, timestamp = payment.timestamp))
                } else {
                    _uiEvent.send(UiEvent.ShowError("שגיאה בהפקת קבלה. בדוק חיבור לאינטרנט ונסה שוב."))
                }
            }
        }
    }

    fun onConfirmNewClient(paymentId: Int, name: String, phone: String?, email: String?) {
        _pendingNewClients.value = _pendingNewClients.value + (paymentId to PendingNewClient(name, phone, email))
    }

    fun onDismissIssuedCard(paymentId: Int) {
        _justIssuedCards.value = _justIssuedCards.value - paymentId
    }

    fun onUpdateClientContact(client: ClientEntity, phone: String?, email: String?) {
        viewModelScope.launch {
            receiptRepository.updateClientContact(client.id, phone, email)
        }
    }

    fun onUpdateClientWhatsAppMessage(client: ClientEntity, message: String) {
        viewModelScope.launch {
            receiptRepository.updateClientWhatsAppMessage(client.id, message)
        }
    }

    fun onToggleAutoSend(client: ClientEntity) {
        viewModelScope.launch {
            receiptRepository.toggleAutoSend(client)
        }
    }

    fun onNoEmailForAutoSend() {
        viewModelScope.launch {
            _uiEvent.send(UiEvent.ShowError("יש להזין כתובת מייל ללקוח כדי לאפשר שליחה אוטומטית"))
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

    fun onFakeIssueReceiptClicked(payment: PaymentEntity) {
        viewModelScope.launch {
            val matchedClientName = paymentProcessingStates.value
                .find { it.payment.id == payment.id }
                ?.matchResult
                ?.let { it as? MatchResult.SingleMatch }
                ?.client?.name
            val info = receiptRepository.addFakeReceipt(payment)
            val finalInfo = if (matchedClientName != null) info.copy(clientName = matchedClientName) else info
            _justIssuedCards.value = _justIssuedCards.value + (payment.id to finalInfo)
        }
    }

    fun onSendEmailFromIssuedCard(docNum: String) {
        viewModelScope.launch {
            val success = ReceiptApiClient.sendDocumentByEmail(docNum)
            if (success) {
                _uiEvent.send(UiEvent.ShowMessage("מייל נשלח ללקוח ע״י iCount ✓"))
            } else {
                _uiEvent.send(UiEvent.ShowError("שליחת המייל נכשלה — בדוק חיבור לאינטרנט"))
            }
        }
    }

    fun onAddFakePaymentClicked() {
        viewModelScope.launch {
            receiptRepository.addFakePayment()
        }
    }

    fun onAddFakeOverduePaymentClicked() {
        viewModelScope.launch {
            receiptRepository.addFakeOverduePayment()
        }
    }

    fun onOpenClientDetail(client: ClientEntity) {
        _selectedClient.value = client
        _currentScreen.value = Screen.CLIENT_DETAIL
        if (client.phone == null) {
            viewModelScope.launch {
                val phone = receiptRepository.fetchAndCachePhone(client.id)
                if (phone != null) {
                    _selectedClient.value = _selectedClient.value?.copy(phone = phone)
                }
            }
        }
    }

    fun onBackToMain() {
        _currentScreen.value = Screen.MAIN
        _selectedClient.value = null
    }

    fun onShareIntentReceived(imageUri: Uri) {
        viewModelScope.launch {
            _isProcessingShare.value = true
            try {
                val context = getApplication<Application>()

                val originalBitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } ?: run {
                    _uiEvent.send(UiEvent.ShowError("לא ניתן לפתוח את התמונה."))
                    return@launch
                }

                // Step 1: ML Kit always runs first — needed for source detection + amount.
                val mlKitResult = runMlKitOcr(originalBitmap)
                val mlKitText   = mlKitResult?.text
                val mlKitAmount = mlKitResult?.amount
                Log.d("OCR_MLKIT", "ML Kit output:\n${mlKitText ?: "(empty)"}")
                Log.d("OCR_MLKIT", "ML Kit amount (bounding-box): $mlKitAmount")

                // Step 2: Detect source from ML Kit output, then run Tesseract with the
                // appropriate preprocessing:
                //   Paybox — white background → scale 2× only (no inversion)
                //   Bit    — dark background  → invert + scale 2× (existing behaviour)
                val isPaybox = mlKitText != null && PayboxShareParser.isPaybox(mlKitText)
                Log.d("OCR_DETECT", "Detected source: ${if (isPaybox) "Paybox" else "Bit"}")

                val preprocessed = withContext(Dispatchers.IO) {
                    val copy = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    if (isPaybox) preprocessForPayboxOcr(copy) else preprocessForOcr(copy)
                }
                val tesseractText = runTesseractOcr(context, preprocessed).also {
                    withContext(Dispatchers.IO) { preprocessed.recycle() }
                }
                Log.d("OCR_TESS", "Tesseract output:\n${tesseractText ?: "(empty)"}")

                // Crop retry: when Paybox amount line has only ₪ (digit missed by both engines),
                // re-run Tesseract on a 3× zoomed crop of the amount area.
                val hasBareShekl = tesseractText?.lines()?.any { it.trim() == "₪" } == true
                Log.d("AmountCrop", "isPaybox=$isPaybox mlKitAmount=$mlKitAmount hasBareShekl=$hasBareShekl")
                val resolvedAmount = mlKitAmount ?: if (isPaybox && hasBareShekl) {
                    retesseractPayboxAmount(context, originalBitmap).also {
                        Log.d("AmountCrop", if (it != null) "Crop amount: $it" else "Crop also failed")
                    }
                } else null
                originalBitmap.recycle()

                // Step 3: Parse with the appropriate parser.
                if (isPaybox) {
                    val payboxText = tesseractText ?: mlKitText ?: run {
                        _uiEvent.send(UiEvent.ShowError("לא ניתן לקרוא טקסט מהתמונה."))
                        return@launch
                    }
                    val paymentData = PayboxShareParser.parse(payboxText, mlKitText ?: payboxText, resolvedAmount, mlKitResult?.nameAboveAmount) ?: run {
                        _uiEvent.send(UiEvent.ShowError("לא נמצאו פרטי תשלום. ודא שמדובר באישור תשלום PayBox."))
                        return@launch
                    }
                    ListenerManager.onPaymentParsed(paymentData)
                } else {
                    val combinedText = "${tesseractText ?: ""}\n${mlKitText ?: ""}"
                    if (BitShareParser.isExpired(combinedText)) {
                        _uiEvent.send(UiEvent.ShowError("תשלום זה פג תוקף — לא ניתן להפיק עבורו קבלה."))
                        return@launch
                    }
                    val bitText = tesseractText ?: mlKitText ?: run {
                        _uiEvent.send(UiEvent.ShowError("לא ניתן לקרוא טקסט מהתמונה. נסה תמונה ברורה יותר."))
                        return@launch
                    }
                    val paymentData = BitShareParser.parse(
                        hebrewText  = bitText,
                        latinText   = mlKitText ?: bitText,
                        mlKitAmount = mlKitAmount
                    ) ?: run {
                        _uiEvent.send(UiEvent.ShowError("לא נמצאו פרטי תשלום בתמונה. ודא שמדובר בתמונת אישור תשלום ביט."))
                        return@launch
                    }
                    ListenerManager.onPaymentParsed(paymentData)
                }
            } catch (e: Exception) {
                Log.e("ShareOCR", "Unexpected exception during share processing", e)
                _uiEvent.send(UiEvent.ShowError("שגיאה בעיבוד התמונה: ${e.message}"))
            } finally {
                _isProcessingShare.value = false
            }
        }
    }

    // ── Name matching ─────────────────────────────────────────────────────────

    // Exact match for Hebrew; prefix match for Latin words (handles OCR artifact מ → n,
    // e.g. "Hanitan" matches "Hanita", "lazarn" matches "lazar").
    private fun wordsMatch(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val isLatin = a.any { it in 'A'..'Z' || it in 'a'..'z' } &&
                      b.any { it in 'A'..'Z' || it in 'a'..'z' }
        if (!isLatin) return false
        val shorter = if (a.length <= b.length) a else b
        val longer  = if (a.length <= b.length) b else a
        return shorter.length >= 3 && longer.startsWith(shorter, ignoreCase = true)
    }

    // ── ML Kit: Latin script, LTR — accurate numbers & dates ─────────────────

    private data class MlKitResult(val text: String, val amount: Double?, val nameAboveAmount: String?)

    private suspend fun runMlKitOcr(bitmap: Bitmap): MlKitResult? =
        suspendCancellableCoroutine { cont ->
            try {
                val image      = InputImage.fromBitmap(bitmap, 0)
                // Hebrew recognizer reads both Hebrew text and numbers from the same image.
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (result.text.isBlank()) {
                            if (cont.isActive) cont.resume(null)
                            return@addOnSuccessListener
                        }

                        // ── Amount: find the "amount block" without relying on ₪ ──────────
                        // ML Kit Latin recogniser does not reliably output the ₪ symbol —
                        // it is either absent or misread (e.g. „160 instead of ₪160).
                        // The amount is still readable as a block of digits.
                        // Strategy:
                        //  1. For every block, strip one optional leading non-alphanumeric
                        //     character (handles „, ₪, •, etc.).
                        //  2. Keep blocks whose remaining text is purely digits, 2–5 chars,
                        //     with no dots (dates) or colons (times).
                        //  3. Among candidates pick the one with the largest bounding box —
                        //     the amount is always displayed in the biggest font on screen.
                        //  4. Validate the result is in the range 10–99 999.
                        // O/o → 0 normalization handles common OCR digit misreads.
                        fun String.normDigits() = replace('O', '0').replace('o', '0')
                            .replace('l', '1').replace('I', '1')

                        Log.d("MlKitOcr", "Blocks: ${result.textBlocks.map { "'${it.text}' box=${it.boundingBox}" }}")

                        val candidateBlocks = result.textBlocks.filter { block ->
                            val text = block.text.normDigits().trim()
                            // Strip one leading non-digit char (handles misread ₪ → „, B, etc.)
                            val core = if (text.isNotEmpty() && !text.first().isDigit()) {
                                val rest = text.drop(1).replace(",", "")
                                if (rest.isNotEmpty() && rest.all { it.isDigit() }) text.drop(1) else text
                            } else text
                            // Strip commas to handle thousand-separator format (e.g. "1,000")
                            val coreDigits = core.replace(",", "")
                            coreDigits.length in 1..5 &&
                                coreDigits.all { it.isDigit() } &&
                                !text.contains('.') &&
                                !text.contains(':')
                        }

                        Log.d("MlKitOcr", "Candidate blocks: ${candidateBlocks.map { "'${it.text}' box=${it.boundingBox}" }}")

                        val amountBlock = candidateBlocks.maxByOrNull { block ->
                            val bb = block.boundingBox
                            if (bb != null) bb.height() * bb.width() else 0
                        }
                        val amount = amountBlock?.let { block ->
                            block.text.normDigits().filter { it.isDigit() }
                                .toDoubleOrNull()?.takeIf { it in 1.0..99_999.0 }
                        }

                        // Collect text from all blocks spatially ABOVE the amount block.
                        // For Paybox: this is the sender's contact name (e.g. "Hanita").
                        // For Bit: this is the name line (e.g. "נשלחו לך מ ...").
                        val nameAboveAmount = amountBlock?.boundingBox?.let { amountBb ->
                            result.textBlocks
                                .filter { block -> block.boundingBox?.bottom?.let { it < amountBb.top } == true }
                                .sortedBy { it.boundingBox?.top ?: 0 }
                                .joinToString(" ") { it.text.trim() }
                                .trim().ifBlank { null }
                        }

                        Log.d("MlKitOcr", "Final amount: $amount")
                        Log.d("MlKitOcr", "Name above amount: $nameAboveAmount")
                        if (cont.isActive) cont.resume(MlKitResult(result.text, amount, nameAboveAmount))
                    }
                    .addOnFailureListener { e ->
                        Log.e("MlKitOcr", "Recognition failed: ${e.javaClass.simpleName} — ${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (e: Exception) {
                Log.w("MlKitOcr", "Error", e)
                if (cont.isActive) cont.resume(null)
            }
        }

    // ── Tesseract: Hebrew model — accurate sender names ───────────────────────

    private suspend fun runTesseractOcr(context: Context, preprocessedBitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            try {
                val tessDataDir = ensureTessData(context)
                val tess = TessBaseAPI()
                if (!tess.init(tessDataDir, "heb")) {
                    Log.w("MainViewModel", "Tesseract init failed")
                    return@withContext null
                }
                tess.setImage(preprocessedBitmap)
                val result = tess.utF8Text
                tess.recycle()
                result.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Tesseract OCR failed", e)
                null
            }
        }

    /**
     * Crop retry for Paybox: when the full-image scan misses the amount digit,
     * crop y 15%–55% of the original (where the Paybox amount is always displayed).
     *
     * Strategy:
     *  1. ML Kit on the raw crop — without the confirmation-code / date / PayBox
     *     blocks in the lower portion, the ₪ amount block may now be detectable.
     *  2. If ML Kit fails, Tesseract on the crop at 3× zoom with contrast boost.
     */
    private suspend fun retesseractPayboxAmount(context: Context, original: Bitmap): Double? {
        try {
            val cropTop = (original.height * 0.15).toInt()
            val cropH   = (original.height * 0.40).toInt()  // y 15%–55%
            if (cropH <= 0) return null

            val crop = withContext(Dispatchers.IO) {
                Bitmap.createBitmap(original, 0, cropTop, original.width, cropH)
            }

            // Step 1: ML Kit on the crop (no preprocessing — ML Kit prefers original pixels)
            val mlKitCropAmount = runMlKitOcr(crop)?.amount
            if (mlKitCropAmount != null) {
                withContext(Dispatchers.IO) { crop.recycle() }
                Log.d("AmountCrop", "ML Kit crop found: $mlKitCropAmount")
                return mlKitCropAmount
            }
            Log.d("AmountCrop", "ML Kit crop null — trying Tesseract")

            // Step 2: Tesseract on a tight sub-crop at 5× zoom.
            // Narrow crop: y=17%–40% of original — covers only the amount line, not the
            // recipient. Aggressive binarization (c=20) sharpens anti-aliased thin strokes.
            // PSM_SPARSE_TEXT finds text anywhere with no direction assumptions.
            return withContext(Dispatchers.IO) {
                crop.recycle()   // discard the wider crop — build a new tighter one
                val tightTop = (original.height * 0.17).toInt()
                val tightH   = (original.height * 0.23).toInt()   // y 17%–40%
                if (tightH <= 0) return@withContext null
                val tight    = Bitmap.createBitmap(original, 0, tightTop, original.width, tightH)
                val sharpened = sharpenBitmap(tight)
                tight.recycle()
                val scaled = Bitmap.createScaledBitmap(sharpened, original.width * 5, tightH * 5, true)
                sharpened.recycle()

                // Grayscale → hard binarization (c=20): anti-aliased thin strokes → solid black
                val processed = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
                Canvas(processed).apply {
                    val paint = Paint()
                    val cm = ColorMatrix().apply {
                        setSaturation(0f)
                        val c = 20f; val t = 128f * (1f - c)
                        postConcat(ColorMatrix(floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )))
                    }
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                    drawBitmap(scaled, 0f, 0f, paint)
                }
                scaled.recycle()

                val tessDataDir = ensureTessData(context)
                val tess = TessBaseAPI()
                if (!tess.init(tessDataDir, "heb")) {
                    processed.recycle()
                    return@withContext null
                }
                // PSM_SPARSE_TEXT (11): no direction/layout assumptions — finds any text fragment
                tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
                tess.setImage(processed)
                val text = tess.utF8Text
                tess.recycle()
                processed.recycle()
                Log.d("AmountCrop", "Tesseract tight-crop raw: '$text'")

                // ₪N wins; fall back to first standalone number
                val shekelMatch = Regex("""₪\s*([\d,]+\.?\d*)""").find(text)
                val numMatch    = Regex("""([\d,]+\.?\d*)""").find(text)
                (shekelMatch?.groupValues?.get(1) ?: numMatch?.groupValues?.get(1))
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
                    ?.takeIf { it in 1.0..99_999.0 }
            }  // closes return withContext(Dispatchers.IO)
        } catch (e: Exception) {
            Log.w("AmountCrop", "Crop OCR exception", e)
            return null
        }
    }

    private fun preprocessForOcr(src: Bitmap): Bitmap {
        // Scale up 2x — improves Tesseract accuracy on small text
        val scaled = Bitmap.createScaledBitmap(src, src.width * 2, src.height * 2, true)
        src.recycle()
        // Grayscale + invert: Bit uses dark background with white text.
        // Tesseract works best with dark text on light background.
        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix(floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        scaled.recycle()
        return out
    }

    // Paybox: white background, dark text.
    // Pipeline: sharpen at 1x (fewer pixels) → scale 2x → grayscale + contrast boost.
    private fun preprocessForPayboxOcr(src: Bitmap): Bitmap {
        val sharpened = sharpenBitmap(src)
        val scaled = Bitmap.createScaledBitmap(sharpened, sharpened.width * 2, sharpened.height * 2, true)
        sharpened.recycle()
        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix().apply {
            setSaturation(0f) // grayscale
            val c = 1.6f; val t = 128f * (1f - c)
            postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        scaled.recycle()
        return out
    }

    // 3×3 unsharp-mask kernel (0 -1 0 / -1 5 -1 / 0 -1 0).
    // Sharpens at 1x before upscaling to keep computation manageable.
    private fun sharpenBitmap(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                fun ch(shift: Int) = (
                    5 * ((pixels[i]     shr shift) and 0xFF) -
                        ((pixels[i - w] shr shift) and 0xFF) -
                        ((pixels[i + w] shr shift) and 0xFF) -
                        ((pixels[i - 1] shr shift) and 0xFF) -
                        ((pixels[i + 1] shr shift) and 0xFF)
                ).coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        src.recycle()
        return result
    }

    private fun ensureTessData(context: Context): String {
        val tessDir = File(context.filesDir, "tessdata")
        tessDir.mkdirs()
        val destFile = File(tessDir, "heb.traineddata")
        val assetSize = try { context.assets.openFd("tessdata/heb.traineddata").length } catch (_: Exception) { -1L }
        if (!destFile.exists() || (assetSize > 0 && destFile.length() != assetSize)) {
            context.assets.open("tessdata/heb.traineddata").use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
        }
        return context.filesDir.absolutePath
    }
}
