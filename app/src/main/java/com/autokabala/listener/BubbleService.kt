package com.autokabala.listener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.*

// ─── Overlay state ────────────────────────────────────────────────────────────

sealed class OverlayState {
    object Processing : OverlayState()
    data class Ready(
        val payment: PaymentEntity,
        val matchResult: MatchResult,
        val allClients: List<ClientEntity>
    ) : OverlayState()
    object Issuing : OverlayState()
    data class Done(val clientName: String?) : OverlayState()
    data class Err(val message: String) : OverlayState()
    data class PendingList(
        val payments: List<PaymentEntity>,
        val clients: List<ClientEntity>
    ) : OverlayState()
}

// ─── Service ──────────────────────────────────────────────────────────────────

class BubbleService : Service() {

    companion object {
        const val ACTION_SHOW          = "com.autokabala.listener.BUBBLE_SHOW"
        const val ACTION_HIDE          = "com.autokabala.listener.BUBBLE_HIDE"
        const val ACTION_PROCESS_SHARE = "com.autokabala.listener.PROCESS_SHARE"
        const val EXTRA_IMAGE_URI      = "image_uri"
        const val EXTRA_ORIGINAL_URI   = "original_uri"  // original gallery URI for later deletion
        private const val NOTIF_ID     = 9001
        const val CHANNEL_ID           = "bubble_channel"
        const val PREFS_NAME           = "autokabala_prefs"
        const val KEY_BUBBLE_ENABLED        = "bubble_enabled"
        const val KEY_FILTER_ACTIVE_CLIENTS = "filter_active_clients"
        const val KEY_OCR_ENGINE            = "ocr_engine"
        const val OCR_ENGINE_AUTO           = "AUTO"
        const val OCR_ENGINE_VISION         = "VISION"
        const val OCR_ENGINE_MLKIT          = "MLKIT"
        const val OCR_ENGINE_OPENAI         = "OPENAI"
        const val KEY_SHARED_URIS           = "shared_image_uris"  // Set<String> of gallery URIs to clean
        private const val KEY_RECEIPT_COUNT = "receipt_count"
        private var instance: BubbleService? = null

        fun show(context: Context) =
            context.startForegroundService(Intent(context, BubbleService::class.java).apply { action = ACTION_SHOW })

        fun hide(context: Context) =
            context.startService(Intent(context, BubbleService::class.java).apply { action = ACTION_HIDE })

        fun processShare(context: Context, fileUri: Uri, originalUri: Uri?) =
            context.startForegroundService(Intent(context, BubbleService::class.java).apply {
                action = ACTION_PROCESS_SHARE
                putExtra(EXTRA_IMAGE_URI, fileUri)
                if (originalUri != null) putExtra(EXTRA_ORIGINAL_URI, originalUri)
            })
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())
    private var bubbleView: View? = null

    // ─── Overlay infrastructure ───────────────────────────────────────────────
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayView: View? = null
    private val overlayAnimVisible = mutableStateOf(false)
    private var tooltipView: View? = null
    private var galleryDialogView: View? = null
    private val overlayState = mutableStateOf<OverlayState>(OverlayState.Processing)
    private var pendingPaymentId: Int? = null  // DB id of in-progress payment; deleted on cancel

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private var feedbackOriginalUri: android.net.Uri? = null  // original share screenshot
    private var feedbackCroppedUri: android.net.Uri? = null   // cropped image sent to OpenAI

    // Minimal LifecycleOwner so ComposeView works inside a Service
    private val serviceLifecycle = object : LifecycleOwner, SavedStateRegistryOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        val savedStateController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        serviceLifecycle.savedStateController.performRestore(null)
        serviceLifecycle.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        serviceLifecycle.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        serviceLifecycle.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDestroy() {
        instance = null
        removeBubble()
        removeOverlay()
        removeGalleryDialog()
        serviceLifecycle.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                startForegroundCompat(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                showBubble()
            }
            ACTION_HIDE -> {
                removeBubble()
                removeOverlay()
                stopSelf()
            }
            ACTION_PROCESS_SHARE -> {
                startForegroundCompat(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                removeBubble()
                val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_IMAGE_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_IMAGE_URI)
                }
                val originalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_ORIGINAL_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(EXTRA_ORIGINAL_URI)
                }
                // Track this gallery URI so GalleryCleanupActivity can delete it later
                if (originalUri != null) {
                    val current = LinkedHashSet(prefs.getStringSet(KEY_SHARED_URIS, emptySet()) ?: emptySet())
                    current.add(originalUri.toString())
                    prefs.edit().putStringSet(KEY_SHARED_URIS, current).apply()
                }
                if (fileUri != null) {
                    overlayState.value = OverlayState.Processing
                    showOverlayWindow()
                    scope.launch { processShareUri(fileUri) }
                } else {
                    showOverlayWindow()
                    scope.launch { showBlankPayment("bit") }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(type: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), type)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    // ─── Bubble (floating button) ─────────────────────────────────────────────

