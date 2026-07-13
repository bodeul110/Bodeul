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
| migration 접속 | `bodeul_migrator` | 불가 | 2 | membership으로 가능 |
| Core 권한 | `bodeul_core_runtime` | 불가 | 제한 없음 | 불가 |
| Core 접속 | `bodeul_core_service` | 불가 | 5 | 불가 |
| Admin 권한 | `bodeul_admin_runtime` | 불가 | 제한 없음 | 불가 |
| Admin 접속 | `bodeul_admin_service` | 불가 | 5 | 불가 |

적용 migration:

- `database_access_foundation`
- `database_access_hardening`

첫 migration 적용 과정에서 Supabase 관리형 `postgres`의 superuser 제한과 PostgreSQL 17 role membership 규칙을 확인했다. 실패 시도는 transaction으로 롤백됐고 role 잔여물이 없음을 확인한 뒤, `SET` membership과 `SET LOCAL ROLE`을 명시한 최종 migration만 적용했다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| runtime role의 SUPERUSER/CREATEROLE/CREATEDB/REPLICATION/BYPASSRLS | 모두 false |
| Core/Admin runtime의 `bodeul` schema USAGE | true |
| Core/Admin runtime의 `bodeul` schema CREATE | false |
| migration role의 `bodeul` schema CREATE | true |
| `anon`, `authenticated`, `service_role`의 `bodeul` schema USAGE | false |
| 접속 role 상태 | 모두 `NOLOGIN` |
| 연결 상한 합계 | 12개, 개발 DB `max_connections`의 20% |
| `public` 신규 객체 자동 grant | `postgres` 전용으로 축소 |
| migration role 신규 함수 기본 실행 권한 | 소유자만 허용 |
| 적용 후 advisor | security 0건, performance 0건 |
| GitHub migration Environment | preview/production 생성, 보호 브랜치와 `bodeul110` 승인 적용, secret 미등록 |
| migration 실행 경로 | `migrateDatabase` Gradle task와 수동 `Core API DB Migration` workflow 추가 |

검증 SQL은 `core-api/db/verification/001_database_access_checks.sql`에 둔다.

## 남은 범위

1. 비밀번호 관리 도구에서 개발 migration과 Core API용 강한 비밀번호를 각각 생성한다.
2. 개발 Supabase에서 `bodeul_migrator`, `bodeul_core_service`만 `LOGIN`으로 활성화한다.
3. migration과 Core API connection string을 GitHub Environment와 OCI secret에 분리해 등록한다.
4. runtime 환경에는 migration 자격 증명을 전달하지 않는지 확인한다.
5. 현재 Vite 관리자 웹은 서버 비밀값을 보관할 수 없으므로 `bodeul_admin_service`는 `NOLOGIN`으로 유지한다. Next.js 서버 전환과 서버 전용 환경변수 경계가 확인된 뒤 별도 비밀번호로 활성화한다.
6. 첫 업무 테이블 migration에서 Core/Admin DML grant와 필요한 RLS 정책을 명시한다.
7. production 적용은 개발 DB 접속·권한·rollback 검증 이후 별도 승인으로 진행한다.

## 참고

- [Supabase Postgres Roles](https://supabase.com/docs/guides/database/postgres/roles)
- [Supabase API 보안과 기본 권한](https://supabase.com/docs/guides/api/securing-your-api)
- [Supabase PostgreSQL 연결](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [PostgreSQL 17 Role Membership](https://www.postgresql.org/docs/17/role-membership.html)
