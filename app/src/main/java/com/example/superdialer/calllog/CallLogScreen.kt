package com.example.superdialer.calllog

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.PermissionGate
import com.example.superdialer.ui.addContact
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.rememberDialAction
import com.example.superdialer.ui.sendSms
import java.time.Instant
import java.time.ZoneId

internal val CallGreen = Color(0xFF2E7D32)
internal const val UNKNOWN_NUMBER = "번호정보 없음"

@Composable
fun CallLogScreen(
    viewModel: CallLogViewModel,
    onOpenHistory: (entryId: Long) -> Unit,
    onOpenContact: (contactId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionGate(
        required = listOf(Manifest.permission.READ_CALL_LOG),
        optional = listOf(Manifest.permission.WRITE_CALL_LOG, Manifest.permission.READ_CONTACTS),
        rationale = "최근 통화 기록을 보려면 통화 기록 권한이 필요합니다.\n연락처 권한을 허용하면 저장된 이름도 표시됩니다.",
        modifier = modifier,
    ) {
        CallLogList(viewModel, onOpenHistory, onOpenContact, modifier)
    }
}

@Composable
private fun CallLogList(
    viewModel: CallLogViewModel,
    onOpenHistory: (Long) -> Unit,
    onOpenContact: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dial = rememberDialAction()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun delete(entry: CallLogEntry) = viewModel.delete(entry) { ok ->
        if (!ok) toast("삭제하지 못했습니다.")
    }

    var pendingDelete by remember { mutableStateOf<CallLogEntry?>(null) }
    val writePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val entry = pendingDelete
        pendingDelete = null
        if (granted && entry != null) delete(entry) else toast("삭제하려면 통화 기록 쓰기 권한이 필요합니다.")
    }

    fun requestDelete(entry: CallLogEntry) {
        if (context.hasPermission(Manifest.permission.WRITE_CALL_LOG)) {
            delete(entry)
        } else {
            pendingDelete = entry
            writePermission.launch(Manifest.permission.WRITE_CALL_LOG)
        }
    }

    fun block(entry: CallLogEntry) = viewModel.block(entry.number) { result ->
        toast(
            when (result) {
                BlockResult.Blocked -> "차단했습니다."
                BlockResult.NotAllowed -> "기본 전화 앱으로 설정해야 번호를 차단할 수 있습니다."
                BlockResult.Failed -> "차단하지 못했습니다."
            }
        )
    }

    // Tapping a row only expands it; the green button is the only thing that dials.
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    val entries = viewModel.entries
    val days = remember(entries) {
        val zone = ZoneId.systemDefault()
        entries.groupBy { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }.values.toList()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            entries.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                days.forEach { dayEntries ->
                    item(key = "day-${dayEntries.first().id}") {
                        DayHeader(formatDayHeader(dayEntries.first().dateMillis))
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        CallLogRow(
                            entry = entry,
                            expanded = expandedId == entry.id,
                            onToggle = { expandedId = if (expandedId == entry.id) null else entry.id },
                            onCall = { dial(entry.number) },
                            onHistory = { onOpenHistory(entry.id) },
                            onContact = {
                                val id = entry.contactId
                                if (id != null) onOpenContact(id) else context.addContact(entry.number)
                            },
                            onMessage = { context.sendSms(entry.number) },
                            onDelete = { requestDelete(entry) },
                            onCopy = { copyNumber(context, entry.number) },
                            onBlock = { block(entry) },
                        )
                    }
                }
            }
            viewModel.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> Text("통화 기록이 없습니다.", modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogRow(
    entry: CallLogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCall: () -> Unit,
    onHistory: () -> Unit,
    onContact: () -> Unit,
    onMessage: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onBlock: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val missed = entry.type == CallType.Missed
    val titleColor = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val formattedNumber = PhoneNumberFormatter.formatLoose(entry.number).ifEmpty { UNKNOWN_NUMBER }
    val duration = formatDuration(entry.durationSeconds)
    val detail = buildList {
        add(formatClock(entry.dateMillis))
        add(entry.type.label)
        if (duration.isNotEmpty()) add(duration)
    }.joinToString(" · ")

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true }),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = entry.type.icon(),
                    contentDescription = entry.type.label,
                    modifier = Modifier.size(20.dp),
                    tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                InitialAvatar(entry.name ?: formattedNumber, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name ?: formattedNumber,
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (entry.name != null) {
                        Text(
                            formattedNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (entry.number.isNotEmpty()) {
                    CallButton(onClick = onCall)
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionButton(Icons.Filled.History, "기록", onHistory)
                    ActionButton(Icons.Filled.Person, "연락처", onContact, enabled = entry.number.isNotEmpty())
                    ActionButton(Icons.AutoMirrored.Filled.Message, "메시지", onMessage, enabled = entry.number.isNotEmpty())
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("삭제") }, onClick = { menuOpen = false; onDelete() })
            if (entry.number.isNotEmpty()) {
                DropdownMenuItem(text = { Text("번호 복사") }, onClick = { menuOpen = false; onCopy() })
                DropdownMenuItem(text = { Text("차단") }, onClick = { menuOpen = false; onBlock() })
            }
        }
    }
}

@Composable
internal fun CallButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(Icons.Filled.Call, contentDescription = "발신", tint = CallGreen)
    }
}

@Composable
internal fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) CallGreen else MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

internal fun CallType.icon(): ImageVector = when (this) {
    CallType.Incoming -> Icons.AutoMirrored.Filled.CallReceived
    CallType.Outgoing -> Icons.AutoMirrored.Filled.CallMade
    CallType.Missed -> Icons.AutoMirrored.Filled.CallMissed
    CallType.Rejected, CallType.Blocked -> Icons.Filled.Block
    CallType.Voicemail -> Icons.Filled.Voicemail
    CallType.Other -> Icons.Filled.Call
}

private fun copyNumber(context: Context, number: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("phone number", number))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "번호를 복사했습니다.", Toast.LENGTH_SHORT).show()
    }
}
