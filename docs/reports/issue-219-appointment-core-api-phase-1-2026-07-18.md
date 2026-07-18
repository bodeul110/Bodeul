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

- 개발 환경의 환자·보호자 예약 생성·수정·취소는 PostgreSQL과 Core API 단일 경로로 전환했다.
- Core API 실패 시 Firestore 예약 쓰기로 자동 대체하지 않는다.
- 기존 예약의 세션·채팅·후속 기록은 `legacyFirestoreId`로 Firestore에서 읽어 API 예약 정보와 합성한다.
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
| 개발 DB Flyway V4 적용 | [run `29636766787`](https://github.com/bodeul110/Bodeul/actions/runs/29636766787) 통과 |
| 개발 DB migration 이력 | V1~V4 모두 성공 |
| Supabase Security Advisor | 경고 0건 |
| Supabase Performance Advisor | 신규 인덱스 미사용 INFO만 존재 |
| Core API preview 최초 배포 | 생성자 주입 오류로 [run `29636860769`](https://github.com/bodeul110/Bodeul/actions/runs/29636860769) 실패 |
| 생성자 주입 회귀 수정 | [PR #226](https://github.com/bodeul110/Bodeul/pull/226) 병합, Spring context 생성 테스트 추가 |
| Core API preview 재배포 | [run `29637112253`](https://github.com/bodeul110/Bodeul/actions/runs/29637112253) 통과 |
| 실제 Firebase ID token 목록·상세 | Android 실기기에서 200 확인 |
| Android 예약 생명주기 | 생성 201, 수정 200, 취소 200 |
| PostgreSQL native 검증 예약 | 1건, `CANCELED`, `version=2`, 서버 가격 계산 일치 |
| Firestore `appointmentRequests` | 기존 4건 유지, 신규 이중 쓰기 0건 |
| Android `assembleDebug` | 통과 |
| Android `testDebugUnitTest` | 통과 |
| `git diff --check` | 통과 |

V4 적용 전에는 개발 Supabase 프로젝트에서 `BEGIN`과 `ROLLBACK` 사이에 전체 SQL을 실행해 현재 데이터 호환성을 먼저 확인했다. 실제 적용 후 Flyway 이력, 예약 4건의 version, 사용자 프로필, runtime ACL과 RLS를 다시 확인했다.

첫 preview revision은 `DefaultAppointmentService`의 운영 생성자와 테스트용 생성자 중 주입 대상을 Spring이 고르지 못해 시작 전에 종료됐다. 운영 생성자에 주입 대상을 명시하고 실제 `database` 프로필 Spring context 생성 테스트를 추가한 뒤 재배포했다. 재배포 workflow는 health 200과 무인증 auth/place 401을 통과했다.

실기기에서는 환자 기준선 계정으로 기존 예약 목록·상세를 조회해 Core API 200과 Firestore 세션 합성 화면을 확인했다. 이어 PostgreSQL native 예약을 생성하고 장소·메모를 수정한 뒤 취소했다. 서버 계산 결과는 기본가와 최종가 69,000원, 현장 결제 `DEFERRED`였고 DB 집계에서도 같은 값을 확인했다.

## 남은 범위

1. #220에서 매니저 예약 조회·매칭·동행 세션을 PostgreSQL로 옮긴다.
2. 매칭 완료 예약의 취소를 예약과 동행 세션의 같은 트랜잭션으로 구현한다.
3. #221에서 채팅·위치 실시간 경계를 Supabase Realtime로 옮긴다.
4. 전체 사용자 프로필을 production 전환 전에 백필하고 연락처 중복을 해소한다.
5. 매니저 경로 전환과 최종 데이터 대조 후 Firestore 예약 쓰기 권한을 제거한다.

관련 이슈: [#219](https://github.com/bodeul110/Bodeul/issues/219), [#220](https://github.com/bodeul110/Bodeul/issues/220)
