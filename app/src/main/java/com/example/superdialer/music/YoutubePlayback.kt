package com.example.superdialer.music

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * Plays YouTube link songs with YouTube's own embedded player (IFrame API) inside a WebView that the full player puts in
 * the place of the cover. The video is always visible while it plays; it pauses when the screen is closed, the screen
 * turns off or the app is left (YouTube does not allow its player to run hidden or in the background, and this does not
 * try to). No JavaScript bridge: the page is only driven with evaluateJavascript calls and read the same way.
 */
object YoutubePlayback {
    private lateinit var app: Context
    private val handler = Handler(Looper.getMainLooper())
    private var web: WebView? = null

    /** The link songs being played, in order, and the one that is current. */
    var queue by mutableStateOf<List<Track>>(emptyList()); private set
    var index by mutableIntStateOf(-1); private set

    /** YouTube's player state: -1 not started, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued. */
    var state by mutableIntStateOf(-1); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set

    /** YouTube's error code of the current video (101 / 150: the owner does not allow playing it outside YouTube), or 0. */
    var errorCode by mutableIntStateOf(0); private set

    /** The video did not start within a few seconds (the embed refused or failed): the screen offers the browser instead. */
    var stalled by mutableStateOf(false); private set
    private var loadedAt = 0L

    /** Bumped every time a video starts, so the UI can open the full player. */
    var startCount by mutableIntStateOf(0); private set

    private var repeatAll = false

    val current: Track? get() = queue.getOrNull(index)
    val active: Boolean get() = current != null
    val isPlaying: Boolean get() = state == 1 || state == 3

    fun init(context: Context) {
        app = context.applicationContext
    }

    // --- the WebView ------------------------------------------------------------------------------------------------

    /** The one WebView of the embedded player. Attach it to the screen with [attach], detach with [detach]. */
    @SuppressLint("SetJavaScriptEnabled")
    fun webView(): WebView = web ?: WebView(MutableContextWrapper(app)).also { v ->
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(v, true) // the YouTube player lives in an iframe
        v.setBackgroundColor(android.graphics.Color.BLACK)
        v.webChromeClient = WebChromeClient()
        v.webViewClient = object : WebViewClient() {
            // Nothing but YouTube may be shown in this view.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                return !(host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".ytimg.com") || host.endsWith(".google.com") || host.endsWith(".gstatic.com") || host.endsWith(".googlevideo.com"))
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                // a new page: the video is not there yet
            }
        }
        web = v
    }

    fun attach(activity: Context) {
        (webView().context as? MutableContextWrapper)?.baseContext = activity
    }

    fun detach() {
        (web?.context as? MutableContextWrapper)?.baseContext = app
    }

    // --- playback -----------------------------------------------------------------------------------------------------

    /** Starts [tracks] (link songs only) at [startIndex]. The music player is paused: only one thing plays at a time. */
    fun start(tracks: List<Track>, startIndex: Int, repeat: Boolean) {
        val links = tracks.filter { it.isLink }
        if (links.isEmpty()) return
        MusicPlayer.pauseForVideo()
        queue = links
        index = startIndex.coerceIn(0, links.lastIndex)
        repeatAll = repeat
        startCount++
        loadCurrent()
    }

    private fun idOf(t: Track) = t.id.removePrefix("yt:")

    private fun loadCurrent() {
        val t = current ?: return
        state = -1
        errorCode = 0
        positionMs = 0L
        durationMs = 0L
        MusicStore.addRecent(t)
        loadedAt = System.currentTimeMillis()
        stalled = false
        web?.evaluateJavascript("window.__sdUnmuted=false", null)
        webView().loadUrl("https://m.youtube.com/watch?v=${idOf(t)}")
        tick()
    }

    fun togglePlay() {
        if (isPlaying) pause() else if (current != null) js(PLAY)
    }

    fun pause() {
        if (web != null) js(PAUSE)
    }

    fun seekTo(ms: Long) {
        js("(function(){var v=document.querySelector('video');if(v)v.currentTime=${ms / 1000.0};})()")
        positionMs = ms
    }

    fun next() {
        if (queue.isEmpty()) return
        index = if (index < queue.lastIndex) index + 1 else if (repeatAll) 0 else return
        loadCurrent()
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (positionMs > 3000 || index == 0) {
            seekTo(0L)
        } else {
            index--
            loadCurrent()
        }
    }

    /** Ends the video session (another song started in the music player, or the data was cleared). */
    fun stop() {
        pause()
        queue = emptyList()
        index = -1
        state = -1
        web?.loadUrl("about:blank")
        handler.removeCallbacks(poll)
    }

    private fun js(code: String) {
        web?.evaluateJavascript(code, null)
    }

    /** Reads the page's video: [paused, ended, time, duration, readyState], or null while there is none (yet). */
    private const val READ = "(function(){var v=document.querySelector('video');if(!v)return null;if(v.muted&&!v.paused&&!window.__sdUnmuted){v.muted=false;window.__sdUnmuted=true;}return [v.paused?1:0,v.ended?1:0,v.currentTime||0,v.duration||0,v.readyState]})()"
    private const val PLAY = "(function(){var v=document.querySelector('video');if(v){v.muted=false;v.play();}})()"
    private const val PAUSE = "(function(){var v=document.querySelector('video');if(v)v.pause();})()"

    private val poll = object : Runnable {
        override fun run() {
            val v = web
            if (v == null || !active) return
            v.evaluateJavascript(READ) { result ->
                if (!active) return@evaluateJavascript
                val a = if (result == null || result == "null") null else runCatching { JSONArray(result) }.getOrNull()
                if (a == null) {
                    state = -1
                    stalled = System.currentTimeMillis() - loadedAt > 10_000
                    return@evaluateJavascript
                }
                val paused = a.optInt(0) == 1
                val ended = a.optInt(1) == 1
                val ready = a.optInt(4)
                val s = when {
                    ended -> 0
                    paused -> 2
                    ready < 3 -> 3
                    else -> 1
                }
                if (s != state) {
                    state = s
                    if (s == 0) next() // ended: the next song, or round again when repeating
                }
                positionMs = (a.optDouble(2, 0.0) * 1000).toLong()
                val d = a.optDouble(3, 0.0)
                durationMs = if (d.isNaN() || d.isInfinite()) 0L else (d * 1000).toLong()
                stalled = false
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun tick() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }
}
