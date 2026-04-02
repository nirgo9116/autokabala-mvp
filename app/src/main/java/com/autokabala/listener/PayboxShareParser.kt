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
    private val datePattern         = Pattern.compile("""(\d{1,2}[./]\d{1,2}[./]\d{2,4})(?!\d)""")
    private val timePattern         = Pattern.compile("""(\d{1,2}:\d{2})""")
    private val dateTimeFormatShort = SimpleDateFormat("dd.MM.yy HH:mm",   Locale.getDefault())
    private val dateTimeFormatLong  = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────────────────

    fun isPaybox(text: String): Boolean {
        // Bit-specific triggers take absolute priority — both apps use "מספר אישור"
        // so we cannot rely on it alone. These phrases only appear in Bit.
        if (text.contains("נשלחו לך מ") || text.contains("ביקשת מ") ||
            text.contains("שלח לך")     || text.contains("שלחה לך")) return false
        return text.contains("הועבר אל") ||
               text.contains("מספר אישור") ||
               text.contains("PayBox", ignoreCase = true) ||
               text.contains("paybox", ignoreCase = true)
    }

    fun parse(hebrewText: String, latinText: String = hebrewText, mlKitAmount: Double? = null, mlKitNameHint: String? = null): PaymentData? {
        val hebrewLines = hebrewText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val latinLines  = latinText.split("\n").map  { it.trim() }.filter { it.isNotBlank() }

        Log.d(TAG, "=== PayboxParser START ===")
        Log.d(TAG, "--- Tesseract lines ---")
        hebrewLines.forEachIndexed { i, line -> Log.d(TAG, "  T[$i]: '$line'") }
        if (latinText != hebrewText) {
            Log.d(TAG, "--- ML Kit lines ---")
            latinLines.forEachIndexed { i, line -> Log.d(TAG, "  M[$i]: '$line'") }
        } else {
            Log.d(TAG, "  (ML Kit text identical to Tesseract — single engine)")
        }
        Log.d(TAG, "  mlKitAmount (bbox spatial): $mlKitAmount")
        if (mlKitNameHint != null) Log.d(TAG, "  mlKitNameHint (bbox above amount): '$mlKitNameHint'")

        val nameCandidates = buildList {
            extractSenderName(hebrewLines, latinLines, mlKitNameHint)?.let { add(NameCandidate(it, "regex")) }
            mlKitNameHint?.let { add(NameCandidate(it, "mlkit_hint")) }
        }
        val senderName = pickBestName(nameCandidates) ?: run {
            Log.w(TAG, "Cannot extract sender name")
            return null
        }
        Log.d(TAG, "Name candidates: ${nameCandidates.map { "${it.source}='${it.name}'(${scoreNameCandidate(it.name)})" }}")
        Log.d(TAG, "Best name: '$senderName'")

        val amount = if (mlKitAmount != null) {
            Log.d(TAG, "Amount → ML Kit bbox won: $mlKitAmount")
            mlKitAmount
        } else {
            Log.d(TAG, "--- Amount extraction (no bbox result) ---")
            extractAmount(hebrewLines, "Tesseract")
                ?: if (latinLines !== hebrewLines && latinText != hebrewText
                        && latinLines.any { it.contains("₪") }) {
                       // Only scan ML Kit text lines if any of them actually contain ₪.
                       // For Paybox, ML Kit returns only bottom-section Latin text (hex, date,
                       // PayBox logo) which never contains ₪ — skip to avoid wasted work.
                       extractAmount(latinLines, "MLKit").also {
                           if (it != null) Log.d(TAG, "Amount → ML Kit text fallback: $it")
                           else Log.w(TAG, "Amount → ML Kit text also failed")
                       }
                   } else {
                       Log.d(TAG, "ML Kit text lines contain no ₪ — skipping text scan")
                       null
                   }
        } ?: run {
            Log.w(TAG, "Amount → ALL engines failed — creating with amount=0 for manual input")
            0.0
        }

        // ML Kit first for timestamp — more accurate on digits; Tesseract as fallback only
        val timestamp = extractTimestamp(latinLines, fallbackLines = hebrewLines)

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
            // Use ML Kit hint when the Tesseract output contains no Latin letters —
            // this tolerates OCR garbage chars like \ / | alongside Hebrew letters.
            val hasNoLatinLetters = tessName.none { it in 'A'..'Z' || it in 'a'..'z' }
            if (hasNoLatinLetters) {
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
        val words = Regex("""[A-Za-z][a-z]{2,}""").findAll(hint)
            .map { it.value }
            .toList()
        return if (words.isNotEmpty()) words.joinToString(" ").also {
            Log.d(TAG, "Latin from bbox hint: '$it'")
        } else null
    }

    // ── Amount ────────────────────────────────────────────────────────────────

    private fun extractAmount(lines: List<String>, engine: String = "?"): Double? {
        Log.d(TAG, "  [$engine] scanning ${lines.size} lines for amount:")
        lines.forEachIndexed { i, line ->
            val nums = buildList { val m = anyNumber.matcher(line); while (m.find()) add(m.group(1)) }
            if (nums.isNotEmpty()) Log.d(TAG, "    [$engine] L$i candidates=$nums | '$line'")
            else Log.d(TAG, "    [$engine] L$i no-numbers | '$line'")
        }
        for ((i, line) in lines.withIndex()) {
            // Skip approval-number lines ("מספר אישור" / OCR variant "מספר אישזר").
            // The confirmation number on this line looks like an amount but isn't.
            if (line.trimStart().startsWith("מספר")) {
                Log.d(TAG, "    [$engine] L$i SKIPPED (approval-number line)")
                continue
            }
            shekelReversed.matcher(line).let { m ->
                while (m.find()) {
                    val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (v != null && v in 1.0..99_999.0) {
                        Log.d(TAG, "  [$engine] Amount [₪N] L$i → $v")
                        return v
                    }
                }
            }
            shekelNormal.matcher(line).let { m ->
                while (m.find()) {
                    val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (v != null && v in 1.0..99_999.0) {
                        Log.d(TAG, "  [$engine] Amount [N₪] L$i → $v")
                        return v
                    }
                }
            }
        }
        Log.d(TAG, "  [$engine] no amount found")
        return null
    }

    // ── Timestamp ─────────────────────────────────────────────────────────────

    private fun extractTimestamp(primaryLines: List<String>, fallbackLines: List<String> = emptyList()): Long {
        // Scan ML Kit lines first — accurate on digits/dates.
        // Only fall back to Tesseract lines if ML Kit yields no valid date.
        var dateRaw:  String? = null
        var dateYear: Int?    = null
        var time:     String? = null

        fun scanLines(lines: List<String>) {
            for (line in lines) {
                if (dateRaw == null) {
                    val m = datePattern.matcher(line)
                    if (m.find()) {
                        val candidate = m.group(1)!!
                        val yearStr = candidate.substringAfterLast('/').substringAfterLast('.')
                        val year = yearStr.toIntOrNull()
                        if (year != null) {
                            dateRaw  = candidate
                            dateYear = year
                            Log.d(TAG, "Date candidate: '$candidate' (year=$year)")
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
        }

        scanLines(primaryLines)
        if (dateRaw == null) {
            Log.d(TAG, "Date not found in ML Kit lines — trying Tesseract fallback")
            scanLines(fallbackLines)
        }

        if (dateRaw == null) {
            Log.w(TAG, "Date not found — using now")
            return System.currentTimeMillis()
        }
        if (dateYear != null && dateYear !in 2020..2035) {
            Log.w(TAG, "Year '$dateYear' unusual — using as-is")
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