    private fun showBubble() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w("BubbleService", "Overlay permission not granted — skipping bubble")
            return
        }
        if (bubbleView != null) return
        val dp = resources.displayMetrics.density
        val size = (72 * dp).toInt()
        val closeSize = (22 * dp).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (16 * dp).toInt()
            y = (80 * dp).toInt()
        }

        val container = FrameLayout(this)

        // App icon
        val iconView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (8 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            elevation = 12f * dp
        }
        container.addView(iconView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // X indicator in top-right corner (visual; touch handled by container below)
        val closeView = android.widget.TextView(this).apply {
            text = "×"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xBB000000.toInt())
            }
            isClickable = false
            isFocusable = false
            elevation = 14f * dp
        }
        container.addView(closeView, FrameLayout.LayoutParams(closeSize, closeSize).apply {
            gravity = Gravity.TOP or Gravity.RIGHT  // absolute, not RTL-relative
        })

        var downRawX = 0f; var downRawY = 0f
        var downX = 0; var downY = 0
        var dragged = false
        val dragThreshold = (8 * dp)

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    downX = params.x; downY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragged && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                        dragged = true
                    }
                    if (dragged) {
                        params.x = (downX + dx).toInt()
                        params.y = (downY + dy).toInt()
                        windowManager.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        val inClose = event.x > (size - closeSize - 4 * dp) && event.y < (closeSize + 4 * dp)
                        if (inClose) removeBubble() else onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        bubbleView = container
        Log.d("BubbleService", "Bubble shown")
    }

    fun removeBubble() {
        removeTooltip()
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubbleView = null
    }

    private fun onBubbleTapped() {
        if (overlayView != null) return  // overlay already open
        scope.launch {
            val db = (application as AutoKabalaApplication).database
            val pending = withContext(Dispatchers.IO) { db.paymentDao().getPendingPaymentsSnapshot() }
            val clients = withContext(Dispatchers.IO) { db.clientDao().getAllClientsSnapshot() }
            if (pending.isEmpty()) {
                val bParams = (bubbleView?.layoutParams as? WindowManager.LayoutParams) ?: return@launch
                showTooltipNearBubble(bParams)
            } else {
                overlayState.value = OverlayState.PendingList(pending, clients)
                showOverlayWindow()
            }
        }
    }

    private fun selectPendingPayment(payment: PaymentEntity) {
        val state = overlayState.value as? OverlayState.PendingList ?: return
        val clientsForMatch = activeFilteredClients(state.clients)
        val match = matchClient(payment.senderName, clientsForMatch)
        pendingPaymentId = payment.id
        overlayState.value = OverlayState.Ready(payment, match, state.clients)
    }


    private fun showTooltipNearBubble(bParams: WindowManager.LayoutParams) {
        removeTooltip()
        val dp = resources.displayMetrics.density
        val tv = android.widget.TextView(this).apply {
            text = "שתף אישור להפקת קבלה"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            val pad = (12 * dp).toInt()
            setPadding(pad, (7 * dp).toInt(), pad, (7 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xDD1C1C1E.toInt())
                cornerRadius = 20 * dp
            }
            elevation = 8f * dp
        }
        val bubbleSize = (72 * dp).toInt()
        val tipParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = maxOf(8, bParams.x - (180 * dp).toInt())
            y = bParams.y + bubbleSize + (4 * dp).toInt()
        }
        windowManager.addView(tv, tipParams)
        tooltipView = tv
        handler.postDelayed({ removeTooltip() }, 2500)
    }

    private fun removeTooltip() {
        tooltipView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        tooltipView = null
    }

    // ─── Share processing ─────────────────────────────────────────────────────

    private suspend fun processShareUri(uri: Uri) {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                if (uri.scheme == "file") {
                    BitmapFactory.decodeFile(uri.path)
                } else {
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            }
            if (bitmap == null) {
                showBlankPayment("bit")
                return
            }
            processScreenshot(bitmap)
        } catch (e: Exception) {
            Log.e("BubbleService", "Failed to process share URI", e)
            showBlankPayment("bit")
        }
    }

    private suspend fun processScreenshot(bitmap: Bitmap) {
        val startMs = System.currentTimeMillis()
        val ocrEngine = prefs.getString(KEY_OCR_ENGINE, OCR_ENGINE_MLKIT) ?: OCR_ENGINE_MLKIT

        // Save original screenshot for "שלח למפתח" feedback (release builds)
        if (!BuildConfig.DEBUG) {
            feedbackOriginalUri = withContext(Dispatchers.IO) {
                try {
                    val file = java.io.File(cacheDir, "feedback_original.jpg")
                    java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                    androidx.core.content.FileProvider.getUriForFile(this@BubbleService, "${packageName}.provider", file)
                } catch (_: Exception) { null }
            }
        }
        try {
            // ── OpenAI (when explicitly selected) ────────────────────────────
            if (ocrEngine == OCR_ENGINE_OPENAI) {
                Log.d("BubbleService", "Trying OpenAI OCR")
                val croppedBitmap = OcrUtils.cropForOpenAi(bitmap)
                var openAiResult = OcrUtils.runOpenAiOcr(croppedBitmap)
                if (croppedBitmap !== bitmap) croppedBitmap.recycle()
                if (openAiResult == null) {
                    val retryBitmap = OcrUtils.cropForOpenAi(bitmap)
                    openAiResult = OcrUtils.runOpenAiOcr(retryBitmap)
                    if (retryBitmap !== bitmap) retryBitmap.recycle()
                }
                bitmap.recycle()
                when (openAiResult) {
                    is OcrUtils.GeminiResult.Success -> { finishPaymentFlow(openAiResult.data, startMs); return }
                    is OcrUtils.GeminiResult.Rejected -> { showBlankPayment("bit"); return }
                    null -> { showBlankPayment("bit"); return }
                }
            }

            // ── Google Vision (VISION force mode only) ───────────────────────
            if (ocrEngine == OCR_ENGINE_VISION) {
                Log.d("BubbleService", "Trying Google Vision OCR (forced)")
                var visionResult = OcrUtils.runGoogleVisionOcr(bitmap)
                if (visionResult == null) visionResult = OcrUtils.runGoogleVisionOcr(bitmap)
                if (visionResult != null) {
                    val visionText = visionResult.text
                    val isPayboxVision = PayboxShareParser.isPaybox(visionText)
                    val visionAmount = OcrUtils.extractAmountFromText(visionText)
                    val visionPaymentData = if (isPayboxVision) {
                        PayboxShareParser.parse(visionText, visionText, visionAmount, null)
                    } else {
                        if (BitShareParser.isExpired(visionText)) {
                            bitmap.recycle(); overlayState.value = OverlayState.Err("תשלום זה פג תוקף"); return
                        }
                        BitShareParser.parse(hebrewText = visionText, latinText = visionText, mlKitAmount = visionAmount)
                    }
                    if (visionPaymentData != null && visionPaymentData.amount > 0) {
                        Log.d("BubbleService", "Google Vision parsed: ${visionPaymentData.senderName} / ${visionPaymentData.amount}")
                        bitmap.recycle(); finishPaymentFlow(visionPaymentData, startMs); return
                    }
                }
                bitmap.recycle()
                showBlankPayment("bit")
                return
            }

            // ── ML Kit + Tesseract ────────────────────────────────────────────
            Log.d("BubbleService", "Running ML Kit + Tesseract OCR")
            if (!OcrUtils.isTesseractAvailable()) {
                bitmap.recycle()
                showBlankPayment("bit")
                return
            }

            val mlKitResult = OcrUtils.runMlKitOcr(bitmap)
            val mlKitText   = mlKitResult?.text
            val mlKitAmount = mlKitResult?.amount
            Log.d("BubbleService", "ML Kit output:\n${mlKitText ?: "(empty)"}")
            Log.d("BubbleService", "ML Kit amount: $mlKitAmount")

            val isPaybox = mlKitText != null && PayboxShareParser.isPaybox(mlKitText)
            Log.d("BubbleService", "Detected source: ${if (isPaybox) "Paybox" else "Bit"}")

            val preprocessed = withContext(Dispatchers.IO) {
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (isPaybox) OcrUtils.preprocessForPayboxOcr(copy) else OcrUtils.preprocessForOcr(copy)
            }
            val tesseractText = OcrUtils.runTesseractOcr(this@BubbleService, preprocessed).also {
                withContext(Dispatchers.IO) { preprocessed.recycle() }
            }
            Log.d("BubbleService", "Tesseract output:\n${tesseractText ?: "(empty)"}")

            val hasBareShekl = tesseractText?.lines()?.any { it.trim() == "₪" } == true
            val resolvedAmount = mlKitAmount ?: if (isPaybox && hasBareShekl) {
                OcrUtils.retesseractPayboxAmount(this@BubbleService, bitmap)
            } else null

            val paymentData = if (isPaybox) {
                val payboxText = tesseractText ?: mlKitText
                payboxText?.let {
                    PayboxShareParser.parse(it, mlKitText ?: it, resolvedAmount, mlKitResult?.nameAboveAmount)
                }
            } else {
                val combinedText = "${tesseractText ?: ""}\n${mlKitText ?: ""}"
                if (BitShareParser.isExpired(combinedText)) {
                    bitmap.recycle()
                    overlayState.value = OverlayState.Err("תשלום זה פג תוקף")
                    return
                }
                val bitText = tesseractText ?: mlKitText
                bitText?.let {
                    BitShareParser.parse(hebrewText = it, latinText = mlKitText ?: it, mlKitAmount = mlKitAmount)
                }
            }

            // ── OpenAI fallback (when MLKit amount=0, no result, or name is suspicious) ──
            // "Latin in ML Kit name area + all-Hebrew extracted name" = Tesseract garbled a Latin name
            val mlKitNameHasLatin = mlKitResult?.nameAboveAmount?.any { it in 'A'..'z' } == true
            val extractedNameAllHebrew = paymentData?.senderName?.all {
                it in '\u05D0'..'\u05EA' || it in '\u05F0'..'\u05F4' || it == ' '
            } == true
            val nameGarbledByTess = !isPaybox && mlKitNameHasLatin && extractedNameAllHebrew
            val nameIsSuspicious = paymentData != null && (isSuspiciousOcrName(paymentData.senderName) || nameGarbledByTess)
            if (nameIsSuspicious) Log.d("BubbleService", "Suspicious name '${paymentData!!.senderName}' (garbledByTess=$nameGarbledByTess) — trying OpenAI fallback")
            // OpenAI fallback temporarily disabled for ML Kit / Tesseract testing
            // if (paymentData == null || paymentData.amount == 0.0 || nameIsSuspicious) {
            //     val croppedBitmap = OcrUtils.cropForOpenAi(bitmap)
            //     val openAiResult = OcrUtils.runOpenAiOcr(croppedBitmap)
            //     if (croppedBitmap !== bitmap) croppedBitmap.recycle()
            //     if (openAiResult is OcrUtils.GeminiResult.Success) {
            //         bitmap.recycle(); finishPaymentFlow(openAiResult.data, startMs); return
            //     }
            // }

            bitmap.recycle()

            if (paymentData == null) {
                showBlankPayment(if (isPaybox) "paybox" else "bit")
                return
            }

            finishPaymentFlow(paymentData, startMs)

        } catch (e: Exception) {
            Log.e("BubbleService", "Error processing screenshot", e)
            showBlankPayment("bit")
        }
    }

    /**
     * Returns true when the extracted sender name looks like OCR garbage and OpenAI should
     * re-parse the screenshot.  Triggers for:
     *  - Very short names (≤ 3 chars) — real names are at least 4 chars
     *  - Names containing dotless-ı (U+0131) — ML Kit OCR artifact for "i" or "l"
     *  - All-lowercase single-word Latin ≤ 5 chars — typical ML Kit misread pattern ("nni", "nnı")
     */
    private fun isSuspiciousOcrName(name: String): Boolean {
        if (name.isBlank() || name.length <= 3) return true
        if (name.contains('\u0131')) return true
        val isLatinOnly = name.all { it.isLetter() && it.code < 256 || it == ' ' || it == '\'' || it == '-' }
        if (isLatinOnly && !name.contains(' ') && name.length <= 5 && name.all { !it.isLetter() || it.isLowerCase() }) return true
        return false
    }

    /** Shows a blank Ready card (amount=0, no name) so the user can fill in manually. */
    private suspend fun showBlankPayment(source: String) {
        val db = (application as AutoKabalaApplication).database
        val entity = PaymentEntity(
            source      = source,
            senderName  = "",
            amount      = 0.0,
            isConfirmed = false,
            timestamp   = System.currentTimeMillis()
        )
        val rowId = withContext(Dispatchers.IO) { db.paymentDao().insertPayment(entity) }
        val saved = entity.copy(id = if (rowId > 0) rowId.toInt() else entity.id)
        pendingPaymentId = saved.id
        val clients = withContext(Dispatchers.IO) { db.clientDao().getAllClientsSnapshot() }
        overlayState.value = OverlayState.Ready(saved, MatchResult.NoMatch, clients)
    }

    private suspend fun finishPaymentFlow(paymentData: PaymentData, startMs: Long) {
        val db = (application as AutoKabalaApplication).database
        val entity = PaymentEntity(
            source      = paymentData.source,
            senderName  = paymentData.senderName,
            amount      = paymentData.amount,
            isConfirmed = paymentData.isConfirmed,
            timestamp   = paymentData.timestamp
        )
        val rowId = withContext(Dispatchers.IO) { db.paymentDao().insertPayment(entity) }
        if (rowId == -1L) {
            overlayState.value = OverlayState.Err("תשלום זה כבר שותף במערכת")
            return
        }

        val saved = withContext(Dispatchers.IO) {
            db.paymentDao().getPendingPaymentsSnapshot().firstOrNull {
                it.senderName == paymentData.senderName && it.amount == paymentData.amount
            } ?: entity.copy(id = rowId.toInt())
        }
        pendingPaymentId = saved.id

        val clients = withContext(Dispatchers.IO) { db.clientDao().getAllClientsSnapshot() }
        val matchResult = matchClient(paymentData.senderName, activeFilteredClients(clients))
        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed < 900L) delay(900L - elapsed)
        overlayState.value = OverlayState.Ready(saved, matchResult, clients)
    }

    // ─── Client matching ──────────────────────────────────────────────────────

    private fun activeFilteredClients(clients: List<ClientEntity>): List<ClientEntity> {
        val filterEnabled = prefs.getBoolean(KEY_FILTER_ACTIVE_CLIENTS, true)
        return if (filterEnabled) clients.filter { it.isActive } else clients
    }

    private fun matchClient(senderName: String, clients: List<ClientEntity>): MatchResult {
        val senderWords = senderName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val senderFirst = senderWords.firstOrNull() ?: return MatchResult.NoMatch
        Log.d("matchClient", "senderName='$senderName' senderFirst='$senderFirst'(len=${senderFirst.length}) totalClients=${clients.size}")

        val firstNameMatches = clients.filter { client ->
            val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val hit = clientWords.any { w -> wordsMatch(w, senderFirst) }
            if (hit) Log.d("matchClient", "  firstMatch: '${client.name}'")
            hit
        }.sortedByDescending { client ->
            // Put first-name matches (e.g. "משה פרסטר") before surname matches (e.g. "טלי משה")
            val firstClientWord = client.name.trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: ""
            wordsMatch(firstClientWord, senderFirst)
        }
        val fullNameMatches = if (senderWords.size >= 2) {
            firstNameMatches.filter { client ->
                val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                clientWords.size >= 2 && clientWords.all { cw -> senderWords.any { sw -> wordsMatch(sw, cw) } }
            }
        } else emptyList()

        val matched = fullNameMatches.ifEmpty { firstNameMatches }
        val isStrong = fullNameMatches.isNotEmpty()
        return when {
            matched.isEmpty()  -> MatchResult.NoMatch
            matched.size == 1  -> MatchResult.SingleMatch(matched.first(), isStrong)
            else               -> MatchResult.MultipleMatches(matched)
        }
    }

    private fun wordsMatch(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        if (a.length < 2 || b.length < 2) return false
        val long  = if (a.length >= b.length) a else b
        val short = if (a.length <  b.length) a else b
        // Prefix: "מאיר" matches "מאירה" — require ≥ 3 chars to avoid "לי" matching "ליאור"
        if (short.length >= 3 && long.startsWith(short, ignoreCase = true)) return true
        // Fuzzy: Levenshtein ≤ 2 only for long words (≥ 6 chars) — 4-char Hebrew names are too
        // similar to each other (e.g. "שרון"↔"הרמן", "רועי"↔"אורי" both score distance 2)
        if (short.length >= 6 && levenshtein(a.lowercase(), b.lowercase()) <= 2) return true
        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                       else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }

    // ─── Developer feedback (release testing) ────────────────────────────────

    private fun sendFeedbackToWhatsApp(payment: PaymentEntity) {
        removeOverlay()
        val text = buildString {
            append("🔍 AutoKabala OCR Report\n")
            append("מקור: ${if (payment.source == "bit") "ביט" else "פייבוקס"}\n")
            append("שם מקורי (OCR): ${payment.senderName.ifBlank { "לא זוהה" }}\n")
            append("שם מפוענח: ${payment.senderName.ifBlank { "לא זוהה" }}\n")
            append("סכום: ₪${payment.amount}\n")
        }
        val uris = ArrayList<android.net.Uri>()
        feedbackOriginalUri?.let { uris.add(it) }
        feedbackCroppedUri?.let { uris.add(it) }

        val intent = when {
            uris.size >= 2 -> android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }
            uris.size == 1 -> android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }
            else -> android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("whatsapp://send?phone=972506818414&text=${android.net.Uri.encode(text)}")).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            val fallback = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(android.content.Intent.createChooser(fallback, null).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // ─── Receipt issuance ─────────────────────────────────────────────────────

    private fun issueReceipt(client: ClientEntity, amount: Double, timestamp: Long, description: String) {
        val state = overlayState.value as? OverlayState.Ready ?: return
        val payment = state.payment.copy(amount = amount, timestamp = timestamp)
        overlayState.value = OverlayState.Issuing
        scope.launch {
            try {
                val repo = (application as AutoKabalaApplication).receiptRepository
                val startMs = System.currentTimeMillis()
                val success = withContext(Dispatchers.IO) {
                    if (client.id.startsWith("new:")) {
                        repo.createClientAndIssueReceipt(
                            payment, client.name, client.phone, client.email, description
                        ) != null
                    } else {
                        repo.issueReceiptForClient(payment, client, description) != null
                    }
                }
                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed < 1200L) delay(1200L - elapsed)
                if (success) {
                    pendingPaymentId = null
                    overlayState.value = OverlayState.Done(client.name)
                    handler.postDelayed({ removeOverlay(); maybeShowGalleryCleanup() }, 3000)
                } else {
                    overlayState.value = OverlayState.Err("שגיאה בהפקת קבלה — בדוק חיבור לאינטרנט")
                }
            } catch (e: Exception) {
                overlayState.value = OverlayState.Err("שגיאה: ${e.message}")
            }
        }
    }

    private fun demoReceipt() {
        val state = overlayState.value as? OverlayState.Ready ?: return
        overlayState.value = OverlayState.Issuing
        scope.launch {
            try {
                val repo = (application as AutoKabalaApplication).receiptRepository
                val startMs = System.currentTimeMillis()
                withContext(Dispatchers.IO) { repo.addFakeReceipt(state.payment) }
                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed < 1200L) delay(1200L - elapsed)
                pendingPaymentId = null  // demo receipt — keep DB record
                overlayState.value = OverlayState.Done(state.payment.senderName)
                handler.postDelayed({ removeOverlay(); maybeShowGalleryCleanup() }, 3000)
            } catch (e: Exception) {
                overlayState.value = OverlayState.Err("שגיאה: ${e.message}")
            }
        }
    }

    private fun maybeShowGalleryCleanup() {
        val count = prefs.getInt(KEY_RECEIPT_COUNT, 0) + 1
        val uris  = prefs.getStringSet(KEY_SHARED_URIS, emptySet()) ?: emptySet()
        if (count >= 8 && uris.isNotEmpty()) {
            prefs.edit().putInt(KEY_RECEIPT_COUNT, 0).apply()
            showGalleryCleanupOverlay()
        } else {
            prefs.edit().putInt(KEY_RECEIPT_COUNT, count).apply()
        }
    }

    // ─── Gallery cleanup dialog (shown after 8 receipts) ─────────────────────

    private fun showGalleryCleanupOverlay() {
        if (galleryDialogView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        val dialogView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(serviceLifecycle)
            setViewTreeSavedStateRegistryOwner(serviceLifecycle)
            setContent {
                MaterialTheme {
                    GalleryCleanupDialog(
                        onConfirm = {
                            removeGalleryDialog()
                            startActivity(
                                Intent(this@BubbleService, GalleryCleanupActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        onDismiss = { removeGalleryDialog() }
                    )
                }
            }
        }
        windowManager.addView(dialogView, params)
        galleryDialogView = dialogView
    }

    private fun removeGalleryDialog() {
        galleryDialogView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        galleryDialogView = null
    }

    // ─── WindowManager overlay ────────────────────────────────────────────────

    private fun showOverlayWindow() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w("BubbleService", "Overlay permission not granted — skipping overlay")
            return
        }
        if (overlayView != null) return
        overlayAnimVisible.value = false

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                MaterialTheme {
                    ReceiptOverlayCard(
                        state            = overlayState.value,
                        animVisible      = overlayAnimVisible.value,
                        onIssue          = ::issueReceipt,
                        onDemoReceipt    = ::demoReceipt,
                        onDismiss        = ::removeOverlay,
                        onSelectPending  = ::selectPendingPayment,
                        onSendFeedback   = ::sendFeedbackToWhatsApp,
                        onOpenICount     = ::openICountWebView,
                        isApiConfigured  = ReceiptApiClient.isConfigured()
                    )
                }
            }
        }
        val rootFrame = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    removeOverlay()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        rootFrame.setViewTreeLifecycleOwner(serviceLifecycle)
        rootFrame.setViewTreeSavedStateRegistryOwner(serviceLifecycle)
        rootFrame.addView(composeView)
        windowManager.addView(rootFrame, params)
        overlayView = rootFrame
        handler.post { overlayAnimVisible.value = true }   // trigger slide-in
    }

    fun removeOverlay() {
        val pid = pendingPaymentId
        if (pid != null && overlayState.value !is OverlayState.Done) {
            pendingPaymentId = null
            scope.launch(Dispatchers.IO) {
                try {
                    (application as AutoKabalaApplication).database.paymentDao().deletePayment(pid)
                } catch (e: Exception) {
                    Log.w("BubbleService", "Could not delete cancelled payment $pid", e)
                }
            }
        }
        pendingPaymentId = null
        overlayAnimVisible.value = false                   // trigger slide-out
        handler.postDelayed({
            overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            overlayView = null
        }, 320)
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "בועת אוטוקבלה", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "מאפשר עיבוד תשלומים"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BubbleService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("אוטוקבלה פעילה")
            .setContentText("שתף את אישור התשלום לקבלת קבלה")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "סגור", stopIntent)
            .build()
    }

    // ─── iCount WebView ────────────────────────────────────────────────────────

    private fun openICountWebView(client: ClientEntity, amount: Double, description: String) {
        removeOverlay()
        ICountWebViewActivity.launch(
            context     = this,
            user        = ReceiptApiClient.user,
            cid         = ReceiptApiClient.cid,
            pass        = ReceiptApiClient.pass,
            clientName  = client.name,
            amount      = amount,
            description = description
        )
    }
}

