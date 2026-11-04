package com.example.superdialer.browser

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.superdialer.ui.ScreenHeader

private fun SitePermission.icon(): ImageVector = when (this) {
    SitePermission.Camera -> Icons.Filled.Videocam
    SitePermission.Microphone -> Icons.Filled.Mic
    SitePermission.Location -> Icons.Filled.LocationOn
}

/** The site name shown to the user: the host of the origin. */
internal fun hostOf(origin: String): String = origin.substringAfter("://").trimEnd('/')

/**
 * "This site wants your camera / microphone / location": a bottom sheet with a clear choice. Swiping it away counts as
 * "not now" (nothing is remembered). Private tabs offer no "always" choices because nothing may be stored for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SitePermissionSheet(request: SitePermissionRequest, onAnswer: (allow: Boolean, remember: Boolean) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onAnswer(false, false) }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).testTag("site_permission_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                request.permissions.forEach { permission ->
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(permission.icon(), contentDescription = permission.label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(hostOf(request.origin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                str(R.string.t_this_site_is_asking_to_use, request.permissions.joinToString(" · ") { it.label }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onAnswer(true, false) }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.t_allow_this_time)) }
            if (!request.isPrivate) {
                FilledTonalButton(onClick = { onAnswer(true, true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(str(R.string.t_always_allow_on_this_site))
                }
            }
            OutlinedButton(onClick = { onAnswer(false, !request.isPrivate) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (request.isPrivate) str(R.string.t_don_t_allow) else str(R.string.t_block))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (request.isPrivate) str(R.string.t_choices_are_not_saved_in_private)
                else str(R.string.t_you_can_change_this_later_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 설정 > 브라우저 > 사이트 권한: the sites that have a remembered answer, with a way to forget each one. */
@Composable
fun SitePermissionsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var confirmAll by remember { mutableStateOf(false) }
    val origins = SitePermissions.origins
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(str(R.string.t_site_permissions), onBack) {
            if (origins.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text(str(R.string.t_reset_all)) }
        }
        Text(
            str(R.string.t_your_choices_when_a_site_wants),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (origins.isEmpty()) {
            Text(
                str(R.string.t_no_saved_sites),
                modifier = Modifier.padding(24.dp).testTag("no_site_permissions"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(origins, key = { it }) { origin ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(hostOf(origin), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        SitePermissions.answers(origin).forEach { (permission, decision) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(permission.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(permission.label, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                Text(
                                    decision.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (decision == SiteDecision.Allow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                                IconButton(onClick = { SitePermissions.resetPermission(origin, permission) }) {
                                    Icon(Icons.Filled.Close, contentDescription = str(R.string.t_reset, permission.label))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(str(R.string.t_reset_all)) },
            text = { Text(str(R.string.t_clear_the_choices_for_all_saved)) },
            confirmButton = { TextButton(onClick = { confirmAll = false; SitePermissions.resetAll() }) { Text(str(R.string.t_reset_2)) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(str(R.string.cancel)) } },
        )
    }
}
