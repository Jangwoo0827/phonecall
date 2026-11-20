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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
    /**
     * Android WebView has no Media Session API (navigator.mediaSession is undefined), which is what lets Chrome show a page's
     * song, its cover and the next / seek buttons on the lock screen. This stand-in, injected before the page's own scripts,
     * only remembers what Spotify's page registers (song info, playback state, position, action handlers); the app reads it
     * (readState) and calls the handlers (control). No JavaScript bridge is exposed to the page.
     */
    private val MEDIA_SESSION_POLYFILL = """
(function(){
if(navigator.mediaSession)return;
var st={metadata:null,playbackState:'none',handlers:{},position:null,posAt:0};
if(!window.MediaMetadata){window.MediaMetadata=function(i){i=i||{};this.title=i.title||'';this.artist=i.artist||'';this.album=i.album||'';this.artwork=i.artwork||[];};}
var ms={
get metadata(){return st.metadata},set metadata(v){st.metadata=v},
get playbackState(){return st.playbackState},set playbackState(v){st.playbackState=v},
setActionHandler:function(a,h){if(h)st.handlers[a]=h;else delete st.handlers[a];},
setPositionState:function(s){st.position=s?{d:s.duration,p:s.position,r:s.playbackRate||1}:null;st.posAt=Date.now();},
setMicrophoneActive:function(){},setCameraActive:function(){}
};
Object.defineProperty(navigator,'mediaSession',{value:ms,configurable:true});
window.__sdMS=st;
try{var op=HTMLMediaElement.prototype.play;HTMLMediaElement.prototype.play=function(){st.media=this;return op.apply(this,arguments);};}catch(e){}
})();
""".trimIndent()

    /** Runs the page's registered media-session handler for [action]; if there is none, runs [fallback]. */
    private fun handlerJs(action: String, fallback: String) =
        "if(!(function(){var S=window.__sdMS;var h=S&&S.handlers&&S.handlers['$action'];if(!h)return false;try{h({action:'$action'});return true}catch(e){return false}})()){$fallback}"

    /** Whether the page has history to go back to. Compose state, so the back handler follows it. */
    var canGoBackNow by mutableStateOf(false)
        private set

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
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(v, MEDIA_SESSION_POLYFILL, setOf(ORIGIN))
        }
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
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBackNow = view.canGoBack()
            }

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
var S=window.__sdMS;
var a=(S&&S.media)||document.querySelector('audio,video');
var t=m?(m.title||''):'';var ar=m?(m.artist||''):'';
var u=(m&&m.artwork&&m.artwork.length)?m.artwork[m.artwork.length-1].src:'';
var p=a?!a.paused:false,pos=a?a.currentTime:0,dur=(a&&isFinite(a.duration))?a.duration:0;
var bar=document.querySelector('[data-testid="floating-now-playing-bar"]');
if(bar){
var sp=[];Array.prototype.forEach.call(bar.querySelectorAll('span'),function(e){if(!e.closest('button')&&e.children.length===0){var x=(e.textContent||'').trim();if(x)sp.push(x);}});
if(!t&&sp.length){t=sp[0];ar=sp[1]||'';}
var bi=bar.querySelector('img');if(!u&&bi)u=bi.currentSrc||bi.src;
var bb=bar.querySelectorAll('button');var pb=bb.length?bb[bb.length-1]:null;var pp=pb?pb.querySelector('path'):null;
if(!a&&pp){p=(pp.getAttribute('d')||'').indexOf('m7.05 3.606')!==0;}
}
if(S&&S.playbackState&&S.playbackState!=='none'){p=S.playbackState==='playing';}
if(S&&S.position){dur=S.position.d||dur;pos=(S.position.p||0)+(p?(Date.now()-S.posAt)/1000*(S.position.r||1):0);}
if(!t){var d=document.title||'';var i=d.indexOf(' • ');if(i>0){var t2=d.slice(0,i).trim(),a2=d.slice(i+3).trim();if(t2&&a2&&t2!=='Spotify'&&a2.indexOf('Spotify')!==0){t=t2;ar=a2;}}}
try{u=u?new URL(u,location.href).href:''}catch(e){u=''}
return JSON.stringify({t:t,a:ar,i:u,p:p,pos:pos,dur:dur});
})()
""".trimIndent()

    /**
     * Next / previous song. The now-playing bar of this page has no such buttons; they live in the full player view, which
     * only exists while it is open. So: open it (tap on the bar), press the button, close it again (its first button is the
     * back arrow). If it is already open, it stays open.
     */
    private fun skipJs(testId: String) = """
(function(id){function q(x){return document.querySelector('[data-testid="'+x+'"]')}
var had=!!q('npv-container');
function act(){var b=q(id);if(b)b.click();if(!had){setTimeout(function(){var c=q('npv-container');var back=c&&c.querySelector('button');if(back)back.click();},450);}}
if(had){act();return;}
var bar=q('floating-now-playing-bar');if(!bar)return;bar.click();setTimeout(act,600);})('$testId')
""".trimIndent()

    /** The page's own play/pause: its audio element if it has one, else the button of its now-playing bar (a play-triangle icon means paused). */
    private fun playPauseJs(want: String) = """
(function(want){var a=(window.__sdMS&&window.__sdMS.media)||document.querySelector('audio,video');if(a){if(want==='play')a.play();else a.pause();return;}
var bar=document.querySelector('[data-testid="floating-now-playing-bar"]');if(!bar)return;
var bb=bar.querySelectorAll('button');if(!bb.length)return;var pb=bb[bb.length-1];var pp=pb.querySelector('path');
var paused=pp&&(pp.getAttribute('d')||'').indexOf('m7.05 3.606')===0;
if((want==='play'&&paused)||(want==='pause'&&!paused))pb.click();})('$want')
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

    /** play / pause, done on the page itself (this page has no previous / next buttons in its now-playing bar). */
    fun control(what: String) {
        val js = when (what) {
            "play", "pause" -> handlerJs(what, playPauseJs(what))
            "next" -> handlerJs("nexttrack", skipJs("npv-skip-forward-button"))
            "prev" -> handlerJs("previoustrack", skipJs("npv-skip-previous-button"))
            else -> if (what.startsWith("seek:")) {
                val sec = what.removePrefix("seek:").toDoubleOrNull() ?: return
                "var S=window.__sdMS;var h=S&&S.handlers&&S.handlers['seekto'];if(h){h({action:'seekto',seekTime:$sec})}else{var a=(S&&S.media)||document.querySelector('audio,video');if(a)a.currentTime=$sec}"
            } else return
        }
        web?.post { web?.evaluateJavascript(js, null) }
    }

    /** Pauses the page's audio (the notification's Stop button, the in-app remove button, clearing the music data). */
    fun pause() {
        web?.evaluateJavascript(playPauseJs("pause"), null)
    }
}
