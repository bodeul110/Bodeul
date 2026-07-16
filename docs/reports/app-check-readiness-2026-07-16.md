# App Check 준비 상태 점검

기준일: 2026-07-17
관련 이슈: #32, #190

## 목적

`bodeul-dev`에서 App Check enforcement를 켜도 되는지 코드 준비 상태가 아니라 실제 provider, debug token, 서비스 설정, 요청 메트릭을 기준으로 판단한다.

## 확인 방법

- Firebase Management API: Android/Web 앱과 SHA 인증서 개수
- Firebase App Check API: provider, debug token 개수, 서비스 enforcement
- Cloud Functions API: `ENABLE_APPCHECK_ENFORCEMENT` 배포 상태
- Cloud Monitoring: 최근 30일 App Check verification count
- GitHub Environment: App Check 관련 secret 이름 존재 여부

API key, provider site key, debug token, 인증서 fingerprint, OAuth access token은 결과에 기록하지 않았다.

## 결과

| 항목 | 결과 |
| --- | --- |
| Firebase 프로젝트 | `bodeul-dev`, 별도 production 프로젝트 없음 |
| Android | 앱 1개, SHA-256 1개는 local debug keystore와 일치, 별도 release 후보 0개, Play Integrity 설정 리소스 있음, debug token 1개 |
| 관리자 웹 | 앱 1개, reCAPTCHA v3/Enterprise provider 미등록, debug token 0개 |
| Firebase 서비스 | Firestore, Storage, Authentication 모두 `UNENFORCED` |
| Cloud Functions | 배포 10개, enforcement `true` 0개 |
| GitHub preview 환경 | `VITE_FIREBASE_APPCHECK_SITE_KEY`, `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` 미설정 |

최근 30일 검증 메트릭:

| 분류 | 요청 수 |
| --- | ---: |
| `VALID` | 1 |
| `INVALID` | 4,163 |
| `MISSING_OUTDATED_CLIENT` | 1,353 |
| `MISSING_UNKNOWN_ORIGIN` | 64 |
| 합계 | 5,581 |

모든 요청은 현재 enforcement가 꺼져 있어 `ALLOW` 처리됐다. 이 중 Android 앱 ID의 Firestore 요청 1건이 `VALID`로 확인됐다. Firebase 공식 기준으로 enforcement를 켜면 unverified 요청은 거부되므로 전체 등록 앱과 주요 흐름이 검증되기 전에는 강제 적용하지 않는다.

## 2026-07-17 Android debug 검증

- API 34 x86_64 에뮬레이터에서 debug APK를 실행했다. APK의 Kakao Map 네이티브 라이브러리가 ARM ABI에만 포함돼 시작 시 종료되는 문제는 [PR #195](https://github.com/bodeul110/Bodeul/pull/195)에서 debuggable 빌드에만 적용되는 fallback으로 보완했다.
- 앱 프로세스 생존, AndroidRuntime fatal 예외 없음, 지원하지 않는 ABI의 지도 SDK 건너뛰기 경고를 확인했다. release 빌드는 같은 네이티브 오류를 다시 던진다.
- 앱이 발급한 debug token을 `codex-emulator-api34-20260717`이라는 표시 이름으로 App Check allowlist에 등록했다. token 원문은 출력하거나 저장소에 기록하지 않았고 등록 직후 로컬 임시 파일을 삭제했다.
- 등록된 debug token을 App Check token으로 교환했으며 응답 TTL은 3,600초였다.
- 유효한 App Check token으로 존재하지 않는 Firestore 문서를 읽는 비파괴 probe를 실행했다. Firestore Rules가 요청을 403으로 거부했지만 App Check 메트릭은 Android 앱 ID, `firestore.googleapis.com`, `ALLOW`, `VALID` 1건으로 집계됐다. 데이터 쓰기는 수행하지 않았다.
- Firebase에 등록된 SHA-256 1개를 원문 출력 없이 로컬 debug keystore와 비교한 결과 일치했다. 별도 release 후보 지문은 없고 `app/build.gradle.kts`에도 release signing 설정이 없다.

## 판단

상태는 `HOLD`다.

- 관리자 웹 provider와 debug token이 없다.
- Android debug provider의 `VALID` 요청은 확인했지만 로그인, 예약, 세션, 채팅 첨부, Core API 장소 검색 흐름은 아직 실행하지 않았다.
- 팀 소유 release keystore와 Gradle signing 설정이 없어 release Play Integrity 후보를 아직 만들 수 없다.
- Android release Play Integrity와 관리자 웹 preview 흐름을 아직 실제 provider token으로 검증하지 않았다.
- Spring Core API는 observe로 배포됐지만 Firebase Auth와 PostgreSQL role이 연결된 요청의 `app_check_verdict=valid`는 아직 없다.
- 향후 Next.js 관리자 서버에는 App Check custom backend 검증이 아직 없다.

기존 Vite 관리자 웹은 Next.js로 교체 중이므로 reCAPTCHA v3를 새로 운영 설정하지 않는다. 새 관리자 웹은 Firebase가 신규 통합에 권장하는 reCAPTCHA Enterprise를 사용한다.

## 구현한 점검 도구

```powershell
$gcloud = "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$env:GOOGLE_OAUTH_ACCESS_TOKEN = (& $gcloud auth print-access-token).Trim()
try {
  npm --prefix tools/firebase run check:app-check -- `
    --project bodeul-dev `
    --json `
    --output reports/app-check-readiness.json
} finally {
  Remove-Item Env:GOOGLE_OAUTH_ACCESS_TOKEN -ErrorAction SilentlyContinue
}
```

출력 JSON은 `tools/firebase/reports/`에 두며 Git 추적 대상에서 제외한다. 명령은 읽기 전용이고 `HOLD`를 오류로 취급하지 않는다.

## 다음 전환 조건

1. 완료: Android debug token을 등록하고 debug 빌드의 `VALID` 요청을 확인했다.
2. 팀 소유 release keystore, alias, 암호 보관 주체를 확정하고 Gradle signing 설정과 Firebase SHA-256 등록을 완료한다.
3. 로그인, 예약, 세션, 채팅 첨부, Core API 장소 검색을 등록된 테스트 사용자로 실행한다.
4. release 서명과 Play Integrity 연결을 ARM 실기기에서 확인한다.
5. Next.js 관리자 웹에 reCAPTCHA Enterprise를 연결하고 preview의 `VALID` 요청을 확인한다.
6. callable Functions를 제한적으로 enforcement한 뒤 정상/실패 흐름과 롤백을 확인한다.
7. Android/Core API와 Web/Next.js 사이에서 `X-Firebase-AppCheck`를 observe 후 enforce로 전환한다.
8. Storage, Firestore, Authentication은 주요 흐름과 서비스별 메트릭을 다시 확인한 뒤 순서대로 전환한다.

## 공식 근거

- [App Check 요청 메트릭 확인](https://firebase.google.com/docs/app-check/monitor-metrics)
- [App Check enforcement 적용](https://firebase.google.com/docs/app-check/enable-enforcement)
- [Android Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Web reCAPTCHA Enterprise provider](https://firebase.google.com/docs/app-check/web/recaptcha-enterprise-provider)
- [Android custom backend 보호](https://firebase.google.com/docs/app-check/android/custom-resource)
- [Custom backend token 검증](https://firebase.google.com/docs/app-check/custom-resource-backend)
