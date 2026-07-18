# 매칭·동행·리포트 PostgreSQL 전환 계약

기준일: 2026-07-18

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

예약이 매니저에게 배정된 뒤 생성되는 동행 세션, 종료 리포트와 후속 처리의 운영 원본을 Firestore에서 PostgreSQL로 옮긴다. 관리자 웹과 Android 앱이 Firestore 문서를 각각 수정하지 않고 관리자 서버와 Spring Core API를 통해 같은 DB 상태를 보게 하는 것이 목표다.

## 선택한 방식

- `appointment_requests`와 1:1인 `companion_sessions`를 둔다.
- 동행 종료 리포트는 세션과 1:1인 `session_reports`로 관리한다.
- 후기·정산 확인·긴급 지원은 예약과 1:1인 `appointment_follow_ups`에 통합한다.
- 관리자 배정은 테이블의 광범위한 쓰기 권한 대신 `assign_companion_session` DB 함수만 실행한다.
- 배정 함수는 관리자와 매니저 role, 예약 상태, 예약 버전을 검증한 뒤 예약 `MATCHED`, 세션 `READY`, 감사 기록을 한 트랜잭션에서 생성한다.
- 앱과 서버의 동시 수정을 검출할 수 있도록 세션·리포트·후속 처리에 `version`을 둔다.
- Core API는 세션 조회·현장 메모·단계 전환·리포트·예약 후속 처리 endpoint를 소유하고, Android가 PostgreSQL에 직접 연결하지 않는다.
- 진행 단계 수는 Android 입력이 아니라 예약의 병원·진료과와 연결된 `hospital_guides.steps`에서 계산한다.

## 대안

- Firestore를 계속 운영 원본으로 두고 PostgreSQL은 조회용 복제본으로만 유지할 수 있다.
- 관리자 서버에 `appointment_requests`와 `companion_sessions` 전체 UPDATE 권한을 부여할 수 있다.
- 채팅 메시지와 위치 이력까지 같은 migration에서 JSONB로 옮길 수 있다.

## 선택 이유

현재 MVP 규모에서도 예약과 동행 상태를 서로 다른 저장소에서 수정하면 매칭 이후 취소, 세션 완료와 예약 완료를 원자적으로 처리할 수 없다. 반면 모든 실시간 데이터를 한 번에 옮기면 검증 범위가 너무 커진다. 그래서 낮은 빈도의 상태·메모·리포트를 먼저 관계형 트랜잭션 경계로 옮기고, 채팅과 고빈도 위치는 #221에서 보관·파기 정책과 함께 전환한다.

관리자 서버에는 배정에 필요한 검증된 함수 실행 권한만 주었다. 이 방식은 별도 관리자 API가 실수로 예약의 개인정보나 가격 필드를 수정하는 범위를 DB에서도 줄인다.

## 데이터 계약

| PostgreSQL | Firestore 원본 | 관계 | 운영 책임 |
| --- | --- | --- | --- |
| `companion_sessions` | `companionSessions` | 예약 1:1, 매니저 N:1 | 배정, 동행 상태와 현장 메모 |
| `session_reports` | `sessionReports` | 세션 1:1 | 종료 리포트와 복약 비교 |
| `appointment_follow_ups` | `appointmentFollowUps` | 예약 1:1 | 후기, 정산 확인, 긴급 지원 |
| `companion_session_assignment_audits` | 기존 복원 불가 | 예약·세션 N:1 | 전환 이후 관리자 배정 감사 |

`nextVisitAt`에는 날짜와 자유 텍스트가 혼재한다. PostgreSQL에서는 정규화 가능한 시각을 `next_visit_at`에, 원문을 `next_visit_note`에 보관해 기존 데이터를 잃지 않는다.

Firestore의 `chatMessages`, `sharedLocationHistory`, 좌표와 읽음 시각은 이번 테이블에 넣지 않는다. 해당 값은 #221 전환 전까지 Firestore legacy 경로에 남는다.

## 상태 전이

