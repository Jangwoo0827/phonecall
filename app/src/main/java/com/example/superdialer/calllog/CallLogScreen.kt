package com.example.superdialer.calllog

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.example.superdialer.kakao.KakaoRoomsList
import com.example.superdialer.messages.ConversationsList
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.FilterChipDefaults
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
import com.example.superdialer.ui.groupShape
import com.example.superdialer.ui.theme.callGreen
import com.example.superdialer.ui.theme.incomingColor
import com.example.superdialer.ui.theme.outgoingColor
import androidx.compose.ui.graphics.Shape
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.rememberDialAction
import com.example.superdialer.ui.sendSms
import com.example.superdialer.ui.shareText
import java.time.Instant
import java.time.ZoneId

internal val UNKNOWN_NUMBER: String get() = str(R.string.t_no_caller_info)

@Composable
fun CallLogScreen(
    viewModel: CallLogViewModel,
    onOpenHistory: (entryId: Long) -> Unit,
    onOpenMessages: (entryId: Long) -> Unit,
    onOpenThread: (number: String) -> Unit,
    onOpenKakao: (room: String) -> Unit,
    onOpenContact: (contactId: Long) -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionGate(
        required = listOf(Manifest.permission.READ_CALL_LOG),
        optional = listOf(Manifest.permission.WRITE_CALL_LOG, Manifest.permission.READ_CONTACTS),
        rationale = str(R.string.t_showing_recent_calls_needs_the_call),
        modifier = modifier,
    ) {
        CallLogList(viewModel, onOpenHistory, onOpenMessages, onOpenThread, onOpenKakao, onOpenContact, onOpenBlocked, onOpenStats, modifier)
    }
}

