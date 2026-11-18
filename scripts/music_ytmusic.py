# One-off patch: "Full song on YouTube" becomes "Listen on YouTube Music" (hands the search to the YouTube Music app).
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
    assert old in s, old[:70]
    return s.replace(old, new, 1)


M = os.path.join("app", "src", "main", "java", "com", "example", "superdialer", "music", "ui")
RES = os.path.join("app", "src", "main", "res")


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


NEW = {
    "music_full_on_youtube": ("Listen on YouTube Music", "YouTube Music에서 듣기"),
    "music_full_help_text": (
        "This song isn't available in full on Audius (the free full-length source). YouTube Music has it: it opens there.",
        "이 곡은 무료 전곡 음원인 Audius에 없어요. YouTube Music에서 들을 수 있어요. 눌러서 열어 보세요.",
    ),
}
for lang, idx in (("values", 0), ("values-ko", 1)):
    def fix(s, idx=idx):
        for key, vals in NEW.items():
            s = re.sub(r'(<string name="%s">)[^<]*(</string>)' % key, lambda m: m.group(1) + esc(vals[idx]) + m.group(2), s)
        return s
    rw(os.path.join(RES, lang, "strings_manual.xml"), fix)

# helper: YouTube Music first, any browser as the fallback
rw(os.path.join(M, "MusicUi.kt"), lambda s: re.sub(
    r"/\*\* Opens the song's full version on YouTube.*?\n}\n",
    '''/** Opens the song in the YouTube Music app (a search for it), or on music.youtube.com when the app is not installed. */
fun openFullOnYoutube(context: Context, track: Track) {
    val q = Uri.encode("${track.title} ${track.artist}")
    val link = Uri.parse("https://music.youtube.com/search?q=$q")
    val app = Intent(Intent.ACTION_VIEW, link).setPackage("com.google.android.apps.youtube.music").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(app) }.recoverCatching { context.startActivity(web) }
}
''',
    s,
    flags=re.S,
))

# a YouTube Music button on the full player, next to share and the queue
def now_playing(s):
    s = sub(s, "            IconButton(onClick = { shareTrack(context, track) }) { Icon(Icons.Filled.Share, str(R.string.music_share), tint = Color.White) }",
            "            if (!track.isLocal) {\n                IconButton(onClick = { openFullOnYoutube(context, track) }) { Icon(Icons.Filled.OpenInNew, str(R.string.music_full_on_youtube), tint = Color.White) }\n            }\n            IconButton(onClick = { shareTrack(context, track) }) { Icon(Icons.Filled.Share, str(R.string.music_share), tint = Color.White) }")
    return sub(s, "import androidx.compose.material.icons.filled.MoreVert\n", "import androidx.compose.material.icons.filled.MoreVert\nimport androidx.compose.material.icons.filled.OpenInNew\n")


rw(os.path.join(M, "NowPlayingScreen.kt"), now_playing)

# the menu shows the YouTube Music entry for every online song, not only for previews
rw(os.path.join(M, "MusicOverlays.kt"), lambda s: sub(s,
    "            SheetAction(Icons.Filled.OpenInNew, str(R.string.music_full_on_youtube)) { openFullOnYoutube(context, track); close() }\n        }\n",
    "        }\n        if (!track.isLocal) {\n            SheetAction(Icons.Filled.OpenInNew, str(R.string.music_full_on_youtube)) { openFullOnYoutube(context, track); close() }\n        }\n"))

# the manifest must let the app see YouTube Music
man = os.path.join("app", "src", "main", "AndroidManifest.xml")
rw(man, lambda s: sub(s, '        <package android:name="com.spotify.music" />\n',
                      '        <package android:name="com.spotify.music" />\n        <!-- YouTube Music, where "Listen on YouTube Music" sends a song (music/ui/MusicUi.kt) -->\n        <package android:name="com.google.android.apps.youtube.music" />\n'))
print("patched")
