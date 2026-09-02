# 매칭·동행·리포트 PostgreSQL 전환 계약

기준일: 2026-07-19

최종 갱신: 2026-08-29

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

예약이 매니저에게 배정된 뒤 생성되는 동행 세션, 종료 리포트와 후속 처리의 운영 원본을 Firestore에서 PostgreSQL로 옮긴다. 관리자 웹과 Android 앱이 Firestore 문서를 각각 수정하지 않고 관리자 서버와 Spring Core API를 통해 같은 DB 상태를 보게 하는 것이 목표다.

## 선택한 방식

- `appointment_requests`와 1:1인 `companion_sessions`를 둔다.
- 동행 종료 리포트는 세션과 1:1인 `session_reports`로 관리한다.
- 후기·정산 확인은 예약과 1:1인 `appointment_follow_ups`에 통합한다. 기존 긴급 지원 열은 과거 데이터 읽기 호환용으로만 유지한다.
- 관리자 배정은 테이블의 광범위한 쓰기 권한 대신 `assign_companion_session` DB 함수만 실행한다.
- 배정 함수는 관리자와 매니저 role, 예약 상태, 예약 버전을 검증한 뒤 예약 `MATCHED`, 세션 `READY`, 감사 기록을 한 트랜잭션에서 생성한다.
- 앱과 서버의 동시 수정을 검출할 수 있도록 세션·리포트·후속 처리에 `version`을 둔다.
- Core API는 세션 조회·현장 메모·단계 전환·리포트·예약 후속 처리 endpoint를 소유하고, Android가 PostgreSQL에 직접 연결하지 않는다.
- 진행 단계는 세션 생성 시 V14가 고정한 `guide_steps_snapshot`에서 계산한다. 이후 `hospital_guides` 수정은 진행 중 세션 응답과 진행 한계를 바꾸지 않는다.
- 가이드 12의 실제 동행 종료는 `CARE_ENDED`, 가이드 13의 업무 완료는 `COMPLETED`로 분리한다. 최초 종료 시각은 서버가 한 번만 기록하고 중복 요청에는 같은 값을 반환한다.
- 가이드 8 결제 증빙과 가이드 10 처방 이미지는 원본과 SHA-256 객체 메타데이터를 Firebase Storage에, 용도·경로·크기·재시도 식별자는 PostgreSQL `companion_session_artifacts`에 저장한다.
- 세션 완료와 리포트 저장 성공을 하나의 원자적 결과로 취급하지 않는다. 세션을 먼저 `COMPLETED`로 확정하고 리포트 저장 상태를 `PENDING`, `READY`, `FAILED`로 별도 추적한다.

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
| `appointment_follow_ups` | `appointmentFollowUps` | 예약 1:1 | 후기, 정산 확인과 legacy 긴급 지원 읽기 호환 |
| `companion_session_assignment_audits` | 기존 복원 불가 | 예약·세션 N:1 | 전환 이후 관리자 배정 감사 |
| `companion_chat_messages` | `companionSessions.chatMessages` | 세션 N:1 | 본문, 발신자와 재시도 중복 제거 |
| `companion_chat_attachments` | 채팅 첨부 배열 | 메시지 N:1 | Firebase Storage 경로와 만료 상태 |
| `companion_chat_read_receipts` | 역할별 읽음 시각 | 세션·사용자 1:1 | 마지막 읽은 메시지와 시각 |
| `companion_session_locations` | 최신 좌표·위치 이력 | 세션 N:1 | 진행 중 최신·최근 10건 위치 |
| `companion_session_artifacts` | 신규 | 세션 N:1 | 가이드 8 결제 증빙과 가이드 10 처방 이미지 메타데이터·중복 요청 제거 |

`nextVisitAt`에는 날짜와 자유 텍스트가 혼재한다. PostgreSQL에서는 정규화 가능한 시각을 `next_visit_at`에, 원문을 `next_visit_note`에 보관해 기존 데이터를 잃지 않는다.

