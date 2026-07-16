# App Check 적용 로드맵

기준일: 2026-07-16

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Android 앱, 관리자 웹, callable Functions, Spring Core API, Next.js 관리자 서버, Firestore, Storage, Authentication에 App Check를 언제 어떤 순서로 적용할지 운영 기준을 정한다.

## 선택한 방식

Functions callable부터 제한적으로 강제한 뒤 custom backend인 Spring Core API와 Next.js 관리자 서버가 `X-Firebase-AppCheck`를 검증하도록 전환한다. Storage, Firestore, Authentication은 App Check 토큰 발급과 주요 사용자 흐름 검증이 끝난 뒤 서비스별로 전환한다.

## 대안

- Firebase Console에서 Firestore와 Storage enforcement를 한 번에 켠다.
- Functions, Firestore, Storage 모두 운영 전까지 보류한다.
- App Check 대신 Firestore/Storage Rules와 Auth만 강화한다.

## 선택 이유

App Check enforcement를 켜면 유효한 App Check 토큰이 없는 요청은 거부된다. BoDeul은 Android 앱, 관리자 웹, callable Functions, Firestore 직접 접근, Storage 업로드/미리보기가 함께 연결돼 있어 한 번에 켜면 정상 사용자 흐름까지 막을 수 있다. 현재 코드에는 callable Functions 전환 스위치가 이미 있으므로, Functions부터 검증하면 위험 범위를 줄이면서 abuse 방어를 시작할 수 있다.

## 리스크

- App Check는 Auth와 Rules를 대체하지 않는다. 인증, 역할 검증, Rules는 계속 보안 기준이다.
- 디버그 토큰은 유효 기기로 간주되므로 노출되면 즉시 Firebase Console에서 폐기해야 한다.
- 기존 Vite 관리자 웹 코드는 `ReCaptchaV3Provider`를 지원하지만 live provider는 등록되지 않았다. 새 Next.js 관리자 웹은 Firebase 권고에 따라 reCAPTCHA Enterprise를 사용한다.
- Firebase CLI `15.22.3` 기준으로 `appcheck:*` 명령이 노출돼 있지 않아 App Check 등록과 enforcement 변경은 Console 또는 공식 REST API로 수행한다.

## 현재 구현 상태

| 영역 | 현재 상태 | 파일 |
| --- | --- | --- |
| Android debug | Debug provider 설치, 토큰 자동 갱신 활성화 | `app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java` |
| Android release | Play Integrity provider 설치, 토큰 자동 갱신 활성화 | `app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java` |
| 앱 시작점 | Firebase App Check provider 설치를 앱 시작 시 호출 | `app/src/main/java/com/example/bodeul/BodeulApplication.java` |
| 관리자 웹 | 기존 Vite 코드에 reCAPTCHA v3 초기화 경로가 있으나 provider와 배포 환경값은 미설정 | `admin-web/src/appCheck.ts` |
| 관리자 웹 개발 | `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` 또는 localhost 자동 debug token 사용 | `admin-web/src/appCheck.ts` |
| callable Functions | `ENABLE_APPCHECK_ENFORCEMENT=true`일 때 `enforceAppCheck` 활성화 | `functions/src/auth.js`, `functions/src/action-delivery.js`, `functions/src/reminders.js` |
| Spring Core API | `off/observe/enforce` 검증 구현, Cloud Run preview 리비전 `00007-8hk`에 observe 배포 완료 | `core-api/`, `core-api-preview-deploy.yml` |
| Next.js 관리자 서버 | 단계 이전 중이며 App Check custom backend 검증은 아직 없음 | `bodeul-admin-web` #10 |
| Firestore/Storage | Firebase Console enforcement 보류 | Firebase Console |

## 2026-07-16 실제 준비 상태

읽기 전용 REST API와 Cloud Monitoring을 사용해 `bodeul-dev`를 확인했다. 상세 결과는 [App Check 준비 상태 점검](../reports/app-check-readiness-2026-07-16.md)에 남겼다.

| 항목 | 확인 결과 |
| --- | --- |
| Firebase 프로젝트 | `bodeul-dev`만 존재하며 별도 production 프로젝트는 없음 |
| Android 앱 | 앱 1개, SHA-256 1개, Play Integrity 설정 리소스 있음, debug token 0개 |
| 관리자 웹 앱 | 앱 1개, reCAPTCHA v3/Enterprise provider 미등록, debug token 0개 |
| Firebase 서비스 | Firestore, Storage, Authentication 모두 `UNENFORCED` |
| callable Functions | 배포 함수 10개 중 `ENABLE_APPCHECK_ENFORCEMENT=true` 0개 |
| 최근 30일 메트릭 | 전체 5,580건 중 `VALID` 0건, invalid 4,163건, outdated client 1,353건, unknown origin 64건 |
| Core API preview | 리비전 `bodeul-core-api-preview-00007-8hk`가 observe로 트래픽 100% 처리, 배포 후 인증 요청과 판정 로그는 아직 0건 |

