package com.autokabala.listener

import org.junit.Assert.*
import org.junit.Test

class PayboxShareParserTest {

    // ── Name extraction ────────────────────────────────────────────────────

    @Test
    fun `hebrew name from title line`() {
        val result = PayboxShareParser.parse("העברה מ ישראל כהן\n₪220\n15.03.26 14:30\nהועבר אל")
        assertEquals("ישראל כהן", result?.senderName)
    }

    @Test
    fun `name directly attached to מ with no space`() {
        // "העברה מHanita" — מ immediately followed by name, no space
        val result = PayboxShareParser.parse("העברה מHanita\n₪100\n15.03.26\nהועבר אל")
        assertEquals("Hanita", result?.senderName)
    }

    @Test
    fun `name with parentheses - parens removed`() {
        // "(עידו)" → "עידו" (parens stripped, content preserved)
        val result = PayboxShareParser.parse("העברה מ (עידו) ישראל\n₪50\n15.03.26\nהועבר אל")
        assertEquals("עידו ישראל", result?.senderName)
    }

    @Test
    fun `Latin name provided via ML Kit bbox hint`() {
        // Tesseract garbles the Latin first name; ML Kit hint has the real name
        val result = PayboxShareParser.parse(
            hebrewText    = "העברה מ גבלגג\n₪100\n15.03.26\nהועבר אל",
            mlKitNameHint = "Hanita"
        )
        assertEquals("Hanita", result?.senderName)
    }

    @Test
    fun `Latin name merged with Hebrew suffix`() {
        // Tesseract: first word garbled, second word is real Hebrew → ML Kit hint replaces only the first word
        val result = PayboxShareParser.parse(
            hebrewText    = "העברה מ גמזוחבח מד\n₪150\n15.03.26\nהועבר אל",
            mlKitNameHint = "Hanita"
        )
        assertEquals("Hanita מד", result?.senderName)
    }

    @Test
    fun `returns null when no transfer line found`() {
        val result = PayboxShareParser.parse("אין כאן שם\n₪100\nהועבר אל")
        assertNull(result)
    }

    // ── Amount ─────────────────────────────────────────────────────────────

    @Test
    fun `amount extracted from reversed shekel`() {
        val result = PayboxShareParser.parse("העברה מ ישראל כהן\n₪220\n15.03.26\nהועבר אל")
        assertEquals(220.0, result?.amount)
    }

    @Test
    fun `ML Kit bbox amount takes priority over OCR text`() {
        val result = PayboxShareParser.parse(
            hebrewText  = "העברה מ ישראל כהן\n₪999\n15.03.26\nהועבר אל",
            mlKitAmount = 180.0
        )
        assertEquals(180.0, result?.amount)
    }

    @Test
    fun `approval number line is not mistaken for amount`() {
        // "מספר אישור" line must be skipped; real amount is on the ₪ line
        val result = PayboxShareParser.parse(
            "העברה מ ישראל כהן\nמספר אישור 123456\n₪300\n15.03.26\nהועבר אל"
        )
        assertEquals(300.0, result?.amount)
    }

    // ── Source detection ───────────────────────────────────────────────────

    @Test
    fun `isPaybox returns true for Paybox-specific phrases`() {
        assertTrue(PayboxShareParser.isPaybox("הועבר אל"))
        assertTrue(PayboxShareParser.isPaybox("מספר אישור 12345"))
        assertTrue(PayboxShareParser.isPaybox("PayBox transfer"))
    }

    @Test
    fun `isPaybox returns false when Bit trigger phrase is present`() {
        assertFalse(PayboxShareParser.isPaybox("נשלחו לך מ דוד לוי"))
        assertFalse(PayboxShareParser.isPaybox("ביקשת מ רחל"))
        assertFalse(PayboxShareParser.isPaybox("שלח לך 100 ₪"))
    }
}
