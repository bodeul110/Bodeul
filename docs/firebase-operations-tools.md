# Firebase 운영 도구

기준일: 2026-04-24

`tools/firebase`는 앱 런타임 코드와 분리된 Firebase 운영용 로컬 스크립트를 모아두는 디렉터리다.

## 목적

- 개발용 기준선 초기화
- 컬렉션 상태 점검
- Firestore 백업
- Firestore 복원

## 위치

- 도구 루트: [tools/firebase](/D:/BoDeul/tools/firebase)
- 공용 helper: [tools/firebase/lib](/D:/BoDeul/tools/firebase/lib)

## 스크립트

### 상태 점검

```powershell
cd D:\BoDeul\tools\firebase
npm run check:state
```

- 기준선 Auth 계정 존재 여부
- `users` 문서 존재 여부
- 관리 대상 컬렉션 문서 수

### 역할별 화면 진입 점검

```powershell
cd D:\BoDeul\tools\firebase
npm run check:readiness
```

- 환자/보호자/매니저/관리자 기준선 계정이 실제 화면 진입에 필요한 컬렉션을 갖췄는지 점검한다.
- 샘플 시나리오(`예약 대기`, `진행 중 동행`, `종료 후속 처리`)가 기대한 연결 상태인지 함께 확인한다.
- 점검 기준은 현재 Firebase 저장소 코드가 읽는 컬렉션 조합에 맞춘다.

### 기준선 초기화

```powershell
cd D:\BoDeul\tools\firebase
npm run reset:baseline:dry-run
npm run reset:baseline:apply
```

- `Firebase Authentication`은 유지
- Firestore 관리 컬렉션을 비움
- 기준선 `users`, `hospitalGuides` 재생성

### 백업

```powershell
cd D:\BoDeul\tools\firebase
npm run backup:state
```

- 기본 저장 위치: `tools/firebase/backups/firestore-backup-YYYYMMDD-HHMMSS.json`
- 백업 파일은 Git 추적 대상에서 제외

### 백업 검증

```powershell
cd D:\BoDeul\tools\firebase
npm run validate:backup -- --file backups/firestore-backup-20260424-020000.json
```

- 백업 파일의 `schemaVersion`, `collections`, 문서 `path`/`id`/`fields` 구조를 검사한다.
- 관리 대상 컬렉션 누락, 잘못된 문서 경로, 중복 path가 있으면 오류 또는 경고로 알려준다.

### 복원

```powershell
cd D:\BoDeul\tools\firebase
npm run restore:state:dry-run -- --file backups/firestore-backup-20260424-020000.json
npm run restore:state:apply -- --file backups/firestore-backup-20260424-020000.json
```

- `--apply`가 없으면 dry-run
- Firestore 문서만 복원
- Auth 계정은 별도 유지

### 현재 상태 diff

```powershell
cd D:\BoDeul\tools\firebase
npm run diff:state -- --file backups/firestore-backup-20260424-020000.json
```

- 백업 파일과 현재 Firestore 상태를 비교해 컬렉션별 추가/삭제/변경 문서를 요약한다.
- 샘플 데이터 주입 전후 차이, 기준선 복원 이후 누락 여부를 빠르게 확인할 때 쓴다.

### 운영 리포트 생성

```powershell
cd D:\BoDeul\tools\firebase
npm run report:ops -- --file backups/firestore-backup-20260424-015754.json
```

- 현재 Firebase 상태와 역할별 화면 진입 점검 결과를 HTML 리포트로 저장한다.
- `--file`을 주면 기준 백업 대비 추가/삭제/변경 문서 요약도 함께 포함한다.
- 기본 저장 위치는 `tools/firebase/reports/firestore-operations-report-YYYYMMDD-HHMMSS.html`이다.

### 운영 워크플로 실행

```powershell
cd D:\BoDeul\tools\firebase
npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json
```

