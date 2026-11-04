package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.update.WhatsNew
import com.example.superdialer.update.UpdateInstaller
import com.example.superdialer.update.ReleaseInfo
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superdialer.crash.CrashLog
import com.example.superdialer.update.UpdateChecker
import com.example.superdialer.update.UpdateState

/** "업데이트 확인" and "오류 기록" rows of the app info card. */
@Composable
internal fun AppInfoRows() {
    val context = LocalContext.current
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    var showUpdate by remember { mutableStateOf(false) }
    var showCrashes by remember { mutableStateOf(false) }
    val update = UpdateChecker.state

    ListItem(
        modifier = Modifier.clickable { UpdateChecker.check(context); showUpdate = true },
        colors = transparent,
        headlineContent = { Text(str(R.string.t_check_for_updates)) },
        supportingContent = {
            Text(
                when (update) {
                    is UpdateState.Available -> str(R.string.t_new_version_available, update.info.version)
                    is UpdateState.Ready -> str(R.string.t_new_version_ready_to_install_tap, update.info.version)
                    is UpdateState.Downloading -> str(R.string.t_downloading_the_new_version)
                    else -> str(R.string.t_checks_the_latest_version_on_github)
                },
                color = if (update is UpdateState.Available || update is UpdateState.Ready) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
    )
    ListItem(
        colors = transparent,
        headlineContent = { Text(str(R.string.t_pre_download_new_versions)) },
        supportingContent = { Text(str(R.string.t_when_you_open_the_app_and)) },
        trailingContent = {
            androidx.compose.material3.Switch(checked = AppSettings.autoDownloadUpdates, onCheckedChange = AppSettings::updateAutoDownloadUpdates)
        },
    )
    val crashCount = CrashLog.version.let { CrashLog.entries().size }
    ListItem(
        modifier = Modifier.clickable { showCrashes = true },
        colors = transparent,
        headlineContent = { Text(str(R.string.t_error_log)) },
        supportingContent = { Text(if (crashCount == 0) str(R.string.t_no_saved_errors) else str(R.string.t_crash_records_tap_to_view_or, crashCount)) },
    )

    if (showUpdate) UpdateDialog(update, onDismiss = { showUpdate = false })
    if (showCrashes) CrashDialog(onDismiss = { showCrashes = false })
}

@Composable
internal fun UpdateDialog(state: UpdateState, onDismiss: () -> Unit, onLater: (() -> Unit)? = null) {
    val context = LocalContext.current
    var askPermission by remember { mutableStateOf<ReleaseInfo?>(null) }

    fun update(info: ReleaseInfo) {
        if (info.apkUrl == null) { openUrl(context, info.pageUrl); return }
        if (!UpdateInstaller.canInstall(context)) { askPermission = info; return }
        UpdateChecker.startUpdate(context, info)
    }

    askPermission?.let { info ->
        AlertDialog(
            onDismissRequest = { askPermission = null },
            title = { Text(str(R.string.t_install_permission_needed)) },
            text = { Text(str(R.string.t_to_install_the_app_yourself_allow)) },
            confirmButton = {
                TextButton(onClick = { askPermission = null; UpdateInstaller.openInstallPermissionSettings(context) }) { Text(str(R.string.t_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { askPermission = null }) { Text(str(R.string.cancel)) } },
        )
    }

    val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.t_updates)) },
        text = {
            when (state) {
                UpdateState.Idle, UpdateState.Checking -> CircularProgressIndicator()
                is UpdateState.UpToDate -> Text(str(R.string.t_you_re_on_the_latest_version, state.current))
                is UpdateState.Failed -> Text(state.message)
                is UpdateState.Downloading -> androidx.compose.foundation.layout.Column {
                    Text(str(R.string.t_downloading_3, state.info.version, if (state.percent >= 0) "${state.percent}%" else ""))
                    if (state.percent >= 0) androidx.compose.material3.LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Installing -> Text(str(R.string.t_installing_if_a_confirmation_appears_t))
                is UpdateState.Ready -> Text(str(R.string.t_the_installer_for_version_is_downloade, state.info.version))
                is UpdateState.Available -> androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 320.dp)) {
                    Text(str(R.string.t_version_is_available_you_can_download, state.info.version))
                    val changes = WhatsNew.cleanNotes(state.info.notes)
                    if (changes.isNotEmpty()) {
                        Text(str(R.string.t_what_s_new), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        changes.forEach { Text("• $it", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { update(state.info) }) { Text(str(R.string.t_update_now)) }
                is UpdateState.Ready -> TextButton(onClick = { update(state.info) }) { Text(str(R.string.t_install)) }
                else -> TextButton(onClick = onDismiss) { Text(if (busy) str(R.string.t_in_background) else str(R.string.t_ok)) }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Available ->
                    if (onLater != null) TextButton(onClick = onLater) { Text(str(R.string.t_later)) }
                    else TextButton(onClick = { openUrl(context, state.info.pageUrl); onDismiss() }) { Text(str(R.string.t_release_page)) }
                is UpdateState.Ready -> TextButton(onClick = onLater ?: onDismiss) { Text(str(R.string.t_later)) }
                else -> {}
            }
        },
    )
}

@Composable
private fun CrashDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { CrashLog.entries() }
    val latest = entries.firstOrNull()?.second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.t_error_log)) },
        text = {
            if (latest == null) {
                Text(str(R.string.t_no_saved_errors_if_the_app))
            } else {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                    Text(str(R.string.t_latest_error_total, entries.size), style = MaterialTheme.typography.labelLarge)
                    Text(latest.take(3000), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (latest != null) TextButton(onClick = { share(context, entries.joinToString("\n\n----\n\n") { it.second }); onDismiss() }) { Text(str(R.string.t_share)) }
            else TextButton(onClick = onDismiss) { Text(str(R.string.t_ok)) }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (latest != null) {
                    TextButton(onClick = { copy(context, latest) }) { Text(str(R.string.t_copy)) }
                    TextButton(onClick = { CrashLog.clear(); onDismiss() }) { Text(str(R.string.t_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(str(R.string.t_close)) }
            }
        },
    )
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, str(R.string.t_no_app_can_open_this), Toast.LENGTH_SHORT).show()
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(str(R.string.t_error_log), text))
    Toast.makeText(context, str(R.string.t_copied), Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, str(R.string.t_share_error_log)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, str(R.string.t_no_app_to_share_with), Toast.LENGTH_SHORT).show()
    }
}

/** Top-of-settings card shown while a newer version is available (or being downloaded / installed). */
@Composable
internal fun UpdateBanner() {
    val state = UpdateChecker.state
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Installing -> state.info
        else -> return
    }
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clickable { open = true },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(str(R.string.t_new_version_is_out, info.version), color = MaterialTheme.colorScheme.onPrimaryContainer) },
            supportingContent = {
                Text(
                    when (state) {
                        is UpdateState.Downloading -> str(R.string.t_downloading_4, if (state.percent >= 0) "${state.percent}%" else "")
                        is UpdateState.Installing -> str(R.string.t_installing)
                        is UpdateState.Ready -> str(R.string.t_ready_to_install_tap_to_install)
                        else -> str(R.string.t_tap_to_update)
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        )
    }
    if (open) UpdateDialog(UpdateChecker.state, onDismiss = { open = false })
}

/** Opens the update dialog at app start when a newer version is waiting (place once near the app root). */
@Composable
fun UpdatePromptHost() {
    if (UpdateChecker.prompt == null) return
    UpdateDialog(
        state = UpdateChecker.state,
        onDismiss = UpdateChecker::snoozePrompt,
        onLater = UpdateChecker::snoozePrompt,
    )
}
