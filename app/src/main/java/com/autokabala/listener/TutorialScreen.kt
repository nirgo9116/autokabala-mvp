package com.autokabala.listener

import com.autokabala.listener.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch

private val TutorialTextColor = Color(0xFF64B5F6) // light blue

/** Normalized (0–1) position of a red circle drawn on top of an iCount screenshot. */
private data class CircleHighlight(
    val centerX: Float,
    val centerY: Float,
    val radiusRatio: Float = 0.09f  // fraction of min(renderedWidth, renderedHeight)
)

private data class TutorialSlide(
    val drawableRes: Int?,
    val title: String,
    val description: String,
    val imageAlignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    val contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    val circleHighlight: CircleHighlight? = null
)

private val SLIDES = listOf(
    TutorialSlide(
        drawableRes = R.drawable.tutorial_welcome_logo,
        title = "ברוכים הבאים לאוטוקבלה!",
        description = "האפליקציה מפיקה קבלה אוטומטית ברגע שמאשרים תשלום מביט או פייבוקס.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_homescreen_settings,
        title = "הרשאה ראשונה: הצגה מעל אפליקציות",
        description = "פתחו את מסך הבית ולחצו על אייקון 'הגדרות' (מסומן בעיגול).",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_settings_main,
        title = "בחרו יישומים",
        description = "בתוך הגדרות, גללו ולחצו על 'יישומים'.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_apps_list,
        title = "מצאו את אוטוקבלה",
        description = "ברשימת היישומים מצאו את AutoKabalaListener ולחצו עליו.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_overlay_permission,
        title = "אפשרו הצגה מעל אפליקציות",
        description = "בפרטי היישום לחצו על 'מוצג מעל פריטים אחרים' ואפשרו אותו.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_overlay_toggle,
        title = "אשרו את ההרשאה",
        description = "הפעילו את המתג 'אשר הרשאה' עבור AutoKabalaListener.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_notif_permission,
        title = "הרשאה שנייה: גישה להתראות",
        description = "הגדרות ← גישה להתראות ← הפעילו את המתג של AutoKabalaListener.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_login,
        title = "שלב 1: כניסה ל-iCount",
        description = "כנסו לאתר app.icount.co.il והתחברו עם המייל, מזהה החברה והסיסמה שלכם.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_dashboard,
        title = "שלב 2: פתחו את תפריט מערכת",
        description = "בתחתית סרגל הניווט הימני לחצו על 'מערכת' (החץ מסמן היכן ללחוץ).",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        circleHighlight = CircleHighlight(centerX = 0.913f, centerY = 0.700f, radiusRatio = 0.09f)
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_settings_nav,
        title = "שלב 3: כנסו להגדרות",
        description = "לאחר פתיחת תפריט מערכת, לחצו על 'הגדרות' כפי שמסומן בחץ.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        circleHighlight = CircleHighlight(centerX = 0.905f, centerY = 0.838f, radiusRatio = 0.09f)
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_settings,
        title = "שלב 4: בחרו אוטומציה",
        description = "בדף ההגדרות לחצו על הכרטיס 'אוטומציה' כפי שמסומן בחץ.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        circleHighlight = CircleHighlight(centerX = 0.382f, centerY = 0.892f, radiusRatio = 0.13f)
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_api_tokens,
        title = "שלב 5: לשונית API Tokens",
        description = "בדף האוטומציה לחצו על הלשונית 'API Tokens' כפי שמסומן בחץ.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        circleHighlight = CircleHighlight(centerX = 0.305f, centerY = 0.499f, radiusRatio = 0.10f)
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_create_token,
        title = "שלב 6: צרו טוקן API",
        description = "לחצו על 'יצירת טוקן API' כפי שמסומן בחץ. לאחר יצירת הטוקן, העתיקו אותו ועברו להגדרות האפליקציה ← חיבור ל-iCount ← שדה 'טוקן API'.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_connect,
        title = "שלב 7: הזינו את פרטי החיבור באפליקציה",
        description = "בהגדרות אוטוקבלה, תחת 'חיבור ל-iCount', מלאו:\n• מזהה חברה (CID) — מספר החברה ב-iCount\n• שם משתמש — המייל שלכם\n• טוקן API — הטוקן שיצרתם בשלב 6\nלאחר מכן לחצו 'סנכרן לקוחות'.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_ishur,
        title = "שתפו אישור תשלום",
        description = "כנסו לאישור תשלום בביט ולחצו על כפתור השיתוף\n(מסומן בעיגול אדום בתמונה)",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf,
        title = "בחרו אוטוקבלה",
        description = "במסך השיתוף לחצו על האייקון של אוטוקבלה.\nאם לא מופיע לחצו על 'עוד' ובחרו אוטוקבלה.\nטיפ: לאחר הלחיצה הראשונה האנדרואיד יזכור את הבחירה ואוטוקבלה תופיע ראשונה בפעם הבאה.",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        imageAlignment = androidx.compose.ui.Alignment.BottomCenter
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_kabala,
        title = "בדקו את הקבלה",
        description = "אם השם או הסכום לא זהים לאלה שבביט\nלחצו על 'שלח למפתח'",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        imageAlignment = androidx.compose.ui.Alignment.TopCenter
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(onClick = onDone) {
                Text("דלג", color = TutorialTextColor)
            }
        }

        Text(
            "ברוכים הבאים לאוטוקבלה!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "כך משתמשים באפליקציה",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            TutorialSlideView(slide = SLIDES[page])
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

            Button(
                onClick = {
                    if (pagerState.currentPage < SLIDES.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onDone()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (pagerState.currentPage < SLIDES.size - 1) "הבא" else "סיום")
            }
        }
    }
}

