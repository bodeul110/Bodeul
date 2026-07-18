# Production 인프라 기본값

기준일: 2026-07-18

이 문서는 BoDeul production 리소스의 실제 식별자, 리전, 배포 경계와 운영 기준을 고정한다. 2026-07-17에 Google Cloud/Firebase와 Supabase production 기반을 생성했으며, 도메인 구매와 실명 담당자 지정은 별도 운영 결정으로 남긴다.

## 결정 요약

- 개발과 production은 Google Cloud/Firebase 프로젝트와 Supabase 프로젝트를 각각 분리한다.
- 관리자 웹은 기존 Vercel 프로젝트 `bodeul-admin-web`의 Preview와 Production 환경을 구분해 사용한다. Vercel 프로젝트를 하나 더 만들지 않는다.
- 관리자 웹과 Core API, Supabase는 Tokyo 리전에 맞춘다.
- 관리자 웹은 Vercel Next.js 서버, 사용자 서비스는 Cloud Run Spring Core API가 담당한다.
- 두 서버는 같은 production PostgreSQL을 서로 다른 최소 권한 role로 사용하며 서로를 proxy로 호출하지 않는다.
- Firebase Auth, FCM, Storage와 Firebase 결합 Functions는 production Firebase 프로젝트에서 유지한다.
- 개발 Preview를 출시 전 검증 환경으로 사용하고, 현재 규모에서는 세 번째 staging 환경을 만들지 않는다.
- 목표 production 전환일은 2026-12-15 10:00 KST로 둔다.
- 월 반복 비용 승인 한도는 150,000 KRW, 정상 목표는 100,000~130,000 KRW로 둔다.

## 리소스 기준

| 범위 | 확정값 | 비고 |
| --- | --- | --- |
| Google Cloud/Firebase 표시 이름 | `BoDeul Production` | 하나의 Google Cloud 프로젝트에서 Firebase를 활성화한다. |
| Google Cloud project ID / number | `bodeul-prod-110` / `649312328770` | 결제 연결과 Firebase 활성화 완료 |
| Google Cloud/Cloud Run 리전 | `asia-northeast1` | Tokyo |
| Cloud Run 서비스 | `bodeul-core-api` | preview 접미사를 사용하지 않는다. |
| Artifact Registry/이미지 | `bodeul-core-api` | 개발 프로젝트와 이름은 같아도 프로젝트 경계로 분리된다. |
| 배포 서비스 계정 | `bodeul-core-deployer` | WIF 배포 전용 |
| 런타임 서비스 계정 | `bodeul-core-runtime` | Secret Manager 접근과 실행 전용 |
| DB 백업 서비스 계정 | `bodeul-db-backup` | 검증된 dump의 GCS 생성·조회 전용, 삭제 권한 없음 |
| WIF pool/provider | `github-actions` / `bodeul-core-api-production` | 저장소, `master`, GitHub Environment 조건을 모두 제한한다. |
| DB 백업 WIF provider | `github-actions` / `bodeul-db-backup-production` | `core-api-migration-production` Environment 전용 |
| 배포 Environment | `core-api-production` | 수동 production 배포와 승인 보호 |
| migration Environment | `core-api-migration-production` | 앱 배포와 DB 변경을 분리한다. |
| Supabase 표시 이름 / ref | `bodeul-prod` / `aoijbzgozbopsxzrasbb` | 개발 프로젝트와 별도 생성 |
| Supabase 리전 | `ap-northeast-1` | Tokyo |
| Vercel 프로젝트 | `bodeul-admin-web` | 기존 프로젝트를 유지한다. |
| Vercel production branch | `master` | 보호된 PR 병합만 허용한다. |
| Vercel Functions 리전 | `hnd1` | Tokyo |
| 관리자 도메인 | `admin.<기준-도메인>` | 기준 도메인 구매 후 연결한다. |
| Core API 도메인 | `api.<기준-도메인>` | 기준 도메인 구매 후 연결한다. |