V8은 Firestore의 `chatMessages`, `sharedLocationHistory`, 좌표와 읽음 시각을 받을 PostgreSQL 계약을 추가한다. V10은 Core API 쓰기 결과 trigger를 추가하고 V11은 managed Realtime table 권한을 migration role에 주지 않는 privileged publisher로 발행 경계를 좁힌다. V12는 실시간 위치 공유와 자동 알림 운영 상태를 추가한다. V18은 `care_ended_at` 이후 신호 발행을 중단하고, privileged bootstrap 006은 신규·재인가 매니저 구독을 거부한다. 개발 환경의 Firebase Third-Party Auth, private channel RLS와 private-only 설정을 검증했고 Android 새 쓰기는 PostgreSQL만 사용한다. 기존 Firestore 값은 자동 백필하지 않으며 rollback 비교 자료로만 남긴다. Realtime 이벤트는 진행 중 커밋 결과 알림으로만 사용한다.

## 채팅·위치 저장 계약

- 채팅 본문은 세션별 행으로 저장하고 `client_message_id`로 네트워크 재시도의 중복 저장을 막는다.
- 첨부 원본은 Firebase Storage에 두되 경로, MIME, 크기와 파기 상태는 PostgreSQL에서 인가한다. Android는 Storage에 직접 쓰지 않고 Core API의 multipart endpoint로 전송한다. Core API는 참여 관계를 확인한 뒤 런타임 서비스 계정으로 저장하며, 다운로드 때도 참여 관계를 다시 확인한다.
- 채팅 첨부는 파일 시그니처가 일치하는 JPEG, PNG, PDF, 파일당 최대 10 MiB, 메시지당 최대 3개다. 저장 경로는 세션 ID, 재시도 식별자와 SHA-256으로 결정한다.
- 가이드 첨부는 현재 단계에서만 배정 매니저가 교체·삭제할 수 있다. `PAYMENT_EVIDENCE`는 JPEG·PNG·PDF 중 최대 1개, `PRESCRIPTION_IMAGE`는 JPEG·PNG 최대 3개이며 파일당 최대 10 MiB다. `client_request_id`와 payload fingerprint는 별도 operation ledger에 남겨 교체·삭제 뒤의 지연 재시도도 다시 적용하지 않으며, 같은 UUID의 다른 내용은 충돌로 거부한다.
- PostgreSQL에 원본 SHA-256을 함께 저장하고 인증 다운로드 때 실제 바이트를 다시 해시한다. 크기 또는 SHA-256이 다르면 손상된 원본으로 보고 반환하지 않는다.
- `CARE_ENDED` 이후에는 새 채팅·채팅 첨부·위치와 가이드 첨부·운영 메모를 저장하지 않는다. 배정 매니저의 기존 채팅·첨부·가이드 첨부·리포트·건강정보·위치 원문 조회도 회수하고, 가이드 13 완료와 이력 확인에 필요한 본인 `managerJournal`, 리포트 생성 상태와 완료 메타데이터만 세션 응답에 남긴다.
- 종료 후 환자는 보관기간 안의 기존 채팅과 첨부를 읽을 수 있다. 보호자의 범위별 인가는 `CHAT`=채팅 본문·읽음 상태, `CHAT+ATTACHMENT`=채팅 첨부, `ATTACHMENT`=가이드 8·10 첨부 목록·원본, `REPORT`=최종 리포트·건강정보로 구분한다. 위치는 역할과 동의 여부에 관계없이 종료 즉시 응답에서 숨긴다.
- 읽음 위치는 `(companion_session_id, user_id)` 한 행으로 관리하고 같은 세션 메시지만 참조할 수 있다.
- 위치는 배정된 매니저와 진행 가능한 세션을 확인하는 `record_companion_location` 함수만 기록한다. 15분보다 오래됐거나 5분보다 미래인 좌표는 거부하고 세션별 최근 10건만 유지한다.
- 최초 `care_ended_at`은 채팅 180일, 첨부 30일, 정밀 위치 24시간 뒤로 `expires_at`을 예약한다. 최종 `COMPLETED`가 지연돼도 이 기준은 이동하지 않으며, 취소 세션만 기존 `canceled_at`을 사용한다. 위치는 `CARE_ENDED`부터 응답에서 즉시 숨기고 실제 삭제와 Storage 정리는 #222 일일 job이 수행한다.
- 보호자 동의는 최초 `care_ended_at + 7일`로 확정하며 종료 후 재부여로 범위·만료일을 다시 열 수 없다. 환자 본인의 즉시 철회는 계속 허용한다.
- 브라우저와 Android의 `anon`, `authenticated`, `service_role`에는 업무 table 권한을 부여하지 않는다. 관리자 runtime은 조회만 가능하고 쓰기는 Core runtime만 수행한다.

