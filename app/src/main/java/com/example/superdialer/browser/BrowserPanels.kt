package com.example.superdialer.browser

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.superdialer.browser.data.Bookmark
import com.example.superdialer.browser.data.HistoryItem
import com.example.superdialer.calllog.formatDateTime
import com.example.superdialer.ui.ScreenHeader

@Composable
internal fun TabsPanel(
    tabs: List<BrowserTab>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNewTab: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader("탭 ${tabs.size}개", onBack) {
                IconButton(onClick = onNewTab) { Icon(Icons.Filled.Add, contentDescription = "새 탭") }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tabs, key = { it.id }) { tab ->
                    val current = tab.id == selectedId
                    ListItem(
                        modifier = Modifier.clickable { onSelect(tab.id) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        ),
                        headlineContent = {
                            Text(tab.title.ifBlank { tab.url.ifBlank { "새 탭" } }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { SiteIcon(tab.url, tab.title, 40.dp) },
                        trailingContent = {
                            IconButton(onClick = { onClose(tab.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "탭 닫기")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookmarksPanel(
    bookmarks: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader("북마크", onBack)
            if (bookmarks.isEmpty()) {
                Text("북마크가 없습니다. 주소창의 별을 눌러 추가하세요.", modifier = Modifier.padding(24.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(bookmarks, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(item) },
                        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { SiteIcon(item.url, item.title, 40.dp) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "북마크 삭제")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun HistoryPanel(
    items: List<HistoryItem>,
    onOpen: (HistoryItem) -> Unit,
    onDelete: (HistoryItem) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader("방문 기록", onBack) {
                if (items.isNotEmpty()) TextButton(onClick = onClear) { Text("전체 삭제") }
            }
            if (items.isEmpty()) {
                Text("방문 기록이 없습니다.", modifier = Modifier.padding(24.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(item) },
                        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatDateTime(item.visitedAt), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        leadingContent = { SiteIcon(item.url, item.title, 40.dp) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "기록 삭제")
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Page video in full screen, above the app's bars. Back or the page's own exit button leaves it. */
@Composable
internal fun FullscreenVideo(video: View, onExit: () -> Unit) {
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { context ->
                FrameLayout(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    (video.parent as? ViewGroup)?.removeView(video)
                    addView(
                        video,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                }
            },
            onRelease = { it.removeAllViews() },
        )
    }
}
