# One-off patch: Spotify's web player opens as the normal mobile page (as in Chrome) and keeps playing in the background.
import os


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
RES = os.path.join("app", "src", "main", "res")

# the mobile page, not PC mode (the same as Chrome on the phone)
rw(os.path.join(APP, "music", "ui", "HomeScreen.kt"), lambda s: sub(s, "                    MusicUi.pendingBrowserDesktop = true\n", ""))

# the browser keeps the page playing
def vm(s):
    # tell the service which tabs to pause, and keep it in step with the open tabs
    s = sub(s, "    fun closeTab(id: Int) {", """    /** Starts or stops the background keep-alive for Spotify's web player depending on whether such a tab is open. */
    private fun syncWebAudio() {
        val open = tabs.any { !it.isPrivate && WebAudioKeepAlive.isMusicUrl(it.url) }
        WebAudioKeepAlive.pauseAll = {
            tabs.filter { WebAudioKeepAlive.isMusicUrl(it.url) }.forEach { t ->
                webViews[t.id]?.evaluateJavascript("document.querySelectorAll('audio,video').forEach(function(e){e.pause()})", null)
            }
        }
        WebAudioKeepAlive.update(getApplication(), open)
    }

    fun closeTab(id: Int) {""")
    s = sub(s, "        ensureNormalTab()\n    }\n\n    /** Closes every private tab", "        ensureNormalTab()\n        syncWebAudio()\n    }\n\n    /** Closes every private tab")
    s = sub(s, "        if (tabs.none { it.id == selectedId }) selectedId = tabs.first { !it.isPrivate }.id\n    }", "        if (tabs.none { it.id == selectedId }) selectedId = tabs.first { !it.isPrivate }.id\n        syncWebAudio()\n    }")
    s = sub(s, "            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {\n                url?.let { tab.url = it }\n", "            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {\n                url?.let { tab.url = it }\n                syncWebAudio()\n")
    return s


rw(os.path.join(APP, "browser", "BrowserViewModel.kt"), vm)

# the page is not paused when the app leaves the screen
rw(os.path.join(APP, "browser", "BrowserScreen.kt"), lambda s: sub(
    s,
    "        onPauseOrDispose { webView.onPause() }",
    "        // Spotify's web player keeps playing in the background (see WebAudioKeepAlive); every other page is paused.\n        onPauseOrDispose { if (!WebAudioKeepAlive.isMusicUrl(tab.url) || tab.isPrivate) webView.onPause() }",
))

# the 3-minute release timer does not end a playing web player
def session(s):
    s = sub(s, "    private val expire = Runnable { release() }", "    private val expire = Runnable {\n        // Spotify's web player keeps the browser alive; look again later instead of releasing it.\n        if (WebAudioKeepAlive.active) handler.postDelayed(this.expire, KEEP_ALIVE_MS) else release()\n    }")
    return s


rw(os.path.join(APP, "browser", "BrowserSession.kt"), session)

# manifest: the service
def man(s):
    return sub(s, '        <service\n            android:name=".kakao.KakaoNotificationService"', '''        <!-- Keeps Spotify's web player playing in the background (browser/WebAudioService.kt). -->
        <service
            android:name=".browser.WebAudioService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <service
            android:name=".kakao.KakaoNotificationService"''')


rw(os.path.join("app", "src", "main", "AndroidManifest.xml"), man)


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


NEW = [
    ("music_web_player_running", "Web player playing", "웹 플레이어 재생 중"),
    ("music_web_player_running_text", "Spotify keeps playing in the background. Tap to open.", "Spotify가 백그라운드에서 계속 재생돼요. 눌러서 열기."),
    ("music_web_player_stop", "Stop", "정지"),
]
OLD = {
    "music_spotify_web_desc": ("Opens open.spotify.com in the browser. Sign in with your own account.", "브라우저에서 open.spotify.com을 열어요. 본인 계정으로 로그인하세요."),
}
import re
for lang, idx in (("values", 1), ("values-ko", 2)):
    def fix(s, idx=idx):
        for key, vals in OLD.items():
            s = re.sub(r'(<string name="%s">)[^<]*(</string>)' % key, lambda m: m.group(1) + esc(vals[idx - 1]) + m.group(2), s)
        if "music_web_player_running" not in s:
            s = s.replace("</resources>", "".join('    <string name="%s">%s</string>\n' % (r[0], esc(r[idx])) for r in NEW) + "</resources>")
        return s
    rw(os.path.join(RES, lang, "strings_manual.xml"), fix)
print("patched")
