# PostgreSQL API 경계 기준

기준일: 2026-06-29

## 작업 목적

Issue #87의 Supabase 개발 DB 준비 이후, 관리자 웹과 Android 앱이 PostgreSQL을 언제, 어떤 경로로 사용해야 하는지 1차 경계를 정한다.

## 결론

현재 단계에서는 클라이언트가 Supabase PostgreSQL에 직접 접속하지 않는다. PostgreSQL 접근은 `bodeul-api` 같은 얇은 API 서버를 통해 시작한다.

Firebase Auth, FCM, Storage, Hosting은 유지한다. PostgreSQL은 관계형 조회, 운영 감사, 정산/통계, 관리자 처리 이력처럼 Firestore보다 RDBMS가 적합한 도메인부터 맡긴다.

## 선택한 방식

| 영역 | 1차 기준 |
| --- | --- |
| 인증 | Firebase Auth 유지 |
| 서버 인증 검증 | API 서버에서 Firebase ID token 검증 |
| 권한 기준 | PostgreSQL `app_users.role`과 필요한 경우 Firebase UID 매핑 |
| DB 접근 | API 서버만 Supabase PostgreSQL connection string 사용 |
| Android 앱 | API 또는 기존 Firebase Repository를 통해 접근. DB 직접 접속 금지 |
| 관리자 웹 | `VITE_BODEUL_DATA_BACKEND=firebase|api` 플래그로 전환 후보 관리 |
| Firebase Functions | FCM, Storage 보조, Firebase 인프라 연결 역할 유지 |
| Supabase Realtime | 관리자 피드처럼 빈도가 예측 가능한 읽기 구독부터 검토 |

## 대안

- 관리자 웹에서 Supabase JS client로 PostgreSQL/RLS를 직접 사용한다.
- Android 앱에서 Supabase client를 직접 붙인다.
- Firebase Functions에서 PostgreSQL 접근 API를 모두 처리한다.
- Oracle VM에 API 서버와 PostgreSQL을 모두 직접 운영한다.

## 선택 이유

현재 MVP 규모에서는 인증, 푸시, 파일, 배포가 Firebase에 이미 연결되어 있다. 여기에 Supabase client를 앱과 웹에 직접 붙이면 Firebase Auth, Supabase Auth/RLS, 서비스 권한 정책이 동시에 섞여 운영 기준이 흐려진다.

API 서버를 얇게 두면 다음 경계를 명확히 할 수 있다.

- DB connection string은 서버 secret으로만 둔다.
- Firebase ID token 검증과 PostgreSQL role 확인을 한 곳에서 처리한다.
- 관리자 쓰기 작업의 감사 로그와 검증을 서버에서 통제한다.
- 특정 도메인을 PostgreSQL source of truth로 전환할 때 클라이언트 플래그로 rollback할 수 있다.

## 도메인별 1차 경계

| 도메인 | 현재 source of truth | PostgreSQL 전환 판단 | API 필요도 |
| --- | --- | --- | --- |
| 병원 가이드 | Firestore | 낮은 위험의 첫 read/write 전환 후보 | 중간 |
| 매니저 서류 메타데이터 | Firestore + Storage | 원본 파일은 Storage 유지, 메타데이터/심사 이력은 PostgreSQL 후보 | 높음 |
| 문의/후속 처리 | Firestore | 관리자 운영 조회와 상태 변경을 PostgreSQL 후보로 둠 | 높음 |
| 관리자 감사 로그 | Firestore | PostgreSQL에 적합. 관리자 작업 API와 함께 확정 | 높음 |
| 예약 요청 | Firestore | Android 앱 영향이 커서 후순위 | 높음 |
| 동행 세션/리포트 | Firestore | 예약 전환 이후 검토 | 높음 |
| 실시간 위치 | Firestore | 마지막 전환 대상. 부하 테스트 전에는 유지 | 높음 |
| FCM 발송 큐 | Firebase/Functions | DB 전환과 분리. 발송은 Firebase 인프라 유지 | 중간 |

## 첫 API 후보

첫 API는 운영 위험이 낮고 비교가 쉬운 read 중심으로 시작한다.

