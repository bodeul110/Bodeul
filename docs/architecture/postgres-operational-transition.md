# PostgreSQL 운영 전환 결정

기준일: 2026-06-26

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

멘토 피드백 이후 BoDeul 운영 저장소를 Firebase Firestore 단독 구조에서 PostgreSQL 기반 운영 DB 구조로 전환한다. 목표는 “Firebase를 모두 제거”가 아니라, Firebase가 잘 맡는 인프라 역할은 유지하고 예약, 매칭, 관리자 운영, 정산, 통계처럼 관계형 모델이 필요한 데이터를 PostgreSQL 중심으로 옮기는 것이다.

## 결론

운영 전환 기준은 `Firebase 인프라 유지 + Supabase PostgreSQL 운영 DB 전환`이다.

| 영역 | 전환 결정 |
| --- | --- |
| Auth | Firebase Auth 유지. API 서버나 마이그레이션 도구는 Firebase ID token을 검증한다. |
| FCM | Firebase Cloud Messaging 유지. 앱 푸시는 제거 대상이 아니다. |
| Storage | 매니저 서류 원본과 첨부 원본은 Firebase Storage 유지. 메타데이터와 심사 이력은 PostgreSQL로 이전한다. |
| Hosting | 관리자 웹 Firebase Hosting 유지. 별도 레포 분리와 운영 Hosting은 별도 결정으로 관리한다. |
| Functions | FCM, Kakao Auth, Storage 보조, 마이그레이션 보조처럼 Firebase 인프라에 가까운 역할은 유지한다. |
| 운영 트랜잭션 DB | Supabase PostgreSQL을 1순위 운영 DB로 둔다. |
| API 서버 | PostgreSQL 접근, 서버 검증, 관리자 쓰기 작업에 필요한 얇은 경계로 둔다. Oracle VM은 API 경계가 필요해지는 시점에 사용한다. |
| Firestore | 전환 전 기준 데이터, 호환 읽기, 캐시, shadow 저장소로 낮춘다. 전환 완료 도메인에서는 source of truth가 아니다. |

## 혼용 원칙

1. 같은 도메인의 source of truth는 하나만 둔다.
2. Firebase Auth와 Supabase Auth를 동시에 운영하지 않는다. 인증의 기준은 Firebase Auth로 고정한다.
3. Android 앱과 관리자 웹은 PostgreSQL에 직접 접속하지 않는다.
4. API 서버는 전체 Firebase 대체 서버가 아니라 DB 접근 경계, Firebase token 검증, 관리자/민감 쓰기 검증을 담당한다.
5. FCM, Storage, Hosting은 운영 전환 후에도 유지 후보로 본다.
6. Supabase Realtime은 관리자 피드나 상태 변경처럼 빈도가 예측 가능한 구독에 먼저 검토한다.
7. 실시간 위치 공유처럼 쓰기 빈도가 높은 기능은 마지막에 부하 테스트 후 Firestore 유지, Supabase Realtime, 서버 WebSocket/SSE 중 하나를 결정한다.

## 명칭 기준

| 항목 | 명칭 |
| --- | --- |
| API 서비스명 | `bodeul-api` |
| API 서버 디렉터리 후보 | `api/` |
| 개발 API VM 후보 | `bodeul-dev-api-01` |
| 운영 API VM 후보 | `bodeul-prod-api-01` |
| Supabase 개발 프로젝트 | `bodeul-dev-rdb` |
| Supabase 운영 프로젝트 | `bodeul-prod-rdb` |
| PostgreSQL 기본 스키마 | `public` |
| 마이그레이션 작업 브랜치 접두어 | `codex/postgres-*` |
| GitHub Environment 개발 후보 | `api-preview` |
| GitHub Environment 운영 후보 | `api-production` |

`bodeul-dev-api-01`과 `api-preview`는 API 서버 작업이 실제로 시작될 때 쓴다. DB schema, import dry-run, 비교 리포트만 진행하는 동안에는 Oracle VM과 API 배포 secret을 만들지 않는다.

## 대안 비교

| 대안 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| Firebase 전체 유지 | 구현 변경이 가장 적고 실시간 기능, Auth, FCM, Storage가 이미 연결돼 있다. | 멘토가 요구한 RDBMS 운영 기준, 정산/통계/검색 확장, 마이그레이션 기준을 보여주기 어렵다. | 인프라는 유지하되 운영 DB는 전환한다. |
| Supabase PostgreSQL만 추가 | PostgreSQL, 관리 콘솔, 백업, Realtime 검토가 가능하다. Firebase Auth를 유지한 채 DB 전환을 설명할 수 있다. | Firestore와 PostgreSQL 이중 운영 기간이 생긴다. Supabase Realtime/RLS 정책 학습이 필요하다. | 1순위로 선택한다. |
| Supabase 전체 전환 | Auth, DB, Storage, Realtime을 한 플랫폼으로 묶을 수 있다. | Firebase Auth, FCM, Storage, Functions, Android SDK 결합을 한 번에 흔든다. 앱 릴리스와 운영 리스크가 크다. | 현재 규모에서는 제외한다. |
| Neon PostgreSQL | DB만 깔끔하게 분리할 수 있고 branching과 비용 예측 장점이 있다. | 실시간 기능은 별도 서버 구현이 필요해 멘토의 실시간 질문에 대한 답이 약하다. | Supabase 비용/Realtime 한계가 확인되면 대안으로 검토한다. |
| Oracle VM에 PostgreSQL 직접 운영 | 인프라 제어권이 높고 서버 운영 경험을 쌓을 수 있다. | DB 백업, 보안 패치, 장애 대응을 팀이 직접 책임져야 한다. 현재 팀에는 운영 리스크가 크다. | 초기 DB로는 제외한다. API 서버 후보로만 둔다. |

