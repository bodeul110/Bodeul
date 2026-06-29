# PostgreSQL seed dry-run 기준 기록

기준일: 2026-06-29

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지 않았다.
현재 구현된 구조를 기준으로 선택 이유, 대안, 한계, 전환 조건을 정리한다.

## 작업 목적

Issue #87의 Supabase 개발 DB 준비 이후 Firestore 백업 JSON을 PostgreSQL seed 입력으로 전환하기 전에, 실제 DB 쓰기 없이 후보 row 수와 필수 필드 누락 여부를 확인하는 기준을 만든다.

## 선택한 방식

`tools/firebase/prepare-postgres-seed-dry-run.js`를 추가해 Firestore 백업 JSON을 로컬에서 읽고 PostgreSQL 테이블별 seed 후보 수, 필수 필드 누락, 초기 제외 컬렉션을 JSON 리포트로 출력한다.
`tools/firebase/build-postgres-seed-input.js`는 같은 백업을 PostgreSQL 테이블별 seed 입력 JSON으로 변환한다.
`tools/firebase/build-postgres-seed-sql.js`는 seed 입력 JSON을 Supabase SQL Editor 또는 psql에서 검토할 수 있는 upsert SQL로 변환한다.

## 대안

- Supabase 개발 DB에 바로 seed를 적용한다.
- SQL insert 파일을 먼저 생성한다.
- Firestore export를 수동으로 확인한다.

## 선택 이유

현재 MVP 규모에서는 먼저 row count와 필수 필드 기준을 자동화하는 편이 rollback 부담이 작다. 실제 insert SQL 생성은 Firestore 문서 ID와 PostgreSQL UUID 외래키 해석 규칙이 seed 입력 JSON에서 충분히 검증된 뒤 진행해야 한다.

## 리스크

- 이 dry-run은 실제 PostgreSQL insert 성공을 보장하지 않는다.
- PostgreSQL UUID 외래키는 seed 입력 JSON에서 deterministic UUID로 변환하지만, 실제 insert 전에는 FK 누락 여부를 다시 확인해야 한다.
- 알림/잡 컬렉션은 FCM, Functions, API 경계가 확정된 뒤 별도로 이전해야 한다.

## Supabase 개발 DB 준비 결과

| 항목 | 값 |
| --- | --- |
| 프로젝트 이름 | `bodeul-dev-rdb` |
| 리전 | `ap-northeast-2` |
| schema 검증 | `begin` / `rollback`으로 실행 성공 |
| schema 적용 | 개발 DB에 적용 완료 |
| 확인 | Supabase Table Editor에서 초기 테이블 생성 확인 |

secret 원문, DB connection string, database password, anon key, service role key는 이슈/PR/문서에 기록하지 않았다.

## dry-run 실행 방법

```bash
npm --prefix tools/firebase run postgres:seed:dry-run -- --file backups/firestore-backup.json
```

출력 파일을 고정하려면 다음처럼 실행한다.

```bash
npm --prefix tools/firebase run postgres:seed:dry-run -- --file backups/firestore-backup.json --output reports/postgres-seed-dry-run.json
```

Windows/npm 환경에서 `--file`, `--output` 옵션명이 전달되지 않으면 위치 인자로 실행한다.

```bash
npm --prefix tools/firebase run postgres:seed:dry-run -- backups/firestore-backup.json reports/postgres-seed-dry-run.json
```

## seed 입력 JSON 생성 방법

```bash
npm --prefix tools/firebase run postgres:seed:build -- --file backups/firestore-backup.json --output reports/postgres-seed-input.json
```

Windows/npm 환경에서 옵션명이 전달되지 않으면 위치 인자로 실행한다.

```bash
npm --prefix tools/firebase run postgres:seed:build -- backups/firestore-backup.json reports/postgres-seed-input.json
```

출력 JSON은 운영 데이터와 개인정보를 포함할 수 있으므로 `.gitignore` 대상인 `tools/firebase/reports/*.json` 아래에만 둔다.

## seed SQL 생성 방법

```bash
npm --prefix tools/firebase run postgres:seed:sql -- --file reports/postgres-seed-input.json --output reports/postgres-seed.sql
```

Windows/npm 환경에서 옵션명이 전달되지 않으면 위치 인자로 실행한다.

```bash
npm --prefix tools/firebase run postgres:seed:sql -- reports/postgres-seed-input.json reports/postgres-seed.sql
```

