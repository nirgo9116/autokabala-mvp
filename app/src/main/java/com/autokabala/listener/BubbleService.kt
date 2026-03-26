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
        const val KEY_BUBBLE_ENABLED   = "bubble_enabled"
        const val KEY_SHARED_URIS  = "shared_image_uris"  // Set<String> of gallery URIs to clean
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
    private var tooltipView: View? = null
    private var galleryDialogView: View? = null
    private val overlayState = mutableStateOf<OverlayState>(OverlayState.Processing)
    private var pendingPaymentId: Int? = null  // DB id of in-progress payment; deleted on cancel

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

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
                    overlayState.value = OverlayState.Err("לא התקבלה תמונה לעיבוד")
                    showOverlayWindow()
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
            y = (200 * dp).toInt()
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
        val match = matchClient(payment.senderName, state.clients)
        pendingPaymentId = payment.id
        overlayState.value = OverlayState.Ready(payment, match, state.clients)
    }

    private fun dragOverlay(dx: Float, dy: Float) {
        val lp = (overlayView?.layoutParams as? WindowManager.LayoutParams) ?: return
        lp.x += dx.toInt()
        lp.y += dy.toInt()
        try { windowManager.updateViewLayout(overlayView, lp) } catch (_: Exception) {}
    }

    private fun shiftOverlayForKeyboard(up: Boolean) {
        val vw = overlayView ?: return
        val lp = (vw.layoutParams as? WindowManager.LayoutParams) ?: return
        val dm = resources.displayMetrics
        lp.y = if (up) (dm.heightPixels * 0.06f).toInt() else (dm.heightPixels * 0.40f).toInt()
        try { windowManager.updateViewLayout(vw, lp) } catch (_: Exception) {}
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
                overlayState.value = OverlayState.Err("לא ניתן לקרוא את התמונה")
                return
            }
            processScreenshot(bitmap)
        } catch (e: Exception) {
            Log.e("BubbleService", "Failed to process share URI", e)
            overlayState.value = OverlayState.Err("שגיאה בטעינת התמונה: ${e.message}")
        }
    }

    private suspend fun processScreenshot(bitmap: Bitmap) {
        try {
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

            bitmap.recycle()

            val paymentData = if (isPaybox) {
                val payboxText = tesseractText ?: mlKitText ?: run {
                    overlayState.value = OverlayState.Err("לא ניתן לקרוא טקסט מהתמונה")
                    return
                }
                PayboxShareParser.parse(payboxText, mlKitText ?: payboxText, resolvedAmount, mlKitResult?.nameAboveAmount) ?: run {
                    overlayState.value = OverlayState.Err("לא נמצאו פרטי תשלום. ודא שמדובר באישור פייבוקס.")
                    return
                }
            } else {
                val combinedText = "${tesseractText ?: ""}\n${mlKitText ?: ""}"
                if (BitShareParser.isExpired(combinedText)) {
                    overlayState.value = OverlayState.Err("תשלום זה פג תוקף")
                    return
                }
                val bitText = tesseractText ?: mlKitText ?: run {
                    overlayState.value = OverlayState.Err("לא ניתן לקרוא טקסט מהתמונה")
                    return
                }
                BitShareParser.parse(hebrewText = bitText, latinText = mlKitText ?: bitText, mlKitAmount = mlKitAmount) ?: run {
                    overlayState.value = OverlayState.Err("לא נמצאו פרטי תשלום. ודא שמדובר באישור ביט.")
                    return
                }
            }

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
                overlayState.value = OverlayState.Err("תשלום זה כבר קיים במערכת")
                return
            }

            val saved = withContext(Dispatchers.IO) {
                db.paymentDao().getPendingPaymentsSnapshot().firstOrNull {
                    it.senderName == paymentData.senderName && it.amount == paymentData.amount
                } ?: entity.copy(id = rowId.toInt())
            }
            pendingPaymentId = saved.id  // track so we can delete if user cancels

            val clients = withContext(Dispatchers.IO) { db.clientDao().getAllClientsSnapshot() }
            val matchResult = matchClient(paymentData.senderName, clients)
            overlayState.value = OverlayState.Ready(saved, matchResult, clients)

        } catch (e: Exception) {
            Log.e("BubbleService", "Error processing screenshot", e)
            overlayState.value = OverlayState.Err("שגיאה בעיבוד: ${e.message}")
        }
    }

    // ─── Client matching ──────────────────────────────────────────────────────

    private fun matchClient(senderName: String, clients: List<ClientEntity>): MatchResult {
        val senderWords = senderName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val senderFirst = senderWords.firstOrNull() ?: return MatchResult.NoMatch

        val firstNameMatches = clients.filter { client ->
            val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            clientWords.any { w -> wordsMatch(w, senderFirst) }
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
        return long.startsWith(short, ignoreCase = true)
    }

    // ─── Receipt issuance ─────────────────────────────────────────────────────

    private fun issueReceipt(client: ClientEntity) {
        val state = overlayState.value as? OverlayState.Ready ?: return
        overlayState.value = OverlayState.Issuing
        scope.launch {
            try {
                val repo = (application as AutoKabalaApplication).receiptRepository
                val outcome = withContext(Dispatchers.IO) {
                    repo.issueReceiptForClient(state.payment, client)
                }
                if (outcome != null) {
                    pendingPaymentId = null  // receipt issued — keep DB record
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
                withContext(Dispatchers.IO) { repo.addFakeReceipt(state.payment) }
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
        if (overlayView != null) return

        val dm = resources.displayMetrics
        val cardWidth = (dm.widthPixels * 0.95f).toInt()
        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((dm.widthPixels - cardWidth) / 2)
            y = (dm.heightPixels * 0.40f).toInt()
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                MaterialTheme {
                    ReceiptOverlayCard(
                        state           = overlayState.value,
                        onIssue         = ::issueReceipt,
                        onDemoReceipt   = ::demoReceipt,
                        onDismiss       = ::removeOverlay,
                        onSelectPending = ::selectPendingPayment,
                        onDrag          = ::dragOverlay,
                        onKeyboardShift = ::shiftOverlayForKeyboard,
                        onCreateClient  = {
                            startActivity(
                                Intent(this@BubbleService, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            removeOverlay()
                        }
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
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView = null
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

@Composable
private fun ReceiptOverlayCard(
    state: OverlayState,
    onIssue: (ClientEntity) -> Unit,
    onDemoReceipt: () -> Unit,
    onDismiss: () -> Unit,
    onSelectPending: (PaymentEntity) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onKeyboardShift: (Boolean) -> Unit = {},
    onCreateClient: () -> Unit = {}
) {
    if (state is OverlayState.Ready) {
        ReadyContent(state, onIssue, onDemoReceipt, onDismiss, onDrag, onKeyboardShift, onCreateClient)
        return
    }
    if (state is OverlayState.PendingList) {
        PendingListContent(state, onSelectPending, onDismiss, onDrag)
        return
    }

    // Processing / Issuing / Done / Err — dark card with drag handle
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DragHandle(onDrag)
        Spacer(Modifier.height(12.dp))

        when (state) {
            is OverlayState.Processing -> ProcessingContent()
            is OverlayState.Issuing    -> IssuingContent()
            is OverlayState.Done       -> DoneContent(state.clientName, onDismiss)
            is OverlayState.Err        -> ErrorContent(state.message, onDismiss)
            is OverlayState.Ready       -> {} // handled above
            is OverlayState.PendingList -> {} // handled above
        }
    }
}

@Composable
private fun ProcessingContent() {
    CircularProgressIndicator(color = BitBlue, modifier = Modifier.size(36.dp))
    Spacer(Modifier.height(12.dp))
    Text("מנתח תמונה...", color = TextPrimary, fontSize = 16.sp)
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
    onIssue: (ClientEntity) -> Unit,
    onDemoReceipt: () -> Unit,
    onDismiss: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onKeyboardShift: (Boolean) -> Unit = {},
    onCreateClient: () -> Unit = {}
) {
    var selectedClient by remember {
        mutableStateOf((state.matchResult as? MatchResult.SingleMatch)?.client)
    }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var anchorWidthPx by remember { mutableStateOf(0) }



    val isBit = state.payment.source.startsWith("bit", ignoreCase = true)
    val brand         = if (isBit) BitBrand    else PayboxBrand
    val actionBg      = if (isBit) Color(0xFF0E1A1F) else Color(0xFFE4F4FC)
    val dismissBg     = if (isBit) Color(0xFF1A2830) else Color(0xFFCCE8F5)
    val dismissBorder = if (isBit) Color(0xFF2A3A45) else Color(0xFF88C8E0)
    val dismissText   = if (isBit) Color.White        else Color(0xFF1A3A5C)
    val ctaText       = if (isBit) Color(0xFF061510)  else Color.White

    val effectiveClient = selectedClient ?: (state.matchResult as? MatchResult.SingleMatch)?.client
    val btnEnabled = effectiveClient != null

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Card ──────────────────────────────────────────────────────────
        PaymentCard(
            state = PaymentProcessingState(payment = state.payment, matchResult = state.matchResult),
            selectedClient = selectedClient,
            calendarEvents = emptyList(),
            onIssueReceipt = { client, _, _, _ -> onIssue(client) },
            onDelete = onDismiss,
            onOpenSheet = { searchQuery = selectedClient?.name ?: ""; showSearch = true },
            onSelectClient = { selectedClient = it; showSearch = false },
            onFakeIssueReceipt = onDemoReceipt,
            showHero = false,
            showActions = false,
            headerColor = brand,
            onHeaderDrag = if (showSearch) null else onDrag,
            onCreateClient = onCreateClient,
            onLkbdBoxWidth = { anchorWidthPx = it },
            clientDropdown = if (showSearch) {
                {
                    OverlayClientSearch(
                        clients = state.allClients,
                        query = searchQuery,
                        initialClientName = selectedClient?.name ?: "",
                        anchorWidthPx = anchorWidthPx,
                        onQueryChange = { searchQuery = it },
                        onSelect = { selectedClient = it; showSearch = false; searchQuery = "" },
                        onDismiss = { showSearch = false },
                        onImeVisible = onKeyboardShift
                    )
                }
            } else null
        )

        // ── Action buttons ────────────────────────────────────────────────
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
            // הפק קבלה
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .alpha(if (btnEnabled) 1f else 0.45f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brand)
                    .clickable(enabled = btnEnabled) { effectiveClient?.let { onIssue(it) } },
                contentAlignment = Alignment.Center
            ) {
                Text("הפק קבלה", color = ctaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            // קבלת דמה (DEBUG only)
            if (BuildConfig.DEBUG) {
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
            }
        }
    }
}

@Composable
private fun DragHandle(onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp, 4.dp)
                .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun PendingListContent(
    state: OverlayState.PendingList,
    onSelect: (PaymentEntity) -> Unit,
    onDismiss: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(20.dp))
    ) {
        DragHandle(onDrag)
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
    onQueryChange: (String) -> Unit,
    onSelect: (ClientEntity) -> Unit,
    onDismiss: () -> Unit,
    onImeVisible: (Boolean) -> Unit = {}
) {
    val sorted = remember(clients) { clients.sortedBy { it.name } }
    val displayed = remember(query, initialClientName, sorted) {
        if (query.isBlank() || query == initialClientName) sorted
        else sorted.filter { it.name.contains(query, ignoreCase = true) }
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
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false, clippingEnabled = false)
    ) {
        val imeBottom = WindowInsets.ime.getBottom(density)
        LaunchedEffect(imeBottom > 0) { onImeVisible(imeBottom > 0) }

        Column(
            modifier = Modifier
                .then(if (anchorWidthPx > 0) Modifier.width(with(density) { anchorWidthPx.toDp() }) else Modifier.wrapContentWidth())
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .background(Color(0xFFF4F1EB), RoundedCornerShape(12.dp))
        ) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = Color.Transparent,
                    backgroundColor = Color(0xFF1565C0).copy(alpha = 0.4f)
                )
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("חפש לקוח...", color = Color(0xFF999999)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF1565C0),
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        focusedTextColor     = Color(0xFF222222),
                        unfocusedTextColor   = Color(0xFF222222),
                        cursorColor          = Color(0xFF1565C0)
                    )
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
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
