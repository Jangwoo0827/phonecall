# One-off patch: YouTube link songs (find on web YouTube / paste a link / add from the browser) and loop playlists.
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
MU = os.path.join(APP, "music")
UI = os.path.join(MU, "ui")
RES = os.path.join("app", "src", "main", "res")

# ---- strings ----------------------------------------------------------------------------------------------------
STRINGS = [
    ("music_add_link", "Add YouTube link", "YouTube 링크 추가"),
    ("music_link_title", "Add a song from a link", "링크로 곡 추가"),
    ("music_link_hint", "Paste a YouTube link", "YouTube 링크를 붙여 넣으세요"),
    ("music_link_add", "Add", "추가"),
    ("music_link_invalid", "That isn't a YouTube video link", "YouTube 영상 링크가 아니에요"),
    ("music_link_loading", "Looking up the video…", "영상을 찾는 중이에요…"),
    ("music_find_on_youtube", "Find \"%1$s\" on YouTube", "YouTube에서 \"%1$s\" 찾기"),
    ("music_find_on_youtube_hint", "Opens YouTube in the browser. On a video, menu ⋮ > Add to Music saves it as a link.", "브라우저에서 YouTube가 열려요. 영상에서 메뉴 ⋮ > 음악에 추가를 누르면 링크로 저장돼요."),
    ("music_add_to_music", "Add to Music", "음악에 추가"),
    ("music_link_song", "YouTube link \u2022 opens in the browser", "YouTube 링크 \u2022 브라우저에서 열려요"),
    ("music_loop", "Repeat forever", "계속 반복"),
    ("music_loop_desc", "Plays this playlist over and over", "이 플레이리스트를 끝나면 처음부터 계속 재생해요"),
    ("music_loop_on", "Repeating forever", "계속 반복 중"),
    ("music_link_not_queued", "Link songs open in the browser; they can't be queued", "링크 곡은 브라우저에서 열려서 대기열에 넣을 수 없어요"),
]


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


for lang, idx in (("values", 1), ("values-ko", 2)):
    def add(s, idx=idx):
        if "music_add_link" in s:
            return s
        return s.replace("</resources>", "".join('    <string name="%s">%s</string>\n' % (r[0], esc(r[idx])) for r in STRINGS) + "</resources>")
    rw(os.path.join(RES, lang, "strings_manual.xml"), add)

# ---- models / codec / store ---------------------------------------------------------------------------------------
rw(os.path.join(MU, "Models.kt"), lambda s: sub(sub(sub(
    s,
    "enum class TrackSource { DEEZER, AUDIUS, LOCAL }",
    "enum class TrackSource { DEEZER, AUDIUS, LOCAL, LINK }",
), "    val isLocal: Boolean get() = source == TrackSource.LOCAL\n",
    "    val isLocal: Boolean get() = source == TrackSource.LOCAL\n\n    /** A saved web link (YouTube): shown in lists, but opened in the browser instead of being played by the player. */\n    val isLink: Boolean get() = source == TrackSource.LINK\n",
), "data class UserPlaylist(val id: String, val name: String, val tracks: List<Track>, val created: Long)",
    "/** A playlist the user made; [loop] = it plays over and over (repeat all) whenever it is started. */\ndata class UserPlaylist(val id: String, val name: String, val tracks: List<Track>, val created: Long, val loop: Boolean = false)"))


def codec(s):
    s = sub(s, '        .put("created", p.created).put("tracks", tracks(p.tracks))', '        .put("created", p.created).put("loop", p.loop).put("tracks", tracks(p.tracks))')
    s = sub(s, '        return UserPlaylist(id, o.optString("name"), tracks(o.optJSONArray("tracks")), o.optLong("created"))', '        return UserPlaylist(id, o.optString("name"), tracks(o.optJSONArray("tracks")), o.optLong("created"), o.optBoolean("loop"))')
    s = sub(s, "    fun createPlaylist(name: String, first: List<Track> = emptyList()): UserPlaylist {\n        val p = UserPlaylist(UUID.randomUUID().toString(), name.trim().ifEmpty { \"Playlist\" }, first.distinctBy { it.id }, System.currentTimeMillis())",
            "    fun createPlaylist(name: String, first: List<Track> = emptyList(), loop: Boolean = false): UserPlaylist {\n        val p = UserPlaylist(UUID.randomUUID().toString(), name.trim().ifEmpty { \"Playlist\" }, first.distinctBy { it.id }, System.currentTimeMillis(), loop)")
    s = sub(s, "    fun deletePlaylist(id: String) {", """    fun setPlaylistLoop(id: String, loop: Boolean) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        playlists[i] = playlists[i].copy(loop = loop)
        save()
    }

    fun deletePlaylist(id: String) {""")
    return s


