# One-off patch: link songs play the regular m.youtube.com watch page (shown in the cover's place) instead of the IFrame embed.
import os
import re


def rw(path, fn):
    s = open(path, encoding="utf-8", newline="").read()
    crlf = "\r\n" in s
    s = s.replace("\r\n", "\n")
    s = fn(s)
    if crlf:
        s = s.replace("\n", "\r\n")
    open(path, "w", encoding="utf-8", newline="").write(s)


def sub(s, old, new):
    assert old in s, old[:80]
    return s.replace(old, new, 1)


M = os.path.join("app", "src", "main", "java", "com", "example", "superdialer", "music")


def yp(s):
    # no more local page / referrer origin
    s = sub(s, '        v.loadDataWithBaseURL(ORIGIN + "/", PAGE, "text/html", "utf-8", null)\n', "")
    a = s.index("    /** YouTube wants an embedding app to identify itself")
    s = s[:a].rstrip() + "\n}\n"
    s = sub(s, "    private var pendingId: String? = null\n", "")
    s = sub(s, "                pageReady = false\n", "                // a new page: the video is not there yet\n")
    s = s.replace("    private var pageReady = false\n", "")
    # starting a track = opening its watch page
    s = sub(s, "        pendingId = idOf(t)\n        loadedAt = System.currentTimeMillis()\n        stalled = false\n        tick()",
            "        loadedAt = System.currentTimeMillis()\n        stalled = false\n        webView().loadUrl(\"https://m.youtube.com/watch?v=${idOf(t)}\")\n        tick()")
    s = sub(s, "        pendingId = null\n", "        web?.loadUrl(\"about:blank\")\n")
    s = sub(s, '        if (isPlaying) pause() else if (current != null) js("__sd.play()")', '        if (isPlaying) pause() else if (current != null) js(PLAY)')
    s = sub(s, '        if (web != null && pageReady) js("__sd.pause()")', '        if (web != null) js(PAUSE)')
    s = sub(s, '        js("__sd.seek(${ms / 1000.0})")', '        js("(function(){var v=document.querySelector(\'video\');if(v)v.currentTime=${ms / 1000.0};})()")')
    # the poll: read the page's <video>
    start = s.index("    private val poll = object : Runnable {")
    end = s.index("    private fun tick() {")
    new_poll = '''    /** Reads the page's video: [paused, ended, time, duration, readyState], or null while there is none (yet). */
    private const val READ = "(function(){var v=document.querySelector('video');return v?[v.paused?1:0,v.ended?1:0,v.currentTime||0,v.duration||0,v.readyState]:null})()"
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

'''
    s = s[:start] + new_poll + s[end:]
    # the constants above sit inside the object: allowed
    # main-frame navigation: YouTube and its sign-in / consent hosts only
    s = s.replace('host.endsWith(".youtube-nocookie.com") ||\n                    host.endsWith(".ytimg.com") || host.endsWith(".google.com") || host.endsWith(".gstatic.com")', 'host.endsWith(".ytimg.com") || host.endsWith(".google.com") || host.endsWith(".gstatic.com") || host.endsWith(".googlevideo.com")')
    return s


rw(os.path.join(M, "YoutubePlayback.kt"), yp)
print("patched")
