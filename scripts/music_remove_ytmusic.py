# One-off patch: remove the YouTube Music hand-off; a song without a full version just plays its preview (and says so on request).
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


def music_ui(s):
    # drop the YouTube helper
    s = re.sub(r"/\*\* Opens the song in the YouTube Music app.*?\n}\n\n?", "", s, flags=re.S)
    # no help dialog state
    s = re.sub(r"\n    /\*\* A song that has no full version on Audius: a dialog offers the YouTube search instead. \*/\n    var fullHelpTrack by mutableStateOf<Track\?>\(null\)\n", "\n", s)
    s = sub(s, "if (full != null) com.example.superdialer.music.MusicPlayer.replaceCurrent(full) else MusicUi.fullHelpTrack = track",
            "if (full != null) com.example.superdialer.music.MusicPlayer.replaceCurrent(full) else toast(context, R.string.music_no_full_version)")
    s = s.replace(" * replaces the current song with it. When Audius does not have it, a dialog offers the YouTube search instead.", " * replaces the current song with it, or says that there is none.")
    return s


rw(os.path.join(M, "MusicUi.kt"), music_ui)


def overlays(s):
    s = sub(s, "        if (!track.isLocal) {\n            SheetAction(Icons.Filled.OpenInNew, str(R.string.music_full_on_youtube)) { openFullOnYoutube(context, track); close() }\n        }\n", "")
    s = re.sub(r"    MusicUi\.fullHelpTrack\?\.let \{ t ->.*?\n    }\n\n", "", s, flags=re.S)
    return s


rw(os.path.join(M, "MusicOverlays.kt"), overlays)

rw(os.path.join(M, "NowPlayingScreen.kt"), lambda s: sub(
    s,
    "            if (!track.isLocal) {\n                IconButton(onClick = { openFullOnYoutube(context, track) }) { Icon(Icons.Filled.OpenInNew, str(R.string.music_full_on_youtube), tint = Color.White) }\n            }\n",
    "",
))

rw(os.path.join("app", "src", "main", "AndroidManifest.xml"), lambda s: sub(
    s,
    '        <!-- YouTube Music, where "Listen on YouTube Music" sends a song (music/ui/MusicUi.kt) -->\n        <package android:name="com.google.android.apps.youtube.music" />\n',
    "",
))


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')


for lang, text in (("values", "No full version of this song"), ("values-ko", "이 곡의 전곡이 없어요")):
    def fix(s, text=text):
        for key in ("music_full_on_youtube", "music_full_help_title", "music_full_help_text"):
            s = re.sub(r'    <string name="%s">[^<]*</string>\n' % key, "", s)
        if "music_no_full_version" not in s:
            s = s.replace("</resources>", '    <string name="music_no_full_version">%s</string>\n</resources>' % esc(text))
        return s
    rw(os.path.join(RES, lang, "strings_manual.xml"), fix)


def claude(s):
    a = s.index("- **YouTube Music**: 상용 곡의 진짜 전곡은")
    return s[:a] + "- **YouTube Music 연결은 뺐음**(사용자 결정): 우리 플레이어 안에서 재생할 수 없어서(공식 API 없음, 스트림 추출은 약관 위반) 곡 메뉴·재생 화면·안내 창의 YouTube Music 이동을 모두 제거. Audius에 전곡이 없는 곡은 30초 미리듣기로 재생되고, 배지/메뉴의 \"전곡으로 재생\"을 누르면 \"이 곡의 전곡이 없어요\"라고 알려 줌\n"


rw("CLAUDE.md", claude)
print("patched")
