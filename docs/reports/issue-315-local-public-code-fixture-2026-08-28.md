# Issue 315 로컬 공고번호 fixture 검증

## 작업 목적

예약 공고번호 정책이 확정되기 전에 운영 계약을 만들지 않고, 합성 데이터에서 `BD-`와 영문·숫자 6자리 형식 및 충돌 재시도 동작만 검증한다.

## 선택한 방식

- `tools/firebase`에 로컬 전용 생성기와 단위 테스트를 둔다.
- 생성 결과에 `local_synthetic_fixture`, `productionReady: false`를 명시한다.
- 예약된 코드와 같은 실행에서 이미 생성한 코드를 모두 충돌 대상으로 취급한다.
- 충돌 재시도 횟수를 제한하고 소진 시 실패시킨다.

## 대안

- PostgreSQL migration과 Core API 발급 계약을 먼저 추가하는 방식
- Firestore 예약 문서나 sample seed에 `publicCode`를 바로 넣는 방식
- 형식만 문서로 남기고 실행 가능한 검증은 만들지 않는 방식

## 선택 이유

현재 MVP 규모에서는 정책 검토를 기다리는 동안에도 코드 형식과 충돌 처리 가능성은 로컬에서 확인할 수 있다. 반면 DB·API·화면에 필드를 먼저 추가하면 검색 권한, 수명, 재사용 금지와 backfill이 확정된 것처럼 굳어질 위험이 있다.

## 리스크

- 이 생성기는 운영 발급기의 난수 강도, 전역 유일성, rate limit을 증명하지 않는다.
- 로컬 fixture 결과를 인증수단이나 실제 예약 조회번호로 사용하면 안 된다.
- 운영 구현 전에는 발급 주체, 노출 대상, 만료·재사용 금지, 검색 권한과 기존 예약 처리 방식을 별도로 확정해야 한다.

## 변경된 범위

- `npm --prefix tools/firebase run fixture:public-codes -- --count 10`으로 로컬 합성 공고번호를 만들 수 있다.
- 앱, Core API, PostgreSQL, Firestore, 화면과 실제 발급 흐름은 변경하지 않았다.

## 검증

- `node --test tools/firebase/test/local-public-code-fixture.test.js`: 5개 통과
- `npm --prefix tools/firebase run test:toolkit`: 38개 통과
- `npm --prefix tools/firebase run fixture:public-codes -- --count 5`: 로컬 전용 표식과 서로 다른 코드 5개 출력 확인
- `npm --prefix tools/firebase run preflight:local`: Android `assembleDebug`, `testDebugUnitTest` 통과
- 같은 preflight의 Firebase 운영 워크플로는 격리 worktree에 `.firebaserc`와 `app/google-services.json`이 없어 프로젝트 ID 확인 전에 중단됐다. Firebase 데이터 읽기·쓰기는 실행되지 않았다.

## 남은 범위

Issue #315의 정책 확정 후 DB migration, Core API 계약, 화면 표시·검색, 기존 예약 backfill을 각각 분리해 진행한다.