## 상태 전이

| 예약 상태 | 세션 상태 | 허용 작업 |
| --- | --- | --- |
| `REQUESTED` | 없음 | 관리자 배정 |
| `MATCHED` | `READY` | 시작 전 취소 또는 동행 시작 |
| `IN_PROGRESS` | `MEETING`~`PAYMENT` | 매니저 진행 단계, 현장 메모와 현재 단계의 선택 첨부 갱신 |
| `IN_PROGRESS` | `CARE_ENDED` | 실제 동행은 종료됐고 선택 일지·최종 완료만 허용 |
| `COMPLETED` | `COMPLETED` | 리포트 상태가 `READY`면 조회, `FAILED`·`PENDING`이면 저장만 재시도 |
| `CANCELED` | `CANCELED` | 읽기 전용 |

상태 전이 API는 서버에서 현재 상태와 `version`을 확인하고 예약과 세션을 같은 DB 트랜잭션으로 갱신한다.

## Core API 계약

| endpoint | 읽기·쓰기 주체 | 처리 |
| --- | --- | --- |
| `GET /api/companion-sessions` | 환자·보호자·매니저 | 보호자는 `APPOINTMENT` 동의가 있는 세션만 포함 |
| `GET /api/companion-sessions/{id}` | 환자·보호자·매니저 | 보호자는 `APPOINTMENT` 동의 후 범위별 필드 마스킹 |
| `PATCH /api/companion-sessions/{id}` | 배정 매니저 | 현장 메모·약국 진행 상태를 `version` 조건으로 부분 갱신 |
| `POST /api/companion-sessions/{id}/advance` | 배정 매니저 | 고정 snapshot의 코드·순서·현재 범위와 `version`을 확인하고 예약 `IN_PROGRESS`와 세션 단계를 한 트랜잭션으로 갱신 |
| `GET /api/companion-sessions/{id}/report` | 환자·`REPORT` 동의 보호자·종료 전 배정 매니저 | `care_ended_at` 이후 배정 매니저의 리포트 원문 조회는 거부 |
| `PUT /api/companion-sessions/{id}/report` | 배정 매니저 | 선택 일지를 검증하고 세션 완료를 먼저 확정한 뒤 리포트를 저장·재시도하며, 응답은 식별자와 version만 남긴 확인용 형태로 마스킹 |
| `POST /api/companion-sessions/{id}/care-end` | 배정 매니저 | 현재 코드 `CARE_COMPLETION`과 `version`을 확인하고 최초 서버 시각을 보존한 채 `CARE_ENDED`로 전환 |
| `PUT /api/companion-sessions/{id}/artifacts` | 배정 매니저 | 현재 가이드 8·10에서 용도별 파일 전체를 멱등 교체 |
| `DELETE /api/companion-sessions/{id}/artifacts?purpose=...` | 배정 매니저 | 현재 단계의 해당 용도 메타데이터를 지우고 Storage 원본을 정리 |
| `GET /api/companion-sessions/{id}/artifacts/{artifactId}` | 환자·`ATTACHMENT` 동의 보호자·종료 전 배정 매니저 | `care_ended_at` 이후 배정 매니저의 가이드 첨부 원문 조회는 거부 |
| `GET /api/companion-sessions/{id}/realtime` | 환자·동의 보호자·종료 전 배정 매니저 | 보호자 채팅은 `CHAT`, 채팅 첨부 metadata는 `CHAT+ATTACHMENT`; 종료 후 위치와 매니저 snapshot은 거부 |
| `POST /api/companion-sessions/{id}/messages` JSON | 환자·보호자·배정 매니저 | `care_ended_at` 전까지만 허용하며 보호자는 `CHAT`, 첨부 metadata가 있으면 `ATTACHMENT`도 필수 |
| `POST /api/companion-sessions/{id}/messages` multipart | 환자·보호자·배정 매니저 | `care_ended_at` 전 `CHAT`과 `ATTACHMENT` 동의를 확인한 뒤 서버 중계 업로드 |
| `GET /api/companion-sessions/{id}/attachments/{attachmentId}` | 환자·동의 보호자·종료 전 배정 매니저 | 보호자는 `CHAT`·`ATTACHMENT`, 만료·삭제 상태를 모두 확인 |
| `PUT /api/companion-sessions/{id}/read-receipt` | 환자·`CHAT` 동의 보호자·종료 전 배정 매니저 | 보관 채팅을 읽는 환자와 동의 보호자는 종료 후에도 읽음 위치 갱신 가능 |
| `POST /api/companion-sessions/{id}/locations` | 배정 매니저 | 좌표·수집 시각·진행 상태 검증 후 최근 위치 기록 |
| `GET /api/appointments/{id}/follow-up` | 환자·보호자·배정 매니저 | 보호자는 별도 `REPORT` 동의 필수 |
| `PATCH /api/appointments/{id}/follow-up` | 환자·보호자 | 보호자는 `REPORT` 동의, 완료 상태와 `version` 필수 |

