# Issue 27 무통장입금 MVP 구현·검증 기록

기준일: 2026-09-02

## 작업 목적

실제 계좌와 금전 수취를 활성화하지 않은 개발·preview 환경에서 무통장입금의 생성, 입금 대기, 운영자 확인, 취소·환불 상태 계약을 먼저 검증한다.

## 선택한 방식

- PostgreSQL과 Core API를 예약 금액과 결제 상태의 source of truth로 둔다.
- Android는 계좌정보가 없는 합성 안내만 제공하고 서버 결제 상태를 임의로 확정하지 않는다.
- 사용자 API는 본인 무통장입금 상태 조회와 입금자명 제출만 허용한다.
- 운영 상태 변경은 관리자 역할을 다시 검사하는 제한 `SECURITY DEFINER` 함수로만 허용하고, 일반 테이블 직접 쓰기와 사용자 호출은 차단한다.
- Firestore 예약 seed는 상세 원장과 감사 이벤트를 안전하게 초기화할 수 있는 `BANK_TRANSFER` + `AWAITING_DEPOSIT`만 이관한다.

## 대안

- 합성 계좌번호와 입금 기한을 앱 또는 seed 도구에서 자동 생성할 수 있다.
- Firestore를 결제 상태 원본으로 유지하고 PostgreSQL에 이중 쓸 수 있다.
- main 저장소에 관리자 HTTP API와 별도 관리자 웹 변경까지 함께 넣을 수 있다.

## 선택 이유

현재 MVP 규모에서는 실제 계좌 운영 조건이 준비되기 전에 상태, 권한, 멱등성과 감사 경계부터 검증하는 편이 안전하다. 결제 상태를 PostgreSQL/Core API 한 곳에서 결정하면 클라이언트의 임의 확인과 Firestore 이중 쓰기를 막을 수 있고, 향후 관리자 서버도 동일한 제한 전이 계약을 사용할 수 있다.

## 구현한 내용

### Android

- 결제 수단에 `BANK_TRANSFER`를 추가하되 신규 예약 기본값은 기존 `CARD`로 유지하고 Android debug 빌드에서만 무통장입금을 선택할 수 있게 했다.
- 알 수 없는 결제 수단·상태는 카드나 대기 상태로 바꾸지 않고 `UNKNOWN`으로 차단한다.
- 무통장입금 확인 화면은 운영 계좌가 없는 개발용 합성 모드임을 표시한다.
- 합성 확인 결과는 승인 코드·승인 시각·입금 완료 상태를 만들지 않는다.
- Mock, Firebase, Core API 저장 경계에서 알 수 없는 결제 수단을 거부한다.
- 입금 대기, 확인 완료, 검토, 환불 요청, 환불 완료, 취소 상태를 예약·운영 화면에서 구분한다.
- 기존 무통장입금 예약 편집에서는 결제 수단과 금액에 영향을 주는 옵션을 잠그고, 서버 상태와 무관한 Firestore 정산 완료 동작은 숨긴 뒤 실행 시점에도 다시 차단한다.

### Core API와 PostgreSQL

- `BANK_TRANSFER` 예약을 `AWAITING_DEPOSIT`으로 생성하고 결제 수단과 금액의 사후 변경을 막았다.
- 요청자 식별자와 정규화한 클라이언트 생성 입력의 SHA-256 지문을 불변 저장해 예약 수정, 현재 프로필·가격 파생값이나 예약 시각 경과와 무관하게 원래 요청 재시도와 다른 내용의 ID 재사용을 구분한다. 지문이 없는 legacy·seed 행은 재시도를 fail-closed한다.
- 사용자 결제 조회와 입금자명 제출 API를 추가하고 예약 소유권, 낙관적 결제 버전, 작업 ID 멱등성을 검사한다.
- 1:1 상세 원장과 append-only 결제 이벤트를 추가했다. 원장 테이블은 Core/Admin runtime의 직접 DML을 허용하지 않는다.
- 관리자 전이는 `SUPER_ADMIN` 또는 `OPERATIONS` 권한과 작업 ID, 결제 버전, 상태 순서를 다시 검사한다.
- 입금 확인 전 매니저 배정을 차단하고, 예약 취소 시 결제 상태를 원자적으로 취소 또는 환불 요청으로 옮긴다.
- 취소 후 입금과 금액·입금자·시점 이상은 자동 확정하지 않고 `REVIEW_REQUIRED`에서 검토하도록 했다.
- 계정 삭제 영향도에 무통장입금 상세 원장과 결제 이벤트 건수를 포함해 환자·관리자 관련 자료를 누락하지 않는다.
- migration, fail-closed rollback, 권한·상태·멱등성 검증 SQL과 GitHub Actions PostgreSQL 검증 단계를 추가했다. rollback은 데이터 gate 전에 관련 세 테이블을 배타 잠가 검사 직후 신규 쓰기가 들어오는 경합도 차단한다.

### Firestore seed

