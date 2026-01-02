package com.autokabala.listener

import android.util.Log
import java.util.regex.Pattern

object PaymentParser {

    // Regex for Bit: "1.0 ש\"ח מחכים לך מניר באפליקציית bit"
    private val bitPattern = Pattern.compile("([\\d,]+\\.?\\d*)\\s*ש\"ח מחכים לך מ(\\S+)")

    // Regex for PayBox: "הועברו לך 1 ש״ח מניר גולדשטיין."
    // The definitive pattern. It accepts both standard quotes (") and gershayim (״).
    private val payboxPattern = Pattern.compile("הועברו לך ([\\d,]+\\.?\\d*)\\s*ש[\"״]ח מ([^.]+)")

    fun parse(packageName: String, rawText: String, timestamp: Long): PaymentData? {
        // The debug logs are no longer needed now that we have found the issue.
        return when (packageName) {
            "com.bnhp.payments.paymentsapp" -> parseBitNotification(rawText, timestamp)
            "com.payboxapp" -> parsePayBoxNotification(rawText, timestamp)
            else -> null
        }
    }

    private fun parseBitNotification(rawText: String, timestamp: Long): PaymentData? {
        val content = rawText.substringAfter("|").trim()
        val matcher = bitPattern.matcher(content)

        if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            val senderName = matcher.group(2)
            val amount = amountStr?.toDoubleOrNull()

            if (amount != null && senderName != null) {
                return PaymentData(
                    source = "bit",
                    senderName = senderName,
                    amount = amount,
                    isConfirmed = false, // Bit notifications are requests
                    timestamp = timestamp,
                    rawText = rawText
                )
            }
        }
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
                    timestamp = timestamp,
                    rawText = rawText
                )
            }
        }
        return null
    }
}