환자 본인의 예약 취소는 `REQUESTED`와 `MATCHED`에서만 허용한다. `MATCHED` 취소는 예약을 먼저 잠근 뒤 활성 세션을 `CANCELED`로 바꾸며, 세션 갱신이 실패하면 전체 트랜잭션을 rollback한다. 정보공유 동의는 대리권이 아니므로 보호자는 예약을 취소할 수 없다. 매니저는 배정된 예약 상세를 읽을 수 있지만 환자용 예약 생성·수정·취소 API는 사용할 수 없다.

## 권한 경계

| role | V5~V18 권한 |
| --- | --- |
| `bodeul_core_runtime` | 세션·리포트·후속 처리·채팅·가이드 첨부·읽음·위치 SELECT, 지정 컬럼 DML, 위치 기록 함수 EXECUTE |
| `bodeul_admin_runtime` | 세션·리포트·후속 처리·배정 감사·채팅·가이드 첨부·읽음·위치 SELECT, 배정 함수 EXECUTE |
| `anon`, `authenticated`, `service_role`, `public` | 업무 테이블과 서버 전용 함수 권한 없음 |
| `bodeul_migration` | Flyway DDL과 Firestore 백필 |

V6~V8은 Core API에 테이블 전체 권한이 아니라 실제 endpoint가 사용하는 컬럼 권한과 RLS 쓰기 정책만 추가한다. V8은 위치 table 직접 INSERT를 허용하지 않고 검증 함수 실행만 허용한다. DELETE 권한과 관리자 runtime의 광범위한 쓰기 권한은 부여하지 않는다.

## Android 전환 경계

