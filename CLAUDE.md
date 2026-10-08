# SuperDialer

## 목표
기본 전화 앱(ROLE_DIALER)으로 설정 가능한 **올인원 전화 앱**.
최종적으로 전화 앱 전체 기능 + 내장 웹브라우저 + 내장 게임을 하나의 앱에 담는다.

## 조건
- Kotlin + Jetpack Compose (Material 3)
- minSdk 29, targetSdk/compileSdk 36 (최신)
- 패키지명 `com.example.superdialer`, 앱 이름 `SuperDialer`
- 대상 기기: 삼성 갤럭시 S23 (Android). 스토어 등록 없음 — GitHub에서 APK만 받아 설치
- 저장소: https://github.com/Jangwoo0827/phonecall.git
- 작업 단계마다 커밋 + 푸시

## 구조
화면별 패키지 (`app/src/main/java/com/example/superdialer/`):
- `dialer/` 키패드
- `calllog/` 최근 기록
- `contacts/` 연락처
- `incall/` 수신·발신·통화 중 화면 (`SuperInCallService` → `CallManager` → `InCallScreen`), 알림(`CallNotifications`)
- `browser/` 내장 웹브라우저
- `games/` 내장 게임
- `hub/` 브라우저와 게임을 한 탭으로 합친 화면 (`WebGamesScreen`, 상단 전환 바)
- `settings/` 설정 탭과 하위 화면(차단 관리, 거절 메시지)
- `navigation/` 하단 탭 네비게이션 (키패드/최근기록/연락처/웹·게임/설정)
- `ui/theme/` 테마

현재 상태: 키패드(자동완성), 최근기록, 연락처(즐겨찾기 토글), 통화 화면, 설정 탭(차단/거절 메시지) 구현됨. 웹·게임 탭(브라우저 + 게임 3종) 구현됨.

## 기본 전화 앱 요건 (매니페스트에 이미 반영)
- `MainActivity`에 `DIAL`(tel/없음), `VIEW tel` intent-filter
- `InCallService` (`BIND_INCALL_SERVICE` 권한, `IN_CALL_SERVICE_UI` 메타데이터)
- `CALL_PHONE`/`READ_CALL_LOG`/`WRITE_CALL_LOG`/`READ_CONTACTS` 런타임 권한 요청 구현됨(`ui/PermissionGate`, `ui/DialAction`). `RoleManager.ROLE_DIALER` 요청 코드는 아직 없음 — 번호 차단은 기본 전화 앱이어야 가능해서 지금은 안내 토스트만 뜸

