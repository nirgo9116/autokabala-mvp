package com.autokabala.listener

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Date

private val durationOptions = listOf(
    30  to "30 דקות",
    45  to "45 דקות",
    60  to "שעה",
    90  to "שעה וחצי",
    120 to "שעתיים"
)

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
    onUpdate: (ScheduledPaymentEntity) -> Unit,
    onDelete: (ScheduledPaymentEntity) -> Unit,
    onDeleteSeries: (ScheduledPaymentEntity) -> Unit,
    onSendReminder: (ScheduledPaymentEntity, ClientEntity?) -> Unit,
    onMarkTookPlace: (ScheduledPaymentEntity, Boolean?, ClientEntity?) -> Unit,
    onSendWhatsApp: (ScheduledPaymentEntity, ClientEntity?, Boolean) -> Unit,
    onIssueReceiptForSession: (ScheduledPaymentEntity, ClientEntity?) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var rescheduleFor by remember { mutableStateOf<Pair<ScheduledPaymentEntity, ClientEntity?>?>(null) }
    var editFor by remember { mutableStateOf<Pair<ScheduledPaymentEntity, ClientEntity?>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("פגישות מתוכננות", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("צור פגישה")
            }
        }
        HorizontalDivider()

        val sorted = remember(scheduledPayments) { scheduledPayments.sortedBy { it.scheduledDate } }

        if (sorted.isEmpty()) {
            EmptyState(
                icon        = Icons.Outlined.CalendarMonth,
                title       = "אין פגישות מתוכננות",
                subtitle    = "צור את הפגישה הראשונה שלך כדי לעקוב אחר המפגשים ולסנכרן עם לוח השנה",
                actionLabel = "צור פגישה",
                onAction    = { showCreateDialog = true }
            )
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
                        onDeleteSeries = if (payment.seriesId != null) { { onDeleteSeries(payment) } } else null,
                        onSendReminder = { onSendReminder(payment, client) },
                        onMarkTookPlace = { tookPlace -> onMarkTookPlace(payment, tookPlace, client) },
                        onSendWhatsApp = { tookPlace -> onSendWhatsApp(payment, client, tookPlace) },
                        onIssueReceipt = { onIssueReceiptForSession(payment, client) },
                        onReschedule = { rescheduleFor = payment to client },
                        onEdit = { editFor = payment to client }
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

    rescheduleFor?.let { (session, client) ->
        CreateScheduledPaymentDialog(
            allClients = allClients,
            calendarEvents = calendarEvents,
            initialClient = client,
            initialAmount = session.amount,
            onDismiss = { rescheduleFor = null },
            onCreate = { entity -> onInsert(entity); rescheduleFor = null }
        )
    }

    editFor?.let { (session, client) ->
        CreateScheduledPaymentDialog(
            allClients = allClients,
            calendarEvents = calendarEvents,
            initialClient = client,
            initialData = session,
            onDismiss = { editFor = null },
            onCreate = { entity ->
                onUpdate(session.copy(
                    clientId = entity.clientId,
                    clientName = entity.clientName,
                    amount = entity.amount,
                    scheduledDate = entity.scheduledDate,
                    description = entity.description,
                    reminderHoursAfter = entity.reminderHoursAfter,
                    durationMinutes = entity.durationMinutes
                ))
                editFor = null
            }
        )
    }
}

