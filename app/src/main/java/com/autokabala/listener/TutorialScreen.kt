package com.autokabala.listener

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import kotlin.math.min

// ── Focus region model ────────────────────────────────────────────────────────
//
// Coordinates are fractions of the image (0f = left/top, 1f = right/bottom).
// Example: FocusRegion(0.6f, 0.5f, 0.95f, 0.75f) selects the bottom-right area.
//
// How zoom is calculated:
//   renderedW / renderedH = actual pixel size of image inside the container (after Fit scaling)
//   regionW  = (right - left) * renderedW
//   scale    = min(containerW / regionW, containerH / regionH).coerceIn(1f, 3f)
//   offsetX  = -(regionCenterX - renderedW/2) * scale   (centers region on screen)
//   offsetY  = -(regionCenterY - renderedH/2) * scale

private data class FocusRegion(
    val left:   Float,   // 0f–1f
    val top:    Float,
    val right:  Float,
    val bottom: Float
)

// ── Slide model ───────────────────────────────────────────────────────────────

private data class TutorialSlide(
    val emoji: String? = null,
    val drawableRes: Int? = null,
    val title: String,
    val description: String,
    val focusRegions: List<FocusRegion> = emptyList(),
    // Permission slide fields
    val permissionLabel: String? = null,
    val permissionSettingsAction: String? = null,
    val permissionSettingsPackage: String? = null,
    val isPermissionGranted: ((android.content.Context) -> Boolean)? = null
)

private fun overlayGranted(ctx: android.content.Context) =
    android.provider.Settings.canDrawOverlays(ctx)

private fun notificationListenerGranted(ctx: android.content.Context) =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private val SLIDES = listOf(
    // ── 0: Welcome ───────────────────────────────────────────────────────────
    TutorialSlide(
        emoji = "🧾",
        title = "ברוך הבא לאוטוקבלה!",
        description = "האפליקציה מפיקה קבלה אוטומטית ברגע שמשתפים אישור תשלום מביט או פייבוקס.\n\nבדקות הקרובות נגדיר אותה יחד."
    ),
    // ── 1: Permission — overlay ──────────────────────────────────────────────
    TutorialSlide(
        emoji = "📋",
        title = "הרשאה ראשונה: הצגה מעל אפליקציות",
        description = "כדי שטופס הקבלה יוצג מעל ביט ופייבוקס, יש לאשר הצגה מעל אפליקציות אחרות.\n\nלחץ על הכפתור, מצא את אוטוקבלה ברשימה והפעל.",
        permissionLabel = "פתח הגדרות",
        permissionSettingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        permissionSettingsPackage = "com.autokabala.listener",
        isPermissionGranted = ::overlayGranted
    ),
    // ── 2: Permission — notification listener ────────────────────────────────
    TutorialSlide(
        emoji = "🔔",
        title = "הרשאה שנייה: גישה להתראות",
        description = "כדי לזהות תשלומים נכנסים, יש להפעיל גישה להתראות עבור אוטוקבלה.\n\nלחץ על הכפתור, מצא את אוטוקבלה ברשימה והפעל.",
        permissionLabel = "פתח הגדרות",
        permissionSettingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        isPermissionGranted = ::notificationListenerGranted
    ),
    // ── 3: iCount — מערכת ────────────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_ic_maarchet,
        title = "פתח את הגדרות iCount",
        description = "בצד שמאל של iCount, לחץ על מערכת (המסומן בחץ) כדי לפתוח את תפריט המערכת.",
        focusRegions = listOf(
            FocusRegion(left = 0.55f, top = 0.48f, right = 0.98f, bottom = 0.78f)
        )
    ),
    // ── 4: iCount — הגדרות ───────────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_ic_hagdarot,
        title = "בחר הגדרות",
        description = "מתוך תפריט מערכת, לחץ על הגדרות (המסומן בחץ).",
        focusRegions = listOf(
            FocusRegion(left = 0.55f, top = 0.48f, right = 0.98f, bottom = 0.78f)
        )
    ),
    // ── 5: iCount — אוטומציה ─────────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_ic_otomatzya,
        title = "אוטומציה",
        description = "בתפריט הגדרות, לחץ על אוטומציה (המסומן בחץ) להגדרת חיבור אוטומטי.",
        focusRegions = listOf(
            FocusRegion(left = 0.18f, top = 0.35f, right = 0.60f, bottom = 0.65f)
        )
    ),
    // ── 6: iCount — API Tokens tab ───────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_ic_api_tab,
        title = "לשונית API Tokens",
        description = "בדף ההגדרות, לחץ על לשונית API Tokens (המסומנת בחץ).",
        focusRegions = listOf(
            FocusRegion(left = 0.35f, top = 0.00f, right = 0.78f, bottom = 0.22f)
        )
    ),
    // ── 7: iCount — יצירת טוקן ──────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_ic_tzor_token,
        title = "צור טוקן API",
        description = "לחץ על יצירת טוקן API (המסומן בחץ), העתק את הטוקן שנוצר והזן אותו בהגדרות האפליקציה.",
        focusRegions = listOf(
            FocusRegion(left = 0.00f, top = 0.00f, right = 0.28f, bottom = 0.22f)
        )
    ),
    // ── 8: Share button ──────────────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf2,
        title = "לחץ על כפתור השיתוף",
        description = "כנסו לאישור תשלום בביט/פייבוקס.\nלחצו על סמל השיתוף (מסומן באדום) כדי לשתף את אישור התשלום."
    ),
    // ── 9: Share row ─────────────────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf3,
        title = "מצא את האייקון",
        description = "במסך השיתוף לחצו על אייקון 'קבלה'.\nבפעם הראשונה לחצו 'עוד' ולחצו על האייקון במסך האפליקציות."
    ),
    // ── 10: Send to developer ────────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf4,
        title = "בדוק את הפענוח",
        description = "בדוק שהשם והסכום זוהו נכון.\nאם יש שגיאה — לחץ על 'שלח למפתח' לדיווח."
    )
)

