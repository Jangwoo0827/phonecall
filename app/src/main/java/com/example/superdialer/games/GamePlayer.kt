package com.example.superdialer.games

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.webkit.WebViewAssetLoader
import com.example.superdialer.browser.attachTo
import com.example.superdialer.browser.detachFromActivity
import com.example.superdialer.ui.ScreenHeader

/** The only thing games can call: read and report this game's best score. */
private class GameBridge(private val gameId: String) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getBest(): Int = GameScores.best(gameId)

    @JavascriptInterface
    fun submitScore(score: Int) {
        // Called on a WebView background thread; scores live in Compose state, so hop to main.
        main.post { GameScores.submit(gameId, score) }
    }
}

/**
 * Builds the WebView for one bundled HTML5 game the same way the browser builds its tabs (created
 * detached with an application-level context, loaded, then attached to the screen). Pages are served
 * from the app's assets through a virtual https host and navigation away from the game is blocked, so
 * the score bridge is only ever exposed to our own files.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
private fun createGameWebView(context: Context, game: GameInfo): WebView {
    val app = context.applicationContext
    val loader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app))
        .build()
    return WebView(MutableContextWrapper(app)).apply {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }
        addJavascriptInterface(GameBridge(game.id), "Native")
        // Surfaces JS errors from the bundled games in logcat (tag "GameConsole").
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("GameConsole", "${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != WebViewAssetLoader.DEFAULT_DOMAIN
        }
        loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/games/${game.path}")
    }
}

@Composable
fun GamePlayer(game: GameInfo, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val activity = LocalContext.current
    // Kept by GameSession (not destroyed when this screen leaves) so a game in progress survives leaving the app.
    val webView = remember(game.id) { GameSession.webViewFor(game.id) { createGameWebView(activity, game) } }

    LifecycleResumeEffect(webView) {
        webView.onResume()
        onPauseOrDispose { webView.onPause() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(game.title, onClose)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                (frame.getChildAt(0) as? WebView)?.detachFromActivity()
                frame.removeAllViews()
            },
        )
    }
}
