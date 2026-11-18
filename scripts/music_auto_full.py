# One-off patch: online songs try the full-length version first ("Prefer full songs" setting), previews only as a fallback.
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
    assert old in s, old[:70]
    return s.replace(old, new, 1)


M = os.path.join("app", "src", "main", "java", "com", "example", "superdialer")
RES = os.path.join("app", "src", "main", "res")

# --- setting
def settings(s):
    s = sub(s, '    private const val KEY_QUICK_DIRECT = "quick_dial_direct"\n', '    private const val KEY_QUICK_DIRECT = "quick_dial_direct"\n    private const val KEY_PREFER_FULL = "music_prefer_full"\n')
    s = sub(s, "    /** Whether the first-run", """    /** Music: play the full-length version of an online song when one exists, the 30-second preview only otherwise. */
    var preferFullSongs by mutableStateOf(true)
        private set

    /** Whether the first-run""")
    s = sub(s, "        quickDialCallsDirectly = p.getBoolean(KEY_QUICK_DIRECT, false)\n", "        quickDialCallsDirectly = p.getBoolean(KEY_QUICK_DIRECT, false)\n        preferFullSongs = p.getBoolean(KEY_PREFER_FULL, true)\n")
    s = sub(s, "    fun updateQuickDialCallsDirectly(enabled: Boolean) {", """    fun updatePreferFullSongs(enabled: Boolean) {
        preferFullSongs = enabled
        prefs?.edit()?.putBoolean(KEY_PREFER_FULL, enabled)?.apply()
    }

    fun updateQuickDialCallsDirectly(enabled: Boolean) {""")
    return s


rw(os.path.join(M, "settings", "AppSettings.kt"), settings)


def settings_screen(s):
    return sub(s, "            var confirmClear by remember { mutableStateOf(false) }\n", """            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.music_prefer_full)) },
                supportingContent = { Text(str(R.string.music_prefer_full_desc)) },
                trailingContent = {
                    Switch(checked = AppSettings.preferFullSongs, onCheckedChange = AppSettings::updatePreferFullSongs)
                },
            )
            var confirmClear by remember { mutableStateOf(false) }
""")


rw(os.path.join(M, "settings", "SettingsScreen.kt"), settings_screen)

STRINGS = [
    ("music_prefer_full", "Prefer full songs", "전곡 우선 재생"),
    ("music_prefer_full_desc", "Look for the full-length version of online songs first and fall back to the 30-second preview only when there is none", "온라인 곡은 전곡 버전을 먼저 찾고, 없을 때만 30초 미리듣기로 재생해요"),
]


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


for lang, idx in (("values", 1), ("values-ko", 2)):
    def add(s, idx=idx):
        if "music_prefer_full" in s:
            return s
        return s.replace("</resources>", "".join('    <string name="%s">%s</string>\n' % (r[0], esc(r[idx])) for r in STRINGS) + "</resources>")
    rw(os.path.join(RES, lang, "strings_manual.xml"), add)


# --- Audius blocking lookup with a memory of hits and misses
def audius(s):
    return sub(s, "    /** The full-length version of [track], or null", """    private val lookups = HashMap<String, String?>()

    /**
     * Stream URL of the full version of [track] for the player's loading thread (blocking, at most one search), or null.
     * Hits and misses are remembered for the session so a song is looked up once.
     */
    fun fullStreamUrlBlocking(track: Track): String? {
        synchronized(lookups) { if (lookups.containsKey(track.id)) return lookups[track.id] }
        val url = try {
            val found = AudiusJson.tracks(getBlocking("/tracks/search?query=${enc("${track.title} ${track.artist}")}&limit=10"))
            AudiusJson.bestMatch(track, found)?.uri
        } catch (e: IOException) {
            return null // network trouble: not remembered, so the next try can still succeed
        }
        synchronized(lookups) { lookups[track.id] = url }
        return url
    }

    /** The full-length version of [track], or null""")


rw(os.path.join(M, "music", "AudiusApi.kt"), audius)


# --- player: resolver prefers the full version; remember which songs play in full
def player(s):
    s = sub(s, """    private val known = HashMap<String, Track>()
""", """    private val known = HashMap<String, Track>()

    /** Ids of online songs whose full-length version is the one being played (the preview badge is hidden for them). */
    val fullIds = androidx.compose.runtime.mutableStateListOf<String>()
""")
    s = sub(s, """                val id = spec.uri.lastPathSegment?.toLongOrNull() ?: throw IOException("bad track")
                val url = DeezerApi.freshPreviewBlocking(id) ?: throw IOException("no preview")
                spec.withUri(Uri.parse(url))""", """                val id = spec.uri.lastPathSegment?.toLongOrNull() ?: throw IOException("bad track")
                val track = known["dz:$id"]
                val full = if (track != null && com.example.superdialer.settings.AppSettings.preferFullSongs) {
                    AudiusApi.fullStreamUrlBlocking(track)
                } else {
                    null
                }
                if (full != null) {
                    handler.post { if (track!!.id !in fullIds) fullIds.add(track.id) }
                    spec.withUri(Uri.parse(full))
                } else {
                    val url = DeezerApi.freshPreviewBlocking(id) ?: throw IOException("no preview")
                    spec.withUri(Uri.parse(url))
                }""")
    return s


rw(os.path.join(M, "music", "MusicPlayer.kt"), player)

# --- UI: the preview badge only for songs that really are previews
U = os.path.join(M, "music", "ui")
rw(os.path.join(U, "NowPlayingScreen.kt"), lambda s: sub(s, "        if (track.isPreview) {\n            val scope", "        if (track.isPreview && track.id !in MusicPlayer.fullIds) {\n            val scope"))
rw(os.path.join(U, "MusicOverlays.kt"), lambda s: sub(s, "        if (track.isPreview) {\n            SheetAction(Icons.Filled.GraphicEq", "        if (track.isPreview && track.id !in MusicPlayer.fullIds) {\n            SheetAction(Icons.Filled.GraphicEq"))
print("patched")
