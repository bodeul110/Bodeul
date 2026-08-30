# 예약 공개 코드 계약

상태: Flyway V19, Spring Core API와 Android에 구현했다. 관리자 정확 검색은 별도 관리자 웹 PR #43에서 검토 중이다. 이 변경은 개발 코드와 마이그레이션 산출물만 준비한 상태이며 production DB에는 적용하지 않는다.

## 작업 목적

내부 UUID를 사용자에게 전달하지 않고도 예약을 식별할 수 있는 짧은 코드를 제공하되, 코드만으로 예약 접근 권한을 얻지 못하게 한다.

## 선택한 방식

- 형식은 `BD-`와 영문 대문자·숫자 6자리의 조합이다.
- Core API가 `SecureRandom`으로 코드를 생성하고 최대 5회 충돌 재시도한다.
- PostgreSQL `appointment_requests.public_code`의 형식, `NOT NULL`, `UNIQUE`, 변경 금지 trigger가 계약을 강제한다.
- 기존 예약은 V19 migration에서 내부 UUID 기반의 결정적 후보를 만들고, 충돌 시 salt를 바꿔 최대 64회 backfill한다.
- 예약 생성 직후부터 신청자와 배정 매니저만 기존 예약 인가를 통과한 응답에서 코드를 받는다. 연결 참여자가 신청자가 아니면 `publicCode`를 빈 값으로 가린다.
- Android는 신청자 예약 상세와 배정 매니저 동행 가이드에서만 코드를 표시한다.
- 관리자 검색은 별도 관리자 웹의 `POST /admin/appointments/public-code`에서만 제공하고, 코드는 URL 접근 로그를 피하도록 JSON 본문으로 전달한다.
- 관리자 서버는 Firebase ID token, App Check와 PostgreSQL `ADMIN` 역할을 확인한 뒤 DB 함수로 정확 일치 검색한다.
- 관리자별 분당 10회로 제한하고, 검색 코드의 평문 대신 SHA-256 해시와 `FOUND`, `NOT_FOUND`, `RATE_LIMITED` 결과를 감사 테이블에 남긴다.

## 대안

- Firestore `reservationCodes/{publicCode}` 문서를 별도 원본으로 두는 방식은 PostgreSQL 예약 원본과 이중 쓰기·정합성 문제가 생겨 제외했다.
- UUID 앞자리나 순번을 노출하는 방식은 예측 가능성이 높아 제외했다.
- 코드만 입력하면 예약을 조회하는 공개 API는 권한 상승 위험 때문에 제외했다.

## 선택 이유

현재 MVP 규모에서는 36의 6제곱 후보 공간과 DB unique 제약, 제한된 재시도로 충분하고, 참가자 인가와 관리자 전용 검색 경계를 그대로 재사용할 수 있다.

## 리스크

- 공개 코드는 비밀값이나 인증 수단이 아니다. API와 화면은 항상 내부 UUID 및 참가자·관리자 인가를 기준으로 처리해야 한다.
- V19의 DB default는 migration과 새 Core API의 롤링 배포 사이에서 구버전 서버가 생성 요청을 처리할 수 있게 하는 호환 경로다. 새 Core API는 항상 직접 생성한 코드를 전달한다.
- 코드 수명은 예약 보존 기간을 따른다. 별도 영구 보관소는 만들지 않으며, 이후 보존 정책이 바뀌면 예약 삭제 계약과 함께 검토한다.
- 관리자 검색 감사 보존과 세부 관리자 역할 분리는 #349의 RBAC·감사 계약을 따른다.

## 적용 순서

1. 별도 개발 DB 백업과 V19 migration·rollback을 검증한다.
2. V19를 적용해 기존 예약 backfill과 구버전 Core API 호환 default를 확인한다.
3. 새 Core API와 Android를 배포해 생성·목록·상세·배정 매니저 표시를 확인한다.
4. 관리자 웹 Preview에서 정확 검색, 404, 분당 10회 제한과 감사 기록을 확인한다.
5. production 적용은 별도 Go/No-Go 승인과 복구 가능한 백업 증적을 갖춘 뒤 실행한다.

## 검증 항목

- Core API 생성 응답의 `publicCode`가 `^BD-[A-Z0-9]{6}$`을 만족한다.
- 코드 충돌 시 새 코드로 재시도하고 같은 `clientRequestId`는 기존 예약을 반환한다.
- 비참가자와 미배정 매니저는 기존 예약 상세 인가에서 거부된다.
- 관리자 검색은 부분 검색 없이 정확 코드만 받고, 비관리자·과다 요청을 거부한다.
- Firestore `reservationCodes`를 생성하거나 읽지 않는다.
- 개발 DB에 V19를 적용한 뒤 `core-api/db/verification/012_appointment_public_code_checks.sql`을 실행해 backfill 형식·중복, 불변 trigger, 감사 RLS와 runtime 최소 권한을 확인한다. 이 절차를 production DB에 실행하거나 V19를 production에 적용하지 않는다.