| 예약 상태 | 세션 상태 | 허용 작업 |
| --- | --- | --- |
| `REQUESTED` | 없음 | 관리자 배정 |
| `MATCHED` | `READY` | 시작 전 취소 또는 동행 시작 |
| `IN_PROGRESS` | `MEETING`~`PAYMENT` | 매니저 진행 단계와 현장 메모 갱신 |
| `COMPLETED` | `COMPLETED` | 리포트·후속 처리 조회 |
| `CANCELED` | `CANCELED` | 읽기 전용 |

상태 전이 API는 서버에서 현재 상태와 `version`을 확인하고 예약과 세션을 같은 DB 트랜잭션으로 갱신한다.

## Core API 계약

| endpoint | 읽기·쓰기 주체 | 처리 |
| --- | --- | --- |
| `GET /api/companion-sessions` | 환자·보호자·매니저 | 본인 참여 또는 본인 배정 세션 최대 100건 |
| `GET /api/companion-sessions/{id}` | 환자·보호자·매니저 | 참여자·배정 관계 확인 후 세션 조회 |
| `PATCH /api/companion-sessions/{id}` | 배정 매니저 | 현장 메모·약국 진행 상태를 `version` 조건으로 부분 갱신 |
| `POST /api/companion-sessions/{id}/advance` | 배정 매니저 | 서버의 병원 가이드 단계 수를 확인하고 예약 `IN_PROGRESS`와 세션 단계를 한 트랜잭션으로 갱신 |
| `GET /api/companion-sessions/{id}/report` | 환자·보호자·매니저 | 참여자·배정 관계 확인 후 리포트 조회 |
| `PUT /api/companion-sessions/{id}/report` | 배정 매니저 | 리포트 upsert, 예약·세션 `COMPLETED`를 한 트랜잭션으로 반영 |
| `GET /api/appointments/{id}/follow-up` | 환자·보호자·배정 매니저 | 예약 참여 관계 확인 후 후기·정산·긴급 지원 기록 조회. 미생성 상태는 `version=0`인 빈 응답 반환 |
| `PATCH /api/appointments/{id}/follow-up` | 환자·보호자 | 완료 예약만 허용하고 `version` 조건으로 제공된 후속 필드만 생성·갱신 |

환자·보호자의 예약 취소는 `REQUESTED`와 `MATCHED`에서만 허용한다. `MATCHED` 취소는 예약을 먼저 잠근 뒤 활성 세션을 `CANCELED`로 바꾸며, 세션 갱신이 실패하면 전체 트랜잭션을 rollback한다. 매니저는 배정된 예약 상세를 읽을 수 있지만 환자용 예약 생성·수정·취소 API는 사용할 수 없다.

## 권한 경계

| role | V5~V7 권한 |
| --- | --- |
| `bodeul_core_runtime` | 세션·리포트·후속 처리 SELECT, 세션 진행 컬럼 UPDATE, 리포트와 후속 처리 지정 컬럼 INSERT·UPDATE |
| `bodeul_admin_runtime` | 세션·리포트·후속 처리·배정 감사 SELECT, 배정 함수 EXECUTE |
| `anon`, `authenticated`, `service_role`, `public` | 테이블과 배정 함수 권한 없음 |
| `bodeul_migration` | Flyway DDL과 Firestore 백필 |

V6와 V7은 Core API에 테이블 전체 권한이 아니라 실제 endpoint가 사용하는 컬럼 권한과 RLS 쓰기 정책만 추가한다. V7 기준 후속 처리 권한은 INSERT 14개 열, UPDATE 13개 열로 제한되며 DELETE 권한과 관리자 runtime의 광범위한 쓰기 권한은 부여하지 않는다.

## Android 전환 경계

