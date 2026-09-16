# 환자 예약 일정 선택 화면 피그마 반영 결정 기록

## 작업 목적

- Figma `보들 MVP`의 예약 날짜 화면 `8:632`를 Android 예약 작성 흐름에 연결한다.
- 날짜와 시간을 서로 다른 시스템 다이얼로그에서 고르던 흐름을 한 화면의 일정 선택 경험으로 정리한다.

## 선택한 방식

- `BookingAppointmentSelectorActivity`를 추가해 달력, 시간 슬롯과 완료 동작을 한 화면에 배치한다.
- 기존 예약 폼과 Core API가 사용하는 `yyyy-MM-dd HH:mm` 계약은 바꾸지 않고 Activity 결과로 선택값만 반환한다.
- 기존 빠른 날짜·시간 버튼은 예약 폼 전체 개편 전까지 유지하며, 날짜 입력란을 누르면 새 화면을 연다.
- Figma 원본의 색상, 간격, Pretendard 서체, 벡터 아이콘과 병원 이미지를 Android 리소스로 변환해 사용한다.
- 과거 날짜는 선택할 수 없게 하고 기존 예약 수정값과 임의 시간 선택은 보존한다.
- 릴리스 Activity는 `exported=false`로 유지하고 디버그 빌드에서만 실기기 독립 진입을 허용한다.

## 대안과 선택 이유

- 기존 `MaterialDatePicker`와 `MaterialTimePicker`를 계속 순서대로 여는 방법은 변경 범위가 작지만 Figma의 한 화면 위계와 선택 상태 비교가 어렵다.
- 예약 폼 전체를 한 번에 단계형 화면으로 바꾸는 방법은 병원 검색 PR과 충돌하고 초안 복원·편집 회귀 범위가 커서 제외했다.
- Figma의 비활성 시간과 `실시간 예약 가능` 문구를 그대로 넣는 방법은 시간대별 가용성 API가 없는 상태에서 실제 예약 가능 여부를 오해하게 하므로 제외했다. 모든 제안 시간은 희망 시간이며 최종 일정은 예약 확정 뒤 안내한다고 표시한다.

## 리스크와 후속 작업

- 시간대별 실제 가용성 API가 추가되면 슬롯 활성 상태와 안내 문구를 서버 응답에 연결해야 한다.
- 예약 폼 전체를 Figma 단계 구조로 개편할 때 기존 빠른 날짜·시간 버튼을 제거하고 새 화면 진입점 하나로 통합한다.
- 큰 글꼴과 더 작은 화면에서는 고정 완료 버튼 뒤의 시간 슬롯을 스크롤해 확인해야 한다.

## 검증

- `gradlew assembleDebug --console=plain --max-workers=1`: 성공
- `git diff --check`: 성공
- `lintDebug`: 이번 변경의 오류는 0건이며 기존 `GuardianSharingConsentActivity`와 `activity_manager_home.xml` 오류 7건으로 전체 작업은 실패한다.
- `testDebugUnitTest --tests com.example.bodeul.ui.booking.BookingAppointmentDateTimeTest`: 테스트 소스 컴파일 후 로컬 Gradle 작업자가 `GradleWorkerMain`을 찾지 못해 실행 단계에서 중단된다.
- 실제 기기 `SM-S901N`에 설치하고 일정 화면의 독립 실행, 날짜 선택, 이전·다음 달 이동, 시간 슬롯 선택, 임의 시간 다이얼로그와 완료 후 종료를 확인했다.
- 초기값 `2026-10-05 16:00`을 전달했을 때 2026년 10월, 5일 선택 상태와 16:00이 복원되는 것을 확인했다.
