# Production PostgreSQL 백업·복원 리허설

기준일: 2026-07-18

## 작업 목적

Flyway 적용 전 schema dump만 있던 production PostgreSQL에 현재 schema와 데이터를 포함한 복원 가능한 백업을 만들고, 실제로 격리 복원되는지 출시 전에 검증한다.

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다. 현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 선택한 방식

- GitHub `core-api-migration-production` Environment의 migration 자격 증명으로 읽기 전용 `pg_dump`를 실행한다.
- PostgreSQL 17 custom format에 owner와 ACL을 포함한다.
- 별도 PostgreSQL 17 컨테이너에 필요한 `NOLOGIN` role을 먼저 만든 뒤 dump를 복원한다.
- 원본과 복원본의 전체 테이블 row 수, owner, RLS, 정책, 인덱스, 제약, table grant와 Flyway 이력을 비교한다.
- 모든 검증을 통과한 경우에만 전용 WIF 서비스 계정으로 GCS에 dump, SHA-256과 보고서를 업로드한다.

GitHub Artifact는 DB 백업 보관소로 사용하지 않았다. 현재 production Google Cloud 안의 비공개 bucket을 사용하므로 제공자 장애까지 완전히 분리한 백업은 아니지만, Supabase와는 별도 저장소이고 기존 운영 권한과 통합할 수 있어 현재 MVP에서 우선 적용했다.

## 실행 결과

- workflow: [Production PostgreSQL Backup and Restore Rehearsal run 29633892075](https://github.com/bodeul110/Bodeul/actions/runs/29633892075)
- commit: `2572421665e199d567c0035ac3d7fbf32bcb25d1`
- 실행 시간: 58초
- 원본 PostgreSQL: 17.6
- 복원 이미지: `postgres:17-alpine`
- dump 크기: 24,242 bytes
- SHA-256: `758e3c87a7df88b8493dff3af045cf1d393e7ce4f1e8876961f4d424195d1f1b`
- GCS object: `gs://bodeul-prod-110-db-backups/postgres/verified/2026/07/18/20260718T062322Z-bodeul-production/20260718T062322Z-bodeul-production.dump`

| 검증 항목 | 원본 | 복원 | 결과 |
| --- | ---: | ---: | --- |
| `app_users` row | 0 | 0 | 일치 |
| `appointment_requests` row | 0 | 0 | 일치 |
| `hospital_guides` row | 0 | 0 | 일치 |
| Flyway history row / 최대 version | 3 / V3 | 3 / V3 | 일치 |
| RLS 활성 업무 테이블 | 3 | 3 | 일치 |
| RLS 정책 | 6 | 6 | 일치 |
| 인덱스 | 14 | 14 | 일치 |
| 제약 | 31 | 31 | 일치 |
| Core/Admin SELECT grant | 각 3 | 각 3 | 일치 |
| schema와 table owner | `bodeul_migration` | `bodeul_migration` | 일치 |

`PUBLIC`, `anon`, `authenticated`, `service_role`의 업무 테이블 grant는 원본과 복원본 모두 0건이었다. GCS에서 dump를 다시 내려받아 계산한 SHA-256도 보고서 값과 일치했다.

## 권한 경계

- WIF provider `bodeul-db-backup-production`은 `bodeul110/Bodeul`, `refs/heads/master`, `core-api-migration-production`을 모두 요구한다.
- 서비스 계정 `bodeul-db-backup`에는 backup bucket의 `roles/storage.objectCreator`, `roles/storage.objectViewer`만 부여했다.
- 서비스 계정 key와 object 삭제 권한은 만들지 않았다.
- workflow는 project ID와 실행 commit SHA를 다시 입력하고 Environment 승인을 받아야 실행된다.

## 대안과 선택 이유

- Supabase 제공자 백업만 사용하는 방식은 무료 등급과 장애 경계에 의존하므로 출시 게이트 증적으로 부족하다.
- 운영 DB에 바로 덮어쓰는 복원은 위험하므로 독립 컨테이너 복원을 선택했다.
- 별도 VM에 백업 서버를 운영하는 방식은 현재 데이터 규모에서 비용과 운영 부담이 더 크다.
- schema-only dump는 데이터·ACL 검증이 불가능하므로 현재 schema의 모든 테이블 데이터와 owner, ACL을 포함했다.

## 리스크와 남은 범위

- 업무 테이블이 현재 0건이므로 구조와 권한 복원은 검증했지만 대용량 복원 시간과 실제 데이터 무결성 부하는 아직 검증하지 않았다.
- schema dump는 global role 정의와 custom login role 비밀번호를 포함하지 않는다. 실제 재해 복구에서는 bootstrap role을 만든 뒤 복원하고 login 비밀번호·membership·runtime 연결을 다시 검증해야 한다.
- GCS bucket은 28일 retention과 7일 soft delete를 사용한다. 실제 사용자 데이터 수용 전에는 주기 실행과 최소 보관 개수를 운영 일정에 고정해야 한다.
- Supabase 일일 백업 등급 또는 동등한 보호 수준 확정은 별도 출시 결정으로 남아 있다.
- PostgreSQL dump에는 Firebase Storage 객체가 포함되지 않는다.

## 재실행

GitHub Actions에서 `Production PostgreSQL Backup and Restore Rehearsal`을 선택하고 다음 값을 입력한다.

- `confirm_project`: `bodeul-prod-110`
- `confirm_commit`: 실행할 `master`의 40자 commit SHA

성공한 run summary의 `migration backup_reference`는 production migration 실행 시 백업 증적으로 사용할 수 있다.
