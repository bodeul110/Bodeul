# PostgreSQL 운영 전환 결정

기준일: 2026-06-26

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

멘토 피드백 이후 BoDeul 운영 저장소를 Firebase Firestore 단독 구조에서 PostgreSQL 기반 운영 구조로 전환한다. 목표는 “Firebase를 당장 모두 제거”가 아니라, 예약, 매칭, 관리자 운영, 정산, 통계처럼 관계형 모델이 필요한 데이터를 PostgreSQL과 서버 API 중심으로 옮기는 것이다.

## 결론

운영 전환 대상은 `Supabase PostgreSQL + Oracle Cloud API 서버`로 잡는다.

| 영역 | 전환 결정 |
| --- | --- |
| 운영 트랜잭션 DB | Supabase PostgreSQL |
| API 서버 | Oracle Cloud Free Tier VM의 Node 22 API 서버 |
| Android 앱 데이터 접근 | Firestore 직접 접근에서 서버 API 호출로 단계 전환 |
| 관리자 웹 데이터 접근 | Firestore 직접 접근에서 서버 API 호출로 단계 전환 |
| Auth | 단기에는 Firebase Auth 유지, API 서버에서 Firebase ID token 검증 |
| FCM | 단기 유지 |
| Storage | 매니저 서류 원본은 단기 Firebase Storage 유지, 메타데이터는 PostgreSQL로 이전 |
| Firebase Functions | 알림/동기화/마이그레이션 보조 역할로 축소 후 API 서버로 단계 이전 |
| Firestore | 운영 source of truth에서 이관 대상/임시 shadow 저장소로 전환 |

## 선택한 방식

1. Supabase PostgreSQL을 운영 트랜잭션 DB로 둔다.
2. Oracle Cloud VM에는 `bodeul-api` 서버를 올린다.
3. 클라이언트는 PostgreSQL에 직접 접속하지 않고 API 서버만 호출한다.
4. API 서버는 Firebase Auth ID token을 검증하고, PostgreSQL의 `app_users.role`과 운영 권한을 기준으로 인가한다.
5. 실시간 요구는 도메인별로 나눈다.
   - 예약/세션/관리자 상태: 서버 API + Supabase Realtime 또는 서버 WebSocket/SSE
   - 푸시 알림: FCM 유지
   - 매니저 위치 공유: 마지막 전환 대상으로 두고 별도 부하 테스트 후 결정
6. Firestore는 마이그레이션 완료 전까지 백업/비교/rollback을 위한 기준 데이터로 유지한다.

## 명칭 기준

| 항목 | 명칭 |
| --- | --- |
| API 서비스명 | `bodeul-api` |
| API 서버 디렉터리 후보 | `api/` |
| 개발 API VM | `bodeul-dev-api-01` |
| 운영 API VM | `bodeul-prod-api-01` |
| Supabase 개발 프로젝트 | `bodeul-dev-rdb` |
| Supabase 운영 프로젝트 | `bodeul-prod-rdb` |
| PostgreSQL 기본 스키마 | `public` |
| 마이그레이션 작업 브랜치 접두어 | `codex/postgres-*` |
| GitHub Environment 개발 | `api-preview` |
| GitHub Environment 운영 | `api-production` |

## 대안 비교

| 대안 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| Supabase PostgreSQL | PostgreSQL, 관리 콘솔, Realtime, 백업 기능을 함께 제공한다. 멘토가 말한 “실시간도 된다”는 설명에 맞다. | Supabase Auth/Storage까지 함께 쓰면 Firebase와 이중 BaaS가 된다. RLS/Realtime 정책 설계가 필요하다. | 운영 전환 1순위로 선택한다. 단, Auth/Storage는 바로 옮기지 않는다. |
| Neon PostgreSQL | 서버리스 PostgreSQL, branching, 비용 예측이 좋다. DB만 깔끔하게 쓰기 좋다. | 앱 실시간 기능은 별도 WebSocket/SSE 서버를 직접 구현해야 한다. 멘토 질문에 대한 “실시간 대안” 설명이 Supabase보다 약하다. | 2순위 대안으로 남긴다. Supabase 비용/Realtime 한계가 확인되면 검토한다. |
| Oracle VM에 PostgreSQL 직접 운영 | 인프라 제어권이 높고 비용을 낮출 수 있다. | DB 백업, 보안 패치, 장애 대응을 팀이 직접 책임져야 한다. 현재 팀에는 운영 리스크가 크다. | 초기 운영 전환 DB로는 제외한다. API 서버만 Oracle VM에서 운영한다. |
| Firebase 유지 | 구현 변경이 가장 적다. | 멘토가 요구한 RDBMS/서버 운영 역량과 마이그레이션 기준을 보여주기 어렵다. 정산/통계/검색 확장에 약하다. | 단기 유지 영역은 남기되, 운영 source of truth는 전환한다. |