// ── TutorialScreen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()

    var refreshTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "אוטוקבלה",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "הגדרה ראשונית",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(20.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false
        ) { page ->
            TutorialSlideView(slide = SLIDES[page], refreshTick = refreshTick)
        }

        // Page dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            repeat(SLIDES.size) { i ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == i) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == i)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                )
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    modifier = Modifier.weight(1f)
                ) { Text("הקודם") }
            } else {
                Spacer(Modifier.weight(1f))
            }

            val isLast = pagerState.currentPage == SLIDES.size - 1
            val currentSlide = SLIDES[pagerState.currentPage]
            val isPermissionSlide = currentSlide.isPermissionGranted != null
            val granted = if (isPermissionSlide) {
                remember(refreshTick) { currentSlide.isPermissionGranted!!(context) }
            } else false

            Button(
                onClick = {
                    if (isPermissionSlide && !granted) {
                        val intent = if (currentSlide.permissionSettingsPackage != null) {
                            Intent(currentSlide.permissionSettingsAction).apply {
                                data = Uri.parse("package:${currentSlide.permissionSettingsPackage}")
                            }
                        } else {
                            Intent(currentSlide.permissionSettingsAction)
                        }
                        context.startActivity(intent)
                    } else if (isLast) {
                        onDone()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = if (isPermissionSlide && !granted)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors()
            ) {
                Text(
                    when {
                        isPermissionSlide && !granted -> currentSlide.permissionLabel ?: "פתח הגדרות"
                        isLast -> "סיום"
                        else -> "הבא"
                    }
                )
            }
        }
    }
}

// ── Slide view ────────────────────────────────────────────────────────────────

