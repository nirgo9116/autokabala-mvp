package com.autokabala.listener

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class TutorialSlide(
    val drawableRes: Int?,
    val title: String,
    val description: String,
    val imageAlignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    val contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit
)

private val SLIDES = listOf(
    TutorialSlide(
        drawableRes = R.drawable.tutorial_ishur,
        title = "שתף אישור תשלום",
        description = "כנס לאישור תשלום בביט ולחץ על כפתור השיתוף\n(מסומן בעיגול אדום בתמונה)",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_shituf,
        title = "בחר אוטוקבלה",
        description = "במסך השיתוף לחץ על האייקון של אוטוקבלה.\nאם לא מופיע לחץ על 'עוד' ובחר אוטוקבלה",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        imageAlignment = androidx.compose.ui.Alignment.BottomCenter
    ),
    TutorialSlide(
        drawableRes = R.drawable.tutorial_kabala,
        title = "בדוק את הקבלה",
        description = "אם השם או הסכום לא זהים לאלה שבביט\nלחץ על 'שלח למפתח'",
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
        Spacer(Modifier.height(24.dp))

        Text(
            "ברוך הבא לאוטוקבלה!",
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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(16.dp))
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
                    "📸\n\n[תמונה תופיע כאן]",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            slide.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            slide.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}
