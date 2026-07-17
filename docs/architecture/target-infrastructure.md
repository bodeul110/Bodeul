# 목표 인프라 구조

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결정

관리자와 사용자 서버를 분리하고 공용 PostgreSQL만 공유한다.

- 관리자 브라우저 → Vercel Next.js 관리자 서버 → Supabase PostgreSQL
- 환자·보호자·매니저 웹/앱 → Cloud Run Spring Core API → Supabase PostgreSQL
- 관리자 Vercel Functions, Cloud Run과 Supabase는 Tokyo 리전에 맞춘다.
- Firebase Auth, FCM, Storage와 Firebase 결합 Functions는 유지한다.
- 두 서버는 서로를 proxy로 호출하지 않는다.
- Kakao Local REST와 서버 비밀값이 필요한 외부 연동은 Spring Core API가 소유한다.

## 선택 이유

관리자 화면은 운영 권한과 배포 주기가 사용자 서비스와 다르다. Next.js 서버가 관리자 전용 DB role을 직접 사용하면 불필요한 서버 간 hop과 CORS 경계를 만들지 않으면서 권한을 분리할 수 있다. 사용자 서비스는 Java/Spring으로 도메인 계약과 외부 API를 한 곳에 모으고 Cloud Run에서 독립 배포한다.

현재 MVP 규모에서는 인증·푸시·파일까지 한 번에 교체하는 비용이 크므로 Firebase를 유지한다. 관계형 무결성, 감사 이력, 정산·통계에 유리한 데이터부터 PostgreSQL로 옮긴다.

## 서버와 DB 경계

| 범위 | 관리자 서버 | Core API |
| --- | --- | --- |
| 런타임 | Vercel Next.js | Google Cloud Run Spring Boot |
| 인증 | Firebase ID token | Firebase ID token |
| 인가 | PostgreSQL `ADMIN` role | 사용자·보호자·매니저 role |
| DB 접속 | `bodeul_admin_service` | `bodeul_core_service` |
| 연결 상한 | 5 | 5 |
| 외부 API | 관리자 전용 후속 연동 | Kakao Local 등 사용자 서비스 연동 |

DDL은 메인 저장소 `core-api/`의 Flyway migration만 소유한다. 런타임 계정에는 필요한 DML만 부여하고 migration 자격 증명을 전달하지 않는다. 브라우저와 APK에는 PostgreSQL 접속 문자열을 넣지 않는다.

## 현재 도달 상태

- Spring Core API Cloud Run preview, WIF 배포, Secret Manager, DB 연결과 rollback을 검증했다.
- 관리자 Next.js Preview에서 Firebase token과 PostgreSQL 관리자 role을 사용한 401·403·200을 검증했다.
- 관리자 DB role은 읽기 전용이고 Preview 환경에만 자격 증명을 두었다.
- Node API 프로토타입과 메인 저장소 관리자 웹 중복본은 제거했다.
- Android의 Kakao Local REST 직접 호출과 REST 키를 제거했다.
- 예약 요청은 PostgreSQL read model 백필까지 진행했지만 쓰기 source of truth는 아직 Firestore다.

## 대안과 판단

| 대안 | 장점 | 현재 판단 |
| --- | --- | --- |
| 하나의 Spring 서버가 관리자와 사용자를 모두 처리 | 서버 수가 적다. | 권한·배포 경계가 다시 결합되므로 채택하지 않는다. |
| Next.js가 Spring을 거쳐 DB 접근 | DB 진입점이 하나다. | 관리자 요청에 불필요한 hop과 장애 지점이 생겨 채택하지 않는다. |
| 클라이언트가 Supabase Data API 직접 사용 | 구현이 빠르다. | 서버 비밀값과 복합 인가를 통제하기 어려워 채택하지 않는다. |
| Firebase 전체 즉시 제거 | 기술 스택이 단순해진다. | Auth·FCM·Storage·실시간 기능 전환 비용이 현재 규모에 비해 커서 채택하지 않는다. |

## production 전환 조건

1. 개발과 분리된 Google Cloud/Firebase 프로젝트와 Supabase 프로젝트를 만들고 기존 Vercel 프로젝트의 Production 환경을 사용한다.
2. production migration과 runtime 자격 증명을 별도 Environment/Secret Manager에 둔다.
3. custom domain, Firebase Auth authorized domain, App Check enforcement를 검증한다.
4. backup/restore와 직전 revision rollback을 production 유사 환경에서 리허설한다.
5. 도메인별 source of truth, 이중 쓰기 금지와 rollback 기준을 문서화한다.
6. 비용 알림, 로그 보존, 관리자 감사 이력과 장애 대응 담당을 확정한다.

## 리스크

- Firestore와 PostgreSQL 병행 기간에는 데이터 불일치가 생길 수 있다.
- 두 서버가 같은 테이블을 다르게 해석하면 계약이 분기될 수 있다.
- Vercel과 Cloud Run의 연결 수가 DB 한도를 잠식할 수 있다.
- Firebase role과 PostgreSQL role 동기화가 늦으면 접근 판단이 달라질 수 있다.

이를 줄이기 위해 도메인별 한 개의 source of truth, 공용 migration, 서버별 최소 권한 role, 연결 상한과 결과 비교를 유지한다.

## 관련 문서

- [현재 인프라 구성도](infra-overview.md)
- [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
- [Issue 159 Node API 종료 기록](../reports/issue-159-node-api-retirement-audit-2026-07-16.md)
- [관리자 웹 환경 기준](../operations/admin-web-environments.md)
- [Production 인프라 기본값](../operations/production-infrastructure-defaults.md)
