# One-off patch that added the full-song actions (Audius, YouTube) and the VALORANT shelf to the Music UI.
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


U = os.path.join("app", "src", "main", "java", "com", "example", "superdialer", "music", "ui")
RES = os.path.join("app", "src", "main", "res")

STRINGS = [
    ("music_looking_for_full", "Looking for the full song…", "전곡을 찾는 중이에요…"),
    ("music_play_full", "Play full song", "전곡으로 재생"),
    ("music_full_on_youtube", "Full song on YouTube", "YouTube에서 전곡 보기"),
    ("music_full_help_title", "No full version here", "여기엔 전곡이 없어요"),
    ("music_full_help_text", "This song isn't available in full on Audius (the free full-length source). You can look for it on YouTube instead.", "이 곡은 무료 전곡 음원인 Audius에 없어요. 대신 YouTube에서 찾아볼 수 있어요."),
    ("music_full_songs", "Full songs", "전곡 재생"),
    ("music_trending_full", "Full songs trending on Audius", "Audius에서 인기 있는 전곡"),
    ("music_full_note", "Full-length songs from independent artists on Audius", "Audius의 독립 아티스트 전곡 음원"),
    ("music_valorant_champions", "VALORANT Champions", "발로란트 챔피언스"),
    ("music_preview_tap_full", "30-second preview · tap for full song", "30초 미리듣기 · 눌러서 전곡 듣기"),
]


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


for lang, idx in (("values", 1), ("values-ko", 2)):
    def add(s, idx=idx):
        if "music_looking_for_full" in s:
            return s
        block = "".join('    <string name="%s">%s</string>\n' % (r[0], esc(r[idx])) for r in STRINGS)
        return s.replace("</resources>", block + "</resources>")
    rw(os.path.join(RES, lang, "strings_manual.xml"), add)


def overlays(s):
    s = sub(s, """        SheetAction(Icons.Filled.Share, str(R.string.music_share)) { shareTrack(context, track); close() }""", """        if (track.isPreview) {
            SheetAction(Icons.Filled.GraphicEq, str(R.string.music_play_full)) { close(); playFullVersion(context, scope, track) }
            SheetAction(Icons.Filled.OpenInNew, str(R.string.music_full_on_youtube)) { openFullOnYoutube(context, track); close() }
        }
        SheetAction(Icons.Filled.Share, str(R.string.music_share)) { shareTrack(context, track); close() }""")
    s = sub(s, """private fun TrackMenu(track: Track) {
    val context = LocalContext.current""", """private fun TrackMenu(track: Track) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()""")
    s = sub(s, "import androidx.compose.material.icons.filled.Favorite\n", "import androidx.compose.material.icons.filled.Favorite\nimport androidx.compose.material.icons.filled.GraphicEq\n")
    s = sub(s, "    MusicUi.createPlaylistWith?.let { initial -> CreatePlaylistDialog(initial) }", """    MusicUi.fullHelpTrack?.let { t ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { MusicUi.fullHelpTrack = null },
            title = { Text(str(R.string.music_full_help_title)) },
            text = { Text(str(R.string.music_full_help_text)) },
            confirmButton = { TextButton(onClick = { openFullOnYoutube(context, t); MusicUi.fullHelpTrack = null }) { Text(str(R.string.music_full_on_youtube)) } },
            dismissButton = { TextButton(onClick = { MusicUi.fullHelpTrack = null }) { Text(str(R.string.cancel)) } },
        )
    }

    MusicUi.createPlaylistWith?.let { initial -> CreatePlaylistDialog(initial) }""")
    return s


rw(os.path.join(U, "MusicOverlays.kt"), overlays)


def now_playing(s):
    start = s.index("        if (!track.isLocal) {\n            Text(\n                str(R.string.music_preview_note),")
    end = s.index("        Spacer(Modifier.height(8.dp))\n        SeekBar()")
    new = """        if (track.isPreview) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Text(
                str(R.string.music_preview_tap_full),
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable { playFullVersion(context, scope, track) }
                    .background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
"""
    s = s[:start] + new + s[end:]
    return sub(s, "import androidx.compose.ui.graphics.lerp\n", "import androidx.compose.ui.graphics.lerp\nimport androidx.compose.ui.draw.clip\n")


rw(os.path.join(U, "NowPlayingScreen.kt"), now_playing)


def home(s):
    s = sub(s, """    val genres = rememberLoad("genres") { DeezerApi.genres() }
    LazyColumn(""", """    val genres = rememberLoad("genres") { DeezerApi.genres() }
    val full = rememberLoad("audius-trending") { AudiusApi.trending(25) }
    val valorant = rememberLoad("valorant") { com.example.superdialer.music.ValorantChampions.load() }
    LazyColumn(""")
    s = sub(s, """        item {
            LoadedContent(chart) { c ->""", """        valorant.value?.takeIf { it.isNotEmpty() }?.let { songs ->
            item { TrackShelf(str(R.string.music_valorant_champions), songs, onPlay = { MusicPlayer.play(songs, it) }) }
        }
        full.value?.takeIf { it.isNotEmpty() }?.let { songs ->
            item { TrackShelf(str(R.string.music_trending_full), songs, onPlay = { MusicPlayer.play(songs, it) }) }
        }
        item {
            LoadedContent(chart) { c ->""")
    return sub(s, "import com.example.superdialer.music.DeezerApi\n", "import com.example.superdialer.music.AudiusApi\nimport com.example.superdialer.music.DeezerApi\n")


rw(os.path.join(U, "HomeScreen.kt"), home)


def search(s):
    s = sub(s, """    val results = rememberLoad(query) { DeezerApi.search(query) }
    val labels = listOf(R.string.music_filter_all, R.string.music_filter_songs, R.string.music_filter_albums, R.string.music_filter_artists, R.string.music_filter_playlists)""", """    val results = rememberLoad(query) { DeezerApi.search(query) }
    val fullSongs = rememberLoad("au$query") { runCatching { AudiusApi.search(query, 25) }.getOrDefault(emptyList()) }
    val labels = listOf(R.string.music_filter_all, R.string.music_filter_songs, R.string.music_filter_albums, R.string.music_filter_artists, R.string.music_filter_playlists, R.string.music_full_songs)""")
    s = sub(s, """    LoadedContent(results) { r ->
        if (r.isEmpty) {""", """    if (filter == 5) {
        val list = fullSongs.value
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(str(R.string.music_no_results, query), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                item { Text(str(R.string.music_full_note), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                trackItems(list, list.size)
            }
        }
        return
    }
    LoadedContent(results) { r ->
        if (r.isEmpty) {""")
    s = sub(s, "            ResultsList(r, filter, query)", "            ResultsList(r, filter, query, fullSongs.value.orEmpty())")
    s = sub(s, "private fun ResultsList(r: SearchResults, filter: Int, query: String) {", "private fun ResultsList(r: SearchResults, filter: Int, query: String, full: List<Track>) {")
    s = sub(s, """                if (r.tracks.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_filter_songs)) }
                    trackItems(r.tracks, 5)
                }""", """                if (r.tracks.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_filter_songs)) }
                    trackItems(r.tracks, 5)
                }
                if (full.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_full_songs)) }
                    trackItems(full, 4)
                }""")
    return sub(s, "import com.example.superdialer.music.DeezerApi\n", "import com.example.superdialer.music.AudiusApi\nimport com.example.superdialer.music.DeezerApi\n")


rw(os.path.join(U, "SearchScreen.kt"), search)
print("patched")
