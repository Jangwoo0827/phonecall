package com.example.superdialer.browser

import androidx.compose.material.icons.filled.Download
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.webkit.WebChromeClient
import android.content.ActivityNotFoundException
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect

private enum class Panel { None, Tabs, Bookmarks, History, Downloads }

/**
 * The browser tab. A tab either shows the start page (editable link tiles) or a website. While a
 * website is open it takes the whole screen: the app's own tab bars are hidden (see
 * [BrowserViewModel.immersive]), the top bar slides away when scrolling down and returns on scroll up
 * or a tap at the top edge, and the bottom navigation bar stays.
 */
@Composable
fun BrowserScreen(viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { viewModel.ensureTab() }
    // Links opened from other apps arrive here as a new tab.
    LaunchedEffect(viewModel.externalUrl) {
        viewModel.consumeExternal()?.let { viewModel.newTab(it) }
    }

    var panel by rememberSaveable { mutableStateOf(Panel.None) }

    // <input type=file>: open the system picker, then answer the page (also when it is cancelled).
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.finishFileChooser(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
    val pendingChooser = viewModel.fileChooser
    LaunchedEffect(pendingChooser) {
        val request = pendingChooser ?: return@LaunchedEffect
        try {
            filePicker.launch(request.params.createIntent())
        } catch (e: ActivityNotFoundException) {
            viewModel.finishFileChooser(null)
        }
    }
    val tab = viewModel.selected ?: return
    val bookmarks by viewModel.bookmarks.collectAsState()
    val bookmarked = !tab.isStart && bookmarks.any { it.url == tab.url }

    // Back: leave fullscreen video, close panels, walk the page history, then return to the start page.
    BackHandler(enabled = viewModel.customView != null || panel != Panel.None || !tab.isStart) {
        when {
            viewModel.customView != null -> viewModel.exitFullscreen()
            panel != Panel.None -> panel = Panel.None
            tab.canGoBack -> viewModel.goBack()
            else -> viewModel.exitToStart()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab.isStart,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(160))
            },
            label = "startOrPage",
        ) { isStart ->
        if (isStart) {
            StartPage(
                viewModel = viewModel,
                tabCount = viewModel.tabs.size,
                onOpenTabs = { panel = Panel.Tabs },
                onOpenBookmarks = { panel = Panel.Bookmarks },
                onOpenHistory = { panel = Panel.History },
                onOpenDownloads = { panel = Panel.Downloads },
            )
        } else {
            WebPage(
                viewModel = viewModel,
                tab = tab,
                bookmarked = bookmarked,
                onOpenTabs = { panel = if (panel == Panel.Tabs) Panel.None else Panel.Tabs },
                onOpenBookmarks = { panel = Panel.Bookmarks },
                onOpenHistory = { panel = Panel.History },
                onOpenDownloads = { panel = Panel.Downloads },
            )
        }
        }

        AnimatedContent(
            targetState = panel,
            transitionSpec = {
                if (targetState == Panel.None) {
                    fadeIn(tween(1)) togetherWith (slideOutVertically(tween(240)) { it / 3 } + fadeOut(tween(240)))
                } else {
                    (slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith fadeOut(tween(120))
                }
            },
            label = "panel",
        ) { shownPanel ->
        when (shownPanel) {
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
            Panel.Downloads -> DownloadsPanel(onBack = { panel = Panel.None })
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

// --- Start page ---------------------------------------------------------------------------------

@Composable
private fun StartPage(
    viewModel: BrowserViewModel,
    tabCount: Int,
    onOpenTabs: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val dials by viewModel.speedDials.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressField(
                initial = "",
                onSubmit = viewModel::load,
                modifier = Modifier.weight(1f),
            )
            TabCountButton(tabCount, onOpenTabs)
            OverflowMenu(viewModel::newTab, onOpenBookmarks, onOpenHistory, onOpenDownloads)
        }
        SpeedDialGrid(
            dials = dials,
            onOpen = { viewModel.load(it.url) },
            onAdd = viewModel::addSpeedDial,
            onAddFolder = viewModel::addSpeedDialFolder,
            onUpdate = viewModel::updateSpeedDial,
            onDelete = viewModel::deleteSpeedDial,
            onMove = viewModel::moveSpeedDial,
            onReorder = viewModel::reorderSpeedDials,
        )
    }
}

// --- Website -------------------------------------------------------------------------------------

@Composable
private fun WebPage(
    viewModel: BrowserViewModel,
    tab: BrowserTab,
    bookmarked: Boolean,
    onOpenTabs: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    var topBarVisible by remember(tab.id) { mutableStateOf(true) }
    // Navigating always brings the bar back so the new address is visible.
    LaunchedEffect(tab.id, tab.url) { topBarVisible = true }

    Column(modifier = Modifier.fillMaxSize()) {
        WebArea(
            viewModel = viewModel,
            tab = tab,
            bookmarked = bookmarked,
            topBarVisible = topBarVisible,
            onTopBarVisible = { topBarVisible = it },
            onOpenBookmarks = onOpenBookmarks,
            onOpenHistory = onOpenHistory,
            onOpenDownloads = onOpenDownloads,
            modifier = Modifier.weight(1f),
        )
        BottomToolbar(
            tab = tab,
            tabCount = viewModel.tabs.size,
            onBack = { if (!viewModel.goBack()) viewModel.exitToStart() },
            onForward = viewModel::goForward,
            onReloadOrStop = viewModel::reloadOrStop,
            onHome = viewModel::exitToStart,
            onTabs = onOpenTabs,
        )
    }
}

/** The page itself with the sliding top bar and the loading line on top of it. */
@Composable
private fun WebArea(
    viewModel: BrowserViewModel,
    tab: BrowserTab,
    bookmarked: Boolean,
    topBarVisible: Boolean,
    onTopBarVisible: (Boolean) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The bar appears on navigation or a touch at the top edge (not on scroll, so feeds like Shorts stay clean)
    // and goes away again after 2 s without a touch on it, unless the address is being edited or a menu is open.
    var touchStamp by remember { mutableIntStateOf(0) }
    var barFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(topBarVisible, touchStamp, barFocused, menuOpen, tab.url) {
        if (topBarVisible && !barFocused && !menuOpen) {
            delay(BAR_AUTO_HIDE_MS)
            onTopBarVisible(false)
        }
    }
    Box(modifier = modifier) {
        WebViewHost(
            viewModel = viewModel,
            tab = tab,
            modifier = Modifier.fillMaxSize(),
            onScrollDown = { onTopBarVisible(false) },
            onTouchTop = { touchStamp++; onTopBarVisible(true) },
        )
        AnimatedVisibility(
            visible = topBarVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onFocusChanged { barFocused = it.hasFocus }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            touchStamp++
                        }
                    }
                },
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            WebTopBar(
                tab = tab,
                bookmarked = bookmarked,
                onExit = viewModel::exitToStart,
                onSubmit = viewModel::load,
                onToggleBookmark = viewModel::toggleBookmark,
                onNewTab = { viewModel.newTab() },
                onOpenBookmarks = onOpenBookmarks,
                onOpenHistory = onOpenHistory,
                onOpenDownloads = onOpenDownloads,
                onMenuOpenChange = { menuOpen = it },
            )
        }
        if (tab.loading) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun WebTopBar(
    tab: BrowserTab,
    bookmarked: Boolean,
    onExit: () -> Unit,
    onSubmit: (String) -> Unit,
    onToggleBookmark: () -> Unit,
    onNewTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "나가기") }
            AddressField(initial = tab.url, resetKey = tab.id, onSubmit = onSubmit, modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (bookmarked) "북마크 해제" else "북마크 추가",
                    tint = if (bookmarked) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OverflowMenu(onNewTab, onOpenBookmarks, onOpenHistory, onOpenDownloads, onMenuOpenChange)
        }
    }
}

