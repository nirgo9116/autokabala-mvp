package com.autokabala.listener

/**
 * Pre-processing step applied to raw OCR text before it reaches any parser.
 *
 * Fixes two classes of OCR noise that break regex matching:
 *
 *  1. EXTRA SPACES — multiple spaces/tabs within a line are collapsed to one.
 *     Handles: "נשלחו  לך מ" → "נשלחו לך מ"
 *              "David   Levy"  → "David Levy"
 *
 *  2. BROKEN TRIGGER WORDS — individual letters of known Hebrew trigger
 *     phrases are reconnected when OCR splits them.
 *     Handles: "נ ש ל ח ו לך מ" → "נשלחו לך מ"
 *              "ב י ק ש ת מ"    → "ביקשת מ"
 *
 * What this does NOT fix:
 *  - Reversed word order ("יול דוד מ ךל וחלשנ") — structural problem, separate concern.
 *  - Broken name letters ("ד וד") — handled by [collectHebrewNameFragments] instead.
 */
object OcrNormalizer {

    fun normalize(text: String): String =
        text.lines()
            .map { collapseSpaces(it) }
            .joinToString("\n")
            .let { reconnectTriggers(it) }

    // ── Step 1: collapse multiple spaces/tabs on each line ────────────────────

    private fun collapseSpaces(line: String): String =
        line.replace(Regex("[ \t]+"), " ").trim()

    // ── Step 2: reconnect broken Hebrew trigger words ─────────────────────────
    // Each regex allows an optional space between every letter of the trigger word.
    // Only the specific words we need for Bit; broad letter-reconnection would corrupt
    // legitimate text (e.g. merging a preposition with the following word).

    private val brokenNshlachu = Regex("""נ\s+ש\s*ל\s*ח\s*ו""")
    private val brokenLcha     = Regex("""(?<![א-ת])ל\s+ך(?![א-ת])""")   // standalone לך only
    private val brokenBikasht  = Regex("""ב\s+י\s*ק\s*ש\s*ת""")

    private fun reconnectTriggers(text: String): String =
        text
            .replace(brokenNshlachu, "נשלחו")
            .replace(brokenLcha,     "לך")
            .replace(brokenBikasht,  "ביקשת")
}
