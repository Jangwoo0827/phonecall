# One-off: remove the old Deezer/Audius/YouTube-link music code; the Music tab is Spotify's web player only.
import os
import re
import glob

ROOT = r"C:\Users\LENOVO\Desktop\VScode\phonecall"
os.chdir(ROOT)
APP = os.path.join("app", "src", "main", "java", "com", "example", "superdialer")


def rw(path, fn):
    s = open(path, encoding="utf-8", newline="").read()
    crlf = "\r\n" in s
    s = s.replace("\r\n", "\n")
    s = fn(s)
    if crlf:
        s = s.replace("\n", "\r\n")
    open(path, "w", encoding="utf-8", newline="").write(s)


def sub(s, old, new=""):
    assert old in s, old[:80]
    return s.replace(old, new, 1)


def cut(s, start, end, keep_end=True):
    a = s.index(start)
    b = s.index(end, a)
    return s[:a] + (s[b:] if keep_end else s[b + len(end):])


# ---- delete files
keep = {"SpotifyWeb.kt", "MusicScreen.kt", "SpotifyScreen.kt"}
for d in (os.path.join(APP, "music"), os.path.join(APP, "music", "ui")):
    for f in glob.glob(os.path.join(d, "*.kt")):
        if os.path.basename(f) not in keep:
            os.remove(f)
for f in glob.glob(os.path.join("app", "src", "test", "java", "com", "example", "superdialer", "music", "*.kt")):
    os.remove(f)

# ---- browser: "add to music" menu item
def bs(s):
    s = cut(s, "                onAddToMusic = if (", "            )\n        }\n    }", True)
    s = sub(s, "    /** Shown on a YouTube video page: saves the video as a link song of the Music tab. */\n    onAddToMusic: (() -> Unit)? = null,\n")
    a = s.index("            if (onAddToMusic != null) {")
    b = s.index("            if (onFind != null || onToggleDesktop != null || onAddToMusic != null) HorizontalDivider()")
    s = s[:a] + "            if (onFind != null || onToggleDesktop != null) HorizontalDivider()" + s[b + len("            if (onFind != null || onToggleDesktop != null || onAddToMusic != null) HorizontalDivider()"):]
    return s


rw(os.path.join(APP, "browser", "BrowserScreen.kt"), bs)


# ---- app shell
def app(s):
    for imp in ("import com.example.superdialer.music.ui.MiniPlayer\n", "import com.example.superdialer.music.ui.MusicOverlays\n", "import com.example.superdialer.music.ui.MusicUi\n"):
        s = sub(s, imp)
    s = cut(s, "    // A page of the Music tab asked for from elsewhere", "    // A website open in the browser takes the whole screen")
    s = sub(s, "{ Column { MiniPlayer(); NavigationBar(", "{ Column { NavigationBar(")
    s = sub(s, "        MusicOverlays()\n")
    return s


rw(os.path.join(APP, "navigation", "SuperDialerApp.kt"), app)


# ---- settings
def st(s):
    return cut(s, "        SettingsGroup(str(R.string.music_settings_title)) {", "        SettingsGroup(str(R.string.t_permissions)) {")


rw(os.path.join(APP, "settings", "SettingsScreen.kt"), st)


def aset(s):
    s = sub(s, '    private const val KEY_PREFER_FULL = "music_prefer_full"\n')
    s = re.sub(r"    var preferFullSongs by mutableStateOf\(true\)\n", "", s)
    s = sub(s, "        preferFullSongs = p.getBoolean(KEY_PREFER_FULL, true)\n")
    s = sub(s, "    fun updatePreferFullSongs(enabled: Boolean) {\n        preferFullSongs = enabled\n        prefs?.edit()?.putBoolean(KEY_PREFER_FULL, enabled)?.apply()\n    }\n\n")
    return s


rw(os.path.join(APP, "settings", "AppSettings.kt"), aset)


def appc(s):
    for l in ("MusicStore.init(this)", "MusicPlayer.init(this)", "YoutubePlayback.init(this)"):
        s = sub(s, "        com.example.superdialer.music.%s\n" % l)
    return s


rw(os.path.join(APP, "SuperDialerApplication.kt"), appc)


# ---- manifest
def man(s):
    s = sub(s, "        <!-- Spotify, for \"Search on Spotify\" in a song's menu (music/MusicUi.kt) -->\n        <package android:name=\"com.spotify.music\" />\n")
    s = cut(s, "        <!-- Music tab playback (media3 session + notification)", "        <!-- Keeps Spotify's web player playing")
    s = s.replace('    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />\n', "")
    return s


rw(os.path.join("app", "src", "main", "AndroidManifest.xml"), man)

# ---- strings: drop music_* keys that nothing references any more
code = ""
for f in glob.glob(os.path.join("app", "src", "main", "java", "**", "*.kt"), recursive=True):
    code += open(f, encoding="utf-8").read()
code += open(os.path.join("app", "src", "main", "AndroidManifest.xml"), encoding="utf-8").read()
removed = 0
for lang in ("values", "values-ko"):
    p = os.path.join("app", "src", "main", "res", lang, "strings_manual.xml")

    def fix(s):
        global removed
        def repl(m):
            global removed
            key = m.group(1)
            if key.startswith("music_") and ("R.string." + key) not in code:
                removed += 1
                return ""
            return m.group(0)
        return re.sub(r'    <string name="([^"]+)">.*?</string>\n', repl, s, flags=re.S)
    rw(p, fix)
print("removed string entries:", removed)