생성된 SQL은 `begin` / `commit`으로 감싸진 upsert 스크립트다. 실제 적용 전에는 Supabase SQL Editor에서 `begin` / `rollback`으로 먼저 검증한다. 출력 SQL은 운영 데이터와 개인정보를 포함할 수 있으므로 `.gitignore` 대상인 `tools/firebase/reports/*.sql` 아래에만 둔다.

검증 전용 SQL은 `postgres:seed:rollback`으로 생성한다. Windows/npm 환경에서 명령 끝의 `--rollback` boolean 옵션이 전달되지 않을 수 있으므로 rollback 전용 script를 사용한다.

```bash
npm --prefix tools/firebase run postgres:seed:rollback -- reports/postgres-seed-input.json reports/postgres-seed-rollback.sql
```

Supabase SQL Editor 적용 순서:

1. `reports/postgres-seed-rollback.sql` 내용을 SQL Editor에 붙여넣고 실행한다.
2. 오류 없이 `Success. No rows returned`가 나오면 Table Editor에서 row가 저장되지 않았는지 확인한다.
3. 같은 seed 입력에서 생성한 `reports/postgres-seed.sql`을 적용용으로 실행한다.
4. 적용 후 아래 row count 쿼리로 예상 row 수와 실제 row 수를 비교한다.

```sql
select 'app_users' as table_name, count(*)::int as row_count from app_users
union all select 'hospital_guides', count(*)::int from hospital_guides
union all select 'appointment_requests', count(*)::int from appointment_requests
union all select 'companion_sessions', count(*)::int from companion_sessions
union all select 'session_reports', count(*)::int from session_reports
union all select 'appointment_follow_ups', count(*)::int from appointment_follow_ups
union all select 'support_requests', count(*)::int from support_requests
union all select 'manager_document_files', count(*)::int from manager_document_files
union all select 'manager_document_reviews', count(*)::int from manager_document_reviews
union all select 'admin_audit_logs', count(*)::int from admin_audit_logs
order by table_name;
```

## ID와 FK 매핑 규칙

PostgreSQL schema 초안은 UUID 기본키를 사용하고, Firestore 백업은 문서 ID를 기준으로 관계를 가진다. seed 입력 단계에서는 다음 규칙으로 UUID를 결정한다.

| 대상 | UUID 입력 키 |
| --- | --- |
| `app_users.id` | `app_users:<firebase_uid>` |
| `appointment_requests.id` | `appointment_requests:<appointmentRequests 문서 ID>` |
| `companion_sessions.id` | `companion_sessions:<companionSessions 문서 ID>` |
| `hospital_guides.id` | `hospital_guides:<hospitalGuides 문서 ID>` |
| `support_requests.id` | `support_requests:<컬렉션명>:<문서 ID>` |
| `manager_document_files.id` | `manager_document_files:<manager uid>:<document key>:<storage path>` |
| `manager_document_reviews.id` | `manager_document_reviews:<manager uid>:<history index>:<status>` |
| `admin_audit_logs.id` | `admin_audit_logs:<adminAuditLogs 문서 ID>` |

UUID는 고정 namespace `8e884ace-2c0f-4a5b-9ddf-2ff3d8efb9d1`와 SHA-1 기반 deterministic UUID 형식으로 만든다. 같은 백업을 여러 번 변환해도 같은 row는 같은 UUID를 가진다.

FK는 원본 Firestore 필드의 문서 ID를 위 규칙으로 다시 변환해 연결한다.

| FK 컬럼 | Firestore 원본 필드 |
| --- | --- |
| `appointment_requests.patient_user_id` | `patientUserId` |
| `appointment_requests.guardian_user_id` | `guardianUserId` |
| `appointment_requests.manager_user_id` | `managerUserId` |
| `companion_sessions.appointment_request_id` | `appointmentRequestId` 또는 `requestId` |
| `session_reports.companion_session_id` | `sessionId` 또는 `companionSessionId` |
| `appointment_follow_ups.appointment_request_id` | `requestId`, `appointmentRequestId`, 없으면 문서 ID |
| `admin_audit_logs.request_id` | `requestId` 또는 `appointmentRequestId` |
| `admin_audit_logs.inquiry_id` | `supportInquiries/<id>`, `clientSupportRequests/<id>` 또는 단순 ID. 단순 ID는 두 문의 컬렉션에서 조회 |

