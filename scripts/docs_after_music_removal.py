import re, io
def rw(p, fn):
    s = open(p, encoding="utf-8", newline="").read()
    crlf = "\r\n" in s
    s = s.replace("\r\n", "\n"); s = fn(s)
    if crlf: s = s.replace("\n", "\r\n")
    open(p, "w", encoding="utf-8", newline="").write(s)

def priv(ko):
    def f(s):
        s = re.sub(r"    <tr><td>Audius[^\n]*\n", "", s)
        s = re.sub(r"    <tr><td>Deezer[^\n]*\n", "", s)
        s = re.sub(r"    <li>Music and audio access[^\n]*\n" if not ko else r"    <li>음악·오디오 접근[^\n]*\n", "", s)
        if ko:
            s = s.replace("    <li>백그라운드 재생(포그라운드 서비스): 음악을 재생하는 동안 알림과 잠금 화면 컨트롤을 보여 주기 위해 사용</li>",
                          "    <li>백그라운드 재생(포그라운드 서비스): 음악 탭의 Spotify 웹 플레이어가 재생되는 동안 앱이 계속 실행되도록 알림을 보여 주기 위해 사용</li>")
            s = s.replace("계정 동기화, 업데이트 확인, 음악 탭", "계정 동기화, 업데이트 확인, 음악 탭(Spotify 웹 플레이어)")
            s = s.replace("    <tr><td>통화 녹음(마이크)", '    <tr><td>Spotify (음악 탭)</td><td>음악 탭은 Spotify의 공식 웹 플레이어(open.spotify.com)를 그대로 보여 줌. 로그인과 재생은 Spotify와 사용자 사이에서 이루어지며 앱은 비밀번호나 재생 기록을 저장하지 않음. 쿠키·저장소는 앱 안 웹 화면에 남음. Spotify의 개인정보 정책이 적용됨</td><td>—</td></tr>\n    <tr><td>통화 녹음(마이크)', 1) if False else s
        else:
            s = s.replace("    <li>Background playback (foreground service): used while music plays, to show the notification and the lock-screen controls</li>",
                          "    <li>Background playback (foreground service): shows a notification so the app keeps running while the Spotify web player in the Music tab plays</li>")
            s = s.replace("account sync, update checks, the Music tab", "account sync, update checks, the Music tab (Spotify's web player)")
        return s
    return f
rw("docs/privacy.html", priv(True)); rw("docs/privacy-en.html", priv(False))

def readme(s):
    return re.sub(r"- \*\*Music\*\* \(Apps tab\):[^\n]*\n", "- **Music** (Apps tab): Spotify's official web player (open.spotify.com) inside the app, signed in with your own account. It keeps playing in the background with a notification (Stop button). Needs the Spotify account's own plan for what it allows; the app does not extract or store any audio.\n", s)
rw("README.md", readme)

def claude(s):
    a = s.index("## 앱 탭 + 음악 (v0.14.0, music/)")
    b = s.index("## 음악: Spotify 웹 플레이어 (v0.15.x)")
    s = s[:a] + """## 앱 탭 (v0.14.0~)
- 하단 탭 "웹·게임"이 **"앱"**(`TopLevelDestination.Hub`, 문자열 `t_web_games`="앱"/"Apps")이고 위쪽 전환 바가 브라우저/게임/**음악** 3칸(`hub/WebGamesScreen`, `BrowserViewModel.hubSection` 0/1/2). 런처 바로가기용 `MainActivity.SHORTCUT_MUSIC`. UI 테스트는 탭 이름 "앱"
- (v0.16.0에서 Deezer/Audius/YouTube 링크 음악 플레이어, 라이브러리·플레이리스트, media3, 음악 설정·권한을 모두 삭제함 — 기록은 git 이력 참고. 음악 탭은 아래 Spotify 웹 플레이어 하나)

""" + s[b:]
    s = s.replace("- 예전 Deezer/Audius/YouTube 링크 음악 화면(`music/ui/*`, `MusicPlayer` 등)은 코드에 남아 있지만 **UI에서 닿지 않음**(지워도 됨). 그래서 `MusicTabTest`(칩 기반 UI 테스트)는 삭제함.", "- 예전 Deezer/Audius/YouTube 링크 음악 코드는 삭제함(`MusicTabTest`도).")
    return s
rw("CLAUDE.md", claude)