- 예약·세션 진행·현장 메모·약국 상태·세션 리포트·예약 후속 처리는 Core API 응답을 화면 원본으로 사용한다.
- 매니저 세션 변경과 리포트 제출은 Core API의 `version` 조건부 요청으로 처리한다.
- 새 Android는 `CARE_COMPLETION`에서 `/care-end`를 호출하고, `CARE_ENDED`에서 선택 일지를 제출한다. 리포트가 `FAILED` 또는 `PENDING`이면 완료 세션을 다시 열어 리포트 저장만 재시도한다.
- 가이드 8은 결제 증빙 JPEG·PNG·PDF 1개, 가이드 10은 JPEG·PNG 0~3개를 Android Storage Access Framework로 선택하며 미첨부 진행을 허용한다.
- 후기·정산 확인 저장은 최신 후속 레코드를 조회한 뒤 해당 `version`으로 부분 갱신하며 Firestore `appointmentFollowUps`에 다시 쓰지 않는다. 값이 있는 신규 `supportEscalationStatus` 요청은 Core API가 거부하고 기존 값은 덮어쓰지 않는다.
- 채팅, 첨부 원본·metadata, 위치 좌표·이력·읽음 시각은 Core API를 사용한다. 첨부 미리보기는 인증된 API 응답을 앱 전용 단기 캐시에 저장한 뒤 `FileProvider` URI로 연다. 환자·매니저 화면은 진행 중에만 private Broadcast를 변경 신호로 받고 Core API snapshot으로 복구한다. 매니저 Android는 상태 문자열뿐 아니라 `careEndedAt`도 확인해 종료 세션의 Realtime 보강 요청을 생략하고 기존 구독을 닫는다. DB publisher는 종료 뒤 신호를 만들지 않으며 bootstrap 006은 신규·재인가 연결을 거부한다. 완료 이력은 `GET /report`를 호출하지 않고 세션 응답의 본인 `managerJournal`과 완료 메타데이터만 표시한다. 보호자 화면은 연결 권한 캐시로 철회 즉시성을 보장할 수 없어 Broadcast를 구독하지 않고 Core API polling만 사용한다.
- Firestore Rules는 예약·세션 진행·리포트·후속 처리뿐 아니라 `companionSessions`의 채팅·위치·읽음 client 쓰기도 거부한다. 기존 문서는 rollback 비교 자료로만 남고 환자·관리자 읽기만 유지한다. 매니저와 보호자는 종료 경계·마스킹·범위별 동의를 적용하는 Core API를 거친다.
- 예약 상세 observer는 Firestore 보조 데이터 listener와 10초 Core API 갱신을 함께 사용한다. 세션 원본을 Firestore에 다시 쓰지 않는다.
- 매니저 홈·이력과 보호자 진행 현황은 Core API 예약·세션 목록을 시작점으로 사용한다. 예약 응답의 배정 매니저 프로필도 PostgreSQL `app_users`에서 조합하므로 Firestore 예약·세션·리포트 문서가 없어도 운영 화면 모델을 만들 수 있다.
- Core API는 세션에 고정된 `guideId`, `guideRevision`, 상세 `steps`, `currentStepCode`, `canAdvance`, `blockedReason`을 반환한다. 각 step의 영상 계약은 URL이 아닌 선택 `videoAssetId`, `videoAssetVersion`, `videoFallbackText`만 포함하며 세 값이 모두 유효할 때만 등록된 자산으로 취급한다. Android Core 경로는 이 snapshot을 Manager·Booking·Guardian 화면 원본으로 사용하고, `steps` 키가 없는 구버전 응답에만 기존 fallback을 사용한다.

## 백필과 rollback

```powershell
npm --prefix tools/firebase run postgres:sessions:check -- --file backups/<백업 파일>.json
npm --prefix tools/firebase run postgres:sessions:rollback -- --file backups/<백업 파일>.json
npm --prefix tools/firebase run postgres:sessions:sql -- --file backups/<백업 파일>.json
```

