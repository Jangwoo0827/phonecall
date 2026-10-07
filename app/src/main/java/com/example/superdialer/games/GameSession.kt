package com.example.superdialer.games

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which game is open and its live WebView. Owned at app level so the game keeps its state while the app is
 * out of sight; [com.example.superdialer.browser.BrowserSession] frees it after the keep-alive time.
 */
object GameSession {
    var playingId by mutableStateOf<String?>(null)

    private var webViewId: String? = null
    private var webView: WebView? = null

    fun webViewFor(id: String, create: () -> WebView): WebView {
        if (webViewId != id) {
            destroyWebView()
            webView = create()
            webViewId = id
        }
        return webView!!
    }

    /** Back to the game list: the finished game is dropped. */
    fun close() {
        playingId = null
        destroyWebView()
    }

    fun release() = close()

    private fun destroyWebView() {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        webViewId = null
    }
}
