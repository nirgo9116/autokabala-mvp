package com.autokabala.listener

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object BitShareParser {

    private const val TAG = "BitShareParser"

    // Names: 1–3 Hebrew OR Latin words (supports mixed-language names like "Reut Lazar").
    // (?:[a-z]{1,2}\s+)? after מ handles OCR artifact where Tesseract inserts a stray lowercase
    // Latin char (e.g. "נשלחו לך מm מיכאל"). Lowercase-only so real Latin names ("Maya Wayn")
    // are not accidentally consumed.
    private val nameReceivedPattern = Pattern.compile(
        """נשלחו לך מ\s*(?:[a-z]{1,2}\s+)?([\u05D0-\u05EAA-Za-z]{2,}(?:\s+[\u05D0-\u05EAA-Za-z']{2,}){0,2})"""
    )

    // "ביקשת מ [name]" — payment request you sent; captured when they pay you back.
    private val nameRequestedPattern = Pattern.compile(
        """ביקשת מ\s*([\u05D0-\u05EAA-Za-z]{2,}(?:\s+[\u05D0-\u05EAA-Za-z']{2,}){0,2})"""
    )

    // Matches any Hebrew OR Latin word of 2+ characters (used to extract name words from a line)
    private val mixedWordPattern    = Pattern.compile("""[\u05D0-\u05EAA-Za-z']{2,}""")
    // Keep Hebrew-only pattern for amount-line exclusion logic
    private val hebrewWordPattern   = Pattern.compile("""[א-ת]{2,15}""")
    private val nameSentYouPattern  = Pattern.compile(
        """([\u05D0-\u05EAA-Za-z]{2,}(?:\s+[\u05D0-\u05EAA-Za-z']{2,}){0,2})\s+שלח[ה]?\s+לך"""
    )

    // Words that indicate sender-added notes, not part of the real name.
    // Trim the name at the first word found in this set.
    private val nameStopWords = setOf("השכנה", "כיתה", "מהקומה", "סטטוס", "לאן")

    private fun trimNameAtStopWords(name: String): String {
        val words = name.split(" ")
        val cutIdx = words.indexOfFirst { it in nameStopWords }
        return if (cutIdx > 0) words.take(cutIdx).joinToString(" ") else name
    }

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
    private val datePattern         = Pattern.compile("""(\d{1,2}[./]\d{1,2}[./]\d{2,4})(?!\d)""")
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
    fun parse(hebrewText: String, latinText: String = hebrewText, mlKitAmount: Double? = null): PaymentData? {
        Log.d(TAG, "=== BitShareParser START ===")
        Log.d(TAG, "Tesseract (Hebrew):\n$hebrewText")
        if (latinText !== hebrewText) Log.d(TAG, "ML Kit (Latin):\n$latinText")

        val normHebrew = OcrNormalizer.normalize(hebrewText)
        val normLatin  = if (latinText === hebrewText) normHebrew else OcrNormalizer.normalize(latinText)

        val hebrewLines = normHebrew.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val latinLines  = normLatin.split("\n").map  { it.trim() }.filter { it.isNotBlank() }

        Log.d(TAG, "Hebrew lines: ${hebrewLines.joinToString(" | ")}")

        val senderName = extractSenderName(hebrewLines, latinLines) ?: run {
            Log.w(TAG, "Cannot extract sender name"); return null
        }

        // Amount: prefer ML Kit bounding-box result (most reliable — picks the largest
        // numeric text block in the image, which is always the payment amount in Bit).
        // Fall back to regex-based extraction from OCR text if bounding-box gave nothing.
        val amount = if (mlKitAmount != null) {
            Log.d(TAG, "Amount from ML Kit bounding-box: $mlKitAmount")
            mlKitAmount
        } else if (latinLines !== hebrewLines) {
            extractAmountFromLatinOcr(latinLines).also { v ->
                if (v != null) Log.d(TAG, "Amount from ML Kit text: $v")
                else Log.w(TAG, "ML Kit text amount failed — falling back to Tesseract")
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

    // Try Tesseract lines first; fall back to ML Kit lines for Latin names.
    private fun extractSenderName(lines: List<String>, fallbackLines: List<String> = emptyList()): String? {
        val tessName = tryExtractNameFromLines(lines)

        if (tessName != null) {
            // If the Tesseract name is composed entirely of Hebrew characters, Tesseract may have
            // garbled a Latin name (e.g. "Maya Wayn" → "חעץהולץ העבוא").
            // When Bit context is confirmed and ML Kit has a valid Latin name, prefer it.
            val isAllHebrew = tessName.all {
                it in '\u05D0'..'\u05EA' || it in '\u05F0'..'\u05F4' || it == ' '
            }
            val hasBitContext = lines.any { it.startsWith("נשלחו לך מ") || it.startsWith("ביקשת מ") }
            if (isAllHebrew && hasBitContext && fallbackLines !== lines) {
                tryExtractLatinName(fallbackLines)?.let { latinName ->
                    // Prefer ML Kit Latin only if it has ProperCase (at least one uppercase letter).
                    // All-lowercase results like "inzu" are OCR noise, not real names.
                    val hasProperCase = latinName.any { it.isUpperCase() }
                    if (hasProperCase && (latinName.contains(' ') || latinName.length >= 4)) {
                        Log.d(TAG, "Preferring ML Kit Latin '$latinName' over garbled Tesseract Hebrew '$tessName'")
                        return latinName
                    }
                }
            }
            return tessName
        }

        // Tesseract found nothing — try ML Kit patterns
        if (fallbackLines !== lines) tryExtractNameFromLines(fallbackLines)?.let { return it }

        // Last resort: Tesseract found Bit context but garbled the Latin name →
        // extract leading Latin words from ML Kit's first line.
        val hasBitContext = lines.any { it.startsWith("נשלחו לך מ") || it.startsWith("ביקשת מ") }
        if (hasBitContext) tryExtractLatinName(fallbackLines)?.let { return it }
        return null
    }

    // Extracts "FirstName LastName" from ML Kit output when the name is Latin.
    // Accepts both ProperCase ("Maya Waynn") and lowercase-first ("michal Shpiegel").
    // Also handles hyphenated names ("Anne-Marie") and apostrophe names ("O'Connor" prefix).
    // Stops at the first token that isn't pure letters/hyphen/apostrophe (digits, Hebrew, etc.).
    private fun tryExtractLatinName(lines: List<String>): String? {
        // Search each line without ^ anchor — ML Kit often prefixes the name with garbled chars
        // (e.g. "NNVNp Meital Tannip u"). Requires uppercase+2lower OR 3+lowercase so that
        // short noise tokens like "Np" or "p" are skipped. Handles lowercase-first names too.
        val namePattern = Pattern.compile(
            """(?<![A-Za-z])((?:[A-Z][a-z]{2,}|[a-z]{3,})(?:[ '\-](?:[A-Z][a-z]{2,}|[a-z]{3,})*)*)"""
        )
        for (line in lines) {
            val m = namePattern.matcher(line)
            if (!m.find()) continue
            var name = m.group(1)?.trim() ?: continue

            // Normalize ML Kit OCR artifacts before any further processing:
            // dotless-ı (U+0131) → regular i  (common in Bit screenshot fonts)
            // Leading "nn" per word → "m"  (ML Kit often splits lowercase "m" into "nn")
            name = name.replace('\u0131', 'i')
            name = name.split(" ").joinToString(" ") { word ->
                if (word.length >= 3 && word.startsWith("nn")) "m" + word.drop(2) else word
            }

            // Strip trailing short all-lowercase words — OCR garbage from Hebrew content
            // (e.g. "Reut Lazarn inu" → "Reut Lazarn"; "inu" is 3 chars, all-lowercase).
            // Legitimate names: ProperCase ("Cohen", "Dan") or long enough ("shpiegel" = 8 chars).
            // We never remove the first word (size > 1 guard).
            val words = name.split(" ").toMutableList()
            while (words.size > 1) {
                val last = words.last()
                if (last.length <= 4 && last.all { it.isLowerCase() }) words.removeAt(words.lastIndex)
                else break
            }
            name = words.joinToString(" ")

            // Collapse doubled trailing letter per word — ML Kit OCR artifact (e.g. "Waynn" → "Wayn").
            name = name.split(" ").joinToString(" ") { word ->
                word.replace(Regex("([A-Za-z])\\1$")) { it.groupValues[1] }
            }

            if (name.isNotBlank()) {
                Log.d(TAG, "Name [latin-fallback]: '$name'")
                return name
            }
        }
        return null
    }

    // Collects name words, merging consecutive short Hebrew fragments.
    // Fixes OCR splitting like "ד וד לוי" → ["דוד","לוי"] instead of dropping "ד".
    private fun collectNameWords(text: String): List<String> {
        val rawTokens = text.trim().split(Regex("\\s+"))
            .mapNotNull { tok ->
                when {
                    tok.isBlank() -> null
                    // Parenthetical pure-Hebrew word like "(יובל)" — strip parens and keep content
                    tok.matches(Regex("\\([\u05D0-\u05EA]+\\)")) -> tok.trim('(', ')')
                    // Normal allowed chars
                    tok.all { c -> c in '\u05D0'..'\u05EA' || c in 'A'..'Z' || c in 'a'..'z' || c == '\'' } -> {
                        // Filter Hebrew words where all letters are identical — OCR artifact (e.g. "חח", "חחח")
                        if (tok.all { it in '\u05D0'..'\u05EA' } && tok.toSet().size == 1) null else tok
                    }
                    else -> null
                }
            }
        val result = mutableListOf<String>()
        var i = 0
        while (i < rawTokens.size) {
            val token = rawTokens[i]
            val isHebrewFragment = token.length <= 2 && token.all { it in '\u05D0'..'\u05EA' }
            if (isHebrewFragment) {
                val merged = StringBuilder(token)
                var j = i + 1
                while (j < rawTokens.size) {
                    val next = rawTokens[j]
                    if (next.all { it in '\u05D0'..'\u05EA' } && next.length <= 2) {
                        merged.append(next); j++
                    } else break
                }
                val word = merged.toString()
                if (word.length >= 2) result.add(word)
                i = j
            } else {
                result.add(token)
                i++
            }
        }
        return result
    }

    private fun tryExtractNameFromLines(lines: List<String>): String? {
        for ((idx, line) in lines.withIndex()) {
            // "נשלחו לך מ [name]" — payment received
            val m = nameReceivedPattern.matcher(line)
            if (m.find()) {
                val nameSection = line.substring(m.start(1))
                val words = collectNameWords(nameSection).toMutableList()
                // Always check the next line — Bit wraps long display names onto a second line
                // (e.g. "יריב ה' באייר 126 ק3" / "טגנסקי", "שני החשמונאים 8 קג" / "פנחס").
                // We include it unconditionally as long as it contains no digits, ₪, or date.
                val nextLine = lines.getOrNull(idx + 1)
                if (nextLine != null &&
                    nextLine.isNotBlank() &&
                    !nextLine.any { it.isDigit() } &&
                    !nextLine.contains("₪") &&
                    !datePattern.matcher(nextLine).find()
                ) {
                    words.addAll(collectNameWords(nextLine))
                }
                if (words.isEmpty()) continue
                // Remove leading OCR artifact: a ≤2-char word before a longer name word
                // (e.g. "וח מיכאל יובל" → "מיכאל יובל", caused by Tesseract misreading Latin 'm')
                if (words.size >= 2 && words[0].length <= 2 && words[1].length >= 3) words.removeAt(0)
                val name = trimNameAtStopWords(words.joinToString(" "))
                Log.d(TAG, "Name [received]: '$name'")
                return name
            }

            // "ביקשת מ [name]" — payment request you sent (captured when they pay)
            val m2 = nameRequestedPattern.matcher(line)
            if (m2.find()) {
                val name = m2.group(1)?.trim()?.let { trimNameAtStopWords(it) }
                if (!name.isNullOrBlank()) {
                    Log.d(TAG, "Name [requested]: '$name'")
                    return name
                }
            }

            // "[name] שלח/ה לך"
            val m3 = nameSentYouPattern.matcher(line)
            if (m3.find()) {
                val name = m3.group(1)?.trim()
                Log.d(TAG, "Name [sentYou]: '$name'")
                return name
            }
        }
        return null
    }

    // ── Amount ────────────────────────────────────────────────────────────────

    private fun extractAmount(lines: List<String>): Double? {
        val nameIdx = lines.indexOfFirst {
            nameReceivedPattern.matcher(it).find() ||
            nameRequestedPattern.matcher(it).find() ||
            nameSentYouPattern.matcher(it).find()
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
     *
     * Strategy A only: requires the ₪ symbol to be present in ML Kit output.
     * Strategy B (standalone-number scan) was removed because ML Kit cannot read Hebrew,
     * so it cannot locate the amount line by proximity to the sender name — it would
     * instead pick up stray numbers from the contact address or reference codes.
     * If ₪ is absent (common with Latin-only OCR), returns null so the caller falls
     * back to Tesseract's name-proximity approach.
     */
    private fun extractAmountFromLatinOcr(lines: List<String>): Double? {
        // Log all candidate lines for debugging
        lines.forEachIndexed { i, line ->
            val nums = buildList { val m = anyNumber.matcher(line); while (m.find()) add(m.group(1)) }
            if (nums.isNotEmpty()) Log.d(TAG, "  MlKit[$i] nums=$nums | '$line'")
        }

        // Strategy A — ₪ symbol must be present
        for ((i, line) in lines.withIndex()) {
            if (skipAmountWords.any { line.contains(it) }) continue
            tryExtractMerged(line, i, "mlkit-merged")?.let { return it }
            tryExtractWithShekel(line, i, "mlkit-shekel")?.let { return it }
            tryExtractReversed(line, i, "mlkit-reversed")?.let { return it }
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

        if (dateRaw == null) {
            Log.w(TAG, "Date not found — using now")
            return System.currentTimeMillis()
        }
        val resolvedTime = time ?: run {
            Log.w(TAG, "Time not found for '$dateRaw' — defaulting to 00:00")
            "00:00"
        }

        val dateNorm = dateRaw.replace('/', '.')
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
            Log.w(TAG, "Date parse exception '$dateNorm $resolvedTime': ${e.message}")
            System.currentTimeMillis()
        }
    }
}