적용 전 `check`, transaction rollback SQL, 적용 SQL 순서로 검증한다. 생성 SQL은 개인정보를 포함하므로 `tools/firebase/reports/`의 Git 제외 경로에만 둔다. V5 DDL rollback은 `core-api/db/rollback/V5__drop_companion_session_operational_schema.sql`, V18 종료·완료 분리 rollback은 `core-api/db/rollback/V18__merge_companion_care_completion.sql`을 사용한다. V18은 기존 `COMPLETED` 행의 원래 완료 시각, 채팅·첨부·위치 만료시각과 보호자 동의 만료 경계를 runtime에 공개하지 않는 baseline ledger에 기록한다. rollback은 남아 있는 legacy 원문과 동의를 원래 값으로 복원하며, ledger 대상 원문이 이미 파기됐으면 백업 없이는 fail-closed로 중단한다. 이후 종료·일지·리포트 상태가 바뀐 행, 신규 `CARE_ENDED`, 첨부 또는 operation ledger가 있으면 관련 데이터를 접근 제한된 운영 산출물로 export하고 Storage 원본까지 정리한 뒤 다시 실행한다. V18 이후 생성됐더라도 기존 상태와 V18 기본값만 가진 행은 rollback을 막지 않는다. 실제 schema rollback은 앱·Core API 쓰기와 신규 Realtime 연결을 중단한 maintenance 상태에서 실행하고, 성공 직후 bootstrap 006 rollback을 연속 실행해 V17 권한식을 복원할 때까지 트래픽을 다시 열지 않는다.

개발 DB 적용은 `Core API DB Migration` workflow의 `apply_companion_session_seed=true` 입력을 사용한다. 적용 SQL은 `core-api-migration-preview`의 일회성 `COMPANION_SESSION_SEED_SQL_BASE64` secret으로 전달하고, `companion_session_seed_sha256` 입력과 실제 파일 해시가 일치해야 한다. workflow 종료를 확인한 즉시 일회성 secret을 삭제한다.

개발 DB 백필은 run `29638905550` attempt 2에서 완료했다. 세션 2건, 리포트 2건, 후속 처리 1건의 FK와 `imported_at` 누락이 모두 0건이고, 예약·세션 상태 조합도 `COMPLETED/COMPLETED`, `IN_PROGRESS/IN_TREATMENT` 각 1건으로 일치했다. 일회성 secret은 실행 직후 삭제했다.

## 리스크와 전환 조건

