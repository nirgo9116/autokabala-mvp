package com.autokabala.listener

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun CreateNewClientDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onCreateConfirm: (String) -> Unit
) {
    var clientName by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Client") },
        text = {
            Column {
                Text("Enter the full name for the new client.")
                TextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Client Name") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreateConfirm(clientName) }) {
                Text("Create & Issue Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
