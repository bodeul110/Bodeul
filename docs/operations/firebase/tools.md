# Firebase 운영 도구

기준일: 2026-07-16

`tools/firebase`는 앱 런타임 코드와 분리된 Firebase 운영용 로컬 스크립트를 모아두는 디렉터리다.

관리자 웹/앱 권한 검증 순서는 [관리자 권한 QA 체크리스트](../admin-access-qa-checklist.md)를 기준으로 맞춘다.

## 목적

- 개발용 기준선 초기화
- 컬렉션 상태 점검
- Firestore 백업
- Firestore 복원
- 역할별 화면 진입 준비도 확인
- 운영 리포트와 프리플라이트 자동화
- 매니저 서류 Storage 정합성 점검과 고아 파일 정리

## 먼저 볼 흐름

운영 도구를 처음 쓸 때는 아래 순서로 보는 것을 권장한다.

1. 기준선 상태 확인: `npm run check:state`
2. 역할 준비도 확인: `npm run check:readiness`
3. 매니저 서류 Storage 점검: `npm run check:manager-storage -- --strict`
4. 기준 백업 생성: `npm run backup:state`
5. 전체 운영 워크플로: `npm run workflow:ops -- --file backups/...json`

주의:

- `check:state`, `check:readiness`, `workflow:ops`, `preflight:local` 같은 Firebase 운영 점검 명령은 로컬 권한 설정이 필요하다.
- refresh token 기반이면 `local.properties`의 `firebaseOauthClientSecret` 또는 `FIREBASE_OAUTH_CLIENT_SECRET` 환경 변수가 없을 때 실행이 실패한다.
- 기획/내부 QA가 바로 볼 계정/시나리오는 [내부 테스트 가이드](../internal-test-guide.md)에 따로 정리했다.

## 위치

- 도구 루트: [tools/firebase](../../../tools/firebase)
- 공용 helper: [tools/firebase/lib](../../../tools/firebase/lib)

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

### 격리 Emulator 복원 리허설

```powershell
cd D:\BoDeul
$env:FIREBASE_PROJECT_ID='bodeul-restore-rehearsal'
npx --prefix tools/firebase firebase emulators:exec `
  --only firestore `
  --project bodeul-restore-rehearsal `
  --config firebase.restore-rehearsal.json `
  "npm --prefix tools/firebase run rehearse:restore:emulator -- --file backups/firestore-backup-YYYYMMDD-HHMMSS.json"
```

- `firebase.restore-rehearsal.json`은 Firestore Emulator를 `127.0.0.1:8180`에 띄운다.
- 리허설 명령은 project id가 `bodeul-restore-rehearsal`이고 Emulator host가 loopback일 때만 실행된다.
- 재백업, 구조 검증, 임시 상태 변조, dry-run, apply, diff, Firestore 전용 workflow strict를 순서대로 실행한다.
- 생성되는 round-trip 백업과 HTML/JSON 결과는 `tools/firebase/backups`, `tools/firebase/reports`의 gitignore 경로에 둔다.
- 실제 결과는 [2026-07-16 복원 리허설 보고서](../../reports/firestore-backup-restore-rehearsal-2026-07-16.md)를 본다.

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
- `--firestore-only`를 붙이면 Auth endpoint를 조회하지 않고 users 문서와 Firestore 관계만 검증하며, Auth 상태는 `미검증`으로 기록한다.
- `--no-app-evidence`를 붙이면 기존 앱 화면 증적을 자동으로 연결하지 않는다.
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

