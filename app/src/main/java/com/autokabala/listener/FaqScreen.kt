package com.autokabala.listener

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FaqItem(val question: String, val answer: String)

private val faqItems = listOf(
    FaqItem(
        "איך מופיעים לקוחות באפליקציה?",
        "לקוחות מתווספים אוטומטית מתשלומים שמגיעים דרך Bit ו-Paybox. ניתן גם לשייך תשלום ללקוח קיים על ידי לחיצה על התשלום בטאב תשלומים."
    ),
    FaqItem(
        "איך מפיקים קבלה?",
        "לחצו על תשלום → שייכו ללקוח → לחצו \"הפק קבלה\". תופיע תיבת אישור עם פרטי הקבלה לפני ההפקה. הקבלה מופקת דרך iCount ונשלחת ללקוח."
    ),
    FaqItem(
        "איך יוצרים פגישה מתוכננת?",
        "לכו לטאב \"מתוכנן\" ← לחצו על כפתור + \"צור פגישה\" ← בחרו לקוח, תאריך ושעה. הפגישה תתווסף אוטומטית ל-Google Calendar שלכם."
    ),
    FaqItem(
        "איך שולחים תזכורת ב-WhatsApp?",
        "בטאב \"תזכורות\" לחצו על \"שלח\" ליד שם הלקוח. WhatsApp ייפתח עם הודעה מוכנה. אם ללקוח יש מספר טלפון, הצ'אט ייפתח ישירות אליו."
    ),
    FaqItem(
        "איך מוסיפים מספר טלפון ללקוח?",
        "לחצו על שם הלקוח בכל מקום באפליקציה ← ייפתח מסך פרטי לקוח ← ערכו את שדה הטלפון ושמרו."
    ),
    FaqItem(
        "מה הבועה שמופיעה על המסך?",
        "זו שכבת-על (Overlay) שמאפשרת לכם לשייך תשלום ולהפיק קבלה מבלי לעזוב את האפליקציה שבה אתם משתמשים. היא מופיעה אוטומטית כשמגיע תשלום חדש."
    ),
    FaqItem(
        "האפליקציה עובדת עם Bit ו-Paybox?",
        "כן. האפליקציה מאזינה להתראות של Bit ו-Paybox ומזהה תשלומים נכנסים אוטומטית. יש לאשר הרשאת גישה להתראות בהגדרות."
    ),
    FaqItem(
        "למה לא מזוהים תשלומים?",
        "בדקו שהאזנה להתראות מופעלת בהגדרות (הגדרות ← הרשאות). ודאו ש-Bit/Paybox מותקנים ושהתראות שלהם מאושרות במכשיר."
    ),
    FaqItem(
        "איך מסנכרנים עם Google Calendar?",
        "הפגישות המתוכננות מתווספות אוטומטית ל-Google Calendar בעת יצירתן. לסנכרן לקוחות ידנית לכו להגדרות ← \"סנכרן לקוחות\"."
    ),
    FaqItem(
        "איך עורכים פגישה קיימת?",
        "בטאב \"מתוכנן\" לחצו על אייקון העיפרון בכרטיס הפגישה. ניתן לשנות לקוח, תאריך, שעה וסכום."
    )
)

@Composable
fun FaqScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            "שאלות נפוצות",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(faqItems) { item ->
                FaqRow(item)
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun FaqRow(item: FaqItem) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                item.answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