// ─── Compose UI ───────────────────────────────────────────────────────────────

private val CardBg       = Color(0xFF1C1C1E)
private val TextPrimary  = Color(0xFFFFFFFF)
private val TextSecond   = Color(0xFFAAAAAA)
private val Divider      = Color(0xFF3A3A3C)
private val BitBlue      = Color(0xFF90CAF9)
private val PayboxPurple = Color(0xFFCE93D8)
private val ActionBlue   = Color(0xFF2563EB)
// Actual brand colors (for overlay skin)
private val BitBrand    = Color(0xFF2DB887)   // Bit green
private val PayboxBrand = Color(0xFF00AEEF)   // Paybox blue

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

@Composable
private fun ReceiptOverlayCard(
    state: OverlayState,
    animVisible: Boolean,
    onIssue: (ClientEntity, Double, Long, String) -> Unit,
    onDemoReceipt: () -> Unit,
    onDismiss: () -> Unit,
    onSelectPending: (PaymentEntity) -> Unit,
    onCreateClient: () -> Unit = {},
    onSendFeedback: (PaymentEntity) -> Unit = {},
    onOpenICount: (ClientEntity, Double, String) -> Unit = { _, _, _ -> },
    isApiConfigured: Boolean = false
) {
    AnimatedVisibility(
        visible = animVisible,
        enter = slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ),
        exit = slideOutVertically(
            animationSpec = tween(260, easing = FastOutLinearInEasing),
            targetOffsetY = { it }
        )
    ) {
        if (state is OverlayState.Ready) {
            ReadyContent(state, onIssue, onDemoReceipt, onDismiss, onCreateClient, onSendFeedback, onOpenICount, isApiConfigured)
            return@AnimatedVisibility
        }
        if (state is OverlayState.PendingList) {
            PendingListContent(state, onSelectPending, onDismiss)
            return@AnimatedVisibility
        }

        // Processing / Issuing / Done / Err
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg, SheetShape)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DragHandle(onDismiss)
            Spacer(Modifier.height(12.dp))
            when (state) {
                is OverlayState.Processing  -> ProcessingContent()
                is OverlayState.Issuing     -> IssuingContent()
                is OverlayState.Done        -> DoneContent(state.clientName, onDismiss)
                is OverlayState.Err         -> ErrorContent(state.message, onDismiss)
                is OverlayState.Ready       -> {}
                is OverlayState.PendingList -> {}
            }
        }
    }
}

