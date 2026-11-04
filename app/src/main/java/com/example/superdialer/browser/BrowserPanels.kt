package com.example.superdialer.browser

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.ui.groupShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
internal fun BookmarksPanel(
    bookmarks: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader(str(R.string.t_bookmarks), onBack)
            var query by remember { mutableStateOf("") }
            if (bookmarks.isNotEmpty()) PanelSearch(query) { query = it }
            val shown = bookmarks.filter { matchesQuery(it.title, it.url, query) }
            if (bookmarks.isEmpty()) {
                Text(str(R.string.t_no_bookmarks_yet_tap_the_star), modifier = Modifier.padding(24.dp))
            } else if (shown.isEmpty()) {
                Text(str(R.string.t_no_results), modifier = Modifier.padding(24.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, item -> item.id }) { index, item ->
                    ListItem(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
                            .clip(groupShape(index, shown.size))
                            .clickable { onOpen(item) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { SiteIcon(item.url, item.title, 40.dp) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Filled.Delete, contentDescription = str(R.string.t_delete_bookmark))
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
            ScreenHeader(str(R.string.t_history), onBack) {
                if (items.isNotEmpty()) TextButton(onClick = onClear) { Text(str(R.string.t_delete_all)) }
            }
            var query by remember { mutableStateOf("") }
            if (items.isNotEmpty()) PanelSearch(query) { query = it }
            val shown = items.filter { matchesQuery(it.title, it.url, query) }
            if (items.isEmpty()) {
                Text(str(R.string.t_no_history_yet), modifier = Modifier.padding(24.dp))
            } else if (shown.isEmpty()) {
                Text(str(R.string.t_no_results), modifier = Modifier.padding(24.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, item -> item.id }) { index, item ->
                    ListItem(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
                            .clip(groupShape(index, shown.size))
                            .clickable { onOpen(item) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                                Icon(Icons.Filled.Delete, contentDescription = str(R.string.t_delete_entry))
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

/** Case-insensitive match on the title or the address; an empty query matches everything. */
internal fun matchesQuery(title: String, url: String, query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || title.contains(q, ignoreCase = true) || url.contains(q, ignoreCase = true)
}

@Composable
private fun PanelSearch(query: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(str(R.string.t_search_by_title_or_address)) },
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = str(R.string.t_clear)) }
            }
        },
    )
}
