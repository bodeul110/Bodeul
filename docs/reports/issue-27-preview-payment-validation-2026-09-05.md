# Issue 27 Preview 결제 환경·실기기 검증

기준일: 2026-09-05

## 작업 판단

- 작업 목적: 병합된 무통장입금 계약과 Preview DB·Core API 버전을 맞춰 실제 인증 요청을 검증할 준비를 한다.
- 선택한 방식: 같은 `master` 커밋의 migration과 배포를 별도 GitHub Actions로 실행한다. seed, retention fixture, 가이드 fixture는 실행하지 않는다.
- 대안: 로컬에서 DB와 Cloud Run을 직접 변경하거나 production까지 함께 갱신하는 방식을 검토했다.
- 선택 이유: 현재 MVP 규모에서는 기존 migration Environment와 WIF 배포 경계를 유지하는 것이 자격 증명 복제와 환경 혼동을 줄인다.
- 리스크: 배포 성공과 권한 메타데이터 검사는 실제 사용자 결제 흐름 성공을 보장하지 않는다. 실제 계좌 노출·수취·환불은 계속 차단한다.

## 구현한 내용

### Preview 환경

- 적용 커밋: `b0c0ea2c3b96eff8bf8c4cd3c4d6477175f1f59e`.
- 개발 DB에서 V20까지 적용된 상태와 V21·V22의 기존 데이터 충돌 여부를 읽기 전용으로 확인했다.
- [Preview migration #33947856797](https://github.com/bodeul110/Bodeul/actions/runs/33947856797)에서 V21·V22와 계정 삭제 영향도 계약 검증이 성공했다.
- migration 입력은 `target=preview`, `confirm_target=preview`, `apply_companion_session_seed=false`, 두 fixture action 모두 `none`이다.
- 같은 커밋의 [Core API Preview 배포 #33947991562](https://github.com/bodeul110/Bodeul/actions/runs/33947991562)가 성공했다. 배포 워크플로의 health·무인증 auth/place search 검사도 통과했다.
- 대상은 `bodeul-dev`의 `bodeul-core-api-preview`, 리전은 `asia-northeast1`이다. production 환경에는 적용하지 않았다.
- 배포 로그에서 `BODEUL_APP_CHECK_MODE=observe`, 기존 매니저 위치 공유 `false`, 진료 전 확인·동행 완료 강제 설정 `false`를 확인했다. 기존 강제 설정을 새로 켜지 않았다.

### 검증 SQL 보완

입금 전 매칭, 환불 요청 전 환불 완료, 취소 예약의 입금 확정 차단 검사에서 테스트 자체의 `RAISE EXCEPTION`도 `P0001`을 사용하고 있었다. 이 경우 함수가 잘못 성공해도 테스트 실패가 예상 도메인 오류로 잡혀 검사가 통과할 수 있다.

세 실패 판정을 별도 코드 `P0004`로 바꾸고 정적 회귀 검사를 추가했다. 이 SQL 보완은 적용된 V22 migration을 바꾸지 않는다. 아래 실기기 검증에서 발견한 JDBC 수정은 별도의 애플리케이션 변경이다.

### 인증·실기기 후속 검증

- `SM-S921N`에 현재 소스의 debug APK를 `adb install -r`로 설치했다. 기존 앱 데이터와 App Check 설정을 지우지 않았으며 회전 테스트 후 기기의 원래 자동 회전 설정도 복구했다.
- APK는 `bodeul-dev` Firebase 설정과 위 Preview API를 사용한다. 저장소의 개발용 환자 기준선 계정으로 로그인했고, Mock이 아닌 실제 예약 조회를 확인했다.
- 인증된 Core API 요청으로 `issue-27-device-20260905` 합성 예약 1건을 생성했다. Android에서 예약 상세의 무통장입금 화면 진입, 입금자명 입력·회전·다른 앱 전환, 저장과 재진입을 확인했다. 예약 생성 자체는 HTTP fixture 준비이며 Android 신청 폼의 전체 입력 흐름 검증과 구분한다.
- Android 저장 후 별도 API 재조회에서 동일한 합성 이름과 `paymentVersion=1`을 확인했다. 이후 HTTP 변경·중복·충돌 검사 뒤 앱을 다시 열어 최신 저장값도 확인했다.
- 실제 계좌는 제공하지 않았고 `instructionAvailable=false`와 앱의 실제 송금 금지 안내를 유지했다. 69,000원은 현행 서버 계산값을 확인한 것이며 새 가격 정책이나 입금 수취가 아니다.

### 취소 오류와 JDBC 보완

앱에서 합성 예약 취소를 누르면 오류 화면이 나타났으며, 같은 예약과 최신 버전으로 직접 요청해도 HTTP 503·`appointment_database_failure`가 재현됐다. DB의 예약은 `REQUESTED`, 결제는 `AWAITING_DEPOSIT`, 결제 버전은 2로 유지됐다. 취소 성공이나 테스트 데이터 정리 완료로 처리하지 않는다.

취소 경로의 `finalizeExpiryAfterCareBoundary`는 보호자 동의 만료 시각을 JDBC에 `Instant` 그대로 전달했다. [pgJDBC 공식 문서](https://jdbc.postgresql.org/documentation/query/#using-java-8-date-and-time-classes)는 `Instant`를 지원하지 않고 `timestamptz`에 `OffsetDateTime`을 사용하도록 명시한다. 실제 `JdbcClient`와 mock JDBC statement를 연결한 회귀 테스트에서도 기존 코드가 `Instant`를 넘기는 것을 확인했다. Cloud Run 로그는 로컬 Google CLI 재인증이 필요해 확인하지 못했으므로, 배포 후 취소 재검증으로 이 수정의 충분성을 확인해야 한다.

- 작업 목적: 예약 취소·동행 종료 시 동의 만료 시각 저장이 JDBC 바인딩 단계에서 실패하는 원인을 제거한다.
- 선택한 방식: 같은 Repository의 동의 부여·철회·만료 확정·감사 이벤트 쓰기 7곳에서 도메인 `Instant`를 UTC `OffsetDateTime`으로 변환한다.
- 대안: 동의 만료 처리를 생략하거나 예외를 무시하는 방식, DB 권한 확대와 스키마 변경은 채택하지 않았다.
- 선택 이유: 현재 MVP에서는 기존 시간 정책과 트랜잭션을 유지하면서 드라이버가 지원하는 타입으로 저장 경계만 고치는 것이 영향 범위가 가장 작다. UTC와 소수점 이하 시간도 유지한다.
- 리스크: 취소뿐 아니라 동의 부여·철회·감사 쓰기도 같은 코드 경계를 사용한다. 네 경로의 JDBC 바인딩 테스트를 추가했지만 수정본의 실제 Preview 취소와 동의 변경 흐름은 재검증이 필요하다.

## 변경된 범위

- `core-api/db/verification/018_bank_transfer_payment_checks.sql`: 실패 판정과 예상 도메인 오류 분리.
- `core-api/src/test/java/com/bodeul/core/appointment/BankTransferPaymentMigrationContractTests.java`: 검사 3곳의 구분 유지 확인.
- `core-api/src/main/java/com/bodeul/core/consent/JdbcGuardianSharingConsentRepository.java`: 쓰기 시각의 UTC JDBC 타입 변환.
- `core-api/src/test/java/com/bodeul/core/consent/JdbcGuardianSharingConsentRepositoryTests.java`: 동의 부여·철회·만료 확정·감사 이벤트의 실제 JDBC 바인딩 인자 검사 4개.
- 이 보고서와 기존 무통장입금 구현 보고서의 후속 검증 링크.
- GitHub Preview 환경 설정 3개는 API 페이지 나눔 때문에 누락으로 오인해 이전 성공 배포값을 재입력했다. 전체 조회로 기존 설정임을 확인했으며, 새 환경이나 키를 만든 작업은 아니다.
- Android, 관리자 웹, Firebase Rules·Functions, production은 수정하지 않았다.

## 검증

| 항목 | 결과 |
| --- | --- |
| 최신 `master` Core API CI | `check`, `migration-contract`, `firestore-emulator` 통과: [#33947181569](https://github.com/bodeul110/Bodeul/actions/runs/33947181569) |
| Preview Flyway | V21·V22 성공을 DB에서 직접 조회 |
| migration·배포 직후 기존 주요 행 수 | 사용자 6, 예약 5, 동행 세션 2, 병원 가이드 1 유지 |
| migration·배포 직후 결제 상세·이벤트 | 각각 0건. 이후 아래 실기기 검증에서 합성 예약 1건 생성 |
| 원장 RLS·소유자 | RLS 활성, `bodeul_migration` 소유 |
| 원장 직접 접근 | Core/Admin runtime과 공개 역할의 SELECT·DML 권한 없음 |
| 사용자 함수 | Core runtime의 본인 조회·입금자명 함수 실행 권한 있음 |
| 관리자 함수 | Admin runtime만 결제 전이 가능, Core runtime은 실행 권한 없음 |
| 역할 간 분리 | Admin runtime에 환자용 결제 조회·입금자명 함수 권한 없음 |
| Supabase security advisor | 신규 원장 2개의 `RLS Enabled No Policy` 정보성 알림. 직접 접근을 막고 제한 함수만 허용하는 계약이므로 공개 정책을 추가하지 않음 |
| 로컬 Core API | `.\core-api\gradlew.bat -p core-api check --console=plain`: JDBC 수정 후 테스트 404개 통과 |
| JDBC 회귀 검사 | 추가 4개는 수정 전 모두 실패, 수정 후 모두 통과. DB 서버 실행이 아니라 실제 `JdbcClient`에서 JDBC statement로 전달되는 인자 검사 |
| Preview 합성 거래 SQL | 앞선 시도는 연결된 Supabase 도구의 `SET ROLE bodeul_migration` 거부로 fixture 생성 전 중단. 이후 검증은 역할 확대 없이 인증된 Core API로 수행 |
| Preview 배포 | 동일 커밋 배포와 workflow smoke test 성공 |
| 직접 HTTP 재검사 | `/health` 200·`UP`, 무인증 auth·결제 GET·입금자명 PATCH 모두 401·`missing_authorization` |
| 잘못된 인증 토큰 | 결제 GET 401·`invalid_firebase_token` |
| Android 빌드·설치 | `.\gradlew.bat assembleDebug --console=plain` 성공, `SM-S921N`에 데이터 유지 설치 성공 |
| 정상 인증·합성 예약 생성 | `/api/auth/me` 200, `POST /api/appointments` 201, 결제 GET 200·`no-store`, 예상 금액 69,000원·버전 0 |
| Android 저장·재조회 | 화면에서 합성 입금자명 저장 후 API GET으로 동일 값·버전 1 확인 |
| 회전·백그라운드 | 가로·세로 회전과 다른 앱 전환 후 미저장 입력값 유지. 결제 화면 재진입 시 서버의 최신 값 표시 |
| 중복 PATCH | 같은 `operationId`·payload 재시도는 200, 응답·버전 동일. DB의 `DEPOSITOR_UPDATED`는 Android 저장 1건과 HTTP 변경 1건뿐 |
| 충돌·입력 거부 | 같은 작업 ID의 다른 payload 409·`payment_operation_conflict`, 오래된 버전 409·`appointment_version_conflict`, 빈 이름 400 |
| 보호자 권한 거부 | 개발용 보호자 계정의 결제 GET·PATCH 모두 403·`appointment_permission_denied`. 다른 환자 계정 간 소유권 검증과는 구분 |
| 앱·HTTP 취소 | 실패 재현. HTTP 503·`appointment_database_failure`, 기존 예약·결제 버전 유지 |
| 실기기 검증 후 DB | 기존 예약 5건 외 합성 예약 1건, 결제 원장 1건, `CREATED` 1건·`DEPOSITOR_UPDATED` 2건. 입금 확인·수취 금액·환불 기록 없음 |

RLS 정보성 알림의 의미는 [Supabase database linter](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)를 참고한다. 원장 접근 허용 정책을 추가해 알림을 없애면 이번 권한 경계를 바꾸게 된다.

무인증 검사는 [Preview 서비스](https://bodeul-core-api-preview-cyvvxy3kia-an.a.run.app/health)의 임의 영(0) UUID 경로에서 수행했다. 정상 인증·실기기 검사는 별도로 만든 합성 예약에서 수행했으며, 위 표에서 두 결과를 구분했다. 토큰·원문 인증 설정·개인 계좌·실사용자 정보를 이 보고서에 넣지 않는다.

이번 작업에서는 별도 최신 DB dump나 원격 schema rollback을 실행하지 않았다. V21·V22의 적용·rollback은 격리 PostgreSQL CI 결과로 구분한다. 현재 Preview에는 합성 예약의 생성 지문·결제 이력이 있어 V22 rollback 보호 조건에 해당한다. 테스트를 통과시키려고 원장·이벤트를 직접 삭제하지 않았으며 이 기록을 production 백업·복원 증적으로 재사용하지 않는다.

## 남은 범위

- PR #405 검토·병합 뒤 기존 Preview 배포 workflow로 JDBC 수정본을 배포하고, 이번 합성 예약의 취소를 재시도한다. 취소 후 원장 `CANCELED`·감사 이벤트, 앱 입력 잠금과 추가 PATCH 거부를 확인한다.
- 같은 JDBC 수정 경로의 동의 부여·철회와 서로 다른 환자 계정 간 결제 소유권은 실제 환경에서 추가 검증한다.
- 이번 범위는 기존 Android 결제 화면의 실서버 조회·저장 검증이다. Android 신청 폼의 전체 입력부터 결제까지 한 번에 거치는 시나리오와 관리자 처리를 모두 완료한 것으로 확대 해석하지 않는다.
- 별도 관리자 서버·웹의 조회와 입금 확인·검토·환불 상태 처리를 연결한다.
- 실제 계좌·수취·환불은 #27의 운영 게이트가 충족되기 전 활성화하지 않는다.

전체 구현 범위는 [무통장입금 MVP 구현·검증 기록](issue-27-bank-transfer-mvp-2026-09-01.md)과 [Issue #27](https://github.com/bodeul110/Bodeul/issues/27)을 따른다.
