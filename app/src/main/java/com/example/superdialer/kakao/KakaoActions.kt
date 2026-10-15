package com.example.superdialer.kakao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "카카오톡 채팅 / 보이스톡 / 페이스톡" buttons for a contact who is a KakaoTalk friend (see [KakaoLinks]). Shows nothing
 * for anyone else, and nothing when contacts cannot be read.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KakaoActions(contactId: Long?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val actions by produceState(emptyList<KakaoAction>(), contactId) {
        value = if (contactId == null) emptyList() else withContext(Dispatchers.IO) { KakaoLinks.actionsFor(context, contactId) }
    }
    if (actions.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("kakao_actions"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { action ->
            AssistChip(
                onClick = { KakaoLinks.open(context, action) },
                label = { Text(KakaoLinks.buttonLabel(action)) },
                leadingIcon = { Icon(iconFor(action.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            )
        }
    }
}

private fun iconFor(kind: KakaoKind): ImageVector = when (kind) {
    KakaoKind.Voice -> Icons.Filled.Call
    KakaoKind.Video -> Icons.Filled.Videocam
    else -> Icons.Filled.ChatBubble
}
