# Issue 348 Firestore 예약·세션 직접 참조 영향도

기준일: 2026-08-27

관련 이슈: [#348 탈퇴·삭제와 법정 보존 분리 구현](https://github.com/bodeul110/Bodeul/issues/348)

## 작업 목적

계정 삭제 준비도 API가 Firestore 예약 요청과 동행 세션에서 인증된 사용자 UID가 현재 어느 역할 필드에 직접 남아 있는지 원문 없이 확인하도록 부분 inventory를 확장한다.

## 선택한 방식

- `appointmentRequests`의 환자·보호자·매니저·요청자 UID와 `companionSessions`의 환자·보호자·매니저 UID를 필드별 aggregation count로 조회한다.
- 문서를 가져오는 일반 query는 사용하지 않고 각 역할의 건수만 반환한다.
- 사용자 문서 정확 조회와 기존 지원 집계 2개를 포함한 총 10개 작업을 먼저 시작하고 하나의 12초 제한을 공유한다.
- 하나라도 실패하면 시작된 작업을 모두 취소하고 Firestore 출처 전체를 `ERROR`, 빈 counts와 `SOURCE_UNAVAILABLE`로 처리한다.
- 성공해도 Firestore는 `PARTIAL`, 전체 결과는 `NOT_EVALUATED`와 `complete=false`를 유지한다.

## 대안

예약·세션마다 여러 역할을 `OR`로 묶어 고유 문서 수를 반환하는 방식도 검토했다. 하지만 요청자가 환자나 보호자와 같은 정상 데이터가 있어 역할별 건수를 단순 합산할 수 없고, 고유 문서 수는 사용자가 어떤 관계로 남아 있는지 가린다. 따라서 이번 범위에서는 합계를 만들지 않고 역할별 직접 참조를 그대로 노출한다.

`sessionReports.sessionId`, `appointmentFollowUps.requestId`, 관리자 정산·긴급·감사 문서처럼 다른 문서 ID로 이어지는 간접 관계는 역참조 계약과 중복 기준이 필요하므로 후속 범위로 분리했다.

## 선택 이유

현재 MVP 규모에서는 모든 legacy 관계를 한 번에 추론해 삭제 후보를 만드는 것보다 저장 계약이 확인된 직접 UID 필드부터 읽기 전용으로 세는 편이 과삭제 위험이 낮다. aggregation count는 문서 ID, 예약 내용과 세션 내용을 Core API 프로세스로 가져오지 않으면서 잔존 관계를 역할별로 확인할 수 있다.

## 리스크

- 현재 UID 필드가 없는 legacy 문서는 equality query에서 제외되므로 0건도 과거 관계 부재를 증명하지 않는다.
- 역할별 건수는 서로 중복될 수 있으므로 합계나 고유 문서 수로 해석하면 안 된다.
- Firebase Admin SDK는 Firestore Rules가 아니라 런타임 서비스 계정 IAM을 사용하므로 개발 Cloud Run 자격의 실제 aggregation 권한은 별도 실호출 전까지 미검증이다.
- 여러 aggregation은 단일 원자 snapshot이 아니므로 삭제 승인 판단에 재사용하지 않고 실제 삭제 직전 재조회와 쓰기 차단이 필요하다.

## 구현 결과

- 응답에 `appointmentRequestsAsPatient`, `appointmentRequestsAsGuardian`, `appointmentRequestsAsManager`, `appointmentRequestsAsRequester`를 추가했다.
- 응답에 `companionSessionsAsPatient`, `companionSessionsAsGuardian`, `companionSessionsAsManager`를 추가했다.
- 예약·세션 합계 key를 만들지 않았으며, `requesterUserId`는 소유권이 아니라 요청 생성자 직접 참조로 구분했다.
- `users/{uid}`가 없어도 지원·예약·세션 직접 참조 집계를 계속 수행한다.
- 기존 읽기 전용·미판정·미완료와 민감 원문 비노출 계약을 유지했다.

## 검증

- Firestore 저장소 집중 테스트에서 9개 aggregation의 collection·field·인증 UID 연결과 일반 query 미사용을 확인했다.
- 사용자 문서 부재, 모든 집계 0건, 9개 각 집계 실패, 첫 작업 timeout 시 시작된 10개 작업 취소, interrupt와 동기 query 준비 실패 경계를 확인했다.
- 서비스와 인증 통합 테스트에서 7개 역할별 응답 key, 합계 key 부재, `PARTIAL`, `NOT_EVALUATED`, `complete=false`와 `no-store`를 확인했다.
- Core API 전체 `check` 257건과 `git diff --check`를 통과했다. 실패·오류·건너뜀은 0건이다.

이번 검증은 모의 Firestore SDK와 통합 테스트 대역으로 수행했다. 실제 개발 Firebase 프로젝트와 Cloud Run 서비스 계정의 aggregation query 권한, 비식별 fixture 결과는 확인하지 않았다.

이번 변경 뒤에도 #348과 `BoDeul 작업 백로그` 상태는 `Blocked`를 유지한다. 정책 승인과 저장소별 삭제·복구 검증이 끝나기 전에는 전체 이슈를 진행 완료로 바꾸지 않는다.

## 남은 범위

- 개발 Cloud Run 서비스 계정과 비식별 fixture를 사용한 aggregation 실호출
- UID 필드가 없는 legacy 예약·세션과 관리자 파생·간접 연관 Firestore 문서 영향도
- Firebase Storage 객체, Firebase Auth 사용자와 백업 영향도
- 실제 탈퇴 승인, 삭제 순서, 부분 실패 복구와 삭제 ledger
