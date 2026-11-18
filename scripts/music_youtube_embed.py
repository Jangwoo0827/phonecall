# One-off patch: link songs play with YouTube's embedded player inside the full player (video in the cover's place).
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
MU = os.path.join(APP, "music")
UI = os.path.join(MU, "ui")
RES = os.path.join("app", "src", "main", "res")

# ---- strings
def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


NEW = [
    ("music_yt_cant_embed", "The owner of this video doesn't allow playing it inside other apps.", "이 영상은 올린 사람이 다른 앱 안에서의 재생을 막아 두었어요."),
    ("music_yt_open_browser", "Open in browser", "브라우저에서 열기"),
    ("music_yt_foreground_note", "YouTube plays only while this screen is open. It pauses when the screen turns off or you leave the app.", "YouTube는 이 화면이 켜져 있을 때만 재생돼요. 화면을 끄거나 앱을 나가면 멈춰요."),
]
CHANGED = {
    "music_link_song": ("YouTube • plays in the app", "YouTube • 앱 안에서 재생"),
}
for lang, idx in (("values", 0), ("values-ko", 1)):
    def fix(s, idx=idx):
        for key, vals in CHANGED.items():
            s = re.sub(r'(<string name="%s">)[^<]*(</string>)' % key, lambda m: m.group(1) + esc(vals[idx]) + m.group(2), s)
        if "music_yt_cant_embed" not in s:
            s = s.replace("</resources>", "".join('    <string name="%s">%s</string>\n' % (k, esc(v[idx])) for k, *v in [(r[0], r[1], r[2]) for r in NEW]) + "</resources>")
        return s
    rw(os.path.join(RES, lang, "strings_manual.xml"), fix)


# ---- MusicPlayer: link songs go to YoutubePlayback; the music player and the video never play together
def player(s):
    s = sub(s, """        // A link song cannot be played by the player: tapping one opens it in the browser, the others play without it.
        val chosen = tracks.getOrNull(startIndex)
        if (chosen != null && chosen.isLink) return openLink(chosen)
""", """        // A link song is played by YouTube's own embedded player (YoutubePlayback), together with the other link songs of the list.
        val chosen = tracks.getOrNull(startIndex)
        if (chosen != null && chosen.isLink) {
            val links = tracks.filter { it.isLink }
            return YoutubePlayback.start(links, links.indexOfFirst { it.id == chosen.id }.coerceAtLeast(0), repeatAll)
        }
""")
    s = sub(s, "        val p = player\n        val start = startIndex.coerceIn(0, tracks.lastIndex)\n", "        YoutubePlayback.stop()\n        val p = player\n        val start = startIndex.coerceIn(0, tracks.lastIndex)\n")
    s = sub(s, "    fun togglePlay() {", """    /** A video starts: the music player stops making sound (it keeps its queue). */
    fun pauseForVideo() {
        built?.pause()
    }

    fun togglePlay() {""")
    s = sub(s, "    fun stop() {\n        built?.run {", "    fun stop() {\n        YoutubePlayback.stop()\n        built?.run {")
    return s


rw(os.path.join(MU, "MusicPlayer.kt"), player)


# ---- the overlays: show the video player, open it when a video starts
def overlays(s):
    s = sub(s, """        visible = MusicUi.nowPlayingOpen && MusicPlayer.current != null,""", """        visible = MusicUi.nowPlayingOpen && (MusicPlayer.current != null || com.example.superdialer.music.YoutubePlayback.active),""")
    s = sub(s, "        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { NowPlayingScreen() }",
            "        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {\n            if (com.example.superdialer.music.YoutubePlayback.active) YoutubeNowPlaying() else NowPlayingScreen()\n        }")
    s = sub(s, "    LaunchedEffect(Unit) { com.example.superdialer.music.MusicPlayer.openLink = { t -> MusicUi.pendingBrowserUrl = t.uri } }\n",
            "    val videoStarts = com.example.superdialer.music.YoutubePlayback.startCount\n    LaunchedEffect(videoStarts) { if (videoStarts > 0) MusicUi.nowPlayingOpen = true }\n")
    return s


rw(os.path.join(UI, "MusicOverlays.kt"), overlays)


# ---- the mini player for a video song: tapping anything opens the full player (the video only plays on screen)
def mini(s):
    s = sub(s, "fun MiniPlayer(modifier: Modifier = Modifier) {\n    val track = MusicPlayer.current ?: return\n",
            "fun MiniPlayer(modifier: Modifier = Modifier) {\n    val video = com.example.superdialer.music.YoutubePlayback.current\n    val track = video ?: MusicPlayer.current ?: return\n")
    s = sub(s, "                IconButton(onClick = { MusicPlayer.togglePlay() }, modifier = Modifier.testTag(\"mini_play\")) {\n                    Icon(\n                        if (MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,\n                        str(if (MusicPlayer.isPlaying) R.string.music_pause else R.string.music_play),\n                    )\n                }",
            "                // A video only plays while it is on screen, so its play button opens the full player.\n                IconButton(onClick = { if (video != null) MusicUi.nowPlayingOpen = true else MusicPlayer.togglePlay() }, modifier = Modifier.testTag(\"mini_play\")) {\n                    Icon(\n                        if (video == null && MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,\n                        str(if (video == null && MusicPlayer.isPlaying) R.string.music_pause else R.string.music_play),\n                    )\n                }")
    s = sub(s, "            val duration = MusicPlayer.durationMs.coerceAtLeast(1L)\n            LinearProgressIndicator(\n                progress = { (MusicPlayer.positionMs.toFloat() / duration).coerceIn(0f, 1f) },",
            "            val duration = (if (video != null) com.example.superdialer.music.YoutubePlayback.durationMs else MusicPlayer.durationMs).coerceAtLeast(1L)\n            val position = if (video != null) com.example.superdialer.music.YoutubePlayback.positionMs else MusicPlayer.positionMs\n            LinearProgressIndicator(\n                progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },")
    return s


rw(os.path.join(UI, "MiniPlayer.kt"), mini)

# ---- app startup, settings: clear music data also stops a video
rw(os.path.join(APP, "SuperDialerApplication.kt"), lambda s: sub(s, "        com.example.superdialer.music.MusicPlayer.init(this)\n", "        com.example.superdialer.music.MusicPlayer.init(this)\n        com.example.superdialer.music.YoutubePlayback.init(this)\n"))

# ---- the old "open in the browser" hook is no longer used by the player
def drop_hook(s):
    s = s.replace("\n    /** Opens a link song (a YouTube video) in the browser; set by the UI. */\n    var openLink: (Track) -> Unit = {}\n", "\n")
    s = s.replace("        if (track.isLink) return openLink(track)\n", "        if (track.isLink) return\n")
    return s


rw(os.path.join(MU, "MusicPlayer.kt"), drop_hook)
print("patched")
