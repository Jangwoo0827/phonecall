package com.example.superdialer.browser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.superdialer.calllog.formatDateTime
import com.example.superdialer.ui.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One download started from the browser, as DownloadManager reports it (an app only ever sees its own downloads). */
internal data class DownloadItem(
    val id: Long,
    val title: String,
    val status: Int,
    val totalBytes: Long,
    val doneBytes: Long,
    val mimeType: String?,
    val modifiedAt: Long,
    val reason: Int,
) {
    val running get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    val done get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED
    val progress: Float? get() = if (running && totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private fun queryDownloads(context: Context): List<DownloadItem> {
    val manager = context.getSystemService(DownloadManager::class.java)
    val out = ArrayList<DownloadItem>()
    manager.query(DownloadManager.Query())?.use { c ->
        val id = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
        val title = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
        val status = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
        val total = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val so = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val mime = c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
        val modified = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
        val reason = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
        while (c.moveToNext()) {
            out += DownloadItem(
                id = c.getLong(id), title = c.getString(title).orEmpty(), status = c.getInt(status),
                totalBytes = c.getLong(total), doneBytes = c.getLong(so), mimeType = c.getString(mime),
                modifiedAt = c.getLong(modified), reason = c.getInt(reason),
            )
        }
    }
    return out.sortedByDescending { it.modifiedAt }
}

private fun openDownload(context: Context, item: DownloadItem) {
    val manager = context.getSystemService(DownloadManager::class.java)
    val uri = manager.getUriForDownloadedFile(item.id)
    if (uri == null) {
        Toast.makeText(context, "파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        return
    }
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "이 파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

/** The downloads started from the browser: progress while running, tap to open when done, remove (also deletes the file). */
@Composable
internal fun DownloadsPanel(onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<DownloadItem?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    // Poll while the panel is open so running downloads move.
    LaunchedEffect(refreshTick) {
        while (true) {
            items = withContext(Dispatchers.IO) { queryDownloads(context) }
            loaded = true
            delay(1000)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader("다운로드", onBack)
            if (loaded && items.isEmpty()) {
                Text("받은 파일이 없습니다.", modifier = Modifier.padding(24.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable(enabled = item.done) { openDownload(context, item) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                when {
                                    item.done -> Icons.Filled.CheckCircle
                                    item.failed -> Icons.Filled.ErrorOutline
                                    else -> Icons.Filled.Download
                                },
                                contentDescription = null,
                                tint = if (item.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        },
                        headlineContent = { Text(item.title.ifBlank { "파일" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                when {
                                    item.done -> Text("${formatBytes(item.totalBytes)} · ${formatDateTime(item.modifiedAt)} · 눌러서 열기")
                                    item.failed -> Text("다운로드하지 못했습니다 (오류 ${item.reason})")
                                    else -> {
                                        val p = item.progress
                                        Text(
                                            if (p != null) "${formatBytes(item.doneBytes)} / ${formatBytes(item.totalBytes)} (${(p * 100).toInt()}%)"
                                            else "${formatBytes(item.doneBytes)} 받는 중…"
                                        )
                                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { if (item.running) removeNow(context, item) { refreshTick++ } else confirmRemove = item }) {
                                Icon(
                                    if (item.running) Icons.Filled.Close else Icons.Filled.Delete,
                                    contentDescription = if (item.running) "취소" else "삭제",
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    confirmRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("삭제") },
            text = { Text("'${item.title}'을(를) 목록에서 지웁니다. 받은 파일도 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(onClick = { removeNow(context, item) { refreshTick++ }; confirmRemove = null }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("취소") } },
        )
    }
}

private fun removeNow(context: Context, item: DownloadItem, then: () -> Unit) {
    context.getSystemService(DownloadManager::class.java).remove(item.id)
    then()
}
