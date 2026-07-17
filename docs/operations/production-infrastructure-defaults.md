# Production 인프라 기본값

기준일: 2026-07-17

이 문서는 BoDeul production 리소스를 만들기 전에 기술적으로 확정할 수 있는 이름, 리전, 배포 경계와 운영 기준을 고정한다. 비용 결제, 도메인 구매와 실명 담당자 지정은 별도 승인 전까지 실행하지 않는다.

## 결정 요약

- 개발과 production은 Google Cloud/Firebase 프로젝트와 Supabase 프로젝트를 각각 분리한다.
- 관리자 웹은 기존 Vercel 프로젝트 `bodeul-admin-web`의 Preview와 Production 환경을 구분해 사용한다. Vercel 프로젝트를 하나 더 만들지 않는다.
- 관리자 웹과 Core API, Supabase는 Tokyo 리전에 맞춘다.
- 관리자 웹은 Vercel Next.js 서버, 사용자 서비스는 Cloud Run Spring Core API가 담당한다.
- 두 서버는 같은 production PostgreSQL을 서로 다른 최소 권한 role로 사용하며 서로를 proxy로 호출하지 않는다.
- Firebase Auth, FCM, Storage와 Firebase 결합 Functions는 production Firebase 프로젝트에서 유지한다.
- 개발 Preview를 출시 전 검증 환경으로 사용하고, 현재 규모에서는 세 번째 staging 환경을 만들지 않는다.

## 리소스 기준

| 범위 | 확정값 | 비고 |
| --- | --- | --- |
| Google Cloud/Firebase 표시 이름 | `BoDeul Production` | 하나의 Google Cloud 프로젝트에서 Firebase를 활성화한다. |
| Google Cloud project ID 후보 | `bodeul-prod-110` | 전역 ID이므로 생성 직전에 사용 가능 여부를 다시 확인한다. |
| Google Cloud/Cloud Run 리전 | `asia-northeast1` | Tokyo |
| Cloud Run 서비스 | `bodeul-core-api` | preview 접미사를 사용하지 않는다. |
| Artifact Registry/이미지 | `bodeul-core-api` | 개발 프로젝트와 이름은 같아도 프로젝트 경계로 분리된다. |
| 배포 서비스 계정 | `bodeul-core-deployer` | WIF 배포 전용 |
| 런타임 서비스 계정 | `bodeul-core-runtime` | Secret Manager 접근과 실행 전용 |
| WIF pool/provider | `github-actions` / `bodeul-core-api-production` | 저장소, `master`, GitHub Environment 조건을 모두 제한한다. |
| 배포 Environment | `core-api-production` | 수동 production 배포와 승인 보호 |
| migration Environment | `core-api-migration-production` | 앱 배포와 DB 변경을 분리한다. |
| Supabase 표시 이름 | `bodeul-prod` | 개발 프로젝트와 별도 생성 |
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
| production | `bodeul-prod-110` 후보 | `bodeul-prod` | Production | 실제 운영 |

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
- DB migration을 먼저 실행하고 호환성 검증 뒤 애플리케이션을 배포한다.
- Cloud Run은 commit SHA image를 사용하고 `/health` 200, 무인증 API 401을 확인한다.
- 직전 정상 revision으로 트래픽을 돌리는 rollback을 출시 전에 리허설한다.
- production 리소스와 secret version이 준비되기 전에는 production workflow를 실행하지 않는다.

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

## 보안과 모니터링

- production secret은 Google Secret Manager와 Vercel Production 환경에만 새로 등록한다. 개발 secret을 복사하지 않는다.
- 서비스 계정 JSON key는 발급하지 않고 GitHub OIDC와 WIF를 사용한다.
- 모든 관리자 계정에 MFA를 적용하고 공용 계정을 금지한다.
- 출시 전 최소 2명의 실명 운영자를 정해 한 명의 계정 잠금이 전체 운영 중단으로 이어지지 않게 한다.
- Google Cloud budget 알림 임계값은 50%, 80%, 100%로 고정한다. 실제 월 한도 금액은 결제 책임자가 정한다.
- Cloud Run 오류율·지연·인스턴스 수, PostgreSQL 연결 수·용량·백업, Vercel 실패 배포와 Firebase Auth 오류를 확인한다.

## 생성과 출시 순서

1. 결제 책임자와 월 예산, 기준 도메인, 운영자 2명, 출시 일정을 확정한다.
2. production Google Cloud 프로젝트를 만들고 Firebase를 활성화한다.
3. production Supabase 프로젝트와 DB role을 만들고 Flyway migration을 실행한다.
4. WIF, 서비스 계정, Artifact Registry, Secret Manager와 Cloud Run을 만든다.
5. Vercel Production 환경변수와 도메인을 연결한다.
6. Firebase authorized domain, App Check, 관리자 MFA와 최소 권한을 검증한다.
7. backup/restore, Cloud Run revision과 Vercel deployment rollback을 리허설한다.
8. smoke test와 운영 담당자 확인 뒤 트래픽을 전환한다.

## 사람 결정이 필요한 항목

- production 결제 계정 소유자와 월 예산 금액
- 구매할 기준 도메인
- 실명 운영자 2명, 장애 대응 책임자와 rollback 승인자
- 출시일, 점검 시간과 사용자 공지 방식

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
