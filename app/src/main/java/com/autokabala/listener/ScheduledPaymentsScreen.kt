package com.autokabala.listener

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val scheduledDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

private val recurrenceOptions = listOf(
    0  to "ללא חזרה",
    1  to "כל יום",
    7  to "כל שבוע",
    14 to "כל שבועיים",
    30 to "כל חודש"
)

private val reminderHourOptions = listOf(
    1  to "שעה לאחר המועד",
    3  to "3 שעות לאחר המועד",
    6  to "6 שעות לאחר המועד",
    12 to "12 שעות לאחר המועד",
    24 to "24 שעות לאחר המועד",
    48 to "48 שעות לאחר המועד"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledPaymentsScreen(
    modifier: Modifier = Modifier,
    scheduledPayments: List<ScheduledPaymentEntity>,
    allClients: List<ClientEntity>,
    calendarEvents: List<CalendarEventEntity> = emptyList(),
    onInsert: (ScheduledPaymentEntity) -> Unit,
    onDelete: (ScheduledPaymentEntity) -> Unit,
    onSendReminder: (ScheduledPaymentEntity, ClientEntity?) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("תשלומים מתוכננים", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("צור תשלום")
            }
        }
        HorizontalDivider()

        val sorted = remember(scheduledPayments) { scheduledPayments.sortedBy { it.scheduledDate } }

        if (sorted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("אין תשלומים מתוכננים", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "צור תשלום מתוכנן כדי לקבל תזכורות",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.id }) { payment ->
                    val client = allClients.find { it.id == payment.clientId }
                    ScheduledPaymentCard(
                        payment = payment,
                        onDelete = { onDelete(payment) },
                        onSendReminder = { onSendReminder(payment, client) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateScheduledPaymentDialog(
            allClients = allClients,
            calendarEvents = calendarEvents,
            onDismiss = { showCreateDialog = false },
            onCreate = { entity -> onInsert(entity); showCreateDialog = false }
        )
    }
}

@Composable
fun ScheduledPaymentCard(
    payment: ScheduledPaymentEntity,
    onDelete: () -> Unit,
    onSendReminder: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(payment.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (payment.description.isNotBlank())
                        Text(payment.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(scheduledDateFormat.format(Date(payment.scheduledDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (payment.reminderRecurrenceDays > 0) {
                        val label = recurrenceOptions.find { it.first == payment.reminderRecurrenceDays }?.second ?: "כל ${payment.reminderRecurrenceDays} ימים"
                        Text("🔁 $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text("₪${payment.amount.toInt()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onSendReminder, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("תזכורת", fontSize = 12.sp)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "מחק", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduledPaymentDialog(
    allClients: List<ClientEntity>,
    calendarEvents: List<CalendarEventEntity> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (ScheduledPaymentEntity) -> Unit
) {
    var selectedClient by remember { mutableStateOf<ClientEntity?>(null) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var clientDropdownExpanded by remember { mutableStateOf(false) }
    var reminderHours by remember { mutableStateOf(24) }
    var reminderHoursExpanded by remember { mutableStateOf(false) }
    var recurrenceDays by remember { mutableStateOf(0) }
    var recurrenceExpanded by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val selectedDateMs = datePickerState.selectedDateMillis ?: System.currentTimeMillis()

    val nearbyEvents = remember(selectedDateMs, calendarEvents) {
        val window = 3L * 24 * 3_600_000
        calendarEvents.filter { it.startTime in (selectedDateMs - window)..(selectedDateMs + window) }.take(3)
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("אישור") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ביטול") } }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("תשלום מתוכנן חדש", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Client
                ExposedDropdownMenuBox(expanded = clientDropdownExpanded, onExpandedChange = { clientDropdownExpanded = it }) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: "", onValueChange = {}, readOnly = true,
                        label = { Text("לקוח") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = clientDropdownExpanded, onDismissRequest = { clientDropdownExpanded = false }) {
                        allClients.forEach { client ->
                            DropdownMenuItem(text = { Text(client.name) }, onClick = { selectedClient = client; clientDropdownExpanded = false })
                        }
                    }
                }
                // Amount
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("סכום (₪)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // Date
                OutlinedTextField(
                    value = scheduledDateFormat.format(Date(selectedDateMs)), onValueChange = {}, readOnly = true,
                    label = { Text("תאריך") },
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Outlined.CalendarMonth, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                // Nearby calendar events
                if (nearbyEvents.isNotEmpty()) {
                    Text("אירועים בלוח השנה שלך:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        nearbyEvents.forEach { event ->
                            SuggestionChip(onClick = {}, label = { Text(event.title, fontSize = 11.sp, maxLines = 1) })
                        }
                    }
                }
                // Description
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("תיאור (אופציונלי)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // Reminder hours
                ExposedDropdownMenuBox(expanded = reminderHoursExpanded, onExpandedChange = { reminderHoursExpanded = it }) {
                    OutlinedTextField(
                        value = reminderHourOptions.find { it.first == reminderHours }?.second ?: "$reminderHours שעות לאחר המועד",
                        onValueChange = {}, readOnly = true, label = { Text("שליחת תזכורת") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderHoursExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = reminderHoursExpanded, onDismissRequest = { reminderHoursExpanded = false }) {
                        reminderHourOptions.forEach { (hours, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { reminderHours = hours; reminderHoursExpanded = false })
                        }
                    }
                }
                // Recurrence
                ExposedDropdownMenuBox(expanded = recurrenceExpanded, onExpandedChange = { recurrenceExpanded = it }) {
                    OutlinedTextField(
                        value = recurrenceOptions.find { it.first == recurrenceDays }?.second ?: "כל $recurrenceDays ימים",
                        onValueChange = {}, readOnly = true, label = { Text("חזרה על תזכורת") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = recurrenceExpanded, onDismissRequest = { recurrenceExpanded = false }) {
                        recurrenceOptions.forEach { (days, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { recurrenceDays = days; recurrenceExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull()
            Button(
                onClick = {
                    val client = selectedClient ?: return@Button
                    val amt = amount ?: return@Button
                    onCreate(ScheduledPaymentEntity(
                        clientId = client.id, clientName = client.name, amount = amt,
                        scheduledDate = selectedDateMs, description = description.trim(),
                        reminderHoursAfter = reminderHours, reminderRecurrenceDays = recurrenceDays
                    ))
                },
                enabled = selectedClient != null && (amount ?: 0.0) > 0
            ) { Text("צור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
