package com.autokabala.listener

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val TERMS_TEXT = """
תנאי שימוש – אוטוקבלה

עודכן לאחרונה: אפריל 2026

ברוכים הבאים לאוטוקבלה. השימוש באפליקציה מהווה הסכמה לתנאים המפורטים להלן. אנא קראו אותם בעיון לפני השימוש.

1. כללי
אוטוקבלה ("האפליקציה") היא כלי עזר לעצמאים המאפשר זיהוי אוטומטי של תשלומים שהתקבלו דרך ביט ופייבוקס והפקת קבלות ב-iCount. השימוש באפליקציה הינו באחריות המשתמש בלבד.

2. תנאי שימוש מותר
המשתמש רשאי להשתמש באפליקציה לצרכים עסקיים חוקיים בלבד. אין לעשות שימוש באפליקציה לצורך עיבוד עסקאות שאינן שייכות למשתמש, הונאה, הלבנת הון או כל פעילות בלתי חוקית אחרת.

3. הרשאות ופרטיות
האפליקציה מבקשת גישה להתראות המכשיר לצורך זיהוי תשלומים נכנסים. המידע מעובד באופן מקומי על גבי המכשיר בלבד ואינו נשלח לשרתים חיצוניים, למעט קריאות API הנדרשות לצורך הפקת הקבלה ב-iCount. האפליקציה אינה אוספת, שומרת או מעבירה מידע אישי לצדדים שלישיים.

4. אחריות מוגבלת
האפליקציה מסופקת "כמות שהיא" (AS IS). המפתח אינו אחראי לכל נזק ישיר או עקיף הנובע משימוש באפליקציה, לרבות שגיאות בזיהוי סכומים, שמות או תאריכים, קבלות שהופקו בצורה שגויה, או אי-עמידה בדרישות רשות המיסים. האחריות לבדיקת נכונות הקבלות המופקות חלה על המשתמש בלבד.

5. שירותי צד שלישי
האפליקציה משתמשת בממשק ה-API של iCount להפקת קבלות. השימוש ב-iCount כפוף לתנאי השימוש של iCount בנפרד. אוטוקבלה אינה קשורה לביט, לפייבוקס או ל-iCount ואינה מייצגת אותם.

6. שינויים בתנאים
המפתח שומר לעצמו את הזכות לעדכן תנאים אלה בכל עת. המשך השימוש באפליקציה לאחר עדכון התנאים מהווה הסכמה לתנאים המעודכנים.

7. סיום שימוש
המשתמש רשאי להפסיק את השימוש באפליקציה בכל עת. המפתח שומר לעצמו את הזכות להפסיק את פעילות האפליקציה בכל עת וללא הודעה מוקדמת.

בלחיצה על "מאשר" אתם מאשרים כי קראתם והבנתם את תנאי השימוש וכי אתם מסכימים להם.
"""

@Composable
fun TermsOfUseScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "תנאי שימוש",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Scrollable legal text
        Text(
            text = TERMS_TEXT.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Checkbox row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "קראתי ואני מאשר את תנאי השימוש",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f)
            ) {
                Text("לא מאשר")
            }
            Button(
                onClick = onAccept,
                enabled = checked,
                modifier = Modifier.weight(1f)
            ) {
                Text("מאשר")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
