package com.autokabala.listener

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as AutoKabalaApplication).database

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return
        val updatedMessages = _messages.value + ChatMessage("user", userText)
        _messages.value = updatedMessages

        viewModelScope.launch {
            _isLoading.value = true
            val systemPrompt = buildSystemPrompt()
            val response = ClaudeApiClient.sendMessage(
                systemPrompt = systemPrompt,
                messages = updatedMessages
            )
            _messages.value = updatedMessages + ChatMessage(
                role = "assistant",
                content = response ?: "מצטער, לא הצלחתי להתחבר. בדוק את מפתח ה-API ונסה שוב."
            )
            _isLoading.value = false
        }
    }

    private suspend fun buildSystemPrompt(): String {
        val clients = database.clientDao().getAllClientsSnapshot()
        val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val recentPayments = database.paymentDao().getRecentPaymentsSnapshot(since)
        val pendingPayments = database.paymentDao().getPendingPaymentsSnapshot()

        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val today = fmt.format(Date())

        val clientsText = if (clients.isEmpty()) "אין לקוחות רשומים" else
            clients.joinToString("\n") { c ->
                "- ${c.name} (ID: ${c.id})" +
                        (if (c.phone != null) ", טל: ${c.phone}" else "") +
                        (if (c.email != null) ", מייל: ${c.email}" else "")
            }

        val pendingText = if (pendingPayments.isEmpty()) "אין תשלומים ממתינים" else
            pendingPayments.joinToString("\n") { p ->
                "- ${p.senderName}: ₪${String.format("%.0f", p.amount)} מ-${p.source} (${fmt.format(Date(p.timestamp))})"
            }

        val processedThisMonth = recentPayments.filter { p ->
            val cal = java.util.Calendar.getInstance()
            val pCal = java.util.Calendar.getInstance().apply { timeInMillis = p.timestamp }
            p.status == "processed" &&
                    pCal.get(java.util.Calendar.MONTH) == cal.get(java.util.Calendar.MONTH) &&
                    pCal.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
        }
        val monthlyTotal = processedThisMonth.sumOf { it.issuedAmount ?: it.amount }

        val historyText = if (recentPayments.isEmpty()) "אין היסטוריה" else
            recentPayments.take(30).joinToString("\n") { p ->
                val name = p.clientName ?: p.senderName
                val amount = String.format("%.0f", p.issuedAmount ?: p.amount)
                val date = fmt.format(Date(p.timestamp))
                val status = when (p.status) {
                    "processed" -> "קבלה ${p.docNum ?: "הונפקה"}"
                    "pending" -> "ממתין"
                    else -> p.status
                }
                "- $name: ₪$amount (${p.source}, $date) — $status"
            }

        return """אתה עוזר CRM חכם לעסק עצמאי ישראלי. האפליקציה מזהה תשלומים מ-Bit ו-Paybox ומנפיקה קבלות דרך מערכת iCount.

ענה **בעברית** תמיד, בצורה ידידותית וקצרה. השתמש במספרים ובנתונים האמיתיים שמטה.

היום: $today

---
**לקוחות רשומים (${clients.size} לקוחות):**
$clientsText

---
**תשלומים ממתינים לטיפול (${pendingPayments.size}):**
$pendingText

---
**סיכום חודש נוכחי:** ₪${String.format("%.0f", monthlyTotal)} (${processedThisMonth.size} קבלות)

---
**היסטוריית תשלומים — 90 יום אחרונים (${recentPayments.size} סה"כ):**
$historyText

---
אתה יכול לענות על שאלות לגבי לקוחות, תשלומים, הכנסות וסטטיסטיקות.
לא ניתן לבצע פעולות ישירות דרך הצ'אט (כמו הנפקת קבלות) — רק מידע ותובנות.""".trimIndent()
    }
}
