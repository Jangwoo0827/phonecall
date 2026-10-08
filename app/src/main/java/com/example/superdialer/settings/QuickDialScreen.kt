package com.example.superdialer.settings

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
        ScreenHeader("단축 다이얼", onBack)
        Text(
            "키패드에서 숫자를 길게 누르면 지정한 번호가 입력됩니다. 이 설정은 이 폰에만 저장되고 계정과 동기화되지 않습니다.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ListItem(
            colors = transparent,
            headlineContent = { Text("길게 누르면 바로 발신") },
            supportingContent = { Text("꺼 두면 번호만 입력되고 통화 버튼을 눌러야 걸립니다 (실수 방지)") },
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
                headlineContent = { Text(dial?.name ?: "지정 안 함") },
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
        title = { Text("${digit}번 단축 다이얼") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("연락처 검색") },
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
                    label = { Text("또는 번호 직접 입력") },
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
            ) { Text("입력한 번호 사용") }
        },
        dismissButton = {
            Column {
                if (QuickDials.get(digit) != null) TextButton(onClick = onClear) { Text("지정 해제") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
