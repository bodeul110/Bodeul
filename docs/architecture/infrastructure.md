# 인프라 개요

기준일: 2026-07-12

이 문서는 현재 `BoDeul` 프로젝트가 어떤 실행 구성으로 동작하는지 빠르게 파악하기 위한 인프라 기준 문서다. 화면 설계나 기능 범위는 기능설명서와 구현 상태 문서를 따르고, 이 문서는 런타임 구성과 운영 경계를 설명한다.

## 1. 현재 구성 요약

```text
Android 앱(app/)
  ├─ Firebase Auth
  ├─ Firestore
  ├─ Firebase Storage
  ├─ Cloud Functions
  └─ Google/Kakao 로그인, Kakao 지도/Local API

관리자 웹(admin-web/)
  ├─ Firebase Auth
  ├─ Firestore
  ├─ Firebase Storage
  ├─ Firebase Hosting preview workflow
  ├─ Vercel/Firebase preview API 모드 후속 검증 후보
  └─ bodeul-api 병원 가이드 API 검증 경로

bodeul-api(api/)
  ├─ Node 22 + TypeScript
  ├─ Firebase Admin SDK ID token 검증
  ├─ PostgreSQL client(pg)
  ├─ PostgreSQL app_users.role 기반 ADMIN 인가
  └─ 병원 가이드 read API

Firebase 인프라
  ├─ Auth
  ├─ Firestore
  ├─ Storage
  ├─ Cloud Functions v2
  ├─ FCM
  └─ Hosting

Supabase PostgreSQL
  ├─ 개발 DB: bodeul-dev-rdb
  ├─ Firestore 백업 기반 seed 적용 검증 완료
  └─ API read 전환 후보 테이블 운영

운영 도구(tools/firebase/)
  ├─ 상태 점검
  ├─ 백업/복원
  ├─ 운영 리포트/프리플라이트
  └─ PostgreSQL seed dry-run/build/sql/rollback

배포/검증
  └─ GitHub Actions(.github/workflows/)
```

현재 핵심 구조는 `Firebase 중심 BaaS + Supabase PostgreSQL 전환 경로 + Node bodeul-api prototype`이다.

Android 앱과 관리자 웹은 아직 대부분 Firebase를 직접 사용한다. PostgreSQL은 운영 DB 전환 대상으로 준비됐고, `bodeul-api`는 클라이언트가 PostgreSQL에 직접 접근하지 않도록 인증과 권한을 통제하는 얇은 서버 경계로 시작했다.

운영 목표는 관리자 웹을 Next.js 서버 애플리케이션으로 Vercel에 배포하고, 사용자 서비스는 Spring Core API를 OCI에 배포하는 것이다. 두 서버는 서로를 경유하지 않고 같은 Supabase PostgreSQL을 사용한다. 자세한 구조는 [목표 인프라 구조](target-infrastructure.md), 구축 절차는 [Spring Core API 인프라 런북](../operations/core-api-infrastructure-runbook.md)을 따른다.

## 2. 클라이언트 구성

### 2-1. Android 앱

- 위치: `app/`
- 기술 스택: `Java + XML`
- 화면 구조: `Activity -> Coordinator -> Binder -> ScreenModel/Formatter -> Repository`
- 역할:
  - 환자/보호자/매니저 사용자 흐름
  - 예약, 동행, 리포트, 알림, 문의, 서류 등록
  - 실시간 위치 확인, 채팅, Kakao 지도/Local API 연동

앱은 [`ServiceLocator.java`](../../app/src/main/java/com/example/bodeul/data/ServiceLocator.java)에서 실제 Firebase 구현과 Mock 구현을 분기한다.

### 2-2. 관리자 웹

- 위치: `admin-web/`
- 기술 스택: `React + Vite`
- 배포 기준: Firebase Hosting
- 역할:
  - 관리자 로그인
  - 매니저 서류 심사와 미리보기
  - 운영 상태 확인
  - 민감정보 마스킹과 유휴 세션 종료