현재 판단은 `HOLD`다. Firebase는 enforcement 전에 정상 요청이 verified로 관측되는지 확인하도록 안내한다. 지금 강제하면 현재 관측된 요청 전체가 차단 후보가 되므로 provider와 debug token을 먼저 준비한다.

## 개발/운영 환경 경계

| 환경 | 기준 |
| --- | --- |
| 개발 | `bodeul-dev`에서 provider 등록, debug token, preview와 실기기 흐름, 단계별 enforcement를 검증한다. 현재는 모든 서비스를 `UNENFORCED`로 유지한다. |
| 운영 | #134에서 별도 Firebase 프로젝트를 만든 뒤 Android 앱 등록, Web provider, Auth domain, Hosting/WIF를 독립 구성한다. 개발 debug token과 provider secret을 복사하지 않는다. |

관리자 웹은 Next.js 전환 중이므로 기존 Vite reCAPTCHA v3 설정에 추가 투자하지 않는다. 새 관리자 웹에서 reCAPTCHA Enterprise를 연결하고 preview에서 `VALID` 요청을 확인한 뒤 운영 설정으로 승격한다.

## 강제 전환 순서

| 단계 | 목표 | 완료 조건 | 실행 |
| --- | --- | --- | --- |
| 0. 등록 현황 확인 | 모든 클라이언트를 Firebase App Check 앱으로 등록 | Android SHA-256, Play Integrity 연결, 관리자 웹 site key, debug token allowlist가 정리됨 | Firebase Console에서 Android/Web 앱 등록과 메트릭 수집 상태 확인 |
| 1. 개발 환경 안정화 | 디버그/로컬 검증이 enforcement 전에도 막히지 않게 준비 | Android debug token과 관리자 웹 debug token이 등록되고, 토큰이 저장소에 없음을 확인 | logcat/브라우저 콘솔로 debug token 확인, Console allowlist 등록 |
| 2. 배포 환경 토큰 검증 | 운영 후보 빌드와 Hosting URL에서 실제 provider 토큰 발급 확인 | Android release 빌드, 관리자 웹 preview/live에서 App Check 실패 없이 로그인/예약/심사 흐름 통과 | 실기기 테스트, Hosting preview/live 테스트, App Check 메트릭 확인 |
| 3. Functions callable enforcement | 가장 좁은 서버 진입점부터 차단 적용 | callable 호출 실패가 없고 소셜 로그인/중복 확인 흐름이 통과 | `ENABLE_APPCHECK_ENFORCEMENT=true` 설정 후 Functions 재배포 |
| 4. custom backend 검증 | Spring Core API와 Next.js 관리자 서버 보호 | Spring 구현 완료. Android 실기기 `valid`와 Next.js provider token 확인 필요 | `X-Firebase-AppCheck` 검증을 observe 후 enforce로 전환 |
| 5. Storage enforcement | 파일 업로드/미리보기 보호 | 매니저 서류, 채팅 첨부 업로드/다운로드가 Android와 관리자 웹에서 통과 | Firebase Console에서 Storage enforcement 전환 |
| 6. Firestore enforcement | DB 직접 접근 보호 | Android 전체 주요 흐름과 관리자 웹 직접 접근 범위가 통과 | Firebase Console에서 Firestore enforcement 전환 |
| 7. Authentication enforcement | 로그인과 token 발급 경로 보호 | Android와 관리자 웹의 로그인/갱신/로그아웃 흐름이 모두 통과 | Firebase Console에서 Authentication enforcement 전환 |
| 8. 운영 모니터링 | 정상 사용자 차단 여부 감시 | 401/403, App Check token 오류, 고객 문의가 정상 범위 | 1주간 일일 확인 후 주간 점검으로 전환 |

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
- 기존 구현은 reCAPTCHA v3 기준이지만, 새 Next.js 관리자 웹은 reCAPTCHA Enterprise로 전환한다.
- 관리자 로그인, 매니저 심사, 서류 미리보기, 문의 응답, 알림/리마인더 수동 실행이 통과한다.

