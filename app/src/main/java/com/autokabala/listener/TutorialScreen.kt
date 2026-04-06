package com.autokabala.listener

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

// ── Slide model ───────────────────────────────────────────────────────────────

private data class TutorialSlide(
    val emoji: String? = null,
    val drawableRes: Int? = null,
    val title: String,
    val description: String,
    val imageAlignment: Alignment = Alignment.Center,
    val contentScale: ContentScale = ContentScale.Fit,
    // Permission slide fields
    val permissionLabel: String? = null,           // e.g. "פתח הגדרות"
    val permissionSettingsAction: String? = null,  // Settings.ACTION_*
    val permissionSettingsPackage: String? = null, // package for overlay intent
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
    // ── 3: Share button — FillWidth so top+bottom visible (less zoom) ────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf2,
        title = "לחץ על כפתור השיתוף",
        description = "כנסו לאישור תשלום בביט/פייבוקס.\nלחצו על סמל השיתוף (מסומן באדום) כדי לשתף את אישור התשלום.",
        contentScale = ContentScale.FillWidth,
        imageAlignment = Alignment.TopCenter
    ),
    // ── 4: Share row — show bottom app row ───────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf3,
        title = "מצא את האייקון",
        description = "במסך השיתוף לחצו על אייקון 'קבלה'.\nבפעם הראשונה לחצו 'עוד' ולחצו על האייקון במסך האפליקציות.",
        contentScale = ContentScale.FillWidth,
        imageAlignment = Alignment.BottomCenter
    ),
    // ── 5: Send to developer ─────────────────────────────────────────────────
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf4,
        title = "בדוק את הפענוח",
        description = "בדוק שהשם והסכום זוהו נכון.\nאם יש שגיאה — לחץ על 'שלח למפתח' לדיווח.",
        contentScale = ContentScale.Fit
    )
)

// ── TutorialScreen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()

    // Re-check permissions when user returns from Settings
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

        // Page indicators
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
                // refreshTick forces recomposition on resume
                remember(refreshTick) { currentSlide.isPermissionGranted!!(context) }
            } else false

            Button(
                onClick = {
                    if (isPermissionSlide && !granted) {
                        // Open settings instead of advancing
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
                else
                    ButtonDefaults.buttonColors()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                slide.drawableRes != null -> {
                    androidx.compose.foundation.Image(
                        painter = painterResource(slide.drawableRes),
                        contentDescription = slide.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = slide.contentScale,
                        alignment = slide.imageAlignment
                    )
                }
                slide.emoji != null -> {
                    Text(slide.emoji, fontSize = 72.sp)
                }
            }

            // Permission badge
            if (granted != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
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

        Text(
            slide.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            slide.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )

        // After granting, show encouragement
        if (granted == true) {
            Text(
                "✓ ההרשאה אושרה בהצלחה",
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