## 선택 이유

- 멘토 피드백은 “Firebase 인프라를 모두 버리라”가 아니라 “DB는 Supabase나 Neon 같은 RDBMS로 바꿔보라”는 방향에 가깝다.
- 현재 프로젝트는 Firebase Auth, FCM, Storage, Hosting, Functions가 이미 앱과 운영 도구에 연결돼 있어 한 번에 제거하면 릴리스와 QA 범위가 과도하게 커진다.
- 관리자 운영, 정산, 문의, 후속 처리, 알림 전달 이력은 관계형 조회와 집계 요구가 커질 가능성이 높다.
- Supabase는 PostgreSQL 기반이고 Realtime Postgres Changes를 제공하므로 실시간 기능을 완전히 포기하지 않고 RDBMS 전환을 설명할 수 있다.
- Firebase Auth ID token은 서버에서 검증할 수 있으므로, Firebase Auth를 유지하면서 PostgreSQL/API 경계를 둘 수 있다.
- FCM은 자체 서버나 Cloud Functions 같은 신뢰할 수 있는 서버 환경에서 계속 발송할 수 있으므로 DB 전환과 분리할 수 있다.

## 현재 Firebase 의존 범위

| 영역 | 현재 의존 | 전환 판단 |
| --- | --- | --- |
| 사용자 프로필/역할 | Firestore `users` | `app_users`로 이전하되 Firebase UID는 외부 식별자로 유지 |
| 예약 | Firestore `appointmentRequests` | `appointment_requests`로 이전 후보 |
| 동행 세션 | Firestore `companionSessions` | `companion_sessions`로 이전 후보 |
| 리포트 | Firestore `sessionReports` | `session_reports`로 이전 후보 |
| 병원 가이드 | Firestore `hospitalGuides` | 낮은 위험의 초기 전환 후보 |
| 문의 | Firestore `supportInquiries`, `clientSupportRequests` | `support_requests` 계열로 통합 후보 |
| 관리자 후속/알림/감사 | 여러 admin 컬렉션 | 운영 데이터 성격이 강하므로 PostgreSQL에 적합 |
| 매니저 서류 원본 | Firebase Storage | 원본은 유지, 메타데이터와 심사 이력만 PostgreSQL 이전 |
| 푸시 | FCM | 유지 |
| 관리자 웹 배포 | Firebase Hosting | 유지 |

## 전환 순서

### 0단계: DB 전환 기준 고정

- Supabase 개발 프로젝트 `bodeul-dev-rdb` 생성
- PostgreSQL schema 초안 검토
- Firestore 백업을 PostgreSQL seed 입력으로 변환하는 dry-run 도구 준비
- Firestore와 PostgreSQL 결과 비교 리포트 형식 정의
- API 서버가 필요한 도메인과 필요하지 않은 도메인 구분

이 단계에서는 Oracle VM을 필수로 만들지 않는다.

### 1단계: 읽기 전용 mirror와 비교

대상:

- `hospitalGuides`
- 관리자 조회용 매니저 서류 심사 메타데이터
- 관리자 문의/후속 처리 일부

원칙:

- Firestore 데이터를 PostgreSQL에 import하되 운영 쓰기는 Firestore에 남긴다.
- row count, 주요 필드, 상태값, 관리자 화면 결과를 비교한다.
- 비교 리포트가 통과하기 전에는 source of truth를 바꾸지 않는다.

### 2단계: API 경계 도입

대상:

- 관리자 웹의 낮은 위험 read API
- 관리자 서류 심사 메타데이터 조회
- 병원 가이드 조회

원칙:

- API 서버는 Firebase ID token을 검증한다.
- PostgreSQL role과 운영 권한을 확인한다.
- 관리자 웹은 `VITE_BODEUL_DATA_BACKEND=firebase|api` 플래그로 전환한다.
- 이 단계에서 Oracle VM 또는 동등한 서버 실행 환경을 선택한다.

### 3단계: 낮은 위험 write 이전

대상:

- 병원 가이드 생성/수정
- 관리자 문의/후속 처리 상태 변경
- 매니저 서류 심사 메타데이터

원칙:

