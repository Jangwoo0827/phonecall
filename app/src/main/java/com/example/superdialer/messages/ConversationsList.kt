package com.example.superdialer.messages

import android.Manifest
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.PermissionGate
import com.example.superdialer.ui.groupShape

/**
 * The "문자" view of the call log tab: text conversations, newest first. A tap opens the conversation
 * (the messages of that number inside the app). Asks for the SMS permission first.
 */
@Composable
fun ConversationsList(
    query: String,
    onOpen: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationsViewModel = viewModel(),
) {
    PermissionGate(
        required = listOf(Manifest.permission.READ_SMS),
        rationale = "문자 대화를 보려면 문자 읽기 권한이 필요합니다.\n(채팅+(RCS)·카카오톡 대화는 보이지 않습니다)",
        modifier = modifier,
    ) {
        LifecycleResumeEffect(Unit) {
            viewModel.refresh()
            onPauseOrDispose { }
        }
        val shown = remember(viewModel.conversations, query) {
            viewModel.conversations.filter { ConversationRepository.matches(it, query) }
        }
        Box(modifier = modifier.fillMaxSize()) {
            when {
                shown.isEmpty() && viewModel.loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                shown.isEmpty() -> Text(
                    if (query.isBlank()) "문자 대화가 없습니다." else "조건에 맞는 문자 대화가 없습니다.",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("conversation_list"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(shown, key = { _, c -> c.threadId }) { index, conversation ->
                        ConversationRow(conversation, groupShape(index, shown.size), onClick = { onOpen(conversation) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialAvatar(conversation.title, size = 48.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    conversation.snippet.ifBlank { "(내용 없음)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conversation.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    ConversationRepository.timeLabel(conversation.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (conversation.unread) {
                    Box(
                        modifier = Modifier.padding(top = 6.dp).size(10.dp),
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp)) {}
                    }
                }
            }
        }
    }
}
