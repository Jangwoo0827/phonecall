# One-off patch: "Spotify" entry in the Music tab opens the official web player (open.spotify.com) in the app's browser in PC mode,
# and the browser lets that one site use protected-media (Widevine) playback.
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


# ---- browser: DRM only for open.spotify.com; a way to open a tab in PC mode
def vm(s):
    s = sub(s, """            override fun onPermissionRequest(request: PermissionRequest) {
                val origin = SitePermissions.normalizeOrigin(request.origin.toString())
""", """            override fun onPermissionRequest(request: PermissionRequest) {
                val origin = SitePermissions.normalizeOrigin(request.origin.toString())
                // Spotify's own web player needs protected-media (DRM) playback to play music; allowed for that one site only.
                if (origin == SPOTIFY_ORIGIN && request.resources.all { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
                    request.grant(request.resources)
                    return
                }
""")
    s = sub(s, "    fun openExternal(url: String) {\n        externalUrl = url\n    }", """    fun openExternal(url: String, desktop: Boolean = false) {
        externalDesktop = desktop
        externalUrl = url
    }

    /** Whether the link waiting in [externalUrl] should open in PC mode. */
    var externalDesktop = false
        private set""")
    s = sub(s, "    fun setDesktopMode(tab: BrowserTab, on: Boolean) {", """    /** Opens [url] in a new tab already in PC mode (a site that only works as its desktop page, such as Spotify's web player). */
    fun newDesktopTab(url: String): BrowserTab {
        val tab = newTab()
        tab.desktopMode = true
        webViewFor(tab).settings.userAgentString = UserAgents.desktop(defaultUserAgent)
        load(url)
        return tab
    }

    fun setDesktopMode(tab: BrowserTab, on: Boolean) {""")
    s = s.rstrip() + '\n\nprivate const val SPOTIFY_ORIGIN = "https://open.spotify.com"\n'
    return s


rw(os.path.join(APP, "browser", "BrowserViewModel.kt"), vm)

rw(os.path.join(APP, "browser", "BrowserScreen.kt"), lambda s: sub(
    s,
    "        viewModel.consumeExternal()?.let { viewModel.newTab(it) }",
    "        val desktop = viewModel.externalDesktop\n        viewModel.consumeExternal()?.let { if (desktop) viewModel.newDesktopTab(it) else viewModel.newTab(it) }",
))

# ---- the app scaffold passes the PC-mode request through
rw(os.path.join(APP, "navigation", "SuperDialerApp.kt"), lambda s: sub(
    s,
    "            browserViewModel.openExternal(url)\n            navigateToTab(TopLevelDestination.Hub.route)\n            MusicUi.pendingBrowserUrl = null",
    "            browserViewModel.openExternal(url, MusicUi.pendingBrowserDesktop)\n            navigateToTab(TopLevelDestination.Hub.route)\n            MusicUi.pendingBrowserUrl = null\n            MusicUi.pendingBrowserDesktop = false",
))
rw(os.path.join(APP, "music", "ui", "MusicUi.kt"), lambda s: sub(
    s,
    "    var pendingBrowserUrl by mutableStateOf<String?>(null)\n",
    "    var pendingBrowserUrl by mutableStateOf<String?>(null)\n    var pendingBrowserDesktop by mutableStateOf(false)\n",
))

# ---- Music home: a Spotify row
def home(s):
    s = sub(s, "        val recents = MusicStore.recents.toList()\n", """        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                    MusicUi.pendingBrowserDesktop = true
                    MusicUi.pendingBrowserUrl = "https://open.spotify.com/"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(Color(0xFF1DB954), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.Black)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(str(R.string.music_spotify_web), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(str(R.string.music_spotify_web_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val recents = MusicStore.recents.toList()
""")
    for imp in ("androidx.compose.foundation.background", "androidx.compose.foundation.layout.Box", "androidx.compose.foundation.shape.CircleShape", "androidx.compose.material.icons.Icons", "androidx.compose.material.icons.filled.MusicNote", "androidx.compose.material3.Icon", "androidx.compose.ui.graphics.Color"):
        if "import " + imp + "\n" not in s:
            s = sub(s, "import androidx.compose.foundation.clickable\n", "import androidx.compose.foundation.clickable\nimport " + imp + "\n")
    return s


rw(os.path.join(APP, "music", "ui", "HomeScreen.kt"), home)


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


NEW = [
    ("music_spotify_web", "Spotify web player", "Spotify 웹 플레이어"),
    ("music_spotify_web_desc", "Opens open.spotify.com in PC mode. Sign in with your own account.", "PC 모드로 open.spotify.com을 열어요. 본인 계정으로 로그인하세요."),
]
for lang, idx in (("values", 1), ("values-ko", 2)):
    def add(s, idx=idx):
        if "music_spotify_web" in s:
            return s
        return s.replace("</resources>", "".join('    <string name="%s">%s</string>\n' % (r[0], esc(r[idx])) for r in NEW) + "</resources>")
    rw(os.path.join(RES, lang, "strings_manual.xml"), add)
print("patched")
