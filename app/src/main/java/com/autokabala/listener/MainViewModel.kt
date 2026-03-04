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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val receiptRepository = (application as AutoKabalaApplication).receiptRepository

    // --- UI State ---
    private val pendingPayments: StateFlow<List<PaymentEntity>> = receiptRepository.pendingPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allClients: StateFlow<List<ClientEntity>> = receiptRepository.allClients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                    clientWords.any { it.equals(senderFirst, ignoreCase = true) }
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
                        senderWords.any { sw -> sw.equals(cw, ignoreCase = true) }
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

    fun onShareIntentReceived(imageUri: Uri) {
        viewModelScope.launch {
            _isProcessingShare.value = true
            try {
                val context = getApplication<Application>()

                // Load original bitmap once — shared between both OCR engines
                val originalBitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } ?: run {
                    _uiEvent.send(UiEvent.ShowError("לא ניתן לפתוח את התמונה."))
                    return@launch
                }

                // Run both engines in parallel:
                //   ML Kit  → original bitmap (handles dark backgrounds natively, reads LTR)
                //   Tesseract → preprocessed copy (inverted, 2× scaled, Hebrew model)
                var mlKitResult: MlKitResult? = null
                var tesseractText: String? = null
                coroutineScope {
                    val mlKitJob = async {
                        runMlKitOcr(originalBitmap)
                    }
                    val tesseractJob = async {
                        val preprocessed = withContext(Dispatchers.IO) {
                            preprocessForOcr(
                                originalBitmap.copy(
                                    originalBitmap.config ?: Bitmap.Config.ARGB_8888, false
                                )
                            )
                        }
                        runTesseractOcr(context, preprocessed).also {
                            withContext(Dispatchers.IO) { preprocessed.recycle() }
                        }
                    }
                    mlKitResult = mlKitJob.await()
                    tesseractText = tesseractJob.await()
                }
                originalBitmap.recycle()

                val mlKitText   = mlKitResult?.text
                val mlKitAmount = mlKitResult?.amount

                Log.d("OCR_MLKIT",  "ML Kit  output:\n${mlKitText  ?: "(empty)"}")
                Log.d("OCR_MLKIT",  "ML Kit  amount (bounding-box): $mlKitAmount")
                Log.d("OCR_TESS",   "Tesseract output:\n${tesseractText ?: "(empty)"}")

                // Tesseract → Hebrew names (primary for name extraction)
                // ML Kit Latin → amount via bounding-box (primary for amount)
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
                    hebrewText  = tesseractText!!,
                    latinText   = mlKitText ?: tesseractText!!,
                    mlKitAmount = mlKitAmount
                )
                if (paymentData == null) {
                    _uiEvent.send(UiEvent.ShowError("לא נמצאו פרטי תשלום בתמונה. ודא שמדובר בתמונת אישור תשלום ביט."))
                    return@launch
                }

                ListenerManager.onPaymentParsed(paymentData)
            } finally {
                _isProcessingShare.value = false
            }
        }
    }

    // ── ML Kit: Latin script, LTR — accurate numbers & dates ─────────────────

    private data class MlKitResult(val text: String, val amount: Double?)

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

                        Log.d("MlKitOcr", "Blocks: ${result.textBlocks.map { "'${it.text}' box=${it.boundingBox}" }}")

                        val candidateBlocks = result.textBlocks.filter { block ->
                            val text = block.text.normDigits().trim()
                            // Strip one leading non-alphanumeric char (misread ₪ → „ etc.)
                            val core = if (text.isNotEmpty() && !text.first().isLetterOrDigit())
                                text.drop(1) else text
                            // Strip commas to handle thousand-separator format (e.g. "1,000")
                            val coreDigits = core.replace(",", "")
                            coreDigits.length in 1..5 &&
                                coreDigits.all { it.isDigit() } &&
                                !text.contains('.') &&
                                !text.contains(':')
                        }

                        Log.d("MlKitOcr", "Candidate blocks: ${candidateBlocks.map { "'${it.text}' box=${it.boundingBox}" }}")

                        val amount = candidateBlocks
                            .maxByOrNull { block ->
                                val bb = block.boundingBox
                                if (bb != null) bb.height() * bb.width() else 0
                            }
                            ?.let { block ->
                                block.text.normDigits().filter { it.isDigit() }
                                    .toDoubleOrNull()?.takeIf { it in 1.0..99_999.0 }
                            }

                        Log.d("MlKitOcr", "Final amount: $amount")
                        if (cont.isActive) cont.resume(MlKitResult(result.text, amount))
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
