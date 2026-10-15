package com.example.superdialer.messages

import com.example.superdialer.kakao.KakaoActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.ui.rememberDialAction
import com.example.superdialer.ui.theme.callGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One conversation with [number], opened from the "문자" list: who it is, a call button, the messages and the compose box. */
@Composable
fun MessagesScreen(
    number: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dial = rememberDialAction()
    val contact by produceState<ConversationRepository.ContactName?>(null, number) {
        value = withContext(Dispatchers.IO) { ConversationRepository(context).lookupName(number) }
    }
    val formatted = PhoneNumberFormatter.formatLoose(number).ifEmpty { number }
    val title = contact?.name ?: formatted

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("", onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialAvatar(title, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (contact?.name != null) {
                    Text(formatted, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { dial(number) }) { Icon(Icons.Filled.Call, contentDescription = "발신", tint = callGreen()) }
        }
        KakaoActions(contact?.id)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MessagesPane(numbers = listOf(number))
        }
    }
}
