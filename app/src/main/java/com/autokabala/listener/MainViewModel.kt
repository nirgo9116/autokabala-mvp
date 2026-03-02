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
            val senderRest  = senderWords.drop(1)  // last name words (empty for notifications)

            // Step 1: all clients where senderFirst matches any word in client name
            val firstNameMatches = if (senderFirst.isBlank()) emptyList() else {
                clients.filter { client ->
                    val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    clientWords.any { it.equals(senderFirst, ignoreCase = true) }
                }
            }

            // Step 2: among those, find clients where ALL sender words (incl. last name) match
            val fullNameMatches = if (senderRest.isNotEmpty()) {
                firstNameMatches.filter { client ->
                    val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    senderRest.all { sw -> clientWords.any { it.equals(sw, ignoreCase = true) } }
                }
            } else emptyList()

            // Use full-name matches when found; otherwise fall back to first-name matches.
            // This lets "דורון" (with OCR noise "טיפול") still match "מיכל דורון" / "רונית דורון".
            val matchingClients = fullNameMatches.ifEmpty { firstNameMatches }
            val isStrong = fullNameMatches.isNotEmpty() // green only when last name confirmed

            // isStrong = green: full name confirmed (bit_share); amber: first-name only
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
    }

    // --- Event Handlers ---

    fun onEnableDisableClicked() {
        if (isEnabled.value) {
            ListenerManager.disable()
        } else {
            ListenerManager.enable()
        }
    }

    fun onIssueReceiptForClientClicked(payment: PaymentEntity, clientId: String) {
        viewModelScope.launch {
            val wasSuccessful = receiptRepository.issueReceiptForClient(payment, clientId)
            if (!wasSuccessful) {
                _uiEvent.send(UiEvent.ShowError("Failed to issue receipt. Please check internet connection and try again."))
            }
        }
    }

    fun onCreateClientAndIssueReceiptClicked(payment: PaymentEntity, newClientName: String) {
        viewModelScope.launch {
            val wasSuccessful = receiptRepository.createClientAndIssueReceipt(payment, newClientName)
            if (!wasSuccessful) {
                _uiEvent.send(UiEvent.ShowError("Failed to create client or issue receipt."))
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
                var mlKitText: String? = null
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
                    mlKitText = mlKitJob.await()
                    tesseractText = tesseractJob.await()
                }
                originalBitmap.recycle()

                Log.d("OCR_MLKIT",  "ML Kit  output:\n${mlKitText  ?: "(empty)"}")
                Log.d("OCR_TESS",   "Tesseract output:\n${tesseractText ?: "(empty)"}")

                // Expired check uses Tesseract output (Hebrew badge text)
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
                    hebrewText = tesseractText!!,
                    latinText  = mlKitText ?: tesseractText!!
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

    private suspend fun runMlKitOcr(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { cont ->
            try {
                val image      = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result.text.takeIf { it.isNotBlank() })
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
