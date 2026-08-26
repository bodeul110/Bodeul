# Production 인프라 읽기 전용 점검

기준일: 2026-08-26

## 목적

Production 배포나 데이터 조회 없이 Google Cloud/Firebase 기반의 누락과 설정 드리프트를 반복해서 확인한다. 배포, 백업, 보존 작업의 서비스 계정을 재사용하지 않고 감사 전용 WIF와 서비스 계정을 사용한다.

## 실행 경계

- GitHub Actions `workflow_dispatch`에서만 실행한다.
- 저장소 `bodeul110/Bodeul`, `master`, 현재 commit SHA, `bodeul-prod-110`을 모두 확인한다.
- GitHub Environment `production-infrastructure-audit`의 승인을 통과해야 한다.
- WIF provider는 저장소 이름과 불변 ID, 소유자 ID, `master`, Environment, workflow 경로와 이벤트를 모두 제한한다.
- 서비스 계정 JSON key를 만들지 않는다.
- 감사 계정에는 구성 metadata 조회 권한만 부여한다. Secret payload, Firestore 문서, Auth 사용자, Storage 객체는 읽을 수 없다.

## 점검 범위

| 범위 | 확인 내용 |
| --- | --- |
| Project/API | project ID·number·상태와 필수 API 활성화 |
| Artifact Registry | Tokyo Docker repository 존재와 형식 |
| WIF/IAM | 전용 provider 조건, 서비스 계정 상태, 사용자 관리 key 0개, project-local IAM binding |
| Secret Manager | 고정 secret 리소스와 version metadata 상태. payload는 조회하지 않음 |
| Cloud Run | 첫 배포 전 부재 또는 존재 시 단일 컨테이너·승인 이미지·준비된 최신 revision·runtime 계정·동적 outbound 기준 |
| Firebase/Auth | Firebase project와 Email/Password·email privacy 설정. 사용자 목록은 조회하지 않음 |
| Firestore | `(default)` database의 Tokyo·Native mode·삭제 방지 metadata |
| Storage | Firebase Storage와 DB backup bucket의 위치·uniform access·public access prevention·보존 metadata |

Cloud Run 첫 revision, Kakao production secret version, release App Check provider/enforcement, Firestore PITR와 Firebase Storage UBLA는 이미 구축됐다고 보는 baseline이 아니라 출시 차단 항목으로 따로 표시한다. Supabase와 PostgreSQL은 [Production DB migration 사전 점검](production-database-migration-readiness.md)에서 다룬다.

IAM 검사는 production 프로젝트에 직접 설정된 binding을 대조한다. 조직·폴더에서 상속된 Allow와 Deny는 이 서비스 계정의 조회 범위가 아니므로 effective IAM 전체를 보장하지 않는다. 2026-08-26 구성 시 조직 Deny 정책 0건과 WIF provider 생성 권한의 `DENY_ACCESS_STATE_NOT_DENIED`를 별도 관리자 점검으로 확인했고, 이때 사용한 임시 `denyReviewer`, WIF 관리자와 서비스 계정 관리자 역할은 확인 직후 모두 회수했다.

## 상태 판정

| 상태 | 의미 |
| --- | --- |
| `PASS` | 인증된 전체 응답을 정상 파싱했고 기대값과 일치 |
| `DRIFT` | 권한 있는 조회에서 리소스 또는 설정 불일치를 확인 |
| `EXPECTED_BLOCKER` | 아직 완료하지 않은 출시 게이트를 확인 |
| `EXPECTED_ABSENT` | 현재 단계에서 부재가 명시된 리소스 |
| `UNAVAILABLE` | 401·403·429, 권한 부족 또는 API 가용성 문제로 판정할 수 없음 |
| `ERROR` | 네트워크·5xx·응답 파싱 등 점검 자체의 오류 |

필수 baseline 항목에 `DRIFT`, `UNAVAILABLE`, `ERROR`가 하나라도 있으면 workflow를 실패시킨다. 403을 리소스 부재로 간주하지 않으며, 404도 선행 project/API 확인이 끝난 고정 리소스에서만 부재로 판단한다.

## 공개 로그 기준

Actions Summary에는 고정 check ID, 상태, 안전한 enum·boolean과 commit SHA만 남긴다. 다음 항목은 출력하거나 artifact로 업로드하지 않는다.

- OAuth/OIDC token과 credential 파일
- Secret payload·version resource name, JDBC URL과 DB 접속 정보
- IAM policy 원문과 개인·그룹·서비스 계정 이메일
- Firebase API key·App ID·Auth domain 원문
- bucket/object 경로와 목록, Firestore 문서
- Cloud Run 전체 설정, 환경변수, secret 참조와 서비스 URL
- `gcloud` 또는 REST의 원시 stdout·stderr

## 최초 구성

