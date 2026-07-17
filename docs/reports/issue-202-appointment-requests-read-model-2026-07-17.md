# 예약 요청 PostgreSQL read model 및 백필 준비

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Firestore `appointmentRequests`를 바로 대체하지 않고, 개발용 Supabase PostgreSQL에 같은 예약 요청을 반복 가능하게 적재할 read model과 검증 경로를 만든다. 이번 단계에서는 Android의 Firestore 접근과 source of truth를 변경하지 않는다.

## 선택한 방식

- Flyway `V3__create_appointment_requests.sql`이 private `bodeul` schema에 테이블을 만든다.
- Firestore 문서 ID와 사용자 UID는 기존 전환 규칙과 같은 deterministic UUID로 변환한다.
- 백필 전 사용자 참조, 필수 시각, 앱 enum, 위도·경도, 가격 계산식과 JSON 배열 형식을 검사한다.
- Core/Admin runtime에는 SELECT만 허용하고 migration role만 백필 SQL을 실행한다.
- apply SQL은 `firestore_id` 기준 upsert로 만들고, rollback SQL은 같은 백업에 포함된 문서 ID만 삭제한다.

## 대안

- Android 예약 API까지 한 번에 Spring으로 전환한다.
- 기존 전체 PostgreSQL seed 생성기를 현재 schema에 맞춰 한 번에 다시 작성한다.
- Firestore 문서 구조를 JSONB 한 컬럼에 그대로 저장한다.

## 선택 이유

현재 MVP 규모에서는 예약 읽기 모델과 백필 정확성을 먼저 확인하는 편이 앱 쓰기 경로까지 동시에 바꾸는 것보다 rollback 범위가 작다. 예약 요청은 이후 동행 세션과 리포트의 기준 FK이므로 먼저 관계와 식별자 규칙을 고정할 가치가 있다. 반면 전체 seed 도구를 한 번에 확장하면 아직 Flyway migration이 없는 테이블까지 같은 변경에 섞이므로 이번에는 예약 요청만 좁게 검증한다.

## 리스크

- Firestore 쓰기는 계속되므로 한 번 백필한 PostgreSQL row는 자동으로 최신 상태가 되지 않는다.
- 예약 정보에는 이름, 연락처, 이메일과 진료 관련 메모가 포함되므로 생성 SQL은 커밋하거나 공유할 수 없다.
- enum이나 가격 정책이 바뀌면 DB check constraint와 변환기 검증 목록도 같은 변경에서 갱신해야 한다.
- 이 read model을 운영 source of truth로 전환하려면 Spring 예약 API, 데이터 비교, write 소유권과 rollback 조건이 추가로 필요하다.

## 로컬 검증 결과

개인정보 값은 출력하지 않고 2026-07-17 개발 Firestore 백업의 구조와 참조만 검사했다.

| 항목 | 결과 |
| --- | --- |
| `users` | 6건 |
| `appointmentRequests` | 4건 |
| 예약 상태 | `REQUESTED`, `IN_PROGRESS`, `COMPLETED` |
| 요청자 역할 | `PATIENT`, `GUARDIAN` |
| patient/guardian/requester 참조 누락 | 0건 |
| manager 참조 | 2건, 누락 0건 |
| 필수 예약 시각·날짜·병원명 누락 | 0건 |
| 가격 음수·계산식 불일치 | 0건 |
| 전용 백필 dry-run | 4건, 오류 0건 |
| Firebase 도구 테스트 | 25건 통과 |
| Core API Gradle `check` | 통과 |
| `git diff --check` | 통과 |
| 개발 DB Flyway V3 | migration run `29557164927` 통과 |
| `appointment_requests` | 0건, owner `bodeul_migration`, RLS 활성화 |
| runtime/public 권한 | Core/Admin SELECT만 허용, 공개 role 조회·쓰기 없음 |
| Supabase Security Advisor | 경고 0건 |

백업에는 ISO-8601 timestamp와 13자리 epoch millis로 저장된 seed 문서가 함께 있었다. 변환기는 두 형식과 Firestore seconds/nanos 형식을 UTC ISO 시각으로 정규화한다.

## 남은 범위

- 커밋 제외된 apply SQL로 예약 요청 4건 백필
- 백필 후 Firestore/PostgreSQL row 수와 사용자 FK 확인
- Spring 예약 read API와 Firestore/PostgreSQL 응답 비교는 후속 작업으로 분리

Flyway V3, RLS, 권한과 Security Advisor 확인은 완료했다. 백필은 Supabase 관리 연결이 INSERT와 `SET ROLE bodeul_migration` 권한을 갖지 않아 0건 상태로 중단했다. 관리 연결 권한을 넓히지 않고 migration 전용 실행 경로를 사용할지 별도 승인 후 결정한다.

관련 이슈: [#202](https://github.com/bodeul110/Bodeul/issues/202), [#154](https://github.com/bodeul110/Bodeul/issues/154)
