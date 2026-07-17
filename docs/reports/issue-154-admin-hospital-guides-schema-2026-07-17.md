# 관리자 병원 가이드 PostgreSQL read model 준비

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

분리된 관리자 웹의 첫 Next.js 서버 경계가 개발용 Supabase PostgreSQL에서 병원 가이드를 읽을 수 있도록 `bodeul.hospital_guides` read model을 준비한다.

## 선택한 방식

- Flyway `V2__create_hospital_guides.sql`이 private `bodeul` schema에 테이블을 만든다.
- `bodeul_core_runtime`과 `bodeul_admin_runtime`에는 SELECT만 부여한다.
- `public`, `anon`, `authenticated`, `service_role`의 직접 접근은 명시적으로 제거한다.
- RLS를 켜고 Core/Admin runtime용 SELECT 정책을 분리한다.
- 병원 가이드 데이터는 migration에 넣지 않고 Firestore 백업 기반 개발 seed로 별도 적용한다.

## 대안

- 관리자 웹이 Firestore를 계속 직접 읽는다.
- Supabase Data API와 브라우저용 key로 테이블을 직접 읽는다.
- 기존 Node `api/`의 `hospital_guides` 계약을 계속 운영한다.

## 선택 이유

현재 MVP 규모에서는 개인정보가 거의 없는 병원 가이드를 첫 서버 read model로 옮기면 관리자 전용 DB role, Firebase ID token 검증, Vercel same-origin API 경계를 낮은 위험으로 검증할 수 있다. 브라우저에 DB 자격 증명을 전달하지 않고, 기존 Vite/Firebase 경로를 rollback으로 유지할 수 있다는 점도 현재 단계에 맞다.

## 리스크

- Firestore와 PostgreSQL을 동시에 수정하면 데이터가 어긋날 수 있으므로 전환 검증 중 PostgreSQL은 read model로만 사용한다.
- `steps`는 JSON 배열이라는 외곽 계약만 강제하므로 내부 필드 고도화 시 별도 migration이 필요하다.
- Vercel serverless 연결은 Supavisor transaction mode와 작은 client pool을 사용하지 않으면 개발 DB 연결 상한을 소모할 수 있다.

## 사전 확인

| 항목 | 결과 |
| --- | --- |
| Supabase project | 개발용 Tokyo project, `ACTIVE_HEALTHY` |
| 기존 `bodeul` table | `flyway_schema_history`, `app_users` |
| `hospital_guides` | 적용 전 없음 |
| `bodeul_admin_service` | `NOLOGIN`, connection limit 5 |
| Core API test | `gradlew check` 통과 |
| SQL 권한 dry-run | 일반 `postgres`는 `bodeul` CREATE와 `bodeul_migration` SET 권한이 없어 거부됨 |

일반 관리 연결에서 DDL이 거부된 결과는 schema 변경이 migration 전용 계정에 제한됐다는 증거다. 실제 적용은 `core-api-migration-preview` Environment의 `bodeul_migrator` 자격 증명으로만 실행한다.

## 적용 결과

[Core API DB Migration run 29524104305](https://github.com/bodeul110/Bodeul/actions/runs/29524104305)에서 Flyway version 2를 개발 DB에 적용했다.

| 항목 | 결과 |
| --- | --- |
| Flyway | version 1, 2 성공 기록 |
| table owner | `bodeul_migration` |
| RLS | 활성화, Core/Admin SELECT 정책 각각 존재 |
| runtime 권한 | Core/Admin SELECT만 허용, INSERT/UPDATE/DELETE/TRUNCATE 없음 |
| 공개 role | `public`, `anon`, `authenticated`, `service_role`의 schema/table CRUD 없음 |
| 병원 가이드 seed | 최신 개발 백업 기준 1건 적용 |
| 사용자 role seed | 6건: ADMIN 1, GUARDIAN 1, MANAGER 3, PATIENT 1 |
| 관리자 접속 role | `bodeul_admin_service`는 `NOLOGIN`, connection limit 5 유지 |
| production | 미적용 |

Supabase Security Advisor는 경고가 없었다. `bodeul.flyway_schema_history`는 private schema의 migration metadata라 RLS를 켜지 않았고 공개 role의 schema 접근은 차단돼 있다. 애플리케이션 데이터 테이블인 `app_users`와 `hospital_guides`의 RLS 경계와 구분해 관리한다.

관리자 웹은 별도 저장소 PR #18에서 Next.js Route Handler까지 반영했고 Preview의 루트 200과 인증 없는 API 401을 확인했다. 실제 ADMIN 200과 비관리자 403은 관리자 접속 role과 Vercel 서버 환경변수를 활성화한 뒤 검증한다.
