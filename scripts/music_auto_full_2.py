# Second half of the "prefer full songs" patch (the setting and its texts were applied by music_auto_full.py).
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

ANCHOR = "    /**\n     * The full-length version of [track], or null when Audius does not have it."
LOOKUP = """    private val lookups = HashMap<String, String?>()

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

"""
rw(os.path.join(M, "music", "AudiusApi.kt"), lambda s: sub(s, ANCHOR, LOOKUP + ANCHOR))


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

U = os.path.join(M, "music", "ui")
rw(os.path.join(U, "NowPlayingScreen.kt"), lambda s: sub(s, "        if (track.isPreview) {\n            val scope", "        if (track.isPreview && track.id !in MusicPlayer.fullIds) {\n            val scope"))
rw(os.path.join(U, "MusicOverlays.kt"), lambda s: sub(s, "        if (track.isPreview) {\n            SheetAction(Icons.Filled.GraphicEq", "        if (track.isPreview && track.id !in MusicPlayer.fullIds) {\n            SheetAction(Icons.Filled.GraphicEq"))
print("patched")
