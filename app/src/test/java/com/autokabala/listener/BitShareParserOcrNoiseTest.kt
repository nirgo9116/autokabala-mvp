package com.autokabala.listener

import org.junit.Assert.*
import org.junit.Test

/**
 * OCR noise tests for BitShareParser.
 *
 * Tests are divided into:
 *  - PASSES TODAY  — regression tests; must never break
 *  - FAILS TODAY   — document known gaps; fix one by one
 *
 * Each failing test describes WHAT is broken and WHY.
 */
class BitShareParserOcrNoiseTest {

    // ═══════════════════════════════════════════════════════════════
    //  PASSES TODAY — do not break these
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `PASS - clean input, two-word Hebrew name`() {
        val r = BitShareParser.parse("נשלחו לך מ דוד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)
    }

    @Test
    fun `PASS - extra spaces between name words are collapsed`() {
        // mixedWordPattern collects tokens then joins with single space → handles this
        val r = BitShareParser.parse("נשלחו לך מ דוד   לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)
    }

    @Test
    fun `PASS - no space between מ and Hebrew name`() {
        // pattern has \s* after מ so 0 spaces is fine
        val r = BitShareParser.parse("נשלחו לך מדוד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)
    }

    @Test
    fun `PASS - Latin name directly attached to מ`() {
        val r = BitShareParser.parse("נשלחו לך מMeital Tanni\n100 ₪\n15.03.26")
        assertNotNull(r)
        assertTrue(r!!.senderName.contains("Meital"))
    }

    @Test
    fun `PASS - single stray OCR char prefix before Hebrew name`() {
        // "מm מיכאל" — the lone 'm' before the name is already stripped
        val r = BitShareParser.parse("נשלחו לך מm מיכאל כהן\n100 ₪\n15.03.26")
        assertEquals("מיכאל כהן", r?.senderName)
    }

    // ═══════════════════════════════════════════════════════════════
    //  FAILS TODAY — known gaps, each with a root-cause comment
    // ═══════════════════════════════════════════════════════════════

    // ── Gap 1: extra space INSIDE the trigger phrase ─────────────
    // The patterns have literal single spaces ("נשלחו לך מ").
    // If OCR inserts an extra space between trigger words the pattern fails.

    @Test
    fun `FAIL - double space inside trigger phrase`() {
        // "נשלחו  לך מ" — extra space after נשלחו breaks the literal pattern
        // Fix: normalise spaces before parsing (see OcrNormalizer proposal)
        val r = BitShareParser.parse("נשלחו  לך מ דוד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)   // currently null
    }

    @Test
    fun `FAIL - extra space between לך and מ`() {
        val r = BitShareParser.parse("נשלחו לך  מ דוד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)   // currently null
    }

    @Test
    fun `FAIL - double space in ביקשת trigger`() {
        val r = BitShareParser.parse("ביקשת  מ רחל כהן\n75 ₪\n15.03.26")
        assertEquals("רחל כהן", r?.senderName)   // currently null
    }

    // ── Gap 2: broken trigger word (OCR splits individual letters) ──
    // OCR sometimes inserts spaces between every letter: "נ ש ל ח ו" instead of "נשלחו".
    // The literal pattern never matches → returns null.

    @Test
    fun `FAIL - trigger word broken into individual letters`() {
        // "נ ש ל ח ו לך מ דוד לוי" — every letter of נשלחו is separated
        // Fix: reconnect known trigger words before parsing (see OcrNormalizer proposal)
        val r = BitShareParser.parse("נ ש ל ח ו לך מ דוד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)   // currently null
    }

    @Test
    fun `FAIL - partial break in trigger word`() {
        // "נשל חו לך מ" — נשלחו split into two fragments
        val r = BitShareParser.parse("נשל חו לך מ דוד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)   // currently null
    }

    @Test
    fun `FAIL - ביקשת broken into letters`() {
        val r = BitShareParser.parse("ב י ק ש ת מ רחל כהן\n75 ₪\n15.03.26")
        assertEquals("רחל כהן", r?.senderName)   // currently null
    }

    // ── Gap 3: broken name word (OCR splits a name's letters) ──────
    // mixedWordPattern requires {2,} chars per token.
    // A single-char fragment like "ד" from "ד וד" is silently dropped.

    @Test
    fun `FAIL - first letter of name detached`() {
        // "ד וד לוי" — first letter of "דוד" split off
        // Fix: merge consecutive single-char Hebrew fragments (see collectNameWords proposal)
        val r = BitShareParser.parse("נשלחו לך מ ד וד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)   // currently "וד לוי"
    }

    @Test
    fun `PASS - every letter of name detached - returns something not null`() {
        // "ד ו ד ל ו י" — every letter is separate. Fragment merging produces "דודלוי"
        // (word boundary lost — unavoidable without knowing the original name in advance).
        // Before this fix the result was null; now at least a non-blank name is returned.
        val r = BitShareParser.parse("נשלחו לך מ ד ו ד ל ו י\n100 ₪\n15.03.26")
        assertNotNull(r)
        assertFalse(r!!.senderName.isBlank())
    }

    @Test
    fun `FAIL - last letter of name detached`() {
        // "דוד לוי" — last letter of last word split off; "י" is dropped
        val r = BitShareParser.parse("נשלחו לך מ דוד לוי\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r?.senderName)   // PASSES — but below variant fails:
        val r2 = BitShareParser.parse("נשלחו לך מ דוד לו י\n100 ₪\n15.03.26")
        assertEquals("דוד לוי", r2?.senderName)  // currently "דוד לו"
    }

    // ── Gap 4: extra spaces in Latin name (latinText fallback) ─────
    // tryExtractLatinName uses ^([A-Za-z][a-z]+...) which stops at the
    // first double-space because the pattern between words is "[ '\-]" (single space).

    @Test
    fun `FAIL - double space within Latin name`() {
        // ML Kit sometimes inserts extra spaces: "David  Levy"
        // Fix: normalise spaces in latinText before passing to parser
        val hebrewText = "נשלחו לך מ גבלגג\n100 ₪\n15.03.26"
        val latinText  = "David  Levy\n100 ₪\n15.03.26"
        val r = BitShareParser.parse(hebrewText, latinText)
        assertEquals("David Levy", r?.senderName)   // currently "David" only
    }

    // ── Gap 5: reversed word order ──────────────────────────────────
    // When OCR renders RTL text as LTR the entire word order is reversed.
    // "יול דוד מ ךל וחלשנ" = "נשלחו לך מ דוד לוי" reversed.
    // No current mechanism detects or reverses this — always returns null.
    // NOTE: this is the hardest gap to fix. Requires line-reversal detection.

    @Test
    fun `FAIL - reversed word order (RTL read as LTR)`() {
        // Reversed: "יול דוד מ ךל וחלשנ" = "נשלחו לך מ דוד לוי"
        val r = BitShareParser.parse("יול דוד מ ךל וחלשנ\n100 ₪\n15.03.26")
        assertNotNull(r)    // currently null — no trigger phrase recognized
        assertEquals("דוד לוי", r?.senderName)
    }
}
