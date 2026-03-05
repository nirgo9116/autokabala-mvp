package com.autokabala.listener

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object PayboxShareParser {

    private const val TAG = "PayboxParser"

    // Paybox title: "העברה מ[full contact name]" — "מ" may be directly attached to the name
    // (no space), e.g. "העברה מHanita מד\"א". The \s* handles both cases.
    private val titlePattern = Pattern.compile("""העברה מ\s*(.+)""")

    // Amount: Paybox shows ₪ before the number (e.g. ₪220)
    private val shekelReversed = Pattern.compile("""₪\s*([\d,]+\.?\d*)""")
    private val shekelNormal   = Pattern.compile("""([\d,]+\.?\d*)\s*₪""")
    private val anyNumber      = Pattern.compile("""([\d,]+\.?\d*)""")

    // Date/time — supports . and / separators
    private val datePattern         = Pattern.compile("""(\d{1,2}[./]\d{1,2}[./]\d{2,4})""")
    private val timePattern         = Pattern.compile("""(\d{1,2}:\d{2})""")
    private val dateTimeFormatShort = SimpleDateFormat("dd.MM.yy HH:mm",   Locale.getDefault())
    private val dateTimeFormatLong  = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────────────────

    fun isPaybox(text: String): Boolean =
        text.contains("הועבר אל") ||
        text.contains("מספר אישור") ||
        text.contains("PayBox", ignoreCase = true) ||
        text.contains("paybox", ignoreCase = true)

    fun parse(hebrewText: String, latinText: String = hebrewText, mlKitAmount: Double? = null, mlKitNameHint: String? = null): PaymentData? {
        val hebrewLines = hebrewText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val latinLines  = latinText.split("\n").map  { it.trim() }.filter { it.isNotBlank() }

        Log.d(TAG, "=== PayboxParser START ===")
        hebrewLines.forEachIndexed { i, line -> Log.d(TAG, "  Line[$i]: '$line'") }
        if (mlKitNameHint != null) Log.d(TAG, "ML Kit name hint (bbox above amount): '$mlKitNameHint'")

        val senderName = extractSenderName(hebrewLines, latinLines, mlKitNameHint) ?: run {
            Log.w(TAG, "Cannot extract sender name")
            return null
        }

        val amount = if (mlKitAmount != null) {
            Log.d(TAG, "Amount from ML Kit bounding-box: $mlKitAmount")
            mlKitAmount
        } else {
            extractAmount(hebrewLines)
        } ?: run {
            Log.w(TAG, "Cannot extract amount")
            return null
        }

        // Combine Tesseract + ML Kit lines for timestamp — ML Kit reads dates more accurately
        val timestamp = extractTimestamp(hebrewLines + latinLines)

        Log.d(TAG, "Result → name='$senderName', amount=$amount, ts=$timestamp")
        Log.d(TAG, "=== PayboxParser END ===")

        return PaymentData(
            source      = "paybox_share",
            senderName  = senderName,
            amount      = amount,
            isConfirmed = true,
            timestamp   = timestamp
        )
    }

    // ── Name ──────────────────────────────────────────────────────────────────

    private fun extractSenderName(lines: List<String>, latinLines: List<String>, mlKitNameHint: String? = null): String? {
        val tessName = extractNameFromLines(lines)

        if (tessName != null) {
            // Detect if Tesseract garbled a Latin name (e.g. "Hanita" → "גמזוחבח").
            // A name is considered all-Hebrew when every char is a Hebrew letter/mark or space/quote.
            val isAllHebrew = tessName.all {
                it in '\u05D0'..'\u05EA' || it in '\u05F0'..'\u05F4' ||
                it == ' ' || it == '"' || it == '\''
            }
            if (isAllHebrew) {
                // Extract Latin words from the ML Kit bounding-box hint (text above the amount).
                // Then build a combined name: ML Kit Latin words replace the corresponding
                // garbled Tesseract Hebrew words; any remaining Tesseract Hebrew words are kept.
                // Example: tessName="גמזוחבח מד\"א", mlKitLatin="Hanita" → "Hanita מד\"א"
                val mlKitLatin = extractLatinFromHint(mlKitNameHint)
                if (mlKitLatin != null) {
                    val combined = mergeLatinWithHebrewSuffix(mlKitLatin, tessName)
                    Log.d(TAG, "Combined name: '$combined' (Latin bbox: '$mlKitLatin', Tess: '$tessName')")
                    return combined
                }
                // Fallback: pattern-based ML Kit text extraction
                if (latinLines !== lines) {
                    extractNameFromLines(latinLines)?.let { latinName ->
                        Log.d(TAG, "Using ML Kit pattern '$latinName' (Tesseract all-Hebrew: '$tessName')")
                        return latinName
                    }
                }
            }
            return tessName
        }

        // Tesseract found nothing — try ML Kit
        if (latinLines !== lines) extractNameFromLines(latinLines)?.let { return it }
        extractLatinFromHint(mlKitNameHint)?.let { return it }
        return null
    }

    /**
     * Merges ML Kit Latin words with Tesseract's Hebrew suffix.
     * Latin words replace Tesseract words from left to right;
     * any remaining Tesseract words (genuine Hebrew) are appended.
     *
     * "Hanita" + "גמזוחבח מד\"א" → "Hanita מד\"א"
     * "John Smith" + "ג'בריש מד\"א" → "John Smith מד\"א"
     */
    private fun mergeLatinWithHebrewSuffix(latinName: String, tessName: String): String {
        val latinWords = latinName.split(" ")
        val tessWords  = tessName.split(" ").toMutableList()
        latinWords.forEachIndexed { i, word ->
            if (i < tessWords.size) tessWords[i] = word else tessWords.add(word)
        }
        return tessWords.joinToString(" ")
    }

    private fun extractNameFromLines(lines: List<String>): String? {
        for (line in lines) {
            val m = titlePattern.matcher(line)
            if (!m.find()) continue
            val raw = m.group(1)?.trim() ?: continue
            // Keep content inside parentheses but remove the parens.
            // "(עידו)" → "עידו" so the matching algorithm can use the nickname.
            val name = raw
                .replace(Regex("""\(([^)]*)\)"""), " $1 ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (name.isNotBlank()) {
                Log.d(TAG, "Name: '$name' (raw: '$raw')")
                return name
            }
        }
        return null
    }

    // Extract Latin words from ML Kit's spatial bounding-box hint (blocks above the amount).
    // Require uppercase-first + at least 2 lowercase (≥3 chars total) to avoid OCR artifacts
    // where Hebrew letters are misread as short Latin sequences (e.g. "ל"→"l", "ט"→"T" → "Ty").
    private fun extractLatinFromHint(hint: String?): String? {
        if (hint.isNullOrBlank()) return null
        val words = Regex("""[A-Z][a-z]{2,}""").findAll(hint)
            .map { it.value }
            .toList()
        return if (words.isNotEmpty()) words.joinToString(" ").also {
            Log.d(TAG, "Latin from bbox hint: '$it'")
        } else null
    }

    // ── Amount ────────────────────────────────────────────────────────────────

    private fun extractAmount(lines: List<String>): Double? {
        lines.forEachIndexed { i, line ->
            val nums = buildList { val m = anyNumber.matcher(line); while (m.find()) add(m.group(1)) }
            if (nums.isNotEmpty()) Log.d(TAG, "  Amount candidates line[$i]: $nums | '$line'")
        }
        for ((i, line) in lines.withIndex()) {
            shekelReversed.matcher(line).let { m ->
                while (m.find()) {
                    val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (v != null && v in 1.0..99_999.0) { Log.d(TAG, "Amount [₪N] line $i: $v"); return v }
                }
            }
            shekelNormal.matcher(line).let { m ->
                while (m.find()) {
                    val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (v != null && v in 1.0..99_999.0) { Log.d(TAG, "Amount [N₪] line $i: $v"); return v }
                }
            }
        }
        return null
    }

    // ── Timestamp ─────────────────────────────────────────────────────────────

    private fun extractTimestamp(lines: List<String>): Long {
        // Scan ALL lines and prefer a valid-year date over a garbled one.
        // Tesseract sometimes adds/swaps digits in the year (e.g. "2025" → "20785"),
        // while ML Kit reads the date row accurately. Combining both and preferring the
        // valid-year candidate fixes this.
        var dateRaw:   String? = null
        var dateYear:  Int?    = null
        var time:      String? = null

        for (line in lines) {
            val alreadyValid = dateYear != null && dateYear in 2020..2035
            if (!alreadyValid) {
                val m = datePattern.matcher(line)
                if (m.find()) {
                    val candidate = m.group(1)!!
                    val yearStr = candidate.substringAfterLast('/').substringAfterLast('.')
                    val year = yearStr.toIntOrNull()
                    if (year != null) {
                        // Prefer valid-year candidate; keep first candidate if none are valid
                        if (dateRaw == null || year in 2020..2035) {
                            dateRaw  = candidate
                            dateYear = year
                            Log.d(TAG, "Date candidate: '$candidate' (year=$year, valid=${year in 2020..2035})")
                        }
                    }
                }
            }
            if (time == null) {
                val m = timePattern.matcher(line)
                if (m.find()) {
                    val c = m.group(1)
                    val hour = c?.substringBefore(":")?.toIntOrNull() ?: 99
                    if (hour in 0..23) { time = c; Log.d(TAG, "Time: '$time'") }
                }
            }
        }

        if (dateRaw == null) {
            Log.w(TAG, "Date not found — using now")
            return System.currentTimeMillis()
        }
        if (dateYear != null && dateYear !in 2020..2035) {
            Log.w(TAG, "Year '$dateYear' out of expected range — using now")
            return System.currentTimeMillis()
        }

        val resolvedTime = time ?: run {
            Log.w(TAG, "Time not found for '$dateRaw' — defaulting to 00:00")
            "00:00"
        }

        val dateNorm = dateRaw.replace('/', '.').replace('-', '.')
        val yearPart = dateNorm.substringAfterLast('.')
        return try {
            val fmt = if (yearPart.length == 4) dateTimeFormatLong else dateTimeFormatShort
            val parsed = fmt.parse("$dateNorm $resolvedTime")
            if (parsed != null) {
                Log.d(TAG, "Parsed date: '$dateNorm $resolvedTime' → ${parsed.time}")
                parsed.time
            } else {
                Log.w(TAG, "Date parse returned null for '$dateNorm $resolvedTime'")
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Date parse error '$dateNorm $resolvedTime': ${e.message}")
            System.currentTimeMillis()
        }
    }
}
