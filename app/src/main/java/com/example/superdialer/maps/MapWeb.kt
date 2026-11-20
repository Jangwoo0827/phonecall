package com.example.superdialer.maps

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewAssetLoader
import com.example.superdialer.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * The map picture of the Map section: T map's vector map (SK open API JavaScript map) in one app-level WebView that shows
 * only our own page (assets/map/index.html). Everything around it (search, route cards, buttons) is native; this object only
 * sends the page short commands (centre, markers, route lines) through evaluateJavascript and reads its console lines
 * ("SDEV|{json}"). There is no JavaScript bridge, so the page cannot call into the app, and no other site can be opened in it.
 */
object MapWeb {
    private const val HOST = "appassets.androidplatform.net"
    private const val PAGE = "https://$HOST/assets/map/index.html"

    private lateinit var app: Context
    private var web: WebView? = null
    private val pending = ArrayList<String>()

    /** True once the page's map exists and accepts commands. Compose state. */
    var ready by mutableStateOf(false)
        private set

    /** What went wrong while loading the map (no-key, sdk-load-failed, ...), or null. Compose state. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Called when the map becomes ready (again), so the screen can redraw its pins and route. */
    var onReady: (() -> Unit)? = null

    /** Called with (lat, lng) when the user taps the map. */
    var onMapClick: ((Double, Double) -> Unit)? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    val hasKey: Boolean get() = BuildConfig.TMAP_APP_KEY.isNotBlank()

    @SuppressLint("SetJavaScriptEnabled")
    fun webView(): WebView = web ?: WebView(MutableContextWrapper(app)).also { v ->
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        v.setBackgroundColor(Color.rgb(233, 236, 239))
        val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app))
            .build()
        v.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            // The page never navigates away on its own; nothing else may be opened in this view.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        v.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val text = message.message()
                if (!text.startsWith("SDEV|")) return false
                handleEvent(text.removePrefix("SDEV|"))
                return true
            }
        }
        web = v
        if (hasKey) {
            v.loadUrl("$PAGE?k=${android.net.Uri.encode(BuildConfig.TMAP_APP_KEY)}")
        } else {
            error = "no-key"
        }
    }.also { web = it }

    private fun handleEvent(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        when {
            o.has("ready") -> {
                ready = true
                error = null
                pending.forEach { web?.evaluateJavascript(it, null) }
                pending.clear()
                onReady?.invoke()
            }
            o.has("error") -> error = o.optString("error")
            o.has("click") -> o.optJSONArray("click")?.let { onMapClick?.invoke(it.optDouble(0), it.optDouble(1)) }
        }
    }

    fun attach(activity: Context) {
        (webView().context as? MutableContextWrapper)?.baseContext = activity
    }

    fun detach() {
        (web?.context as? MutableContextWrapper)?.baseContext = app
    }

    /** Runs a command on the page once its map is ready (earlier commands wait). */
    private fun run(js: String) {
        val v = web
        if (v != null && ready) v.evaluateJavascript(js, null) else pending.add(js)
    }

    fun center(lat: Double, lng: Double, zoom: Int? = null) = run("SD.center($lat,$lng,${zoom ?: "null"})")

    fun showMe(lat: Double, lng: Double) = run("SD.me($lat,$lng)")

    fun showPin(lat: Double, lng: Double) = run("SD.pin($lat,$lng)")

    fun clearPin() = run("SD.clearPin()")

    fun clearRoute() = run("SD.clearRoute()")

    /** Draws a route: one coloured line per leg, plus small dots where the rider changes vehicle, and fits the view to it. */
    fun showRoute(legs: List<RouteLeg>, stops: List<LatLng>) {
        val legArray = JSONArray()
        legs.forEach { leg ->
            legArray.put(
                JSONObject()
                    .put("c", leg.cssColor)
                    .put("w", if (leg.mode == LegMode.Walk) 5 else 8)
                    .put("p", JSONArray().also { arr -> leg.points.forEach { arr.put(JSONArray().put(it.lat).put(it.lng)) } }),
            )
        }
        val stopArray = JSONArray().also { arr -> stops.forEach { arr.put(JSONArray().put(it.lat).put(it.lng)) } }
        run("SD.route($legArray,$stopArray)")
    }

    /** Frees the page (the app was left for a while); the next visit loads the map again. */
    fun release() {
        web?.apply {
            stopLoading()
            destroy()
        }
        web = null
        ready = false
        error = null
        pending.clear()
    }
}
