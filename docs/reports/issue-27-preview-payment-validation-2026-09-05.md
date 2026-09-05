# Issue 27 Preview 결제 환경 적용·검증

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

세 실패 판정을 별도 코드 `P0004`로 바꾸고 정적 회귀 검사를 추가했다. 적용된 V22 migration과 애플리케이션 동작은 변경하지 않았다.

## 변경된 범위

- `core-api/db/verification/018_bank_transfer_payment_checks.sql`: 실패 판정과 예상 도메인 오류 분리.
- `core-api/src/test/java/com/bodeul/core/appointment/BankTransferPaymentMigrationContractTests.java`: 검사 3곳의 구분 유지 확인.
- 이 보고서와 기존 무통장입금 구현 보고서의 후속 검증 링크.
- GitHub Preview 환경 설정 3개는 API 페이지 나눔 때문에 누락으로 오인해 이전 성공 배포값을 재입력했다. 전체 조회로 기존 설정임을 확인했으며, 새 환경이나 키를 만든 작업은 아니다.
- Android, 관리자 웹, Firebase Rules·Functions, production은 수정하지 않았다.

## 검증

| 항목 | 결과 |
| --- | --- |
| 최신 `master` Core API CI | `check`, `migration-contract`, `firestore-emulator` 통과: [#33947181569](https://github.com/bodeul110/Bodeul/actions/runs/33947181569) |
| Preview Flyway | V21·V22 성공을 DB에서 직접 조회 |
| 적용 전후 기존 주요 행 수 | 사용자 6, 예약 5, 동행 세션 2, 병원 가이드 1 유지 |
| 신규 결제 상세·이벤트 | 각각 0건. 실제 거래나 영구 합성 fixture를 만들지 않음 |
| 원장 RLS·소유자 | RLS 활성, `bodeul_migration` 소유 |
| 원장 직접 접근 | Core/Admin runtime과 공개 역할의 SELECT·DML 권한 없음 |
| 사용자 함수 | Core runtime의 본인 조회·입금자명 함수 실행 권한 있음 |
| 관리자 함수 | Admin runtime만 결제 전이 가능, Core runtime은 실행 권한 없음 |
| 역할 간 분리 | Admin runtime에 환자용 결제 조회·입금자명 함수 권한 없음 |
| Supabase security advisor | 신규 원장 2개의 `RLS Enabled No Policy` 정보성 알림. 직접 접근을 막고 제한 함수만 허용하는 계약이므로 공개 정책을 추가하지 않음 |
| 로컬 Core API | `.\core-api\gradlew.bat -p core-api check --console=plain`: 테스트 400개 통과 |
| Preview 합성 거래 SQL | 실행 불가. 연결된 Supabase 도구가 `SET ROLE bodeul_migration`에서 거부되어 fixture 생성 전 중단. 이후 기존 행 수와 원장 0건 재확인 |
| Preview 배포 | 동일 커밋 배포와 workflow smoke test 성공 |
| 직접 HTTP 재검사 | `/health` 200·`UP`, 무인증 auth·결제 GET·입금자명 PATCH 모두 401·`missing_authorization` |
| 잘못된 인증 토큰 | 결제 GET 401·`invalid_firebase_token` |
| Android 실기기 | ADB 연결 기기 0대, 미실행 |

RLS 정보성 알림의 의미는 [Supabase database linter](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)를 참고한다. 원장 접근 허용 정책을 추가해 알림을 없애면 이번 권한 경계를 바꾸게 된다.

HTTP 검사는 [Preview 서비스](https://bodeul-core-api-preview-cyvvxy3kia-an.a.run.app/health)의 임의 영(0) UUID 결제 경로로 수행했다. 인증 필터에서 거부된 결과이며, 결제 Controller의 정상 조회·저장이나 다른 사용자의 소유권 검사가 성공했다는 증거로 사용하지 않는다.

이번 작업에서는 별도 최신 DB dump나 원격 schema rollback을 실행하지 않았다. V21·V22의 적용·rollback은 격리 PostgreSQL CI 결과로 구분한다. 생성 지문·결제 이력이 쌓이면 V22 rollback이 차단되므로 이 기록을 production 백업·복원 증적으로 재사용하지 않는다.

## 남은 범위

- 정상 Firebase 인증 세션으로 합성 예약 생성, 본인 조회, 입금자명 저장·재조회, 중복·충돌·다른 사용자 거부를 실제 Preview HTTP 경계에서 검증한다.
- Android Firebase 모드 실기기에서 같은 흐름과 회전·재진입을 확인한다. Mock 검증과 구분한다.
- 별도 관리자 서버·웹의 조회와 입금 확인·검토·환불 상태 처리를 연결한다.
- 실제 계좌·수취·환불은 #27의 운영 게이트가 충족되기 전 활성화하지 않는다.

전체 구현 범위는 [무통장입금 MVP 구현·검증 기록](issue-27-bank-transfer-mvp-2026-09-01.md)과 [Issue #27](https://github.com/bodeul110/Bodeul/issues/27)을 따른다.
