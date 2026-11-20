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
    private var keepOn = true

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
        // After the notification player was removed, touching the page (to play something) brings it back.
        v.setOnTouchListener { _, e ->
            if (e.action == android.view.MotionEvent.ACTION_DOWN && !keepOn) {
                keepOn = true
                WebAudioKeepAlive.update(app, SOURCE, true)
            }
            false
        }
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
                if (keepOn) WebAudioKeepAlive.update(app, SOURCE, true)
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

    /** The in-app "remove notification" button: pauses the page and removes the notification player (and the keep-alive). */
    fun dismissNotification() {
        keepOn = false
        pause()
        WebAudioKeepAlive.clear(app)
    }

    fun canGoBack(): Boolean = web?.canGoBack() == true

    fun goBack() {
        web?.goBack()
    }

    /** What the page's media session / audio element say right now (for the lock-screen player). */
    class PlayerState(val title: String, val artist: String, val artUrl: String, val playing: Boolean, val positionSec: Double, val durationSec: Double)

    private val READ_JS = """
(function(){
var m=navigator.mediaSession&&navigator.mediaSession.metadata;
var a=document.querySelector('audio,video');
var t=m?(m.title||''):'';var ar=m?(m.artist||''):'';
if(!t){var d=document.title||'';var i=d.indexOf(' • ');if(i>0){var t2=d.slice(0,i).trim(),a2=d.slice(i+3).trim();if(t2&&a2&&t2!=='Spotify'&&a2.indexOf('Spotify')!==0){t=t2;ar=a2;}}}
var u=(m&&m.artwork&&m.artwork.length)?m.artwork[m.artwork.length-1].src:'';
if(!u&&t){var tl=t.toLowerCase();var imgs=document.querySelectorAll('img');
for(var k=0;k<imgs.length&&!u;k++){var im=imgs[k];var al=(im.alt||'').toLowerCase();
var box=im.closest('[aria-label],[data-testid]');
var lab=box?((box.getAttribute('aria-label')||'')+' '+(box.getAttribute('data-testid')||'')).toLowerCase():'';
if((al&&al.indexOf(tl)>=0)||lab.indexOf(tl)>=0||/now-playing|nowplaying|cover-art/.test(lab)){u=im.currentSrc||im.src;}}}
if(!u&&t){var im2=document.querySelectorAll('img');for(var q=0;q<im2.length&&!u;q++){var r=im2[q].getBoundingClientRect();if(r.width>=24&&r.width<=96&&r.bottom>window.innerHeight*0.6&&r.top<window.innerHeight&&/scdn|spotifycdn|image/.test(im2[q].src)){u=im2[q].currentSrc||im2[q].src;}}}
try{u=u?new URL(u,location.href).href:''}catch(e){u=''}
return JSON.stringify({t:t,a:ar,i:u,p:a?!a.paused:false,pos:a?a.currentTime:0,dur:(a&&isFinite(a.duration))?a.duration:0});
})()
""".trimIndent()

    fun readState(callback: (PlayerState?) -> Unit) {
        val v = web
        if (v == null) { callback(null); return }
        v.evaluateJavascript(READ_JS) { raw ->
            val s = runCatching {
                val json = org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String)
                val art = json.optString("i")
                PlayerState(
                    json.optString("t"), json.optString("a"),
                    if (art.startsWith("https://")) art else "",
                    json.optBoolean("p"), json.optDouble("pos", 0.0), json.optDouble("dur", 0.0),
                )
            }.getOrNull()
            callback(s)
        }
    }

    /** play / pause / next / prev, done on the page itself (its audio element or its own buttons). */
    fun control(what: String) {
        val js = if (what.startsWith("seek:")) {
            val sec = what.removePrefix("seek:").toDoubleOrNull() ?: return
            "var a=document.querySelector('audio,video');if(a)a.currentTime=$sec"
        } else when (what) {
            "play" -> "var a=document.querySelector('audio,video');if(a)a.play()"
            "pause" -> "var a=document.querySelector('audio,video');if(a)a.pause()"
            "next" -> "var b=document.querySelector('[data-testid=\"control-button-skip-forward\"],[aria-label=\"Next\"]');if(b)b.click()"
            "prev" -> "var b=document.querySelector('[data-testid=\"control-button-skip-back\"],[aria-label=\"Previous\"]');if(b)b.click()"
            else -> return
        }
        web?.post { web?.evaluateJavascript(js, null) }
    }

    /** Pauses the page's audio (the notification's Stop button, or clearing the music data). */
    fun pause() {
        web?.evaluateJavascript("document.querySelectorAll('audio,video').forEach(function(e){e.pause()})", null)
    }
}
