package com.autokabala.listener

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.autokabala.listener.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val TutorialTextColor = Color(0xFF64B5F6) // light blue
private val TutorialDescriptionColor = Color(0xFF5C8DFF) // dark/royal blue, legible on the dark tutorial background

private data class TutorialSlide(
    val drawableRes: Int?,
    val title: String,
    val description: String,
    val imageAlignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    val contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    val settingsAction: String? = null  // Android settings action to open, null = no button
)

private val SLIDES = listOf(
    TutorialSlide(
        drawableRes = R.drawable.tutorial_setup_welcome,
        title = "ברוכים הבאים לאוטוקבלה!",
        description = "כשמתקבל אישור תשלום בביט או פייבוקס, משתפים אותו עם אוטוקבלה דרך תפריט השיתוף — האפליקציה קוראת את פרטי התשלום ומציעה להפיק קבלה.\nשימו לב: השיתוף הוא ידני בכל פעם — האפליקציה לא מזהה תשלומים אוטומטית.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_setup_overlay,
        title = "הרשאה: הצגה מעל אפליקציות",
        description = "כדי שטופס הקבלה יוצג מעל ביט ופייבוקס, יש לאשר הצגה מעל אפליקציות אחרות.\nלחץ על הכפתור כדי לעבור להגדרות ולאשר.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
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
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_settings_nav,
        title = "שלב 3: כנסו להגדרות",
        description = "לאחר פתיחת תפריט מערכת, לחצו על 'הגדרות' כפי שמסומן בחץ.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_settings,
        title = "שלב 4: בחרו אוטומציה",
        description = "בדף ההגדרות לחצו על הכרטיס 'אוטומציה' כפי שמסומן בחץ.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_icount_api_tokens,
        title = "שלב 5: לשונית API Tokens",
        description = "בדף האוטומציה לחצו על הלשונית 'API Tokens' כפי שמסומן בחץ.",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
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
        title = "בדקו את הפרטים והפיקו קבלה",
        description = "ודאו שהשם בשדה 'לכבוד' נכון (אפשר לבחור לקוח אחר), ואם צריך הוסיפו תיאור לשירות.\nלחצו 'הפק קבלה' — הקבלה תופק אוטומטית דרך iCount.\nאם השם או הסכום לא תואמים למה שמופיע בביט, אפשר לתקן אותם כאן, או ללחוץ 'שלח למפתח' כדי לדווח על טעות.",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        imageAlignment = androidx.compose.ui.Alignment.TopCenter
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

        val currentSlide = SLIDES[pagerState.currentPage]

        // Settings button — shown only on permission slides
        if (currentSlide.settingsAction != null) {
            Button(
                onClick = {
                    val intent = if (currentSlide.settingsAction == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    } else {
                        Intent(currentSlide.settingsAction)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0)
                )
            ) {
                Text("פתח הגדרות לאישור ההרשאה")
            }
            Spacer(Modifier.height(8.dp))
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
                androidx.compose.foundation.Image(
                    painter = painterResource(slide.drawableRes),
                    contentDescription = slide.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = slide.contentScale,
                    alignment = slide.imageAlignment
                )
            } else {
                Text(
                    "📸\n\n[הוסף תמונה]",
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
            color = TutorialDescriptionColor,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