- 특정 도메인을 PostgreSQL source of truth로 전환하면 해당 도메인의 Firestore 쓰기는 중단한다.
- 필요한 경우 Firestore shadow sync는 읽기 호환용으로만 둔다.
- rollback은 플래그와 import 비교 기준으로 수행한다.

### 4단계: 예약/세션 이전

대상:

- 예약 상세 조회
- 예약 생성/수정/취소
- 매칭
- 세션 진행 상태 변경
- 리포트 저장

원칙:

- Android 앱 릴리스가 필요하므로 관리자 웹 전환보다 늦게 진행한다.
- 전환 전후 API 응답 계약과 기존 Repository 모델을 비교한다.
- Firestore와 PostgreSQL 이중 쓰기 기간을 길게 두지 않는다.

### 5단계: 실시간/알림 재설계

대상:

- 실시간 위치
- 보호자 상태 업데이트
- 관리자 알림 피드
- FCM 발송 큐

판단 기준:

- 관리자 피드와 단순 상태 변경은 Supabase Realtime을 먼저 검토한다.
- 위치처럼 쓰기 빈도가 높은 데이터는 Firestore 유지, Supabase Realtime, 서버 WebSocket/SSE 중 하나를 부하 테스트 후 결정한다.
- FCM은 운영 앱 푸시에 계속 필요하므로 제거 대상이 아니다.

## 첫 운영 전환 범위

첫 실제 작업은 코드 전환보다 데이터 기준 검증에 둔다.

1. Supabase 개발 프로젝트 `bodeul-dev-rdb` 생성
2. PostgreSQL schema 초안 보완
3. Firestore 백업을 PostgreSQL seed JSON으로 변환하는 dry-run 도구
4. Firestore와 PostgreSQL 비교 리포트
5. 관리자 웹 매니저 서류 심사 read API 필요 여부 판단

첫 기능 전환 후보는 관리자 웹의 병원 가이드 또는 매니저 서류 심사 메타데이터다. Android 앱의 예약/세션 전환은 그 다음이다.

## 리스크

| 리스크 | 대응 |
| --- | --- |
| Firestore와 PostgreSQL 이중 운영 기간에 데이터 불일치가 생길 수 있다. | 도메인별 source of truth를 명확히 하고 비교 리포트를 만든다. |
| Android 앱과 관리자 웹이 Firestore를 직접 읽는 코드가 많다. | Repository/API client 계층을 먼저 만들고 화면 단위로 교체한다. |
| Auth를 바로 바꾸면 로그인과 권한이 동시에 흔들린다. | Firebase Auth 유지, API 서버에서 ID token 검증. |
| Supabase Realtime이 위치 공유 부하를 감당하지 못할 수 있다. | 위치 공유는 마지막 전환 대상으로 두고 부하 테스트 후 결정. |
| Oracle Free Tier는 용량/회수/가용성 리스크가 있다. | API 서버가 실제 필요해지는 시점에 배포/롤백 문서를 먼저 만든다. |
| Supabase DB secret이 클라이언트나 공개 GitHub 이슈에 노출될 수 있다. | DB connection string은 GitHub Environment secret 또는 로컬 비공개 설정에만 둔다. |

## 멘토님께 설명할 답변

멘토님 피드백을 기준으로 Firebase를 모두 제거하지 않고 역할을 나눴다. Auth, FCM, Storage, Hosting은 Firebase가 이미 안정적으로 맡고 있으므로 유지한다. 대신 운영 데이터 중 관계형 조회와 통계가 커질 영역은 Supabase PostgreSQL로 옮긴다. Firestore는 전환 전 기준 데이터와 호환 저장소로 낮추고, 전환된 도메인에서는 PostgreSQL을 source of truth로 둔다. API 서버는 Firebase 대체 서버가 아니라 PostgreSQL 접근, Firebase token 검증, 관리자 쓰기 검증에 필요한 얇은 경계로 시작한다.

## 참고 문서

- Firebase ID token 검증: <https://firebase.google.com/docs/auth/admin/verify-id-tokens>
- Firebase Cloud Messaging 서버 환경: <https://firebase.google.com/docs/cloud-messaging/server-environment>
- Firebase Hosting: <https://firebase.google.com/docs/hosting>
- Firebase Storage Security Rules: <https://firebase.google.com/docs/storage/security>
- Supabase Firebase Auth: <https://supabase.com/docs/guides/auth/third-party/firebase-auth>
- Supabase Realtime Postgres Changes: <https://supabase.com/docs/guides/realtime/postgres-changes>
- Supabase 가격표: <https://supabase.com/pricing>
- Neon 가격표: <https://neon.com/pricing>
- Oracle Cloud Always Free Resources: <https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm>

## 관련 이슈

- [#86 PostgreSQL 운영 전환 기준 확정](https://github.com/bodeul110/Bodeul/issues/86)
- [#87 Supabase 개발 DB 준비 및 API 경계 검토](https://github.com/bodeul110/Bodeul/issues/87)
- [#88 bodeul-api 서버 골격 추가](https://github.com/bodeul110/Bodeul/issues/88)