rw(os.path.join(MU, "MusicStore.kt"), codec)


# ---- player: link songs never enter the queue; repeat-all for loop playlists --------------------------------------
def player(s):
    s = sub(s, "    var sleepAfterTrack by mutableStateOf(false); private set\n", "    var sleepAfterTrack by mutableStateOf(false); private set\n\n    /** Opens a link song (a YouTube video) in the browser; set by the UI. */\n    var openLink: (Track) -> Unit = {}\n")
    s = sub(s, "    fun play(tracks: List<Track>, startIndex: Int = 0, shuffled: Boolean = false) {\n        if (tracks.isEmpty()) return\n        val p = player\n        val start = startIndex.coerceIn(0, tracks.lastIndex)\n",
            """    fun play(tracks: List<Track>, startIndex: Int = 0, shuffled: Boolean = false, repeatAll: Boolean = false) {
        if (tracks.isEmpty()) return
        // A link song cannot be played by the player: tapping one opens it in the browser, the others play without it.
        val chosen = tracks.getOrNull(startIndex)
        if (chosen != null && chosen.isLink) return openLink(chosen)
        val playable = tracks.filter { !it.isLink }
        if (playable.isEmpty()) return
        if (playable.size != tracks.size) return play(playable, playable.indexOfFirst { it.id == chosen?.id }.coerceAtLeast(0), shuffled, repeatAll)
        val p = player
        val start = startIndex.coerceIn(0, tracks.lastIndex)
""")
    s = sub(s, "        shuffle = false\n        if (shuffled) applyShuffle(true)\n        p.prepare()", "        shuffle = false\n        if (shuffled) applyShuffle(true)\n        if (repeatAll) {\n            p.repeatMode = Player.REPEAT_MODE_ALL\n            repeat = Player.REPEAT_MODE_ALL\n        }\n        p.prepare()")
    s = sub(s, "    fun playShuffled(tracks: List<Track>) {\n        if (tracks.isEmpty()) return\n        play(tracks, (tracks.indices).random(), shuffled = true)",
            "    fun playShuffled(tracks: List<Track>, repeatAll: Boolean = false) {\n        val playable = tracks.filter { !it.isLink }\n        if (playable.isEmpty()) return\n        play(playable, (playable.indices).random(), shuffled = true, repeatAll = repeatAll)")
    s = sub(s, "    fun playNext(track: Track) {\n        val p = player", "    fun playNext(track: Track) {\n        if (track.isLink) return openLink(track)\n        val p = player")
    s = sub(s, "    fun addToQueue(track: Track) {\n        val p = player", "    fun addToQueue(track: Track) {\n        if (track.isLink) return openLink(track)\n        val p = player")
    s = sub(s, "    fun addAllToQueue(tracks: List<Track>) {\n        if (tracks.isEmpty()) return\n        val p = player", "    fun addAllToQueue(all: List<Track>) {\n        val tracks = all.filter { !it.isLink }\n        if (tracks.isEmpty()) return\n        val p = player")
    return s


rw(os.path.join(MU, "MusicPlayer.kt"), player)


# ---- MusicUi state ---------------------------------------------------------------------------------------------------
def music_ui(s):
    s = sub(s, "    var addTracks by mutableStateOf<List<Track>?>(null)\n", "    var addTracks by mutableStateOf<List<Track>?>(null)\n\n    /** The \"add a YouTube link\" dialog, and a page the browser should open (set here, consumed by the app scaffold). */\n    var addLinkOpen by mutableStateOf(false)\n    var pendingBrowserUrl by mutableStateOf<String?>(null)\n")
    s = sub(s, "    var createPlaylistWith by mutableStateOf<List<Track>?>(null)\n", "    var createPlaylistWith by mutableStateOf<List<Track>?>(null)\n    var createPlaylistLoop by mutableStateOf(false)\n")
    return s


