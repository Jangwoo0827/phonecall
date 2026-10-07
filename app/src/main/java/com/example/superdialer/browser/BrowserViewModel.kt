package com.example.superdialer.browser

import android.annotation.SuppressLint
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.superdialer.browser.data.Bookmark
import com.example.superdialer.browser.data.BrowserDatabase
import com.example.superdialer.browser.data.HistoryItem
import com.example.superdialer.browser.data.SpeedDial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** UI-visible state of one browser tab. The matching [WebView] lives in [BrowserViewModel]. */
class BrowserTab(val id: Int) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /** True while the tab shows the speed-dial start page instead of a website. */
    var isStart by mutableStateOf(true)
    internal var lastRecordedUrl: String? = null
}

data class DownloadRequest(val url: String, val userAgent: String?, val mimeType: String?, val fileName: String)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val db = BrowserDatabase.get(application)
    private val faviconStore = com.example.superdialer.browser.data.FaviconStore.get(application)

    val tabs = mutableStateListOf<BrowserTab>()
    var selectedId by mutableIntStateOf(-1)
        private set
    val selected: BrowserTab? get() = tabs.firstOrNull { it.id == selectedId }

    /** Which section of the web/games tab is showing: 0 browser, 1 games. */
    var hubSection by mutableIntStateOf(0)

    /** A website is open full screen: the app hides its own tab bars so the page gets the whole screen. */
    val immersive: Boolean get() = hubSection == 0 && selected?.isStart == false

    /** Non-null while a page's video is shown full screen. */
    var customView by mutableStateOf<View?>(null)
        private set
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    var pendingDownload by mutableStateOf<DownloadRequest?>(null)
        private set

    /** A link opened from another app, waiting for the browser screen to pick it up. */
    var externalUrl by mutableStateOf<String?>(null)
        private set

    val bookmarks: StateFlow<List<Bookmark>> = db.bookmarkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val speedDials: StateFlow<List<SpeedDial>> = db.speedDialDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<HistoryItem>> = db.historyDao().observeRecent(HISTORY_LIMIT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val webViews = HashMap<Int, WebView>()
    private var nextId = 1

    // --- Tabs ---------------------------------------------------------------------------------

    /** The browser always has at least one tab. Called when the browser screen first appears. */
    fun ensureTab() {
        if (tabs.isEmpty()) newTab()
    }

    fun newTab(url: String? = null): BrowserTab {
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(getApplication(), "탭은 최대 ${MAX_TABS}개까지 열 수 있습니다.", Toast.LENGTH_SHORT).show()
            return selected ?: tabs.last()
        }
        val tab = BrowserTab(nextId++)
        tabs += tab
        selectedId = tab.id
        if (url != null) {
            tab.isStart = false
            webViewFor(tab).loadUrl(url)
        }
        return tab
    }

    fun selectTab(id: Int) {
        if (tabs.any { it.id == id }) selectedId = id
    }

    fun closeTab(id: Int) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        webViews.remove(id)?.let(::destroy)
        tabs.removeAt(index)
        when {
            tabs.isEmpty() -> newTab()
            selectedId == id -> selectedId = tabs[index.coerceAtMost(tabs.lastIndex)].id
        }
    }

    fun webViewFor(tab: BrowserTab): WebView = webViews.getOrPut(tab.id) { createWebView(tab) }

    // --- Navigation ---------------------------------------------------------------------------

    fun load(input: String) {
        val url = UrlResolver.resolve(input) ?: return
        val tab = selected ?: return
        tab.isStart = false
        webViewFor(tab).loadUrl(url)
    }

    /** Leaves the website and returns to the start page (the X button / back at the first page). */
    fun exitToStart() {
        val tab = selected ?: return
        webViews.remove(tab.id)?.let(::destroy)
        tab.isStart = true
        tab.url = ""
        tab.title = ""
        tab.loading = false
        tab.progress = 0
        tab.canGoBack = false
        tab.canGoForward = false
        tab.lastRecordedUrl = null
    }

    fun goBack(): Boolean {
        val tab = selected?.takeUnless { it.isStart } ?: return false
        val view = webViewFor(tab)
        if (!view.canGoBack()) return false
        view.goBack()
        return true
    }

    fun goForward() {
        selected?.takeUnless { it.isStart }?.let { webViewFor(it).takeIf(WebView::canGoForward)?.goForward() }
    }

    fun reloadOrStop() {
        val tab = selected?.takeUnless { it.isStart } ?: return
        val view = webViewFor(tab)
        if (tab.loading) view.stopLoading() else view.reload()
    }

    fun goHome() = exitToStart()

    fun openExternal(url: String) {
        externalUrl = url
    }

    fun consumeExternal(): String? = externalUrl.also { externalUrl = null }

    // --- Bookmarks / history ------------------------------------------------------------------

    fun toggleBookmark() {
        val tab = selected ?: return
        val url = tab.url
        if (!UrlResolver.isWebScheme(Uri.parse(url).scheme)) return
        viewModelScope.launch(Dispatchers.IO) {
            if (bookmarks.value.any { it.url == url }) {
                db.bookmarkDao().deleteByUrl(url)
            } else {
                db.bookmarkDao().upsert(Bookmark(url = url, title = tab.title.ifBlank { url }, createdAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteBookmark(item: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) { db.bookmarkDao().deleteById(item.id) }
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch(Dispatchers.IO) { db.historyDao().deleteById(item.id) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { db.historyDao().deleteAll() }
    }

    // --- Speed dial ---------------------------------------------------------------------------

    fun addSpeedDial(title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = db.speedDialDao()
            dao.insert(SpeedDial(title = title, url = url, position = dao.nextPosition()))
        }
    }

    fun updateSpeedDial(item: SpeedDial, title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) { db.speedDialDao().update(item.copy(title = title, url = url)) }
    }

    fun deleteSpeedDial(item: SpeedDial) {
        viewModelScope.launch(Dispatchers.IO) { db.speedDialDao().deleteById(item.id) }
    }

    // --- Fullscreen video / downloads ---------------------------------------------------------

    fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        customView = null
    }

    fun dismissDownload() {
        pendingDownload = null
    }

    fun confirmDownload() {
        val request = pendingDownload ?: return
        pendingDownload = null
        val context = getApplication<Application>()
        if (!UrlResolver.isWebScheme(Uri.parse(request.url).scheme)) {
            Toast.makeText(context, "이 형식의 다운로드는 지원하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = context.getSystemService(DownloadManager::class.java)
        val download = DownloadManager.Request(Uri.parse(request.url)).apply {
            request.mimeType?.let(::setMimeType)
            CookieManager.getInstance().getCookie(request.url)?.let { addRequestHeader("Cookie", it) }
            request.userAgent?.let { addRequestHeader("User-Agent", it) }
            setTitle(request.fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.fileName)
        }
        try {
            manager.enqueue(download)
            Toast.makeText(context, "다운로드를 시작합니다: ${request.fileName}", Toast.LENGTH_SHORT).show()
        } catch (e: RuntimeException) {
            Toast.makeText(context, "다운로드를 시작하지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- WebView setup ------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView {
        // The context is swapped to the visible Activity while attached, see WebViewHost.
        val webView = WebView(android.content.MutableContextWrapper(getApplication<Application>()))
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // No local file / content access from web pages; no mixed content; no popups.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tab.loading = true
                url?.let { tab.url = it }
                syncNavigation(tab, view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.loading = false
                tab.title = view.title.orEmpty()
                syncNavigation(tab, view)
                recordVisit(tab, view)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                url?.let { tab.url = it }
                syncNavigation(tab, view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !handleLink(request.url)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title.orEmpty()
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                val pageUrl = view.url ?: return
                if (icon != null) faviconStore.put(pageUrl, icon)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            pendingDownload = DownloadRequest(url, userAgent, mimeType, URLUtil.guessFileName(url, contentDisposition, mimeType))
        }
        return webView
    }

    private fun syncNavigation(tab: BrowserTab, view: WebView) {
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
    }

    private fun recordVisit(tab: BrowserTab, view: WebView) {
        val url = view.url ?: return
        if (!UrlResolver.isWebScheme(Uri.parse(url).scheme) || tab.lastRecordedUrl == url) return
        tab.lastRecordedUrl = url
        val item = HistoryItem(url = url, title = view.title?.takeIf { it.isNotBlank() } ?: url, visitedAt = System.currentTimeMillis())
        viewModelScope.launch(Dispatchers.IO) { db.historyDao().insert(item) }
    }

    /** Returns true when the WebView may load [uri] itself; other schemes are handed off or blocked. */
    private fun handleLink(uri: Uri): Boolean {
        if (UrlResolver.isWebScheme(uri.scheme)) return true
        val action = when (uri.scheme?.lowercase()) {
            "tel" -> Intent.ACTION_DIAL
            "mailto", "sms", "smsto" -> Intent.ACTION_VIEW
            else -> return false // file:, content:, intent:, javascript: ... are never followed from a page
        }
        try {
            getApplication<Application>().startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(getApplication(), "이 링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    private fun destroy(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    /** Frees the WebViews and stops observing; the instance is dropped afterwards (see [BrowserSession]). */
    fun release() {
        webViews.values.forEach(::destroy)
        webViews.clear()
        tabs.clear()
        viewModelScope.cancel()
    }

    override fun onCleared() = release()

    private companion object {
        const val MAX_TABS = 20
        const val HISTORY_LIMIT = 500
    }
}

/** Points the WebView's context at [context] while it is on screen, and back at the app afterwards. */
internal fun WebView.attachTo(context: Context) {
    (this.context as? android.content.MutableContextWrapper)?.baseContext = context
}

internal fun WebView.detachFromActivity() {
    val wrapper = this.context as? android.content.MutableContextWrapper ?: return
    wrapper.baseContext = wrapper.applicationContext
}
