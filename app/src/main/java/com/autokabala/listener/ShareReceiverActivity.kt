package com.autokabala.listener

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Transparent, no-history Activity that handles plain-text payment shares from Bit/Paybox.
 *
 * Fallback flow (used when the iCount API is unavailable):
 *   1. Receive shared text via Intent.EXTRA_TEXT
 *   2. Parse payment details (name, amount, description)
 *   3. Copy formatted receipt data to clipboard
 *   4. Open iCount immediately
 *   5. Finish — user pastes manually inside iCount
 *
 * Lifecycle:
 *   onCreate  →  receiveSharedText()
 *             →  parsePaymentText()
 *             →  copyToClipboard()
 *             →  openICount()
 *             →  finish()
 *
 * The Activity has no layout, no visible UI, and never appears in the back-stack
 * (android:noHistory="true" in the manifest). Execution is synchronous and completes
 * in a single onCreate call.
 */
class ShareReceiverActivity : Activity() {

    companion object {
        private const val TAG               = "ShareReceiver"
        private const val ICOUNT_PACKAGE    = "il.co.icount.app"
        private const val ICOUNT_WEB_URL    = "https://app.icount.co.il/hash/create_doc.php?doctype=receipt"
        // Pause between Toast and iCount launch — long enough for the Toast to register
        // visually, short enough to feel instant. 150ms is imperceptible as "waiting".
        private const val HANDOFF_DELAY_MS  = 150L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Suppress the default Activity enter animation — this Activity is transparent
        // and should be invisible. Without this, Android briefly flashes the window
        // background colour during the transition, which looks like a bug.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        if (intent?.action != Intent.ACTION_SEND) {
            Log.w(TAG, "Unexpected action: ${intent?.action} — finishing")
            finish()
            return
        }

        val text = receiveSharedText()
        if (text == null) {
            Log.w(TAG, "No shared text — finishing")
            finish()
            return
        }

        val formatted = parsePaymentText(text)
        copyToClipboard(formatted)

        // Brief pause: lets the Toast become visible on screen before iCount's
        // window covers everything. 150ms is imperceptible as a delay but gives
        // the user clear confirmation that something happened.
        Handler(Looper.getMainLooper()).postDelayed({
            openICount()
            finish()
            // Suppress the exit animation too — iCount should appear to slide in
            // from its own launch, not from this Activity sliding out.
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, HANDOFF_DELAY_MS)
    }

    // ── Step 3 — Receive ──────────────────────────────────────────────────────

    /** Reads EXTRA_TEXT from the incoming share intent. Returns null if absent. */
    private fun receiveSharedText(): String? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) {
            Log.w(TAG, "EXTRA_TEXT is null or blank")
            return null
        }
        Log.d(TAG, "Received shared text (${text.length} chars):\n$text")
        return text.trim()
    }

    // ── Step 4 — Parse ───────────────────────────────────────────────────────

    /**
     * Parses raw payment text into a formatted receipt string ready for clipboard.
     * Falls back to the raw text so the user always gets something useful.
     *
     * Reuses the existing [BitShareParser] and [PayboxShareParser] — both accept
     * plain text (same string passed as both hebrewText and latinText since no
     * OCR is involved here).
     */
    private fun parsePaymentText(raw: String): String {
        val data = if (PayboxShareParser.isPaybox(raw)) {
            Log.d(TAG, "Source: Paybox")
            PayboxShareParser.parse(hebrewText = raw, latinText = raw)
        } else {
            Log.d(TAG, "Source: Bit (default)")
            BitShareParser.parse(hebrewText = raw, latinText = raw)
        }

        if (data == null) {
            Log.w(TAG, "Parser returned null — falling back to raw text")
            return raw
        }

        val amountStr = if (data.amount % 1.0 == 0.0) {
            data.amount.toInt().toString()
        } else {
            data.amount.toString()
        }

        Log.d(TAG, "Parsed → name='${data.senderName}', amount=$amountStr")

        val description = extractDescription(raw, data.senderName)

        return buildString {
            append("לקוח: ${data.senderName}\n")
            append("סכום: ${amountStr}₪")
            if (description != null) {
                append("\nתיאור: $description")
                Log.d(TAG, "Description: '$description'")
            }
        }
    }

    /**
     * Finds a free-text description line in the raw payment message.
     *
     * Looks for lines that are not:
     *  - the sender name
     *  - an amount line (contains ₪ or is all-digits)
     *  - a date/time line
     *  - a known structural keyword (approval number, status, etc.)
     *
     * Bit example: "תשלום שיעור פרטי" appears after the amount line.
     */
    private fun extractDescription(raw: String, senderName: String): String? {
        val skipKeywords = listOf("₪", "מספר", "אישור", "PayBox", "paybox",
                                  "הועבר", "תאריך", "שעה", "סטטוס", "יתרה")
        val dateOrTime   = Regex("""\d{1,2}[./]\d{1,2}|\d{1,2}:\d{2}""")
        val firstNameWord = senderName.split(" ").firstOrNull() ?: ""

        return raw.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .firstOrNull { line ->
                skipKeywords.none { line.contains(it) } &&
                !dateOrTime.containsMatchIn(line) &&
                !line.all { it.isDigit() || it == ' ' || it == ',' } &&
                (firstNameWord.isBlank() || !line.contains(firstNameWord))
            }
    }

    // ── Step 5 — Clipboard ───────────────────────────────────────────────────

    /** Copies [content] to the system clipboard and shows a Toast confirmation. */
    private fun copyToClipboard(content: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("icount_receipt", content)
        clipboard.setPrimaryClip(clip)
        Log.d(TAG, "Copied to clipboard:\n$content")
        Toast.makeText(this, "פרטי הקבלה הועתקו", Toast.LENGTH_SHORT).show()
    }

    // ── Step 6 — Open iCount ─────────────────────────────────────────────────

    /**
     * Opens the iCount native app if installed, otherwise falls back to the
     * iCount web receipt page in the default browser.
     *
     * Both intents carry FLAG_ACTIVITY_NEW_TASK so they launch cleanly from
     * this transparent, no-history Activity.
     */
    private fun openICount() {
        // Attempt 1: launch installed iCount native app
        val nativeIntent = packageManager.getLaunchIntentForPackage(ICOUNT_PACKAGE)
        if (nativeIntent != null) {
            Log.d(TAG, "Opening iCount native app ($ICOUNT_PACKAGE)")
            nativeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(nativeIntent)
            return
        }

        // Attempt 2: open iCount receipt page in browser
        Log.d(TAG, "iCount app not installed — opening web fallback")
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ICOUNT_WEB_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(webIntent)
    }
}