rw(os.path.join(UI, "MusicUi.kt"), music_ui)


# ---- overlays: add-link dialog, loop checkbox, link song in the menu ------------------------------------------------
def overlays(s):
    s = sub(s, "    MusicUi.createPlaylistWith?.let { initial -> CreatePlaylistDialog(initial) }", "    LaunchedEffect(Unit) { com.example.superdialer.music.MusicPlayer.openLink = { t -> MusicUi.pendingBrowserUrl = t.uri } }\n    if (MusicUi.addLinkOpen) AddLinkDialog()\n    MusicUi.createPlaylistWith?.let { initial -> CreatePlaylistDialog(initial) }")
    s = sub(s, "    var name by remember { mutableStateOf(suggested) }\n    fun close() { MusicUi.createPlaylistWith = null }",
            "    var name by remember { mutableStateOf(suggested) }\n    var loop by remember { mutableStateOf(false) }\n    fun close() { MusicUi.createPlaylistWith = null }")
    s = sub(s, "            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(str(R.string.music_playlist_name)) }, shape = RoundedCornerShape(12.dp))\n        },\n        confirmButton = {\n            TextButton(onClick = {\n                val p = MusicStore.createPlaylist(name.ifBlank { suggested }, initial)",
            """            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(str(R.string.music_playlist_name)) }, shape = RoundedCornerShape(12.dp))
                Row(Modifier.fillMaxWidth().clickable { loop = !loop }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = loop, onCheckedChange = { loop = it })
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(str(R.string.music_loop))
                        Text(str(R.string.music_loop_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = MusicStore.createPlaylist(name.ifBlank { suggested }, initial, loop)""")
    s = sub(s, "        SheetAction(Icons.Filled.SkipNext, str(R.string.music_play_next)) { MusicPlayer.playNext(track); close() }\n        SheetAction(Icons.Filled.QueueMusic, str(R.string.music_add_to_queue)) { MusicPlayer.addToQueue(track); close() }\n",
            "        if (!track.isLink) {\n            SheetAction(Icons.Filled.SkipNext, str(R.string.music_play_next)) { MusicPlayer.playNext(track); close() }\n            SheetAction(Icons.Filled.QueueMusic, str(R.string.music_add_to_queue)) { MusicPlayer.addToQueue(track); close() }\n        }\n")
    s += '''
/** Paste a YouTube link (prefilled from the clipboard when it holds one); the video becomes a link song to put in a playlist. */
@Composable
private fun AddLinkDialog() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var text by remember {
        mutableStateOf(clipboard.getText()?.text?.trim()?.takeIf { com.example.superdialer.music.YoutubeLinks.isVideoLink(it) }.orEmpty())
    }
    var busy by remember { mutableStateOf(false) }
    fun close() { MusicUi.addLinkOpen = false }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(str(R.string.music_link_title)) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = true, enabled = !busy,
                label = { Text(str(R.string.music_link_hint)) }, shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy && text.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        val song = com.example.superdialer.music.YoutubeLinks.fetch(text)
                        busy = false
                        if (song == null) {
                            toast(context, R.string.music_link_invalid)
                        } else {
                            close()
                            MusicUi.addTracks = listOf(song)
                        }
                    }
                },
            ) { Text(str(if (busy) R.string.music_link_loading else R.string.music_link_add)) }
        },
        dismissButton = { TextButton(onClick = ::close) { Text(str(R.string.cancel)) } },
    )
}
'''
    s = sub(s, "import androidx.compose.runtime.getValue\n", "import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\n")
    s = sub(s, "import kotlinx.coroutines", "import kotlinx.coroutines") if False else s
    if "import kotlinx.coroutines.launch" not in s:
        s = sub(s, "import com.example.superdialer.music.Track\n", "import com.example.superdialer.music.Track\nimport kotlinx.coroutines.launch\n")
    return s


rw(os.path.join(UI, "MusicOverlays.kt"), overlays)


