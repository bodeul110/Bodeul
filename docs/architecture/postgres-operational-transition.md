# PostgreSQL 운영 전환 결정

기준일: 2026-07-02

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

멘토 피드백 이후 BoDeul 운영 저장소를 Firebase Firestore 단독 구조에서 PostgreSQL 기반 운영 DB 구조로 단계적으로 전환한다. 목표는 Firebase를 모두 제거하는 것이 아니라, Firebase가 잘 맡는 인프라 역할은 유지하고 예약, 매칭, 관리자 운영, 정산, 통계처럼 관계형 모델이 필요한 데이터를 PostgreSQL 중심으로 옮기는 것이다.

## 현재 결론

운영 전환 기준은 `Firebase 인프라 유지 + Supabase PostgreSQL 운영 DB 전환 + bodeul-api 서버 경계`다.

| 영역 | 전환 결정 |
| --- | --- |
| Auth | Firebase Auth 유지. API 서버와 마이그레이션 도구는 Firebase ID token 또는 Firebase UID를 기준으로 사용자를 연결한다. |
| FCM | 유지. 푸시 전송은 DB 전환과 분리한다. |
| Storage | 유지. 매니저 서류와 채팅 첨부 원본은 Storage에 두고, 메타데이터와 심사 이력만 PostgreSQL 전환 후보로 둔다. |
| Hosting | 관리자 웹 Firebase Hosting 유지. 별도 레포 분리는 데이터/API/배포 계약 고정 이후 진행한다. |
| Functions | FCM, Kakao/Naver custom token, Firebase 인프라 보조 작업은 유지한다. |
| 운영 트랜잭션 DB | Supabase PostgreSQL을 1차 운영 DB 후보로 둔다. |
| API 서버 | `bodeul-api`를 PostgreSQL 접근, Firebase token 검증, 관리자/민감 작업 검증 경계로 둔다. |
| Firestore | 도메인별 전환 전까지 source of truth로 유지한다. 전환된 도메인에서는 읽기 호환 또는 shadow 저장소로 낮춘다. |

## 현재 진행 상태

