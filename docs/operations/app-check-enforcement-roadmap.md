# App Check 적용 로드맵

기준일: 2026-08-26

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
- 별도 저장소의 Next.js와 Vite rollback build는 공용 reCAPTCHA Enterprise provider를 사용한다. Vite는 빌드·런타임 rollback 자산이며 provider rollback 경로가 아니다.
- Firebase CLI `15.22.3` 기준으로 `appcheck:*` 명령이 노출돼 있지 않아 App Check 등록과 enforcement 변경은 Console 또는 공식 REST API로 수행한다.

## 현재 구현 상태

| 영역 | 현재 상태 | 파일 |
| --- | --- | --- |
| Android debug | Debug provider 설치, 토큰 자동 갱신 활성화 | `app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java` |
| Android release | Play Integrity provider 설치, 토큰 자동 갱신 활성화, 서명 입력 누락 시 릴리스 산출물 차단 | `app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java`, `app/build.gradle.kts` |
| 앱 시작점 | Firebase App Check provider 설치를 앱 시작 시 호출 | `app/src/main/java/com/example/bodeul/BodeulApplication.java` |
| 관리자 웹 | Next.js에서 reCAPTCHA Enterprise provider와 header 전달을 구현하고 Vercel Production에서 client를 활성화 | [`bodeul-admin-web` PR #37](https://github.com/bodeul110/bodeul-admin-web/pull/37) |
| 관리자 웹 rollback | Vite build도 공용 Enterprise provider를 사용하며 빌드·런타임 rollback 자산으로만 유지 | `bodeul-admin-web` |
| callable Functions | `ENABLE_APPCHECK_ENFORCEMENT=true`일 때 `enforceAppCheck` 활성화 | `functions/src/auth.js`, `functions/src/action-delivery.js`, `functions/src/reminders.js` |
| Spring Core API | `off/observe/enforce` 검증 구현, Cloud Run preview 리비전 `00007-8hk`에 observe 배포 완료 | `core-api/`, `core-api-preview-deploy.yml` |
| Next.js 관리자 서버 | Firebase ID token·DB role 검증에 App Check `off/observe/enforce`, 허용 Web App ID와 rollback 경계를 추가하고 Production에 `observe` 배포 | [`bodeul-admin-web` PR #37](https://github.com/bodeul110/bodeul-admin-web/pull/37) |
| Firestore/Storage/Authentication | production은 모두 기본 `OFF`, 개발은 관찰 설정 유지 | Firebase Console, App Check REST API |

## 2026-07-17 개발 환경 검증 기록

읽기 전용 REST API와 Cloud Monitoring을 사용해 `bodeul-dev`를 확인했다. 상세 결과는 [App Check 준비 상태 점검](../reports/app-check-readiness-2026-07-16.md)에 남겼다.

| 항목 | 확인 결과 |
| --- | --- |
| Firebase 프로젝트 | 개발 `bodeul-dev`, production `bodeul-prod-110` 분리. production Android/Web 앱 등록과 Auth·Firestore·Storage 초기화 완료 |
| Android 앱 | 앱 1개, 등록 SHA-256 1개는 debug 인증서와 일치, release 후보 0개, Play Integrity 설정 리소스 있음, emulator와 ARM 실기기 debug token 등록 |
| 관리자 웹 앱 | 개발·production Web 앱 각 1개, reCAPTCHA v3/Enterprise provider 미등록, production debug token 미등록 |
| Firebase 서비스 | 개발 Firestore, Storage, Authentication은 `UNENFORCED`. production 현재 상태는 아래 별도 표에서 관리 |
| callable Functions | 당시 개발 배포 함수 10개 중 `ENABLE_APPCHECK_ENFORCEMENT=true` 0개 |
| 당시 최근 30일 메트릭 | 전체 5,581건 중 Android Firestore `VALID` 1건, invalid 4,163건, outdated client 1,353건, unknown origin 64건 |
| Core API preview | 리비전 `bodeul-core-api-preview-00007-8hk`가 observe로 트래픽 100% 처리, ARM debug 실기기 장소 검색 3건 `valid`와 HTTP 200 확인 |

현재 판단은 `HOLD`다. Android debug 실기기의 주요 화면 15건, 채팅 첨부, Kakao Map, Core API `valid`는 확인했다. Gradle에는 누락·부분 입력 상태의 릴리스 산출물을 차단하는 [서명 운영 계약](android-release-signing.md)을 추가했다. 관리자 웹은 production Enterprise provider, header 전달과 서버 `observe`까지 배포했지만 인증된 관리자 세션의 `VALID` 요청은 아직 확인하지 않았다. 팀 소유 release key와 인증서 등록, release Play Integrity, Web `VALID` 요청, #192의 enforce/rollback 재현이 남아 있어 아직 강제하지 않는다. 상세 Android 결과는 [Issue 190 ARM 실기기 검증 기록](../reports/issue-190-arm-device-validation-2026-07-17.md)에 남겼다.

## 2026-08-26 production 감사 기준과 현재 상태

Production 감사는 provider metadata만 보지 않고 앱 식별자, release 인증서, provider API, Firebase 서비스 모드, callable Functions와 실제 최근 요청을 함께 확인한다.

| 감사 항목 | 기준 | 현재 production |
| --- | --- | --- |
| Android·Web 앱 | 저장소가 고정한 앱 종류·App ID·package·활성 상태와 일치 | 앱 리소스는 존재 |
| Android release 인증서 | 보호된 `ANDROID_RELEASE_SHA256`과 Firebase 등록 SHA-256 exact match | 등록된 release SHA-256 없음 |
| Play Integrity | API 활성, production 앱 설정과 TTL, 팀 소유 release/Play 연결 | release provider 준비 조건 미충족 |
| reCAPTCHA Enterprise | API 활성, App Check site key와 제한된 `SCORE` Web key | provider·production hostname 제한과 client 전송 구현 완료, 인증된 `VALID` 요청 증거 없음 |
| Firebase 서비스 | Identity Platform·Firestore·Storage를 개별 조회 | 모두 기본 `OFF` |
| callable Functions | production 대상 함수와 `ENABLE_APPCHECK_ENFORCEMENT` 확인 | 배포 함수 0개 |
| 최근 7일 메트릭 | Android·Web별 `ALLOW`이면서 `VALID`인 요청 | 양쪽 모두 0건 |

현재 production 단계는 `preparing`이다. Web provider와 client 전송·서버 `observe` 배포는 완료했지만 Android release provider, 인증된 Web `VALID` 요청, monitoring, Functions enforcement와 Firebase 서비스 enforcement는 완료한 상태가 아니다.

## 감사 단계 모델

| 상태 | 의미 |
| --- | --- |
| `unverified` | 두 production provider가 준비되지 않았고 Identity Platform·Firestore·Storage가 모두 `OFF` |
| `preparing` | provider 하나 이상을 준비 중이지만 세 Firebase 서비스는 모두 `OFF` |
| `observe` | 두 provider, production callable이 준비됐고 callable과 세 Firebase 서비스가 모두 관찰 상태 |
| `staged` | callable 또는 세 Firebase 서비스 중 일부만 강제된 상태 |
| `enforced` | callable과 세 Firebase 서비스가 모두 강제된 상태 |

상태값은 원격 구성과 저장소 기대값의 일치 여부를 나타낸다. `observe` 이후 enforcement로 이동하려면 Android·Web 각각 최근 7일 `ALLOW`·`VALID` 요청과 주요 사용자 흐름을 별도로 통과해야 한다.

### 세부 감사 계약

- Android와 Web App ID는 공개 결과에 노출하지 않고 원격 앱 리소스와 내부 exact match에만 사용한다.
- Production Android·Web 앱에는 App Check debug token을 등록하지 않는다. 하나라도 있으면 최근 `ALLOW`·`VALID`를 release provider 증거로 인정하지 않는다.
- `observe` 이상에서는 `ANDROID_RELEASE_SHA256`과 Firebase의 release SHA-256이 정확히 일치해야 한다. 팀 소유 key와 Play App Signing 경계가 없는 임시 인증서는 허용하지 않는다.
- Play Integrity와 reCAPTCHA Enterprise API 활성 상태를 확인한다. Android는 배포 채널 확정 전 인식되지 않은 앱 버전을 허용하지 않으며 기기 무결성·라이선스는 Firebase 기본 정책을 요구한다. Web key는 `SCORE`, 승인된 production hostname 제한, 전체 도메인 허용·AMP·testing option 비활성을 요구한다.
- Identity Platform은 Email/Password와 이메일 열거 보호를 계속 확인하며 App Check Auth 모드는 `identitytoolkit.googleapis.com`에서 별도로 판정한다.
- Firebase 서비스 모드는 `identitytoolkit.googleapis.com`, `firestore.googleapis.com`, `firebasestorage.googleapis.com`을 각각 조회한다. 누락된 mode는 `OFF`다.
- callable Functions는 production 고정 함수 목록과 `ENABLE_APPCHECK_ENFORCEMENT`을 검사한다. 함수가 없으면 enforcement가 준비됐다고 보지 않는다.
- Cloud Monitoring은 최근 7일 `firebaseappcheck.googleapis.com/services/verification_count`에서 App ID별 `result=ALLOW`, `security=VALID`만 집계한다. `CONSUMED`나 누락·invalid 요청은 정상 증거에 포함하지 않는다.
- Spring Core API는 custom backend이므로 위 Firebase Monitoring 집계에 포함되지 않는다. `app_check_verdict`, 앱 구분과 경로를 포함한 Cloud Run 구조 로그, 실제 200·401 응답으로 observe/enforce를 별도 입증한다.

## 개발/운영 환경 경계

| 환경 | 기준 |
| --- | --- |
| 개발 | `bodeul-dev`에서 provider 등록, debug token, preview와 실기기 흐름, 단계별 enforcement를 검증한다. 현재는 모든 서비스를 `UNENFORCED`로 유지한다. |
| 운영 | `bodeul-prod-110`에 Android/Web 앱을 등록했고 canonical 관리자 웹 hostname용 reCAPTCHA Enterprise key, Web App Check와 Auth domain을 구성했다. Vercel Production은 client 활성화와 서버 `observe`를 배포했지만 인증된 `VALID` 요청이 없어 전체 단계는 `preparing`이다. release SHA-256과 Play Integrity는 별도로 준비하며 개발 debug token과 provider 설정을 복사하지 않는다. |

관리자 웹의 Next.js 전환과 reCAPTCHA Enterprise 연결은 완료했다. Vite build도 같은 Enterprise provider를 사용하는 빌드·런타임 rollback 자산으로만 유지하고, 현재 Production `observe`에서 인증된 `VALID` 요청과 주요 관리자 흐름을 확인한 뒤 `enforce` 전환을 판단한다.

## 강제 전환 순서

| 단계 | 목표 | 완료 조건 | 실행 |
| --- | --- | --- | --- |
| 0. 등록 현황 확인 | 모든 클라이언트를 Firebase App Check 앱으로 등록 | exact release SHA-256, Play Integrity 연결, 제한된 관리자 웹 Enterprise key가 정리됨 | 읽기 전용 production 감사에서 `preparing` 상태와 세부 provider 조건 확인 |
| 1. 개발 환경 안정화 | 디버그/로컬 검증이 enforcement 전에도 막히지 않게 준비 | Android 완료. 관리자 웹 debug token 등록과 저장소 비노출 확인 필요 | logcat/브라우저 콘솔로 debug token 확인, Console allowlist 등록 |
| 2. 배포 환경 토큰 검증 | 운영 후보 빌드와 Vercel URL에서 실제 provider 토큰 발급 확인 | Android·Web 각각 최근 7일 `ALLOW`·`VALID`, 로그인/예약/심사 흐름 통과 | 세 서비스를 `UNENFORCED`로 관찰하고 실기기·Vercel Production 흐름 검증 |
| 3. Functions callable enforcement | 배포된 production callable부터 좁게 차단 적용 | 대상 함수가 실제 배포돼 있고 소셜 로그인/중복 확인 흐름이 통과 | `ENABLE_APPCHECK_ENFORCEMENT=true` 설정 후 Functions 재배포. 함수 0개이면 이 단계 완료로 보지 않음 |
| 4. custom backend 검증 | Spring Core API와 Next.js 관리자 서버 보호 | Spring의 Android debug `valid` 완료. Next.js Production `observe` 배포 완료. release Play Integrity와 인증된 Web `VALID` 요청 확인 필요 | `X-Firebase-AppCheck` 검증을 observe 후 enforce로 전환 |
| 5. Storage enforcement | 파일 업로드/미리보기 보호 | 매니저 서류, 채팅 첨부 업로드/다운로드가 Android와 관리자 웹에서 통과 | Firebase Console에서 Storage enforcement 전환 |
| 6. Firestore enforcement | DB 직접 접근 보호 | Android 전체 주요 흐름과 관리자 웹 직접 접근 범위가 통과 | Firebase Console에서 Firestore enforcement 전환 |
| 7. Authentication enforcement | 로그인과 token 발급 경로 보호 | Android와 관리자 웹의 로그인/갱신/로그아웃 흐름이 모두 통과 | Firebase Console에서 Authentication enforcement 전환 |
| 8. 운영 모니터링 | 정상 사용자 차단 여부 감시 | 401/403, App Check token 오류, 고객 문의가 정상 범위 | 1주간 일일 확인 후 주간 점검으로 전환 |

## 단계별 검증 체크리스트

### Android

- Firebase Console에 Android 앱이 App Check 대상으로 등록돼 있다.
- release signing certificate의 SHA-256이 등록돼 있다.
- Google Play Console에서 Play Integrity API가 Firebase 프로젝트와 연결돼 있다.
- Google Play 전용 배포가 확정되기 전에는 기기 무결성·라이선스 요구를 Firebase 기본값으로 유지한다. 전용 배포가 확정되면 `LICENSED` 요구를 별도 변경으로 적용하고 실제 배포 경로를 다시 검증한다.
- debug 빌드 실행 시 출력된 debug token이 Console allowlist에 등록돼 있다.
- debug token 값은 Git, PR, 공개 이슈, 로그 첨부에 남기지 않는다.
- 실기기에서 로그인, 예약 생성, 보호자/환자 연결, 동행 세션, 채팅 첨부, 리포트 조회가 통과한다.

### 관리자 웹

- `NEXT_PUBLIC_FIREBASE_APPCHECK_SITE_KEY`가 preview/live 배포 환경에 설정돼 있다.
- `NEXT_PUBLIC_FIREBASE_APPCHECK_DEBUG_TOKEN`은 로컬 또는 CI 검증에만 사용하고 공개 저장소나 production 환경에 넣지 않는다.
- reCAPTCHA Enterprise key는 `SCORE` 유형이고 승인된 production hostname만 허용한다. 전체 도메인 허용, AMP, testing option은 사용하지 않는다.
- Firebase Console에서 관리자 웹 도메인이 App Check 앱 설정과 맞는다.
- Next.js와 Vite rollback build는 공용 `ReCaptchaEnterpriseProvider`를 사용하고, Next.js 서버는 허용 Web App ID를 검증한다. Vite build는 production provider의 대안이 아니다.
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

- 최근 7일 App Check 메트릭에서 Android·Web 각각 `ALLOW`·`VALID` 요청이 관측된다.
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

- 확인한 Firebase 프로젝트와 Vercel URL
- Android debug/release 빌드 구분
- 관리자 웹 preview/live 구분
- 수행한 사용자 흐름
- Firebase Console App Check 메트릭 판단
- Android·Web별 최근 7일 `ALLOW`·`VALID` 요청 수. App ID 원문은 기록하지 않음
- Spring Core API의 구조 로그와 실제 HTTP 결과. Firebase Monitoring 결과와 합산하지 않음
- enforcement를 켠 서비스와 시각
- 실패/롤백 여부
- 남은 debug token과 폐기 대상 token 목록

## 현재 결론

현재 production에서는 App Check를 강제하지 않는다. Web은 canonical production hostname으로 제한한 reCAPTCHA Enterprise key, provider 설정, Next.js header 전달과 서버 `observe` 배포를 완료했다. 다만 인증된 관리자 세션의 `VALID` 요청이 없고 Android exact release SHA-256과 Play 연결도 남아 있다. Identity Platform·Firestore·Storage는 모두 기본 `OFF`이고 production callable Functions와 최근 7일 Android·Web `ALLOW`·`VALID` 요청도 0건이므로 전체 단계는 `preparing`이다. 과거 개발 Android debug와 Spring Core API preview `valid` 기록은 개발 경로의 근거일 뿐 production 전환 증거로 재사용하지 않는다. 실제 요청 증거를 확보한 다음 Functions, custom backend, Storage, Firestore, Authentication 순서로 제한적으로 강제하고 각 단계를 별도로 rollback 검증한다.

## 참고 공식 문서

- Firebase App Check 개요: <https://firebase.google.com/docs/app-check>
- App Check enforcement 활성화: <https://firebase.google.com/docs/app-check/enable-enforcement>
- App Check 서비스 설정 REST API: <https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.services>
- Cloud Monitoring App Check 지표: <https://cloud.google.com/monitoring/api/metrics_gcp_d_h>
- Cloud Functions App Check enforcement: <https://firebase.google.com/docs/app-check/cloud-functions>
- Android Play Integrity provider: <https://firebase.google.com/docs/app-check/android/play-integrity-provider>
- Android debug provider: <https://firebase.google.com/docs/app-check/android/debug-provider>
- Web reCAPTCHA v3 provider: <https://firebase.google.com/docs/app-check/web/recaptcha-provider>
- Web reCAPTCHA Enterprise provider: <https://firebase.google.com/docs/app-check/web/recaptcha-enterprise-provider>
- reCAPTCHA Enterprise key REST API: <https://cloud.google.com/recaptcha/docs/reference/rest/v1/projects.keys>
- Android custom backend 보호: <https://firebase.google.com/docs/app-check/android/custom-resource>
- Web custom backend 보호: <https://firebase.google.com/docs/app-check/web/custom-resource>
- Custom backend token 검증: <https://firebase.google.com/docs/app-check/custom-resource-backend>