- `BANK_TRANSFER` 생성 상태인 `AWAITING_DEPOSIT`만 PostgreSQL projection으로 옮기고 계좌 안내·입금 기한·승인값은 만들지 않는다.
- 이미 서버 전이를 거친 5개 상태는 상세 원장과 이벤트를 함께 복원하는 별도 backfill 대상으로 차단한다.
- seed 재적용은 기존 PostgreSQL projection과 상세 원장이 다르면 SQLSTATE `55000`으로 중단하고 기존 행을 Firestore 값으로 덮어쓰지 않는다.
- rollback은 초기 원장과 단일 `CREATED` 이벤트를 확인한 뒤 이벤트, 상세 원장, 예약 순서로 삭제한다. 전이 이력이 있으면 전체 작업을 중단한다.

## 변경된 범위

- `app/`: 결제 수단·상태 모델, 예약·확인·운영 화면, 저장 경계와 회귀 테스트
- `core-api/`: 사용자 결제 API, 예약 서비스 계약, V22 migration·rollback·검증기와 테스트
- `tools/firebase/`: Firestore 예약 seed의 무통장입금 이관·재적용·rollback 보호
- `.github/workflows/core-api.yml`: PostgreSQL migration-contract 검증 단계
- `docs/architecture/appointment-core-api.md`: 상태, 권한, 원장, 운영 활성화 경계

Firebase Rules, Functions와 별도 `bodeul-admin-web` 저장소는 변경하지 않았다.

## 검증

| 검증 | 결과 |
| --- | --- |
| `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain` | Android 단위 테스트 180개 통과, debug APK 생성 |
| `.\core-api\gradlew.bat -p core-api check --rerun-tasks --console=plain` | Core API 테스트 387개 통과 |
| `npm --prefix tools/firebase run test:toolkit` | Firebase 도구 테스트 73개 통과 |
| `yq e '.' .github/workflows/core-api.yml` | YAML 파싱 통과 |
| migration 검증기 `bash -n` | 셸 구문 검사 통과 |
| `git diff --check` | 공백·형식 검사 통과 |
| PR #394 GitHub Actions | `preflight`, `scope`, Android/Core API `check`, CodeQL, Firestore emulator, PostgreSQL `migration-contract` 포함 8개 검사 통과 |
| `SM-S921N` / Android 16 실기기 | 앱 데이터를 지우지 않고 debug APK를 갱신 설치했다. 기본 카드 선택 유지, 무통장입금 전환, 69,000원 표시, 동의 누락 차단, 합성 접수 완료, `실결제 미연동`, 승인 번호·승인 시각 미생성을 확인했으며 crash/ANR은 없었다. |

로컬 Docker Desktop에서는 PostgreSQL 검증기를 실행하지 못했지만 PR의 PostgreSQL 17 `migration-contract`에서 V22 적용, 상태·권한 계약과 rollback을 실제 검증했다.

실기기 APK는 작업 트리에 Firebase 설정 파일이 없는 Mock 모드였다. 따라서 Android 합성 화면은 검증했지만 preview PostgreSQL V22 적용과 같은 revision의 Core API 배포를 전제로 하는 실제 API 종단 흐름은 아직 검증하지 않았다.

예약 입력 중 만남 위치 확인 버튼이 3버튼 시스템 탐색 영역에 가려지는 기존 화면 결함도 재현했다. #394가 해당 화면을 변경하지 않았으므로 무통장입금 PR에 섞지 않고 #395로 분리했다.

## 리스크

- 실제 계좌가 없는 합성 검증 상태를 production 수취 준비 완료로 간주하면 안 된다.
- 입금자명은 개인정보이므로 응답·로그·감사 이벤트에서 원문 노출 범위를 늘리지 않아야 한다.
- `DEPOSIT_CONFIRMED`, `REVIEW_REQUIRED`, `REFUND_REQUESTED`, `REFUNDED`, `CANCELED`인 legacy 자료는 projection만 seed할 수 없고 상세 원장·이벤트 backfill이 필요하다.
- 제한 DB 함수는 향후 관리자 서버가 호출할 내부 경계다. 브라우저나 Android에 DB 자격 증명을 제공하면 안 된다.
- 환불 상태는 수동 처리 기록이며 실제 은행 이체 완료를 자동 검증하지 않는다.
- Core API 신규 버전은 V22 migration을 먼저 적용해야 한다. 생성 지문이나 결제 원장이 쌓인 뒤에는 V22 rollback이 기본 차단되므로 백업과 보정 절차 없이 앱만 먼저 되돌리면 안 된다.

## 남은 범위

- Android의 사용자 결제 조회·입금자명 제출 API 연결과 실제 계좌 없는 preview 종단 검증
- 별도 관리자 서버와 관리자 웹의 제한 전이 함수 연결
- 운영 명의 계좌, 현금영수증, 취소·환불 절차와 접근 책임자 승인 뒤 production 활성화
