package com.example.superdialer.kakao

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.superdialer.messages.ConversationRepository
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.ui.groupShape

/** Whether the user has allowed this app to see notifications (a system setting only they can change). */
fun notificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Opens the system screen where notification access is switched on for this app. */
fun openNotificationAccessSettings(context: Context) {
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, KakaoNotificationService::class.java).flattenToString(),
        )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(detail)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            Toast.makeText(context, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** The "카톡" view of the call log tab: chats collected from KakaoTalk's notifications, or how to switch that on. */
@Composable
fun KakaoRoomsList(onOpenRoom: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationAccessGranted(context)) }
    // the user comes back from the system settings after allowing access
    LifecycleResumeEffect(Unit) {
        granted = notificationAccessGranted(context)
        onPauseOrDispose { }
    }
    val rooms = remember(KakaoInbox.messages) { KakaoInbox.rooms() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (!KakaoInbox.enabled || !granted) {
            SetupCard(granted = granted, onEnable = {
                KakaoInbox.switchCollecting(true)
                if (!granted) openNotificationAccessSettings(context)
            }, onOpenSettings = { openNotificationAccessSettings(context) })
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (rooms.isEmpty()) {
                Text(
                    if (KakaoInbox.enabled && granted) "아직 모은 카톡 알림이 없어요.\n카톡이 오면 알림에 뜬 내용이 여기에 쌓여요."
                    else "",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("kakao_room_list"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(rooms.size, key = { rooms[it].name }) { index ->
                        val room = rooms[index]
                        RoomRow(room, groupShape(index, rooms.size), onClick = { onOpenRoom(room.name) })
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { KakaoLinks.openApp(context) }) { Text("카카오톡 열기") }
            if (rooms.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("모은 알림 지우기") }
        }
    }
    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("모은 카톡 알림을 지울까요?") },
            text = { Text("이 폰에 저장된 카톡 알림 내용이 모두 지워집니다. 카카오톡 앱의 대화에는 영향이 없습니다.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; KakaoInbox.clear() }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SetupCard(granted: Boolean, onEnable: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("kakao_setup"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("카톡 알림 모아 보기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "카카오톡은 다른 앱에 대화를 열어 주지 않아서, 카톡이 알림으로 보여 주는 것(보낸 사람·미리보기)만 " +
                    "이 폰 안에 모아서 보여 드려요. 서버로 보내지 않고, 알림에 뜬 것만 보이며, 알림에 답장도 할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!granted) {
                Text(
                    "켜면 설정의 '알림 접근'에서 SuperDialer를 허용해야 해요. 스위치가 회색이면 설정 > 앱 > SuperDialer > ⋮ > " +
                        "'제한된 설정 허용'을 먼저 누르세요(앱을 파일로 설치하면 안드로이드가 막아 둡니다).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!KakaoInbox.enabled) Button(onClick = onEnable) { Text("모아 보기 켜기") }
                if (!granted) OutlinedButton(onClick = onOpenSettings) { Text("알림 접근 설정 열기") }
            }
        }
    }
}

@Composable
private fun RoomRow(room: KakaoRoom, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(room.name, size = 48.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (room.last.outgoing) "나: " else "") + room.last.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                ConversationRepository.timeLabel(room.last.timeMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** One collected chat: its messages as bubbles, a reply box while the chat's notification is still showing, and a way into KakaoTalk. */
@Composable
fun KakaoRoomScreen(room: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages = remember(KakaoInbox.messages, room) { KakaoInbox.messagesIn(room) }
    var text by rememberSaveable { mutableStateOf("") }
    val canReply = KakaoInbox.canReply(room)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(room, onBack) {
            TextButton(onClick = { if (!KakaoInbox.openChat(room)) KakaoLinks.openApp(context) }) { Text("카톡에서 열기") }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (messages.isEmpty()) {
                Text("모은 메시지가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            messages.forEach { message ->
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (message.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (!message.outgoing && message.sender != room) {
                                Text(message.sender, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                ConversationRepository.timeLabel(message.timeMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (canReply) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("답장") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                IconButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        if (KakaoInbox.reply(context, room, text)) text = ""
                        else Toast.makeText(context, "답장을 보내지 못했어요. 카톡에서 직접 답해 주세요.", Toast.LENGTH_SHORT).show()
                    },
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "답장 보내기", tint = MaterialTheme.colorScheme.primary) }
            }
        } else {
            Text(
                "답장은 이 대화의 카톡 알림이 아직 떠 있을 때만 보낼 수 있어요. 알림을 지웠다면 카톡에서 답해 주세요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