- 현재 상태 점검, 역할별 화면 진입 점검, 백업 검증, diff, HTML 리포트 생성, JSON 요약 저장을 한 번에 수행한다.
- 기본 산출물은 `tools/firebase/reports/` 아래에 HTML 리포트와 JSON 요약으로 함께 저장된다.
- `--strict`를 붙이면 역할 준비도 미달, 샘플 시나리오 실패, 백업 검증 오류가 있을 때 종료 코드를 `1`로 반환한다.
- `--json`을 붙이면 콘솔에도 최종 요약 JSON을 그대로 출력한다.

### 로컬 프리플라이트

```powershell
cd D:\BoDeul\tools\firebase
npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json
```

- Firebase 운영 워크플로, `assembleDebug`, `testDebugUnitTest`를 한 번에 실행한다.
- 중간 단계가 실패해도 끝까지 실행한 뒤 최종 상태를 계산해 Markdown/JSON 요약 파일을 `tools/firebase/reports/` 아래에 남긴다.
- `--skip-workflow`, `--skip-build`, `--skip-tests`로 필요한 단계만 뺄 수 있다.
- 기본적으로 최종 상태가 실패면 종료 코드도 `1`로 반환한다.

### 샘플 서비스 데이터 주입

```powershell
cd D:\BoDeul\tools\firebase
npm run seed:sample:dry-run
npm run seed:sample:apply
```

- `users`, `hospitalGuides` 기준선은 유지하고 예약/세션/후속 처리 샘플만 추가한다.
- 고정 ID 문서를 upsert하므로 같은 스크립트를 다시 실행해도 중복 샘플이 늘어나지 않는다.
- 기본 시나리오는 `예약 대기`, `진행 중 동행`, `종료 후속 처리` 3개다.
- 함께 생성되는 컬렉션은 `appointmentRequests`, `companionSessions`, `sessionReports`,
  `appointmentFollowUps`, `supportInquiries`, `adminSettlementRecords`,
  `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`,
  `adminActionDeliveries`, `adminActionDeliveryJobs`, `appointmentReminderJobs`다.

## 관리 원칙

- 배포 코드인 `functions/`에는 운영 스크립트를 두지 않는다.
- 초기화/백업/복원/점검 스크립트는 모두 `tools/firebase` 아래에 둔다.
- 이후 컬렉션 마이그레이션, 백업 검증도 같은 위치에 추가한다.

### 앱 화면 증적 캡처

```powershell
cd D:\BoDeul\tools\firebase
npm run capture:app -- --screen-id login --title "로그인 화면"
```

- 연결된 에뮬레이터 또는 USB 디바이스의 현재 화면을 캡처해 `tools/firebase/reports/screenshots/` 아래에 저장한다.
- 캡처 결과는 기본적으로 `tools/firebase/reports/app-navigation-evidence-latest.json`에 누적한다.
- 원하는 화면까지 직접 이동한 뒤 실행하는 방식을 기본으로 하고, `--launch-main`을 주면 런처 진입 화면을 먼저 띄운다.
- `--preset`을 주면 debug 자동 진입 액티비티가 기준선 계정 로그인과 화면 이동을 먼저 수행한 뒤 포커스를 확인하고 캡처한다.
- 프리셋 자동 진입은 앱을 강제 재시작한 뒤 수행하므로 이전 로그인 세션이나 남아 있던 화면 상태 영향을 줄인다.
- 현재 지원 프리셋은 `patient-home`, `guardian-home`, `patient-booking`, `guardian-booking-status`, `patient-booking-follow-up`, `guardian-report`, `manager-home`, `manager-history`, `manager-guide`, `manager-support`, `manager-profile`, `admin-dashboard`다.
- `--role`, `--status`, `--note`, `--activity`, `--serial`, `--manifest`, `--image` 옵션으로 증적 메타데이터를 함께 기록할 수 있다.
- `--request-id`, `--force-sign-in`, `--route-wait-ms`로 예약 상세/후속 처리 대상과 자동 진입 대기 시간을 조정할 수 있다.
- 생성된 증적 파일은 `npm run report:ops -- --app-evidence ...`, `npm run workflow:ops -- --app-evidence ...`, `npm run preflight:local -- --app-evidence ...`로 운영 리포트와 프리플라이트에 연결한다.