## 빌드 / 설치
```bash
./gradlew assembleDebug
# 결과물: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
- JDK 17 이상 필요 (`JAVA_HOME` 설정), Android SDK는 `local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`로 지정 (`local.properties`는 gitignore)
- 이 PC: JDK는 `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`, SDK는 `%LOCALAPPDATA%\Android\Sdk` (`local.properties`에 `sdk.dir=C:/Users/LENOVO/AppData/Local/Android/Sdk`). 첫 빌드는 의존성 다운로드로 ~7분 소요

## 에뮬레이터 테스트
- 실기기(S23)는 Family Link 제한으로 USB 디버깅 허용 팝업이 안 떠서 에뮬레이터를 사용
- Windows Hypervisor Platform 활성화 필요 (관리자 PowerShell: `Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All` 후 재부팅)
- AVD `s23_api36` (pixel_7, API 36 google_apis x86_64, RAM 2GB). 실행: `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd s23_api36`
- 부팅 직후 "System UI isn't responding"가 뜨면 Wait 선택 (호스트 메모리 부족 때문)
- 화면 확인: `adb exec-out screencap -p > shot.png`, 탭: `adb shell input tap x y`
- 에뮬레이터는 실제 통신망이 없음 — 가상 통화는 `adb emu gsm call 01012345678`. 기본 전화 앱 지정/통화 품질은 실기기에서 확인
- 폰에서 설치할 때는 개발자 옵션 > USB 디버깅 또는 APK 직접 설치(출처를 알 수 없는 앱 허용)
- 기본 전화 앱 설정: 설정 > 앱 > 기본 앱 > 전화 앱

## 규칙
- 각 탭 기능은 해당 패키지 안에서 구현하고 `navigation/`은 라우팅만 담당
- 한글 문자열은 현재 코드 내 상수 사용 (추후 필요시 strings.xml로 이동)

## 키패드 (dialer/)
- `DialerViewModel`: 번호 상태(raw: 숫자/*/#/선행 +) + 외부 `tel:` 인텐트 처리. `MainActivity`(singleTask)가 onCreate/onNewIntent에서 `handleIntent` 호출 → 키패드 탭으로 이동 + 번호 채움
- `PhoneNumberFormatter`: 한국 번호 표시용 포맷 (010-1234-5678, 02-1234-5678, 031-123-4567, 1588-1234, +82 10-...). 단위 테스트 `PhoneNumberFormatterTest` (`./gradlew testDebugUnitTest`)
- `DtmfPlayer`: 키 누르는 동안 DTMF 톤. 끄려면 `DtmfPlayer.enabled = false` 또는 `DialerScreen(dtmfEnabled = ...)` — 기본값은 `DialerDefaults.DTMF_ENABLED`, 설정 화면 생기면 여기에 연결
- `CallPlacer`: `TelecomManager.placeCall()`. 권한 없으면 안내 문구 + 설정 열기 버튼
- 지우기 길게 누르기 = 전체 삭제, 0 길게 누르기 = `+`
- 기본 전화 앱이 아닐 때는 통화 화면을 시스템 전화 앱이 띄움 (`incall/`은 아직 스텁)

## 최근기록 (calllog/) — 에이닷 전화 스타일
- 날짜 구분선(오늘/어제 + 날짜·요일), 행: 종류 아이콘 · 아바타 · 이름/번호 · 시각·종류·통화시간 · 초록 발신 버튼
- **행을 탭하면 발신하지 않고 펼쳐짐**(기록/연락처/메시지). 발신은 초록 버튼만. 길게 누르기 = 삭제/번호 복사/차단
- 기록 → `CallHistoryScreen`(같은 번호 통화 내역, `numberKey`로 +82/010 동일 취급). 연락처 → 저장된 번호면 연락처 상세, 아니면 시스템 연락처 추가 화면
- `CallLogRepository`(CallLog.Calls + PhoneLookup), `CallLogViewModel`(코루틴 IO, 화면 resume 때 refresh). 삭제는 WRITE_CALL_LOG 필요
- 이름 없는 번호 표기는 "번호정보 없음"

## 연락처 (contacts/)
- 번호가 있는 연락처만. `ContactSorting`: 한글(가나다) → 영문 → 숫자/기호, 초성 인덱스(ㄲ→ㄱ 등 병합), 검색은 이름/초성(ㅎㄱㄷ)/번호 숫자
- 즐겨찾기(starred)는 상단 "즐겨찾기" 섹션에 고정 + 전체 목록에도 별 표시. 즐겨찾기 토글은 아직 없음(WRITE_CONTACTS 필요)
- 상세: 사진/이니셜, 번호 목록(유형 라벨), 문자(`smsto:`), 발신
- 단위 테스트: `ContactSortingTest`, `CallLogFormatTest`

## 테스트 주의 (중요)
- **실기기(S23, `R3CWA0MFJVY`)에는 adb로 터치 입력(`input tap/swipe`)을 보내지 말 것.** 통화 기록/연락처에 실제 번호가 있고 탭 한 번에 실제 발신될 수 있음(실제로 한 번 발생). 인터랙션 테스트는 에뮬레이터에서만, 모든 adb 명령에 `-s emulator-5554` 지정
- 에뮬레이터 테스트 데이터는 `adb shell content insert`로 연락처(raw_contacts → data)와 `content://call_log/calls`에 직접 삽입

