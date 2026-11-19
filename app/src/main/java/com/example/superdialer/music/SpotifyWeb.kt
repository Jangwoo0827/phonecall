package com.example.superdialer.music

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.superdialer.browser.UserAgents
import com.example.superdialer.browser.WebAudioKeepAlive

/**
 * Spotify's official web player (open.spotify.com) shown inside the Music tab. It is the real site, used with the user's
 * own login; nothing is taken out of it. The page lives in one WebView that is kept when the user switches chips or leaves
 * the app, so its music goes on playing in the background (see [WebAudioKeepAlive], which shows the notification that keeps
 * the app running). It identifies as Chrome (Spotify refuses the WebView identification) and is the only place besides the
 * browser's own Spotify tab that may use protected-media (DRM) playback.
 */
object SpotifyWeb {
    const val HOME = "https://open.spotify.com/"
    private const val ORIGIN = "https://open.spotify.com"
    private const val SOURCE = "music-tab"

    private lateinit var app: Context
    private var web: WebView? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun webView(): WebView = web ?: WebView(MutableContextWrapper(app)).also { v ->
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = UserAgents.chromeMobile(WebSettings.getDefaultUserAgent(app))
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(v, true) // sign-in pages set cookies across spotify domains
        v.setBackgroundColor(android.graphics.Color.BLACK)
        v.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Only Spotify's own page may play protected (DRM) media; nothing else (camera, microphone, ...) is granted.
                val origin = request.origin.toString().trimEnd('/')
                if (origin == ORIGIN && request.resources.all { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                }
            }
        }
        v.webViewClient = object : WebViewClient() {
            // Spotify, its sign-in providers and its content hosts; anything else is not opened here.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                val ok = request.url.scheme == "https" && ALLOWED.any { host == it || host.endsWith(".$it") }
                return !ok
            }

            override fun onPageFinished(view: WebView, url: String?) {
                WebAudioKeepAlive.update(app, SOURCE, true)
            }
        }
        web = v
        v.loadUrl(HOME)
    }.also { web = it }

    private val ALLOWED = listOf(
        "spotify.com", "scdn.co", "spotifycdn.com", "spotify.net",
        "google.com", "gstatic.com", "googleapis.com", "recaptcha.net",
        "facebook.com", "apple.com", "appleid.apple.com",
    )

    fun attach(activity: Context) {
        (webView().context as? MutableContextWrapper)?.baseContext = activity
    }

    fun detach() {
        (web?.context as? MutableContextWrapper)?.baseContext = app
    }

    fun canGoBack(): Boolean = web?.canGoBack() == true

    fun goBack() {
        web?.goBack()
    }

    /** Pauses the page's audio (the notification's Stop button, or clearing the music data). */
    fun pause() {
        web?.evaluateJavascript("document.querySelectorAll('audio,video').forEach(function(e){e.pause()})", null)
    }
}
