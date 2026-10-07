# SuperDialer

기본 전화 앱으로 설정할 수 있는 올인원 안드로이드 전화 앱입니다. 전화 기능에 내장 웹 브라우저와 내장 게임을 함께 담았습니다.
스토어 등록 없이 이 저장소의 **Releases**에서 APK를 받아 직접 설치하는 방식으로 배포합니다. (대상 기기: 갤럭시 S23 / Android 10 이상)

## 기능

- **전화**: 키패드(한국 번호 포맷, 자동완성), 수신·발신·통화 중 화면(음소거, 스피커, 키패드, 보류, 통화 대기), 메시지로 거절, 부재중 알림, 잠금화면 수신
- **최근기록 · 연락처**: 검색(이름·초성·번호), 필터, 동일 번호 묶기, 선택/전체 삭제, 즐겨찾기, 번호 차단, 통화 내역·문자(SMS) 내용 보기
- **브라우저**: 스피드 다이얼 시작 화면, 전체 화면 웹 보기, 멀티 탭, 북마크·방문 기록, 다운로드, 전체화면 동영상
- **게임**: 2048, 스네이크, 벽돌깨기 (최고 점수 저장)
- **설정**: 기본 전화 앱 상태, 차단 관리, 거절 메시지, 키패드음, 권한 상태

기술 구조와 개발 메모는 [CLAUDE.md](CLAUDE.md)를 참고하세요.

## 설치 방법

1. 폰 브라우저로 이 저장소의 [Releases](../../releases) 페이지에서 최신 `SuperDialer-vX.Y.Z.apk`를 내려받습니다.
2. **출처를 알 수 없는 앱 설치 허용**: 파일을 열면 나오는 안내에서 "이 출처 허용"을 켭니다. (설정 > 앱 > 특별한 접근 > 알 수 없는 앱 설치에서 브라우저/내 파일을 허용해도 됩니다.)
3. **Play Protect 경고**: "유해한 앱 차단됨/확인되지 않은 앱" 경고가 나오면 **자세히 > 무시하고 설치**를 누릅니다. Google Play 밖에서 받은 앱이라 나오는 경고입니다.
4. **삼성 자동 차단(Auto Blocker)**: 설치가 막히면 설정 > 보안 및 개인정보 보호 > **Auto Blocker**에서 "승인되지 않은 앱 설치 차단"을 끄고 설치한 뒤 필요하면 다시 켭니다.
5. 설치 후 앱을 열면 **알림 권한** 팝업과 **기본 전화 앱 안내 화면**이 나옵니다.

### 기본 전화 앱으로 설정

- 첫 실행 안내 화면에서 "기본 전화 앱으로 설정" → 시스템 팝업에서 SuperDialer 선택
- 나중에 하려면 앱의 **설정 탭 > 기본 전화 앱**에서 다시 요청하거나, 휴대폰 설정 > 앱 > 기본 앱 > 전화 앱에서 선택합니다.
- 통화 화면과 번호 차단은 기본 전화 앱일 때만 동작합니다.
- **되돌리기**: 휴대폰 설정 > 앱 > 기본 앱 > 전화 앱에서 원래 쓰던 전화 앱(삼성 전화 등)을 고르면 됩니다. 테스트 중 통화가 안 되면 바로 되돌리세요.

### 설치 후 알아둘 점

- **잠금화면 전화 바로가기**는 기본 전화 앱 설정과 별개입니다. 잠금화면 편집(설정 > 배경화면 및 스타일 > 잠금화면 편집)에서 왼쪽 아래 바로가기를 SuperDialer로 바꾸세요.
- **전체 화면 알림**(잠금화면에서 수신 화면 표시)은 Android 14 이상에서 설정 탭의 "전체 화면 알림"에서 허용해야 할 수 있습니다.
- 통화 기록·문자 권한이 허용되지 않으면 설정 > 앱 > SuperDialer > 오른쪽 위 ⋮ > **제한된 설정 허용**을 먼저 해 보세요. (직접 설치한 앱에 적용되는 안드로이드의 보호 기능입니다.)
- 통화 녹음은 안드로이드 정책상 일반 앱이 할 수 없어 지원하지 않습니다.

