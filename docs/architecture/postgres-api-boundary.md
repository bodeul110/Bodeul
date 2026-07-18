# PostgreSQL API 경계

기준일: 2026-07-18

## 원칙

Android, 웹 브라우저와 Firebase Functions가 PostgreSQL 접속 문자열을 소유하지 않는다. 사용자 요청은 Spring Core API, 관리자 요청은 Next.js 관리자 서버를 통해서만 PostgreSQL에 접근한다.

```text
관리자 브라우저 ─ Next.js 관리자 서버 ─┐
                                      ├─ Supabase PostgreSQL
사용자·매니저 웹/앱 ─ Spring Core API ─┘
```

두 서버는 서로를 proxy로 호출하지 않는다. 같은 DB를 사용하되 접속 계정, 권한, 연결 상한과 배포 비밀값을 분리한다.

## 인증과 인가

1. 클라이언트가 Firebase Auth로 로그인한다.
2. 서버가 Firebase ID token의 서명, issuer, audience와 만료를 검증한다.
3. Firebase UID를 `bodeul.app_users.firebase_uid`에 연결한다.
4. 서버가 요청에 필요한 PostgreSQL 역할을 확인한다.
5. 서버 전용 DB role의 허용된 SQL만 실행한다.

App Check는 이 흐름을 대체하지 않는다. App Check가 유효해도 ID token과 PostgreSQL role 검증을 계속 수행한다.

## 역할

| 역할 | 용도 | 권한 기준 |
| --- | --- | --- |
| `bodeul_migration` / `bodeul_migrator` | Flyway DDL과 소유권 | migration workflow에서만 사용 |
| `bodeul_core_runtime` / `bodeul_core_service` | 사용자 서비스 | Core API에 필요한 DML만 허용 |
| `bodeul_admin_runtime` / `bodeul_admin_service` | 관리자 서비스 | 운영 조회와 검증된 관리자 전용 함수만 허용 |

브라우저, APK, 공개 `NEXT_PUBLIC_*`/`VITE_*` 값에는 DB 접속 정보를 넣지 않는다.

## 연결 방식

| 실행 환경 | 연결 |
| --- | --- |
| Vercel Next.js | Supavisor transaction mode 6543, pool max 1, Supabase Root CA 검증 |
| Cloud Run Spring | Supavisor session mode 5432, Hikari pool max 5 |
| migration·복구 | runtime과 분리한 migration 접속, transaction과 검증 SQL 사용 |

관리자 DB 계정의 PostgreSQL connection limit은 5이며 애플리케이션 pool은 1로 더 좁게 제한한다. TLS 인증서 검증을 끄지 않는다.

## 도메인 전환 조건

특정 도메인의 source of truth를 PostgreSQL로 바꾸려면 다음을 모두 만족해야 한다.

- schema migration과 rollback이 재현 가능하다.
- Firebase 백필 row 수와 필수 필드·관계 비교가 통과한다.
- 인증·역할별 401·403·정상 응답을 실제 환경에서 확인한다.
- 기존 경로와 API 결과 비교가 통과한다.
- 쓰기 소유자를 하나로 정하고 무제한 이중 쓰기를 두지 않는다.
- 장애 시 rollback 절차와 데이터 보정 책임을 기록한다.
- backup/restore를 격리 환경에서 리허설한다.

병원 가이드 관리자 조회와 매니저 배정, Android 예약·동행·리포트·후속 처리 쓰기는 이 경계를 실제 검증했다. 개발 환경에서 해당 도메인은 PostgreSQL이 source of truth이고 Firestore Rules는 업무 쓰기를 거부한다. 채팅·위치는 V8 schema까지만 준비했으며 Core API·Realtime·Android 전환 전까지 Firestore legacy 경로를 제한적으로 유지한다.

## 금지하는 구조

- 브라우저나 Android에서 PostgreSQL 직접 접속
- Next.js → Spring → PostgreSQL 관리자 요청
- Spring → Next.js → PostgreSQL 사용자 요청
- runtime 서비스에 migration 자격 증명 전달
- 같은 도메인의 Firestore와 PostgreSQL을 종료 조건 없이 동시에 쓰기
- 인증서 검증을 끈 외부 DB 접속

## 관련 문서

- [목표 인프라 구조](target-infrastructure.md)
- [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
- [Supabase DB 권한 검증](../reports/supabase-database-access-foundation-2026-07-13.md)
- [Issue 159 Node API 종료 기록](../reports/issue-159-node-api-retirement-audit-2026-07-16.md)
