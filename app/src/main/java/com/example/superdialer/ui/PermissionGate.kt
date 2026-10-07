package com.example.superdialer.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * Shows [content] once every [required] permission is granted; otherwise shows [rationale] with a
 * request button (and a settings shortcut after the first request). [optional] permissions are
 * requested together with the required ones but never block the content.
 */
@Composable
fun PermissionGate(
    required: List<String>,
    rationale: String,
    modifier: Modifier = Modifier,
    optional: List<String> = emptyList(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    fun allGranted() = required.all(context::hasPermission)

    var granted by remember { mutableStateOf(allGranted()) }
    var requested by rememberSaveable { mutableStateOf(false) }

    // The user may flip the permission in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = allGranted() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = allGranted()
        requested = true
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = rationale, textAlign = TextAlign.Center)
            Button(
                onClick = { launcher.launch((required + optional).toTypedArray()) },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("권한 허용") }
            if (requested) {
                TextButton(onClick = { context.openAppSettings() }) { Text("설정 열기") }
            }
        }
    }
}
