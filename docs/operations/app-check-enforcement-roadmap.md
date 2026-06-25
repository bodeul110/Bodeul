# App Check 적용 로드맵

기준일: 2026-06-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Android 앱, 관리자 웹, callable Functions, Firestore, Storage에 App Check를 언제 어떤 순서로 강제할지 운영 기준을 정한다.

## 선택한 방식

Functions callable부터 제한적으로 강제하고, Storage와 Firestore는 App Check 토큰 발급과 주요 사용자 흐름 검증이 끝난 뒤 서비스별로 전환한다.

## 대안

- Firebase Console에서 Firestore와 Storage enforcement를 한 번에 켠다.
- Functions, Firestore, Storage 모두 운영 전까지 보류한다.
- App Check 대신 Firestore/Storage Rules와 Auth만 강화한다.

## 선택 이유

App Check enforcement를 켜면 유효한 App Check 토큰이 없는 요청은 거부된다. BoDeul은 Android 앱, 관리자 웹, callable Functions, Firestore 직접 접근, Storage 업로드/미리보기가 함께 연결돼 있어 한 번에 켜면 정상 사용자 흐름까지 막을 수 있다. 현재 코드에는 callable Functions 전환 스위치가 이미 있으므로, Functions부터 검증하면 위험 범위를 줄이면서 abuse 방어를 시작할 수 있다.

## 리스크

- App Check는 Auth와 Rules를 대체하지 않는다. 인증, 역할 검증, Rules는 계속 보안 기준이다.
- 디버그 토큰은 유효 기기로 간주되므로 노출되면 즉시 Firebase Console에서 폐기해야 한다.
- 관리자 웹은 현재 `ReCaptchaV3Provider`를 사용한다. Firebase 공식 문서는 새 웹 통합에 reCAPTCHA Enterprise 사용을 권장하므로, 운영 enforcement 전에는 v3 유지 또는 Enterprise 전환을 명시적으로 결정해야 한다.
- Firebase CLI `15.22.2` 기준으로 `appcheck:*` 명령이 노출돼 있지 않아 App Check 등록, 메트릭 확인, enforcement 전환은 Firebase Console에서 수행해야 한다.

## 현재 구현 상태

| 영역 | 현재 상태 | 파일 |
| --- | --- | --- |
| Android debug | Debug provider 설치, 토큰 자동 갱신 활성화 | `app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java` |
| Android release | Play Integrity provider 설치, 토큰 자동 갱신 활성화 | `app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java` |
| 앱 시작점 | Firebase App Check provider 설치를 앱 시작 시 호출 | `app/src/main/java/com/example/bodeul/BodeulApplication.java` |
| 관리자 웹 | `VITE_FIREBASE_APPCHECK_SITE_KEY`가 있을 때 reCAPTCHA v3 provider 초기화 | `admin-web/src/appCheck.ts` |
| 관리자 웹 개발 | `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` 또는 localhost 자동 debug token 사용 | `admin-web/src/appCheck.ts` |
| callable Functions | `ENABLE_APPCHECK_ENFORCEMENT=true`일 때 `enforceAppCheck` 활성화 | `functions/src/auth.js`, `functions/src/action-delivery.js`, `functions/src/reminders.js` |
| Firestore/Storage | Firebase Console enforcement 보류 | Firebase Console |

## 강제 전환 순서

| 단계 | 목표 | 완료 조건 | 실행 |
| --- | --- | --- | --- |
| 0. 등록 현황 확인 | 모든 클라이언트를 Firebase App Check 앱으로 등록 | Android SHA-256, Play Integrity 연결, 관리자 웹 site key, debug token allowlist가 정리됨 | Firebase Console에서 Android/Web 앱 등록과 메트릭 수집 상태 확인 |
| 1. 개발 환경 안정화 | 디버그/로컬 검증이 enforcement 전에도 막히지 않게 준비 | Android debug token과 관리자 웹 debug token이 등록되고, 토큰이 저장소에 없음을 확인 | logcat/브라우저 콘솔로 debug token 확인, Console allowlist 등록 |
| 2. 배포 환경 토큰 검증 | 운영 후보 빌드와 Hosting URL에서 실제 provider 토큰 발급 확인 | Android release 빌드, 관리자 웹 preview/live에서 App Check 실패 없이 로그인/예약/심사 흐름 통과 | 실기기 테스트, Hosting preview/live 테스트, App Check 메트릭 확인 |
| 3. Functions callable enforcement | 가장 좁은 서버 진입점부터 차단 적용 | callable 호출 실패가 없고, 관리자 수동 발송/소셜 로그인/중복 확인 흐름이 통과 | `ENABLE_APPCHECK_ENFORCEMENT=true` 설정 후 Functions 재배포 |
| 4. Storage enforcement | 파일 업로드/미리보기 보호 | 매니저 서류, 채팅 첨부 업로드/다운로드가 Android와 관리자 웹에서 통과 | Firebase Console에서 Storage enforcement 전환 |
| 5. Firestore enforcement | DB 직접 접근 보호 | Android 전체 주요 흐름과 관리자 웹 목록/심사/문의/알림 흐름이 통과 | Firebase Console에서 Firestore enforcement 전환 |
| 6. 운영 모니터링 | 정상 사용자 차단 여부 감시 | 403, App Check token 오류, 고객 문의가 정상 범위 | 1주간 일일 확인 후 주간 점검으로 전환 |

