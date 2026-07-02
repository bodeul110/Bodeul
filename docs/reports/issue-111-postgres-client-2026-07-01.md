# Issue 111 PostgreSQL client 초기화 기록

기준일: 2026-07-01

## 작업 목적

#88에서 추가한 `DATABASE_URL` 검증을 실제 PostgreSQL client 초기화와 연결한다.

## 선택한 방식

1차 DB 접근 방식은 Drizzle ORM이 아니라 `pg` pool로 둔다.

- `DATABASE_URL`이 없으면 PostgreSQL client를 만들지 않는다.
- `DATABASE_URL`이 있으면 `pg` pool을 만든다.
- pool 기본값은 `max=5`, `idleTimeoutMillis=10000`, `connectionTimeoutMillis=5000`으로 둔다.
- 서버 종료 시 HTTP 서버 종료 후 pool을 닫는다.
- 연결 확인은 `select 1` 기준으로 두고 실패 시 `db_connection_failed`만 반환한다.

## 대안

- Drizzle ORM을 바로 도입한다.
- DB client 없이 mock repository를 먼저 둔다.
- 서버 시작 시 DB 연결 확인을 강제한다.

## 선택 이유

현재 MVP 규모에서는 실제 관리자 read API가 아직 확정되지 않았으므로 ORM보다 pool 생명주기를 먼저 고정하는 편이 안전하다. 서버 시작 시 연결 확인을 강제하면 secret 없는 CI와 로컬 실행이 깨질 수 있어, pool 초기화와 연결 확인을 분리한다.

## 리스크

- pool 설정이 과하면 Supabase 개발 DB connection limit에 영향을 줄 수 있다.
- 서버 시작 시 실제 연결을 강제하지 않으므로 DB 장애는 첫 query 또는 명시적 연결 확인 시점에 드러난다.
- connection string이 오류 메시지나 로그에 노출되지 않도록 후속 API에서도 같은 기준을 유지해야 한다.

## 검증

| 항목 | 결과 |
| --- | --- |
| `npm --prefix api run check` | 통과, 테스트 29개 성공 |
| `DATABASE_URL` 없음 | client 미생성 |
| `DATABASE_URL` 있음 | 제한된 pool 설정으로 client 생성 |
| pool 종료 | `close()`에서 `end()` 호출 |
| 연결 확인 성공 | `{ok: true}` |
| 연결 확인 실패 | `{ok: false, error: "db_connection_failed"}` |
| `npm --prefix api audit --json` | moderate 6건, #110의 `firebase-admin` 전이 의존성 경고와 동일 |
| 민감값 패턴 검색 | 실제 secret 없음 |

## 남은 범위

- 실제 PostgreSQL query 기반 관리자 read API
- PostgreSQL `app_users.role` 기반 관리자 인가
- 운영 환경 `DATABASE_URL` secret 주입