## 환경 분리

| 환경 | Google Cloud/Firebase | Supabase | Vercel | 용도 |
| --- | --- | --- | --- | --- |
| 개발 | `bodeul-dev` | 현재 Tokyo 개발 프로젝트 | Preview | PR, 실연동, 실기기 검증 |
| production | `bodeul-prod-110` | `bodeul-prod` | Production | 출시 전 격리 운영 |

Vercel Preview에는 개발 Firebase와 개발 관리자 DB 값만 둔다. Production에는 production 값만 두며, 값이 없을 때 서버 API가 설정 오류로 종료되는 fail-closed 상태를 유지한다. Firebase authorized domain에는 실제 관리자 도메인과 출시 전 검증에 필요한 Vercel 도메인만 정확한 호스트명으로 등록하고 wildcard를 사용하지 않는다.

동시 릴리스가 늘거나 production과 같은 데이터 규모·외부 연동으로 장기간 QA해야 할 때 세 번째 staging 프로젝트를 검토한다. 현재 MVP 규모에서는 비용과 운영 대상을 늘리는 효과가 더 크므로 추가하지 않는다.

## 배포 정책

### 관리자 웹

- `master` PR에는 `lint-and-build`, CodeQL과 Vercel Preview 성공을 요구한다.
- 보호 규칙을 통과한 squash merge를 production 배포 승인으로 간주한다.
- Vercel은 같은 프로젝트의 `master` 병합을 Production에 자동 배포한다.
- 배포 후 루트 200, 무인증 관리자 API 401, 함수 리전 `hnd1`을 확인한다.
- 이전 정상 deployment로 즉시 rollback할 수 있어야 한다.

### Core API와 DB migration

- production 배포와 migration은 `workflow_dispatch`로만 실행한다.
- `core-api-production`과 `core-api-migration-production` GitHub Environment의 승인 보호를 유지한다.
- 배포 workflow는 `master`의 실제 40자 commit SHA와 `bodeul-core-api` 서비스명을 다시 입력해야 진행한다.
- production DB migration도 `master`의 실제 40자 commit SHA와 복원 가능한 백업 증적을 요구한다.
- DB migration을 먼저 실행하고 호환성 검증 뒤 애플리케이션을 배포한다.
- Cloud Run은 commit SHA image를 사용하고 `/health` 200, 무인증 API 401을 확인한다.
- smoke test 실패 시 직전 정상 revision이 있으면 트래픽을 자동 복구하고, 수동 rollback도 출시 전에 리허설한다.
- production 리소스와 secret version이 준비되기 전에는 production workflow를 실행하지 않는다.

초기 production 런타임은 1 vCPU, 1 GiB, 최소 인스턴스 0, 최대 인스턴스 2, 인스턴스당 DB pool 2로 시작한다. 최대 DB 연결을 4개로 제한하면서 초기 트래픽에 두 인스턴스까지 대응하는 현재 MVP 기준이다. 실제 지연·연결 수와 비용을 확인한 뒤 조정한다.

production Secret Manager ID는 다음으로 고정한다.

- `bodeul-core-api-production-db-jdbc-url`
- `bodeul-core-api-production-db-username`
- `bodeul-core-api-production-db-password`
- `bodeul-core-api-production-kakao-local-rest-api-key`

`core-api/deploy/cloud-run/set-production-secrets.ps1`은 production project ID 재입력과 허용된 secret ID 검사를 통과한 경우에만 기존 secret에 version을 추가한다.

## PostgreSQL 권한

production DB도 개발 DB와 같은 역할 경계를 사용하되 자격 증명은 새로 만든다.

| role | 용도 | 초기 권한 |
| --- | --- | --- |
| `bodeul_migration` / `bodeul_migrator` | Flyway와 schema 변경 | migration에만 사용 |
| `bodeul_core_runtime` / `bodeul_core_service` | 사용자 서비스 | 전환한 도메인의 필요한 DML만 부여 |
| `bodeul_admin_runtime` / `bodeul_admin_service` | 관리자 서버 | 초기 SELECT-only, 쓰기 기능별 별도 검토 |