@Composable
private fun TutorialSlideView(slide: TutorialSlide) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (slide.drawableRes != null) {
                val painter = painterResource(slide.drawableRes)
                var boxSize by remember { mutableStateOf(Size.Zero) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { boxSize = it.toSize() }
                ) {
                    ZoomableImage(
                        drawableRes = slide.drawableRes,
                        contentDescription = slide.title,
                        contentScale = slide.contentScale,
                        alignment = slide.imageAlignment,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Red circle overlay — iCount slides only
                    val ch = slide.circleHighlight
                    if (ch != null && boxSize != Size.Zero) {
                        val imgSize = painter.intrinsicSize
                        if (imgSize.width > 0f && imgSize.height > 0f) {
                            val scale = minOf(
                                boxSize.width / imgSize.width,
                                boxSize.height / imgSize.height
                            )
                            val rendW = imgSize.width * scale
                            val rendH = imgSize.height * scale
                            val left = (boxSize.width - rendW) / 2f
                            val top  = (boxSize.height - rendH) / 2f
                            val cx = left + ch.centerX * rendW
                            val cy = top  + ch.centerY * rendH
                            val r  = ch.radiusRatio * minOf(rendW, rendH)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = Color(0xFFFF3B30),
                                    radius = r,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "📸\n\n[תמונה תופיע כאן]",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            slide.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = TutorialTextColor
        )

        Text(
            slide.description,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = TutorialTextColor,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun ZoomableImage(
    drawableRes: Int,
    contentDescription: String,
    contentScale: ContentScale,
    alignment: Alignment,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var composableSize by remember { mutableStateOf(Size.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale > 1f) constrainOffset(offset + panChange, newScale, composableSize)
                 else Offset.Zero
    }

    Box(
        modifier = modifier
            .onSizeChanged { composableSize = it.toSize() }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        val newScale = 2.5f
                        val rawOffset = Offset(
                            (composableSize.width / 2f - tapOffset.x) * (newScale - 1f),
                            (composableSize.height / 2f - tapOffset.y) * (newScale - 1f)
                        )
                        scale = newScale
                        offset = constrainOffset(rawOffset, newScale, composableSize)
                    }
                }
            }
            .transformable(state = transformableState)
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = contentScale,
            alignment = alignment
        )

        if (scale == 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "🔍 לחץ להגדלה",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun constrainOffset(offset: Offset, scale: Float, size: Size): Offset {
    if (size == Size.Zero) return offset
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}
