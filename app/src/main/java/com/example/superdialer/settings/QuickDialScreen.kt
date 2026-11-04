package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.superdialer.contacts.ContactsViewModel
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.dialer.QuickDial
import com.example.superdialer.dialer.QuickDials
import com.example.superdialer.ui.ScreenHeader

/** Assign a contact (or a typed number) to keypad digits 1-9: long-press the digit on the keypad to use it. */
@Composable
fun QuickDialScreen(contactsViewModel: ContactsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf<Char?>(null) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(str(R.string.t_speed_dial), onBack)
        Text(
            str(R.string.t_long_pressing_a_digit_on_the),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ListItem(
            colors = transparent,
            headlineContent = { Text(str(R.string.t_long_press_places_the_call_at)) },
            supportingContent = { Text(str(R.string.t_when_off_the_number_is_only)) },
            trailingContent = {
                Switch(checked = AppSettings.quickDialCallsDirectly, onCheckedChange = AppSettings::updateQuickDialCallsDirectly)
            },
        )
        HorizontalDivider()
        for (digit in '1'..'9') {
            val dial = QuickDials.entries[digit]
            ListItem(
                modifier = Modifier.clickable { picking = digit },
                colors = transparent,
                leadingContent = { Text(digit.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                headlineContent = { Text(dial?.name ?: str(R.string.t_not_set)) },
                supportingContent = { if (dial != null) Text(PhoneNumberFormatter.formatLoose(dial.number)) },
            )
        }
    }

    picking?.let { digit ->
        PickDialog(
            digit = digit,
            contactsViewModel = contactsViewModel,
            onDismiss = { picking = null },
            onPick = { QuickDials.set(digit, it); picking = null },
            onClear = { QuickDials.clear(digit); picking = null },
        )
    }
}

@Composable
private fun PickDialog(
    digit: Char,
    contactsViewModel: ContactsViewModel,
    onDismiss: () -> Unit,
    onPick: (QuickDial) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    val rows = remember(query, contactsViewModel.contacts) {
        contactsViewModel.contacts
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .flatMap { c -> c.numbers.map { c.name to it } }
            .take(60)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.t_speed_dial_2, digit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(str(R.string.t_search_contacts)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(rows) { (name, number) ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(QuickDial(name, number)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(name) },
                            supportingContent = { Text(PhoneNumberFormatter.formatLoose(number)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text(str(R.string.t_or_enter_a_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = manual.any { it.isDigit() },
                onClick = { onPick(QuickDial(manual.trim(), manual.filter { it.isDigit() || it == '+' })) },
            ) { Text(str(R.string.t_use_the_entered_number)) }
        },
        dismissButton = {
            Column {
                if (QuickDials.get(digit) != null) TextButton(onClick = onClear) { Text(str(R.string.t_remove)) }
                TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) }
            }
        },
    )
}
