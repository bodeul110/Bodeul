# 목표 인프라 구조

기준일: 2026-07-18

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결정

관리자와 사용자 서버를 분리하고 공용 PostgreSQL만 공유한다.

- 관리자 브라우저 → Vercel Next.js 관리자 서버 → Supabase PostgreSQL
- 환자·보호자·매니저 웹/앱 → Cloud Run Spring Core API → Supabase PostgreSQL
- 관리자 Vercel Functions, Cloud Run과 Supabase는 Tokyo 리전에 맞춘다.
- Firebase Auth, FCM, App Check, Storage와 Firebase 결합 Functions는 유지한다.
- 채팅·위치·상태 갱신은 PostgreSQL 커밋 후 Supabase Realtime private Broadcast로 전달한다.
- Firestore는 전환 기간의 읽기 전용 rollback 자료로만 남기고 안정화 후 업무 경로에서 제거한다.
- 두 서버는 서로를 proxy로 호출하지 않는다.
- Kakao Local REST와 서버 비밀값이 필요한 외부 연동은 Spring Core API가 소유한다.

## 선택 이유

관리자 화면은 운영 권한과 배포 주기가 사용자 서비스와 다르다. Next.js 서버가 관리자 전용 DB role을 직접 사용하면 불필요한 서버 간 hop과 CORS 경계를 만들지 않으면서 권한을 분리할 수 있다. 사용자 서비스는 Java/Spring으로 도메인 계약과 외부 API를 한 곳에 모으고 Cloud Run에서 독립 배포한다.

현재 MVP 규모에서는 인증·푸시·파일까지 한 번에 교체하는 비용이 크므로 Firebase의 전문 기능은 유지한다. 업무 데이터는 관계형 무결성, 감사 이력, 정산·통계와 단일 원본을 위해 PostgreSQL로 옮긴다.

Realtime은 DB 직접 접근 경로가 아니다. Supabase Third-Party Auth에 개발·production Firebase 프로젝트를 각각 등록하고, 클라이언트는 `role: authenticated` claim이 포함된 Firebase JWT로 private 채널을 구독한다. `realtime.messages` RLS는 Firebase 프로젝트 ID, 채널 주제와 도메인 참여 관계를 검증한다. 권위 있는 조회와 명령은 계속 Spring 또는 Next.js 서버를 거친다. 이를 통해 멘토가 제안한 서버 경계를 유지하면서 Firestore 실시간 listener를 대체한다.

## 서버와 DB 경계

| 범위 | 관리자 서버 | Core API |
| --- | --- | --- |
| 런타임 | Vercel Next.js | Google Cloud Run Spring Boot |
| 인증 | Firebase ID token | Firebase ID token |
| 인가 | PostgreSQL `ADMIN` role | 사용자·보호자·매니저 role |
| DB 접속 | `bodeul_admin_service` | `bodeul_core_service` |
| DB role 연결 상한 | 5 | 5 |
| 프로세스 연결 pool | 1 | 인스턴스당 2 |
| 외부 API | 관리자 전용 후속 연동 | Kakao Local 등 사용자 서비스 연동 |

DDL은 메인 저장소 `core-api/`의 Flyway migration만 소유한다. 런타임 계정에는 필요한 DML만 부여하고 migration 자격 증명을 전달하지 않는다. 브라우저와 APK에는 PostgreSQL 접속 문자열을 넣지 않는다.

## 현재 도달 상태

- Spring Core API Cloud Run preview, WIF 배포, Secret Manager, DB 연결과 rollback을 검증했다.
- 관리자 Next.js Preview에서 Firebase token과 PostgreSQL 관리자 role을 사용한 401·403·200을 검증했다.
- 관리자 DB role은 읽기 전용이고 Preview 환경에만 자격 증명을 두었다.
- Node API 프로토타입과 메인 저장소 관리자 웹 중복본은 제거했다.
- Android의 Kakao Local REST 직접 호출과 REST 키를 제거했다.
- 개발 환경의 예약·동행 세션·리포트·후속 처리는 PostgreSQL을 쓰기 source of truth로 사용하고, Firestore Rules는 해당 업무 쓰기를 거부한다.
- 개발 DB의 채팅·위치는 PostgreSQL V8·V9 schema, 최소 권한과 보관 계약까지 적용했으며 Core API·Realtime·Android 경로 전환 전까지 Firestore legacy 쓰기를 제한적으로 유지한다.
- production Google Cloud/Firebase `bodeul-prod-110`과 Supabase `bodeul-prod`를 개발 환경과 분리했다.
- production Flyway V1~V3, 최소 권한 role, Artifact Registry, WIF와 DB Secret Manager version을 검증했다.
- production PostgreSQL dump를 격리 PostgreSQL 17에 복원해 schema, row 수, owner, ACL, RLS, 인덱스와 제약 일치를 검증했다.
- production Supabase 조직은 아직 Free이며, 실제 사용자 데이터 투입 전 Pro 전환이 필요하다.
- Kakao 운영 키, 첫 Cloud Run revision과 Vercel Production 관리자 DB는 아직 연결하지 않았다.