## 통화 화면 (incall/) — 기본 전화 앱이어야 동작
- `SuperInCallService`가 Telecom 콜을 받아 `CallManager`(싱글톤, StateFlow)에 반영. 이름은 PhoneLookup으로 비동기 해석
- 수신: 전체화면 알림(+거절/받기 액션) → `InCallActivity`. 링톤은 Telecom이 재생(우리 앱은 소리 안 냄). 발신: 서비스가 `InCallActivity`를 직접 띄움
- 화면: 수신(받기/거절/메시지로 거절) · 연결 중 · 통화 중(타이머, 음소거, 키패드 DTMF, 스피커, 통화 추가, 보류, 종료) · 보류 중인 다른 통화 전환 배너 · "통화 종료" 1.2초 표시
- 부재중 알림(탭하면 최근기록). 통화 녹음 버튼은 안내 토스트만(안드로이드가 일반 앱의 통화 녹음 불허)
- `settings/AppSettings`(키패드음 등), `settings/RejectMessageStore`(거절 메시지 프리셋)
- 에뮬레이터 테스트: 기본 전화 앱 지정 `adb -s emulator-5554 shell cmd role add-role-holder --user 0 android.app.role.DIALER com.example.superdialer`, 가상 수신 `adb -s emulator-5554 emu gsm call 번호`, 끊기 `emu gsm cancel 번호`, 가상 발신 `am start -a android.intent.action.CALL -d tel:번호`. 에뮬레이터는 느려서 화면이 뜨기까지 ~4초

## 최근기록 추가 기능 / 설정
- 최근기록 상단: 검색(이름/초성/번호) + ⋮(선택 삭제, 전체 삭제(확인 다이얼로그), 차단 관리) + 필터 칩(전체/부재중/수신/발신)
- 같은 날 연속된 같은 번호 통화는 한 행으로 묶고 "(n)" 표시 (`groupCalls`, 삭제도 묶음 단위). 길게 누르면 선택 모드(체크박스) 진입
- 삭제는 WRITE_CALL_LOG 필요 — 없으면 요청 후 대기 중이던 삭제를 이어서 실행
- 설정(`settings/`): 기본 전화 앱, 차단 관리(`BlockedNumberContract`, 기본 전화 앱일 때만 목록/추가/해제 가능), 거절 메시지 편집(`RejectMessageStore`), 착신전환·부가서비스(시스템 통화 설정 열기 `ACTION_SHOW_CALL_SETTINGS`), 키패드음 스위치(`AppSettings`), 권한 상태, 전체 화면 알림, 앱 버전
- 설정은 독립 하단 탭 (route `settings`), 차단 관리·거절 메시지는 그 하위 (`settings/blocked`, `settings/reject`)라 하단 탭은 설정이 강조됨. 하위 경로(`x/...`)는 상위 탭 강조를 유지하고, 선택된 탭을 다시 누르면 탭 루트로 복귀
- 에뮬레이터에서 adb로 한글/숫자 입력 시 Gboard "스타일러스 체험" 팝업이 가로챌 수 있음(앱 문제 아님)

## 키패드 자동완성 / 연락처 보강
- 키패드: 2자리 이상 입력하면 번호에 포함되는 연락처 → 최근 통화(미저장 번호) 순으로 최대 3개 제안(`findSuggestions`, 번호 키 `numberKey`로 +82/010 동일 취급). 탭하면 번호가 채워짐. 연락처에 없는 번호면 "연락처에 추가"(시스템 연락처 추가 화면)
- 연락처 목록: 상단 + (시스템 연락처 추가), ⋮(차단 관리)
- 연락처 상세: 즐겨찾기 토글(WRITE_CONTACTS 요청 후 `Contacts.STARRED` 갱신), 공유, 편집(시스템 편집 화면), 이 연락처의 통화 기록 최대 20건
- 최근기록 길게 누르기 메뉴에 "번호 공유" 추가

## 구현하지 않은 항목 (이유)
- 통화 녹음: 안드로이드 10+ 는 일반 앱의 통화 음성 녹음을 막음(마이크만 가능해 상대 목소리가 안 녹음됨). 통화 화면 녹음 버튼은 안내 토스트만
- 착신전환: 통신사 기능이라 앱이 대신 설정하지 않고 시스템 통화 설정 화면으로 연결
- 해외 baro/로밍 통화, 스팸·업체 번호 DB, 안심통화: 별도 인프라/데이터 필요
- 통화 대기(통화 중 두 번째 전화): 코드는 있으나 에뮬레이터 모뎀이 두 번째 가상 전화에서 통화를 끊어 검증 못 함 — 실기기 확인 필요

