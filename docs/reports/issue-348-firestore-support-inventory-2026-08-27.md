# Issue 348 Firestore 지원 문서 부분 영향도

기준일: 2026-08-27

관련 이슈: [#348 탈퇴·삭제와 법정 보존 분리 구현](https://github.com/bodeul110/Bodeul/issues/348)

## 작업 목적

계정 삭제 준비도 API가 사용자 문서뿐 아니라 인증된 본인과 직접 연결된 이용자·매니저 문의 문서의 잔존 건수를 원문 없이 확인하도록 Firestore 부분 inventory를 확장한다.

## 선택한 방식

- 요청값이 아니라 인증 principal의 Firebase UID만 조회 키로 사용한다.
- `clientSupportRequests.userId`와 `supportInquiries.managerUserId`의 equality aggregation count만 실행한다.
- 사용자 문서 조회와 두 집계를 동시에 시작하고 전체 12초 제한을 공유한다.
- 하나라도 실패하면 성공한 일부 건수도 반환하지 않고 Firestore 출처 전체를 `ERROR`로 처리한다.
- 성공해도 Firestore는 `PARTIAL`, 전체 결과는 `NOT_EVALUATED`와 `complete=false`를 유지한다.

## 대안

일반 collection query로 문서를 받은 뒤 개수를 계산할 수 있지만 문서 ID와 문의 원문을 Core API 프로세스로 가져오므로 제외했다. 예약·세션 전환 잔존과 관리자 파생 문서를 같은 변경에서 모두 추적하는 방식은 간접 관계와 중복 집계 기준을 먼저 고정해야 하므로 후속으로 분리했다. 보관기간 승인은 실제 삭제·보존 실행 단계의 선행조건으로 유지한다.

## 선택 이유

현재 MVP 규모에서는 실제 삭제 실행기를 먼저 만들기보다 직접 소유 필드가 확정된 컬렉션부터 읽기 전용 건수로 확인하는 편이 과삭제와 개인정보 노출 위험을 줄인다. aggregation count는 필요한 활동 건수만 반환하고 문서 ID, 제목, 본문과 답변을 읽지 않는다.

## 리스크

- Firebase Admin SDK는 Firestore Rules가 아니라 런타임 서비스 계정 IAM을 사용하므로 실제 개발 환경 권한은 별도 실호출로 확인해야 한다.
- owner 필드가 없거나 잘못된 legacy 문서와 예약 ID로만 간접 연결된 문서는 이번 집계에서 확인되지 않는다.
- 세 조회는 단일 원자 snapshot이 아니므로 향후 삭제 승인 판단에는 재사용하지 않고 삭제 직전 재조회와 쓰기 차단이 필요하다.
- 문의 건수도 개인 활동 정보이므로 본인 인증과 `Cache-Control: no-store` 경계를 유지해야 한다.

## 구현 결과

- Firestore 응답에 `clientSupportRequests`, `supportInquiries` 건수를 추가했다.
- `users/{uid}` 문서가 없어도 두 문의 컬렉션의 잔존 건수는 계속 확인한다.
- 빈 UID는 Firestore 접근 전에 차단한다.
- 사용자 문서 또는 두 집계 중 하나라도 실패하면 `SOURCE_UNAVAILABLE`, 빈 counts로 닫힌다.
- UID, 문서 ID, 문의 원문, 파일명, Storage 경로와 raw 오류는 응답에 포함하지 않는다.

## 검증

- Firestore 저장소·서비스 집중 테스트와 Firebase 인증 통합 테스트 통과
- 본인 UID의 정확한 두 owner 필드, 0건·양수·사용자 문서 부재와 각 집계 실패 경계 검증
- Core API 전체 `check` 247건 통과, 실패·오류·건너뜀 0건

이번 검증은 모의 Firestore SDK와 통합 테스트 대역으로 수행했다. 실제 개발 Firebase 프로젝트와 Cloud Run 서비스 계정의 aggregation query 권한은 아직 확인하지 않았다.

이번 변경 뒤에도 #348과 `BoDeul 작업 백로그` 상태는 `Blocked`를 유지한다. 정책 승인과 저장소별 삭제·복구 검증이 끝나기 전에는 전체 이슈를 진행 완료로 바꾸지 않는다.

## 남은 범위

- 개발 Cloud Run 서비스 계정과 비식별 fixture를 사용한 aggregation 실호출
- 예약·세션 전환 잔존과 관리자 파생·간접 연관 Firestore 문서 영향도
- Firebase Storage 객체, Firebase Auth 사용자와 백업 영향도
- 실제 탈퇴 승인, 삭제 순서, 부분 실패 복구와 삭제 ledger
