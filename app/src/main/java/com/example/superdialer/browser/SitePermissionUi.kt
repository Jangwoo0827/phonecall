package com.example.superdialer.browser

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
                "이 사이트가 ${request.permissions.joinToString(" · ") { it.label }} 사용을 요청하고 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onAnswer(true, false) }, modifier = Modifier.fillMaxWidth()) { Text("이번만 허용") }
            if (!request.isPrivate) {
                FilledTonalButton(onClick = { onAnswer(true, true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("이 사이트에서 항상 허용")
                }
            }
            OutlinedButton(onClick = { onAnswer(false, !request.isPrivate) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (request.isPrivate) "허용 안 함" else "차단")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (request.isPrivate) "비공개 탭에서는 선택을 저장하지 않아요."
                else "나중에 설정 > 브라우저 > 사이트 권한에서 바꿀 수 있어요.",
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
        ScreenHeader("사이트 권한", onBack) {
            if (origins.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text("모두 초기화") }
        }
        Text(
            "사이트가 카메라·마이크·위치를 쓰려고 할 때 한 선택이 여기 저장돼요. 초기화하면 다음에 그 사이트가 요청할 때 다시 물어봐요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (origins.isEmpty()) {
            Text(
                "저장된 사이트가 없어요.",
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
                                    Icon(Icons.Filled.Close, contentDescription = "${permission.label} 초기화")
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
            title = { Text("모두 초기화") },
            text = { Text("저장된 모든 사이트의 선택을 지울까요? 다음에 사이트가 요청하면 다시 물어봐요.") },
            confirmButton = { TextButton(onClick = { confirmAll = false; SitePermissions.resetAll() }) { Text("초기화") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("취소") } },
        )
    }
}