## 단계별 검증 체크리스트

### Android

- Firebase Console에 Android 앱이 App Check 대상으로 등록돼 있다.
- release signing certificate의 SHA-256이 등록돼 있다.
- Google Play Console에서 Play Integrity API가 Firebase 프로젝트와 연결돼 있다.
- debug 빌드 실행 시 출력된 debug token이 Console allowlist에 등록돼 있다.
- debug token 값은 Git, PR, 공개 이슈, 로그 첨부에 남기지 않는다.
- 실기기에서 로그인, 예약 생성, 보호자/환자 연결, 동행 세션, 채팅 첨부, 리포트 조회가 통과한다.

### 관리자 웹

- `VITE_FIREBASE_APPCHECK_SITE_KEY`가 preview/live 배포 환경에 설정돼 있다.
- `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN`은 로컬 또는 CI 검증에만 사용하고 공개 저장소에 넣지 않는다.
- Firebase Console에서 관리자 웹 도메인이 App Check 앱 설정과 맞는다.
- 현재 구현은 reCAPTCHA v3 기준이다. 운영 enforcement 전에는 reCAPTCHA Enterprise 전환 여부를 결정한다.
- 관리자 로그인, 매니저 심사, 서류 미리보기, 문의 응답, 알림/리마인더 수동 실행이 통과한다.

### Functions

- `functions/package.json`의 `firebase-functions`는 App Check runtime option을 지원하는 버전이다.
- `ENABLE_APPCHECK_ENFORCEMENT` 값 변경 뒤 Functions를 재배포한다.
- 소셜 로그인 custom token 발급 함수가 Android 앱에서 정상 호출된다.
- 관리자용 수동 dispatch callable 함수가 관리자 웹에서 정상 호출된다.
- scheduled Functions는 callable이 아니므로 App Check enforcement 대상과 분리해 본다.

### Firestore/Storage

- Firebase Console의 App Check 메트릭에서 정상 클라이언트 요청이 충분히 관측된다.
- Mock 모드 검증은 enforcement 판단 근거로 사용하지 않는다.
- Firestore enforcement 전에는 Android와 관리자 웹의 직접 Firestore 접근 화면을 모두 확인한다.
- Storage enforcement 전에는 파일 업로드, 다운로드 URL, 관리자 미리보기, 채팅 첨부를 확인한다.

## 롤백 기준

| 영역 | 롤백 조건 | 조치 |
| --- | --- | --- |
| Functions callable | 정상 앱/관리자 웹에서 callable 호출이 반복적으로 실패 | `ENABLE_APPCHECK_ENFORCEMENT=false` 또는 미설정으로 되돌리고 Functions 재배포 |
| Storage | 정상 서류 업로드/미리보기/채팅 첨부가 차단 | Firebase Console에서 Storage enforcement 해제 |
| Firestore | 로그인 후 주요 목록, 예약, 세션, 관리자 대시보드 접근이 차단 | Firebase Console에서 Firestore enforcement 해제 |
| Debug token | 토큰이 PR, 이슈, 로그, 채팅에 노출 | Firebase Console에서 해당 token 폐기 후 새 token 등록 |

Firebase Console의 서비스별 enforcement는 적용 또는 해제 후 반영까지 시간이 걸릴 수 있으므로, 전환 직후에는 최소 15분 동안 같은 시나리오를 반복 확인한다.

## 운영 증적 형식

전환 전후에는 `docs/reports/app-check-readiness-YYYY-MM-DD.md` 형식으로 결과를 남긴다.

필수 항목:

- 확인한 Firebase 프로젝트와 Hosting URL
- Android debug/release 빌드 구분
- 관리자 웹 preview/live 구분
- 수행한 사용자 흐름
- Firebase Console App Check 메트릭 판단
- enforcement를 켠 서비스와 시각
- 실패/롤백 여부
- 남은 debug token과 폐기 대상 token 목록

## 현재 결론

현재 프로젝트에서는 App Check를 바로 전면 강제하지 않는다. 먼저 debug token과 웹 site key를 정리하고, 관리자 웹의 reCAPTCHA v3 유지 여부를 결정한 뒤, Functions callable부터 제한적으로 강제한다. Firestore와 Storage는 Android 앱과 관리자 웹의 주요 흐름이 모두 App Check token을 안정적으로 붙이는 것이 확인된 뒤 전환한다.

## 참고 공식 문서

- Firebase App Check 개요: <https://firebase.google.com/docs/app-check>
- App Check enforcement 활성화: <https://firebase.google.com/docs/app-check/enable-enforcement>
- Cloud Functions App Check enforcement: <https://firebase.google.com/docs/app-check/cloud-functions>
- Android Play Integrity provider: <https://firebase.google.com/docs/app-check/android/play-integrity-provider>
- Android debug provider: <https://firebase.google.com/docs/app-check/android/debug-provider>
- Web reCAPTCHA v3 provider: <https://firebase.google.com/docs/app-check/web/recaptcha-provider>
