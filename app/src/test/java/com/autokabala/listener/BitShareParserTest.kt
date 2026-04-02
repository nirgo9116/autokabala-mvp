package com.autokabala.listener

import org.junit.Assert.*
import org.junit.Test

class BitShareParserTest {

    // ── Received (נשלחו לך מ) ─────────────────────────────────────────────

    @Test
    fun `received - two-word Hebrew name`() {
        val result = BitShareParser.parse("נשלחו לך מ דוד לוי\n105 ₪\n15.03.26")
        assertEquals("דוד לוי", result?.senderName)
    }

    @Test
    fun `received - three-word Hebrew name`() {
        val result = BitShareParser.parse("נשלחו לך מ יוסי כהן לוי\n50 ₪\n15.03.26")
        assertEquals("יוסי כהן לוי", result?.senderName)
    }

    @Test
    fun `received - OCR artifact single-char prefix stripped`() {
        // "מm מיכאל" — the stray 'm' is an OCR artifact and must be ignored
        val result = BitShareParser.parse("נשלחו לך מm מיכאל כהן\n105 ₪\n15.03.26")
        assertEquals("מיכאל כהן", result?.senderName)
    }

    @Test
    fun `received - Latin name via ML Kit fallback when Tesseract garbles it`() {
        // Tesseract output has garbled all-Hebrew noise; ML Kit first line has the real name
        val hebrewText = "נשלחו לך מ גבלגג\n100 ₪\n15.03.26"
        val latinText  = "David Levy\n100 ₪\n15.03.26"
        val result = BitShareParser.parse(hebrewText, latinText)
        assertEquals("David Levy", result?.senderName)
    }

    @Test
    fun `received - lowercase-first Latin name accepted`() {
        val hebrewText = "נשלחו לך מ גבלגג\n100 ₪\n15.03.26"
        val latinText  = "michal Shpiegel\n100 ₪\n15.03.26"
        val result = BitShareParser.parse(hebrewText, latinText)
        assertNotNull(result)
        assertTrue(result!!.senderName.contains("michal", ignoreCase = true))
    }

    // ── Requested (ביקשת מ) ──────────────────────────────────────────────

    @Test
    fun `requested - two-word Hebrew name`() {
        val result = BitShareParser.parse("ביקשת מ רחל אלוני\n75 ₪\n15.03.26")
        assertEquals("רחל אלוני", result?.senderName)
    }

    @Test
    fun `requested - Latin name`() {
        val result = BitShareParser.parse("ביקשת מ John Smith\n200 ₪\n15.03.26")
        assertEquals("John Smith", result?.senderName)
    }

    // ── Sent you (שלח/ה לך) ──────────────────────────────────────────────

    @Test
    fun `sent you - male form`() {
        val result = BitShareParser.parse("ישראל ישראלי שלח לך\n200 ₪\n15.03.26")
        assertEquals("ישראל ישראלי", result?.senderName)
    }

    @Test
    fun `sent you - female form`() {
        val result = BitShareParser.parse("שרה כהן שלחה לך\n150 ₪\n15.03.26")
        assertEquals("שרה כהן", result?.senderName)
    }

    // ── Structured name (Google Vision bounding box) ──────────────────────

    @Test
    fun `structured name takes priority over regex`() {
        val result = BitShareParser.parse(
            hebrewText     = "נשלחו לך מ דוד לוי\n100 ₪\n15.03.26",
            structuredName = "Maya Cohen"
        )
        assertEquals("Maya Cohen", result?.senderName)
    }

    @Test
    fun `invalid structured name falls back to regex`() {
        // "אא" has no word of 3+ chars → isValidName returns false → regex extraction used instead
        val result = BitShareParser.parse(
            hebrewText     = "נשלחו לך מ דוד לוי\n100 ₪\n15.03.26",
            structuredName = "אא"
        )
        assertEquals("דוד לוי", result?.senderName)
    }

    // ── Amount ─────────────────────────────────────────────────────────────

    @Test
    fun `amount extracted with normal shekel symbol`() {
        val result = BitShareParser.parse("נשלחו לך מ דוד לוי\n250 ₪\n15.03.26")
        assertEquals(250.0, result?.amount)
    }

    @Test
    fun `amount extracted with reversed shekel symbol`() {
        val result = BitShareParser.parse("נשלחו לך מ דוד לוי\n₪180\n15.03.26")
        assertEquals(180.0, result?.amount)
    }

    @Test
    fun `ML Kit bbox amount takes priority over OCR text`() {
        val result = BitShareParser.parse(
            hebrewText  = "נשלחו לך מ דוד לוי\n999 ₪\n15.03.26",
            mlKitAmount = 180.0
        )
        assertEquals(180.0, result?.amount)
    }

    @Test
    fun `returns null when amount cannot be extracted`() {
        // No digits anywhere — amount extraction truly finds nothing
        val result = BitShareParser.parse("נשלחו לך מ דוד לוי\nאין כסף כאן")
        assertNull(result)
    }

    // ── Expired payment ────────────────────────────────────────────────────

    @Test
    fun `isExpired detects expiry badge`() {
        assertTrue(BitShareParser.isExpired("עבר התוקף"))
        assertTrue(BitShareParser.isExpired("פג תוקף"))
        assertTrue(BitShareParser.isExpired("עבר blah התוקף blah"))
    }

    @Test
    fun `isExpired returns false for normal payment`() {
        assertFalse(BitShareParser.isExpired("נשלחו לך מ דוד לוי"))
    }
}
