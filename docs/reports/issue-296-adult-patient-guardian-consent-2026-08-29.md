# Issue 296 성인 환자 보호자 동의·철회 구현

기준일: 2026-08-29

## 작업 목적

예약 참여 관계를 보호자 열람 동의로 오인하지 않고, 성인 환자가 예약별 정보 범위를 직접 부여·철회하도록 PostgreSQL, Core API와 Android를 연결한다.

## 구현한 내용

- Flyway V17에 현재 동의, 정책 설정과 추가 전용 감사 이벤트를 추가했다.
- 정책 버전, 대상 보호자, 행위자, 성인 본인 확인 시각, 시작·만료·철회와 `version`을 저장한다.
- Core runtime에 필요한 조회·생성·갱신과 감사 이벤트 추가만 허용하고 공개 역할·Admin runtime 직접 접근을 차단했다.
- `GET`, `PUT`, `DELETE /api/appointments/{id}/guardian-sharing-consent`를 추가했다.
- #375 순수 판정 계약을 예약 ID까지 포함하도록 보강하고 예약·세션·채팅·첨부·리포트 인가에 연결했다.
- 기존 Functions 채팅·위치·예약 리마인더는 Admin SDK가 Rules를 우회하므로 보호자 발송을 fail-closed로 중단하고 환자·매니저만 유지했다.
- 보호자 Broadcast를 끄고 Android 보호자 화면은 매 요청 인가되는 Core API polling만 사용한다. 환자·매니저 Realtime은 유지한다.
- 보호자 신규 예약 생성을 환자 프로필 조회 전에 403으로 막고 Android에 환자 본인이 예약을 만든 뒤 공유해야 한다고 안내했다.
- 정보공유 동의를 예약 업무 대리 권한과 분리해 보호자의 예약 생성·수정·취소와 후속 기록 저장을 모두 403으로 막았다. 보호자 예약 응답은 일정·병원·상태만 남기고 참여자 식별자, 구체 만남 장소, 건강·결제 정보를 제거했으며 Android의 편집·취소·후속 저장 액션도 숨기거나 비활성화했다.
- Android 예약 상태 화면에 범위 선택, 성인 본인 확인, 임시·확정 만료 안내와 철회 화면을 연결했다. `ATTACHMENT`는 `CHAT`과 함께만 선택할 수 있다.
- 전환 비교용 Firestore 예약·세션·리포트·후속 처리와 Storage 첨부의 보호자 직접 접근을 차단했다.

## 선택한 방식과 이유

현재 MVP에는 생년월일 검증 원본이 없으므로 성인 환자 자기선언을 API 필수값으로 받고 확인 시각을 감사 데이터에 남겼다. 의사결정 능력 제한과 법정대리인 경로를 추정 구현하지 않고 기본 거부한다.

현재 정책 버전과 위치 기능 플래그는 DB 한 행을 Core API가 읽는다. Supabase Broadcast의 연결 중 권한 캐시는 철회 즉시성을 보장할 수 없어 보호자 구독을 전부 닫았다. 보호자는 Core API polling을 사용하고 환자·매니저만 기존 Realtime을 유지한다.

동행 중 지연으로 동의가 먼저 만료되지 않도록 최초 만료는 임시 상태로 저장한다. 예약 취소 또는 동행 완료가 기록되면 실제 시각에서 7일 뒤로 만료를 확정한다.

## 검증

| 검증 | 결과 |
| --- | --- |
| Core API 전체 `check` | 통과 |
| 동의·예약·세션·Realtime 관련 Core API 테스트 | 통과 |
| Functions Node 22 발송·보존 정책 테스트 | 40 통과, emulator fixture 3건 제외 |
| Android `assembleDebug` | 통과 |
| Android `testDebugUnitTest` | 통과 |
| Firestore·Storage Rules emulator | 7/7 통과 |
| V17 migration·rollback·검증 SQL 정적 계약 테스트 | 통과 |
| PostgreSQL 17 disposable CI 실제 apply·scenario·rollback 경로 | workflow 추가, 로컬 실행 환경 없음 |
| 개발 DB 실제 migration | 미실행 |
| production DB 적용·배포 | 미실행 |

Rules emulator에서는 관계만 있는 보호자의 예약, 세션, 리포트, 후속 처리와 첨부 직접 접근이 모두 실패하는 것을 확인했다. Core API 테스트에서는 미동의, 범위 불일치, 정책 버전 불일치, 만료, 철회, 다른 예약·보호자와 위치 비활성 상태를 기본 거부하고 알림 대상에서도 제외하는 것을 확인했다.

로컬 Docker daemon과 PostgreSQL이 실행 중이 아니어서 V17의 실제 PostgreSQL 적용·rollback은 수행하지 않았다. CI에는 disposable PostgreSQL 17에서 Flyway V17 적용, 권한 점검, `MATCHED` 취소·동의 만료 시나리오, 보호자 Broadcast 거부, bootstrap rollback, 데이터 존재 시 V17 rollback 중단, export 후 정리·rollback 검사를 순서대로 실행하는 경로를 추가했다. 운영 데이터 쓰기와 배포는 수행하지 않았다.

## 적용 전 남은 범위

1. 새 disposable PostgreSQL 17 CI 경로가 실제로 통과하는지 확인한다.
2. 개발 DB 백업 증적을 확인하고 Flyway V17을 적용한다.
3. `db/verification/012_guardian_sharing_consent_checks.sql`을 실행한다.
4. Realtime bootstrap 005를 적용하고 verification 004로 보호자 거부와 환자·매니저 허용을 검증한다.
5. Core API·Android·Rules 배포 뒤 미동의, 범위별 허용, 만료와 철회 즉시 차단을 실기기에서 확인한다.

## 남은 위험

- 성인 확인은 자기선언이며 공인 본인확인이나 생년월일 대조가 아니다.
- 기존 보호자는 새 동의가 없으면 차단되므로 배포 전 테스트 계정과 사용자 안내가 필요하다.
- 보호자는 15초 polling을 사용하므로 환자·매니저 Realtime보다 갱신이 늦을 수 있다.
- 실제 PostgreSQL migration·rollback과 장기 연결 Realtime 철회 동작은 아직 검증하지 않았다.
- 정책 버전 변경은 기존 동의를 전부 무효화하므로 재동의 계획과 함께 적용해야 한다.