# ---- library: add-link row + loop marker -------------------------------------------------------------------------------
def library(s):
    s = sub(s, "                item {\n                    LibraryRow(\n                        title = str(R.string.music_liked_songs),",
            """                item {
                    LibraryRow(
                        title = str(R.string.music_add_link),
                        subtitle = null,
                        onClick = { MusicUi.addLinkOpen = true },
                        cover = {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Link, null)
                            }
                        },
                    )
                }
                item {
                    LibraryRow(
                        title = str(R.string.music_liked_songs),""")
    s = sub(s, "                        subtitle = str(R.string.music_playlist_by, songsLabel(p.tracks.size)),\n                        onClick = { MusicUi.open(Dest.UserPlaylist(p.id)) },",
            "                        subtitle = str(R.string.music_playlist_by, songsLabel(p.tracks.size)) + if (p.loop) \" \\u2022 \\u221E\" else \"\",\n                        onClick = { MusicUi.open(Dest.UserPlaylist(p.id)) },")
    s = sub(s, "import androidx.compose.material.icons.filled.Favorite\n", "import androidx.compose.material.icons.filled.Favorite\nimport androidx.compose.material.icons.filled.Link\n")
    return s


rw(os.path.join(UI, "LibraryScreen.kt"), library)


# ---- detail pages: loop menu, plays with repeat-all --------------------------------------------------------------------
def detail(s):
    s = sub(s, "    playlistId: String? = null,\n    firstTracks: Int = Int.MAX_VALUE,", "    playlistId: String? = null,\n    loop: Boolean = false,\n    firstTracks: Int = Int.MAX_VALUE,")
    s = sub(s, "MusicPlayer.playShuffled(tracks) }, enabled = tracks.isNotEmpty()) {\n                        Icon(Icons.Filled.Shuffle, str(R.string.music_shuffle_play))", "MusicPlayer.playShuffled(tracks, loop) }, enabled = tracks.isNotEmpty()) {\n                        Icon(Icons.Filled.Shuffle, str(R.string.music_shuffle_play))")
    s = sub(s, "if (playingHere) MusicPlayer.togglePlay() else MusicPlayer.play(tracks)", "if (playingHere) MusicPlayer.togglePlay() else MusicPlayer.play(tracks, repeatAll = loop)")
    s = sub(s, "onClick = { MusicPlayer.play(tracks, tracks.indexOf(t).coerceAtLeast(0)) },", "onClick = { MusicPlayer.play(tracks, tracks.indexOf(t).coerceAtLeast(0), repeatAll = loop) },")
    s = sub(s, "        emptyText = str(R.string.music_empty_playlist),\n        playlistId = playlist.id,", "        emptyText = str(R.string.music_empty_playlist),\n        playlistId = playlist.id,\n        loop = playlist.loop,")
    s = sub(s, "                    DropdownMenuItem(text = { Text(str(R.string.music_rename)) }, onClick = { menu = false; MusicUi.renamePlaylistId = playlist.id })",
            """                    DropdownMenuItem(
                        text = { Text(str(R.string.music_loop)) },
                        trailingIcon = { if (playlist.loop) Icon(Icons.Filled.Check, str(R.string.music_loop_on)) },
                        onClick = { menu = false; MusicStore.setPlaylistLoop(playlist.id, !playlist.loop) },
                    )
                    DropdownMenuItem(text = { Text(str(R.string.music_add_link)) }, onClick = { menu = false; MusicUi.addLinkOpen = true })
                    DropdownMenuItem(text = { Text(str(R.string.music_rename)) }, onClick = { menu = false; MusicUi.renamePlaylistId = playlist.id })""")
    s = sub(s, "        meta = { Text(songsLabel(playlist.tracks.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },",
            "        meta = {\n            Text(songsLabel(playlist.tracks.size) + if (playlist.loop) \" \\u2022 \" + str(R.string.music_loop_on) else \"\", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)\n        },")
    s = sub(s, "import androidx.compose.material.icons.filled.Favorite\n", "import androidx.compose.material.icons.filled.Check\nimport androidx.compose.material.icons.filled.Favorite\n")
    return s


rw(os.path.join(UI, "DetailScreens.kt"), detail)