관리자 웹은 [`firebase.ts`](../../admin-web/firebase.ts)에서 Firebase App/Auth/Firestore/Storage를 초기화한다. 기본 데이터 경로는 Firebase지만, `VITE_BODEUL_DATA_BACKEND=api` 환경에서는 병원 가이드 검증 화면이 `bodeul-api`를 호출한다. #140/#123 댓글 기준으로 Oracle API URL과 로컬 관리자 웹 API 모드 검증은 통과했고, Vercel 또는 Firebase Hosting preview URL 기반 팀 공유 화면 검증은 후속 작업으로 분리한다.

## 3. 서버/API 구성

### 3-1. 기존 bodeul-api prototype

- 위치: `api/`
- 기술 스택: `Node 22 + TypeScript`
- 검증 명령: `npm --prefix api run check`
- GitHub Actions: `.github/workflows/api.yml`
- 현재 엔드포인트:
  - `GET /healthz`
  - `HEAD /healthz`
  - `GET /admin/api-contract`
  - `HEAD /admin/api-contract`
  - `GET /admin/hospital-guides`

`bodeul-api`는 Firebase 전체 대체 서버가 아니다. 현재 목적은 PostgreSQL 접근, Firebase ID token 검증, 관리자 권한 검증, 낮은 위험 read API 전환을 검증하는 것이다. Spring Core API와 Next.js 관리자 서버가 같은 계약을 구현하면 운영 후보에서 제외한다.

### 3-2. 목표 서버 구성

| 서버 | 배포 | 책임 |
| --- | --- | --- |
| Next.js 관리자 서버 | Vercel | 관리자 인증·인가, 관리자 조회와 운영 처리, 관리자 DB role 사용 |
| Spring Core API | OCI | 예약·매칭·세션·리포트, 사용자 조회와 쓰기, Kakao 서버 API proxy |

두 서버는 서로를 거쳐 DB에 접근하지 않는다. DB schema migration은 한 곳에서 소유하고 runtime role은 분리한다.

### 3-3. 인증과 권한

관리자 API는 `Authorization: Bearer <Firebase ID token>` 헤더를 사용한다.

| 단계 | 기준 |
| --- | --- |
| token 검증 | Firebase Admin SDK `verifyIdToken` |
| 사용자 연결 | token의 `uid`와 PostgreSQL `app_users.firebase_uid` |
| 관리자 권한 | PostgreSQL `app_users.role == 'ADMIN'` |
| 권한 없음 | 403 `admin_role_required` |
| DB/인가 설정 없음 | 503 `authorization_not_configured` |

API 서버 환경변수는 다음 기준으로 둔다.

| 이름 | 용도 | 비고 |
| --- | --- | --- |
| `DATABASE_URL` | Supabase PostgreSQL 연결 문자열 | 문서, 이슈, PR 본문에 원문을 남기지 않는다. |
| `FIREBASE_PROJECT_ID` | ADC 사용 시 Firebase project 지정 | 서비스 계정 JSON이 없을 때 사용 가능 |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase Admin SDK 서비스 계정 JSON | GitHub Environment secret 또는 서버 비공개 설정으로만 주입 |

### 3-4. PostgreSQL client

현재 DB client는 `pg` pool을 직접 사용한다.

| 항목 | 현재 값 |
| --- | --- |
| pool max | `5` |
| idle timeout | `10000ms` |
| connection timeout | `5000ms` |
| 연결 확인 실패 응답 | `db_connection_failed` |
| 비밀값 노출 방침 | connection string 원문을 응답이나 로그에 남기지 않는다. |

ORM은 아직 도입하지 않았다. 현재 범위에서는 SQL과 DTO를 작게 유지하고, 쿼리와 마이그레이션 범위가 커질 때 Drizzle/Prisma 같은 도입 여부를 다시 판단한다.

## 4. Firebase 구성

### 4-1. Authentication

- 사용자 로그인, 세션, Firebase ID token 발급 기준이다.
- Android 앱과 관리자 웹은 Firebase Auth를 계속 사용한다.
- `bodeul-api`도 Firebase Auth token을 서버에서 검증한다.

### 4-2. Firestore

현재 Android 앱과 관리자 웹의 주요 운영 데이터 source of truth다.

대표 컬렉션:

- `users`
- `appointmentRequests`
- `companionSessions`
- `sessionReports`
- `appointmentFollowUps`
- `supportInquiries`
- 관리자 운영 컬렉션

PostgreSQL 전환 중에도 도메인별 전환 조건이 충족되기 전까지 Firestore를 유지한다.

### 4-3. Firebase Storage

- 매니저 서류 원본과 채팅 첨부 원본을 저장한다.
- 대표 경로:
  - `manager-documents/{managerUserId}/{documentKey}/{fileName}`
  - `companion-chat-attachments/{sessionId}/{timestamp-fileName}`

PostgreSQL 전환 대상은 원본 파일 자체가 아니라 메타데이터, 심사 이력, 감사 이력이다.

### 4-4. Cloud Functions

- 위치: `functions/`
- 런타임: Node 22
- 기본 리전: `asia-northeast3`
- 역할:
  - Kakao/Naver custom token 발급
  - 연결 사용자 조회 보조
  - FCM 알림
  - 예약 리마인더와 관리자 액션 전달 작업
  - Firestore 동기화 보조

Cloud Functions는 FCM, Storage 보조, Firebase 인프라와 가까운 작업을 계속 담당한다. PostgreSQL read/write 경계는 `bodeul-api`로 분리한다.

## 5. Supabase PostgreSQL 구성

### 5-1. 현재 상태

- 개발 프로젝트명: `bodeul-dev-rdb`
- 운영 프로젝트명 후보: `bodeul-prod-rdb`
- GitHub Issue #87과 PR #101 기준으로 개발 DB schema 적용, Firestore 백업 기반 seed, row count, FK spot check를 완료했다.
- 병원 가이드 read API는 PostgreSQL `hospital_guides`를 조회한다.

### 5-2. 전환 원칙

- 클라이언트는 PostgreSQL connection string을 직접 사용하지 않는다.
- PostgreSQL 접근은 `bodeul-api`나 운영 도구에서만 수행한다.
- 특정 도메인의 source of truth를 PostgreSQL로 바꾸기 전에는 Firestore와 PostgreSQL 결과 비교, rollback 기준, write 경계가 필요하다.
- 실시간 위치처럼 쓰기 빈도가 높은 기능은 마지막에 부하 테스트 후 결정한다.

## 6. 운영 도구 구성

위치: `tools/firebase/`

주요 역할:

- Firestore 상태 점검
- 매니저 서류 Storage 정합성 점검
- 백업/복원
- 운영 리포트 생성
- 로컬/CI 프리플라이트
- Firestore/Storage Rules emulator 테스트
- PostgreSQL seed dry-run/build/sql/rollback

PostgreSQL 관련 명령:

| 명령 | 용도 |
| --- | --- |
| `npm --prefix tools/firebase run postgres:seed:dry-run` | Firestore 백업을 PostgreSQL seed 후보로 변환하기 전 row count와 필수 필드 누락 점검 |
| `npm --prefix tools/firebase run postgres:seed:build` | PostgreSQL seed 입력 JSON 생성 |
| `npm --prefix tools/firebase run postgres:seed:sql` | Supabase SQL Editor 또는 psql 검토용 upsert SQL 생성 |
| `npm --prefix tools/firebase run postgres:seed:rollback` | seed 적용 rollback SQL 생성 |

쓰기 작업은 dry-run을 먼저 사용하고, apply 또는 SQL 실행은 명시적 요청과 대상 환경 확인 후 수행한다.

## 7. 배포와 검증

### 7-1. 관리자 웹

- 현재 검증 경로: Firebase Hosting preview workflow
- API 모드 실연동 검증: Oracle API + 로컬 관리자 웹 API 모드 1차 통과, Vercel/Firebase preview URL 검증은 후속 후보
- Firebase project: `.firebaserc` 기준 `bodeul-dev`
- Hosting public: `admin-web/dist`
- dev live URL 기준: `https://bodeul-dev.web.app`
- preview workflow: `.github/workflows/admin-web-preview-deploy.yml`
- build workflow: `.github/workflows/admin-web.yml`

