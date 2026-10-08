package com.example.superdialer.settings

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
                    else -> "GitHub에 올라온 최신 버전을 확인합니다"
                },
                color = if (update is UpdateState.Available) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
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
private fun UpdateDialog(state: UpdateState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("업데이트") },
        text = {
            when (state) {
                UpdateState.Idle, UpdateState.Checking -> CircularProgressIndicator()
                is UpdateState.UpToDate -> Text("최신 버전(${state.current})을 쓰고 있습니다.")
                is UpdateState.Failed -> Text(state.message)
                is UpdateState.Available -> androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 320.dp)) {
                    Text("새 버전 ${state.info.version} 이 올라와 있습니다. 받은 APK를 열어 설치하면 됩니다 (디버그 빌드가 깔려 있으면 먼저 지워야 합니다).")
                    if (state.info.notes.isNotBlank()) Text("\n" + state.info.notes.take(1200), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (state is UpdateState.Available) {
                TextButton(onClick = { openUrl(context, state.info.apkUrl ?: state.info.pageUrl); onDismiss() }) {
                    Text(if (state.info.apkUrl != null) "APK 받기" else "릴리스 페이지")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("확인") }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available) {
                TextButton(onClick = { openUrl(context, state.info.pageUrl); onDismiss() }) { Text("릴리스 페이지") }
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
