# Supabase DB 권한 기반 검증

기준일: 2026-07-13

## 작업 목적

Spring Core API와 Next.js 관리자 서버가 같은 PostgreSQL을 사용하더라도 자격 증명과 권한을 공유하지 않도록 개발 DB의 role과 schema 경계를 먼저 만든다.

## 선택한 방식

- 서버 전용 테이블은 Data API에 노출하지 않는 `bodeul` schema에 둔다.
- DDL 권한, Core API runtime, 관리자 runtime 권한을 별도 group role로 관리한다.
- 실제 접속에 사용할 role은 별도로 만들되 비밀번호를 migration에 넣지 않고 `NOLOGIN` 상태로 시작한다.
- runtime role은 schema `USAGE`만 받고 `CREATE`는 받지 않는다.
- `public`에서 생성되는 새 객체의 `anon`, `authenticated`, `service_role` 자동 grant를 제거한다.

대안은 모든 서버가 `postgres` 또는 하나의 공용 계정을 사용하는 방식, `public` schema와 RLS만 사용하는 방식이었다. 현재 MVP에서도 서버별 사고 범위와 쿼리 추적을 분리할 필요가 있고, 아직 업무 테이블이 없는 시점이라 권한 경계를 먼저 적용하는 비용이 낮아 선택하지 않았다.

## 적용 전 상태

| 항목 | 확인 결과 |
| --- | --- |
| 환경 | 사용자가 개발용으로 확인한 도쿄 리전 Supabase 프로젝트 |
| PostgreSQL | 17.6 |
| `max_connections` | 60 |
| 업무 테이블 | `public` 0개, 별도 업무 schema 없음 |
| 업무용 custom role | 없음 |
| migration history | 없음 |
| `public` 기본 grant | `anon`, `authenticated`, `service_role`에 테이블·함수·sequence 자동 권한 존재 |
| advisor | security 0건, performance 0건 |

기존 문서에는 서울 리전의 `bodeul-dev-rdb` 적용 기록이 있으나 현재 연결된 계정에서는 해당 프로젝트가 조회되지 않았다. 이번 결과는 사용자가 개발용으로 확인한 현재 도쿄 프로젝트에 한정한다.

## 적용 내용

| 구분 | role | 로그인 | 연결 상한 | schema CREATE |
| --- | --- | --- | ---: | --- |
| migration 권한 | `bodeul_migration` | 불가 | 제한 없음 | 가능 |
| migration 접속 | `bodeul_migrator` | 가능 | 2 | membership으로 가능 |
| Core 권한 | `bodeul_core_runtime` | 불가 | 제한 없음 | 불가 |
| Core 접속 | `bodeul_core_service` | 가능 | 5 | 불가 |
| Admin 권한 | `bodeul_admin_runtime` | 불가 | 제한 없음 | 불가 |
| Admin 접속 | `bodeul_admin_service` | 불가 | 5 | 불가 |

적용 migration:

- `database_access_foundation`
- `database_access_hardening`

첫 migration 적용 과정에서 Supabase 관리형 `postgres`의 superuser 제한과 PostgreSQL 17 role membership 규칙을 확인했다. 실패 시도는 transaction으로 롤백됐고 role 잔여물이 없음을 확인한 뒤, `SET` membership과 `SET LOCAL ROLE`을 명시한 최종 migration만 적용했다.

GitHub Actions 첫 실접속에서 생성된 Flyway history table은 로그인 role이 소유하고 있었다. 관리 API 경로에서는 소유자 권한이 없어 변경이 거부됐으며, 개발 DB의 migration 접속 계정으로 소유자를 `bodeul_migration`에 정렬한 뒤 다시 검증했다. 실패한 변경은 Supabase migration 이력에 남지 않았다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| runtime role의 SUPERUSER/CREATEROLE/CREATEDB/REPLICATION/BYPASSRLS | 모두 false |
| Core/Admin runtime의 `bodeul` schema USAGE | true |
| Core/Admin runtime의 `bodeul` schema CREATE | false |
| migration role의 `bodeul` schema CREATE | true |
| `anon`, `authenticated`, `service_role`의 `bodeul` schema USAGE | false |
| 접속 role 상태 | `bodeul_migrator`, `bodeul_core_service`는 `LOGIN`; `bodeul_admin_service`는 2026-07-17 Preview 전용 `LOGIN`으로 전환 |
| 연결 상한 합계 | 12개, 개발 DB `max_connections`의 20% |
| `public` 신규 객체 자동 grant | `postgres` 전용으로 축소 |
| migration role 신규 함수 기본 실행 권한 | 소유자만 허용 |
| 적용 후 advisor | security 0건, performance INFO 1건. migration이 아직 없어 Flyway history 성공 인덱스가 사용되지 않았다는 항목으로 유지 |
| GitHub migration Environment | preview/production 생성, 보호 브랜치와 `bodeul110` 승인 적용, preview secret 3개 등록, production 미등록 |
| migration 실행 경로 | `migrateDatabase` Gradle task와 수동 `Core API DB Migration` workflow 추가 |
| 실제 migration 검증 | 첫 실행의 비웹 Security 설정 문제를 PR #165로 수정한 뒤 [preview 실행](https://github.com/bodeul110/Bodeul/actions/runs/29224126924) 성공 |
| Flyway 객체 소유자 | 연결 시 `SET ROLE bodeul_migration`을 실행하고 `flyway_schema_history` 소유자를 `bodeul_migration`으로 통일 |

검증 SQL은 `core-api/db/verification/001_database_access_checks.sql`에 둔다.

## 2026-07-17 후속 상태

- Core API는 Cloud Run preview에서 runtime 자격 증명만 사용하고 migration 자격 증명을 전달하지 않는 구성을 검증했다.
- `bodeul_admin_service`는 Next.js Vercel Preview 전용으로 활성화했다. connection limit 5와 필요한 SELECT만 허용하며 production에는 자격 증명을 등록하지 않았다.
- 관리자 Preview에서 무인증 401, 비관리자 403, 관리자 200과 병원 가이드 조회를 실제 확인했다.
- production 적용은 개발과 분리된 프로젝트·role·자격 증명, backup/restore 검증 후 진행한다.

## 참고

- [Supabase Postgres Roles](https://supabase.com/docs/guides/database/postgres/roles)
- [Supabase API 보안과 기본 권한](https://supabase.com/docs/guides/api/securing-your-api)
- [Supabase PostgreSQL 연결](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [PostgreSQL 17 Role Membership](https://www.postgresql.org/docs/17/role-membership.html)