## 비교 리포트 기준

| PostgreSQL 테이블 | Firestore 기준 | 비교 기준 |
| --- | --- | --- |
| `app_users` | `users` | 사용자 문서 수, `role` 필수 필드 |
| `manager_document_files` | `users.managerDocumentFiles`, `users.managerDocumentFilePaths` | 매니저 서류 파일 경로 수 |
| `manager_document_reviews` | `users.managerDocumentHistory` | 매니저 서류 심사 이력 수 |
| `hospital_guides` | `hospitalGuides` | 문서 수, 병원명/진료과 필수 필드 |
| `appointment_requests` | `appointmentRequests` | 문서 수, 예약 상태 필수 필드 |
| `companion_sessions` | `companionSessions` | 문서 수, 예약 연결/현재 상태 필수 필드 |
| `session_reports` | `sessionReports` | 문서 수, 동행 세션 연결 필수 필드 |
| `appointment_follow_ups` | `appointmentFollowUps`, `adminSettlementRecords`, `adminEmergencyIssues` | 예약 ID 기준 통합 row 수 |
| `support_requests` | `supportInquiries`, `clientSupportRequests` | 문의 문서 통합 수 |
| `admin_audit_logs` | `adminAuditLogs` | 감사 로그 수, action summary 필수 필드 |

## 초기 제외 범위

| Firestore 컬렉션 | 제외 이유 |
| --- | --- |
| `adminActionNotifications` | FCM/관리자 알림 전달 상태는 API/알림 경계 확정 후 이전 |
| `adminActionDeliveries` | 알림 전달 이력은 운영 API와 FCM 설계 확정 후 이전 |
| `adminActionDeliveryJobs` | 잡 실행 상태는 PostgreSQL 초기 seed 검증 범위에서 제외 |
| `appointmentReminderJobs` | 예약 리마인더 잡은 FCM/Functions 운영 경계 확정 후 이전 |

## 2026-06-29 로컬 검증

| 항목 | 결과 |
| --- | --- |
| `node -c tools/firebase/prepare-postgres-seed-dry-run.js` | 통과 |
| `node -c tools/firebase/build-postgres-seed-input.js` | 통과 |
| `node -c tools/firebase/build-postgres-seed-sql.js` | 통과 |
| 샘플 백업 dry-run 리포트 생성 | 통과 |
| 샘플 백업 seed 입력 JSON 생성 | 통과 |
| 샘플 seed 입력 JSON 기반 SQL 생성 | 통과 |
| 실제 Firestore 백업 생성 | `tools/firebase/backups/postgres-seed-dry-run-20260629.json` 생성 |
| 실제 백업 구조 검증 | 오류 0건, 경고 0건 |
| 실제 백업 dry-run 리포트 생성 | 통과 |
| 실제 백업 seed 입력 JSON 생성 | 통과 |
| 실제 백업 seed SQL 생성 | 통과 |
| 실제 백업 rollback 검증용 SQL 생성 | 통과 |
| rollback 전용 npm script 검증 | `postgres:seed:rollback` 출력 SQL이 `rollback;`으로 종료 |
| Supabase rollback SQL 1차 실행 | `admin_audit_logs.inquiry_id` FK 불일치로 실패 |
| FK 매핑 보완 | 단순 문의 ID를 `supportInquiries`와 `clientSupportRequests` 양쪽에서 조회하도록 수정 |
| seed 입력 JSON FK 자체 검증 | `admin_audit_logs.inquiry_id` 누락 0건 |
| Supabase rollback SQL 재실행 | 통과 |
| Supabase 적용용 SQL 실행 | `tools/firebase/reports/postgres-seed-20260629.sql` 적용 성공 |
| Supabase row count 비교 | seed 입력 JSON 기대값과 일치 |
| Supabase FK spot check | 모든 누락 count 0건 |
| 주요 필드 로컬 spot check | 필수 연결/상태/본문 필드가 기대 범위와 일치 |
| 문의 응답 시간 보정 | `RECEIVED` 문의의 `responded_at`은 `null`, `ANSWERED` 문의 2건만 응답 시간 유지 |
| 문의 수정 시간 보정 | `support_requests.updated_at`은 schema의 `not null` 제약에 맞춰 원본 `updatedAt`이 없으면 `created_at`으로 채움 |
| Supabase 문의 시간 spot check | `support_requests` 8건 모두 `updated_at` 값 존재, `RECEIVED` 6건의 `responded_at`은 `null`, `ANSWERED` 2건의 `responded_at`은 값 존재 |