### Functions

- `functions/package.json`의 `firebase-functions`는 App Check runtime option을 지원하는 버전이다.
- `ENABLE_APPCHECK_ENFORCEMENT` 값 변경 뒤 Functions를 재배포한다.
- 소셜 로그인 custom token 발급 함수가 Android 앱에서 정상 호출된다.
- 관리자용 수동 dispatch callable 함수가 관리자 웹에서 정상 호출된다.
- scheduled Functions는 callable이 아니므로 App Check enforcement 대상과 분리해 본다.

### Custom backend

- Android와 관리자 웹은 App Check token을 URL이 아닌 `X-Firebase-AppCheck` header로 전송한다.
- Spring Core API는 공식 JWKS와 claim 조건을 Spring Security로 검증하고, Next.js 관리자 서버는 Firebase Admin SDK로 token을 검증한다.
- 처음에는 누락/유효 상태만 기록하고 정상 앱의 header 전송이 확인된 뒤 차단을 켠다.
- Firebase ID token 인증과 PostgreSQL role 인가는 App Check와 별도로 계속 적용한다.

### Firestore/Storage/Authentication

- Firebase Console의 App Check 메트릭에서 정상 클라이언트 요청이 충분히 관측된다.
- Mock 모드 검증은 enforcement 판단 근거로 사용하지 않는다.
- Firestore enforcement 전에는 Android와 관리자 웹의 직접 Firestore 접근 화면을 모두 확인한다.
- Storage enforcement 전에는 파일 업로드, 다운로드 URL, 관리자 미리보기, 채팅 첨부를 확인한다.
- Authentication enforcement는 로그인 blast radius가 가장 크므로 마지막에 적용한다.

## 롤백 기준

| 영역 | 롤백 조건 | 조치 |
| --- | --- | --- |
| Functions callable | 정상 앱/관리자 웹에서 callable 호출이 반복적으로 실패 | `ENABLE_APPCHECK_ENFORCEMENT=false` 또는 미설정으로 되돌리고 Functions 재배포 |
| Custom backend | 정상 앱의 Core API/관리자 API 호출이 401로 차단 | 서버 enforcement를 observe 모드로 되돌리고 Firebase ID token 인가는 유지 |
| Storage | 정상 서류 업로드/미리보기/채팅 첨부가 차단 | Firebase Console에서 Storage enforcement 해제 |
| Firestore | 로그인 후 주요 목록, 예약, 세션, 관리자 대시보드 접근이 차단 | Firebase Console에서 Firestore enforcement 해제 |
| Authentication | 정상 로그인, token 갱신, 로그아웃이 차단 | Firebase Console에서 Authentication enforcement 해제 |
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

현재 프로젝트에서는 App Check를 강제하지 않는다. Spring Core API와 Android header 전달 코드는 준비했고 preview 리비전 `00007-8hk`는 observe 모드로 운용한다. 배포와 무인증 smoke test는 통과했지만 인증 요청이 없어 실제 판정 로그는 아직 없다. Android debug token과 Play Integrity 실기기 검증, Next.js 관리자 웹의 reCAPTCHA Enterprise 연결, 각 등록 앱의 `VALID` 요청 관측을 먼저 완료한다. 그 다음 Functions callable과 custom backend를 제한적으로 전환한다. Storage, Firestore, Authentication은 주요 사용자 흐름과 서비스별 메트릭을 다시 확인한 뒤 순서대로 적용한다.

## 참고 공식 문서

- Firebase App Check 개요: <https://firebase.google.com/docs/app-check>
- App Check enforcement 활성화: <https://firebase.google.com/docs/app-check/enable-enforcement>
- Cloud Functions App Check enforcement: <https://firebase.google.com/docs/app-check/cloud-functions>
- Android Play Integrity provider: <https://firebase.google.com/docs/app-check/android/play-integrity-provider>
- Android debug provider: <https://firebase.google.com/docs/app-check/android/debug-provider>
- Web reCAPTCHA v3 provider: <https://firebase.google.com/docs/app-check/web/recaptcha-provider>
- Web reCAPTCHA Enterprise provider: <https://firebase.google.com/docs/app-check/web/recaptcha-enterprise-provider>
- Android custom backend 보호: <https://firebase.google.com/docs/app-check/android/custom-resource>
- Web custom backend 보호: <https://firebase.google.com/docs/app-check/web/custom-resource>
- Custom backend token 검증: <https://firebase.google.com/docs/app-check/custom-resource-backend>