# ---- search: find on YouTube row -----------------------------------------------------------------------------------------
def search(s):
    s = sub(s, "    if (filter == 5) {\n        val list = fullSongs.value", """    Column(
        Modifier.fillMaxWidth().clickable { MusicUi.pendingBrowserUrl = com.example.superdialer.music.YoutubeLinks.searchUrl(query) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
            Text(str(R.string.music_find_on_youtube, query), Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(str(R.string.music_find_on_youtube_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    if (filter == 5) {
        val list = fullSongs.value""")
    s = sub(s, "import androidx.compose.material.icons.filled.History\n", "import androidx.compose.material.icons.filled.History\nimport androidx.compose.material.icons.filled.OpenInNew\n")
    return s


rw(os.path.join(UI, "SearchScreen.kt"), search)

# ---- track row: link songs are marked ----------------------------------------------------------------------------------------
rw(os.path.join(UI, "Components.kt"), lambda s: sub(
    s,
    "                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)",
    "                Text(\n                    if (track.isLink) str(R.string.music_link_song) else track.artist,\n                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,\n                )",
))

# ---- app scaffold: open the browser on request -------------------------------------------------------------------------------
def scaffold(s):
    return sub(s, "    // A website open in the browser takes the whole screen", """    // A link asked for by the Music tab (a YouTube search or a link song): open it in the browser tab.
    LaunchedEffect(MusicUi.pendingBrowserUrl, navReady) {
        val url = MusicUi.pendingBrowserUrl
        if (navReady && url != null) {
            browserViewModel.hubSection = 0
            browserViewModel.openExternal(url)
            navigateToTab(TopLevelDestination.Hub.route)
            MusicUi.pendingBrowserUrl = null
        }
    }

    // A website open in the browser takes the whole screen""")


rw(os.path.join(APP, "navigation", "SuperDialerApp.kt"), scaffold)


# ---- browser menu: Add to Music on a YouTube video ------------------------------------------------------------------------
def browser(s):
    s = sub(s, "    onToggleDesktop: (() -> Unit)? = null,\n) {\n    var open by remember { mutableStateOf(false) }", "    onToggleDesktop: (() -> Unit)? = null,\n    /** Shown on a YouTube video page: saves the video as a link song of the Music tab. */\n    onAddToMusic: (() -> Unit)? = null,\n) {\n    var open by remember { mutableStateOf(false) }")
    s = sub(s, "            if (onFind != null || onToggleDesktop != null) HorizontalDivider()", """            if (onAddToMusic != null) {
                DropdownMenuItem(
                    text = { Text(str(R.string.music_add_to_music)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { open = false; onAddToMusic() },
                )
            }
            if (onFind != null || onToggleDesktop != null || onAddToMusic != null) HorizontalDivider()""")
    s = sub(s, "                onFind = onFind,\n                desktopMode = tab.desktopMode,\n                onToggleDesktop = onToggleDesktop,\n            )", """                onFind = onFind,
                desktopMode = tab.desktopMode,
                onToggleDesktop = onToggleDesktop,
                onAddToMusic = if (!tab.isPrivate && com.example.superdialer.music.YoutubeLinks.isVideoLink(tab.url)) {
                    {
                        scope.launch {
                            val song = com.example.superdialer.music.YoutubeLinks.fetch(tab.url)
                            if (song != null) com.example.superdialer.music.ui.MusicUi.addTracks = listOf(song)
                            else android.widget.Toast.makeText(context, str(R.string.music_link_invalid), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    null
                },
            )""")
    s = sub(s, "            val context = LocalContext.current\n            OverflowMenu(\n                onNewTab, onNewPrivateTab,", "            val context = LocalContext.current\n            val scope = androidx.compose.runtime.rememberCoroutineScope()\n            OverflowMenu(\n                onNewTab, onNewPrivateTab,")
    if "import kotlinx.coroutines.launch" not in s:
        s = sub(s, "import androidx.compose.material3.DropdownMenuItem\n", "import androidx.compose.material3.DropdownMenuItem\nimport kotlinx.coroutines.launch\n")
    return s


rw(os.path.join(APP, "browser", "BrowserScreen.kt"), browser)
print("patched")
