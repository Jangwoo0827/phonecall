package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.ui.rememberDefaultDialerStatus

@Composable
fun BlockedNumbersScreen(
    viewModel: BlockedNumbersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val defaultDialer = rememberDefaultDialerStatus()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(str(R.string.t_blocked_numbers), onBack)

        if (!viewModel.canBlock) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(str(R.string.t_blocking_numbers_needs_superdialer_to))
                if (!defaultDialer.isDefault) {
                    Button(onClick = defaultDialer.request) { Text(str(R.string.t_set_as_default_phone_app)) }
                }
            }
            return@Column
        }

        var input by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text(str(R.string.t_number_to_block)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    viewModel.add(input.trim()) { ok ->
                        if (ok) {
                            input = ""
                        } else {
                            Toast.makeText(context, str(R.string.t_couldn_t_block), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) { Text(str(R.string.t_add)) }
        }

        if (viewModel.numbers.isEmpty()) {
            Text(str(R.string.t_no_blocked_numbers), modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(viewModel.numbers, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(PhoneNumberFormatter.formatLoose(item.number).ifEmpty { item.number }) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.remove(item) }) {
                                Icon(Icons.Filled.Delete, contentDescription = str(R.string.t_unblock))
                            }
                        },
                    )
                }
            }
        }
    }
}