@Composable
private fun CallLogList(
    viewModel: CallLogViewModel,
    onOpenHistory: (Long) -> Unit,
    onOpenMessages: (Long) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenKakao: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dial = rememberDialAction()
    // The "문자" chip swaps the calls for the text conversations (same search box).
    var showMessages by rememberSaveable { mutableStateOf(false) }
    // ... and the "카톡" chip shows the chats collected from KakaoTalk's notifications.
    var showKakao by rememberSaveable { mutableStateOf(false) }

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
        if (granted && action != null) action() else toast(str(R.string.t_deleting_needs_the_call_log_write))
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
        if (!ok) toast(str(R.string.t_couldn_t_delete))
    }

    fun block(entry: CallLogEntry) = viewModel.block(entry.number) { result ->
        toast(
            when (result) {
                BlockResult.Blocked -> str(R.string.t_blocked)
                BlockResult.NotAllowed -> str(R.string.t_set_this_app_as_the_default)
                BlockResult.Failed -> str(R.string.t_couldn_t_block)
            }
        )
    }

    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    // Tapping a row only expands it; the green button is the only thing that dials.
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var memoEntry by remember { mutableStateOf<CallLogEntry?>(null) }
    memoEntry?.let { entry ->
        NoteDialog(
            title = str(R.string.t_call_note),
            initial = CallNotes.noteFor(entry)?.text.orEmpty(),
            onSave = { CallNotes.setFor(entry, it) },
            onDismiss = { memoEntry = null },
        )
    }

    // Computed here from the states themselves (see CallLogViewModel.groups), cached while none of them changes.
    val groups = remember(viewModel.entries, viewModel.filter, viewModel.query) { viewModel.groups }
    // A list keeps showing the item it was on when items appear above it, so a call that arrives while the user is
    // looking at the top (or never scrolled) would sit just out of view. Until the user scrolls, stay on the newest.
    val listState = rememberLazyListState()
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) userScrolled = true }
    }
    val newestId = groups.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (!userScrolled) listState.scrollToItem(0)
    }
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
                onOpenStats = onOpenStats,
            )
            FilterRow(
                selected = viewModel.filter,
                messagesSelected = showMessages,
                kakaoSelected = showKakao,
                onSelect = { showMessages = false; showKakao = false; viewModel.onFilterChange(it) },
                onMessages = { showMessages = true; showKakao = false },
                onKakao = { showKakao = true; showMessages = false },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showKakao -> KakaoRoomsList(onOpenRoom = onOpenKakao)
                showMessages -> ConversationsList(
                    query = viewModel.query,
                    onOpen = { onOpenThread(it.number) },
                )
                groups.isNotEmpty() -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    days.forEach { dayGroups ->
                        item(key = "day-${dayGroups.first().id}") {
                            DayHeader(formatDayHeader(dayGroups.first().latest.dateMillis))
                        }
                        itemsIndexed(dayGroups, key = { _, g -> g.id }) { index, group ->
                            CallLogRow(
                                shape = groupShape(index, dayGroups.size),
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
                                onMessage = { onOpenMessages(group.latest.id) },
                                onMemo = { memoEntry = group.latest },
                                onSelect = { viewModel.startSelecting(group.id) },
                                onDelete = { withWritePermission { viewModel.deleteGroup(group, ::onDeleted) } },
                                onCopy = { copyNumber(context, group.latest.number) },
                                onShare = { context.shareText(PhoneNumberFormatter.formatLoose(group.latest.number)) },
                                onBlock = { block(group.latest) },
                            )
                        }
                    }
                }
                viewModel.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                viewModel.entries.isNotEmpty() ->
                    Text(str(R.string.t_no_calls_match), modifier = Modifier.align(Alignment.Center))
                else -> Text(str(R.string.t_no_call_history), modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(str(R.string.t_delete_all)) },
            text = { Text(str(R.string.t_delete_all_call_history_this_can)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    withWritePermission { viewModel.deleteAll(::onDeleted) }
                }) { Text(str(R.string.t_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(str(R.string.cancel)) } },
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
    onOpenStats: () -> Unit,
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
            placeholder = { Text(str(R.string.t_search_name_initials_number)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = str(R.string.t_clear_search))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = str(R.string.t_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(str(R.string.t_delete_selected)) }, onClick = { menuOpen = false; onSelect() })
                DropdownMenuItem(text = { Text(str(R.string.t_delete_all)) }, onClick = { menuOpen = false; onDeleteAll() })
                DropdownMenuItem(text = { Text(str(R.string.t_call_statistics)) }, onClick = { menuOpen = false; onOpenStats() })
                DropdownMenuItem(text = { Text(str(R.string.t_blocked_numbers)) }, onClick = { menuOpen = false; onOpenBlocked() })
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: CallFilter,
    messagesSelected: Boolean,
    kakaoSelected: Boolean,
    onSelect: (CallFilter) -> Unit,
    onMessages: () -> Unit,
    onKakao: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        CallFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected && !messagesSelected && !kakaoSelected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                colors = colors,
            )
        }
        FilterChip(selected = messagesSelected, onClick = onMessages, label = { Text(str(R.string.t_texts)) }, colors = colors)
        FilterChip(selected = kakaoSelected, onClick = onKakao, label = { Text(str(R.string.t_kakaotalk)) }, colors = colors)
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
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = str(R.string.t_cancel_selection)) }
        Text(str(R.string.t_selected, count), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onSelectAll) { Text(str(R.string.t_select_all)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = str(R.string.t_delete)) }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogRow(
    shape: Shape,
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
    onShare: () -> Unit,
    onBlock: () -> Unit,
    onMemo: () -> Unit,
) {
    val entry = group.latest
    val memo = CallNotes.noteFor(entry)?.text
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
                .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
                .clip(shape)
                .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
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
                        tint = entry.type.tint(),
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
                    if (memo != null) {
                        Text(
                            str(R.string.t_note, memo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (entry.number.isNotEmpty() && !selecting) {
                    CallButton(onClick = onCall)
                }
            }
            AnimatedVisibility(
                visible = expanded && !selecting,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionButton(Icons.Filled.History, str(R.string.t_history_2), onHistory)
                    ActionButton(Icons.Filled.Person, str(R.string.shortcut_contacts), onContact, enabled = entry.number.isNotEmpty())
                    ActionButton(Icons.AutoMirrored.Filled.Message, str(R.string.t_messages), onMessage, enabled = entry.number.isNotEmpty())
                    ActionButton(Icons.Filled.EditNote, str(R.string.t_note_2), onMemo, enabled = entry.number.isNotEmpty())
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(str(R.string.t_select)) }, onClick = { menuOpen = false; onSelect() })
            DropdownMenuItem(text = { Text(str(R.string.t_delete)) }, onClick = { menuOpen = false; onDelete() })
            if (entry.number.isNotEmpty()) {
                DropdownMenuItem(text = { Text(str(R.string.t_copy_number)) }, onClick = { menuOpen = false; onCopy() })
                DropdownMenuItem(text = { Text(str(R.string.t_share_number)) }, onClick = { menuOpen = false; onShare() })
                DropdownMenuItem(text = { Text(str(R.string.t_block)) }, onClick = { menuOpen = false; onBlock() })
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
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(Icons.Filled.Call, contentDescription = str(R.string.t_outgoing), tint = callGreen())
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
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) callGreen() else MaterialTheme.colorScheme.outline,
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
        Toast.makeText(context, str(R.string.t_number_copied), Toast.LENGTH_SHORT).show()
    }
}

/** Icon color by call type: incoming green, outgoing blue, missed red. */
@Composable
internal fun CallType.tint(): Color = when (this) {
    CallType.Incoming -> incomingColor()
    CallType.Outgoing -> outgoingColor()
    CallType.Missed -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