## 대안과 판단

| 대안 | 장점 | 현재 판단 |
| --- | --- | --- |
| 하나의 Spring 서버가 관리자와 사용자를 모두 처리 | 서버 수가 적다. | 권한·배포 경계가 다시 결합되므로 채택하지 않는다. |
| Next.js가 Spring을 거쳐 DB 접근 | DB 진입점이 하나다. | 관리자 요청에 불필요한 hop과 장애 지점이 생겨 채택하지 않는다. |
| 클라이언트가 Supabase Data API 직접 사용 | 구현이 빠르다. | 서버 비밀값과 복합 인가를 통제하기 어려워 채택하지 않는다. |
| Firebase 전체 즉시 제거 | 기술 스택이 단순해진다. | Auth·FCM·Storage·실시간 기능 전환 비용이 현재 규모에 비해 커서 채택하지 않는다. |
| Cloud Run WebSocket과 Redis 직접 운영 | 실시간 계층을 완전히 소유한다. | 재연결, 다중 인스턴스 동기화와 상시 운영 비용이 커 초기에는 채택하지 않는다. |
| Firestore와 PostgreSQL을 장기 이중 원본으로 유지 | 기존 기능 변경이 적다. | 정합성·복구·권한 기준이 분기되므로 최종 구조로 허용하지 않는다. |

## production 전환 조건

1. [x] 개발과 분리된 Google Cloud/Firebase 프로젝트와 Supabase 프로젝트를 만들고 기존 Vercel 프로젝트의 Production 환경을 사용한다.
2. [x] production migration과 Core API runtime 자격 증명을 별도 Environment/Secret Manager에 둔다.
3. [ ] Kakao 운영 키를 등록하고 첫 Cloud Run revision의 인증·DB·rollback을 검증한다.
4. [ ] Vercel Production에 SELECT-only 관리자 DB 자격 증명을 연결하고 관리자 401·403·200을 검증한다.
5. [ ] custom domain, Firebase Auth authorized domain과 App Check provider/enforcement를 검증한다.
6. [x] PostgreSQL backup/restore를 격리 환경에서 리허설한다.
7. [x] 도메인별 source of truth, 이중 쓰기 금지와 rollback 기준을 문서화한다.
8. [x] 월 비용 한도, 데이터 보관 기간과 목표 전환일을 확정한다.
9. [ ] Cloud Run과 Vercel 직전 revision rollback을 리허설한다.
10. [ ] 관리자 감사 이력과 실명 장애 대응 담당 2명을 확정한다.
11. [ ] 2026-12-15 Go/No-Go 게이트를 통과한다.

## 리스크

- Firestore와 PostgreSQL 병행 기간에는 데이터 불일치가 생길 수 있다.
- 두 서버가 같은 테이블을 다르게 해석하면 계약이 분기될 수 있다.
- Vercel과 Cloud Run의 연결 수가 DB 한도를 잠식할 수 있다.
- Firebase role과 PostgreSQL role 동기화가 늦으면 접근 판단이 달라질 수 있다.
- Realtime 이벤트가 누락되거나 중복되면 화면 상태가 일시적으로 어긋날 수 있다.

이를 줄이기 위해 도메인별 한 개의 source of truth, 공용 migration, 서버별 최소 권한 role, 연결 상한과 결과 비교를 유지한다. Realtime 이벤트는 화면 갱신 신호로만 취급하고 재연결 후 서버 API로 최신 상태를 다시 조회한다.

## 관련 문서

- [현재 인프라 구성도](infra-overview.md)
- [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
- [Issue 159 Node API 종료 기록](../reports/issue-159-node-api-retirement-audit-2026-07-16.md)
- [관리자 웹 환경 기준](../operations/admin-web-environments.md)
- [Production 인프라 기본값](../operations/production-infrastructure-defaults.md)
- [2026년 Production 운영 전환 계획](../operations/production-transition-plan-2026.md)
- [데이터 보관 및 파기 정책](../operations/data-retention-policy.md)
- [Production 인프라 구축 기록](../reports/production-infrastructure-bootstrap-2026-07-17.md)