@Composable
fun ScheduledPaymentCard(
    payment: ScheduledPaymentEntity,
    onDelete: () -> Unit,
    onDeleteSeries: (() -> Unit)? = null,
    onSendReminder: () -> Unit,
    onMarkTookPlace: (Boolean?) -> Unit,
    onSendWhatsApp: (Boolean) -> Unit,
    onIssueReceipt: () -> Unit,
    onReschedule: () -> Unit,
    onEdit: () -> Unit
) {
    val isPast = payment.scheduledDate < System.currentTimeMillis()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("מחיקת פגישה") },
            text = { Text("האם למחוק רק פגישה זו, או את כל סדרת הפגישות?") },
            confirmButton = {
                TextButton(onClick = { onDeleteSeries?.invoke(); showDeleteDialog = false }) {
                    Text("מחק את כל הסדרה", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("מחק פגישה זו בלבד")
                }
            }
        )
    }
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
                        Text(dateTimeFormat.format(Date(payment.scheduledDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (payment.reminderRecurrenceDays > 0) {
                        val label = recurrenceOptions.find { it.first == payment.reminderRecurrenceDays }?.second ?: "כל ${payment.reminderRecurrenceDays} ימים"
                        Text("🔁 $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "ערוך", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(payment.amount.toFormattedAmount(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            // Took-place indicator
            if (isPast) {
                when (payment.tookPlace) {
                    null -> {
                        Text(
                            "האם הפגישה התקיימה?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onMarkTookPlace(true) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("✓ כן", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { onMarkTookPlace(false) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("✗ לא", fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                    }
                    true -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✓ הפגישה התקיימה", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                            TextButton(
                                onClick = { onMarkTookPlace(null) },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("שנה", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (payment.receiptIssued) {
                            Text("קבלה הופקה ✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        } else {
                            FilledTonalButton(
                                onClick = onIssueReceipt,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("הפק קבלה", fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { onSendWhatsApp(true) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("💬 שלח תודה", fontSize = 12.sp) }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                    }
                    false -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✗ הפגישה לא התקיימה", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            TextButton(
                                onClick = { onMarkTookPlace(null) },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("שנה", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = onReschedule,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("קבע פגישה חדשה", fontSize = 12.sp) }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { onSendWhatsApp(false) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("💬 שלח הודעה", fontSize = 12.sp) }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPast) {
                    OutlinedButton(onClick = onSendReminder, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("תזכורת", fontSize = 12.sp)
                    }
                } else {
                    Spacer(Modifier.size(0.dp))
                }
                IconButton(
                    onClick = { if (onDeleteSeries != null) showDeleteDialog = true else onDelete() },
                    modifier = Modifier.size(36.dp)
                ) {
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
    initialClient: ClientEntity? = null,
    initialAmount: Double? = null,
    initialData: ScheduledPaymentEntity? = null,
    onDismiss: () -> Unit,
    onCreate: (ScheduledPaymentEntity) -> Unit
) {
    val initCal = remember(initialData) {
        Calendar.getInstance().apply { timeInMillis = initialData?.scheduledDate ?: System.currentTimeMillis() }
    }
    var selectedClient by remember { mutableStateOf(initialClient) }
    var clientSearch by remember { mutableStateOf(initialClient?.name ?: "") }
    var amountText by remember { mutableStateOf(initialData?.amount?.toInt()?.toString() ?: initialAmount?.toInt()?.toString() ?: "") }
    var description by remember { mutableStateOf(initialData?.description ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedHour   by remember { mutableStateOf(initCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(initCal.get(Calendar.MINUTE)) }
    var clientDropdownExpanded by remember { mutableStateOf(false) }

    val filteredClients = remember(clientSearch, allClients) {
        if (clientSearch.isBlank()) allClients
        else {
            val searchWords = clientSearch.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            allClients.filter { client ->
                val clientWords = client.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                searchWords.all { sw -> clientWords.any { cw -> cw.startsWith(sw, ignoreCase = true) } }
            }
        }
    }

    // Auto-select when search narrows down to exactly one match
    LaunchedEffect(filteredClients) {
        if (filteredClients.size == 1 && clientSearch.isNotBlank()) {
            selectedClient = filteredClients.first()
        }
    }
    var reminderHours by remember { mutableStateOf(initialData?.reminderHoursAfter ?: 24) }
    var reminderHoursExpanded by remember { mutableStateOf(false) }
    var recurrenceDays by remember { mutableStateOf(0) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var durationMinutes by remember { mutableStateOf(initialData?.durationMinutes ?: 60) }
    var durationExpanded by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialData?.scheduledDate ?: System.currentTimeMillis())
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

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("בחר שעה") },
            text = { TimeInput(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePicker = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("ביטול") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialData != null) "ערוך פגישה" else "פגישה מתוכננת חדשה", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Client
                ExposedDropdownMenuBox(expanded = clientDropdownExpanded, onExpandedChange = { clientDropdownExpanded = it }) {
                    OutlinedTextField(
                        value = clientSearch,
                        onValueChange = { clientSearch = it; selectedClient = null; clientDropdownExpanded = true },
                        label = { Text("לקוח") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = clientDropdownExpanded, onDismissRequest = { clientDropdownExpanded = false }) {
                        filteredClients.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(client.name) },
                                onClick = { selectedClient = client; clientSearch = client.name; clientDropdownExpanded = false }
                            )
                        }
                    }
                }
                // Amount
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("סכום (₪)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // Date + Time row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1.5f).clickable { showDatePicker = true }) {
                        OutlinedTextField(
                            value = dateOnlyFormat.format(Date(selectedDateMs)), onValueChange = {}, readOnly = true,
                            label = { Text("תאריך") },
                            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        )
                    }
                    val timeLabel = "%02d:%02d".format(selectedHour, selectedMinute)
                    Box(modifier = Modifier.weight(1f).clickable { showTimePicker = true }) {
                        OutlinedTextField(
                            value = timeLabel, onValueChange = {}, readOnly = true,
                            label = { Text("שעה") },
                            trailingIcon = { Icon(Icons.Outlined.Schedule, null) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        )
                    }
                }
                // Duration
                ExposedDropdownMenuBox(expanded = durationExpanded, onExpandedChange = { durationExpanded = it }) {
                    OutlinedTextField(
                        value = durationOptions.find { it.first == durationMinutes }?.second ?: "$durationMinutes דקות",
                        onValueChange = {}, readOnly = true, label = { Text("משך הפגישה") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                        durationOptions.forEach { (mins, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { durationMinutes = mins; durationExpanded = false })
                        }
                    }
                }
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
                // Recurrence (only for new meetings, not edits)
                if (initialData == null) ExposedDropdownMenuBox(expanded = recurrenceExpanded, onExpandedChange = { recurrenceExpanded = it }) {
                    OutlinedTextField(
                        value = recurrenceOptions.find { it.first == recurrenceDays }?.second ?: "כל $recurrenceDays ימים",
                        onValueChange = {}, readOnly = true, label = { Text("תדירות הפגישה") },
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
            val amount = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    val client = selectedClient ?: return@Button
                    val finalTimestamp = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMs
                        set(Calendar.HOUR_OF_DAY, selectedHour)
                        set(Calendar.MINUTE, selectedMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onCreate(ScheduledPaymentEntity(
                        clientId = client.id, clientName = client.name, amount = amount,
                        scheduledDate = finalTimestamp, description = description.trim(),
                        reminderHoursAfter = reminderHours, reminderRecurrenceDays = recurrenceDays,
                        durationMinutes = durationMinutes
                    ))
                },
                enabled = selectedClient != null
            ) { Text("צור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