관리자 웹 Firebase Hosting preview 배포는 GitHub Actions OIDC와 Google Cloud Workload Identity Federation을 사용한다. Firebase refresh token fallback은 제거된 상태다. production live 배포 기준은 #134에서 확정한다. #140에서는 Vercel preview를 직접 완료 범위에서 제외했으며, 이후 Vercel/Firebase preview URL을 쓰더라도 목적은 production 결정이 아니라 API 모드 팀 공유 검증으로 한정한다.

### 7-2. API

- workflow: `.github/workflows/api.yml`
- 트리거: `api/**`, workflow 파일 변경, 수동 실행
- CI Node 버전: 22
- 검증: `npm ci`, `npm run check`

최근 병합 PR #109, #114, #115, #116, #117, #121, #128, #136, #137, #138에서 관련 검증 workflow가 통과했다. 2026-07-08 GitHub 이슈 댓글 기준으로는 Oracle preview API의 `/healthz`, Supabase 조회, Firebase Admin 인증, 관리자 권한 API 호출, 로컬 관리자 웹 API 모드, 병원 가이드 응답 비교가 통과했다.

### 7-3. Android/Firebase/보안

| workflow | 역할 |
| --- | --- |
| `android-preflight.yml` | Android 빌드, Firebase 운영 도구 preflight, 선택적 self-hosted runner 실행 |
| `firebase-rules.yml` | Firestore/Storage Rules emulator 테스트 |
| `codeql.yml` | Android/Java/Kotlin, JavaScript/TypeScript CodeQL 분석 |

2026-07-02 확인 기준 GitHub code scanning open alert는 0건이었다. 이후 보안 의존성 이슈 #103은 PR #136 병합으로 종료됐다.

## 8. GitHub 기준 현재 상태

최근 인프라 관련 병합:

- #101 Supabase 개발 DB seed 검증 기준 추가
- #109 `bodeul-api` 서버 골격 추가
- #114 Firebase Admin SDK 인증 연결
- #115 PostgreSQL client 초기화
- #116 관리자 role 기반 인가 추가
- #117 병원 가이드 read API 추가
- #121 관리자 웹 병원 가이드 API 연결

주의할 GitHub 상태:

- #88과 #113은 완료 근거를 남기고 종료했다.
- #122는 관리자 웹 API 환경변수와 CORS origin 설정 확정으로 종료했다.
- #123은 병원 가이드 Firestore/API 응답 비교 기록을 추적한다. 로컬 비교와 비교 도구는 반영됐고, 실제 배포 API 응답 비교는 2026-07-08 댓글 기준 `passed`로 기록됐다.
- #103 `uuid` 전이 취약점 검토는 PR #136 병합 후 종료했다.
- #134, #135, #140은 관리자 웹 production 기준, 레포 분리 실행 준비, Oracle/Supabase/Firebase Admin 실연동 검증과 Vercel preview 후속 분리를 각각 추적한다.
- #32, #64, #65, #66은 운영 전환 전 계속 추적해야 하는 인프라 이슈다.

## 9. 보안과 운영 기준

- 보안값, API key, `DATABASE_URL`, Firebase service account JSON, Supabase service role key는 저장소와 공개 GitHub 이슈/PR에 남기지 않는다.
- App Check는 Android와 관리자 웹 초기화 경로가 있으나 enforcement는 단계적으로 적용한다.
- Firestore/Storage Rules는 emulator 테스트와 영향 범위 문서화를 거쳐 배포한다.
- 운영 데이터 쓰기 작업은 dry-run과 백업을 먼저 수행한다.
- 관리자 API는 Firestore role이 아니라 PostgreSQL `app_users.role` 기준으로 검증한다.

## 10. 같이 봐야 할 문서

1. [현재 인프라 구성도](infra-overview.md)
2. [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
3. [PostgreSQL API 경계 기준](postgres-api-boundary.md)
4. [관리자 API 초기 응답 계약](admin-api-contract.md)
5. [PostgreSQL seed dry-run 기준 기록](../reports/postgres-seed-dry-run-plan-2026-06-29.md)
6. [인프라 운영 기준](../operations/infrastructure-operations-baseline.md)
7. [관리자 웹 GitHub Environment 기준](../operations/admin-web-environments.md)
