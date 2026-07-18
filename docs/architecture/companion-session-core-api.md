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

## 권한 경계

| role | V5 권한 |
| --- | --- |
| `bodeul_core_runtime` | 세션·리포트·후속 처리 SELECT |
| `bodeul_admin_runtime` | 세션·리포트·후속 처리·배정 감사 SELECT, 배정 함수 EXECUTE |
| `anon`, `authenticated`, `service_role`, `public` | 테이블과 배정 함수 권한 없음 |
| `bodeul_migration` | Flyway DDL과 Firestore 백필 |

Core API의 쓰기 endpoint를 구현할 때 필요한 컬럼·함수 권한만 후속 migration으로 추가한다. V5 단계에서는 아직 Android의 Firestore 쓰기를 바꾸지 않는다.

## 백필과 rollback

```powershell
npm --prefix tools/firebase run postgres:sessions:check -- --file backups/<백업 파일>.json
npm --prefix tools/firebase run postgres:sessions:rollback -- --file backups/<백업 파일>.json
npm --prefix tools/firebase run postgres:sessions:sql -- --file backups/<백업 파일>.json
```

적용 전 `check`, transaction rollback SQL, 적용 SQL 순서로 검증한다. 생성 SQL은 개인정보를 포함하므로 `tools/firebase/reports/`의 Git 제외 경로에만 둔다. V5 DDL rollback은 `core-api/db/rollback/V5__drop_companion_session_operational_schema.sql`을 사용한다.

개발 DB 적용은 `Core API DB Migration` workflow의 `apply_companion_session_seed=true` 입력을 사용한다. 적용 SQL은 `core-api-migration-preview`의 일회성 `COMPANION_SESSION_SEED_SQL_BASE64` secret으로 전달하고, `companion_session_seed_sha256` 입력과 실제 파일 해시가 일치해야 한다. workflow 종료를 확인한 즉시 일회성 secret을 삭제한다.

## 리스크와 전환 조건

- V5 적용만으로 source of truth가 바뀌지는 않는다. Core API와 관리자 서버가 PostgreSQL을 사용하고 Android의 대응 Firestore 쓰기가 중지돼야 전환 완료다.
- 기존 배정의 관리자 actor는 Firestore에 없으므로 감사 기록을 추정해 만들지 않는다. 전환 이후 배정부터 기록한다.
- 채팅과 위치가 Firestore에 남는 동안 세션 화면은 두 저장소를 합성한다. 한쪽 장애 시 부분 정보가 보일 수 있다.
- 개발 DB 백필 후 row/FK/상태 비교, 관리자 Preview 배정, 실기기 동행 완료와 rollback을 모두 통과해야 production migration 대상으로 승격한다.
