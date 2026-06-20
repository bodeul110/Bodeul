# 리팩토링 로드맵

기준일: 2026-06-20

이 문서는 현재 코드베이스에서 `지금 손대야 하는 리팩토링`만 추려 우선순위와 분할 기준을 고정한 계획 문서다.

## 판단 기준

- 기능 동작은 이미 닫혀 있고, 새 기능 추가 시 영향 범위가 과도하게 커지는 구간만 우선 대상으로 본다.
- 단순 파일 길이보다 `서로 다른 책임이 한 파일에 섞여 있는지`를 더 중요하게 본다.
- 전면 재작성 대신 `기존 동작 유지 + 모듈 분리` 방식으로 진행한다.

## 현재 우선순위

### 1. Firebase Functions 분리

- 현재 상태
  - `functions/index.js`가 인증, 리마인더, 문의, 채팅, 위치 알림, 토큰 정리를 한 파일에서 담당한다.
  - 배포 영향 범위가 너무 넓고, 작은 수정도 전체 함수 파일 검토가 필요하다.
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
  - `AdminActivity`가 관리자 대시보드 대부분의 렌더링과 이벤트를 한 화면에서 처리한다.
  - `FirebaseAdminRepository`가 요청, 운영, 가이드, 서류, 문의, 액션센터까지 같이 다룬다.
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
  - `MockBodeulRepository`가 예약, 문의, 위치, 채팅, 관리자 동작을 모두 한 파일에서 처리한다.
  - Firebase 동작과 mock 동작의 차이를 추적하기 어렵다.
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
  - `admin-web/src/App.tsx`가 로그인, 세션, 목록, 상세 모달, Storage 미리보기, idle timer를 함께 처리한다.
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

## 작업 순서

1. Functions 분리
2. 관리자 앱 분리
3. Mock 저장소 분리
4. 관리자 웹 분리

이 순서를 유지하는 이유는 다음과 같다.

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