- 예약·세션 진행·현장 메모·약국 상태·세션 리포트·예약 후속 처리는 Core API 응답을 화면 원본으로 사용한다.
- 매니저 세션 변경과 리포트 제출은 Core API의 `version` 조건부 요청으로 처리한다.
- 후기·정산 확인·긴급 지원 저장은 최신 후속 레코드를 조회한 뒤 해당 `version`으로 부분 갱신하며 Firestore `appointmentFollowUps`에 다시 쓰지 않는다.
- 채팅, 첨부, 위치 좌표·이력·읽음 시각과 실시간 위치 공유 상태는 #221까지 Firestore에 남기고 화면에서 합성한다.
- 예약 상세 observer는 Firestore 보조 데이터 listener와 10초 Core API 갱신을 함께 사용한다. 세션 원본을 Firestore에 다시 쓰지 않는다.
- 관리자 서버의 PostgreSQL 배정은 검증했지만 Android가 아직 Firebase 보조 데이터를 목록 시작점으로 사용한다. 따라서 Firestore 보조 문서 없이 Core API에서 생성된 예약·배정이 앱에 나타나는지는 별도 전환이 필요하다.

## 백필과 rollback

```powershell
npm --prefix tools/firebase run postgres:sessions:check -- --file backups/<백업 파일>.json
npm --prefix tools/firebase run postgres:sessions:rollback -- --file backups/<백업 파일>.json
npm --prefix tools/firebase run postgres:sessions:sql -- --file backups/<백업 파일>.json
```

적용 전 `check`, transaction rollback SQL, 적용 SQL 순서로 검증한다. 생성 SQL은 개인정보를 포함하므로 `tools/firebase/reports/`의 Git 제외 경로에만 둔다. V5 DDL rollback은 `core-api/db/rollback/V5__drop_companion_session_operational_schema.sql`을 사용한다.

개발 DB 적용은 `Core API DB Migration` workflow의 `apply_companion_session_seed=true` 입력을 사용한다. 적용 SQL은 `core-api-migration-preview`의 일회성 `COMPANION_SESSION_SEED_SQL_BASE64` secret으로 전달하고, `companion_session_seed_sha256` 입력과 실제 파일 해시가 일치해야 한다. workflow 종료를 확인한 즉시 일회성 secret을 삭제한다.

개발 DB 백필은 run `29638905550` attempt 2에서 완료했다. 세션 2건, 리포트 2건, 후속 처리 1건의 FK와 `imported_at` 누락이 모두 0건이고, 예약·세션 상태 조합도 `COMPLETED/COMPLETED`, `IN_PROGRESS/IN_TREATMENT` 각 1건으로 일치했다. 일회성 secret은 실행 직후 삭제했다.

## 리스크와 전환 조건

- V5 적용만으로 source of truth가 바뀌지는 않는다. Core API와 관리자 서버가 PostgreSQL을 사용하고 Android의 대응 Firestore 쓰기가 중지돼야 전환 완료다.
- 기존 배정의 관리자 actor는 Firestore에 없으므로 감사 기록을 추정해 만들지 않는다. 전환 이후 배정부터 기록한다.
- 채팅과 위치가 Firestore에 남는 동안 세션 화면은 두 저장소를 합성한다. 한쪽 장애 시 부분 정보가 보일 수 있다.
- 개발 DB 백필 후 row/FK/상태 비교, 관리자 Preview 배정, 실기기 동행 완료와 rollback을 모두 통과해야 production migration 대상으로 승격한다.
- V6 Core 쓰기 권한은 개발 DB migration run `29639792606`에서 검증했다. Cloud Run Preview run `29639915209` 이후 실제 Firebase token으로 환자·보호자·매니저 목록 200, 관리자 목록 403, 환자 수정 403, 매니저 version 충돌 409를 확인했다.
- V7은 PostgreSQL 17 임시 인스턴스에서 V1부터 연속 migration을 적용했다. Core runtime의 후속 처리 생성·부분 수정은 각각 version 1·2를 반환했고 오래된 version 수정은 0건이었으며, `anon`, `authenticated`, `service_role`에는 후속 처리 권한이 없음을 확인했다.
- Android 실기기에서는 매니저 홈, 과거 이력, 보호자 리포트와 예약 상세가 PostgreSQL 세션 상태를 표시했다. 관리자 웹 PR #23의 Vercel Preview는 같은 개발 DB에서 배정 성공 201과 예약 `MATCHED`, 세션 `READY`, 감사 1건을 확인했다. 다만 이 임시 Core-only 예약을 Android 목록에서 조회하는 경로는 Firebase 보조 데이터 의존을 제거한 뒤 검증한다.
