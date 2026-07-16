# Issue 191 Spring Core API App Check 적용 기록

기준일: 2026-07-17

## 작업 목적

Firebase ID token이 유출되더라도 등록되지 않은 앱에서 Core API를 호출하는 위험을 줄이기 위해 Android와 Spring 사이에 App Check 검증 경계를 추가한다.

## 선택한 방식

- Android는 Core API 호출 전에 App Check token을 요청하고 발급된 경우 `X-Firebase-AppCheck` 헤더로 전송한다.
- Spring은 `off`, `observe`, `enforce` 세 모드를 제공하며 기본값은 `off`다.
- Cloud Run preview는 `observe`를 사용해 기존 사용자 요청을 차단하지 않고 `valid`, `missing`, `invalid`, `unavailable`을 구분한다.
- Firebase Auth와 PostgreSQL role 인가는 App Check와 독립적으로 유지한다.
- Java Admin SDK 9.10.0에는 App Check 검증 API가 없으므로 Spring Security의 Nimbus JWT decoder를 사용한다.

## 검증 조건

App Check token은 공식 JWKS를 사용해 다음 조건을 모두 확인한다.

1. 서명 알고리즘이 RS256이다.
2. JWT header의 `typ`가 `JWT`다.
3. issuer가 `https://firebaseappcheck.googleapis.com/533563500316`이다.
4. token이 만료되지 않았다.
5. audience에 `projects/533563500316`이 포함된다.
6. subject의 Firebase app ID가 비어 있지 않다.

## 검토한 대안

| 대안 | 판단 |
| --- | --- |
| Firebase Java Admin SDK에서 직접 검증 | 현재 사용 버전에 App Check API가 없어 적용할 수 없다. |
| 별도 Node 검증 proxy 추가 | Spring 앞에 서버를 하나 더 두어 목표 아키텍처와 운영 경계가 복잡해지므로 제외했다. |
| 수동 JWT 파서 구현 | 서명, JWKS cache, 시간과 claim 검증 오류 가능성이 커 검증된 Spring Security decoder를 사용한다. |
| 바로 enforce 적용 | 현재 최근 30일 `VALID`가 0건이고 실기기 provider 검증 전이므로 정상 요청 차단 위험이 커 제외했다. |

## 선택 이유

현재 MVP 규모에서는 기존 Spring Security에 관리형 JWT decoder를 추가하는 방식이 별도 서비스를 만들지 않으면서 필요한 검증 조건을 충족한다. observe와 enforce를 같은 코드 경로에서 전환하므로 실제 정상 token이 확인된 뒤 환경변수만 바꿔 차단을 시작할 수 있다.

## 보안과 운영 기준

- token 원문은 응답, 로그, 문서에 남기지 않는다.
- observe 로그에는 판정, 검증된 app ID, servlet path만 기록한다.
- App Check 검증은 Firebase 인증이 끝난 요청에 적용해 기존 무인증 오류 계약을 유지한다.
- `FIREBASE_PROJECT_NUMBER`는 공개 식별자이며 GitHub `core-api-preview` Environment variable로 관리한다.
- preview workflow는 `BODEUL_APP_CHECK_MODE=observe`를 고정한다.

## 로컬 검증

| 항목 | 결과 |
| --- | --- |
| Core API | `core-api/gradlew.bat check --console=plain`, 53개 테스트 실패 0 |
| JWT 조건 | issuer, audience, 만료, `typ`, app ID 테스트 추가 |
| 모드 | off/observe/enforce, 누락/중복/위조/unavailable/valid 테스트 추가 |
| 기존 인가 | Firebase ID token과 PostgreSQL role 통합 테스트 통과 |
| Workflow | `yq e '.' .github/workflows/core-api-preview-deploy.yml` 성공 |
| Android | `gradlew.bat assembleDebug`, `testDebugUnitTest` 성공, 43개 테스트 실패 0 |

## Cloud Run preview 배포 검증

| 항목 | 확인 결과 |
| --- | --- |
| 반영 PR | [#193 Core API App Check 관찰 경계 추가](https://github.com/bodeul110/Bodeul/pull/193) |
| 배포 실행 | [Core API Preview Deploy #29518038972](https://github.com/bodeul110/Bodeul/actions/runs/29518038972), 전체 단계 성공 |
| 배포 commit | `000afc350fa3654cb97c9d23a539e45322322e95` |
| 리전과 리비전 | `asia-northeast1`, `bodeul-core-api-preview-00007-8hk` |
| 트래픽 | 최신 리비전 100% |
| 실행 설정 | `FIREBASE_PROJECT_ID=bodeul-dev`, `FIREBASE_PROJECT_NUMBER=533563500316`, `BODEUL_APP_CHECK_MODE=observe` |
| smoke test | `/health` 200, 무인증 `/api/auth/me`와 `/api/places/search` 401 및 `missing_authorization` 확인 |

배포 후 Cloud Logging에서 `app_check_verdict`를 조회했지만 기록은 0건이었다. App Check filter는 Firebase ID token과 PostgreSQL role 확인이 끝난 인증 요청만 관찰하므로, 무인증 smoke test는 판정 로그를 만들지 않는다. 이번 검증을 위해 migration 계정을 사용하는 범용 SQL 실행 경로를 새로 만들지는 않았다. 실제 `valid` 관측은 등록된 테스트 사용자가 Android debug/Play Integrity token으로 요청하는 Issue #190에서 수행한다.

## 리스크와 남은 범위

- Android debug token allowlist와 Play Integrity 실기기 `valid`는 Issue #190 범위다.
- 정상 App Check token이 없는 현재 상태에서는 preview enforce를 실행하지 않는다.
- 누락·위조 요청의 분기와 로그 형식은 자동화 테스트로 확인했으며, Cloud Run의 실제 판정 로그는 인증된 테스트 사용자의 요청이 들어온 뒤 확인한다.
- 실제 `valid`, enforce 전환, 즉시 observe 롤백은 Issue #190 완료 후 Issue #192에서 검증한다.
- JWKS 조회 장애가 enforce 중 발생하면 503으로 처리하며 즉시 observe로 되돌린다.

Issue #191은 Android header 전달, Spring 검증 경계, observe 배포까지를 완료 범위로 본다. 실기기 provider와 정상 token 증적은 #190, 실제 차단과 롤백은 #192에서 추적한다.

## 공식 근거

- [Android custom backend token 전송](https://firebase.google.com/docs/app-check/android/custom-resource)
- [Custom backend token 검증](https://firebase.google.com/docs/app-check/custom-resource-backend)
- [App Check 요청 메트릭](https://firebase.google.com/docs/app-check/monitor-metrics)