@Composable
private fun TutorialSlideView(slide: TutorialSlide, refreshTick: Int) {
    val context = LocalContext.current
    val granted = if (slide.isPermissionGranted != null) {
        remember(refreshTick) { slide.isPermissionGranted(context) }
    } else null

    // ── Zoom state (resets automatically when page changes — new composable instance) ──
    val scaleAnim   = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    var focusIdx    by remember { mutableStateOf(-1) }   // -1 = full view
    var hasInteracted by remember { mutableStateOf(false) }

    // Measured container size (pixels) — updated by onSizeChanged
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val scope = rememberCoroutineScope()
    val hasFocus = slide.focusRegions.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            when {
                slide.drawableRes != null -> {
                    val painter = painterResource(slide.drawableRes)
                    val intrinsicW = painter.intrinsicSize.width
                    val intrinsicH = painter.intrinsicSize.height

                    // ── Compute rendered image size inside container (ContentScale.Fit) ──
                    val cW = containerSize.width.toFloat()
                    val cH = containerSize.height.toFloat()
                    val renderedW: Float
                    val renderedH: Float
                    if (cW > 0 && cH > 0 && intrinsicW > 0 && intrinsicH > 0) {
                        val fitScale = min(cW / intrinsicW, cH / intrinsicH)
                        renderedW = intrinsicW * fitScale
                        renderedH = intrinsicH * fitScale
                    } else {
                        renderedW = cW
                        renderedH = cH
                    }

                    // ── Zoom helpers ──────────────────────────────────────────
                    fun animateToRegion(region: FocusRegion) {
                        if (renderedW <= 0f || renderedH <= 0f) return
                        val regionW = (region.right  - region.left) * renderedW
                        val regionH = (region.bottom - region.top)  * renderedH
                        val targetScale = min(cW / regionW, cH / regionH).coerceIn(1f, 3f)
                        val regionCX = (region.left + region.right)  / 2f * renderedW
                        val regionCY = (region.top  + region.bottom) / 2f * renderedH
                        val targetOffX = -(regionCX - renderedW / 2f) * targetScale
                        val targetOffY = -(regionCY - renderedH / 2f) * targetScale
                        scope.launch {
                            launch { scaleAnim.animateTo(targetScale,  spring(dampingRatio = 0.75f)) }
                            launch { offsetXAnim.animateTo(targetOffX, spring(dampingRatio = 0.75f)) }
                            launch { offsetYAnim.animateTo(targetOffY, spring(dampingRatio = 0.75f)) }
                        }
                    }

                    fun resetZoom() {
                        focusIdx = -1
                        scope.launch {
                            launch { scaleAnim.animateTo(1f,  spring()) }
                            launch { offsetXAnim.animateTo(0f, spring()) }
                            launch { offsetYAnim.animateTo(0f, spring()) }
                        }
                    }

                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = slide.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX      = scaleAnim.value
                                scaleY      = scaleAnim.value
                                translationX = offsetXAnim.value
                                translationY = offsetYAnim.value
                            }
                            // Pinch-to-zoom + pan (manual override)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scaleAnim.value * zoom).coerceIn(1f, 3f)
                                    val maxX = (newScale - 1f) * renderedW / 2f
                                    val maxY = (newScale - 1f) * renderedH / 2f
                                    scope.launch {
                                        scaleAnim.snapTo(newScale)
                                        if (newScale > 1f) {
                                            offsetXAnim.snapTo((offsetXAnim.value + pan.x).coerceIn(-maxX, maxX))
                                            offsetYAnim.snapTo((offsetYAnim.value + pan.y).coerceIn(-maxY, maxY))
                                        } else {
                                            offsetXAnim.snapTo(0f)
                                            offsetYAnim.snapTo(0f)
                                        }
                                    }
                                    hasInteracted = true
                                }
                            }
                            // Single tap → next focus region | Double tap → reset
                            .pointerInput("tap") {
                                detectTapGestures(
                                    onTap = {
                                        hasInteracted = true
                                        if (hasFocus) {
                                            val next = (focusIdx + 1) % slide.focusRegions.size
                                            focusIdx = next
                                            animateToRegion(slide.focusRegions[next])
                                        }
                                    },
                                    onDoubleTap = {
                                        hasInteracted = true
                                        if (scaleAnim.value > 1f) {
                                            resetZoom()
                                        } else if (hasFocus) {
                                            focusIdx = 0
                                            animateToRegion(slide.focusRegions[0])
                                        }
                                    }
                                )
                            },
                        contentScale = ContentScale.Fit,
                        alignment    = Alignment.Center
                    )

                    // ── Hint pill ─────────────────────────────────────────────
                    val hintText = when {
                        !hasInteracted && hasFocus          -> "הקש לפוקוס | גרור לזום"
                        scaleAnim.value > 1f                -> "הקש פעמיים לאיפוס"
                        else                                -> null
                    }
                    hintText?.let {
                        Text(
                            text     = it,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color    = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    // ── Focus region dots (when multiple regions) ─────────────
                    if (slide.focusRegions.size > 1 && focusIdx >= 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            slide.focusRegions.forEachIndexed { i, _ ->
                                Box(
                                    modifier = Modifier
                                        .size(if (i == focusIdx) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == focusIdx) Color.White
                                            else Color.White.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }

                slide.emoji != null -> {
                    Text(slide.emoji, fontSize = 72.sp)
                }
            }

            // Permission badge
            if (granted != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    if (granted) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "הרשאה אושרה",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF5722).copy(alpha = 0.9f)
                        ) {
                            Text(
                                "נדרש אישור",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Title
        Text(
            slide.title,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground
        )

        // Description
        Text(
            slide.description,
            style      = MaterialTheme.typography.bodyMedium,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )

        if (granted == true) {
            Text(
                "✓ ההרשאה אושרה בהצלחה",
                color      = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold,
                style      = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