// --- Shared pieces -------------------------------------------------------------------------------

/** Address / search field. Shows [initial] and resets when it changes; keeps what you type otherwise. */
@Composable
private fun AddressField(
    initial: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
) {
    val focus = LocalFocusManager.current
    var text by remember(initial, resetKey) { mutableStateOf(initial) }
    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier,
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
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun OverflowMenu(
    onNewTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenChange: (Boolean) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { onOpenChange(open) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "더보기") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("새 탭") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { open = false; onNewTab() },
            )
            DropdownMenuItem(
                text = { Text("북마크") },
                leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                onClick = { open = false; onOpenBookmarks() },
            )
            DropdownMenuItem(
                text = { Text("방문 기록") },
                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                onClick = { open = false; onOpenHistory() },
            )
            DropdownMenuItem(
                text = { Text("다운로드") },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = { open = false; onOpenDownloads() },
            )
        }
    }
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(count.toString(), style = MaterialTheme.typography.labelSmall) }
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
            IconButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            IconButton(onClick = onForward, enabled = tab.canGoForward, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "앞으로")
            }
            IconButton(onClick = onReloadOrStop, modifier = Modifier.weight(1f)) {
                if (tab.loading) {
                    Icon(Icons.Filled.Close, contentDescription = "중지")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                }
            }
            IconButton(onClick = onHome, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Home, contentDescription = "시작 페이지")
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { TabCountButton(tabCount, onTabs) }
        }
    }
}

/**
 * Hosts the selected tab's WebView; the view itself is owned by the ViewModel so tabs keep their state.
 * Finger movement is reported (without consuming it) so the top bar can hide on scroll down and show on
 * a touch near the top edge.
 */
@Composable
private fun WebViewHost(
    viewModel: BrowserViewModel,
    tab: BrowserTab,
    modifier: Modifier = Modifier,
    onScrollDown: () -> Unit,
    onTouchTop: () -> Unit,
) {
    val activity = LocalContext.current
    val density = LocalDensity.current
    val topZonePx = with(density) { 56.dp.toPx() }
    val thresholdPx = with(density) { 24.dp.toPx() }
    val webView = viewModel.webViewFor(tab)

    val latestDown by rememberUpdatedState(onScrollDown)
    val latestTop by rememberUpdatedState(onTouchTop)
    val drag = remember { DragTracker() }

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
            webView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        drag.lastY = event.y
                        drag.accumulated = 0f
                        if (event.y < topZonePx) latestTop()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.y - drag.lastY
                        drag.lastY = event.y
                        // A change of direction starts counting again.
                        if (dy * drag.accumulated < 0) drag.accumulated = 0f
                        drag.accumulated += dy
                        if (drag.accumulated < -thresholdPx) {
                            latestDown()
                            drag.accumulated = 0f
                        }
                    }
                }
                false // never consume: the page still scrolls and handles taps itself
            }
        },
        onRelease = { frame ->
            (frame.getChildAt(0) as? android.webkit.WebView)?.apply {
                setOnTouchListener(null)
                detachFromActivity()
            }
            frame.removeAllViews()
        },
    )
}

private const val BAR_AUTO_HIDE_MS = 2000L

private class DragTracker {
    var lastY = 0f
    var accumulated = 0f
}