| 단계 | 상태 | GitHub 근거 | 문서/코드 근거 |
| --- | --- | --- | --- |
| PostgreSQL 전환 기준 결정 | 완료 | [#89](https://github.com/bodeul110/Bodeul/pull/89), [#90](https://github.com/bodeul110/Bodeul/pull/90) | 이 문서, [PostgreSQL API 경계 기준](postgres-api-boundary.md) |
| Supabase 개발 DB 준비 | 완료 | [#87](https://github.com/bodeul110/Bodeul/issues/87), [#101](https://github.com/bodeul110/Bodeul/pull/101) | [PostgreSQL seed dry-run 기준 기록](../reports/postgres-seed-dry-run-plan-2026-06-29.md) |
| API 서버 골격 | 완료, 이슈 #88 종료 | [#109](https://github.com/bodeul110/Bodeul/pull/109), [#88](https://github.com/bodeul110/Bodeul/issues/88) | `api/`, [Issue 88 보고서](../reports/issue-88-api-skeleton-2026-06-30.md) |
| Firebase Admin SDK 인증 | 완료 | [#114](https://github.com/bodeul110/Bodeul/pull/114), [#110](https://github.com/bodeul110/Bodeul/issues/110) | `api/src/firebase-admin.ts` |
| PostgreSQL client | 완료 | [#115](https://github.com/bodeul110/Bodeul/pull/115), [#111](https://github.com/bodeul110/Bodeul/issues/111) | `api/src/database.ts` |
| 관리자 role 인가 | 완료 | [#116](https://github.com/bodeul110/Bodeul/pull/116), [#112](https://github.com/bodeul110/Bodeul/issues/112) | `api/src/authorization.ts` |
| 첫 read API | API 서버와 관리자 웹 병원 가이드 1차 연결 완료, 이슈 #113 종료 | [#117](https://github.com/bodeul110/Bodeul/pull/117), [#121](https://github.com/bodeul110/Bodeul/pull/121), [#113](https://github.com/bodeul110/Bodeul/issues/113) | `api/src/hospital-guides.ts`, `admin-web/src/components/HospitalGuideApiPanel.tsx`, [관리자 API 계약](admin-api-contract.md) |
| API 환경 설정 | 후속 진행 | [#122](https://github.com/bodeul110/Bodeul/issues/122) | `BODEUL_API_ALLOWED_ORIGINS`, `VITE_BODEUL_API_BASE_URL`, `VITE_BODEUL_DATA_BACKEND` |
| API 응답 비교 | 로컬 API 라우트 비교 완료, 라이브 API 호출 검증 남음 | [#123](https://github.com/bodeul110/Bodeul/issues/123) | [병원 가이드 Firestore/API 응답 비교 기록](../reports/hospital-guide-firestore-api-comparison-2026-07-06.md) |
| API 배포 인프라 | 미정 | 별도 결정 필요 | Oracle VM 또는 동등 실행 환경, `api-preview`/`api-production` Environment 결정 필요 |

## 선택한 방식

1. 같은 도메인의 source of truth는 하나만 둔다.
2. Firebase Auth와 Supabase Auth를 동시에 운영하지 않는다. 인증 기준은 Firebase Auth로 고정한다.
3. Android 앱과 관리자 웹은 PostgreSQL에 직접 접속하지 않는다.
4. API 서버는 Firebase 대체 서버가 아니라 DB 접근, Firebase token 검증, 관리자/민감 쓰기 검증 경계로 시작한다.
5. FCM, Storage, Hosting은 전환 전후에도 유지 후보로 본다.
6. Supabase Realtime은 관리자 알림처럼 빈도가 예측 가능한 구독부터 검토한다.
7. 실시간 위치 공유처럼 쓰기 빈도가 높은 기능은 마지막에 부하 테스트 후 Firestore 유지, Supabase Realtime, 서버 WebSocket/SSE 중 하나를 결정한다.

## 명칭 기준

| 항목 | 명칭 |
| --- | --- |
| API 서비스명 | `bodeul-api` |
| API 서버 디렉터리 | `api/` |
| 개발 API VM 후보 | `bodeul-dev-api-01` |
| 운영 API VM 후보 | `bodeul-prod-api-01` |
| Supabase 개발 프로젝트 | `bodeul-dev-rdb` |
| Supabase 운영 프로젝트 후보 | `bodeul-prod-rdb` |
| PostgreSQL 기본 스키마 | `public` |
| API 개발 GitHub Environment 후보 | `api-preview` |
| API 운영 GitHub Environment 후보 | `api-production` |

`bodeul-dev-api-01`과 `api-preview`는 API 배포 작업이 실제로 시작될 때 만든다. DB schema, import dry-run, 비교 리포트만 진행하는 동안에는 Oracle VM과 API 배포 secret을 만들지 않는다.

## 대안 비교

| 대안 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| Firebase 전체 유지 | 구현 변경이 가장 적고 실시간 기능, Auth, FCM, Storage가 이미 연결되어 있다. | 멘토가 요구한 RDBMS 운영 기준, 정산/통계/검색 확장, 마이그레이션 기준을 보여주기 어렵다. | 인프라는 유지하되 운영 DB는 전환한다. |
| Supabase PostgreSQL만 추가 | PostgreSQL, 관리 콘솔, 백업, Realtime 검토가 가능하다. Firebase Auth를 유지한 채 DB 전환을 설명할 수 있다. | Firestore와 PostgreSQL 이중 운영 기간이 생긴다. Supabase Realtime/RLS 정책 학습이 필요하다. | 1차 선택이다. |
| Supabase 전체 전환 | Auth, DB, Storage, Realtime을 한 플랫폼으로 묶을 수 있다. | Firebase Auth, FCM, Storage, Functions, Android SDK 결합을 한 번에 흔든다. 릴리스와 운영 리스크가 크다. | 현재 규모에서는 제외한다. |
| Neon PostgreSQL | DB만 분리하기 깔끔하고 branching 장점이 있다. | 실시간 기능은 별도 서버 구현이 필요하다. 멘토의 실시간 질문에 대한 답이 약하다. | Supabase 한계가 확인되면 대안으로 검토한다. |
| Oracle VM에 PostgreSQL 직접 운영 | 인프라 제어권과 서버 운영 경험을 얻을 수 있다. | DB 백업, 보안 패치, 장애 대응을 팀이 직접 책임져야 한다. | 초기 DB로는 제외한다. API 서버 후보로만 둔다. |

## 선택 이유

- 멘토 피드백은 Firebase 인프라 전체 폐기가 아니라 DB와 서버 경계를 언제 RDBMS/API로 가져갈지에 대한 질문에 가깝다.
- 현재 프로젝트는 Firebase Auth, FCM, Storage, Hosting, Functions가 이미 앱과 운영 도구에 연결되어 있어 한 번에 제거하면 리스크와 QA 범위가 과도하게 커진다.
- 관리자 운영, 정산, 문의, 알림 전달 이력은 관계형 조회와 감사 요구가 커질 가능성이 높다.
- Supabase는 PostgreSQL 기반이고 Realtime Postgres Changes를 제공하므로 실시간 기능도 완전히 포기하지 않고 RDBMS 전환을 설명할 수 있다.
- Firebase ID token은 서버에서 검증할 수 있으므로 Firebase Auth를 유지하면서 PostgreSQL/API 경계를 둘 수 있다.

## 전환 순서

### 0단계: DB 전환 기준 고정

완료 상태다.

- Supabase 개발 프로젝트 `bodeul-dev-rdb` 생성
- PostgreSQL schema 초안 검증
- Firestore 백업 기반 seed 입력 JSON과 upsert SQL 생성
- Supabase 개발 DB seed 적용
- row count, FK, 주요 필드 spot check 통과

### 1단계: API 서버 경계 도입

완료 상태다.

- `api/` 디렉터리 추가
- Node 22 + TypeScript API 서버 골격
- `GET /healthz`
- Firebase Admin SDK ID token 검증
- PostgreSQL client 초기화
- PostgreSQL `app_users.role` 기반 관리자 인가
- `GET /admin/api-contract`
- `GET /admin/hospital-guides`

### 2단계: 관리자 웹 read API 전환

1차 완료 상태다.

대상:

- 병원 가이드 목록: `api` 모드 검증 화면 연결 완료
- 매니저 서류 심사 메타데이터: 후속 후보
- 문의/후속 처리 조회: 후속 후보

원칙:

- 관리자 웹에 `VITE_BODEUL_DATA_BACKEND=firebase|api` 전환 경로를 둔다.
- 병원 가이드 검증 화면은 `VITE_BODEUL_DATA_BACKEND=api`와 `VITE_BODEUL_API_BASE_URL`이 있을 때만 API를 호출한다.
- API 장애 시 기본값 `firebase`로 되돌린다.
- Firestore 응답과 PostgreSQL/API 응답 비교는 #123에서 기록한다.

### 3단계: 관리자 write API 전환

후속 상태다.

대상:

- 병원 가이드 생성/수정
- 관리자 문의/후속 처리 상태 변경
- 매니저 서류 심사 메타데이터 변경

원칙:

- 특정 도메인을 PostgreSQL source of truth로 전환하면 해당 도메인의 Firestore 쓰기는 중단하거나 shadow write로 낮춘다.
- 관리자 작업 감사 로그는 PostgreSQL에 남긴다.
- rollback은 feature flag와 import 비교 기준으로 수행한다.

### 4단계: 예약/세션/리포트 전환

후속 상태다.

Android 앱 영향이 크므로 관리자 웹 전환보다 늦게 진행한다.

### 5단계: 실시간/알림 재설계

후속 상태다.

실시간 위치, 보호자 상태 업데이트, 관리자 알림 피드, FCM 발송 큐는 Firestore 유지, Supabase Realtime, 서버 WebSocket/SSE 중 부하 테스트 후 결정한다.

## 현재 리스크

| 리스크 | 대응 |
| --- | --- |
| Firestore와 PostgreSQL 이중 운영 기간에 데이터 불일치가 생길 수 있다. | 도메인별 source of truth를 명확히 하고 비교 리포트를 만든다. |
| 관리자 웹 API 환경값이 어긋나면 브라우저 preflight에서 막힌다. | #122에서 `BODEUL_API_ALLOWED_ORIGINS`와 `VITE_BODEUL_API_BASE_URL`을 환경별로 맞춘다. |
| Firestore와 PostgreSQL 병원 가이드 데이터가 어긋날 수 있다. | #123에서 count와 주요 필드 spot check를 기록한다. |
| API 배포 위치가 아직 없다. | Oracle VM 또는 동등 실행 환경을 별도 이슈로 결정한다. |
| API secret이 공개 GitHub 문맥에 노출될 수 있다. | `DATABASE_URL`, service account JSON, service role key는 GitHub Environment secret 또는 서버 비공개 설정에만 둔다. |
| `uuid` 전이 취약점 경고가 남아 있다. | #103에서 functions/tools/firebase/api 영향 범위를 나눠 Dependabot/수동 업데이트를 판단한다. |

## 멘토에게 설명할 답변

Firebase를 모두 제거하지 않는다. Auth, FCM, Storage, Hosting처럼 현재 앱과 운영 도구에 안정적으로 연결된 인프라는 유지한다. 대신 운영 데이터 중 관계형 조회, 관리자 처리 이력, 정산, 통계처럼 RDBMS가 더 설명하기 쉬운 영역은 Supabase PostgreSQL로 옮긴다. 클라이언트는 DB에 직접 붙지 않고 `bodeul-api`를 통해 접근한다. 이 API는 Firebase 대체 서버가 아니라 PostgreSQL 접근, Firebase token 검증, 관리자 권한 검증을 담당하는 얇은 경계다.

## 관련 문서와 이슈

- [PostgreSQL API 경계 기준](postgres-api-boundary.md)
- [관리자 API 초기 응답 계약](admin-api-contract.md)
- [PostgreSQL seed dry-run 기준 기록](../reports/postgres-seed-dry-run-plan-2026-06-29.md)
- [Issue #87 Supabase 개발 DB 준비 및 API 경계 검토](https://github.com/bodeul110/Bodeul/issues/87)
- [Issue #88 bodeul-api 서버 골격 추가](https://github.com/bodeul110/Bodeul/issues/88)
- [Issue #113 bodeul-api 1차 read API 실제 데이터 조회 연결](https://github.com/bodeul110/Bodeul/issues/113)
- [Issue #122 관리자 웹 API 환경변수와 CORS origin 설정 확정](https://github.com/bodeul110/Bodeul/issues/122)
- [Issue #123 병원 가이드 Firestore/API 응답 비교 기록](https://github.com/bodeul110/Bodeul/issues/123)