- CI용 실행점은 [run-ci-preflight.js](../../../tools/firebase/run-ci-preflight.js)이고, 내부에서 [run-local-preflight.js](../../../tools/firebase/run-local-preflight.js)를 그대로 재사용한다.
- Firebase 입력이 준비되면 운영 워크플로까지 포함하고, 준비되지 않았으면 자동으로 `--skip-workflow`를 붙여 빌드/테스트만 수행한다.
- `--require-firebase`를 주면 `FIREBASE_TOKEN`, 프로젝트 식별 정보가 없을 때 실패로 종료한다.
- GitHub Actions 워크플로는 [.github/workflows/android-preflight.yml](../../../.github/workflows/android-preflight.yml)에 추가했다.
- `workflow_dispatch`로 실제 실행하려면 이 워크플로 파일이 원격 기본 브랜치에도 올라가 있어야 한다. 로컬에만 있고 아직 push하지 않았다면 `gh workflow run`은 `workflow ... not found on the default branch`로 실패한다.
- GitHub Actions에서 전체 점검을 돌리려면 아래 시크릿/변수를 맞춘다.
  - `secrets.FIREBASE_TOKEN`
  - `secrets.FIREBASE_OAUTH_CLIENT_SECRET` (`FIREBASE_TOKEN`이 refresh token일 때만)
  - `secrets.GOOGLE_SERVICES_JSON`
  - `secrets.FIREBASERC_JSON`
  - `vars.FIREBASE_PROJECT_ID`
- `FIREBASE_TOKEN`은 Firebase 공식 문서 기준 `firebase login:ci`로 발급받는 refresh token 또는 access token을 받을 수 있다.
- refresh token을 쓰는 경우 [firebase-toolkit.js](../../../tools/firebase/lib/firebase-toolkit.js)가 access token으로 교환해야 하므로, `FIREBASE_OAUTH_CLIENT_SECRET`도 함께 필요하다.
- 이 OAuth client secret은 저장소에 하드코딩하지 않고, 로컬에서는 `local.properties`의 `firebaseOauthClientSecret`, CI에서는 `secrets.FIREBASE_OAUTH_CLIENT_SECRET`으로 분리한다.
- 시크릿이 없으면 워크플로는 기본적으로 Android 빌드/테스트만 수행하고, 생성된 `tools/firebase/reports/` 산출물은 아티팩트로 업로드한다.

### GitHub Actions 시크릿 반영

```powershell
cd D:\BoDeul
node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run
node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dispatch
```

- [configure-actions-firebase.js](../../../tools/github/configure-actions-firebase.js)는 origin 원격 또는 `--repo` 값 기준으로 저장소를 해석하고, 아래 항목을 GitHub Actions에 반영한다.
  - `secrets.FIREBASE_TOKEN`
  - `secrets.FIREBASE_OAUTH_CLIENT_SECRET` (`FIREBASE_TOKEN`이 refresh token일 때)
  - `secrets.GOOGLE_SERVICES_JSON`
  - `secrets.FIREBASERC_JSON`
  - `vars.FIREBASE_PROJECT_ID`
