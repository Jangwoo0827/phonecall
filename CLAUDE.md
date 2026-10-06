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
- `incall/` 통화 중 화면 (현재는 `SuperInCallService` 스텁만 존재)
- `browser/` 내장 웹브라우저
- `games/` 내장 게임
- `navigation/` 하단 탭 네비게이션 (키패드/최근기록/연락처/브라우저/게임)
- `ui/theme/` 테마

현재 상태: 하단 탭 뼈대만 구현됨. 각 탭 화면은 빈 화면.

## 기본 전화 앱 요건 (매니페스트에 이미 반영)
- `MainActivity`에 `DIAL`(tel/없음), `VIEW tel` intent-filter
- `InCallService` (`BIND_INCALL_SERVICE` 권한, `IN_CALL_SERVICE_UI` 메타데이터)
- 런타임 권한 요청 및 `RoleManager.ROLE_DIALER` 요청 코드는 아직 없음

## 빌드 / 설치
```bash
./gradlew assembleDebug
# 결과물: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
- JDK 17 이상 필요 (`JAVA_HOME` 설정), Android SDK는 `local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`로 지정 (`local.properties`는 gitignore)
- 폰에서 설치할 때는 개발자 옵션 > USB 디버깅 또는 APK 직접 설치(출처를 알 수 없는 앱 허용)
- 기본 전화 앱 설정: 설정 > 앱 > 기본 앱 > 전화 앱

## 규칙
- 각 탭 기능은 해당 패키지 안에서 구현하고 `navigation/`은 라우팅만 담당
- 한글 문자열은 현재 코드 내 상수 사용 (추후 필요시 strings.xml로 이동)
