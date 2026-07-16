# App Check 준비 상태 점검

기준일: 2026-07-16
관련 이슈: #32

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
| Android | 앱 1개, SHA-256 1개, Play Integrity 설정 리소스 있음, debug token 0개 |
| 관리자 웹 | 앱 1개, reCAPTCHA v3/Enterprise provider 미등록, debug token 0개 |
| Firebase 서비스 | Firestore, Storage, Authentication 모두 `UNENFORCED` |
| Cloud Functions | 배포 10개, enforcement `true` 0개 |
| GitHub preview 환경 | `VITE_FIREBASE_APPCHECK_SITE_KEY`, `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` 미설정 |

최근 30일 검증 메트릭:

| 분류 | 요청 수 |
| --- | ---: |
| `VALID` | 0 |
| `INVALID` | 4,163 |
| `MISSING_OUTDATED_CLIENT` | 1,353 |
| `MISSING_UNKNOWN_ORIGIN` | 64 |
| 합계 | 5,580 |

모든 요청은 현재 enforcement가 꺼져 있어 `ALLOW` 처리됐다. Firebase 공식 기준으로 enforcement를 켜면 unverified 요청은 거부되므로 현재 상태에서 강제 적용하지 않는다.

## 판단

상태는 `HOLD`다.

- Android debug token allowlist가 비어 있다.
- 관리자 웹 provider와 debug token이 없다.
- 등록된 Android/Web 앱에서 `VALID` 요청이 관측되지 않았다.
- Android release Play Integrity와 관리자 웹 preview 흐름을 아직 실제 provider 토큰으로 검증하지 않았다.
- Spring Core API와 향후 Next.js 관리자 서버는 아직 `X-Firebase-AppCheck`를 검증하지 않는다.

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

1. Android debug token을 등록하고 debug 빌드의 `VALID` 요청을 확인한다.
2. release 서명과 Play Integrity 연결을 실기기에서 확인한다.
3. Next.js 관리자 웹에 reCAPTCHA Enterprise를 연결하고 preview의 `VALID` 요청을 확인한다.
4. callable Functions를 제한적으로 enforcement한 뒤 정상/실패 흐름과 롤백을 확인한다.
5. Android/Core API와 Web/Next.js 사이에서 `X-Firebase-AppCheck`를 observe 후 enforce로 전환한다.
6. Storage, Firestore, Authentication은 주요 흐름과 서비스별 메트릭을 다시 확인한 뒤 순서대로 전환한다.

## 공식 근거

- [App Check 요청 메트릭 확인](https://firebase.google.com/docs/app-check/monitor-metrics)
- [App Check enforcement 적용](https://firebase.google.com/docs/app-check/enable-enforcement)
- [Android Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Web reCAPTCHA Enterprise provider](https://firebase.google.com/docs/app-check/web/recaptcha-enterprise-provider)
- [Android custom backend 보호](https://firebase.google.com/docs/app-check/android/custom-resource)
- [Custom backend token 검증](https://firebase.google.com/docs/app-check/custom-resource-backend)