## 선택 이유

- 현재 데이터 계약은 이미 `data-api.md`에 서버 API 형태로 초안이 있다.
- 관리자 운영, 정산, 문의, 후속 처리, 알림 전달 이력은 관계형 조회와 집계 요구가 커질 가능성이 높다.
- Supabase는 PostgreSQL 기반이고 Realtime Postgres Changes를 제공하므로 실시간 기능을 완전히 포기하지 않고 RDBMS 전환을 설명할 수 있다.
- Android 앱이 Java 기반이라 Supabase 클라이언트를 직접 붙이기보다 서버 API를 두는 편이 장기적으로 안전하다.
- Firebase Auth, FCM, Storage까지 한 번에 바꾸면 인증, 푸시, 파일 접근, Rules, 앱 릴리스가 동시에 흔들린다. 그래서 DB/API부터 전환한다.

## 현재 Firebase 의존 범위

| 영역 | 현재 의존 | 전환 판단 |
| --- | --- | --- |
| 사용자 프로필/역할 | Firestore `users` | `app_users`로 이전. Firebase UID는 외부 식별자로 유지 |
| 예약 | Firestore `appointmentRequests` | `appointment_requests`로 우선 이전 |
| 동행 세션 | Firestore `companionSessions` | `companion_sessions`로 이전 |
| 리포트 | Firestore `sessionReports` | `session_reports`로 이전 |
| 병원 가이드 | Firestore `hospitalGuides` | `hospital_guides`로 이전. 낮은 위험의 초기 전환 후보 |
| 문의 | Firestore `supportInquiries`, `clientSupportRequests` | `support_requests` 계열로 통합 |
| 관리자 후속/알림/감사 | 여러 admin 컬렉션 | 운영 데이터 성격이 강하므로 PostgreSQL에 적합 |
| 매니저 서류 원본 | Firebase Storage | 원본은 단기 유지, 메타데이터와 심사 이력만 PostgreSQL 이전 |
| 푸시 | FCM | 단기 유지 |
| 관리자 웹 배포 | Firebase Hosting | 단기 유지 |

## 전환 순서

### 0단계: 운영 전환 준비

- Supabase 개발 프로젝트 `bodeul-dev-rdb` 생성
- Oracle VM `bodeul-dev-api-01` 생성
- `api/` 서버 골격과 헬스체크 추가
- Firebase ID token 검증 미들웨어 추가
- PostgreSQL schema 초안과 마이그레이션 도구 준비

### 1단계: 낮은 위험 도메인 이전

대상:

- `hospitalGuides`
- 관리자 조회용 매니저 서류 심사 메타데이터
- 관리자 문의/후속 처리 일부

이유:

- 실시간 위치나 예약 상태 전이보다 트래픽과 상태 복잡도가 낮다.
- 관리자 웹에서 먼저 API 호출로 바꾸면 Android 앱 릴리스 리스크를 줄일 수 있다.

### 2단계: 예약/세션 read API 이전

대상:

- 예약 상세 조회
- 매니저 이력 조회
- 관리자 운영 대시보드 조회

원칙:

- 처음에는 Firestore 백업과 PostgreSQL 결과를 비교한다.
- 클라이언트 화면을 바꾸기 전 API 응답이 기존 모델과 같은지 검증한다.

### 3단계: 예약/세션 write 이전

대상:

- 예약 생성/수정/취소
- 매칭
- 세션 진행 상태 변경
- 리포트 저장

원칙:

- 특정 도메인을 PostgreSQL source of truth로 전환하면 해당 도메인의 Firestore 쓰기는 중단한다.
- 필요한 경우 Firestore shadow sync는 읽기 호환용으로만 둔다.

