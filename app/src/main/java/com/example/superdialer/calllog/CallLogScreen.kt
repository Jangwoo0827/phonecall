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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
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
    onOpenBlocked: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionGate(
        required = listOf(Manifest.permission.READ_CALL_LOG),
        optional = listOf(Manifest.permission.WRITE_CALL_LOG, Manifest.permission.READ_CONTACTS),
        rationale = "최근 통화 기록을 보려면 통화 기록 권한이 필요합니다.\n연락처 권한을 허용하면 저장된 이름도 표시됩니다.",
        modifier = modifier,
    ) {
        CallLogList(viewModel, onOpenHistory, onOpenContact, onOpenBlocked, onOpenSettings, modifier)
    }
}

@Composable
private fun CallLogList(
    viewModel: CallLogViewModel,
    onOpenHistory: (Long) -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dial = rememberDialAction()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // Deleting needs WRITE_CALL_LOG: ask once, then run the action that was waiting.
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingDelete
        pendingDelete = null
        if (granted && action != null) action() else toast("삭제하려면 통화 기록 쓰기 권한이 필요합니다.")
    }

    fun withWritePermission(action: () -> Unit) {
        if (context.hasPermission(Manifest.permission.WRITE_CALL_LOG)) {
            action()
        } else {
            pendingDelete = action
            writePermission.launch(Manifest.permission.WRITE_CALL_LOG)
        }
    }

    fun onDeleted(ok: Boolean) {
        if (!ok) toast("삭제하지 못했습니다.")
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

    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    // Tapping a row only expands it; the green button is the only thing that dials.
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    val groups = viewModel.groups
    val days = remember(groups) {
        val zone = ZoneId.systemDefault()
        groups.groupBy { Instant.ofEpochMilli(it.latest.dateMillis).atZone(zone).toLocalDate() }.values.toList()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (viewModel.selecting) {
            SelectionBar(
                count = viewModel.selected.size,
                onClose = viewModel::stopSelecting,
                onSelectAll = viewModel::selectAll,
                onDelete = { withWritePermission { viewModel.deleteSelected(::onDeleted) } },
            )
        } else {
            SearchBar(
                query = viewModel.query,
                onQueryChange = viewModel::onQueryChange,
                onSelect = { groups.firstOrNull()?.let { viewModel.startSelecting(it.id) } },
                onDeleteAll = { confirmDeleteAll = true },
                onOpenBlocked = onOpenBlocked,
                onOpenSettings = onOpenSettings,
            )
            FilterRow(selected = viewModel.filter, onSelect = viewModel::onFilterChange)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                groups.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    days.forEach { dayGroups ->
                        item(key = "day-${dayGroups.first().id}") {
                            DayHeader(formatDayHeader(dayGroups.first().latest.dateMillis))
                        }
                        items(dayGroups, key = { it.id }) { group ->
                            CallLogRow(
                                group = group,
                                selecting = viewModel.selecting,
                                checked = group.id in viewModel.selected,
                                expanded = expandedId == group.id,
                                onClick = {
                                    if (viewModel.selecting) viewModel.toggleSelected(group.id)
                                    else expandedId = if (expandedId == group.id) null else group.id
                                },
                                onCall = { dial(group.latest.number) },
                                onHistory = { onOpenHistory(group.latest.id) },
                                onContact = {
                                    val id = group.latest.contactId
                                    if (id != null) onOpenContact(id) else context.addContact(group.latest.number)
                                },
                                onMessage = { context.sendSms(group.latest.number) },
                                onSelect = { viewModel.startSelecting(group.id) },
                                onDelete = { withWritePermission { viewModel.deleteGroup(group, ::onDeleted) } },
                                onCopy = { copyNumber(context, group.latest.number) },
                                onBlock = { block(group.latest) },
                            )
                        }
                    }
                }
                viewModel.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                viewModel.entries.isNotEmpty() ->
                    Text("조건에 맞는 통화 기록이 없습니다.", modifier = Modifier.align(Alignment.Center))
                else -> Text("통화 기록이 없습니다.", modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("전체 삭제") },
            text = { Text("모든 통화 기록을 삭제할까요? 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    withWritePermission { viewModel.deleteAll(::onDeleted) }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("이름, 초성, 번호 검색") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "검색어 지우기")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "더보기")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("선택 삭제") }, onClick = { menuOpen = false; onSelect() })
                DropdownMenuItem(text = { Text("전체 삭제") }, onClick = { menuOpen = false; onDeleteAll() })
                DropdownMenuItem(text = { Text("차단 관리") }, onClick = { menuOpen = false; onOpenBlocked() })
                DropdownMenuItem(text = { Text("설정") }, onClick = { menuOpen = false; onOpenSettings() })
            }
        }
    }
}

@Composable
private fun FilterRow(selected: CallFilter, onSelect: (CallFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CallFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "선택 취소") }
        Text("${count}개 선택", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onSelectAll) { Text("전체 선택") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "삭제") }
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
    group: CallGroup,
    selecting: Boolean,
    checked: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onHistory: () -> Unit,
    onContact: () -> Unit,
    onMessage: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onBlock: () -> Unit,
) {
    val entry = group.latest
    var menuOpen by remember { mutableStateOf(false) }
    val missed = entry.type == CallType.Missed
    val titleColor = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val formattedNumber = PhoneNumberFormatter.formatLoose(entry.number).ifEmpty { UNKNOWN_NUMBER }
    val title = (entry.name ?: formattedNumber) + if (group.count > 1) " (${group.count})" else ""
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
                .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    Checkbox(checked = checked, onCheckedChange = { onClick() }, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        imageVector = entry.type.icon(),
                        contentDescription = entry.type.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                InitialAvatar(entry.name ?: formattedNumber, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
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
                if (entry.number.isNotEmpty() && !selecting) {
                    CallButton(onClick = onCall)
                }
            }
            if (expanded && !selecting) {
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
            DropdownMenuItem(text = { Text("선택") }, onClick = { menuOpen = false; onSelect() })
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
