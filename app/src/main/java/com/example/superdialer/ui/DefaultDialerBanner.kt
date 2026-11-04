package com.example.superdialer.ui

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import android.app.role.RoleManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.rememberUpdatedState
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
fun rememberDefaultDialerStatus(onResult: (granted: Boolean) -> Unit = {}): DefaultDialerStatus {
    val context = LocalContext.current
    val latestOnResult by rememberUpdatedState(onResult)
    var isDefault by remember { mutableStateOf(context.isDefaultDialer()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = context.isDefaultDialer() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = context.isDefaultDialer()
        latestOnResult(isDefault)
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
    val context = LocalContext.current
    val status = rememberDefaultDialerStatus { granted ->
        Toast.makeText(
            context,
            if (granted) str(R.string.t_set_as_the_default_phone_app) else str(R.string.t_not_set_as_the_default_phone),
            Toast.LENGTH_SHORT,
        ).show()
    }
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
                str(R.string.t_set_this_app_as_the_default_2),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = status.request) { Text(str(R.string.t_settings)) }
        }
    }
}
