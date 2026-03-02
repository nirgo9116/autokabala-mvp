package com.autokabala.listener

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object BitShareParser {

    private const val TAG = "BitShareParser"

    // Names: 1–3 Hebrew words (supports business names like "גלית אלי טיפול").
    // \s* after מ handles OCR that may insert a space between the preposition and the name.
    private val nameReceivedPattern = Pattern.compile("""נשלחו לך מ\s*([א-ת]{2,15}(?:\s+[א-ת]{2,15}){0,2})""")
    private val nameSentYouPattern   = Pattern.compile("""([א-ת]{2,15}(?:\s+[א-ת]{2,15}){0,2})\s+שלח[ה]?\s+לך""")

    // Amount patterns ─────────────────────────────────────────────────────────
    // Merged: collects all digit/comma/space chars before ₪ — handles OCR splitting "59₪" → "5 9₪".
    // Applied FIRST in strategy 1 (lines near name) to catch the merged number before normal pattern
    // grabs just the last digit group.  Applied only near the name to limit false positives.
    private val shekelMergedDigits = Pattern.compile("""(\d[\d, ]{0,9})\s*[;, ]*\s*₪""")
    // Normal: "105 ₪" or "105₪" — with optional garbage chars between number and ₪
    private val shekelNormal   = Pattern.compile("""([\d,]+\.?\d*)\s*[;, ]*\s*₪""")
    // Reversed RTL: "₪ 5" or "₪5"
    private val shekelReversed = Pattern.compile("""₪\s*([\d,]+\.?\d*)""")
    // First number at start of line (e.g. "105,:" → 105)
    private val lineStartNum   = Pattern.compile("""^([\d,]+\.?\d*)""")
    // Any number anywhere
    private val anyNumber      = Pattern.compile("""([\d,]+\.?\d*)""")

    // Lines that contain these words are NOT amount lines — skip them
    private val skipAmountWords = listOf("תאריך", "שעה", "מספר", "סטטוס", "יתרה", "לאן", "הצגה")

    // Date / time ─────────────────────────────────────────────────────────────
    // Supports dot (01.03.26) and slash (01/03/26), 1–2 digit day/month
    private val datePattern         = Pattern.compile("""(\d{1,2}[./]\d{1,2}[./]\d{2,4})""")
    private val timePattern         = Pattern.compile("""(\d{1,2}:\d{2})""")
    private val dateTimeFormatShort = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    private val dateTimeFormatLong  = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse a Bit payment screenshot using two OCR sources:
     *  - [hebrewText]  : Tesseract output (accurate Hebrew → used for sender name)
     *  - [latinText]   : ML Kit Latin output (accurate LTR numbers → used for amount & date)
     *
     * When only one engine is available, pass the same text for both parameters.
     */
    fun parse(hebrewText: String, latinText: String = hebrewText): PaymentData? {
        Log.d(TAG, "=== BitShareParser START ===")
        Log.d(TAG, "Tesseract (Hebrew):\n$hebrewText")
        if (latinText !== hebrewText) Log.d(TAG, "ML Kit (Latin):\n$latinText")

        val hebrewLines = hebrewText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val latinLines  = latinText.split("\n").map  { it.trim() }.filter { it.isNotBlank() }

        Log.d(TAG, "Hebrew lines: ${hebrewLines.joinToString(" | ")}")

        val senderName = extractSenderName(hebrewLines) ?: run {
            Log.w(TAG, "Cannot extract sender name"); return null
        }

        // Amount: prefer ML Kit (LTR, no digit-flip), fall back to Tesseract
        val amount = if (latinLines !== hebrewLines) {
            extractAmountFromLatinOcr(latinLines).also { v ->
                if (v != null) Log.d(TAG, "Amount from ML Kit: $v")
                else Log.w(TAG, "ML Kit amount extraction failed — falling back to Tesseract")
            } ?: extractAmount(hebrewLines)
        } else {
            extractAmount(hebrewLines)
        } ?: run {
            Log.w(TAG, "Cannot extract amount"); return null
        }

        // Timestamp: combine both sources for best coverage
        val timestamp = extractTimestamp(latinLines + hebrewLines)

        Log.d(TAG, "Result → name='$senderName', amount=$amount, ts=$timestamp")
        Log.d(TAG, "=== BitShareParser END ===")

        return PaymentData(
            source = "bit_share",
            senderName = senderName,
            amount = amount,
            isConfirmed = true,
            timestamp = timestamp
        )
    }

    // ── Name ──────────────────────────────────────────────────────────────────

    private fun extractSenderName(lines: List<String>): String? {
        for (line in lines) {
            val m = nameReceivedPattern.matcher(line)
            if (m.find()) {
                val name = m.group(1)?.trim()
                    ?.replace(Regex("""[\d₪.,]+$"""), "")?.trim()
                    ?.takeIf { it.length >= 2 }
                Log.d(TAG, "Name [received]: '$name'")
                return name
            }
            val m2 = nameSentYouPattern.matcher(line)
            if (m2.find()) {
                val name = m2.group(1)?.trim()
                Log.d(TAG, "Name [sentYou]: '$name'")
                return name
            }
        }
        return null
    }

    // ── Amount ────────────────────────────────────────────────────────────────

    private fun extractAmount(lines: List<String>): Double? {
        val nameIdx = lines.indexOfFirst {
            nameReceivedPattern.matcher(it).find() || nameSentYouPattern.matcher(it).find()
        }

        // Log all numbers per line for debugging
        lines.forEachIndexed { i, line ->
            val nums = buildList { val m = anyNumber.matcher(line); while (m.find()) add(m.group(1)) }
            if (nums.isNotEmpty()) Log.d(TAG, "  Line[$i] nums=$nums | '$line'")
        }

        // Strategy 1 — ₪ on lines near name.
        // Try merged (strips OCR-inserted spaces inside a number) first, then normal, then reversed.
        if (nameIdx != -1) {
            for (i in nameIdx..(nameIdx + 2)) {
                if (i >= lines.size) break
                val line = lines[i]
                tryExtractMerged(line, i, "1-merged")?.let { return it }
                tryExtractWithShekel(line, i, "1-normal")?.let { return it }
                tryExtractReversed(line, i, "1-reversed")?.let { return it }
            }
        }

        // Strategy 2 — ₪ anywhere, but SKIP lines with context words (תאריך, מספר אישור, etc.)
        for ((i, line) in lines.withIndex()) {
            if (skipAmountWords.any { line.contains(it) }) continue
            tryExtractWithShekel(line, i, "2-normal")?.let { return it }
            tryExtractReversed(line, i, "2-reversed")?.let { return it }
        }

        // Strategy 3 — first number at start of lines near name, skipping context lines
        if (nameIdx != -1) {
            for (i in (nameIdx + 1)..(nameIdx + 2)) {
                if (i >= lines.size) break
                val line = lines[i].trim()
                if (skipAmountWords.any { line.contains(it) }) continue
                // Skip pure-Hebrew lines (names/descriptions, not amounts)
                if (line.all { it in '\u05D0'..'\u05EA' || it == ' ' }) continue

                val m = lineStartNum.matcher(line)
                if (m.find()) {
                    val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (v != null && v in 1.0..99_999.0) {
                        Log.d(TAG, "Amount [3-lineStart] line $i: $v  '$line'")
                        return v
                    }
                }
            }
        }

        return null
    }

    private fun tryExtractWithShekel(line: String, idx: Int, tag: String): Double? {
        val m = shekelNormal.matcher(line)
        while (m.find()) {
            val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
            if (v != null && v in 1.0..99_999.0) {
                Log.d(TAG, "Amount [$tag] line $idx: $v")
                return v
            }
        }
        return null
    }

    private fun tryExtractReversed(line: String, idx: Int, tag: String): Double? {
        val m = shekelReversed.matcher(line)
        while (m.find()) {
            val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
            if (v != null && v in 1.0..99_999.0) {
                Log.d(TAG, "Amount [$tag-rtl] line $idx: $v")
                return v
            }
        }
        return null
    }

    // ── ML Kit amount extractor ───────────────────────────────────────────────

    /**
     * Extract amount from ML Kit Latin OCR output.
     * ML Kit reads LTR so digits are never reversed (59 stays 59, not 95).
     * Zeros are preserved because modern deep-learning OCR handles bold fonts well.
     *
     * Strategy A: ₪ symbol present in output (ML Kit may or may not recognise it).
     * Strategy B: standalone number on a non-date / non-time / non-reference line.
     */
    private fun extractAmountFromLatinOcr(lines: List<String>): Double? {
        // Log all candidate lines for debugging
        lines.forEachIndexed { i, line ->
            val nums = buildList { val m = anyNumber.matcher(line); while (m.find()) add(m.group(1)) }
            if (nums.isNotEmpty()) Log.d(TAG, "  MlKit[$i] nums=$nums | '$line'")
        }

        // Strategy A — ₪ present (same patterns as Tesseract strategies 1+2)
        for ((i, line) in lines.withIndex()) {
            if (skipAmountWords.any { line.contains(it) }) continue
            tryExtractMerged(line, i, "mlkit-merged")?.let { return it }
            tryExtractWithShekel(line, i, "mlkit-shekel")?.let { return it }
            tryExtractReversed(line, i, "mlkit-reversed")?.let { return it }
        }

        // Strategy B — standalone number (no ₪ recognised by ML Kit)
        // Skip: date lines (dd.MM.yy), pure time lines (HH:mm), reference lines (contain "-")
        for ((i, line) in lines.withIndex()) {
            if (skipAmountWords.any { line.contains(it) }) continue
            if (line.contains("-")) continue                       // reference numbers
            if (datePattern.matcher(line).find()) continue         // date lines
            if (line.trim().matches(Regex("\\d{1,2}:\\d{2}"))) continue  // pure time

            val m = anyNumber.matcher(line)
            while (m.find()) {
                val v = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (v != null && v in 1.0..99_999.0) {
                    Log.d(TAG, "Amount [mlkit-standalone] line $i: $v  '$line'")
                    return v
                }
            }
        }

        return null
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    // Handles OCR that splits a number across tokens: "59₪" → "5 9₪".
    // Strips spaces (and commas) from the matched digit group before parsing.
    private fun tryExtractMerged(line: String, idx: Int, tag: String): Double? {
        val m = shekelMergedDigits.matcher(line)
        while (m.find()) {
            val raw = m.group(1) ?: continue
            val v = raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
            if (v != null && v in 1.0..99_999.0) {
                Log.d(TAG, "Amount [$tag] line $idx: $v  (raw='$raw')")
                return v
            }
        }
        return null
    }

    // ── Expired payment detection ─────────────────────────────────────────────

    // Returns true when the screenshot shows a Bit payment that has already expired.
    // Bit displays a red "עבר התוקף" badge and grays out the amount in such cases.
    fun isExpired(ocrText: String): Boolean {
        val text = ocrText.replace("\n", " ")
        return text.contains("עבר התוקף") ||
               text.contains("פג תוקף")   ||
               // OCR sometimes splits or garbles the badge — check for both words on same pass
               (text.contains("עבר") && text.contains("התוקף"))
    }

    // ── Timestamp ─────────────────────────────────────────────────────────────

    private fun extractTimestamp(lines: List<String>): Long {
        var dateRaw: String? = null
        var time: String? = null

        for (line in lines) {
            if (dateRaw == null) {
                val m = datePattern.matcher(line)
                if (m.find()) {
                    dateRaw = m.group(1)
                    Log.d(TAG, "Date candidate: '$dateRaw'")
                }
            }
            if (time == null) {
                val m = timePattern.matcher(line)
                if (m.find()) {
                    val c = m.group(1)
                    if (c != null && c.contains(":")) {
                        val hour = c.substringBefore(":").toIntOrNull() ?: 99
                        if (hour in 0..23) {
                            time = c
                            Log.d(TAG, "Time candidate: '$time'")
                        }
                    }
                }
            }
        }

        if (dateRaw == null || time == null) {
            Log.w(TAG, "Date/time not found (date='$dateRaw', time='$time') — using now")
            return System.currentTimeMillis()
        }

        val dateNorm = dateRaw.replace('/', '.')
        val yearPart = dateNorm.substringAfterLast('.')
        return try {
            val fmt = if (yearPart.length == 4) dateTimeFormatLong else dateTimeFormatShort
            val parsed = fmt.parse("$dateNorm $time")
            if (parsed != null) {
                Log.d(TAG, "Parsed date: '$dateNorm $time' → ${parsed.time}")
                parsed.time
            } else {
                Log.w(TAG, "Date parse returned null for '$dateNorm $time'")
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Date parse exception '$dateNorm $time': ${e.message}")
            System.currentTimeMillis()
        }
    }
}
