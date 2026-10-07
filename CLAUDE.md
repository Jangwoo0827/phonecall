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

현재 상태: 키패드(자동완성), 최근기록, 연락처(즐겨찾기 토글), 통화 화면, 설정 탭(차단/거절 메시지) 구현됨. 웹·게임 탭(브라우저/게임 전환 바)은 두 화면 모두 빈 화면.

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