실제 백업, seed 입력 JSON, seed SQL은 운영 데이터와 개인정보를 포함할 수 있으므로 `.gitignore` 대상 경로에만 보관하고 커밋하지 않는다. secret 원문은 문서와 이슈에 기록하지 않는다.

## Supabase row count 비교 결과

| 테이블 | 기대 row 수 | 실제 row 수 |
| --- | ---: | ---: |
| `admin_audit_logs` | 10 | 10 |
| `app_users` | 6 | 6 |
| `appointment_follow_ups` | 1 | 1 |
| `appointment_requests` | 4 | 4 |
| `companion_sessions` | 2 | 2 |
| `hospital_guides` | 1 | 1 |
| `manager_document_files` | 3 | 3 |
| `manager_document_reviews` | 7 | 7 |
| `session_reports` | 2 | 2 |
| `support_requests` | 8 | 8 |

## 주요 필드 spot check 결과

| 테이블 | 확인한 필드 | 결과 |
| --- | --- | --- |
| `app_users` | `firebase_uid`, `role`, `name`, `email` | 6/6 채움 |
| `appointment_requests` | `firestore_id`, `status`, `patient_name`, `guardian_name`, `hospital_name`, `appointment_at`, `final_price` | 4/4 채움 |
| `companion_sessions` | `firestore_id`, `appointment_request_id`, `current_status`, `guardian_update` | 2/2 채움 |
| `session_reports` | `firestore_id`, `companion_session_id`, `summary`, `medication_notes` | 2/2 채움 |
| `support_requests` | `firestore_id`, `title`, `body`, `status_code` | 8/8 채움 |
| `support_requests` | `response_text`, `responded_at` | `ANSWERED` 2/2 채움, `RECEIVED` 6/6 비움 |
| `manager_document_reviews` | `manager_user_id`, `status`, `reviewed_at` | 7/7 채움 |
| `admin_audit_logs` | `source_type`, `action_summary`, `created_at` | 10/10 채움 |
| `admin_audit_logs` | `request_id`, `inquiry_id` | 예약 감사 6건과 문의 감사 4건으로 분리되어 FK 누락 0건 |

## schema 보완 판단

현재 seed 검증 범위에서는 추가 schema 변경 없이 진행한다.

- `support_requests.responded_at`은 응답이 없는 접수 상태 문의에서 `null`이어야 하므로 seed 변환 로직에서 응답 존재 여부를 먼저 판단한다.
- `support_requests.updated_at`은 `not null` 컬럼이므로 Firestore 원본 `updatedAt`이 없으면 `created_at`을 사용한다.
- `build-postgres-seed-sql.js`는 nullable 컬럼의 `null` 값을 insert/upsert SQL에 명시해, 이미 잘못 들어간 값도 재적용 시 정정할 수 있게 한다.
- `phone`, `manager_document_status`, `review_note`, `request_id`, `inquiry_id`처럼 일부 row에서 비는 값은 현재 데이터 의미상 허용 범위다.
- FCM 알림, 예약 리마인더 잡, 운영 전달 이력은 API/Functions 경계가 확정된 뒤 별도 테이블 설계로 이전한다.

## Supabase 재확인 SQL

`responded_at`과 `updated_at` 보정 이후 생성한 `tools/firebase/reports/postgres-seed-20260629.sql`을 Supabase SQL Editor에서 다시 실행하면 기존 row가 upsert로 정정된다.

```sql
select firestore_id, status_code, response_text, responded_by_name, responded_at
from support_requests
order by firestore_id;
```

실제 확인 결과는 `RECEIVED` 문의 6건의 `responded_at`이 모두 `null`이고, `ANSWERED` 문의 2건만 응답 시간이 있는 상태다. `updated_at`은 8건 모두 값이 있으며, Firestore 원본 `updatedAt`이 없는 row는 `created_at`과 같은 값으로 채웠다.

## 남은 범위

Issue #87 기준 문서 범위는 완료됐다.

후속 구현 범위:

- `bodeul-api` 서버 골격과 `GET /healthz` 추가
- 관리자 웹 첫 read API 후보 구현
- 도메인별 source of truth 전환 PR 작성