Google Cloud 관리자 권한이 있는 계정으로 재인증한 뒤 다음 스크립트를 실행한다.

```powershell
.\core-api\deploy\cloud-run\setup-production-infrastructure-auditor.ps1 `
  -ProjectId bodeul-prod-110 `
  -ConfirmProjectId bodeul-prod-110 `
  -ConfirmProjectNumber 649312328770 `
  -ConfirmEnvironment production-infrastructure-audit `
  -OperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmOperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmApply APPLY-PRODUCTION-INFRA-AUDITOR
```

스크립트는 현재 활성 계정을 바꾸지 않고 지정한 계정으로만 호출한다. 전용 custom role, `bodeul-infra-auditor` 서비스 계정, `github-actions/bodeul-infra-audit-production` provider와 정확한 WIF impersonation binding을 멱등적으로 구성한다.

GitHub Environment에는 공개 식별자만 변수로 등록한다. 비밀값과 서비스 계정 key는 등록하지 않는다.

현재 진행 단계는 [`tools/gcp/production-infrastructure-state.json`](../../tools/gcp/production-infrastructure-state.json)에 고정한다. 상태가 실제로 바뀌면 리소스 변경과 같은 PR에서 이 파일을 함께 갱신한다. 실제 상태와 기대 상태가 앞서거나 뒤처지면 감사는 드리프트로 실패한다. GitHub Environment 변수로 단계 값을 재정의하지 않는다.

| JSON 필드 / 실행 환경변수 | 현재 값 | 전환 값 |
| --- | --- | --- |
| `cloudRun` / `CLOUD_RUN_EXPECTED_STATE` | `absent` | 첫 승인 배포 후 `present` |
| `kakaoSecret` / `KAKAO_SECRET_EXPECTED_STATE` | `metadata-only` | 운영 version 등록 후 `enabled` |
| `firestorePitr` / `FIRESTORE_PITR_EXPECTED_STATE` | `deferred` | PITR 적용 후 `enabled` |
| `firebaseStorageUbla` / `FIREBASE_STORAGE_UBLA_EXPECTED_STATE` | `deferred` | 개발 버킷 실검증 후 `enabled` |
| `appCheck` / `APP_CHECK_EXPECTED_STATE` | `unverified` | metadata 점검 구현 후 `observe`, release 검증 후 `enforced` |

2026-08-26에 필수 API 15개, 감사 custom role, keyless 서비스 계정, exact-subject WIF provider와 impersonation을 구성했다. Firebase Storage에는 Public Access Prevention을 bucket 수준으로 강제했다. UBLA는 유효 조직 정책 아래 활성화하면 바로 되돌릴 수 없으므로, 개발 버킷에서 매니저 서류·채팅 첨부·Core API·보존 정책 경로를 실검증할 때까지 `deferred`로 둔다.

같은 날 deploy와 DB backup provider도 불변 저장소·소유자 ID, 정확한 workflow 경로와 `workflow_dispatch`로 제한하고, 서비스 계정 impersonation을 Environment 전체 principalSet에서 exact subject 하나로 축소했다. 이 변경은 각 기존 workflow 파일과 Environment 이름을 유지하므로 정상 수동 실행 subject는 바뀌지 않는다.

운영 provider 강화는 Google Cloud 관리자 권한을 임시로 부여한 뒤 다음 스크립트로 적용한다. 스크립트는 기존 provider와 서비스 계정에 예상 밖 설정이 있으면 변경하지 않고 중단하며, 적용 후 정확한 조건과 단일 subject binding을 다시 검증한다.

```powershell
.\core-api\deploy\cloud-run\harden-production-operational-wif.ps1 `
  -ProjectId bodeul-prod-110 `
  -ConfirmProjectId bodeul-prod-110 `
  -ConfirmProjectNumber 649312328770 `
  -ConfirmDeployEnvironment core-api-production `
  -ConfirmBackupEnvironment core-api-migration-production `
  -OperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmOperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmApply APPLY-PRODUCTION-OPERATIONAL-WIF-HARDENING
```

## 실행

1. `master` 최신 commit SHA를 확인한다.
2. Actions의 `Production Infrastructure Audit`을 연다.
3. project ID와 commit SHA를 입력하고 실행한다.
4. `production-infrastructure-audit` 승인을 완료한다.
5. Summary의 baseline 상태와 출시 차단 항목을 확인한다.

2026-08-26 운영 provider 강화 후 관리자 단기 토큰 점검과 [`master` GitHub WIF 감사 run 32969865527](https://github.com/bodeul110/Bodeul/actions/runs/32969865527)에서 baseline 전체가 통과했고 출시 차단 항목 5개가 분리됐다. 이후 실행 결과도 원시 응답이 아니라 정제된 상태표와 run 링크만 `docs/reports/` 및 Issue #134에 남긴다.