## 개발

필요한 것: JDK 17, Android SDK(platform 36). `local.properties`에 `sdk.dir`를 지정하거나 `ANDROID_HOME`을 설정합니다.

```bash
./gradlew testDebugUnitTest      # 단위 테스트
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 릴리스 배포 (GitHub Actions)

`v`로 시작하는 태그를 푸시하면 [워크플로](.github/workflows/release.yml)가 단위 테스트 → 서명된 릴리스 APK 빌드 → GitHub Releases 업로드를 자동으로 합니다.

### 1. 서명 키(keystore) 만들기 — 한 번만

```bash
keytool -genkeypair -v -keystore superdialer-release.jks -alias superdialer \
  -keyalg RSA -keysize 2048 -validity 10000
```

비밀번호, 이름 등을 물어봅니다. 입력한 **keystore 비밀번호**, **키 별칭(alias)**, **키 비밀번호**를 기억해 두세요. (위 예시의 alias는 `superdialer`)

> **중요**: `superdialer-release.jks`를 **안전한 곳에 백업**하세요. 잃어버리면 같은 서명으로 업데이트할 수 없어 앱을 지우고 다시 설치해야 합니다. 저장소에 커밋하면 안 됩니다. (`.gitignore`가 `*.jks`를 막아 둡니다.)

### 2. keystore를 base64 문자열로 바꾸기

GitHub Secrets에는 파일을 올릴 수 없어서 텍스트(base64)로 바꿔 넣습니다.

- Windows PowerShell (클립보드로 복사):
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("superdialer-release.jks")) | Set-Clipboard
  ```
- Git Bash / Linux:
  ```bash
  base64 -w0 superdialer-release.jks
  ```
- macOS:
  ```bash
  base64 -i superdialer-release.jks | pbcopy
  ```

### 3. GitHub Secrets 등록

저장소의 **Settings > Secrets and variables > Actions > New repository secret**에서 4개를 만듭니다.

| 이름 | 값 |
|---|---|
| `KEYSTORE_BASE64` | 2번에서 만든 base64 문자열 |
| `KEYSTORE_PASSWORD` | keystore 비밀번호 |
| `KEY_ALIAS` | 키 별칭 (예: `superdialer`) |
| `KEY_PASSWORD` | 키 비밀번호 |

### 4. 태그 푸시로 릴리스

```bash
git tag v0.2.0
git push origin v0.2.0
```

Actions 탭에서 진행 상황을 볼 수 있고, 끝나면 Releases에 `SuperDialer-v0.2.0.apk`가 올라옵니다.
버전 이름은 태그에서(`v0.2.0` → `0.2.0`), 버전 코드는 워크플로 실행 번호에서 자동으로 정해져 새 릴리스가 이전 설치본 위에 업데이트됩니다.

### 알아둘 점

- 릴리스 APK는 **디버그 빌드와 서명이 달라** 서로 덮어 설치되지 않습니다. 폰에 `adb install`로 올린 디버그 빌드가 있으면 지우고(앱 데이터 삭제) 릴리스를 설치해야 합니다. 한 번 릴리스 키로 설치한 뒤에는 이후 릴리스가 계속 업데이트로 설치됩니다.
- Secrets가 없으면 워크플로가 "KEYSTORE_BASE64 secret is not set" 오류로 바로 멈춥니다.
- 로컬에서 서명 없이 `./gradlew assembleRelease`를 실행하면 서명되지 않은 `app-release-unsigned.apk`가 만들어집니다. 로컬에서 서명하려면 `KEYSTORE_FILE`(파일 경로), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` 환경변수를 지정하세요.
