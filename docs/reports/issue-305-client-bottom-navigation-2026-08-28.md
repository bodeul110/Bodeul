# Issue 305 환자·보호자 공통 하단 내비게이션 구현

기준일: 2026-08-28

## 구현한 내용

- `홈 / 일정·이력 / 동행방 / 내 정보` 공통 하단 탭과 화면별 초기 선택 상태를 추가했다.
- 홈, 예약 이력, 동행방과 새 내 정보 화면을 최상위 탭으로 연결했다.
- 내 정보 화면은 Firebase Auth 저장소가 반환한 현재 사용자의 역할·이름·이메일·연락처만 표시한다.
- 동행방을 예약 상세 밖에서 열면 기존 예약 목록 API의 응답 중 `MATCHED` 또는 `IN_PROGRESS` 요청만 선택한다.
- 참여 가능한 동행방이 없으면 오류 대신 빈 상태와 홈 이동을 표시한다.

## 변경된 범위

- Android Java: 공통 navigation binder·router·역할별 표시 정책, 내 정보 Coordinator·Binder·Activity, 동행방 top-level 대상 선택
- Android XML: 공통 하단 메뉴, 네 화면의 하단 여백·탭, 내 정보 화면
- 테스트: 서버 반환 예약 목록에서 동행방 대상을 선택하는 순수 로직
- 문서: [환자·보호자 공통 하단 내비게이션](../design/client-bottom-navigation.md)

API, DB migration, Firebase Rules, 정보공유 동의와 역할 권한은 변경하지 않았다.

## 근거 확인

- Notion `개발 정책 관련 내용`을 2026-08-28 실조회해 공통 탭과 예약별 참여관계 답변을 확인했다.
- 저장소의 [Figma 현행 화면 지도](../design/figma-current-screen-map.md)에서 홈의 하단 내비게이션 위계와 용어 불일치를 확인했다.
- Figma `보들 가이드` 환자 홈 node `845:349`의 재조회는 연결 계정 편집 권한 부족으로 실패했으므로, 마지막 실조회 기록 이후 변경을 확인했다고 표시하지 않는다.

## 검증

- `ClientBottomNavigationVisibilityTest`: 환자·보호자에게만 공통 탭이 표시되고 매니저·관리자는 제외됨을 확인
- `ClientCompanionRoomEntryStateTest`: 서버 응답에 현재 참여 예약 ID가 없거나 ID가 비어 있을 때 빈 상태가 됨을 확인
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `.\gradlew.bat assembleDebug --console=plain`
- `git diff --check`
- 실기기 UI·TalkBack 검증은 이번 작업에서 수행하지 않았다.

## 남은 범위

- Figma 원본의 역할 혼재 카드와 하단 탭 명칭 정리
- 최종 리포트의 일정 상세 진입과 완료 후 채팅 읽기 전용 정책 검증
- 공통 탭의 실기기 화면 크기·TalkBack·뒤로가기 확인