- 개발 환경은 Core API와 관리자 서버가 PostgreSQL을 사용하고 Android의 대응 Firestore 쓰기를 중지해 전환 조건을 충족했다. production은 같은 migration과 역할별 종단 검증을 통과해야 전환 완료로 본다.
- Core API snapshot 응답은 V14 열을 전제로 하므로 V14 migration을 먼저 적용한 뒤 API를 배포한다. 코드 없는 `LEGACY_HOSPITAL_GUIDE_V0`는 의미를 추정하지 않고 진행을 차단하므로, 신규 배정 전에 운영 가이드를 코드 계약 v1으로 승격해야 한다.
- V18 코드가 새 열을 항상 읽으므로 DB migration을 Core API 배포보다 먼저 적용한다. 기존 `COMPLETED` 행은 리포트가 있으면 `READY`, 없으면 `FAILED`로 backfill하고 `care_ended_at`은 기존 완료·갱신·시작 시각 순으로 보존한다. V18은 세션 행 잠금과 DB trigger로 종료와 동시에 들어오는 채팅·첨부·위치·동의 재부여를 직렬화하고, `care_ended_at` 기준 TTL을 먼저 확정해 늦은 `COMPLETED` 전환이 보존 시각을 덮어쓰지 않게 한다. Flyway 뒤에는 postgres 권한으로 bootstrap 006과 권한 시나리오 015를 실행해야 기존 Realtime helper도 갱신된다.
- 구버전 Android의 마지막 단계 직접 완료는 `BODEUL_SESSION_COMPLETION_ENFORCEMENT=false` 동안만 허용한다. 이 혼합 버전 기간의 돌봄 종료는 `care_ended_at`과 단계만 저장하고 기존 `current_status` 문자열을 유지해 구버전 enum 파서를 깨뜨리지 않는다. 플래그를 켠 뒤에만 `CARE_ENDED`를 DB와 응답에 노출한다.
- MVP에서는 사고·긴급상황 전용 중단·지원 상태와 자동 연락을 제공하지 않는다. 향후 도입할 경우 정상 `CARE_ENDED`·`COMPLETED`와 구분한 새 계약으로 설계하고, 기존 legacy 값을 기능 계약으로 재사용하지 않는다.
- 가이드 첨부 교체가 DB 반영 전에 실패하면 생성한 Storage 객체를 즉시 삭제하지 않고 orphan으로 남긴다. 동시 요청의 승자 객체를 지우지 않는 fail-safe이며, 최종 orphan 회수와 보존 만료는 #222에서 커밋된 DB 참조 집합을 기준으로 처리해야 한다.
- 기존 배정의 관리자 actor는 Firestore에 없으므로 감사 기록을 추정해 만들지 않는다. 전환 이후 배정부터 기록한다.
- 기존 Firestore 세션 문서는 rollback 비교 자료로 남아 있으므로 운영 화면이 이를 업무 원본으로 다시 사용하지 않는지 회귀 검증한다.
- 개발 DB 백필 후 row/FK/상태 비교, 관리자 Preview 배정, 실기기 동행 완료와 rollback을 모두 통과해야 production migration 대상으로 승격한다.
- V6 Core 쓰기 권한은 개발 DB migration run `29639792606`에서 검증했다. Cloud Run Preview run `29639915209` 이후 실제 Firebase token으로 환자·보호자·매니저 목록 200, 관리자 목록 403, 환자 수정 403, 매니저 version 충돌 409를 확인했다.
- V7은 PostgreSQL 17 임시 인스턴스의 V1~V7 연속 적용과 개발 DB migration run `29642658596`을 통과했다. Core runtime의 후속 처리 생성·부분 수정은 version을 증가시키고 오래된 version 수정을 차단하며 `anon`, `authenticated`, `service_role`에는 권한이 없다. Preview 리비전 `00011-tp4`에서 환자 실기기 GET·PATCH 7건 200과 App Check `valid`, actor 일치를 확인했다.
- Android 실기기에서는 매니저 홈, 과거 이력, 보호자 리포트와 예약 상세가 PostgreSQL 세션 상태를 표시했다. 관리자 웹 PR #23의 Vercel Preview는 같은 개발 DB에서 배정 성공 201과 예약 `MATCHED`, 세션 `READY`, 감사 1건을 확인했다. Preview 리비전 `00012-tqv`에서는 Firestore 예약·세션 문서가 0건인 임시 Core-only 배정을 매니저 홈, 보호자 리포트, 환자 예약 상세에서 모두 확인했다. 관련 API 요청은 모두 200이고 App Check 판정은 `valid`였다.
- 개발 Rules emulator에서 예약·세션·리포트·후속 처리 비교 문서의 매니저·보호자 직접 읽기, 기존 세션 채팅·위치 client 쓰기와 Storage 채팅 첨부 client 쓰기 거부를 7개 시나리오로 검증했다. 환자 보관 조회와 관리자 운영 조회는 유지한다. Android 관리자 앱의 Firestore 직접 배정은 더 이상 운영 경로가 아니며 별도 관리자 웹 서버 API를 사용한다.
- 개발 DB V12 migration run `29650223504`, Cloud Run Preview deploy run `29651623086`과 개발 Firestore Rules 배포를 완료했다. 실제 세션의 채팅·읽음·위치·재연결, FCM 실기기 알림과 private Realtime 10개 동시 연결·10/10 Broadcast 수신을 확인했다.
- Core-only 첨부는 서버 중계 방식을 선택했다. 현재 30 MiB 이하 요청을 Core API 메모리에서 검증하므로 MVP 규모에는 적합하지만, 첨부 트래픽이나 파일 크기가 커지면 짧은 수명의 서명 URL과 완료 확인 API로 전환한다. Storage 저장 뒤 DB 저장이 실패한 객체는 즉시 보상 삭제하지 않고 #222의 일일 정리 작업이 커밋된 참조와 대조해 회수한다.
