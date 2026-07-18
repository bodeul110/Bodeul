# 예약 Core API 전환 계약

기준일: 2026-07-18

## 작업 목적

Firestore `appointmentRequests`에 직접 쓰던 예약 기본 흐름을 Spring Core API와 PostgreSQL의 단일 쓰기 경로로 옮긴다.

## 선택한 방식

- `appointment_requests`를 기존 read model에서 PostgreSQL 운영 원본으로 승격한다.
- Firebase ID token 검증 뒤 PostgreSQL `app_users.role`과 예약 참여자 UUID를 확인한다.
- 예약 가격, 최초 상태, 결제 상태와 요청자 식별자는 서버가 결정한다.
- 생성 요청에는 `clientRequestId`, 수정·취소에는 `version`을 사용한다.
- Core runtime에는 SELECT·INSERT·UPDATE만 주고 물리 DELETE는 허용하지 않는다.
- Admin runtime은 기존 SELECT-only를 유지한다.

## 대안

1. Android가 Supabase Data API에 직접 쓰는 방식은 서버 인가와 가격 계산을 우회할 수 있어 제외했다.
2. Firestore와 PostgreSQL에 계속 이중 쓰는 방식은 장애 시 어느 쪽이 원본인지 결정하기 어려워 제외했다.
3. 매칭·세션·채팅까지 한 번에 옮기는 방식은 rollback 범위가 너무 커 예약 CRUD와 분리했다.

## 선택 이유

현재 MVP 규모에서는 예약 CRUD 하나를 먼저 끝내 DB role, API 인가, Android 네트워크와 backfill 절차를 검증하는 것이 운영 위험이 가장 작다. PostgreSQL 원본 전환 경험을 확보한 뒤 같은 방식을 매칭·동행·리포트에 적용할 수 있다.

## API

| 메서드 | 경로 | 용도 | 성공 응답 |
| --- | --- | --- | ---: |
| `GET` | `/api/appointments` | 로그인 사용자가 환자 또는 보호자로 연결된 예약 목록 | 200 |
| `GET` | `/api/appointments/{id}` | 예약 상세 | 200 |
| `POST` | `/api/appointments` | 예약 생성 | 201 |
| `PUT` | `/api/appointments/{id}` | `REQUESTED` 예약 수정 | 200 |
| `POST` | `/api/appointments/{id}/cancel` | `REQUESTED` 예약 취소 | 200 |

모든 경로는 Firebase ID token이 필요하며 응답에 `Cache-Control: no-store`를 사용한다. 타인 예약은 403, 없는 예약은 404, 허용되지 않은 상태 전이와 오래된 `version`은 409를 반환한다.

## 서버 소유 값

- 기본 가격은 69,000원이다.
- 왕복은 22,000원, 보행 보조는 8,000원, 휠체어는 15,000원을 더한다.
- 첫 방문 쿠폰은 5,000원, 가족 쿠폰은 10,000원을 뺀다.
- 예약 최초 상태는 `REQUESTED`다.
- 현장 결제는 `DEFERRED`, 카드와 간편 결제는 실제 결제 서버 연동 전까지 `PENDING`으로 저장한다.
- 클라이언트가 전달한 가격이나 결제 승인값은 신뢰하지 않는다.

## 데이터와 권한

- `firestore_id`와 `imported_at`은 전환 전 생성된 legacy 행에만 값이 있다.
- `client_request_id`는 사용자별 중복 생성을 차단한다.
- `version`은 수정·취소 경쟁을 검출한다.
- `app_users.name`, `email`, `phone`은 현재 사용자 스냅샷과 연결 계정 조회에 사용한다.
- V4 적용 시 기존 예약의 최신 스냅샷으로 참여 사용자 프로필을 우선 채운다.
- 전체 환자·보호자 프로필 백필이 끝나지 않은 사용자의 예약 생성은 409로 차단한다.

## 단계 경계

매칭 이후 취소는 `companion_sessions`와 함께 처리해야 한다. 세션 source of truth가 아직 Firestore이므로 이번 API는 `MATCHED` 이후 취소를 409로 막고 #220에서 트랜잭션 경계를 함께 옮긴다. 채팅과 실시간 상세 갱신은 #221 범위다.

Android 전환은 다음 순서를 지킨다.

1. 개발 DB V4와 프로필 백필 검증
2. Core API 정상·401·403·404·409·503 확인
3. Firestore와 PostgreSQL 목록·상세 응답 비교
4. Android 기본 예약 메서드만 Core API로 전환
5. 개발 환경 Firestore 신규 예약 쓰기 0건 확인

## 리스크

- 프로필 연락처 중복이 있으면 연결 계정을 하나로 정할 수 없어 409를 반환한다.
- 현재 가격 규칙은 코드 상수이므로 운영자가 가격을 바꿔야 할 시점에는 별도 가격 정책 테이블이 필요하다.
- 실제 결제 승인 서버가 없으므로 카드·간편 결제 완료를 운영 사실로 간주하면 안 된다.
- source of truth 전환 뒤에는 Firestore 이중 쓰기로 rollback하지 않고 PostgreSQL 백업 또는 검증된 보정 절차를 사용한다.
