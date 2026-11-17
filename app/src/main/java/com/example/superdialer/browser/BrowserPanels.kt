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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

/**
 * Page video in full screen. The video view is attached to the activity's own window (a separate Dialog window made
 * YouTube pause the video), the system bars are hidden, and the screen turns to landscape and follows the sensor while
 * it is up. Back or the page's own exit button leaves it, which restores the bars and the orientation.
 */
@Composable
internal fun FullscreenVideo(video: View, onExit: () -> Unit) {
    var context = LocalContext.current
    while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
    val activity = context as? android.app.Activity ?: return
    DisposableEffect(video) {
        val window = activity.window
        val decor = window.decorView as ViewGroup
        val container = FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            isClickable = true
        }
        (video.parent as? ViewGroup)?.removeView(video)
        container.addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        decor.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val controller = WindowCompat.getInsetsController(window, decor)
        val oldBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        val oldCutout = window.attributes.layoutInDisplayCutoutMode
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val oldOrientation = activity.requestedOrientation
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity.requestedOrientation = oldOrientation
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = oldCutout }
            controller.systemBarsBehavior = oldBehavior
            controller.show(WindowInsetsCompat.Type.systemBars())
            container.removeAllViews()
            decor.removeView(container)
        }
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
