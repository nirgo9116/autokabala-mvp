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

    fun onTabSelected(index: Int) { _selectedTabIndex.value = index }

    // --- Payment History (last 14 days) ---
    private val historyWindowStart = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
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

    sealed class UiEvent {
        data class ShowError(val message: String) : UiEvent()
        data class ReceiptIssued(val docUrl: String?, val clientPhone: String?, val emailSent: Boolean = false) : UiEvent()
    }

    // --- Event Handlers ---

    fun onEnableDisableClicked() {
        if (isEnabled.value) {
            ListenerManager.disable()
        } else {
            ListenerManager.enable()
        }
    }

    fun onIssueReceiptForClientClicked(payment: PaymentEntity, client: ClientEntity) {
        viewModelScope.launch {
            val outcome = receiptRepository.issueReceiptForClient(payment, client)
            if (outcome != null) {
                val phone = client.phone ?: receiptRepository.fetchAndCachePhone(client.id)
                _uiEvent.send(UiEvent.ReceiptIssued(outcome.docUrl, phone, outcome.emailSent))
            } else {
                _uiEvent.send(UiEvent.ShowError("שגיאה בהפקת קבלה. בדוק חיבור לאינטרנט ונסה שוב."))
            }
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

    fun onCreateClientAndIssueReceiptClicked(payment: PaymentEntity, newClientName: String) {
        viewModelScope.launch {
            val docUrl = receiptRepository.createClientAndIssueReceipt(payment, newClientName)
            if (docUrl != null) {
                _uiEvent.send(UiEvent.ReceiptIssued(docUrl.ifBlank { null }, null))
            } else {
                _uiEvent.send(UiEvent.ShowError("שגיאה ביצירת לקוח או הפקת קבלה."))
            }
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

    fun onAddFakePaymentClicked() {
        viewModelScope.launch {
            receiptRepository.addFakePayment()
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
                    val copy = originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                    if (isPaybox) preprocessForPayboxOcr(copy) else preprocessForOcr(copy)
                }
                val tesseractText = runTesseractOcr(context, preprocessed).also {
                    withContext(Dispatchers.IO) { preprocessed.recycle() }
                }
                originalBitmap.recycle()
                Log.d("OCR_TESS", "Tesseract output:\n${tesseractText ?: "(empty)"}")

                // Step 3: Parse with the appropriate parser.
                if (isPaybox) {
                    if (tesseractText == null) {
                        _uiEvent.send(UiEvent.ShowError("לא ניתן לקרוא טקסט מהתמונה."))
                        return@launch
                    }
                    val paymentData = PayboxShareParser.parse(tesseractText, mlKitText ?: tesseractText, mlKitAmount, mlKitResult?.nameAboveAmount) ?: run {
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
                    if (tesseractText == null) {
                        _uiEvent.send(UiEvent.ShowError("לא ניתן לקרוא טקסט מהתמונה. נסה תמונה ברורה יותר."))
                        return@launch
                    }
                    val paymentData = BitShareParser.parse(
                        hebrewText  = tesseractText,
                        latinText   = mlKitText ?: tesseractText,
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
                        Log.w("MlKitOcr", "Recognition failed", e)
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

    // Paybox uses white background with dark text — Tesseract works natively.
    // Scale 2× for accuracy; no colour inversion needed.
    private fun preprocessForPayboxOcr(src: Bitmap): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, src.width * 2, src.height * 2, true)
        src.recycle()
        return scaled
    }

    private fun ensureTessData(context: Context): String {
        val tessDir = File(context.filesDir, "tessdata")
        tessDir.mkdirs()
        val destFile = File(tessDir, "heb.traineddata")
        if (!destFile.exists()) {
            context.assets.open("tessdata/heb.traineddata").use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
        }
        return context.filesDir.absolutePath
    }
}
