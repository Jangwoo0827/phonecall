package com.example.superdialer.messages

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.superdialer.calllog.formatDateTime
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.openAppSettings
import com.example.superdialer.ui.sendSms

/**
 * Recent text messages with [numbers] as chat bubbles, newest first (received on the left, sent on the right).
 * Asks for the SMS permission first. Meant to sit inside a scrolling list, so it is a plain Column.
 */
@Composable
fun MessagesPane(
    numbers: List<String>,
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = viewModel(),
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasPermission(Manifest.permission.READ_SMS)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    // The user may flip the permission in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasPermission(Manifest.permission.READ_SMS) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }

    LaunchedEffect(numbers, granted) { if (granted) viewModel.load(numbers) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!granted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "주고받은 문자를 보려면 문자 읽기 권한이 필요합니다.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = { launcher.launch(Manifest.permission.READ_SMS) }) { Text("권한 허용") }
                if (asked) TextButton(onClick = { context.openAppSettings() }) { Text("설정 열기") }
            }
            return@Column
        }

        val messages = viewModel.messages
        when {
            messages.isEmpty() && viewModel.loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            messages.isEmpty() -> Text(
                "주고받은 문자가 없습니다.\n(MMS와 채팅+·카카오톡 등 채팅 메시지는 표시되지 않습니다)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    messages.forEach { Bubble(it) }
                }
                TextButton(
                    onClick = { numbers.firstOrNull()?.let(context::sendSms) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("문자 앱에서 이어서 보기") }
            }
        }
    }
}

@Composable
private fun Bubble(message: SmsMessage) {
    val mine = message.outgoing
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatDateTime(message.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** "통화 기록 / 메시지" switch shown on the contact and call-history screens. */
@Composable
fun ActivityChips(
    showMessages: Boolean,
    callCount: Int,
    onSelect: (showMessages: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.FilterChip(
            selected = !showMessages,
            onClick = { onSelect(false) },
            label = { Text(if (callCount > 0) "통화 기록 $callCount" else "통화 기록") },
            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        androidx.compose.material3.FilterChip(
            selected = showMessages,
            onClick = { onSelect(true) },
            label = { Text("메시지") },
            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}
