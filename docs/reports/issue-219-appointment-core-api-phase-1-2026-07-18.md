# 예약 PostgreSQL source of truth 전환 1단계 검증

기준일: 2026-07-18

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Firestore `appointmentRequests`에 남아 있는 예약 쓰기 책임을 PostgreSQL과 Spring Core API로 옮기기 전에, 운영 테이블 전환 migration과 인증된 예약 API를 구현하고 개발 DB에서 적용 가능성을 검증한다.

## 구현한 내용

- Flyway V4가 기존 예약 read model을 PostgreSQL 운영 테이블로 승격한다.
- 앱 사용자 프로필에 예약 참여자 확인에 필요한 이름, 이메일, 연락처를 추가한다.
- Core runtime에는 예약 생성·수정 권한만 부여하고 물리 삭제 권한은 부여하지 않는다.
- Firebase ID token 인증 뒤 환자·보호자 역할과 예약 참여 관계를 검사한다.
- 예약 목록, 상세, 생성, 수정, 취소 API를 추가한다.
- 생성 요청에는 `clientRequestId`, 수정·취소에는 `version`을 사용해 중복 생성과 동시 수정을 방지한다.
- 금액과 결제 상태는 클라이언트 입력을 신뢰하지 않고 서버 정책으로 계산한다.
- 운영 row가 생성된 뒤에는 V3 read model로 잘못 되돌아가지 않도록 rollback guard를 둔다.

## 경계와 판단

- PostgreSQL이 예약의 source of truth가 되는 시점은 V4 적용, Core API 배포, 인증 요청 검증, Android 전환이 모두 끝난 뒤다.
- 이번 코드만 병합해도 Android는 즉시 전환되지 않으며 Firestore 쓰기 경로는 유지된다.
- 매칭 완료 예약 취소는 `companion_sessions`가 아직 Firestore에 있으므로 이번 API에서 허용하지 않는다. 예약과 동행 세션을 같은 트랜잭션 경계로 옮기는 후속 단계에서 처리한다.
- 예약 row는 취소 상태로 보존하고 삭제하지 않는다. 감사와 운영 이력 때문에 runtime role에도 DELETE를 허용하지 않는다.

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| Core API Gradle `check` | 통과 |
| 예약 서비스 단위 테스트 | 가격 계산, 참여자 식별, 멱등성, 역할 제한, 낙관적 잠금, 취소 경계 통과 |
| 예약 API 통합 테스트 | 인증, 목록, 생성, 수정, 오류 응답, DB 장애 응답 통과 |
| Flyway V4 계약 테스트 | migration, rollback guard, 권한·RLS 계약 통과 |
| 개발 Supabase V4 트랜잭션 dry-run | SQL 전체 실행 후 rollback 성공 |
| 기존 예약 row | 4건, `version` 준비 4건 |
| 사용자 프로필 | 2건, 예약 API 준비 미완료 0건 |
| Core runtime 쓰기 권한 | INSERT, UPDATE만 존재 |
| dry-run 종료 후 V4 영구 반영 여부 | 미반영 확인 |
| `git diff --check` | 통과 |

dry-run은 개발 Supabase 프로젝트에서 `BEGIN`과 `ROLLBACK` 사이에 V4 전체 SQL을 실행했다. 실제 schema version과 데이터는 변경하지 않았으며, 이번 결과는 현재 데이터와 migration 문법의 호환성 검증이다.

## 남은 범위

1. 개발 DB에 Flyway V4를 실제 적용하고 권한, RLS, advisor 결과를 다시 확인한다.
2. Core API preview를 배포하고 미인증 401 및 실제 Firebase token 기반 예약 요청을 검증한다.
3. Android 예약 Repository를 Core API로 전환하고 생성·수정·취소·재조회 실기기 흐름을 검증한다.
4. Firestore 예약 쓰기를 차단하기 전에 데이터 대조와 rollback 기준을 충족했는지 확인한다.
5. 매칭·동행 세션을 PostgreSQL로 옮긴 뒤 매칭 완료 예약의 취소 트랜잭션을 구현한다.

관련 이슈: [#219](https://github.com/bodeul110/Bodeul/issues/219), [#220](https://github.com/bodeul110/Bodeul/issues/220)
