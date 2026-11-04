package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.superdialer.ui.ScreenHeader

/** Index of the message being edited, [NEW] for a new one, or null when no dialog is open. */
private const val NEW = -1

@Composable
fun RejectMessagesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<Int?>(null) }
    val messages = RejectMessageStore.messages

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(str(R.string.t_decline_messages), onBack) {
            IconButton(onClick = { editing = NEW }) { Icon(Icons.Filled.Add, contentDescription = str(R.string.t_add_message)) }
        }
        Text(
            str(R.string.t_when_you_tap_decline_with_message),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(messages) { index, message ->
                ListItem(
                    modifier = Modifier.clickable { editing = index },
                    headlineContent = { Text(message) },
                    trailingContent = {
                        IconButton(onClick = { RejectMessageStore.remove(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = str(R.string.t_delete))
                        }
                    },
                )
            }
        }
        TextButton(
            onClick = { RejectMessageStore.resetToDefaults() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) { Text(str(R.string.t_restore_default_messages)) }
    }

    editing?.let { index ->
        var text by remember(index) { mutableStateOf(messages.getOrNull(index).orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (index == NEW) str(R.string.t_add_message) else str(R.string.t_edit_message)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        if (index == NEW) RejectMessageStore.add(text) else RejectMessageStore.update(index, text)
                        editing = null
                    },
                ) { Text(str(R.string.t_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(str(R.string.cancel)) } },
        )
    }
}
