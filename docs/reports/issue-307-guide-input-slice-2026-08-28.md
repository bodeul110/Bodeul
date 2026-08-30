# Issue 307 승인 입력 범위 구현 기록

기준일: 2026-08-28

후속 상태: 2026-08-29 #307 정상 완료 범위에서 선택 결제 증빙, 선택 처방 이미지, `CARE_ENDED`·`COMPLETED` 분리와 선택 일지·리포트 재시도 코드를 구현했다. 아래의 미구현 판단은 V16 가이드 5 작업 당시 기록이며 최신 상태는 [후속 구현 기록](issue-307-companion-completion-2026-08-29.md)을 따른다.

## 승인 근거

- Notion [개발 정책 관련 내용](https://app.notion.com/p/3c448490990280dc8237d9963a47a475)의 P0-06은 가이드 4 선택 입력, 가이드 5 필수 확인, 가이드 10 최대 3장, 가이드 13 최대 300자를 이미 정해진 기술 경계로 둔다.
- GitHub [#307 OWNER 댓글](https://github.com/bodeul110/Bodeul/issues/307#issuecomment-5452788336)은 위 네 항목과 빈값·재시도·재진입 처리를 바로 구현 가능한 범위로 한정한다.
- 사고 중단, 결제 증빙 의무, `CARE_ENDED`와 `COMPLETED`의 의미, 민감정보 정책은 승인 범위가 아니므로 변경하지 않았다.

## 판단 기록

### 작업 목적

가이드 5에서 증상, 질문과 전달 사항을 확인하지 않은 채 다음 단계로 진행하는 문제를 막고, 화면을 나갔다 돌아와도 확인 상태를 복원한다.

### 선택한 방식

PostgreSQL 세션에 `pre_consultation_confirmed`를 저장하고 Android가 체크 상태를 저장·해제한 뒤 서버 재조회 결과를 다시 표시한다. Core API의 `STEP_INPUT_REQUIRED` 판정과 동시 진행 차단은 `bodeul.session.pre-consultation-enforcement` 설정 뒤에 두고 기본값을 `false`로 유지한다.

### 대안

Android 로컬 상태만으로 버튼을 막거나, 기존 자유 메모의 비어 있지 않음을 확인 완료로 간주하는 방법을 검토했다.

### 선택 이유

로컬 상태는 앱 재시작과 다른 기기 재진입을 견디지 못하고, 자유 메모는 승인된 `필수 확인`과 의미가 다르다. 확인값은 현재 세션 source of truth인 PostgreSQL에 저장한다. 다만 구버전 앱이 확인값을 보낼 수 없으므로 서버 차단은 Android 보급과 검증 뒤 별도 승인으로 켜야 한다.

### 리스크

- V16 migration과 Core API를 서버 차단 `false` 상태로 먼저 배포하고 Android를 보급·검증한 뒤, 별도 승인으로 서버 차단을 켜야 한다. 이번 변경에서는 설정을 켜지 않는다.
- 현재 확인값은 전체 확인 여부 한 개만 저장한다. 항목별 체크 이력이나 확인 시각이 필요해지면 별도 계약이 필요하다.
- 개발 DB와 실제 기기에서의 재진입 검증은 이 코드 검증과 별도로 수행해야 한다.

## 구현한 내용

- V16 migration, rollback, verification SQL과 runtime role의 열 단위 UPDATE 권한을 추가했다.
- Core API PATCH 응답에 확인 상태를 연결하고, 현재 단계가 `PRE_CONSULTATION`일 때만 상태를 수정하도록 제한했다.
- Core API 진행 판정과 repository advance SQL의 확인 조건을 같은 기본 비활성 설정으로 감쌌다. 설정을 켠 뒤에는 조회와 쓰기 사이 동시 요청도 SQL에서 다시 확인한다.
- Android Core API 경로와 개발용 Mock 저장소가 확인 상태를 저장·복원한다. Firebase mapper는 기존 문서의 확인값을 읽을 수 있지만 Rules가 세션 직접 수정을 거부하므로 Firebase 저장은 지원하지 않고 Core API 필요 오류를 반환한다.
- 가이드 5 화면에 되돌릴 수 있는 확인 체크를 추가하고, 미확인 안내와 비활성 진행 버튼을 표시한다. 체크 변경 요청을 보내는 즉시 체크와 진행 버튼을 비활성화해 저장 중 이전 진행 상태를 누르지 못하게 했다.
- 선택 현장 메모는 빈 문자열 저장을 허용해 이전 값을 지울 수 있고, 숫자 `0`으로 대체하지 않는다.

## 함께 확인한 승인 범위

| 항목 | 현재 판단 | 이번 변경 |
| --- | --- | --- |
| 가이드 4 선택 입력 | 구조화된 숫자 측정 필드가 없고 기존 메모의 빈 값을 숫자 `0`으로 변환하는 코드도 없다. | 선택 메모의 빈 문자열 저장과 지우기를 허용했다. 중복 필드나 임의 단위는 추가하지 않았다. |
| 가이드 10 최대 3장 | 최대 장수는 승인됐지만 전용 Storage 경로, metadata, 파일 형식·용량·열람·파기 계약과 API가 없다. | 채팅 첨부를 처방 자료로 재사용하지 않았다. |
| 가이드 13 최대 300자 | 전용 `manager_journal` 필드와 저장 API가 없다. 기존 리포트 요약은 의미가 달라 같은 제한을 적용할 수 없다. | 일지 데이터 계약과 완료 시점 확정 뒤 구현하도록 남겼다. |

## 검증

- Core API 동행 세션 대상 테스트: 통과
- 서버 차단 기본 `false`와 명시적 `true`, SQL guard 파라미터, 설정 binding 테스트: 통과
- V16 migration contract 테스트: 통과
- 전체 Core API `check`: 통과
- Android `ManagerGuideCoordinatorPolicyTest`, `CoreApiCompanionSessionClientTest`, Firebase 직접 쓰기 거부 테스트: 통과
- Android 전체 `testDebugUnitTest`: 통과
- Android `assembleDebug`: 통과
- Core API preview·production workflow YAML 파싱: 통과
- 실제 개발 DB V16 적용과 실기기 재진입: 미실행
- production 서버 진행 차단: 기본값 `false`, 활성화하지 않음

## 남은 범위

- #307은 가이드 2·3·6·8·10·12·13의 정책이 남아 있어 닫지 않는다.
- 배포는 V16 migration, Core API(`BODEUL_SESSION_PRE_CONSULTATION_ENFORCEMENT=false`), Android 보급·검증 순서로 진행한다. 서버 차단을 `true`로 바꾸는 작업은 별도 승인과 구버전 잔존율 확인 뒤 수행한다.
- 가이드 10은 파일 저장·권한·보존 계약을 먼저 확정한다.
- 가이드 13은 전용 일지 필드, 필수 여부와 최종 완료 전이를 함께 확정한다.
- 사고·중단, 결제 증빙, `CARE_ENDED`와 `COMPLETED`는 별도 승인 전 구현하지 않는다.
