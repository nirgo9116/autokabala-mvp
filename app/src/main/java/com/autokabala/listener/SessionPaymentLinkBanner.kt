package com.autokabala.listener

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val sessionFmt = SimpleDateFormat("EEEE 'בשעה' HH:mm", Locale("iw"))

@Composable
fun SessionPaymentLinkBanner(
    payment: PaymentEntity,
    matchedSession: ScheduledPaymentEntity,
    onConfirmLink: () -> Unit,
    onDismiss: () -> Unit
) {
    val sessionLabel = matchedSession.description.ifBlank { "פגישה" }
    val dateStr      = sessionFmt.format(Date(matchedSession.scheduledDate))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("📅 $sessionLabel — $dateStr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("התשלום הזה עבור הפגישה הזו?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss) { Text("לא קשור", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirmLink) { Text("כן, הפק קבלה", fontSize = 12.sp) }
            }
        }
    }
}
