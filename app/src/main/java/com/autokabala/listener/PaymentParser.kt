package com.autokabala.listener

import android.util.Log
import java.util.regex.Pattern

object PaymentParser {

    // Regex for Bit: Handles both "מ שירלי" and "משירלי"
    private val bitPattern = Pattern.compile("""([\d,]+\.?\d*)\s*ש"ח מחכים לך מ\s?(.+?)\s*באפליקציית bit""")
    // Regex for Bit (Payment Request Successful): "בקשת התשלום שנשלחה בהצלחה...לשרון גאגץ'...על סך 1.0 ש"ח..."
    private val bitRequestSuccessPattern = Pattern.compile("""בקשת התשלום שנשלחה בהצלחה.*?ל(.+?) על סך ([\d,]+\.?\d*)\s*ש"ח""")
    // Regex for Bit (Payment Request You Sent): "בקשת התשלום ששלחת...למיטל טנאי...על סך 1.0 ש"ח..הצליחה"
    private val bitRequestYouSentSuccessPattern = Pattern.compile("""בקשת התשלום ששלחת.*?ל(.+?) על סך ([\d,]+\.?\d*)\s*ש"ח.*?הצליחה""")

    // Regex for PayBox: "הועברו לך 1 ש״ח מניר גולדשטיין."
    private val payboxPattern = Pattern.compile("""הועברו לך ([\d,]+\.?\d*)\s*ש["״]ח מ([^.]+\.)""")

    fun parse(packageName: String, rawText: String, timestamp: Long): PaymentData? {
        return when (packageName) {
            "com.bnhp.payments.paymentsapp" -> parseBitNotification(rawText, timestamp)
            "com.payboxapp" -> parsePayBoxNotification(rawText, timestamp)
            else -> null
        }
    }

    private fun parseBitNotification(rawText: String, timestamp: Long): PaymentData? {
        val content = rawText.replace("\n", " ").substringAfter("|").trim()

        // Try the new "Payment Request You Sent" pattern first
        val youSentMatcher = bitRequestYouSentSuccessPattern.matcher(content)
        if (youSentMatcher.find()) {
            val senderName = youSentMatcher.group(1)?.trim()
            val amountStr = youSentMatcher.group(2)?.replace(",", "")
            val amount = amountStr?.toDoubleOrNull()

            if (amount != null && senderName != null) {
                Log.d("PaymentParser", "Successfully parsed Bit Request You Sent Success. Name: '$senderName', Amount: $amount")
                return PaymentData(
                    source = "bit",
                    senderName = senderName,
                    amount = amount,
                    isConfirmed = true, // This is a confirmed payment
                    timestamp = timestamp
                )
            }
        }

        // Try the "Payment Request Successful" pattern second
        val successMatcher = bitRequestSuccessPattern.matcher(content)
        if (successMatcher.find()) {
            val senderName = successMatcher.group(1)?.trim()
            val amountStr = successMatcher.group(2)?.replace(",", "")
            val amount = amountStr?.toDoubleOrNull()

            if (amount != null && senderName != null) {
                Log.d("PaymentParser", "Successfully parsed Bit Request Success. Name: '$senderName', Amount: $amount")
                return PaymentData(
                    source = "bit",
                    senderName = senderName,
                    amount = amount,
                    isConfirmed = true, // This is a confirmed payment
                    timestamp = timestamp
                )
            }
        }

        // Fallback to the original pattern
        val regularMatcher = bitPattern.matcher(content)
        if (regularMatcher.find()) {
            val amountStr = regularMatcher.group(1)?.replace(",", "")
            val senderName = regularMatcher.group(2)?.trim()
            val amount = amountStr?.toDoubleOrNull()

            if (amount != null && senderName != null) {
                Log.d("PaymentParser", "Successfully parsed regular Bit payment. Name: '$senderName', Amount: $amount")
                return PaymentData(
                    source = "bit",
                    senderName = senderName,
                    amount = amount,
                    isConfirmed = false, // This is a payment request
                    timestamp = timestamp
                )
            }
        }

        Log.w("PaymentParser", "Failed to parse Bit notification: $content")
        return null
    }

    private fun parsePayBoxNotification(rawText: String, timestamp: Long): PaymentData? {
        val title = rawText.substringBefore("|").trim()
        val content = rawText.substringAfter("|").trim()
        val matcher = payboxPattern.matcher(content)

        if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            val senderNameFromBody = matcher.group(2)?.trim()
            val senderName = if (title.isNotEmpty()) title else senderNameFromBody
            val amount = amountStr?.toDoubleOrNull()

            if (amount != null && senderName != null && senderName.isNotEmpty()) {
                return PaymentData(
                    source = "paybox",
                    senderName = senderName,
                    amount = amount,
                    isConfirmed = false, // PayBox notifications are also requests
                    timestamp = timestamp
                )
            }
        }
        return null
    }
}