| 후보 | 이유 | 제외 조건 |
| --- | --- | --- |
| `GET /healthz` | 배포와 모니터링 최소 기준 | 없음 |
| `GET /admin/hospital-guides` | row 수가 작고 화면 비교가 쉽다 | 병원 가이드 수정 흐름까지 한 번에 묶어야 하면 후순위 |
| `GET /admin/manager-document-reviews` | 관리자 웹 운영 가치가 크고 PostgreSQL seed 검증 범위와 연결된다 | Storage 원본 접근 권한 경계가 정리되지 않으면 read-only로 제한 |
| `GET /admin/support-requests` | 문의 통합 테이블 검증 결과와 연결된다 | 상태 변경 API가 같이 필요하면 별도 write 검증 후 진행 |

write API는 read API의 인증, 권한, row 비교가 통과한 뒤 진행한다.

## Oracle VM 생성 조건

`bodeul-dev-api-01`은 아래 조건을 만족할 때 만든다.

- `api/` 서버 골격과 `GET /healthz`가 PR로 준비됐다.
- `DATABASE_URL`, Firebase service account, 배포 SSH key를 어디에 넣을지 문서화됐다.
- 관리자 웹 또는 Android 앱이 API를 실제로 호출하는 PR이 준비됐다.
- 장애 시 `BODEUL_DATA_BACKEND=firebase`로 되돌리는 rollback 기준이 있다.
- API 로그에 secret, ID token 원문, DB connection string을 남기지 않는 기준이 있다.

DB schema 검증, seed dry-run, row count 비교만 하는 동안에는 Oracle VM을 만들지 않는다.

## GitHub Environment 생성 조건

`api-preview`는 API 서버 PR이 시작될 때 만든다.

| 값 | 생성 시점 |
| --- | --- |
| `API_BASE_URL` | 개발 API URL이 생긴 뒤 |
| `FIREBASE_PROJECT_ID` | API 서버가 Firebase token 검증을 시작할 때 |
| `SUPABASE_PROJECT_NAME` | API 서버가 개발 DB에 연결될 때 |
| `DATABASE_URL` | API 서버가 PostgreSQL에 실제 연결될 때 담당자가 직접 입력 |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | 서버 token 검증 구현이 PR에 포함될 때 담당자가 직접 입력 |

`DATABASE_URL`, service account JSON, Supabase service role key는 문서, 이슈, PR 본문에 기록하지 않는다.

## source of truth 전환 조건

특정 도메인을 PostgreSQL source of truth로 바꾸려면 아래 조건을 모두 만족해야 한다.

- Firestore 백업과 PostgreSQL seed/import row count가 일치한다.
- 주요 필드 spot check가 통과한다.
- FK 누락이 0건이다.
- read API 응답이 기존 관리자 웹 또는 Android 화면 모델과 비교 가능하다.
- write 전환 대상이면 Firestore 쓰기 중단 또는 shadow write 정책이 문서화됐다.
- rollback 플래그와 복구 순서가 있다.

## 현재 Issue #87 결론

2026-06-29 기준 `bodeul-dev-rdb`는 생성됐고, schema 적용과 실제 Firestore 백업 기반 seed 적용 검증이 완료됐다.

Issue #87에서는 API 서버를 구현하지 않는다. 대신 다음 이슈에서 `bodeul-api` 골격과 첫 read API 후보를 구현할 수 있도록 경계를 문서화한 상태로 마무리한다.

## 리스크

- API 서버를 너무 빨리 넓히면 Firebase와 PostgreSQL 이중 운영 범위가 커진다.
- Supabase RLS와 Firebase Auth를 동시에 클라이언트에서 직접 다루면 권한 기준이 중복된다.
- Oracle VM을 먼저 만들면 배포/보안/장애 대응 문서 없이 운영 부담이 생긴다.
- 실시간 위치를 PostgreSQL/Realtime으로 서둘러 옮기면 쓰기 부하와 지연 문제가 먼저 터질 수 있다.

## 관련 문서

- [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
- [PostgreSQL 운영 전환 런북](../operations/postgres-operational-transition-runbook.md)
- [PostgreSQL seed dry-run 기준 기록](../reports/postgres-seed-dry-run-plan-2026-06-29.md)
- [PostgreSQL schema 초안](postgres-schema-draft.sql)
