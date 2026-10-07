package com.example.superdialer.calllog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.addContact
import com.example.superdialer.ui.rememberDialAction
import com.example.superdialer.ui.sendSms

/** Every call with the same number as [entryId], newest first, plus call / contact / message actions. */
@Composable
fun CallHistoryScreen(
    viewModel: CallLogViewModel,
    entryId: Long,
    onBack: () -> Unit,
    onOpenContact: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context: Context = LocalContext.current
    val dial = rememberDialAction()

    // The list may be empty if the process was recreated while this screen was open.
    LaunchedEffect(entryId) { if (viewModel.entries.isEmpty()) viewModel.refresh() }

    val entries = viewModel.entries
    val entry = entries.firstOrNull { it.id == entryId }
    val history = remember(entries, entry) {
        if (entry == null) {
            emptyList()
        } else if (entry.number.isEmpty()) {
            listOf(entry)
        } else {
            val key = numberKey(entry.number)
            entries.filter { numberKey(it.number) == key }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
        }

        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (viewModel.loading) CircularProgressIndicator() else Text("통화 기록을 찾을 수 없습니다.")
            }
            return@Column
        }

        val formattedNumber = PhoneNumberFormatter.formatLoose(entry.number).ifEmpty { UNKNOWN_NUMBER }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InitialAvatar(entry.name ?: formattedNumber, size = 96.dp, fontSize = 40.sp)
            Text(
                text = entry.name ?: formattedNumber,
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (entry.name != null) {
                Text(formattedNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.number.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionButton(Icons.Filled.Call, "전화", { dial(entry.number) })
                    ActionButton(Icons.AutoMirrored.Filled.Message, "메시지", { context.sendSms(entry.number) })
                    ActionButton(
                        Icons.Filled.Person,
                        if (entry.contactId != null) "연락처" else "연락처 추가",
                        {
                            val id = entry.contactId
                            if (id != null) onOpenContact(id) else context.addContact(entry.number)
                        },
                    )
                }
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history, key = { it.id }) { item ->
                val missed = item.type == CallType.Missed
                val tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.type.icon(), contentDescription = item.type.label, tint = tint)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.type.label, color = if (missed) tint else MaterialTheme.colorScheme.onSurface)
                        Text(
                            formatDateTime(item.dateMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val duration = formatDuration(item.durationSeconds)
                    if (duration.isNotEmpty()) {
                        Text(duration, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
