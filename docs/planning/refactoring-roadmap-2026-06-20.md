# 리팩토링 로드맵

기준일: 2026-06-23

이 문서는 2026-06-20 리팩토링 라운드에서 `지금 손대야 하는 리팩토링`만 추려 우선순위와 분할 기준을 고정한 계획 문서다.

2026-06-23 기준으로 이 문서의 `#7` ~ `#10` 계획 범위는 완료됐다. 아래 우선순위는 당시 실행 계획과 완료 기준을 보존한 기록이며, 최신 구조 요약은 [현재 구현 상태](../status/implementation-status.md)를 우선한다.

## 판단 기준

- 기능 동작은 이미 닫혀 있고, 새 기능 추가 시 영향 범위가 과도하게 커지는 구간만 우선 대상으로 본다.
- 단순 파일 길이보다 `서로 다른 책임이 한 파일에 섞여 있는지`를 더 중요하게 본다.
- 전면 재작성 대신 `기존 동작 유지 + 모듈 분리` 방식으로 진행한다.

## 계획 범위와 현재 상태

### 1. Firebase Functions 분리

- 현재 상태
  - 완료.
  - `functions/index.js`는 `initializeApp()`과 모듈 export 집계만 맡는다.
  - 인증, 알림, 액션 전달, 동기화, 리마인더 로직은 `functions/src/` 아래 파일로 분리돼 있다.
- 목표
  - `auth`
  - `reminder`
  - `support`
  - `chat`
  - `location`
  - `notification token`
  축으로 모듈을 나눈다.
- 완료 기준
  - 엔트리 파일은 export 조립 역할만 남긴다.
  - 각 기능군이 별도 파일에서 테스트 가능한 helper 단위로 분리된다.

### 2. 관리자 앱 화면/저장소 분리

- 현재 상태
  - 완료.
  - 관리자 앱 주요 섹션은 `Admin*SectionController`로 분리됐다.
  - `FirebaseAdminRepository`의 요청, 운영, 가이드, 서류, 문의, 액션센터 저장 축은 기능별 store/mapper로 분리됐다.
- 목표
  - `request`
  - `operations`
  - `guide`
  - `manager documents`
  - `support`
  - `action center`
  - `action delivery`
  축으로 화면/저장소 책임을 나눈다.
- 완료 기준
  - `AdminActivity`는 탭 전환과 공통 흐름만 남긴다.
  - Firebase 관리자 저장소는 기능군별 delegate/helper로 분리된다.

### 3. Mock 저장소 분리

- 현재 상태
  - 완료.
  - 예약, 매니저, 문의, 관리자 축을 `Mock*Store`로 나누고, `MockBodeulRepository`는 공유 상태와 helper 제공 범위로 좁혔다.
- 목표
  - `mock booking`
  - `mock manager`
  - `mock support`
  - `mock admin`
  식으로 역할을 나눈다.
- 완료 기준
  - 기능군별 mock 구현이 분리되고, 루트 저장소는 조합과 공유 상태만 관리한다.

### 4. 관리자 웹 구성 분리

- 현재 상태
  - 완료.
  - `AdminAuthScreen`, `AdminShell`, `ManagerApprovalList`, `ManagerReviewModal`, `useAdminIdleSession`, `useManagerDocumentPreviews`로 분리했다.
  - `admin-web/src/App.tsx`는 인증 상태, 목록 구독, 저장 액션, 화면 전환을 조합하는 셸 역할로 줄었다.
- 목표
  - `auth shell`
  - `manager review list`
  - `review modal`
  - `preview resolver hook`
  - `idle session handler`
  로 나눈다.
- 완료 기준
  - `App.tsx`는 라우팅 수준의 셸 역할만 남긴다.
  - 서류 미리보기와 심사 모달 로직은 분리된 컴포넌트/훅으로 이동한다.

## 이번 라운드에서 하지 않는 것

- 결제/정산 실운영 연동
- OCR 기반 복약 비교
- 위치/GPS 기능 자체 추가
- UI 재디자인 목적의 대규모 화면 재작성

이 항목들은 리팩토링이 아니라 기능 확장 또는 운영 정책 결정이 더 먼저 필요하다.

## 완료된 작업 순서

1. Functions 분리
2. 관리자 앱 분리
3. Mock 저장소 분리
4. 관리자 웹 분리

