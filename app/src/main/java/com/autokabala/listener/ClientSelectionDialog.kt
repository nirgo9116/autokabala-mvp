package com.autokabala.listener

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClientSelectionDialog(
    clients: List<ClientEntity>,
    onDismiss: () -> Unit,
    onClientSelected: (ClientEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Client") },
        text = {
            LazyColumn {
                items(clients) { client ->
                    Text(
                        text = client.name,
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .clickable { onClientSelected(client) }
                            .padding(vertical = 16.dp)
                    )
                }
            }
        },
        confirmButton = { }
    )
}
