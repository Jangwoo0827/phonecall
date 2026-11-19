# One-off patch: Spotify's web player becomes a chip of the Music tab (instead of opening a browser tab).
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


APP = os.path.join("app", "src", "main", "java", "com", "example", "superdialer")
UI = os.path.join(APP, "music", "ui")
RES = os.path.join("app", "src", "main", "res")


# ---- WebAudioKeepAlive: several sources (the browser's Spotify tab, the Music tab's page)
def keepalive(s):
    a = s.index("    /** True while a normal (non-private) tab shows the web player")
    b = s.index("class WebAudioService")
    new = '''    private val sources = HashSet<String>()

    /** True while some page (a browser tab or the Music tab's player) is a web player that must keep playing. */
    @Volatile
    var active = false
        private set

    /** Pauses the audio of every web player (set by the browser view model and the Music tab). */
    var pauseAll: () -> Unit = {}

    fun isMusicUrl(url: String?) = url != null && url.startsWith(HOST)

    /** [source] says whether it needs the keep-alive; the service runs while any source does. */
    fun update(context: Context, source: String, needed: Boolean) {
        val changed = synchronized(sources) { if (needed) sources.add(source) else sources.remove(source) }
        if (!changed) return
        val want = synchronized(sources) { sources.isNotEmpty() }
        if (want == active) return
        active = want
        val app = context.applicationContext
        if (want) {
            runCatching { ContextCompat.startForegroundService(app, Intent(app, WebAudioService::class.java)) }
        } else {
            app.stopService(Intent(app, WebAudioService::class.java))
        }
    }

    /** The Stop button: forget every source. */
    fun clear(context: Context) {
        synchronized(sources) { sources.clear() }
        if (active) {
            active = false
            context.applicationContext.stopService(Intent(context.applicationContext, WebAudioService::class.java))
        }
    }
}

'''
    s = s[:a] + new + s[b:]
    s = sub(s, "            WebAudioKeepAlive.pauseAll()\n            WebAudioKeepAlive.update(this, false)\n            stopSelf()", "            WebAudioKeepAlive.pauseAll()\n            WebAudioKeepAlive.clear(this)\n            stopSelf()")
    return s


rw(os.path.join(APP, "browser", "WebAudioService.kt"), keepalive)

# the browser's own Spotify tab is one source; "pause all" pauses both
def vm(s):
    s = sub(s, "        WebAudioKeepAlive.update(getApplication(), open)", "        WebAudioKeepAlive.update(getApplication(), \"browser\", open)")
    s = sub(s, "            tabs.filter { WebAudioKeepAlive.isMusicUrl(it.url) }.forEach { t ->", "            com.example.superdialer.music.SpotifyWeb.pause()\n            tabs.filter { WebAudioKeepAlive.isMusicUrl(it.url) }.forEach { t ->")
    return s


rw(os.path.join(APP, "browser", "BrowserViewModel.kt"), vm)

rw(os.path.join(APP, "SuperDialerApplication.kt"), lambda s: sub(
    s,
    "        com.example.superdialer.music.YoutubePlayback.init(this)\n",
    "        com.example.superdialer.music.YoutubePlayback.init(this)\n        com.example.superdialer.music.SpotifyWeb.init(this)\n",
))


# ---- the Music tab: a fourth chip with the page
def screen(s):
    s = sub(s, "                    Triple(R.string.music_tab_library, Icons.Filled.LibraryMusic, \"music_tab_library\"),\n                )",
            "                    Triple(R.string.music_tab_library, Icons.Filled.LibraryMusic, \"music_tab_library\"),\n                    Triple(R.string.music_tab_spotify, Icons.Filled.MusicNote, \"music_tab_spotify\"),\n                )")
    s = sub(s, "                1 -> SearchScreen()\n                2 -> LibraryScreen()\n                else -> HomeScreen()",
            "                1 -> SearchScreen()\n                2 -> LibraryScreen()\n                3 -> SpotifyScreen()\n                else -> HomeScreen()")
    s = sub(s, "    BackHandler(enabled = MusicUi.stack.isNotEmpty()) { MusicUi.back() }",
            "    BackHandler(enabled = MusicUi.stack.isNotEmpty()) { MusicUi.back() }\n    // On the Spotify page, Back first walks the page's own history.\n    BackHandler(enabled = MusicUi.stack.isEmpty() && MusicUi.tab == 3 && com.example.superdialer.music.SpotifyWeb.canGoBack()) {\n        com.example.superdialer.music.SpotifyWeb.goBack()\n    }")
    s = sub(s, "import androidx.compose.material.icons.filled.LibraryMusic\n", "import androidx.compose.material.icons.filled.LibraryMusic\nimport androidx.compose.material.icons.filled.MusicNote\n")
    s = s.replace("                tabs.forEachIndexed { i, (label, icon, tag) ->", "                tabs.forEachIndexed { i, (label, icon, tag) ->")
    return s


rw(os.path.join(UI, "MusicScreen.kt"), screen)

SPOTIFY_SCREEN = '''package com.example.superdialer.music.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.superdialer.music.SpotifyWeb

/**
 * Spotify's web player inside the Music tab. The page itself is kept by [SpotifyWeb] (so the music goes on when another chip
 * is opened or the app is left); this only puts it on screen.
 */
@Composable
fun SpotifyScreen(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SpotifyWeb.attach(ctx)
            FrameLayout(ctx).apply {
                val web = SpotifyWeb.webView()
                (web.parent as? ViewGroup)?.removeView(web)
                addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        onRelease = { frame ->
            frame.removeAllViews()
            SpotifyWeb.detach()
        },
    )
}
'''
open(os.path.join(UI, "SpotifyScreen.kt"), "w", encoding="utf-8", newline="").write(SPOTIFY_SCREEN)


# ---- Home row switches to the chip
rw(os.path.join(UI, "HomeScreen.kt"), lambda s: sub(
    s,
    "                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {\n                    MusicUi.pendingBrowserUrl = \"https://open.spotify.com/\"\n                },",
    "                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { MusicUi.tab = 3 },",
))


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


for lang, tab, desc in (
    ("values", "Spotify", "Spotify's web player, right here. Sign in with your own account."),
    ("values-ko", "Spotify", "Spotify 웹 플레이어를 여기서 바로 써요. 본인 계정으로 로그인하세요."),
):
    def fix(s, tab=tab, desc=desc):
        s = re.sub(r'(<string name="music_spotify_web_desc">)[^<]*(</string>)', lambda m: m.group(1) + esc(desc) + m.group(2), s)
        if "music_tab_spotify" not in s:
            s = s.replace("</resources>", '    <string name="music_tab_spotify">%s</string>\n</resources>' % esc(tab))
        return s
    rw(os.path.join(RES, lang, "strings_manual.xml"), fix)
print("patched")