- `anon`, `authenticated`, `service_role`을 애플리케이션 DB 접속 계정으로 사용하지 않는다.
- 브라우저와 APK에서 Supabase Data API나 PostgreSQL에 직접 연결하지 않는다.
- public schema의 public role grant는 0을 유지한다.
- 도메인마다 Firestore 또는 PostgreSQL 중 하나만 쓰기 source of truth로 둔다.

## 백업과 복원

- 실제 사용자 데이터를 받기 전에 production Supabase를 일일 백업을 제공하는 유료 등급 또는 동등한 보호 수준으로 전환한다.
- 제공자 일일 백업은 최소 7일 보존을 기준으로 한다.
- 매주 암호화한 logical dump를 제공자 외부의 제한된 저장소에 보관하고 4주 뒤 순환 삭제한다.
- 최초 출시 전 복원 리허설을 완료하고 이후 분기마다 반복한다.
- 복원 후 custom login role 비밀번호를 교체하고 runtime 권한, row 수와 핵심 쿼리를 다시 검증한다.
- PostgreSQL 백업에는 Firebase Storage 객체가 포함되지 않으므로 파일 백업과 복원 절차를 별도로 유지한다.
- DB가 4GB를 넘거나 허용 가능한 데이터 손실 시간이 24시간보다 짧아지면 PITR을 적용한다.
- `.github/workflows/postgres-production-backup-restore.yml`은 production migration 자격 증명으로 읽기 전용 custom-format dump를 만든다. 별도 PostgreSQL 컨테이너에서 owner, ACL, 전체 테이블 row 수, RLS, 정책, 인덱스, 제약과 Flyway 이력을 대조한 경우에만 GCS에 업로드한다.
- workflow의 GCS 권한은 `bodeul-db-backup`에 한정하며 object 생성·조회만 허용한다. dump는 GitHub Artifact에 올리지 않는다.
- 검증된 object는 `gs://bodeul-prod-110-db-backups/postgres/verified/YYYY/MM/DD/<실행시각>/`에 dump, SHA-256과 복원 보고서를 함께 보관한다.

## 보안과 모니터링

- production secret은 Google Secret Manager와 Vercel Production 환경에만 새로 등록한다. 개발 secret을 복사하지 않는다.
- 서비스 계정 JSON key는 발급하지 않고 GitHub OIDC와 WIF를 사용한다.
- 모든 관리자 계정에 MFA를 적용하고 공용 계정을 금지한다.
- 출시 전 최소 2명의 실명 운영자를 정해 한 명의 계정 잠금이 전체 운영 중단으로 이어지지 않게 한다.
- Google Cloud budget 알림 임계값은 50%, 80%, 100%로 고정한다. 개발 10,000 KRW, production 30,000 KRW를 유지한다.
- 실제 사용자 데이터 투입 전 Supabase 조직을 Pro로, 실제 운영 전 Vercel을 개발자 좌석 2개의 Pro로 전환한다.
- Supabase spend cap을 유지하고 PITR, custom domain과 Log Drain은 초기 운영 비용에 포함하지 않는다.
- Cloud Run 오류율·지연·인스턴스 수, PostgreSQL 연결 수·용량·백업, Vercel 실패 배포와 Firebase Auth 오류를 확인한다.

## 생성과 출시 순서

1. 월 150,000 KRW 운영 한도와 2026-12-15 목표 일정을 기준으로 한다. 결제 책임자, 기준 도메인과 운영자 2명은 출시 전에 확정한다.
2. production Google Cloud 프로젝트를 만들고 Firebase를 활성화한다. 완료.
3. production Supabase 프로젝트와 DB role을 만들고 Flyway migration을 실행한다. 완료.
4. WIF, 서비스 계정, Artifact Registry와 Secret Manager를 만든다. 완료. Cloud Run 서비스 생성은 첫 승인 배포에서 수행한다.
5. Vercel Production 환경변수와 도메인을 연결한다.
6. Firebase authorized domain, App Check, 관리자 MFA와 최소 권한을 검증한다.
7. backup/restore, Cloud Run revision과 Vercel deployment rollback을 리허설한다.
8. smoke test와 운영 담당자 확인 뒤 트래픽을 전환한다.

