package com.example.superdialer.ui

import android.app.role.RoleManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

fun Context.isDefaultDialer(): Boolean =
    getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

class DefaultDialerStatus(val isDefault: Boolean, val request: () -> Unit)

/** Tracks whether this app holds the Phone role and launches the system role-request dialog. */
@Composable
fun rememberDefaultDialerStatus(): DefaultDialerStatus {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(context.isDefaultDialer()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = context.isDefaultDialer() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = context.isDefaultDialer()
    }
    return remember(isDefault) {
        DefaultDialerStatus(isDefault) {
            val role = context.getSystemService(RoleManager::class.java)
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                launcher.launch(role.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
        }
    }
}

/** Shown while the app is not the default Phone app: incoming-call and in-call screens need the role. */
@Composable
fun DefaultDialerBanner(modifier: Modifier = Modifier) {
    val status = rememberDefaultDialerStatus()
    if (status.isDefault) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "기본 전화 앱으로 설정하면 수신·통화 화면을 사용할 수 있어요.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = status.request) { Text("설정") }
        }
    }
}