이 순서를 유지했던 이유는 다음과 같다.

- Functions는 배포 영향 범위가 가장 넓다.
- 관리자 앱/저장소는 내부 복잡도가 가장 높다.
- Mock 저장소는 분리 이후 테스트 유지 비용을 줄인다.
- 관리자 웹은 현재 기능은 안정적이라 네 번째가 적절하다.

## GitHub 이슈 기준

- 추적용 상위 이슈 1건
- 실행용 하위 이슈 4건
- 각 이슈는 이 문서를 기준 문서로 링크한다.

### 연결 이슈

- [#6 리팩토링 구조 정리 트래킹](https://github.com/bodeul110/Bodeul/issues/6)
- [#7 Firebase Functions 모듈 분리](https://github.com/bodeul110/Bodeul/issues/7)
- [#8 관리자 앱 화면 및 저장소 분리](https://github.com/bodeul110/Bodeul/issues/8)
- [#9 Mock 저장소 분리](https://github.com/bodeul110/Bodeul/issues/9)
- [#10 관리자 웹 App.tsx 분리](https://github.com/bodeul110/Bodeul/issues/10)

## 메모

- 현재 기준선 검증은 `assembleDebug`, `testDebugUnitTest`, `admin-web lint/build`, `functions/index.js` 문법 검사까지 통과 상태다.
- 리팩토링 중에는 기능 추가를 섞지 않는 편이 맞다.

## 진행 메모

- 2026-06-20 1차 진행
  - `#7` 기준으로 `functions/src/auth.js`, `functions/src/notifications.js`를 먼저 분리했다.
  - `functions/index.js`는 인증/알림 export를 집계만 하도록 줄였다.
  - `reminder`, `action delivery`, `sync` 계열은 다음 단계에서 분리한다.
- 2026-06-20 2차 진행
  - `functions/src/sync.js`를 추가하고 `syncLinkedAppointmentParticipants`, `cleanupAppointmentReminderJobs`를 분리했다.
  - 사용자-예약 연결 규칙과 예약 알림 정리 규칙의 수정 범위를 `sync.js`로 좁혔다.
- 2026-06-20 3차 진행
  - `functions/src/action-delivery.js`를 추가하고 관리자 후속 푸시 전달 배치와 수동 재실행 callable을 분리했다.
  - `functions/index.js`에 남아 있던 알림 export 중복 선언도 함께 제거해 집계 파일 역할을 더 분명하게 맞췄다.
- 2026-06-20 4차 진행
  - `functions/src/reminders.js`를 추가하고 예약 알림 생성/발송/수동 재실행 흐름을 분리했다.
  - `functions/index.js`를 `initializeApp()` + 모듈 집계만 남는 형태로 줄여, Functions 루트 파일 역할을 명확히 정리했다.
- 2026-06-20 5차 진행
  - `#8` 기준으로 관리자 문의 축을 먼저 분리했다.
  - `AdminActivity`의 문의 스냅샷, 필터, 응답 다이얼로그, 빈 상태 렌더링을 `AdminSupportSectionController`로 옮겼다.
  - `FirebaseAdminRepository`에서는 문의 응답 저장 규칙과 문서 매핑을 각각 `FirebaseAdminSupportStore`, `FirebaseAdminSupportMapper`로 분리했다.
  - 다음 단계는 관리자 서류 심사 축 또는 관리자 운영 액션 축 분리다.
- 2026-06-20 6차 진행
  - `#8`의 두 번째 축으로 관리자 서류 심사 섹션을 분리했다.
  - `AdminActivity`의 서류 목록, 승인/반려 다이얼로그, 파일 미리보기, 이력 다이얼로그를 `AdminManagerDocumentSectionController`로 옮겼다.
  - `FirebaseAdminRepository`에서는 서류 검토 트랜잭션을 `FirebaseAdminManagerDocumentStore`로 분리했다.
  - 다음 단계는 관리자 운영 액션 축 또는 관리자 요청 축 분리다.
- 2026-06-20 7차 진행
  - `#8`의 세 번째 축으로 관리자 액션 센터 UI를 분리했다.
  - `AdminActivity`의 액션 센터 요약/필터/목록 렌더링을 `AdminActionCenterSectionController`로 옮겼다.
  - 읽음/해결 저장은 액티비티에 남겨 두고, 다음 단계에서 저장소 분리로 이어간다.
- 2026-06-20 8차 진행
  - `#8`의 남은 액션 센터 저장 축을 `FirebaseAdminActionCenterStore`로 분리했다.
  - `FirebaseAdminRepository`는 액션 센터 읽음/해결 요청을 위임하고, 저장 규칙과 감사/전달 아티팩트 생성은 store에서 닫도록 정리했다.
  - 다음 단계는 관리자 요청 관리 축 분리 또는 `#8` 마무리 범위 점검이다.
- 2026-06-20 9차 진행
  - `#8`의 요청 관리 축을 `AdminRequestSectionController`로 분리했다.
  - `AdminActivity`는 요청 저장 액션만 남기고, 대기/관리 요청 섹션의 필터/확장/빈 상태/카드 렌더링은 컨트롤러가 맡도록 정리했다.
  - 다음 단계는 `#8` 범위 점검 후 이슈 마감 또는 액션 전달 축 추가 정리다.
- 2026-06-20 10차 진행
  - `#8`의 action delivery 축을 `AdminActionDeliverySectionController`로 분리했다.
  - `AdminActivity`는 액션 전달 데이터 바인딩만 남기고, 요약/빈 상태/목록 렌더링은 섹션 컨트롤러가 맡도록 정리했다.
  - 현재 `#8`은 operations와 guide 축의 추가 분리 여부만 남아 있다.
- 2026-06-20 11차 진행
  - `#8`의 operations 축을 `AdminOperationsSectionController`로 분리했다.
  - `AdminActivity`는 운영 저장 다이얼로그만 남기고, 모니터링/정산 필터와 요약/목록 렌더링은 섹션 컨트롤러가 맡도록 정리했다.
  - 현재 `#8`에서 남은 큰 축은 guide다.
- 2026-06-20 12차 진행
  - `#8`의 guide 축을 `AdminGuideSectionController`로 분리했다.
  - 목록 렌더링, 편집 상태, 삭제 확인, 폼 검증을 컨트롤러로 옮기고 액티비티에는 저장/삭제 요청만 남겼다.
- 2026-06-20 13차 진행
  - `#8`의 저장소 남은 범위였던 request, guide, operations 저장 로직을 각각 `FirebaseAdminRequestStore`, `FirebaseAdminGuideStore`, `FirebaseAdminOperationsStore`로 분리했다.
  - 이 단계로 관리자 앱 화면 및 저장소 분리의 계획 범위를 모두 마쳤다.
- 2026-06-20 14차 진행
  - `#9` 1차로 `MockBookingStore`, `MockManagerStore`를 추가했다.
  - `MockBookingRepository`, `MockManagerRepository`는 새 store를 직접 사용하도록 바꾸고, `MockBodeulRepository`는 공유 상태와 helper 제공 범위를 열어 내부 경계를 분리했다.
  - support/admin 축은 다음 단계에서 같은 패턴으로 이어간다.
- 2026-06-20 15차 진행
  - `#9` 2차로 `MockSupportStore`, `MockAdminStore`를 추가했다.
  - `MockClientSupportRepository`, `MockManagerRepository`, `MockAdminRepository`는 새 store를 직접 사용하도록 바꾸고, `MockBodeulRepository`는 support/admin 공유 상태와 helper를 제공하는 쪽으로 경계를 더 좁혔다.
  - `assembleDebug`, `testDebugUnitTest` 검증을 통과했고, `#9` 계획 범위인 booking/manager/support/admin 분리를 모두 완료했다.
- 2026-06-20 16차 진행
  - `#10` 1차로 `AdminAuthScreen`, `AdminShell`, `useAdminIdleSession`, `adminSession`을 추가했다.
  - `App.tsx`는 메뉴 상태, 매니저 목록 구독, 화면 전환을 담당하는 쉘 역할로 줄였고, 로그인 화면과 유휴 세션 타이머를 분리했다.
  - `lint`, `build` 검증을 통과했다.
- 2026-06-20 17차 진행
  - `#10` 2차로 `ManagerApprovalList`, `ManagerReviewModal`, `useManagerDocumentPreviews`를 추가했다.
  - `App.tsx`에서 manager review list, review modal, preview resolver hook 범위를 분리해 계획 범위를 모두 완료했다.
  - `lint`, `build` 검증을 통과했고 `#10`을 마감할 수 있는 상태다.
