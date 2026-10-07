package com.example.superdialer.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect

private enum class Panel { None, Tabs, Bookmarks, History }

@Composable
fun BrowserScreen(viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { viewModel.ensureTab() }
    // Links opened from other apps arrive here as a new tab.
    LaunchedEffect(viewModel.externalUrl) {
        viewModel.consumeExternal()?.let { viewModel.newTab(it) }
    }

    var panel by rememberSaveable { mutableStateOf(Panel.None) }
    val tab = viewModel.selected
    val bookmarks by viewModel.bookmarks.collectAsState()
    val bookmarked = tab != null && bookmarks.any { it.url == tab.url }

    // Back: leave fullscreen video, close panels, then walk the page history.
    BackHandler(enabled = viewModel.customView != null || panel != Panel.None || tab?.canGoBack == true) {
        when {
            viewModel.customView != null -> viewModel.exitFullscreen()
            panel != Panel.None -> panel = Panel.None
            else -> viewModel.goBack()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (tab != null) {
            AddressBar(
                tab = tab,
                bookmarked = bookmarked,
                onSubmit = { input ->
                    panel = Panel.None
                    viewModel.load(input)
                },
                onNewTab = { viewModel.newTab() },
                onToggleBookmark = viewModel::toggleBookmark,
                onOpenBookmarks = { panel = Panel.Bookmarks },
                onOpenHistory = { panel = Panel.History },
            )
            if (tab.loading) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (tab != null) {
                WebViewHost(viewModel, tab, Modifier.fillMaxSize())
            }
            when (panel) {
                Panel.None -> Unit
                Panel.Tabs -> TabsPanel(
                    tabs = viewModel.tabs,
                    selectedId = viewModel.selectedId,
                    onSelect = { viewModel.selectTab(it); panel = Panel.None },
                    onClose = viewModel::closeTab,
                    onNewTab = { viewModel.newTab(); panel = Panel.None },
                    onBack = { panel = Panel.None },
                )
                Panel.Bookmarks -> BookmarksPanel(
                    bookmarks = bookmarks,
                    onOpen = { viewModel.load(it.url); panel = Panel.None },
                    onDelete = viewModel::deleteBookmark,
                    onBack = { panel = Panel.None },
                )
                Panel.History -> {
                    val history by viewModel.history.collectAsState()
                    HistoryPanel(
                        items = history,
                        onOpen = { viewModel.load(it.url); panel = Panel.None },
                        onDelete = viewModel::deleteHistory,
                        onClear = viewModel::clearHistory,
                        onBack = { panel = Panel.None },
                    )
                }
            }
        }

        if (tab != null) {
            BottomToolbar(
                tab = tab,
                tabCount = viewModel.tabs.size,
                onBack = { viewModel.goBack() },
                onForward = viewModel::goForward,
                onReloadOrStop = viewModel::reloadOrStop,
                onHome = viewModel::goHome,
                onTabs = { panel = if (panel == Panel.Tabs) Panel.None else Panel.Tabs },
            )
        }
    }

    viewModel.customView?.let { FullscreenVideo(it, onExit = viewModel::exitFullscreen) }

    viewModel.pendingDownload?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDownload,
            title = { Text("파일 다운로드") },
            text = { Text("${request.fileName}\n\n이 파일을 다운로드할까요?") },
            confirmButton = { TextButton(onClick = viewModel::confirmDownload) { Text("다운로드") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDownload) { Text("취소") } },
        )
    }
}

@Composable
private fun AddressBar(
    tab: BrowserTab,
    bookmarked: Boolean,
    onSubmit: (String) -> Unit,
    onNewTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
    // Shows the page URL; resets whenever the tab or its URL changes, keeps what you type otherwise.
    var text by remember(tab.id, tab.url) { mutableStateOf(tab.url) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("주소 또는 검색어") },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Close, contentDescription = "지우기") }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                focus.clearFocus()
                onSubmit(text)
            }),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        IconButton(onClick = onToggleBookmark) {
            Icon(
                if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (bookmarked) "북마크 해제" else "북마크 추가",
                tint = if (bookmarked) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "더보기") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("새 탭") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuOpen = false; onNewTab() },
                )
                DropdownMenuItem(
                    text = { Text("북마크") },
                    leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                    onClick = { menuOpen = false; onOpenBookmarks() },
                )
                DropdownMenuItem(
                    text = { Text("방문 기록") },
                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                    onClick = { menuOpen = false; onOpenHistory() },
                )
            }
        }
    }
}

@Composable
private fun BottomToolbar(
    tab: BrowserTab,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(Modifier.weight(1f), enabled = tab.canGoBack, onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            ToolbarButton(Modifier.weight(1f), enabled = tab.canGoForward, onClick = onForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "앞으로")
            }
            ToolbarButton(Modifier.weight(1f), onClick = onReloadOrStop) {
                if (tab.loading) {
                    Icon(Icons.Filled.Close, contentDescription = "중지")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                }
            }
            ToolbarButton(Modifier.weight(1f), onClick = onHome) {
                Icon(Icons.Filled.Home, contentDescription = "홈")
            }
            ToolbarButton(Modifier.weight(1f), onClick = onTabs) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(tabCount.toString(), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
}

/** Hosts the selected tab's WebView; the view itself is owned by the ViewModel so tabs keep their state. */
@Composable
private fun WebViewHost(viewModel: BrowserViewModel, tab: BrowserTab, modifier: Modifier = Modifier) {
    val activity = LocalContext.current
    val webView = viewModel.webViewFor(tab)

    LifecycleResumeEffect(webView) {
        webView.onResume()
        onPauseOrDispose { webView.onPause() }
    }

    AndroidView(
        modifier = modifier,
        factory = { FrameLayout(it) },
        update = { frame ->
            if (frame.getChildAt(0) !== webView) {
                frame.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.attachTo(activity)
                frame.addView(
                    webView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        },
        onRelease = { frame ->
            (frame.getChildAt(0) as? android.webkit.WebView)?.detachFromActivity()
            frame.removeAllViews()
        },
    )
}