- 로컬에서는 `FIREBASE_OAUTH_CLIENT_SECRET` 환경 변수 또는 `local.properties`의 `firebaseOauthClientSecret` 값을 읽어 위 시크릿으로 올린다.
- `--dispatch`를 붙이면 `android-preflight.yml`을 `workflow_dispatch`로 즉시 실행한다.
- `--backup-file`, `--app-evidence`, `--workflow`로 dispatch 입력값을 조정할 수 있다.
- 현재 로컬 원격은 `git@github.com:bodeul110/Bodeul.git`이지만, GitHub CLI 계정이 해당 저장소 API 접근 권한이 없는 상태면 시크릿 반영은 실패한다. 이 경우 `gh auth login` 또는 `gh auth switch`로 저장소 권한이 있는 계정으로 바꾼 뒤 다시 실행한다.
- `--app-evidence` 경로는 repo 루트 기준 경로와 `tools/firebase` 작업 디렉터리 기준 경로를 둘 다 허용한다. CI에서는 `tools/firebase/templates/app-navigation-evidence.sample.json`처럼 repo 루트 기준 경로를 그대로 써도 된다.
- 원격 전체 모드 검증은 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true --field app_evidence_path=tools/firebase/templates/app-navigation-evidence.sample.json`로 수행했고, 실행 결과는 [GitHub Actions run 24873140407](https://github.com/bodeul110/Bodeul/actions/runs/24873140407)에서 확인할 수 있다.

### Rules emulator 테스트

```powershell
cd D:\BoDeul
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
npm --prefix tools/firebase run test:rules
```

- [rules-emulator-tests/run-rules-tests.js](../../../tools/firebase/rules-emulator-tests/run-rules-tests.js)는 Firebase emulator를 띄운 뒤 Firestore/Storage Rules 허용/거부 시나리오를 실행한다.
- 테스트 대상은 `users`, `appointmentRequests`, `companionSessions`, `sessionReports`, 관리자 운영 컬렉션, `manager-documents`, `companion-chat-attachments`다.
- Firebase CLI 15.22.3 emulator는 Java 21 이상이 필요하다. Android Studio JBR 21 또는 CI의 `setup-java@v5` Java 21을 사용한다.
- GitHub Actions에서는 [.github/workflows/firebase-rules.yml](../../../.github/workflows/firebase-rules.yml)이 같은 테스트를 실행한다.
## 2026-05-04 추가된 도구

### 매니저 서류 Storage 점검

```powershell
cd D:\BoDeul\tools\firebase
npm run check:manager-storage
npm run check:manager-storage -- --json
npm run check:manager-storage -- --strict
npm run cleanup:manager-storage:dry-run
npm run cleanup:manager-storage:apply
```

- [check-manager-document-storage.js](../../../tools/firebase/check-manager-document-storage.js)는 `users/{uid}.managerDocumentFiles`, `managerDocumentFilePaths`, 레거시 경로 필드와 `manager-documents/` 아래 실제 Storage 객체를 비교한다.
- 점검 결과는 기본적으로 `tools/firebase/reports/manager-document-storage-check-YYYYMMDD-HHMMSS.json`에 저장된다.
- `--strict`를 주면 누락 객체나 경로 불일치가 있을 때 비정상 종료해 CI나 수동 점검에서 바로 걸 수 있다.
- `cleanup:manager-storage:dry-run`은 고아 파일 삭제 후보만 계산하고 실제 삭제는 하지 않는다.
- `cleanup:manager-storage:apply`는 실제 삭제를 수행하지만, 누락 객체나 경로 불일치가 있으면 기본적으로 중단한다.
- 정말 예외적으로 계속 진행해야 할 때만 `--delete-orphans --apply --force`를 수동 실행한다.
- 대량 삭제 방지를 위해 기본 최대 삭제 수는 20건이며, 필요하면 `--max-delete`로 조정한다.

### 매니저 서류 샘플 업로드

```powershell
cd D:\BoDeul\tools\firebase
npm run seed:manager-docs:dry-run
npm run seed:manager-docs:apply
```

- [seed-manager-document-storage-sample.js](../../../tools/firebase/seed-manager-document-storage-sample.js)는 `manager@bodeul.app` 기준으로 신분증/자격증/범죄경력 조회서 샘플 PNG 3개를 업로드하고, 같은 경로를 `users/{uid}` 메타데이터에도 저장한다.
- 기본값은 dry-run이며, `--apply`를 줘야 실제 Storage 업로드와 Firestore 메타데이터 반영을 수행한다.
- 이 스크립트는 관리자 웹 미리보기나 Storage 경로 점검을 실데이터로 검증할 때만 사용한다.

### 패치 주의

- [firebase-toolkit.js](../../../tools/firebase/lib/firebase-toolkit.js)의 `patchDocumentFields()`는 `updateMask.fieldPaths`를 함께 붙여 부분 업데이트로 동작한다.
- 운영 도구에서 Firestore 문서를 수정할 때는 필요한 필드만 넘기는 방식을 전제로 하고, 전체 문서 덮어쓰기가 필요한 경우에는 별도 전용 스크립트로 다루는 편이 안전하다.