## 현재 준비 상태

- `.github/workflows/core-api-production-deploy.yml`에 보호된 수동 배포, 대상 재확인과 smoke 실패 rollback을 준비했다.
- `.github/workflows/core-api-migration.yml`의 production 경로에 `master` SHA, 백업 증적과 사전 Core API 검사를 적용했다.
- `core-api/deploy/cloud-run/set-production-secrets.ps1`에 production 전용 secret version 입력 경계를 준비했다.
- `core-api-production`에는 production GCP/Firebase 식별자와 DB Secret Manager version을 등록했다. Kakao production secret version은 비어 있어 첫 배포는 계속 fail-closed다.
- `core-api-migration-production`에는 production migration 자격 증명을 등록했고, run `29570950189`에서 보호 승인 뒤 Flyway V1~V3 적용을 완료했다.
- production Firestore와 Storage에는 저장소의 현재 Rules를 배포했다. Firestore는 Tokyo와 삭제 방지를 사용하고 App Check는 아직 강제하지 않는다.
- production Supabase는 빈 데이터 상태로 `bodeul` schema, 최소 권한 role, RLS 3개 테이블과 정책 6개를 갖는다. 공개 role table grant는 0건이다.
- production Supabase 조직은 현재 Free다. 2026-11-16까지 Pro로 전환하고 spend cap과 일일 7일 백업을 확인한다.
- pre-migration schema dump는 비공개 GCS bucket에 28일 보존으로 저장했다. 실제 restore 리허설은 출시 게이트로 남아 있다.
- production logical dump 전용 서비스 계정, WIF provider와 GitHub Environment 변수를 구성했다. 2026-07-18에 현재 production dump를 격리 PostgreSQL에 복원해 owner, ACL, row 수, RLS, 정책, 인덱스, 제약과 Flyway 이력 일치를 확인했다.
- production 리소스 생성 후 첫 배포 전에는 App Check를 `observe`로 시작하고 정상 release 요청을 확인한 뒤 `enforce`로 바꾼다.

## 사람 결정이 필요한 항목

- 구매할 기준 도메인
- 실명 운영자 2명, 장애 대응 책임자와 rollback 승인자
- 2026-12-15 전환에 맞춘 사용자 공지 내용과 최종 점검 시간

나머지 리소스 이름, 리전, 환경 경계, 배포·백업·보안 기본값은 이 문서를 기준으로 진행한다.

## 근거

- [Vercel 환경 분리](https://vercel.com/docs/deployments/environments)
- [Vercel Functions 리전 설정](https://vercel.com/docs/functions/configuring-functions/region)
- [Vercel 리전 목록](https://vercel.com/docs/regions)
- [Supabase 환경 관리](https://supabase.com/docs/guides/deployment/managing-environments)
- [Supabase 백업](https://supabase.com/docs/guides/platform/backups)
- [Firebase 환경 분리](https://firebase.google.com/docs/projects/dev-workflows/overview-environments)

## 관련 문서

- [목표 인프라 구조](../architecture/target-infrastructure.md)
- [관리자 웹 환경 기준](admin-web-environments.md)
- [Spring Core API Cloud Run 인프라 런북](core-api-infrastructure-runbook.md)
- [비용과 쿼터 모니터링](cost-monitoring.md)
- [2026년 Production 운영 전환 계획](production-transition-plan-2026.md)
- [데이터 보관 및 파기 정책](data-retention-policy.md)
- [Production PostgreSQL 백업·복원 리허설](../reports/postgres-production-backup-restore-rehearsal-2026-07-18.md)
