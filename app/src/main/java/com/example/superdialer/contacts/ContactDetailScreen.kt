package com.example.superdialer.contacts

import com.example.superdialer.ui.theme.starColor
import com.example.superdialer.ui.theme.callGreen
import com.example.superdialer.ui.groupShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superdialer.calllog.CallLogViewModel
import com.example.superdialer.calllog.CallType
import com.example.superdialer.calllog.formatDateTime
import com.example.superdialer.calllog.formatDuration
import com.example.superdialer.calllog.icon
import com.example.superdialer.calllog.numberKey
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.messages.ActivityChips
import com.example.superdialer.messages.MessagesPane
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.rememberDialAction
import com.example.superdialer.ui.sendSms

private const val MAX_HISTORY = 20

@Composable
fun ContactDetailScreen(
    viewModel: ContactsViewModel,
    callLogViewModel: CallLogViewModel,
    contactId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dial = rememberDialAction()

    LaunchedEffect(contactId) {
        viewModel.loadDetail(contactId)
        if (context.hasPermission(Manifest.permission.READ_CALL_LOG)) callLogViewModel.refresh()
    }
    val detail = viewModel.detail?.takeIf { it.id == contactId }

    // Starring writes to contacts, which needs WRITE_CONTACTS: ask once and then finish the toggle.
    var pendingStar by remember { mutableStateOf<Boolean?>(null) }
    fun applyStar(starred: Boolean) = viewModel.toggleStar(contactId, starred) { ok ->
        if (!ok) Toast.makeText(context, "즐겨찾기를 변경하지 못했습니다.", Toast.LENGTH_SHORT).show()
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val wanted = pendingStar
        pendingStar = null
        if (granted && wanted != null) {
            applyStar(wanted)
        } else {
            Toast.makeText(context, "즐겨찾기를 바꾸려면 연락처 수정 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }
    fun toggleStar(current: Boolean) {
        if (context.hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            applyStar(!current)
        } else {
            pendingStar = !current
            writePermission.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("", onBack) {
            if (detail != null) {
                IconButton(onClick = { toggleStar(detail.starred) }) {
                    Icon(
                        if (detail.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (detail.starred) "즐겨찾기 해제" else "즐겨찾기 추가",
                        tint = if (detail.starred) starColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { shareContact(context, detail) }) {
                    Icon(Icons.Filled.Share, contentDescription = "공유")
                }
                IconButton(onClick = { editContact(context, detail.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "편집")
                }
            }
        }

        if (detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val keys = remember(detail) { detail.numbers.map { numberKey(it.number) }.toSet() }
        val history = remember(callLogViewModel.entries, keys) {
            callLogViewModel.entries.filter { numberKey(it.number) in keys }.take(MAX_HISTORY)
        }

        var showMessages by rememberSaveable { mutableStateOf(false) }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(top = 12.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (detail.photo != null) {
                        Image(
                            bitmap = detail.photo.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        InitialAvatar(detail.name, size = 112.dp, fontSize = 46.sp)
                    }
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(detail.name, fontSize = 28.sp, style = MaterialTheme.typography.headlineSmall)
                        if (detail.starred) {
                            Icon(Icons.Filled.Star, contentDescription = "즐겨찾기", tint = starColor())
                        }
                    }
                }
            }
            itemsIndexed(detail.numbers, key = { _, e -> e.number }) { index, entry ->
                ListItem(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
                        .clip(groupShape(index, detail.numbers.size))
                        .clickable { dial(entry.number) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    headlineContent = { Text(PhoneNumberFormatter.formatLoose(entry.number)) },
                    supportingContent = { Text(entry.label) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { context.sendSms(entry.number) }) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "문자 보내기")
                            }
                            IconButton(onClick = { dial(entry.number) }) {
                                Icon(Icons.Filled.Call, contentDescription = "발신", tint = callGreen())
                            }
                        }
                    },
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                ActivityChips(showMessages = showMessages, callCount = history.size, onSelect = { showMessages = it })
            }
            if (showMessages) {
                item { MessagesPane(numbers = detail.numbers.map { it.number }) }
            } else {
                if (history.isEmpty()) {
                    item {
                        Text(
                            "통화 기록이 없습니다.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(history, key = { "h-${it.id}" }) { call ->
                    val missed = call.type == CallType.Missed
                    val tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    val duration = formatDuration(call.durationSeconds)
                    ListItem(
                        leadingContent = { Icon(call.type.icon(), contentDescription = call.type.label, tint = tint) },
                        headlineContent = { Text(call.type.label, color = if (missed) tint else Color.Unspecified) },
                        supportingContent = { Text(formatDateTime(call.dateMillis)) },
                        trailingContent = { if (duration.isNotEmpty()) Text(duration) },
                    )
                }
            }
        }
    }
}

private fun shareContact(context: Context, detail: ContactDetail) {
    val text = buildString {
        append(detail.name)
        detail.numbers.forEach { append("\n").append(PhoneNumberFormatter.formatLoose(it.number)) }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, "연락처 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "공유할 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun editContact(context: Context, contactId: Long) {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    try {
        context.startActivity(Intent(Intent.ACTION_EDIT, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "연락처 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}
