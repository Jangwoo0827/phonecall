package com.example.superdialer.settings

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
        headlineContent = { Text("업데이트 확인") },
        supportingContent = {
            Text(
                when (update) {
                    is UpdateState.Available -> "새 버전 ${update.info.version} 이 있습니다"
                    is UpdateState.Ready -> "새 버전 ${update.info.version} 설치 준비됨 · 눌러서 설치"
                    is UpdateState.Downloading -> "새 버전 받는 중…"
                    else -> "GitHub에 올라온 최신 버전을 확인합니다"
                },
                color = if (update is UpdateState.Available || update is UpdateState.Ready) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
    )
    ListItem(
        colors = transparent,
        headlineContent = { Text("새 버전 미리 받아 두기") },
        supportingContent = { Text("앱을 열 때 새 버전이 있으면 Wi-Fi에서 설치 파일을 받아 두고 알려 줍니다. 설치는 직접 누릅니다") },
        trailingContent = {
            androidx.compose.material3.Switch(checked = AppSettings.autoDownloadUpdates, onCheckedChange = AppSettings::updateAutoDownloadUpdates)
        },
    )
    val crashCount = CrashLog.version.let { CrashLog.entries().size }
    ListItem(
        modifier = Modifier.clickable { showCrashes = true },
        colors = transparent,
        headlineContent = { Text("오류 기록") },
        supportingContent = { Text(if (crashCount == 0) "저장된 오류가 없습니다" else "앱이 멈춘 기록 ${crashCount}건 · 눌러서 보기·공유") },
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
            title = { Text("설치 허용 필요") },
            text = { Text("앱을 직접 설치하려면 이 앱에 '출처를 알 수 없는 앱 설치'를 허용해야 합니다. 다음 화면에서 SuperDialer를 허용한 뒤 돌아와서 다시 눌러 주세요.") },
            confirmButton = {
                TextButton(onClick = { askPermission = null; UpdateInstaller.openInstallPermissionSettings(context) }) { Text("설정 열기") }
            },
            dismissButton = { TextButton(onClick = { askPermission = null }) { Text("취소") } },
        )
    }

    val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("업데이트") },
        text = {
            when (state) {
                UpdateState.Idle, UpdateState.Checking -> CircularProgressIndicator()
                is UpdateState.UpToDate -> Text("최신 버전(${state.current})을 쓰고 있습니다.")
                is UpdateState.Failed -> Text(state.message)
                is UpdateState.Downloading -> androidx.compose.foundation.layout.Column {
                    Text("${state.info.version} 받는 중… ${if (state.percent >= 0) "${state.percent}%" else ""}")
                    if (state.percent >= 0) androidx.compose.material3.LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Installing -> Text("설치 중입니다. 확인 창이 뜨면 '설치'를 눌러 주세요. 설치가 끝나면 앱이 다시 시작됩니다.")
                is UpdateState.Ready -> Text("새 버전 ${state.info.version} 설치 파일을 받아 두었습니다. 설치하면 앱이 다시 시작됩니다.")
                is UpdateState.Available -> androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 320.dp)) {
                    Text("새 버전 ${state.info.version} 이 올라와 있습니다. 앱 안에서 받아 바로 설치할 수 있습니다 (디버그 빌드가 깔려 있으면 먼저 지워야 합니다).")
                    val changes = WhatsNew.cleanNotes(state.info.notes)
                    if (changes.isNotEmpty()) {
                        Text("바뀐 내용", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        changes.forEach { Text("• $it", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { update(state.info) }) { Text("지금 업데이트") }
                is UpdateState.Ready -> TextButton(onClick = { update(state.info) }) { Text("설치") }
                else -> TextButton(onClick = onDismiss) { Text(if (busy) "백그라운드로" else "확인") }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Available ->
                    if (onLater != null) TextButton(onClick = onLater) { Text("나중에") }
                    else TextButton(onClick = { openUrl(context, state.info.pageUrl); onDismiss() }) { Text("릴리스 페이지") }
                is UpdateState.Ready -> TextButton(onClick = onLater ?: onDismiss) { Text("나중에") }
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
        title = { Text("오류 기록") },
        text = {
            if (latest == null) {
                Text("저장된 오류가 없습니다. 앱이 멈추면 여기에 남고, 폰 밖으로는 나가지 않습니다.")
            } else {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                    Text("가장 최근 오류 (총 ${entries.size}건)", style = MaterialTheme.typography.labelLarge)
                    Text(latest.take(3000), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (latest != null) TextButton(onClick = { share(context, entries.joinToString("\n\n----\n\n") { it.second }); onDismiss() }) { Text("공유") }
            else TextButton(onClick = onDismiss) { Text("확인") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (latest != null) {
                    TextButton(onClick = { copy(context, latest) }) { Text("복사") }
                    TextButton(onClick = { CrashLog.clear(); onDismiss() }) { Text("지우기") }
                }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("오류 기록", text))
    Toast.makeText(context, "복사했습니다.", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, "오류 기록 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "공유할 앱이 없습니다.", Toast.LENGTH_SHORT).show()
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
            headlineContent = { Text("새 버전 ${info.version} 이 있어요", color = MaterialTheme.colorScheme.onPrimaryContainer) },
            supportingContent = {
                Text(
                    when (state) {
                        is UpdateState.Downloading -> "받는 중… ${if (state.percent >= 0) "${state.percent}%" else ""}"
                        is UpdateState.Installing -> "설치 중…"
                        is UpdateState.Ready -> "설치 준비됨 · 눌러서 설치"
                        else -> "눌러서 업데이트"
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