@Composable
private fun ProcessingContent() {
    CircularProgressIndicator(color = BitBlue, modifier = Modifier.size(36.dp))
    Spacer(Modifier.height(12.dp))
    Text("מכין קבלה...", color = TextPrimary, fontSize = 16.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun IssuingContent() {
    CircularProgressIndicator(color = BitBlue, modifier = Modifier.size(36.dp))
    Spacer(Modifier.height(12.dp))
    Text("מפיק קבלה...", color = TextPrimary, fontSize = 16.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun DoneContent(clientName: String?, onDismiss: () -> Unit) {
    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(8.dp))
    Text("קבלה הופקה!", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    if (clientName != null)
        Text("עבור $clientName", color = TextSecond, fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onDismiss) { Text("סגור", color = BitBlue) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    Text(message, color = Color(0xFFFF6B6B), fontSize = 15.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onDismiss) { Text("סגור", color = TextSecond) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ReadyContent(
    state: OverlayState.Ready,
    onIssue: (ClientEntity, Double, Long, String) -> Unit,
    onDemoReceipt: () -> Unit,
    onDismiss: () -> Unit,
    onCreateClient: () -> Unit = {},
    onSendFeedback: (PaymentEntity) -> Unit = {},
    onOpenICount: (ClientEntity, Double, String) -> Unit = { _, _, _ -> },
    isApiConfigured: Boolean = false
) {
    var selectedClient by remember {
        mutableStateOf((state.matchResult as? MatchResult.SingleMatch)?.client)
    }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var anchorWidthPx by remember { mutableStateOf(0) }
    var showCreateForm by remember { mutableStateOf(false) }
    var editedAmount by remember { mutableStateOf(state.payment.amount) }
    var editedTimestamp by remember { mutableStateOf(state.payment.timestamp) }
    var editedDescription by remember { mutableStateOf("") }
    var showAmountDialog by remember { mutableStateOf(false) }



    val isBit = state.payment.source.startsWith("bit", ignoreCase = true)
    val brand         = if (isBit) BitBrand    else PayboxBrand
    val actionBg      = if (isBit) Color(0xFF0E1A1F) else Color(0xFFE4F4FC)
    val dismissBg     = if (isBit) Color(0xFF1A2830) else Color(0xFFCCE8F5)
    val dismissBorder = if (isBit) Color(0xFF2A3A45) else Color(0xFF88C8E0)
    val dismissText   = if (isBit) Color.White        else Color(0xFF1A3A5C)
    val ctaText       = if (isBit) Color(0xFF061510)  else Color.White

    val effectiveClient = selectedClient ?: (state.matchResult as? MatchResult.SingleMatch)?.client
    val btnEnabled = effectiveClient != null

    if (showCreateForm) {
        NewClientFormContent(
            brand = brand,
            actionBg = actionBg,
            dismissBg = dismissBg,
            dismissBorder = dismissBorder,
            dismissText = dismissText,
            onBack = { showCreateForm = false },
            onConfirm = { name, phone ->
                selectedClient = ClientEntity(
                    id = "new:${System.currentTimeMillis()}",
                    name = name,
                    phone = phone.ifBlank { null },
                    email = null
                )
                showCreateForm = false
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth().clip(SheetShape)) {
            PaymentCard(
                state = PaymentProcessingState(payment = state.payment, matchResult = state.matchResult),
                selectedClient = selectedClient,
                calendarEvents = emptyList(),
                onIssueReceipt = { client, amt, ts, desc -> onIssue(client, amt, ts, desc) },
                onCurrentValues = { amt, ts, desc -> editedAmount = amt; editedTimestamp = ts; editedDescription = desc },
                onDelete = onDismiss,
                onOpenSheet = {
                    if (showSearch) {
                        val exact = state.allClients.firstOrNull { it.name.equals(searchQuery.trim(), ignoreCase = true) }
                        if (exact != null) selectedClient = exact
                        showSearch = false
                    } else {
                        searchQuery = selectedClient?.name ?: ""
                        showSearch = true
                    }
                },
                onSelectClient = { selectedClient = it; showSearch = false },
                onFakeIssueReceipt = onDemoReceipt,
                showHero = false,
                showActions = false,
                headerColor = brand,
                onHeaderDrag = null,
                onCreateClient = { showCreateForm = true },
                onDismiss = onDismiss,
                onLkbdBoxWidth = { anchorWidthPx = it },
                searchQuery = searchQuery,
                onSearchQueryChange = if (showSearch) { { searchQuery = it } } else null,
                clientDropdown = if (showSearch) {
                    {
                        OverlayClientSearch(
                            clients = state.allClients,
                            query = searchQuery,
                            initialClientName = selectedClient?.name ?: "",
                            anchorWidthPx = anchorWidthPx,
                            onSelect = { selectedClient = it; showSearch = false; searchQuery = "" },
                            onDismiss = {
                                val exact = state.allClients.firstOrNull {
                                    it.name.equals(searchQuery.trim(), ignoreCase = true)
                                }
                                if (exact != null) selectedClient = exact
                                showSearch = false
                            },
                            onImeVisible = {}
                        )
                    }
                } else null
            )

            // ── Action buttons ────────────────────────────────────────────────
            if (showAmountDialog) {
                // ── Inline amount-change confirmation ─────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(actionBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val origStr = "₪${"%.2f".format(state.payment.amount)}"
                    val newStr  = "₪${"%.2f".format(editedAmount)}"
                    Text(
                        "הסכום שונה מ-$origStr ל-$newStr. האם להפיק?",
                        color = dismissText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(dismissBg)
                                .border(1.dp, dismissBorder, RoundedCornerShape(14.dp))
                                .clickable { showAmountDialog = false },
                            contentAlignment = Alignment.Center
                        ) { Text("ביטול", color = dismissText, fontWeight = FontWeight.Medium) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(brand)
                                .clickable {
                                    showAmountDialog = false
                                    effectiveClient?.let { onIssue(it, editedAmount, editedTimestamp, editedDescription) }
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("הפק", color = ctaText, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(actionBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ✕ dismiss
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(dismissBg)
                            .border(1.dp, dismissBorder, RoundedCornerShape(14.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 20.sp, color = dismissText)
                    }
                    if (BuildConfig.DEBUG) {
                        // ── DEBUG: הפק קבלה רגיל ────────────────────────────
                        val issueEnabled = btnEnabled
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .alpha(if (issueEnabled) 1f else 0.45f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(brand)
                                .clickable(enabled = issueEnabled) {
                                    effectiveClient?.let {
                                        if (editedAmount != state.payment.amount && state.payment.amount != 0.0)
                                            showAmountDialog = true
                                        else
                                            onIssue(it, editedAmount, editedTimestamp, editedDescription)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("הפק קבלה", color = ctaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        // 🌐 iCount WebView
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .alpha(if (btnEnabled) 1f else 0.45f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(dismissBg)
                                .border(1.dp, dismissBorder, RoundedCornerShape(14.dp))
                                .clickable(enabled = btnEnabled) {
                                    effectiveClient?.let { onOpenICount(it, editedAmount, editedDescription) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌐", fontSize = 20.sp)
                        }
                        // קבלת דמה
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(dismissBg)
                                .border(1.dp, dismissBorder, RoundedCornerShape(14.dp))
                                .clickable { onDemoReceipt() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔬", fontSize = 20.sp)
                        }
                    } else {
                        // ── RELEASE: API if configured, WebView fallback ──────
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .alpha(if (btnEnabled) 1f else 0.45f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(brand)
                                .clickable(enabled = btnEnabled) {
                                    effectiveClient?.let { client ->
                                        if (isApiConfigured) {
                                            if (editedAmount != state.payment.amount && state.payment.amount != 0.0)
                                                showAmountDialog = true
                                            else
                                                onIssue(client, editedAmount, editedTimestamp, editedDescription)
                                        } else {
                                            onOpenICount(client, editedAmount, editedDescription)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isApiConfigured) "הפק קבלה" else "🌐 פתח ב-iCount",
                                color = ctaText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewClientFormContent(
    brand: Color,
    actionBg: Color,
    dismissBg: Color,
    dismissBorder: Color,
    dismissText: Color,
    onBack: () -> Unit,
    onConfirm: (name: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val ctaEnabled = name.isNotBlank()

    Column(modifier = Modifier.fillMaxWidth().clip(SheetShape)) {
        // ── Header (brand color, same as card) ────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().background(brand)
        ) {
            DragHandle(onBack)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("לקוח חדש", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("קבלה", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        // ── Fields ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF4F1EB))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = Color.Transparent,
                    backgroundColor = brand.copy(alpha = 0.3f)
                )
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("שם לקוח") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = brand,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        focusedTextColor = Color(0xFF222222),
                        unfocusedTextColor = Color(0xFF222222),
                        focusedLabelColor = brand,
                        cursorColor = brand
                    )
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("טלפון (אופציונלי)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ctaEnabled) onConfirm(name.trim(), phone.trim()) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = brand,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        focusedTextColor = Color(0xFF222222),
                        unfocusedTextColor = Color(0xFF222222),
                        focusedLabelColor = brand,
                        cursorColor = brand
                    )
                )
            }
        }
        // ── Action row ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(actionBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(dismissBg)
                    .border(1.dp, dismissBorder, RoundedCornerShape(14.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 22.sp, color = dismissText)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .alpha(if (ctaEnabled) 1f else 0.45f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brand)
                    .clickable(enabled = ctaEnabled) { onConfirm(name.trim(), phone.trim()) },
                contentAlignment = Alignment.Center
            ) {
                Text("אישור", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun DragHandle(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(onDismiss) {
                var dragTotal = 0f
                detectDragGestures(
                    onDragEnd    = { if (dragTotal > 56.dp.toPx()) onDismiss(); dragTotal = 0f },
                    onDragCancel = { dragTotal = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    if (dragAmount.y > 0) dragTotal += dragAmount.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(40.dp, 4.dp).background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun PendingListContent(
    state: OverlayState.PendingList,
    onSelect: (PaymentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, SheetShape)
    ) {
        DragHandle(onDismiss)
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("תשלומים ממתינים", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF333333))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = TextPrimary, fontSize = 13.sp)
            }
        }
        HorizontalDivider(color = Divider)
        state.payments.forEach { payment ->
            val isBit = payment.source.startsWith("bit")
            val srcColor = if (isBit) BitBlue else PayboxPurple
            val srcName  = if (isBit) "ביט" else "פייבוקס"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(payment) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .background(srcColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(srcName, color = srcColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(payment.senderName, color = TextPrimary, fontSize = 14.sp)
                }
                Text("₪${payment.amount.toLong()}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            HorizontalDivider(color = Color(0xFF2A2A2C))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GalleryCleanupDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable {},  // consume clicks so background dismiss doesn't fire
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("ניקוי גלריה", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "נקה את הגלריה מתמונות של ביט ופייבוקס?",
                    color = TextSecond,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("ביטול", color = TextSecond) }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) { Text("אישור") }
                }
            }
        }
    }
}

@Composable
private fun OverlayClientSearch(
    clients: List<ClientEntity>,
    query: String,
    initialClientName: String = "",
    anchorWidthPx: Int = 0,
    onSelect: (ClientEntity) -> Unit,
    onDismiss: () -> Unit,
    onImeVisible: (Boolean) -> Unit = {}
) {
    val sorted = remember(clients) { clients.sortedBy { it.name } }
    val displayed = remember(query, initialClientName, sorted) {
        if (query.isBlank() || query == initialClientName) sorted
        else sorted.filter { it.name.startsWith(query.trim(), ignoreCase = true) }
    }
    val initialIndex = remember(initialClientName, sorted) {
        sorted.indexOfFirst { it.name.equals(initialClientName, ignoreCase = true) }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(initialClientName) {
        if (initialIndex > 0) listState.scrollToItem(initialIndex)
    }
    val density = LocalDensity.current

    // Force popup to always open downward from anchor
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.bottom)
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false, clippingEnabled = false)
    ) {
        val imeBottom = WindowInsets.ime.getBottom(density)
        LaunchedEffect(imeBottom > 0) { onImeVisible(imeBottom > 0) }

        Column(
            modifier = Modifier
                .then(if (anchorWidthPx > 0) Modifier.width(with(density) { anchorWidthPx.toDp() }) else Modifier.wrapContentWidth())
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .background(Color(0xFFF4F1EB), RoundedCornerShape(12.dp))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
            ) {
                items(displayed) { client ->
                    Text(
                        client.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(client) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        color = Color(0xFF222222)
                    )
                    HorizontalDivider(color = Color(0xFFDDD8CC))
                }
            }
        }
    }
}
