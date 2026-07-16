package com.autokabala.listener

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Current terms version. Bump this and users will be asked to re-accept. */
const val TERMS_VERSION = 1

private const val SHORT_TERMS_HE = """אוטוקבלה מזהה תשלומים (ביט, פייבוקס) דרך התראות המכשיר ומפיקה עבורם קבלות ב-iCount.

• הזיהוי מתבצע במכשיר שלכם; פרטי התשלום (סכום, שם, תיאור) נשלחים ל-iCount לצורך הפקת הקבלה בפועל.
• חלק מהשירותים עשויים להיות בתשלום, במחיר שיוצג באפליקציה; שינוי מחיר ייכנס לתוקף בהודעה מראש של 30 יום לפחות.
• האפליקציה מסופקת כפי שהיא — ייתכנו טעויות בזיהוי או בהפקת קבלות, והאחריות לבדוק את נכונות הנתונים היא עליכם.
• האפליקציה אינה מהווה ייעוץ חשבונאי או משפטי.
• ניתן להפסיק את השימוש בכל עת; ביטול מנוי בתשלום מתבצע דרך החנות שבה בוצעה הרכישה."""

private const val FULL_TERMS_HE = """אוטוקבלה – תנאי שימוש
עודכן לאחרונה: אפריל 2026

ברוכים הבאים לאוטוקבלה. השימוש באפליקציה מהווה הסכמה לתנאים המפורטים להלן. אם אינך מסכים לתנאים, אין להשתמש באפליקציה.

1. כללי
אוטוקבלה היא אפליקציה המסייעת בזיהוי תשלומים (ביט, פייבוקס ואמצעים דומים) ובהפקת קבלות באמצעות iCount. השימוש באפליקציה הוא באחריות המשתמש בלבד.

2. גיל וכשירות משפטית
השימוש באפליקציה מיועד למשתמשים בני 18 ומעלה, או לעסקים/עצמאים הפועלים כדין. משתמש המשתמש באפליקציה מטעם עסק מצהיר שהוא מוסמך להתחייב בשם אותו עסק.

3. שימוש מותר
השימוש באפליקציה מותר לצרכים חוקיים בלבד. אין להשתמש בה לצורך פעילות בלתי חוקית, עיבוד מידע שאינו שייך למשתמש, או פגיעה בצדדים שלישיים.

4. פרטיות והרשאות
האפליקציה קוראת התראות תשלום נבחרות (מאפליקציות כמו ביט ופייבוקס) לצורך זיהוי אוטומטי של תשלומים ויצירת קבלה, ואינה קוראת או מעבדת התראות שאינן קשורות לתשלומים. זיהוי וניתוח ההתראות מתבצע במכשיר המשתמש בלבד. לצורך הפקת קבלה בפועל, פרטי התשלום הרלוונטיים (סכום, שם משלם, תיאור) נשלחים ל-iCount באמצעות ה-API שלה ונשמרים במערכות iCount בהתאם למדיניות הפרטיות שלה. מדיניות פרטיות מפורטת תפורסם בנפרד ותהיה זמינה בתוך האפליקציה וב-Google Play.

5. שירותים בתשלום
חלק מהשירותים באפליקציה עשויים להיות בתשלום. המחירים העדכניים יוצגו באפליקציה. ייתכן חיוב חודשי או חד-פעמי, בהתאם לתוכנית שנרכשה. שינוי מחירים ייכנס לתוקף לא לפני 30 יום ממועד הודעה על כך בתוך האפליקציה, ולא יחול על תקופת חיוב ששולמה מראש. תנאי ביטול/החזר כספי יהיו בהתאם למדיניות הפלטפורמה (Google Play וכו׳) ולהוראות חוק הגנת הצרכן, תשמ"א-1981, ככל שהן חלות.

6. ביטול והפסקת שירות
המשתמש רשאי להפסיק את השימוש בכל עת. ביטול מנוי בתשלום ייעשה דרך הפלטפורמה שבה בוצעה הרכישה, ובכפוף למדיניותה. תשלומים עבור תקופה שכבר חלפה לא יוחזרו באופן רטרואקטיבי, אלא אם נדרש אחרת על-פי דין.

7. שירותי צד שלישי
האפליקציה משתמשת ב-iCount ובשירותים נוספים לצורך הפקת קבלות ותפעול. אוטוקבלה אינה אחראית לזמינות, לתקינות או לתקלות של שירותים אלה. האחריות על שמירת פרטי הגישה (מפתחות API, סיסמאות וכדומה) חלה על המשתמש בלבד.

8. קניין רוחני
כל הזכויות באפליקציה, לרבות עיצוב, קוד, סימני מסחר ותוכן, שייכות לאוטוקבלה או לבעלי הרישיון שלה. אין להעתיק, לשכפל או להנדס לאחור את האפליקציה ללא הסכמה מראש ובכתב.

9. הגבלת אחריות
האפליקציה מסופקת "כפי שהיא" (AS IS), ללא מצג או התחייבות מכל סוג, למעט התחייבויות שאינן ניתנות להגבלה על-פי דין. ייתכנו טעויות בזיהוי תשלומים או בהפקת קבלות; האחריות לבדוק את נכונות הנתונים והקבלות המופקות היא על המשתמש בלבד. האפליקציה אינה מהווה ייעוץ חשבונאי, מיסויי או משפטי. במידה המרבית המותרת בדין, אחריות אוטוקבלה לכל נזק לא תעלה על הסכום ששולם בפועל על-ידי המשתמש בששת החודשים שקדמו לאירוע.

10. שינויים בתנאים
ניתן לעדכן תנאים אלה מעת לעת. שינוי מהותי יוצג למשתמש לצורך אישור מחדש בתוך האפליקציה. המשך שימוש לאחר אישור כאמור מהווה הסכמה לתנאים המעודכנים.

11. סיום שירות
שמורה הזכות להפסיק את פעילות האפליקציה, כולה או חלקה, בכל עת ומכל סיבה, לרבות בשל הפרת תנאים אלה.

12. דין וסמכות שיפוט
על תנאים אלה יחולו דיני מדינת ישראל בלבד. סמכות השיפוט הבלעדית בכל עניין הנוגע לתנאים אלה תהא נתונה לבתי המשפט המוסמכים במחוז מרכז / תל אביב.

13. יצירת קשר
לשאלות בנוגע לתנאים אלה ניתן לפנות לכתובת: [להשלים כתובת מייל תמיכה].

בלחיצה על "אני מסכים" המשתמש מאשר שקרא את התנאים, הבין אותם, והסכים להם."""

@Composable
fun TermsScreen(onAccept: () -> Unit) {
    var showFullTerms by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }

    if (showFullTerms) {
        AlertDialog(
            onDismissRequest = { showFullTerms = false },
            title = { Text("תנאי שימוש מלאים") },
            text = {
                Text(
                    FULL_TERMS_HE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showFullTerms = false }) { Text("סגור") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "תנאי שימוש",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(SHORT_TERMS_HE, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showFullTerms = true }) {
                Text("קרא את התנאים המלאים")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text(
                "קראתי ואני מסכים/ה לתנאי השימוש",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onAccept,
            enabled = checked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("אני מסכים והמשך")
        }
    }
}