## 기본 전화 앱 / 첫 실행 (4단계)
- 첫 실행 시 기본 전화 앱이 아니면 `onboarding/DefaultDialerOnboarding` 안내 화면 → `RoleManager.createRequestRoleIntent(ROLE_DIALER)`. 허용하면 메인으로, 거부하면 안내 문구 + 다시 시도/계속. 한 번 처리하면 `AppSettings.onboardingDone`
- 설정 탭에 기본 전화 앱 상태(+되돌리는 방법), 다시 요청 버튼. 요청 결과는 토스트
- 매니페스트 점검 결과: `DIAL`(번호 없음/tel/voicemail), `VIEW tel:`, `CALL_BUTTON`, `VIEW vnd.android.cursor.dir/calls`, `InCallService`(BIND_INCALL_SERVICE + IN_CALL_SERVICE_UI), 전체화면 인텐트·알림 권한 모두 선언
- 다중 통화: 보류 중 통화 전환(`swapTo`), 병합(`merge`, 다자간 통화는 부모 콜만 표시)
- S23 설치 후 테스트 체크리스트: `docs/S23_TEST_CHECKLIST.md`

## 내장 브라우저 (browser/, 5단계)
- `BrowserViewModel`(activity 스코프)이 탭별 `WebView`를 보관 → 탭/화면 전환·회전에도 페이지 상태 유지. `MutableContextWrapper`로 화면에 붙을 때만 Activity 컨텍스트를 물려 누수 방지(`WebViewHost`). MainActivity는 `configChanges`로 회전 시 재생성하지 않음
- 주소창: `UrlResolver`가 입력 해석 (URL/호스트면 https://, localhost·IPv4는 http://, 그 외는 구글 검색). `javascript:`/`file:` 등은 URL로 취급하지 않음
- **시작 화면(스피드 다이얼)**: 새 탭/나가기/홈은 시작 화면. 링크 타일 격자(4열), 탭하면 열기, 길게 누르면 수정/삭제, "+"로 추가(최대 24개). Room `speed_dials`(DB v2, 마이그레이션 1→2가 기본 타일 6개 시딩: 네이버/구글/유튜브/다음/쿠팡/위키백과)
- **웹 화면(몰입형)**: 사이트가 열려 있거나 게임을 실행 중이면(게임은 `GameSession.playingId != null`, 헤더의 뒤로 화살표로 나감)(`BrowserViewModel.immersive`) 위쪽 "브라우저/게임" 전환 바와 앱 하단 탭 바를 숨겨 페이지가 전체 화면을 씀. 상단 바(왼쪽 나가기 X · 주소 · 북마크 · ⋮)는 아래로 스크롤하면 숨고 위로 스크롤하거나 화면 위쪽 56dp를 터치하면 나타남(터치 이동량만 감지, 이벤트는 소비하지 않음). 로딩 진행선은 항상 맨 위. 하단 사이트 이동 바(뒤로/앞으로/새로고침/시작 페이지/탭)는 항상 표시. 뒤로가기는 페이지 기록 → 더 갈 곳이 없으면 시작 화면
- `hubSection`(0 브라우저, 1 게임)은 `BrowserViewModel`에 있어 앱이 하단 바 숨김 여부를 판단할 수 있음
- 북마크·방문 기록: Room(`browser/data/BrowserDatabase`, KSP). 방문 기록은 페이지 로드 완료 시 저장, 개별/전체 삭제
- 다운로드: `DownloadListener` → 확인 다이얼로그 → `DownloadManager`(다운로드 폴더). 풀스크린 동영상: `onShowCustomView`를 시스템 바 위 전체 화면 Dialog로 표시
- 보안: file/content 접근 차단, 혼합 콘텐츠 차단, 팝업 창 차단, 서드파티 쿠키 차단, Safe Browsing on, JS 브릿지(addJavascriptInterface) 없음, 웹 페이지가 여는 file:/content:/intent: 링크는 무시(tel:/mailto:/sms:만 외부 앱으로). 카메라·마이크·위치 권한 요청은 거부(기본 동작)
- 앱 전체 `usesCleartextTraffic=true`: 브라우저가 http:// 사이트를 열 수 있게 하려는 것 (이 앱의 네트워크 사용은 브라우저뿐)
- **외부 링크 옵션**: 매니페스트의 `ExternalLinkAlias`(http/https VIEW 필터)는 기본 비활성. 설정 탭 "외부 링크를 이 브라우저로 열기"로 켜고 끔(`settings/ExternalLinks`, `PackageManager.setComponentEnabledSetting`). 켜면 다른 앱 링크가 `BrowserViewModel.openExternal` → 새 탭으로 열림. 기본을 꺼 둔 이유: 켜는 순간 폰의 모든 웹 링크 "다음으로 열기" 선택지에 이 앱이 나타나 기본 브라우저가 될 수 있는데, 이 브라우저는 파일 업로드(<input type=file>)·인증서 오류 화면·비밀번호 저장 같은 일반 브라우저 기능이 없는 가벼운 인앱 브라우저라서
- 미구현: 파일 업로드(`onShowFileChooser`), 탭 상태 영구 저장(앱 재시작 시 새 탭 1개로 시작), 비공개 모드

## 문자 내용 보기 (messages/)
- 연락처 상세와 통화 내역 화면에 "통화 기록 / 메시지" 전환 칩. 메시지는 말풍선(받은 문자 왼쪽, 보낸 문자 오른쪽)으로 최근 30건, 가장 최근 것이 맨 위. 아래 "문자 앱에서 이어서 보기"는 시스템 문자 앱을 `smsto:`로 엶
- `SmsRepository`: `Telephony.Sms` 프로바이더(READ_SMS 필요)에서 최근 5000건을 훑어 `numberKey`가 같은 번호(010… = +82 10…)만 골라냄. 화면을 열 때만 읽고 저장하지 않음. `MessagesPane`이 권한 요청(허용/설정 열기)까지 처리
- 한계: 일반 SMS만 보임. MMS, RCS(채팅+), 카카오톡 같은 채팅 메시지는 SMS 프로바이더에 없어 표시되지 않음
- READ_SMS는 통화기록처럼 제한된 권한이라, adb로 설치하면 허용 팝업이 뜨지만 파일 관리자 등으로 직접 설치한 APK는 막힐 수 있음(막히면 기본 전화 앱 설정 후에도 안 되면 설정 > 앱 > SuperDialer > 권한 확인)
- 에뮬레이터 테스트: 받은 문자 `adb emu sms send 번호 "내용"`

## 내장 게임 (games/, 6단계)
- 게임 선택 화면: 카드 2열 그리드(제목·설명·최고 점수). 카드를 탭하면 `GamePlayer`(WebView)로 실행, 뒤로가기/화살표로 선택 화면 복귀
- **게임 추가 방법**: `app/src/main/assets/games/<폴더>/index.html`을 넣고 `assets/games/games.json`에 `{id, title, description, path, color}` 한 줄 추가 (코틀린 수정 없음). 파싱은 `GameCatalog.parse`(깨진 항목·`..`/절대경로 path는 건너뜀, 테스트 있음)
- 기본 게임: 2048(스와이프), 스네이크(스와이프로 방향 전환, 탭 시작), 벽돌깨기(터치 드래그로 패들). 각각 단일 HTML 파일
- 점수: 게임이 `Native.getBest()` / `Native.submitScore(점수)`를 호출 → `GameScores`(SharedPreferences, 게임 id별 최고 점수, Compose 상태라 카드에 바로 반영). 브릿지(`GameBridge`)는 이 두 함수만 노출
- 보안: 게임은 `WebViewAssetLoader`로 가상 https 호스트(appassets.androidplatform.net)에서만 로드, 다른 호스트로의 이동은 차단, 파일/콘텐츠 접근 off. 점수 브릿지는 우리 파일에만 노출됨
- **렌더링 주의(해결한 버그)**: `AndroidView(factory = { WebView(context) ... })`처럼 factory 안에서 WebView를 만들면 에뮬레이터에서 페이지 배경만 그려지고 내용이 안 보였음(DOM·JS는 정상). 브라우저처럼 **WebView를 `MutableContextWrapper(applicationContext)`로 따로 만들고 loadUrl → 화면에 붙일 때 `attachTo(activity)`** 하는 방식으로 바꾸자 해결 (`GamePlayer.createGameWebView`)
- JS 오류 확인: logcat 태그 `GameConsole`

## 릴리스 배포 (GitHub Actions, 7단계)
- `v*` 태그를 푸시하면 `.github/workflows/release.yml`이 단위 테스트 → 서명된 `assembleRelease` → `apksigner verify` → GitHub Release에 `SuperDialer-<태그>.apk` 업로드
- 서명 정보는 GitHub Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`에서 읽음 (환경변수로 `KEYSTORE_FILE` 등을 넘기는 방식은 `app/build.gradle.kts`의 `signingConfigs`). `KEYSTORE_FILE`이 없으면 서명 없이 `app-release-unsigned.apk` 생성(로컬 빌드용)
- 버전: `VERSION_NAME`(태그에서 v 제거), `VERSION_CODE`(워크플로 실행 번호) 환경변수, 없으면 0.1.0 / 1
- keystore 만들기·base64 변환·Secrets 등록·태그 푸시 절차는 README.md "릴리스 배포" 참고. **Secrets 등록과 태그 푸시는 사용자가 직접 함** (로컬에서는 임시 키로 서명 빌드·`apksigner verify`까지 검증함)
- 릴리스 키와 디버그 키가 달라 폰에 디버그 빌드가 있으면 지우고(데이터 삭제) 릴리스를 설치해야 함
- `.gitattributes`로 `gradlew`는 LF 고정, `gradlew`는 git에서 실행 권한(100755)으로 저장됨 (리눅스 러너용)

## 디자인 시스템 (UI 개편)
- **팔레트**(`ui/theme/Theme.kt`): 초록(primary) + 슬레이트 블루(secondary) + 앰버(tertiary, 즐겨찾기 별). 밝은 테마는 연한 회녹색 배경 위에 흰 카드, 어두운 테마는 거의 검정에 가까운 녹색 배경 위에 한 단계 밝은 카드. 폰 배경화면 색(Material You)은 기본 꺼짐이며 설정 탭 "배경화면 색상 따라가기"로 켤 수 있음(`AppSettings.dynamicColor`)
- **색 도우미**: `CallButtonGreen`(채운 발신 버튼), `EndCallRed`(종료/거절), `callGreen()`(톤 바탕 위 통화 아이콘), `incomingColor()/outgoingColor()`(수신 초록·발신 파랑, 부재중은 error), `starColor()`. 하드코딩 색 대신 이것들을 쓸 것
- **서체/모양**: 제목·이름은 SemiBold/Bold, 모서리 12/18/24/32dp. 목록은 `groupShape(index, count)`로 그룹의 바깥 모서리만 크게(24dp) 안쪽은 작게(6dp) 하고 행 사이 2dp 간격 → 날짜/글자 그룹별 둥근 카드 묶음(최근기록, 연락처, 번호 목록). 설정은 `SettingsGroup` 카드
- **아바타**: `InitialAvatar`가 이름 해시로 8가지 색 쌍(밝은/어두운 테마별) 중 하나를 고름. 같은 사람은 항상 같은 색
- **하단 탭 바**: 선택 표시(indicator)는 primaryContainer, 바 배경은 surfaceContainer
- **키패드**: 키는 surfaceContainer 원(눌리면 primaryContainer), 숫자 32sp, 발신 버튼 80dp 초록 + 그림자
- **통화 화면**: 어두운 초록 그라데이션 배경, 80dp 받기/거절 버튼
- 참고한 벤치마크: 구글 전화 앱의 Material 3 Expressive 개편(단순한 탐색, 시간순 목록, 큰 둥근 모양), 에이닷 전화(어두운 카드형 행, 굵은 이름, 초록 원형 발신 버튼, 탭하면 펼쳐지는 액션)

## 아이콘
- 앱 아이콘: `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` (초록 그라데이션 + 흰 수화기 + 노란 스파클, Android 13 테마 아이콘 지원)
- 브라우저 링크 아이콘: `browser/data/FaviconStore`가 사이트 자체의 `<link rel=icon>`/`/favicon.ico`를 받아 캐시(`cacheDir/favicons`), 제3자 서비스 미사용. `browser/SiteIcon`이 스피드 다이얼/탭/북마크/기록에 표시하고 아이콘이 없으면 글자 타일. 단위 테스트 `FaviconStoreTest`
- 게임 카드 아이콘: `games/GameIcon`(Canvas로 직접 그림, id별). 새 게임 추가 시 여기에 케이스 추가(없으면 점 격자)

## 앱을 나가도 3분 유지 (BrowserSession / GameSession)
- `browser/BrowserSession`이 `BrowserViewModel`(탭·WebView)을 Activity가 아닌 앱 수준에서 보관. `MainActivity.onStop`에서 3분 타이머 시작, `onStart`에서 취소. 만료되면 `release()`로 WebView 해제(+`GameSession.release()`), 이미 만료된 뒤 멈춰 있던 Activity가 돌아오면 `recreate()`
- `games/GameSession`: 열린 게임 id(`playingId`)와 게임 WebView 보관. 게임 목록으로 돌아가면(`close()`) 그 게임은 종료
- 프로세스가 죽은 경우 대비: 나갈 때 탭 주소·선택 탭·웹/게임 섹션·열린 게임 id를 SharedPreferences(`browser_session`)에 저장, 3분 이내에 다시 열면 복원(페이지는 다시 로드, 스크롤·게임 판은 초기화)
- 브라우저 상단 바: 사이트 진입/화면 맨 위 터치 때만 나타나고 2초(`BAR_AUTO_HIDE_MS`) 무터치면 사라짐. 주소 입력 중·메뉴 열림 중엔 유지. 스크롤 올림으로는 안 뜸

## 키패드 레이아웃 (고정 높이)
- 자동완성은 `SuggestionStrip`(가로 한 줄, 높이 68dp 고정 슬롯)로 표시해서 제안 유무와 상관없이 키패드 크기가 변하지 않음 (예전엔 제안이 늘면 키가 줄어 글자/영문이 잘렸음). 키 안의 숫자/영문 크기는 키 지름에 비례해 조정
- 번호 표시는 한 줄에 다 들어올 때까지 글자 크기를 42sp부터 2sp씩 줄임(최소 16sp), `...`로 자르지 않음
- 좁은 화면 테스트: 에뮬레이터에서 `adb -s emulator-5554 shell wm size 1080x1700` + `settings put system font_scale 1.3`, 끝나면 `wm size reset`, font_scale 1.0

## 계정 / 동기화 (account/, 선택 사항)
- 설정 탭 "계정"에서 이메일+비밀번호 로그인/회원가입(Supabase Auth). **로그인 없이도 앱 전체 사용 가능**, 로그인하면 시작 페이지 링크·북마크·게임 최고 점수·키패드음/화면 색 설정·거절 메시지를 동기화. 통화·연락처·문자는 올리지 않음
- Supabase 프로젝트: `wugixanaquagedgtqmyv` (이름 "Checked", 리전 ap-northeast-2). 체크리스트 노트 웹앱(`extension/Summarizer`, 별도 repo `Jangwoo0827/checklist_summarizer`)과 **같은 프로젝트를 공유**. 우리 데이터는 `public.superdialer_sync(user_id uuid PK → auth.users, data jsonb, updated_at)` 한 테이블뿐이고 RLS로 본인 행만 읽기/쓰기(authenticated). 체크리스트 앱의 `user_data`(아이디만 쓰는 익명 정책)는 그대로 둠
- `SupabaseApi`: SDK 없이 HttpURLConnection으로 `/auth/v1/signup|token|recover`와 `/rest/v1/superdialer_sync`. URL과 publishable key(`sb_publishable_…`)는 공개용이라 코드에 직접 둠. 에러는 한글 메시지로 변환(`errorMessage`, 테스트 있음)
- `AccountManager`(싱글톤, Compose 상태): 세션은 SharedPreferences `account`에 저장 → 앱을 껐다 켜도 자동 로그인, access token은 만료 60초 전에 refresh. refresh 실패 시 로그아웃 + 안내
- 동기화 규칙(`runSync`): 서버에 스냅샷 1개. 서버 `updated_at`이 마지막 동기화 때와 다르면 받아오되(로컬도 바뀌었으면 로컬 우선으로 올림), 이 폰만 바뀌었으면 올림. 이 폰의 첫 동기화는 서버 쪽이 우선. 게임 점수는 항상 큰 값. 앱이 앞으로 오거나 나갈 때(`MainActivity.onStart/onStop`, 15초 간격 제한), 로그인 직후, "지금 동기화" 버튼에서 실행. 스냅샷 모양/병합은 `SyncSnapshot`(테스트 있음)
- 가입 시 메일 인증이 켜져 있으면 가입 직후 세션이 없어 "메일 인증 후 로그인" 안내. 인증 없이 바로 쓰려면 Supabase 대시보드 Authentication > Sign In / Providers > Email 에서 "Confirm email"을 끄면 됨(이 설정은 앱/MCP로 바꾸지 않음)
- **체크리스트 자동 로그인**(`browser/ChecklistLink`): 시작 페이지 첫 타일 "체크리스트"(`https://jangwoo0827.github.io/checklist_summarizer/`, DB v3 마이그레이션이 기존 폰에도 맨 앞에 추가). 로그인 상태에서 이 주소의 페이지가 다 열리면 localStorage `checklist_note_user_v1`을 읽어 있으면 계정에 기억(다른 폰으로도 동기화), 없고 계정에 기억된 아이디가 있으면 한 번 넣고 새로고침 → 자동 로그인. 사이트 쪽 로그아웃을 해도 같은 탭에선 다시 넣지 않음. 형식이 맞는 아이디(`^[a-z0-9가-힣_-]{2,32}$`)만, 정확히 그 주소에서만 주입
- 체크리스트 앱은 비밀번호 없는 "아이디" 동기화라 아이디를 아는 사람은 누구나 볼 수 있음(사이트 README 참고). SuperDialer 계정 데이터는 그와 달리 RLS로 본인만 접근

## 움직임 (슬라이딩 UI)
- `navigation/NavTransitions`: 탭 사이는 탭 순서 방향으로 살짝 밀리며 페이드, 하위 화면(연락처 상세·통화 내역·차단 관리 등)은 오른쪽에서 밀려 들어오고 뒤로 가면 오른쪽으로 나감(아래 화면은 1/4만큼 시차). NavHost의 enter/exit/popEnter/popExit에 연결
- 하단 탭 바와 웹·게임 상단 전환 바는 몰입 모드 진입 시 `AnimatedVisibility`로 슬라이드/접힘. 브라우저↔게임 섹션, 시작 페이지↔웹 페이지, 탭·북마크·기록 패널(아래에서 올라옴), 게임 목록↔게임 화면, 최근기록 행 펼침도 모두 애니메이션

## 테마 선택
- 설정 > 화면: 테마(시스템/라이트/다크, `ThemeMode`) + 강조 색 5종(초록/파랑/보라/주황/분홍, `AccentColor`). `AppSettings.themeMode/accent`에 저장, 계정 동기화 대상(`SyncSnapshot.themeMode/accent`)
- 초록은 손으로 맞춘 기본 팔레트 그대로, 나머지는 `schemeFor`가 강조 역할(primary/container)을 바꾸고 회색 계열을 강조 색 쪽으로 살짝 물들임. '배경화면 색상 따라가기'가 켜져 있으면 그쪽이 우선

## 스피드 다이얼 순서·폴더
- 타일을 길게 눌러 끌면 순서 변경(`SpeedDialGrid`, `detectDragGesturesAfterLongPress` + 그리드 레이아웃 정보로 겹친 칸과 교체, 끝나면 `reorderSpeedDials`로 저장). 끌지 않고 길게만 누르면 수정/폴더로 이동/삭제 메뉴. adb로 테스트할 땐 `input swipe`가 바로 움직여 롱프레스가 안 되므로 `input draganddrop x1 y1 x2 y2 2000` 사용
- 폴더: `SpeedDial.parentId/isFolder`(DB v4, 마이그레이션 3→4), 한 단계만. + 버튼의 링크/폴더 선택으로 추가, 폴더 타일은 안의 사이트 아이콘 4개 미리보기, 탭하면 폴더 안으로(뒤로가기로 나옴). 폴더 삭제 시 안의 링크도 삭제
- 계정 동기화: `SyncSnapshot.Link(folder, parent=폴더의 목록 인덱스)`, `SpeedDialTree.flatten/plan`(테스트 있음)

## 파일 업로드 / 다운로드 관리
- 업로드: 페이지의 `<input type=file>`은 `WebChromeClient.onShowFileChooser` → `BrowserViewModel.fileChooser` → 브라우저 화면이 시스템 파일 선택기를 열고(`FileChooserParams.createIntent`), 결과를 `finishFileChooser`로 돌려줌(취소해도 반드시 한 번 호출, 이전 요청은 null로 응답). 에뮬레이터에서 the-internet.herokuapp.com/upload로 확인
- 다운로드 관리: 브라우저 ⋮ 메뉴 > 다운로드(`DownloadsPanel`). 별도 DB 없이 `DownloadManager.query`로 이 앱이 시작한 다운로드를 1초마다 읽어 진행률/완료/실패 표시, 완료 항목을 누르면 `getUriForDownloadedFile`로 열기, 삭제는 `DownloadManager.remove`(파일도 삭제)
