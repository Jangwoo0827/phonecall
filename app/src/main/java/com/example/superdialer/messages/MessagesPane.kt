package com.example.superdialer.messages

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Image
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import android.widget.Toast
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
                    str(R.string.t_showing_texts_needs_the_permission_to),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = { launcher.launch(Manifest.permission.READ_SMS) }) { Text(str(R.string.t_allow_permission)) }
                if (asked) TextButton(onClick = { context.openAppSettings() }) { Text(str(R.string.t_open_settings)) }
            }
            return@Column
        }

        Composer(numbers.firstOrNull().orEmpty())

        val sentHere = SentSms.forNumbers(numbers)
        val messages = SentSms.merge(viewModel.messages, sentHere)
        when {
            messages.isEmpty() && viewModel.loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            messages.isEmpty() -> Text(
                str(R.string.t_no_texts_yet_rcs_and_kakaotalk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> {
                // Where the list starts, so a missing recent chat is obvious: Samsung "채팅+"/RCS chats are not readable by apps.
                Text(
                    str(R.string.t_latest_text_chats_over_rcs_don, formatDateTime(messages.first().dateMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    messages.forEach { message ->
                        Bubble(message, onRetry = {
                            numbers.firstOrNull()?.let { SmsSender.send(context, it, message.body, retryId = message.sentId) }
                        })
                    }
                }
                TextButton(
                    onClick = { numbers.firstOrNull()?.let(context::sendSms) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(str(R.string.t_continue_in_the_messages_app)) }
            }
        }
    }
}

/** Write and send a text from here. Sent texts are kept by this app (a non-default SMS app cannot add them to the system inbox). */
@Composable
private fun Composer(number: String) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var pendingSend by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A picture/video is not sent from here: it is copied and the phone's own message composer opens with it attached.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        scope.launch {
            val staged = try {
                withContext(Dispatchers.IO) { MmsShare.stage(context, picked) }
            } catch (e: MmsShare.TooLargeException) {
                Toast.makeText(context, str(R.string.t_the_file_is_too_big_only), Toast.LENGTH_LONG).show()
                return@launch
            } catch (e: java.io.IOException) {
                Toast.makeText(context, str(R.string.t_couldn_t_load_the_photo), Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (MmsShare.send(context, number, staged, text)) text = ""
            else Toast.makeText(context, str(R.string.t_can_t_open_the_messages_app), Toast.LENGTH_SHORT).show()
        }
    }

    fun doSend(body: String) {
        if (SmsSender.send(context, number, body)) text = ""
        else Toast.makeText(context, str(R.string.t_couldn_t_send_the_text), Toast.LENGTH_SHORT).show()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val body = pendingSend
        pendingSend = null
        if (granted && body != null) doSend(body)
        else Toast.makeText(context, str(R.string.t_sending_texts_needs_the_permission_to), Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(str(R.string.t_send_text)) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            IconButton(
                enabled = number.isNotBlank(),
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            ) { Icon(Icons.Filled.Image, contentDescription = str(R.string.t_send_photo), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(
                enabled = text.isNotBlank() && number.isNotBlank(),
                onClick = {
                    if (context.hasPermission(Manifest.permission.SEND_SMS)) {
                        doSend(text)
                    } else {
                        pendingSend = text
                        permission.launch(Manifest.permission.SEND_SMS)
                    }
                },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = str(R.string.t_send), tint = MaterialTheme.colorScheme.primary) }
        }
        Text(
            str(R.string.t_texts_sent_from_here_are_only),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Bubble(message: SmsMessage, onRetry: () -> Unit) {
    val mine = message.outgoing
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.attachments.isNotEmpty()) MmsAttachments(message.attachments)
                if (message.body.isNotBlank() || message.attachments.isEmpty()) {
                    Text(message.body, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    formatDateTime(message.dateMillis) + (if (message.isMms) " · MMS" else "") + when {
                        message.sentId != 0L && message.status == SentMessage.SENDING -> str(R.string.t_sending)
                        message.sentId != 0L && message.status == SentMessage.FAILED -> str(R.string.t_failed)
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.status == SentMessage.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (message.sentId != 0L && message.status == SentMessage.FAILED) {
                    TextButton(onClick = onRetry) { Text(str(R.string.t_send_again)) }
                }
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
            label = { Text(if (callCount > 0) str(R.string.t_calls_2, callCount) else str(R.string.t_calls_3)) },
            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        androidx.compose.material3.FilterChip(
            selected = showMessages,
            onClick = { onSelect(true) },
            label = { Text(str(R.string.t_messages)) },
            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}