### 4단계: 실시간/알림 재설계

대상:

- 실시간 위치
- 보호자 상태 업데이트
- 관리자 알림 피드
- FCM 발송 큐

판단 기준:

- 단순 상태 변경 구독은 Supabase Realtime을 먼저 검토한다.
- 위치처럼 쓰기 빈도가 높은 데이터는 WebSocket/SSE, Redis, 전용 위치 테이블 분리 중 하나를 부하 테스트 후 결정한다.
- FCM은 운영 앱 푸시에 계속 필요하므로 제거 대상이 아니다.

### 5단계: Firebase 축소

조건:

- PostgreSQL 백업/복원 리허설 완료
- API 서버 배포/롤백 문서화 완료
- Android와 관리자 웹의 주요 데이터 쓰기가 API 서버로 이전
- Firestore와 PostgreSQL 데이터 비교 리포트가 연속 2회 이상 통과

완료 후 판단:

- Firestore는 legacy read-only 또는 백업 보관으로 낮춘다.
- Firebase Functions는 FCM, Storage, Auth 보조 기능만 남기거나 API 서버로 합친다.

## 첫 운영 전환 범위

첫 PR에서 코드 전환까지 바로 하지 않는다. 먼저 아래 산출물을 준비한다.

1. `api/` 서버 골격
2. PostgreSQL schema 초안
3. Firebase Firestore 백업을 PostgreSQL seed JSON으로 변환하는 dry-run 도구
4. 관리자 웹 매니저 서류 심사 read API 초안
5. `BODEUL_DATA_BACKEND=firebase|api` 전환 플래그 기준

첫 실제 기능 전환은 관리자 웹의 매니저 서류 심사 목록을 대상으로 한다. Android 앱의 예약/세션 전환은 그 다음이다.

## 리스크

| 리스크 | 대응 |
| --- | --- |
| Android 앱과 관리자 웹이 Firestore를 직접 읽는 코드가 많다. | Repository/API client 계층을 먼저 만들고 화면 단위로 교체한다. |
| Auth를 바로 바꾸면 로그인과 권한이 동시에 흔들린다. | Firebase Auth 유지, API 서버에서 ID token 검증. |
| Supabase Realtime이 위치 공유 부하를 감당하지 못할 수 있다. | 위치 공유는 마지막 전환 대상으로 두고 부하 테스트 후 결정. |
| Oracle Free Tier는 용량/회수/가용성 리스크가 있다. | 운영 전에는 백업, 재배포 스크립트, 대체 배포 위치를 문서화한다. |
| Firestore와 PostgreSQL 이중 쓰기 기간에 데이터 불일치가 생길 수 있다. | 도메인별 source of truth를 명확히 하고 비교 리포트를 만든다. |

## 멘토님께 설명할 답변

현재까지는 빠른 MVP 구현과 실시간 기능 때문에 Firebase를 중심으로 사용했다. 다만 운영 전환 단계에서는 예약, 정산, 통계, 관리자 운영처럼 관계형 조회가 커질 영역을 PostgreSQL로 옮기기로 했다. Supabase PostgreSQL을 1순위로 잡은 이유는 SQL/RDBMS 전환을 하면서도 Realtime 기능을 검토할 수 있기 때문이다. 서버는 Oracle Cloud Free Tier에서 API 서버를 먼저 운영해 보고, Firebase Auth/FCM/Storage는 한 번에 제거하지 않고 API 서버와 PostgreSQL로 source of truth를 옮긴 뒤 단계적으로 축소한다.

## 참고 문서

- Supabase Realtime Postgres Changes: <https://supabase.com/docs/guides/realtime/postgres-changes>
- Supabase 가격표: <https://supabase.com/pricing>
- Neon 가격표: <https://neon.com/pricing>
- Oracle Cloud Always Free Resources: <https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm>

## 관련 이슈

- [#86 PostgreSQL 운영 전환 기준 확정](https://github.com/bodeul110/Bodeul/issues/86)
- [#87 Supabase/Oracle 개발 리소스 준비](https://github.com/bodeul110/Bodeul/issues/87)
- [#88 bodeul-api 서버 골격 추가](https://github.com/bodeul110/Bodeul/issues/88)