```powershell
cd D:\BoDeul\tools\firebase
npm run capture:app -- --preset manager-home
npm run capture:app -- --preset guardian-booking-status --request-id request-seed-progress
```

### CI 프리플라이트

```powershell
cd D:\BoDeul\tools\firebase
npm run preflight:ci -- --app-evidence templates/app-navigation-evidence.sample.json
```

- CI용 실행점은 [run-ci-preflight.js](/D:/BoDeul/tools/firebase/run-ci-preflight.js)이고, 내부에서 [run-local-preflight.js](/D:/BoDeul/tools/firebase/run-local-preflight.js)를 그대로 재사용한다.
- Firebase 입력이 준비되면 운영 워크플로까지 포함하고, 준비되지 않았으면 자동으로 `--skip-workflow`를 붙여 빌드/테스트만 수행한다.
- `--require-firebase`를 주면 `FIREBASE_TOKEN`, 프로젝트 식별 정보가 없을 때 실패로 종료한다.
- GitHub Actions 워크플로는 [.github/workflows/android-preflight.yml](/D:/BoDeul/.github/workflows/android-preflight.yml)에 추가했다.
- `workflow_dispatch`로 실제 실행하려면 이 워크플로 파일이 원격 기본 브랜치에도 올라가 있어야 한다. 로컬에만 있고 아직 push하지 않았다면 `gh workflow run`은 `workflow ... not found on the default branch`로 실패한다.
- GitHub Actions에서 전체 점검을 돌리려면 아래 시크릿/변수를 맞춘다.
  - `secrets.FIREBASE_TOKEN`
  - `secrets.GOOGLE_SERVICES_JSON`
  - `secrets.FIREBASERC_JSON`
  - `vars.FIREBASE_PROJECT_ID`
- `FIREBASE_TOKEN`은 Firebase 공식 문서 기준 `firebase login:ci`로 발급받는 refresh token을 기준으로 보고, [firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js)에서 access token으로 자동 교환해 사용한다. 로컬 `firebase login` 상태에서 [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)를 실행하면 같은 값을 GitHub 시크릿으로 올릴 수 있다.
- 시크릿이 없으면 워크플로는 기본적으로 Android 빌드/테스트만 수행하고, 생성된 `tools/firebase/reports/` 산출물은 아티팩트로 업로드한다.

### GitHub Actions 시크릿 반영

```powershell
cd D:\BoDeul
node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run
node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dispatch
```

- [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)는 origin 원격 또는 `--repo` 값 기준으로 저장소를 해석하고, 아래 항목을 GitHub Actions에 반영한다.
  - `secrets.FIREBASE_TOKEN`
  - `secrets.GOOGLE_SERVICES_JSON`
  - `secrets.FIREBASERC_JSON`
  - `vars.FIREBASE_PROJECT_ID`
- `--dispatch`를 붙이면 `android-preflight.yml`을 `workflow_dispatch`로 즉시 실행한다.
- `--backup-file`, `--app-evidence`, `--workflow`로 dispatch 입력값을 조정할 수 있다.
- 현재 로컬 원격은 `git@github.com:bodeul110/Bodeul.git`이지만, GitHub CLI 계정이 해당 저장소 API 접근 권한이 없는 상태면 시크릿 반영은 실패한다. 이 경우 `gh auth login` 또는 `gh auth switch`로 저장소 권한이 있는 계정으로 바꾼 뒤 다시 실행한다.
- `--app-evidence` 경로는 repo 루트 기준 경로와 `tools/firebase` 작업 디렉터리 기준 경로를 둘 다 허용한다. CI에서는 `tools/firebase/templates/app-navigation-evidence.sample.json`처럼 repo 루트 기준 경로를 그대로 써도 된다.
- 원격 전체 모드 검증은 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true --field app_evidence_path=tools/firebase/templates/app-navigation-evidence.sample.json`로 수행했고, 실행 결과는 [GitHub Actions run 24873140407](https://github.com/bodeul110/Bodeul/actions/runs/24873140407)에서 확인할 수 있다.
