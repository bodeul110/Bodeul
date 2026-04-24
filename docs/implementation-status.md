# 구현 상태

기준: 2026-04-15

## 1. 현재 동작하는 기능

### 인증

- 이메일 로그인 / 회원가입 / 비밀번호 재설정
- Google, Kakao, Naver 로그인
- 이메일 인증, 프로필 보완
- Firebase 미설정 시 목업 모드 자동 전환

### 환자 / 보호자

- 병원 동행 신청 생성
- 내 신청 목록 조회
- `REQUESTED` 상태 요청 수정 / 취소
- `MATCHED` 상태 요청 취소
- 권한 없음 / 로그인 필요 / 불러오기 실패 상태 패널 표시
- 신청 단계에서 환자-보호자 연결 정보 입력
- 기존 계정이 있으면 이메일 또는 전화번호 기준 자동 연결
- 계정이 없어도 신청 시점 이름 / 전화번호 / 이메일 스냅샷 저장
- 보호자 진행 현황 조회
- 최종 진료 리포트 조회

### 매니저

- 매니저 홈
- 서류 등록 요약 저장
- 활동 가능 일정 저장
- 매니저 홈 권한 / 로그인 / 불러오기 실패 상태 패널 표시
- 병원 동행 가이드 진행
- 동행 가이드 권한 / 로그인 / 불러오기 실패 상태 패널 표시
- 보호자 공유 메시지 저장
- 복약 메모 저장
- 진료 리포트 저장

### 관리자

- 미배정 요청 조회
- 수동 매칭
- 병원 가이드 등록
- 병원 가이드 수정 / 삭제
- 운영 이력 상태별 필터
- 운영 이력 날짜별 필터
- 운영 요청 상세 정보 펼침
- 운영 중 요청 조회
- 권한 없음 / 로그인 필요 / 불러오기 실패 상태 패널 표시

### 알림 / 서버

- 예약 시 `appointmentAtEpochMillis`, `appointmentDateKey`, `reminderStages` 저장
- 매일 오전 9시 기준 `D7`, `D3`, `D1` 알림 작업 생성
- 알림 작업 큐 처리 및 시뮬레이션 / 실발송 상태 기록
- 사용자 문서 생성 / 수정 시 기존 신청 문서 자동 재연결
- 예약 취소 / 삭제 / 일정 변경 시 남아 있는 `appointmentReminderJobs` 자동 정리

## 2. 이번 작업에서 구현한 내용

- 관리자 운영 이력에 `오늘`, `다가오는 일정`, `지난 일정` 날짜 필터를 추가했다.
- 운영 카드마다 상세 정보 펼침 영역을 추가해 요청 ID, 세션 상태, 계정 연결 상태, 보호자 공유 메시지, 복약 메모를 바로 확인할 수 있게 했다.
- 상태 필터와 날짜 필터를 함께 적용해 현재 표시 건수와 집계를 동시에 보도록 정리했다.
- 관련 문자열과 현재 작업 상태 문서를 업데이트했다.

## 3. 변경된 범위

- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/res/layout/activity_admin.xml`
- `app/src/main/res/layout/item_admin_request.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

## 4. 남은 범위

### 기능

- 운영 이력 전용 별도 상세 화면은 아직 없다.
- 매니저 서류 파일 업로드, 증빙 이미지 첨부, 관리자 승인 상태는 아직 없다.
- `IN_PROGRESS` 이후 요청 변경 / 취소 정책은 아직 앱에서 막기만 하고 별도 운영 대응 화면은 없다.

### UI

- 실제 소셜 아이콘 리소스 교체
- 날짜 / 시간 선택기 주변 빠른 선택 UX 보강 여부 검토

## 5. 다음 권장 순서

1. 매니저 서류 파일 업로드 / 승인 상태 확장
2. 관리자 운영 이력 전용 상세 화면 검토
3. 날짜 / 시간 선택기 주변 빠른 선택 UX 검토

## 6. 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`

## 7. 2026-04-15 추가 업데이트

### 구현

- 매니저 홈에 `서류 검토 상태`, `관리자 메모` 표시를 추가했다.
- 매니저가 서류 요약을 다시 저장하면 상태가 `PENDING_REVIEW`로 바뀌고 기존 관리자 메모는 초기화된다.
- 관리자 화면에 `매니저 서류 검토` 섹션을 추가했다.
- 관리자 화면에서 매니저별 `서류 요약`, `가능 일정`, `검토 메모`를 보고 `승인`, `보완 요청`을 저장할 수 있다.
- 목업 저장소와 Firebase 저장소 모두 같은 상태 모델을 사용하도록 맞췄다.

### 변경 범위

- `domain/model`: `ManagerDocumentStatus`, `ManagerDocumentOverview`, `ManagerHomeProfile`, `AdminDashboard`
- `data`: `AdminRepository`, `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`, `ManagerActivity`
- `layout`: `activity_admin.xml`, `activity_manager_home.xml`, `item_admin_manager_document.xml`, `dialog_admin_document_review.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`와 `증빙 이미지 미리보기`는 아직 없다.
- 관리자 검토 이력의 `타임라인`과 `담당자 로그`는 아직 없다.
- 날짜/시간 선택기의 `빠른 선택 UX`는 1차 반영을 마쳤고, 추가 보강 항목이 남아 있다.

## 8. 2026-04-15 추가 업데이트

### 구현

- 동행 신청 화면에 `빠른 날짜 선택`, `빠른 시간 선택` 버튼을 추가했다.
- `오늘`, `내일`, `모레`, `오전 10시`, `오후 2시`, `오후 4시`를 바로 선택할 수 있다.
- 빠른 날짜와 빠른 시간은 누적 적용되므로 날짜를 먼저 고르고 시간을 이어서 맞출 수 있다.
- 기존 `MaterialDatePicker`, `MaterialTimePicker` 흐름은 그대로 유지한다.

### 변경 범위

- `ui`: `BookingActivity`
- `layout`: `activity_booking.xml`
- `values`: `strings.xml`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`와 `증빙 이미지 미리보기`
- 관리자 검토 이력의 `타임라인`, `담당자 로그`
- 빠른 선택 버튼의 `선택 상태 강조`, `주말/야간 프리셋` 같은 추가 UX

## 9. 2026-04-15 추가 업데이트

### 구현

- 관리자 서류 검토 카드에 `서류 제출 시각`, `최근 검토 시각`, `담당자`를 함께 보여주는 타임라인을 추가했다.
- 매니저 홈 프로필 모델에 서류 제출/검토 시각과 검토 담당자 이름을 저장하도록 확장했다.
- 매니저가 서류 요약을 다시 저장하면 기존 검토 이력은 초기화되고, 관리자가 승인 또는 보완 요청을 저장하면 검토 시각과 담당자 이름이 함께 기록된다.

### 변경 범위

- `domain/model`: `ManagerHomeProfile`
- `data`: `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`
- `layout`: `item_admin_manager_document.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`, `증빙 이미지 미리보기`
- 관리자 검토 이력의 별도 `로그/타임라인 화면`
- 빠른 선택 버튼의 `선택 상태 강조`, `주말/야간 프리셋`

## 10. 2026-04-15 추가 업데이트

### 구현

- 동행 신청의 빠른 날짜/시간 버튼에 현재 선택 상태를 바로 보여주는 강조 스타일을 추가했다.
- 날짜가 `오늘`, `내일`, `모레` 중 하나와 일치하면 해당 버튼이 선택 상태로 유지된다.
- 시간이 `오전 10시`, `오후 2시`, `오후 4시` 중 하나와 일치하면 해당 시간 버튼도 함께 강조된다.
- 달력/시간 선택기, 빠른 선택 버튼, 수정 모드 진입/해제 모두 같은 선택 상태 계산을 재사용한다.

### 변경 범위

- `ui`: `BookingActivity`
- `docs`: `implementation-status.md`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`, `증빙 이미지 미리보기`
- 관리자 검토 이력의 별도 `로그/타임라인 화면`
- 빠른 선택 버튼의 `주말/야간 프리셋`

## 11. 2026-04-15 추가 업데이트

### 구현

- 관리자 서류 카드에 `검토 이력 보기` 버튼을 추가하고, 별도 다이얼로그에서 제출/승인/보완 요청 기록을 시간순으로 확인할 수 있게 했다.
- 목업 저장소와 Firebase 저장소 모두 `managerDocumentHistory` 배열을 유지하도록 확장했다.
- 매니저가 서류 요약을 저장하면 `SUBMITTED` 이력이 쌓이고, 관리자가 승인 또는 보완 요청을 저장하면 `APPROVED`, `REJECTED` 이력이 이어서 누적된다.

### 변경 범위

- `domain/model`: `ManagerDocumentHistoryEntry`, `ManagerDocumentHistoryEventType`, `ManagerDocumentOverview`
- `data`: `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`
- `layout`: `dialog_admin_document_history.xml`, `item_admin_document_history.xml`, `item_admin_manager_document.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`
- `docs`: `data-api-draft.md`, `implementation-status.md`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`, `증빙 이미지 미리보기`
- 관리자 검토 이력의 `필터/검색`, `운영 메모 고정` 같은 2차 기능
- 빠른 선택 버튼의 `주말/야간 프리셋`

## 12. 2026-04-22 추가 업데이트

### 구현

- 기능 설명서와 피그마 캡처 기준으로 전면 개편용 목표 구조 문서 `docs/restructure-target-map.md`를 추가했다.
- 작업 규칙에 `액티비티/프래그먼트에는 흐름 제어만 남기고 역할별 객체로 분리하는 객체지향 원칙`을 명시했다.
- 스플래시 진입 분기를 `EntryFlowCoordinator`로 분리해 `스플래시 -> 권한 안내 -> 유형 선택 -> 로그인` 흐름을 조정할 수 있게 바꿨다.
- `PermissionGuideActivity`, `PermissionGuideCatalog`, `PermissionGuideItem`, `PermissionGuideItemBinder`, `PermissionGuidePreferences`를 추가해 권한 안내 화면과 권한 요청/저장 로직을 객체로 분리했다.
- 유형 선택 화면을 피그마 카드 구조에 맞게 다시 구성하고 `RoleOptionCardBinder`로 선택 강조 로직을 분리했다.
- 로그인 화면에서는 매니저 단독 진입 시 역할 칩을 숨겨 피그마 흐름과 겹치는 중복 선택을 줄였다.

### 변경 범위

- `AGENTS.md`
- `docs`: `implementation-status.md`, `restructure-target-map.md`
- `manifest`: `AndroidManifest.xml`
- `ui/auth`: `SplashActivity`, `RoleSelectionActivity`, `LoginActivity`, `EntryFlowCoordinator`, `PermissionGuideActivity`, `PermissionGuideCatalog`, `PermissionGuideItem`, `PermissionGuideItemBinder`, `PermissionGuidePreferences`, `RoleOptionCardBinder`
- `layout`: `activity_permission_guide.xml`, `item_permission_guide.xml`, `activity_role_selection.xml`
- `values`: `strings.xml`

### 남은 범위

- 로그인 화면의 세부 시각 요소와 회원가입 흐름을 피그마 단계별 화면에 더 가깝게 정리
- 환자/보호자 홈, 예약 신청, 결제 흐름을 기능 설명서 구조로 재분해
- 매니저 홈을 `실시간 AI 매칭`, `주변 대기 환자`, `경력/문의` 중심 구조로 전환
- 동행 중 위치/채팅/현장 사진/약국 단계와 종료 후 후기/정산/긴급 신고 화면 추가

## 13. 2026-04-22 추가 업데이트

### 구현

- 기존 `MainActivity`의 임시 허브 구조를 제거하고 환자/보호자 전용 서비스 홈으로 교체했다.
- `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `ClientHomeNotice`, `ClientHomeNoticeProvider`를 추가해 홈 데이터 조합, 화면 모델, 뷰 바인딩, 안내 카드 구성을 객체로 분리했다.
- 홈 화면에 `동행 신청`, `진행 현황/보호자 리포트`, `최근 접수 내역`, `서비스 안내` 섹션을 추가해 기능 설명서의 환자/보호자 메인 홈 구조를 반영했다.
- 보호자는 진행 중 요청이 있으면 `보호자 리포트` 중심으로, 환자는 `요청 현황` 중심으로 홈 액션과 히어로 카드 문구가 바뀌도록 구성했다.
- 로그인 후 관리자 계정이 더 이상 `MainActivity`로 들어오지 않도록 `AuthFlowRouter`를 수정해 `AdminActivity`로 직접 분기하도록 정리했다.
- `assembleDebug --console=plain`으로 빌드 검증을 완료했다.

### 변경 범위

- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `ClientHomeNotice`, `ClientHomeNoticeProvider`
- `ui/auth`: `AuthFlowRouter`
- `ui/root`: `MainActivity`
- `layout`: `activity_main.xml`, `item_client_home_notice.xml`
- `values`: `strings.xml`
- `docs`: `implementation-status.md`

### 남은 범위

- 환자 건강 프로필 등록, 병원 검색, 희망 매니저 성별, 왕복/편도, 예상 비용, 결제/쿠폰 흐름 추가
- 매니저 홈을 기능 설명서 기준의 `실시간 매칭 On/Off`, `주변 대기 환자`, `주요 메뉴 버튼` 구조로 재설계
- 실시간 위치/안심 채팅, 현장 사진 피드, 약국 동행/복약 안내, 최종 후기/정산/SOS 화면 추가
- 관리자 운영 화면을 최종 기능 설명서 기준의 승인·운영·정산 구조로 확장
## 14. 2026-04-22 추가 업데이트

### 구현

- `BookingRequestDraft`, `BookingPriceSummary`와 예약 옵션 열거형을 추가해 예약 신청 데이터를 객체 하나로 전달하도록 바꿨다.
- `AppointmentRequest`에 건강 프로필, 이동 보조, 왕복/편도, 희망 매니저 성별, 결제 수단, 쿠폰, 비용 요약 필드를 확장했다.
- `BookingCoordinator`, `BookingDashboard`, `BookingDashboardBinder`, `BookingFormBinder`, `BookingRequestCardBinder`, `BookingAppointmentSelector`, `BookingPriceEstimator`, `BookingPresentationFormatter`를 추가해 예약 화면의 데이터 조합, 폼 검증, 날짜 선택, 요청 카드 표현을 객체로 분리했다.
- `BookingActivity`는 인증 확인, 저장소 호출, 화면 전환 같은 흐름 제어만 담당하도록 다시 구성했다.
- 예약 화면을 `환자 건강 프로필 -> 연결 정보 -> 방문 일정 -> 서비스 옵션 -> 결제 및 쿠폰 -> 비용 요약` 구조로 재구성했고, 요청 카드에도 건강 프로필과 결제 요약을 함께 보여주도록 바꿨다.
- `testDebugUnitTest`, `assembleDebug --console=plain` 검증을 마쳤다.

### 변경 범위

- `domain/model`: `AppointmentRequest`, `BookingRequestDraft`, `BookingPriceSummary`, `BookingTripType`, `BookingMobilitySupport`, `BookingManagerGenderPreference`, `BookingPaymentMethod`, `BookingCouponType`
- `data`: `BookingRepository`, `MockBodeulRepository`, `MockBookingRepository`, `FirebaseBookingRepository`
- `ui/booking`: `BookingActivity`, `BookingCoordinator`, `BookingDashboard`, `BookingDashboardBinder`, `BookingFormBinder`, `BookingRequestCardBinder`, `BookingAppointmentSelector`, `BookingPriceEstimator`, `BookingPresentationFormatter`, `BookingOptionGroupBinder`
- `layout`: `activity_booking.xml`, `item_booking_request.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 병원 검색, 지도 기반 위치 선택, 실제 결제 완료 화면과 쿠폰 상세 적용 규칙
- 환자/보호자 홈 이후의 결제 완료, 매칭 대기, 예약 상세 흐름 연결
- 매니저 실시간 매칭 홈, 동행 중 위치/사진/약국 단계, 종료 후 후기/정산/SOS 화면
## 15. 2026-04-23 추가 업데이트

### 구현

- `ManagerActivity`를 인증/상태 전환/저장소 호출만 담당하는 흐름 제어기로 정리하고, 매니저 홈 렌더링은 전용 객체에 위임했다.
- `ManagerHomeCoordinator`, `ManagerHomeScreenModel`, `ManagerHomeHeroModel`, `ManagerHomeActionCardModel`, `ManagerHomePromoCardModel`, `ManagerHomeLiveFeedModel`을 추가해 대기형 홈과 진행형 홈을 같은 화면 모델 체계로 분리했다.
- `ManagerHomeDashboardBinder`, `ManagerHomeActionCardBinder`, `ManagerHomePromoCardBinder`를 추가해 히어로 카드, 빠른 액션, 소개 카드, 진행 중 동행 카드를 객체 기준으로 바인딩하도록 바꿨다.
- 매니저 홈 레이아웃을 최신 피그마 기준으로 재구성해 `실시간 AI 매칭 대기`, `서류 등록`, `스케줄 등록`, `과거 경력`, `문의하기`, `오늘 연결된 동행 일정` 구조를 반영했다.
- 진행 중 동행이 있으면 히어로 버튼이 바로 가이드를 열고, 대기 상태에서는 매칭 요청을 다시 확인하는 흐름으로 분기되도록 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/manager`: `ManagerActivity`, `ManagerHomeCoordinator`, `ManagerHomeDashboardBinder`, `ManagerHomeActionCardBinder`, `ManagerHomePromoCardBinder`, `ManagerHomeScreenModel`, `ManagerHomeHeroModel`, `ManagerHomeActionCardModel`, `ManagerHomePromoCardModel`, `ManagerHomeLiveFeedModel`, `ManagerHomePresentationFormatter`, `ManagerHomeActionType`
- `layout`: `activity_manager_home.xml`, `item_manager_home_action_card.xml`, `item_manager_home_promo_card.xml`
- `values`: `strings.xml`

### 남은 범위

- 병원 검색, 지도 기반 위치 선택, 실제 결제 완료 화면과 예약 상세/매칭 대기 흐름 연결
- 동행 중 실시간 위치, 현장 사진, 약국/복약 단계, 종료 후 후기/정산/SOS 화면 개편
- 과거 경력, 문의하기, 내 페이지, 가이드 외 탭을 실제 화면과 데이터 흐름으로 연결
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장

## 16. 2026-04-23 추가 업데이트

### 구현

- `CompanionSession`에 `locationSummary`, `fieldPhotoNote` 필드를 추가해 위치 공유와 현장 사진/서류 메모를 세션 데이터로 별도 관리하도록 확장했다.
- `ManagerRepository`와 목업/Firebase 저장소에 위치 메모, 현장 메모 저장 메서드를 추가해 매니저 진행 화면의 입력이 실제 세션 데이터로 반영되도록 정리했다.
- `ManagerGuideCoordinator`, `ManagerGuideScreenModel`, `ManagerGuideFocusModel`, `ManagerGuideStageModel`, `ManagerGuideDashboardBinder`, `ManagerGuideStageItemBinder`를 추가해 동행 진행 화면의 단계 계산, 포커스 카드 조합, 레일 렌더링을 액티비티 밖으로 분리했다.
- `ManagerGuideActivity`는 인증 확인, 저장소 호출, 저장 액션만 담당하도록 재작성했고, 레이아웃도 최신 피그마 기준의 `상단 동행 요약 -> 단계 레일 + 현재 단계 포커스 -> 위치 공유/보호자 공유 -> 현장 사진/복약 -> 최종 리포트` 구조로 교체했다.
- 환자/보호자 예약 상세도 같은 세션 데이터를 보도록 `BookingStatusCoordinator`에 위치 메모와 현장 사진 메모 라인을 추가했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `CompanionSession`
- `data`: `ManagerRepository`, `MockBodeulRepository`, `MockManagerRepository`, `FirebaseManagerRepository`, `FirebaseAdminRepository`, `FirebaseBookingRepository`, `FirebaseGuardianReportRepository`
- `ui/manager`: `ManagerGuideActivity`, `ManagerGuideCoordinator`, `ManagerGuideDashboardBinder`, `ManagerGuideStageItemBinder`, `ManagerGuideScreenModel`, `ManagerGuideFocusModel`, `ManagerGuideStageModel`, `ManagerGuideStageState`, `ManagerGuidePresentationFormatter`
- `ui/booking`: `BookingStatusCoordinator`
- `layout`: `activity_manager_guide.xml`, `item_manager_guide_stage.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 병원 검색, 지도 기반 위치 선택, 실제 결제 완료 화면 연결
- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 리포트 화면을 최신 피그마 흐름으로 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편

## 17. 2026-04-23 추가 업데이트

### 구현

- `GuardianReportCoordinator`, `GuardianReportScreenModel`, `GuardianReportHighlightModel`, `GuardianReportEntryCardModel`, `GuardianReportLineItem`, `GuardianReportDashboardBinder`, `GuardianReportEntryCardBinder`, `GuardianReportPresentationFormatter`를 추가해 보호자 진행 화면의 문자열 조합과 카드 렌더링을 액티비티 밖으로 분리했다.
- `GuardianReportActivity`는 인증 확인, 저장소 호출, 로딩/에러 상태, 예약 상세 화면 이동만 담당하도록 재작성했다.
- 보호자 화면 레이아웃을 최신 구조에 맞춰 `상단 계정 요약 -> 대표 진행 현황 -> 요청별 진행 카드`로 재구성했고, 각 카드에서 `진행 상태`, `위치 공유 메모`, `현장 사진 메모`, `현장 복약 메모`, `최종 리포트`를 함께 확인할 수 있게 바꿨다.
- 보호자 카드와 대표 진행 현황 버튼에서 바로 `BookingStatusActivity`로 이동하도록 연결해 진행 화면과 예약 상세 흐름이 자연스럽게 이어지도록 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/report`: `GuardianReportActivity`, `GuardianReportCoordinator`, `GuardianReportDashboardBinder`, `GuardianReportEntryCardBinder`, `GuardianReportScreenModel`, `GuardianReportHighlightModel`, `GuardianReportEntryCardModel`, `GuardianReportLineItem`, `GuardianReportPresentationFormatter`
- `layout`: `activity_guardian_report.xml`, `item_guardian_report.xml`, `item_guardian_report_line.xml`
- `values`: `strings.xml`

### 남은 범위

- 병원 검색, 지도 기반 위치 선택, 실제 결제 완료 화면 연결
- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 메인 진행 흐름을 더 촘촘하게 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편

## 18. 2026-04-23 추가 업데이트

### 구현

- `BookingRepository`에 병원 선택용 조회 메서드를 추가하고, 목업/Firebase 저장소 모두 `hospitalGuides`를 병원별 후보 목록으로 묶어 반환하도록 확장했다.
- `BookingHospitalSelectorActivity`, `BookingHospitalSelectorCoordinator`, `BookingHospitalCatalog`, `BookingHospitalOptionAdapter`를 추가해 병원/진료과 검색과 선택을 예약 폼 밖의 별도 흐름으로 분리했다.
- 예약 폼은 병원/진료과를 직접 입력하지 않고 선택 결과만 반영하도록 바꿨고, 선택 직후 만남 장소가 비어 있으면 기본 만남 위치 문구를 자동 제안하도록 정리했다.
- `BookingCompletionActivity`, `BookingCompletionCoordinator`, `BookingCompletionBinder`, `BookingCompletionSnapshot`, `BookingCompletionScreenModel`을 추가해 접수/수정 후 토스트 대신 완료 요약 화면으로 이어지도록 재구성했다.
- `BookingActivity`는 제출 성공 후 폼 초기화와 대시보드 갱신만 담당하고, 병원 선택과 완료 화면 이동은 전용 객체와 액티비티로 위임하도록 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `data`: `BookingRepository`, `MockBookingRepository`, `FirebaseBookingRepository`
- `domain/model`: `BookingHospitalOption`, `BookingHospitalSelection`
- `ui/booking`: `BookingActivity`, `BookingFormBinder`, `BookingHospitalSelectorActivity`, `BookingHospitalSelectorCoordinator`, `BookingHospitalCatalog`, `BookingHospitalOptionAdapter`, `BookingCompletionActivity`, `BookingCompletionCoordinator`, `BookingCompletionBinder`, `BookingCompletionSnapshot`, `BookingCompletionScreenModel`
- `layout`: `activity_booking.xml`, `activity_booking_hospital_selector.xml`, `item_booking_hospital_option.xml`, `activity_booking_completion.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 지도 기반 위치 선택과 실제 결제 승인/완료 단계를 예약 흐름에 연결
- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 메인 진행 흐름을 더 촘촘하게 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편

## 19. 2026-04-23 추가 업데이트

### 구현

- `BookingLocationSelectorActivity`, `BookingLocationSelectorCoordinator`, `BookingLocationMapView`, `BookingLocationOptionAdapter`를 추가해 만남 장소를 자유 입력이 아니라 지도 기반 선택 흐름으로 분리했다.
- 위치 선택은 병원/진료과 선택 결과를 기준으로 `정문 안내 데스크`, `외래 대기 라운지`, `주차장 승하차 구역`, `약국 연결 지점` 후보를 객체로 조합하고, 예약 폼에는 선택 결과만 반영하도록 정리했다.
- `BookingPaymentApprovalActivity`, `BookingPaymentApprovalCoordinator`, `BookingPaymentApprovalBinder`, `BookingPaymentCheckoutSnapshot`을 추가해 예약 제출 직전에 결제 승인 또는 현장 결제 확정 단계를 별도 화면으로 분리했다.
- `BookingRequestDraft`와 `AppointmentRequest`에 결제 승인 상태, 승인 번호, 승인 시각, 승인 수단 스냅샷을 추가했고, 목업/Firebase 저장소 모두 같은 필드를 읽고 쓰도록 확장했다.
- 예약 상세와 완료 화면에도 결제 승인 상태가 보이도록 `BookingStatusCoordinator`, `BookingCompletionSnapshot`, `BookingCompletionCoordinator`, `BookingPresentationFormatter`를 함께 갱신했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `BookingMeetingLocationSelection`, `BookingMeetingPointOption`, `BookingPaymentApproval`, `BookingPaymentStatus`, `BookingRequestDraft`, `AppointmentRequest`
- `data`: `MockBodeulRepository`, `FirebaseBookingRepository`, `FirebaseManagerRepository`, `FirebaseGuardianReportRepository`, `FirebaseAdminRepository`
- `ui/booking`: `BookingActivity`, `BookingFormBinder`, `BookingLocationSelectorActivity`, `BookingLocationSelectorCoordinator`, `BookingLocationSelectorScreenModel`, `BookingLocationMapView`, `BookingLocationOptionAdapter`, `BookingPaymentApprovalActivity`, `BookingPaymentApprovalCoordinator`, `BookingPaymentApprovalBinder`, `BookingPaymentCheckoutSnapshot`, `BookingPaymentApprovalScreenModel`, `BookingStatusCoordinator`, `BookingCompletionSnapshot`, `BookingCompletionCoordinator`, `BookingPresentationFormatter`
- `layout`: `activity_booking.xml`, `activity_booking_location_selector.xml`, `item_booking_location_option.xml`, `activity_booking_payment_approval.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`

### 남은 범위

- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 메인 진행 흐름을 더 촘촘하게 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장
## 20. 2026-04-23 추가 업데이트

### 구현

- `AppointmentProgressComposer`, `AppointmentProgressOverviewModel`, `AppointmentProgressStageModel`, `AppointmentProgressStageItemBinder`를 추가해 예약 진행 상태를 공통 로드맵 객체로 해석하도록 정리했다.
- `BookingStatusCoordinator`, `BookingStatusScreenModel`, `BookingStatusBinder`, `activity_booking_status.xml`을 갱신해 예약 상세를 `현재 상태 요약 -> 진행 단계 -> 다음 안내 -> 참여자/예약 요약 -> 리포트` 구조로 확장했다.
- `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `MainActivity`, `activity_main.xml`을 갱신해 환자·보호자 메인 홈에서도 대표 요청의 진행 로드맵을 같은 기준으로 보여주도록 맞췄다.
- 보호자 홈은 `GuardianReportEntry`의 실시간 세션/리포트 데이터를 우선 사용하고, 환자 홈은 요청 상태만으로도 동일한 단계 해석을 유지하도록 정리했다.
- `assembleDebug --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/common`: `AppointmentProgressComposer`, `AppointmentProgressOverviewModel`, `AppointmentProgressStageModel`, `AppointmentProgressStageState`, `AppointmentProgressStageItemBinder`
- `ui/booking`: `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingStatusBinder`, `BookingStatusScreenModel`
- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`
- `app`: `MainActivity`
- `layout`: `activity_booking_status.xml`, `activity_main.xml`, `item_appointment_progress_stage.xml`
- `values`: `strings.xml`

### 남은 범위

- 종료 후 후기/정산/SOS 화면과 예약 완료 이후 후속 흐름 정리
- 매니저 `과거 경력`, `문의하기`, `내 페이지`를 실제 기능 화면과 데이터 흐름으로 연결
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장
## 21. 2026-04-23 추가 업데이트

### 구현

- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`을 추가해 완료된 동행 기준의 `후기 -> 정산 확인 -> SOS 안내` 후속 흐름을 별도 화면으로 분리했다.
- 후기 선택은 `BookingFollowUpRating`, `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`로 분리하고, 예약별 만족도와 저장 시각을 기기 로컬 저장소에 유지하도록 정리했다.
- 완료된 예약 상세의 기본 액션을 `후기·정산·SOS 보기`로 바꾸고, 보호자 계정은 보조 액션으로 기존 보호자 리포트도 계속 열 수 있게 유지했다.
- SOS 영역에는 담당 매니저 다이얼 연결, `119` 다이얼 연결, 긴급 안내 다이얼로그를 추가해 완료 후 화면에서도 바로 대응 흐름을 확인할 수 있게 했다.
- `assembleDebug --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `BookingFollowUpRating`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`, `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`, `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingStatusActionType`
- `layout`: `activity_booking_follow_up.xml`, `item_booking_follow_up_rating.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`

### 남은 범위

- 매니저 `과거 경력`, `문의하기`, `내 페이지`를 실제 기능 화면과 데이터 흐름으로 연결
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장
- 후기/정산/SOS의 서버 저장 API와 관리자 후속 처리 흐름을 백엔드 모델에 연결
## 22. 2026-04-23 매니저 과거 이력 / 문의하기 / 내 페이지 반영

### 구현

- `ManagerRepository`에 `getManagerDocumentOverview`, `getManagerHistoryDetails`를 추가해 매니저 홈 외의 실제 상세 화면도 같은 저장소 계약으로 연결했다.
- `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`와 관련 카드 모델을 추가해 완료된 동행 세션과 최종 리포트를 `과거 동행 이력` 화면으로 분리했다.
- `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerProfileBinder`와 서류 이력 카드 바인더를 추가해 계정 정보, 서류 상태, 검토 메모, 활동 가능 일정, 서류 검토 이력을 `내 페이지` 화면으로 구성했다.
- `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportPreferences`를 추가해 `문의하기` 화면에서 로컬 저장 기반 문의 접수와 최근 문의 내역 확인이 가능하도록 정리했다.
- `ManagerQuickNoteDialogController`, `ManagerQuickNoteType`으로 서류 요약 / 일정 요약 수정 대화상자를 공용 객체로 분리했고, 매니저 홈 빠른 액션과 새 `내 페이지` 화면이 같은 편집 흐름을 재사용하도록 맞췄다.
- `ManagerActivity`, `activity_manager_home.xml`, `AndroidManifest.xml`, `strings.xml`을 갱신해 빠른 액션과 하단 네비게이션이 `과거 이력`, `가이드`, `내 페이지`, `문의하기` 실화면으로 연결되게 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `data`: `ManagerRepository`
- `data/mock`: `MockManagerRepository`
- `data/firebase`: `FirebaseManagerRepository`
- `ui/manager`: `ManagerActivity`, `ManagerQuickNoteDialogController`, `ManagerQuickNoteType`, `ManagerInfoLineItem`, `ManagerDocumentHistoryItemModel`, `ManagerDocumentHistoryItemBinder`, `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerProfileBinder`, `ManagerProfileScreenModel`, `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`, `ManagerHistoryEntryCardModel`, `ManagerHistoryScreenModel`, `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportPreferences`, `ManagerSupportCategory`, `ManagerSupportInquiry`, `ManagerSupportInquiryStatus`, `ManagerSupportInquiryCardModel`, `ManagerSupportInquiryCardBinder`, `ManagerSupportScreenModel`
- `layout`: `activity_manager_home.xml`, `activity_manager_profile.xml`, `activity_manager_history.xml`, `activity_manager_support.xml`, `item_manager_info_line.xml`, `item_manager_document_history.xml`, `item_manager_history_entry.xml`, `item_manager_support_inquiry.xml`
- `manifest`: `AndroidManifest.xml`
- `values`: `strings.xml`

### 남은 범위

- 관리자 운영 화면을 최종 기능 설명서 기준의 배정 / 정산 / 모니터링 구조로 확장
- `문의하기`의 서버 저장 API와 관리자 응답 흐름 연결
- 매니저 과거 이력에 실서비스 기준 필터 / 정산 내역 / 평가 데이터 추가
## 23. 2026-04-23 관리자 운영 화면 모니터링/정산 확장

### 구현

- `AdminRequestOverview`에 `sessionReport`를 추가하고, `MockAdminRepository`, `FirebaseAdminRepository`가 요청별 세션 리포트까지 함께 조합하도록 확장했다.
- `ui/admin` 아래에 `AdminOperationsPresentationFormatter`, `AdminOperationsCoordinator`, `AdminOperationCardBinder`와 운영 카드 모델들을 추가해 `실시간 운영 모니터링`, `정산 확인` 섹션을 액티비티 로직에서 분리했다.
- `activity_admin.xml`에 운영/정산 요약과 카드 컨테이너를 추가하고, `AdminActivity`는 새 대시보드 모델을 받아 섹션 렌더링만 담당하도록 연결했다.
- 모니터링 카드는 `담당 매니저`, `현재 단계`, `보호자 공유`, `위치 공유`, `현장 사진`, `복약 메모`를 한 번에 보여주고, 정산 카드는 `최종 금액`, `결제 수단`, `승인 상태`, `승인 번호`, `승인 시각`, `다음 방문`, `최종 리포트`를 묶어 보여준다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `AdminRequestOverview`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminOperationLineItem`, `AdminOperationCardModel`, `AdminOperationsDashboardModel`, `AdminOperationsPresentationFormatter`, `AdminOperationsCoordinator`, `AdminOperationCardBinder`
- `layout`: `activity_admin.xml`, `item_admin_operation_card.xml`, `item_admin_operation_line.xml`
- `values`: `strings.xml`

### 남은 범위

- 관리자 운영 화면의 배정/가이드/관리중 요청을 같은 수준의 객체들로 추가 분리
- 관리자 화면에서 정산 후속 처리, 문의 응답, 긴급 이슈 대응까지 실제 서버 액션 연결
- 매니저 문의하기, 후기/정산/SOS의 서버 저장 API 연결
## 24. 2026-04-23 관리자 요청 카드 객체 분리

### 구현

- `ui/admin` 아래에 `AdminManagedRequestFilter`, `AdminManagedRequestDateFilter`, `AdminRequestPresentationFormatter`, `AdminRequestCoordinator`, `AdminRequestCardBinder`와 요청 카드/필터 모델들을 추가했다.
- `AdminActivity`의 `배정 대기`와 `관리 중 요청` 영역은 이제 액티비티 안에서 문자열과 상세 패널을 직접 조합하지 않고, 코디네이터가 만든 카드 모델을 받아 렌더링만 하도록 바꿨다.
- `관리 중 요청`의 상태 필터, 날짜 필터, 요약 집계도 `AdminManagedRequestSectionModel` 기준으로 분리해서 액티비티 내부 조건문을 줄였다.
- 요청 카드의 상태 배지, 참여자 표시, 상세 운영 패널, 배정 버튼 목록은 `AdminRequestCardModel`과 `AdminRequestCardBinder`를 통해 일관되게 처리한다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/admin`: `AdminActivity`, `AdminManagedRequestFilter`, `AdminManagedRequestDateFilter`, `AdminManagedFilterChipModel`, `AdminManagedDateFilterChipModel`, `AdminRequestAssignActionModel`, `AdminRequestCardModel`, `AdminManagedRequestSectionModel`, `AdminRequestPresentationFormatter`, `AdminRequestCoordinator`, `AdminRequestCardBinder`

### 남은 범위

- 관리자 화면의 병원 가이드 목록/편집 영역도 같은 수준의 바인더와 코디네이터로 분리
- 관리자 화면의 정산 후속 처리, 문의 응답, 긴급 이슈 대응 액션의 실제 서버 연결
- 매니저 문의하기와 후기/정산/SOS의 서버 저장 API 연결
## 25. 2026-04-23 의존성 버전 중앙관리 + 관리자 가이드 영역 분리

### 구현

- 루트/앱 빌드 스크립트의 하드코딩된 플러그인과 라이브러리 버전을 [gradle/libs.versions.toml](/D:/BoDeul/gradle/libs.versions.toml:1) 기준의 version catalog로 옮겼다.
- [build.gradle.kts](/D:/BoDeul/build.gradle.kts:1), [app/build.gradle.kts](/D:/BoDeul/app/build.gradle.kts:1)는 catalog alias를 사용하도록 바꿨고, 실제 버전 값은 유지했다.
- 관리자 병원 가이드 영역은 `AdminGuideCoordinator`, `AdminGuideCardBinder`, `AdminGuideFormBinder`와 가이드 카드/폼 모델들로 분리했다.
- [AdminActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java:68)는 이제 병원 가이드 목록 카드와 폼 모드 문자열을 직접 조합하지 않고, 가이드 코디네이터와 바인더를 통해 렌더링한다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `gradle`: `libs.versions.toml`
- `build`: `build.gradle.kts`, `app/build.gradle.kts`
- `ui/admin`: `AdminActivity`, `AdminGuideCardModel`, `AdminGuideFormModel`, `AdminGuidePresentationFormatter`, `AdminGuideCoordinator`, `AdminGuideCardBinder`, `AdminGuideFormBinder`

### 남은 범위

- 관리자 매니저 서류 검토 영역도 같은 수준의 코디네이터/바인더 구조로 분리
- 관리자 정산 후속 처리, 문의 응답, 긴급 이슈 대응 액션의 실제 서버 연결
- 매니저 문의하기와 후기/정산/SOS의 서버 저장 API 연결
## 26. 2026-04-23 관리자 매니저 서류 검토 영역 분리

### 구현

- `ui/admin` 아래에 `AdminManagerDocumentCardModel`, `AdminManagerDocumentHistoryItemModel`, `AdminManagerDocumentPresentationFormatter`, `AdminManagerDocumentCoordinator`, `AdminManagerDocumentCardBinder`, `AdminManagerDocumentHistoryItemBinder`를 추가해서 서류 검토 카드와 검토 이력 렌더링을 객체로 분리했다.
- `AdminActivity`는 이제 서류 상태 문구, 배지 색상, 타임라인 문구, 이력 본문을 직접 조합하지 않고, 코디네이터가 만든 모델을 받아 카드 렌더링과 클릭 처리만 맡는다.
- `검토 이력 보기` 다이얼로그도 동일한 이력 모델과 바인더를 사용하도록 바꿔서 카드 화면과 이력 다이얼로그가 같은 표현 규칙을 공유하게 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/admin`: `AdminActivity`, `AdminManagerDocumentCardModel`, `AdminManagerDocumentHistoryItemModel`, `AdminManagerDocumentPresentationFormatter`, `AdminManagerDocumentCoordinator`, `AdminManagerDocumentCardBinder`, `AdminManagerDocumentHistoryItemBinder`

### 남은 범위

- 관리자 서류 검토의 `승인/보완 요청` 후속 액션을 서버 감사 로그나 알림 전송과 연결
- 관리자 정산 후속 처리, 문의 응답, 긴급 이슈 대응 액션의 실제 서버 연동
- 매니저 문의하기와 후기/정산/SOS 저장 API 연결
## 27. 2026-04-23 관리자 후속 처리 / 문의 저장 흐름 연결

### 구현

- `AdminRepository`, `ManagerRepository` 계약에 관리자 정산 후속 처리, 긴급 이슈 처리, 매니저 문의 응답/조회 메서드를 추가하고 목업/Firebase 구현까지 연결했다.
- 관리자 화면은 `AdminOperationsCoordinator`, `AdminSupportCoordinator` 기준으로 `정산 후속 처리`, `긴급 이슈 대응`, `문의 응답` 버튼을 실제 저장 흐름과 연결했다.
- 매니저 문의하기는 `ManagerSupportActivity`, `ManagerSupportCoordinator` 기준으로 로컬 임시 저장 대신 저장소 기반 조회/등록 구조로 전환했다.
- 더 이상 사용하지 않는 `ManagerSupportPreferences`, 구형 문의 전용 로컬 모델들을 제거해 문의 도메인을 `SupportInquiry`로 일원화했다.
- `MockBodeulRepositoryTest`에 문의 등록/답변, 관리자 액션 레코드 생성 검증을 추가했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `data`: `AdminRepository`, `ManagerRepository`, `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`, `MockManagerRepository`
- `data/firebase`: `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `domain/model`: `AdminSettlementRecord`, `AdminEmergencyIssueRecord`, `AdminRequestActionOverview`, `SupportInquiry` 관련 모델
- `ui/admin`: `AdminActivity`, `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`, `AdminSupportCoordinator`, `AdminSupportInquiryPresentationFormatter`, `AdminOperationCardBinder`, `AdminSupportInquiryCardBinder`
- `ui/manager`: `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportInquiryCardBinder`
- `values/layout/test`: `strings_admin_manager_extension.xml`, `activity_manager_support.xml`, `item_admin_operation_card.xml`, `item_admin_support_inquiry.xml`, `item_manager_support_inquiry.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 후기 / 정산 / SOS 후속 데이터를 실제 서버 API와 완전히 동기화
- 관리자 화면의 후속 처리 액션에 알림, 감사 로그, 운영 이력 계층 추가
- 매니저 과거 이력에 평가 / 정산 필터와 실데이터 기준 집계 추가
## 28. 2026-04-23 예약 후속 후기 저장소 연동

### 구현

- `AppointmentFollowUpReviewRating`, `AppointmentFollowUpRecord`를 추가해 완료된 예약의 후기 값을 UI 로컬 객체가 아닌 도메인 모델로 분리했다.
- `BookingRepository`에 `getAppointmentFollowUp`, `saveAppointmentFollowUpReview` 계약을 추가하고, `MockBookingRepository`, `FirebaseBookingRepository`가 같은 방식으로 후기 조회/저장을 처리하도록 연결했다.
- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`는 더 이상 `BookingFollowUpPreferences`를 사용하지 않고 저장소에서 받은 후속 레코드를 기준으로 화면을 렌더링한다.
- 기존 로컬 임시 저장 전용 클래스인 `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`, `BookingFollowUpRating`은 제거했다.
- `MockBodeulRepositoryTest`에 완료 예약 기준 후기 조회/저장 테스트를 추가했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `AppointmentFollowUpReviewRating`, `AppointmentFollowUpRecord`
- `data`: `BookingRepository`, `MockBodeulRepository`
- `data/mock`: `MockBookingRepository`
- `data/firebase`: `FirebaseBookingRepository`
- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`
- `values/test`: `strings_booking_follow_up_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- SOS 접수와 정산 후속 확인도 같은 예약 후속 저장 모델로 확장
- 완료 예약 상세와 홈 요약에 저장된 후기 상태를 함께 노출
- 후기 / SOS 후속 데이터의 관리자 운영 화면 반영
## 29. 2026-04-23 예약 후속 정산/SOS 저장 확장

### 구현

- `AppointmentFollowUpRecord`를 `후기`, `정산 확인`, `SOS 기록`을 함께 담는 구조로 확장하고, `AppointmentFollowUpSettlementStatus`, `AppointmentFollowUpSupportEscalationStatus` 도메인 값을 추가했다.
- `BookingRepository`에 `saveAppointmentFollowUpSettlement`, `saveAppointmentFollowUpSupportEscalation` 계약을 추가하고, `MockBookingRepository`, `FirebaseBookingRepository`가 같은 문서에 병합 저장하도록 맞췄다.
- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `activity_booking_follow_up.xml`을 갱신해 `정산 확인 저장`, `정산 문의 기록`, `SOS 기록 상태`를 실제 저장 흐름으로 연결했다.
- 완료된 예약 상세는 `BookingStatusActivity`, `BookingStatusCoordinator`에서 후속 레코드를 함께 읽어 `후기`, `정산 확인`, `SOS 기록`을 리포트 카드에 노출하도록 변경했다.
- 환자/보호자 홈은 `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder` 기준으로 완료 예약의 후속 상태를 최근 요청/히어로 요약에 함께 보이도록 정리했다.
- 관리자 운영 화면은 `AdminRequestActionOverview`, `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`, `FirebaseAdminRepository`, `MockBodeulRepository`를 통해 완료 요청의 사용자 후속 상태를 `정산 확인` 카드에 함께 표기하도록 확장했다.
- `MockBodeulRepositoryTest`에 정산/SOS 저장 보존 테스트와 관리자 액션 오버뷰 후속 노출 테스트를 추가했고, `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 마쳤다.

### 변경 범위

- `domain/model`: `AppointmentFollowUpRecord`, `AppointmentFollowUpSettlementStatus`, `AppointmentFollowUpSupportEscalationStatus`, `AdminRequestActionOverview`
- `data`: `BookingRepository`, `MockBodeulRepository`
- `data/mock`: `MockBookingRepository`
- `data/firebase`: `FirebaseBookingRepository`, `FirebaseAdminRepository`
- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingPresentationFormatter`
- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`
- `ui/admin`: `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`
- `values/layout/test`: `activity_booking_follow_up.xml`, `strings_follow_up_status_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 예약 후속 데이터의 실제 서버 API 명세를 `review`, `settlement`, `support escalation` 단위로 분리해 백엔드 계약까지 확정
- 관리자 운영 화면에서 사용자 후속 상태를 기준으로 필터/우선순위 정렬 추가
- 후기/정산/SOS 후속 데이터에 대한 관리자 알림, 감사 로그, 운영 이력 계층 연결

## 30. 2026-04-23 관리자 운영 후속 우선순위/필터 정리

### 구현

- `AdminOperationsCoordinator`는 이제 `AdminMonitoringFilter`, `AdminSettlementFilter`를 받아 운영 대상과 정산 대상을 각각 필터링하고, 후속 상태 기준 우선순위 정렬을 먼저 적용한다.
- `AdminOperationsDashboardModel`은 `요약 -> 경고 문구 -> 필터 칩 -> 카드 목록` 구조로 확장했고, `AdminOperationCardModel`, `AdminOperationBadgeModel`, `AdminOperationBadgeTone`으로 상태 배지와 우선 확인 배지를 분리했다.
- `AdminActivity`, `activity_admin.xml`, `AdminOperationCardBinder`, `item_admin_operation_card.xml`은 실시간 운영/정산 섹션에 경고 요약, 필터 칩, 최근 처리 문구를 추가해 `긴급 대응`, `사용자 문의`, `관리자 미처리` 건을 바로 좁혀 볼 수 있게 바꿨다.
- `AdminOperationsPresentationFormatter`는 필터 라벨, 경고 요약, 최근 기록 문구를 전담하고, `strings_admin_operation_extension.xml`로 운영 확장 문구를 분리했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 통과했다.

### 변경 범위

- `ui/admin`: `AdminActivity`, `AdminOperationsCoordinator`, `AdminOperationsDashboardModel`, `AdminOperationsPresentationFormatter`, `AdminOperationCardModel`, `AdminOperationCardBinder`, `AdminMonitoringFilter`, `AdminSettlementFilter` 및 관련 칩/배지 모델
- `layout/values`: `activity_admin.xml`, `item_admin_operation_card.xml`, `strings_admin_operation_extension.xml`, `strings_admin_manager_extension.xml`

### 남은 범위

- 관리자 후속 처리 액션의 알림 전송, 감사 로그, 운영 이력 저장 계층 연결
- 후속 상태 기반 필터 규칙을 서버 쿼리/API 계약까지 끌어올려 데이터량이 커져도 같은 정렬 규칙을 유지
- 매니저 과거 이력 화면에 후기/정산 결과와 필터를 연결해 완료 이후 이력까지 같은 축으로 정리

## 31. 2026-04-23 매니저 과거 이력 후속 상태/필터 반영

### 구현

- `AppointmentRequestDetail`에 `followUpRecord`를 포함시키고, `MockBodeulRepository`, `MockManagerRepository`, `FirebaseManagerRepository`가 완료된 요청의 후기/정산/SOS 후속 기록을 함께 조합하도록 확장했다.
- `ManagerHistoryCoordinator`, `ManagerHistoryScreenModel`, `ManagerHistoryEntryCardModel`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`를 기준으로 과거 이력 화면을 `요약 -> 필터 칩 -> 완료 카드 목록` 구조로 재정리했다.
- 완료 카드에는 `동행 완료` 배지 외에 `후속 미기록`, `후기 저장`, `정산 확인/문의`, `SOS 기록` 후속 배지를 함께 노출하고, 최근 후속 기록 시각과 상세 라인도 같이 보여주도록 구성했다.
- `strings_manager_history_extension.xml`로 관련 문구를 분리했고, `MockBodeulRepositoryTest`에 매니저 이력 조회 시 후속 기록이 포함되는 테스트를 추가했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 통과했다.

### 변경 범위

- `domain/model`: `AppointmentRequestDetail`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockManagerRepository`
- `data/firebase`: `FirebaseManagerRepository`
- `ui/manager`: `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`, `ManagerHistoryScreenModel`, `ManagerHistoryEntryCardModel`, `ManagerHistoryFilter`, `ManagerHistoryFilterChipModel`, `ManagerHistoryBadgeModel`, `ManagerHistoryBadgeTone`
- `layout/values/test`: `activity_manager_history.xml`, `item_manager_history_entry.xml`, `strings_manager_history_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 관리자 후속 처리 액션의 알림/감사 로그/운영 이력 저장 계층 연결
- 후속 상태 기반 우선순위와 필터 규칙을 서버 응답 계약으로 승격
- 매니저 과거 이력 화면에 평가 평균, 정산 금액, 기간 필터 같은 운영용 지표 확장

## 32. 2026-04-23 관리자 후속 알림/감사 로그 저장 계층 연결

### 구현

- `AdminActionNotification`, `AdminAuditLogEntry`, `AdminActionSourceType`, `AdminActionNotificationLevel` 도메인을 추가하고 `AdminDashboard`에 후속 알림/감사 로그 목록을 포함시켰다.
- `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`는 이제 `정산 후속 저장`, `긴급 대응 저장`, `문의 응답 저장` 시 알림과 감사 로그를 같이 남기고, 관리자 대시보드 조회 때도 함께 불러온다.
- 관리자 화면에는 `후속 알림 및 감사 로그` 섹션을 추가했고, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryBinder` 기준으로 알림과 감사 로그를 시간순 카드 타임라인으로 노출한다.
- `activity_admin.xml`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`에 요약/빈 상태/카드 문구를 반영했고, `MockBodeulRepositoryTest`에 저장 직후 알림/감사 로그가 생성되는 테스트를 추가했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 통과했다.

### 변경 범위

- `domain/model`: `AdminDashboard`, `AdminActionNotification`, `AdminAuditLogEntry`, `AdminActionSourceType`, `AdminActionNotificationLevel`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterScreenModel`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`, `AdminActionCenterTone`
- `layout/values/test`: `activity_admin.xml`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 후속 상태 기반 우선순위와 필터 규칙을 서버 응답 계약으로 승격
- 관리자 후속 알림의 읽음/해결 상태와 운영 히스토리 필터 추가
- 매니저 과거 이력 화면에 평가 평균, 정산 금액, 기간 필터 같은 운영용 지표 확장

## 33. 2026-04-23 관리자 후속 알림 상태/매니저 이력 지표/의존성 점검

### 구현

- `AdminActionNotification`에 `isRead`, `readAt`, `isResolved`, `resolvedAt`, `resolvedByName` 필드를 추가하고, `AdminRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `MockBodeulRepository`에 읽음 처리와 해결 완료/재오픈 저장 흐름을 연결했다.
- 관리자 액션센터는 `AdminActionCenterActionType`, `AdminActionCenterActionModel`, 확장된 `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder` 기준으로 `미확인/읽음/해결 완료` 상태 배지와 `읽음 처리/해결 완료/다시 열기` 액션을 가진 카드 구조로 바꿨다.
- 액션센터 목록에는 `전체/미확인/해결 대기/해결 완료/감사 로그` 필터 칩을 추가해서 운영자가 필요한 후속 이력만 빠르게 추려보게 했다.
- `AdminActivity`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`에 상태 버튼과 저장 토스트를 반영했고, 액션 수행 뒤 최신 대시보드를 다시 바인딩하도록 정리했다.
- 매니저 과거 이력은 `ManagerHistoryMetricModel`, `ManagerHistoryMetricBinder`, `item_manager_history_metric.xml`을 추가해서 `완료 동행`, `후속 기록률`, `정산 문의`, `SOS 기록` 지표를 상단에 노출하도록 확장했다.
- `MockBodeulRepositoryTest`에 관리자 후속 알림 `읽음 -> 해결 완료 -> 다시 열기` 상태 전이 테스트를 추가했고, 테스트 결과는 `20 tests, 0 failures, 0 errors`로 확인했다.
- 의존성은 version catalog 기준으로 점검해 `Android Gradle Plugin 9.2.0`, `Gradle 9.4.1`, `AppCompat 1.7.1`, `androidx.credentials 1.5.0`으로 올렸고, `sdkmanager`로 `platforms;android-35`를 설치한 뒤 `compileSdk 35`에서 빌드 통과를 확인했다.

### 변경 범위

- `domain/model`: `AdminActionNotification`
- `data`: `AdminRepository`, `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`, `AdminActionCenterActionType`, `AdminActionCenterActionModel`, `AdminActionCenterFilter`, `AdminActionCenterFilterChipModel`
- `ui/manager`: `ManagerHistoryActivity`, `ManagerHistoryBinder`, `ManagerHistoryCoordinator`, `ManagerHistoryScreenModel`, `ManagerHistoryMetricModel`, `ManagerHistoryMetricBinder`
- `layout/values/test`: `item_admin_action_center_entry.xml`, `activity_manager_history.xml`, `item_manager_history_metric.xml`, `strings_admin_action_center_extension.xml`, `strings_manager_history_extension.xml`, `MockBodeulRepositoryTest`
- `gradle`: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`

### 남은 범위

- 관리자 후속 알림의 서버 측 우선순위/필터 규칙을 API 계약으로 올리기
- 관리자 후속 알림 상태 변경 시 푸시/운영 알림 계층 연결
- 매니저 과거 이력의 기간 필터, 정산 금액, 평가 평균 같은 운영 지표 추가

## 34. 2026-04-23 관리자 후속 알림 서버 계약화

### 구현

- `AdminActionNotificationState`, `AdminActionNotificationPriority`, `AdminActionNotificationFilterKey`, `AdminActionNotificationContract`를 추가해서 관리자 후속 알림의 상태, 우선순위, 필터 태그 계산 규칙을 도메인 객체로 분리했다.
- `AdminActionNotification`은 이제 `state`, `priority`, `filterKeys`를 함께 보관하고, 기존 `isRead`, `isResolved`만 있는 데이터도 생성자에서 같은 계약으로 보정한다.
- `FirebaseAdminRepository`는 후속 알림 생성, 읽음 처리, 해결 완료/재오픈 시 `state`, `priority`, `filterKeys`를 Firestore 문서에 함께 저장하고, 대시보드 로드 시에도 이 값을 우선 사용하도록 맞췄다.
- `MockBodeulRepository`도 같은 계약값으로 정렬과 상태 전이를 맞췄고, 관리자 액션센터는 `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder` 기준으로 로컬 조건문 대신 계약값만 사용하도록 바꿨다.
- 액션센터 카드는 `상태 배지 + 우선순위 배지`를 함께 보여주고, `미확인/해결 대기/해결 완료` 필터 역시 `filterKeys` 기준으로 계산한다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain --rerun-tasks`로 다시 수행했고, `MockBodeulRepositoryTest` 결과는 `20 tests, 0 failures, 0 errors`다.

### 변경 범위

- `domain/model`: `AdminActionNotification`, `AdminActionNotificationContract`, `AdminActionNotificationState`, `AdminActionNotificationPriority`, `AdminActionNotificationFilterKey`
- `data`: `MockBodeulRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`
- `layout/values/test`: `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 관리자 후속 알림 상태 변경을 푸시/운영 알림 전송 계층과 연결
- `filterKeys` 기반 서버 쿼리와 우선순위 응답 규격을 실제 API 명세로 고정
- 매니저 과거 이력에 기간 필터, 정산 금액, 평가 평균 같은 운영 지표를 추가
## 35. 2026-04-23 관리자 후속 알림 전달 기록 연동
### 구현

- `AdminActionDeliveryChannel`, `AdminActionDeliveryStatus`, `AdminActionDeliveryTrigger`, `AdminActionDeliveryRecord`를 추가해 후속 알림 생성/읽음/해결/재오픈 시점의 전달 이력을 별도 도메인으로 분리했다.
- `AdminDashboard`가 `actionDeliveries`를 함께 보유하도록 확장하고, `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`가 동일한 전달 기록 컬렉션을 조합하도록 맞췄다.
- 후속 알림 생성 시 `앱 푸시`, `운영 피드` 전달 기록을 같이 남기고, 읽음 처리/해결 완료/재오픈 시에는 추가 푸시 생략 여부와 운영 피드 반영 여부를 각각 기록하도록 저장소 로직을 정리했다.
- 관리자 화면에는 `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryCardBinder`를 추가해 `후속 알림 전달 기록` 섹션을 별도로 렌더링하도록 구성했다.
- `activity_admin.xml`, `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`을 추가/수정해 채널, 트리거, 상태, 최근 처리 시각, 전달 메모를 카드 형태로 노출했다.
- `MockBodeulRepositoryTest`에 전달 기록 생성/상태 전이 검증을 추가했고, `assembleDebug`, `testDebugUnitTest --rerun-tasks`를 모두 다시 통과시켰다.

### 변경 범위

- `domain/model`: `AdminDashboard`, `AdminActionDeliveryChannel`, `AdminActionDeliveryStatus`, `AdminActionDeliveryTrigger`, `AdminActionDeliveryRecord`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryDashboardModel`, `AdminActionDeliveryCardModel`, `AdminActionDeliveryCardBinder`
- `layout/values/test`: `activity_admin.xml`, `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 전달 기록을 실제 푸시 발송 큐/운영 알림 채널과 연결
- 전달 실패 재시도, 읽음 확인, 해결 완료 후 후속 SLA 규칙을 서버 계약으로 고정
- 관리자 액션센터의 필터와 전달 기록 섹션을 동일한 서버 응답 모델로 합치기
## 36. 2026-04-23 관리자 후속 알림 전달 계약/SLA 반영
### 구현

- `AdminActionDeliveryContract`, `AdminActionDeliveryState`, `AdminActionDeliveryPriority`, `AdminActionDeliveryFilterKey`, `AdminActionDeliverySlaStatus`를 추가해 전달 기록의 상태, 우선순위, 필터 태그, SLA, 재시도 규칙을 도메인 객체로 고정했다.
- `AdminActionDeliveryRecord`는 이제 `attemptCount`, `maxAttemptCount`, `confirmedAtMillis`, `nextRetryAtMillis`, `slaDueAtMillis`와 파생 필드를 함께 보유하고, 기존 데이터는 생성자에서 같은 규칙으로 보정하도록 정리했다.
- `MockBodeulRepository`, `FirebaseAdminRepository`는 읽음 처리 시 `APP_PUSH=confirmed`, 재오픈 시 `APP_PUSH=sent`, 운영 피드 반영 시 `OPERATIONS_FEED=confirmed`를 남기도록 바꿔 실제 채널 반영 흐름과 전달 기록을 더 가깝게 맞췄다.
- Firebase 전달 문서는 이제 `state`, `priority`, `filterKeys`, `slaStatus`, `attemptCount`, `maxAttemptCount`, `confirmedAt`, `nextRetryAt`, `slaDueAt`를 함께 저장하도록 확장했다.
- 관리자 전달 기록 섹션은 `확인 대기/조치 필요/완료` 요약과 `채널 + 전달 결과 + 처리 상태` 배지를 함께 보여주고, 본문에는 읽음 확인 마감/확인 시각/재시도 예정/SLA 상태를 함께 노출하도록 정리했다.
- `MockBodeulRepositoryTest`에 SLA 초과 시 `follow_up_required`로 승격되는 규칙을 추가했고, 읽음 처리 시 앱 푸시 전달 기록이 `confirmed`로 남는 흐름도 검증했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 수행했다.

### 변경 범위

- `domain/model`: `AdminActionDeliveryStatus`, `AdminActionDeliveryRecord`, `AdminActionDeliveryContract`, `AdminActionDeliveryState`, `AdminActionDeliveryPriority`, `AdminActionDeliveryFilterKey`, `AdminActionDeliverySlaStatus`
- `data`: `MockBodeulRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryCardModel`, `AdminActionDeliveryCardBinder`
- `layout/values`: `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`
- `docs`: `data-api-draft.md`, `firebase-setup.md`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 전달 실패 재시도 큐와 실제 푸시 발송 결과를 `adminActionDeliveries`에 연결
- 관리자 액션센터와 전달 기록 섹션을 같은 필터/정렬 응답 모델로 더 통합
- 운영자 읽음 확인 주체, 재시도 소진 정책, SLA 초과 알림 정책을 서버 작업 큐 문서까지 확장
## 37. 2026-04-24 관리자 후속 알림 전달 큐 연동
### 구현

- `FirebaseAdminRepository`가 앱 푸시 채널의 `sent` 전달 기록을 만들 때 `adminActionDeliveryJobs` 큐 문서를 함께 생성하도록 바꿨다. 생성/재오픈 알림은 큐에 넣고, 읽음 확인/해결 완료처럼 즉시 종료되는 기록은 기존처럼 바로 저장한다.
- Firebase 전달 기록 로딩 시에는 문서에 저장된 파생 상태를 그대로 믿지 않고 현재 시점 기준으로 `AdminActionDeliveryRecord`를 다시 계산하게 바꿔, SLA 마감 시각이 지나면 앱에서 바로 `조치 필요`로 보이도록 맞췄다.
- `functions/index.js`에 `deliverAdminActionDeliveryJobs`, `dispatchAdminActionDeliveryJobs`를 추가하고, `adminActionDeliveryJobs`의 `PENDING/FAILED` 작업을 선점해 실제 발송 또는 시뮬레이션 처리한 뒤 결과를 `adminActionDeliveries`에 다시 반영하도록 구성했다.
- Functions는 수신 관리자 계정을 `ADMIN` 역할 사용자 기준으로 해석하고, 연동값이 없으면 `SIMULATED`, 수신자 없음이면 `SKIPPED`, 오류면 `FAILED`, 성공이면 `SENT` 작업 상태로 남긴다.
- `firestore.rules`에 `appointmentFollowUps`, `supportInquiries`, `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs`, `appointmentReminderJobs` 접근 규칙을 추가해 Firebase 관리자 저장소와 큐 문서가 실제 권한 범위 안에서 동작하도록 정리했다.
- `data-api-draft.md`, `firebase-setup.md`에 `adminActionDeliveryJobs` 컬렉션, Functions 엔트리, 환경 변수, 처리 흐름을 문서화했다.

### 변경 범위

- `data/firebase`: `FirebaseAdminRepository`
- `functions`: `functions/index.js`
- `firebase`: `firestore.rules`
- `docs`: `data-api-draft.md`, `firebase-setup.md`

### 남은 범위

- 실제 푸시 공급자 응답 스펙에 맞춰 `ADMIN_PUSH_ENDPOINT` payload 필드를 최종 고정
- 전달 큐 결과와 관리자 액션센터 필터를 같은 서버 응답 모델로 더 통합
- 운영자 읽음 확인 주체, SLA 초과 재알림, 재시도 소진 후 수동 재발송 정책을 백오피스 작업 문서까지 확장

## 38. 2026-04-24 Gradle 성능/안정성 설정 점검
### 구현

- 루트 `gradle.properties`의 Gradle 데몬 JVM 메모리를 `-Xmx4096m`으로 상향하고 `daemon`, `parallel`, `caching`, `configuration-cache`를 명시해 Android Studio + Codex 작업 환경에서 빌드 재사용성을 높였다.
- 기존 `org.gradle.jvmargs=-Xmx2048m` 설정은 새 메모리 설정과 충돌하므로 제거하고 단일 값으로 정리했다.
- 루트 `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`을 다시 점검해 deprecated 설정, 중복 repository 선언, 불필요 의존성, 과도한 컴파일 옵션 여부를 확인했다.
- repository 선언은 `settings.gradle.kts`의 중앙 관리 한 곳으로 유지되고 있고, `app` 모듈의 `compileOptions`는 Java 17 단일 설정만 사용 중이라 추가 간소화가 필요하지 않음을 확인했다.
- `app/build.gradle.kts`의 `buildFeatures.resValues = true`는 `defaultConfig.resValue(...)` 사용 때문에 필수라 유지했다.
- 검증은 `help`, `assembleDebug`, `testDebugUnitTest`를 모두 다시 실행했고, `configuration-cache`가 저장 후 재사용되는 것까지 확인했다.

### 변경 범위

- `gradle`: `gradle.properties`
- `docs`: `implementation-status.md`

### 남은 범위

- Android Studio IDE 힙 메모리 설정이 별도로 낮게 잡혀 있다면 Gradle과 별개로 IDE 힙도 상향 검토
- CI가 있다면 CI 환경에서도 `configuration-cache` 재사용 여부를 한 번 더 확인

## 39. 2026-04-24 Gradle 병목 분석 점검
### 구현

- 루트/모듈 Gradle 스크립트와 실제 빌드 결과를 다시 점검한 결과, 현재 프로젝트에는 `kapt`, `annotationProcessor`, `ksp`, `productFlavors`가 없고 모듈도 `:app` 하나뿐이라 흔한 Gradle 병목 후보인 어노테이션 처리, flavor 조합 폭증, 모듈 간 중복 의존성 문제는 없음을 확인했다.
- `settings.gradle.kts`의 repository 선언은 `pluginManagement`와 `dependencyResolutionManagement` 두 블록으로만 유지되고 있고, 모듈별 `repositories {}` 반복 선언은 없어서 repository 중복으로 인한 sync 낭비도 없다.
- `app/build.gradle.kts`에는 `debug` 외 별도 build type 확장이나 flavor가 없고, `buildFeatures.resValues = true`만 `defaultConfig.resValue(...)` 때문에 유지되고 있어 debug 빌드에서 불필요하게 무거운 사용자 정의 설정은 없는 상태다.
- `assembleDebug --profile` 기준 주요 시간은 설정 단계가 아니라 Android 기본 태스크에 몰려 있었다. 상대적으로 크게 보인 항목은 `checkDebugDuplicateClasses`, `checkDebugAarMetadata`, `mergeDebugAssets`, `processDebugNavigationResources`, `mergeDebugNativeLibs`, `mergeDebugResources`, `processDebugResources`였고, `compileDebugKotlin`은 `NO-SOURCE` 상태로 약 `0.185s` 수준이었다.
- `help --no-configuration-cache --profile` 기준 설정 단계는 전체 `1.748s`였고, 그중 `:app` 구성 `1.140s`, 루트 프로젝트 구성 `0.608s`로 측정됐다. `configuration-cache`를 켠 상태에서는 같은 구성이 재사용되어 `help`, `assembleDebug`, `testDebugUnitTest` 모두 `Configuration cache entry reused`를 확인했다.
- `:app:properties` 기준 실제 적용값은 `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`, `org.gradle.daemon=true`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `android.nonTransitiveRClass=true`였다.
- AGP 9의 내장 Kotlin 때문에 Kotlin 소스가 없어도 `compileDebugKotlin`, `compileDebugUnitTestKotlin` 태스크가 `NO-SOURCE`로 생성되는 것을 확인했다. 이는 AGP 9 기본 동작이며, 현재 프로젝트에서는 성능 영향이 작고 opt-out은 안정성 리스크가 있어 추가 변경은 하지 않았다.

### 변경 범위

- `docs`: `implementation-status.md`

### 남은 범위

- 빌드 시간이 더 문제되면 Android 기본 태스크 비중이 큰 의존성 묶음(`Firebase Auth + Credentials + Google ID`)과 리소스 수를 기능 단위로 줄이는 방향 검토
- AGP 9의 `android.enableAppCompileTimeRClass` 같은 추가 최적화 플래그는 리소스 상수 사용 방식 점검 후 별도 브랜치에서 검증

## 40. 2026-04-24 관리자 액션센터/전달 기록 공용 응답 모델 정리
### 구현

- `AdminActionOverview`, `AdminActionContract`를 추가해 액션센터와 전달 기록 섹션이 함께 참조하는 공용 요약 카운트와 정렬 규칙을 도메인 계약으로 올렸다.
- `AdminDashboard`는 이제 `actionOverview`를 함께 보유하고, `MockAdminRepository`, `FirebaseAdminRepository`가 대시보드 조합 시 `actionNotifications`, `auditLogs`, `actionDeliveries`와 함께 같은 요약 응답을 생성하도록 맞췄다.
- `MockBodeulRepository`, `FirebaseAdminRepository`의 관리자 후속 처리 목록 정렬도 `AdminActionContract`를 사용하도록 통일해 목업/실데이터 모드 모두 `알림=priority -> createdAt`, `감사 로그=createdAt`, `전달 기록=priority -> processedAt` 기준을 공유하게 했다.
- `AdminActionCenterCoordinator`, `AdminActionDeliveryCoordinator`는 더 이상 UI 안에서 요약 카운트를 다시 세지 않고, 대시보드의 `actionOverview`를 그대로 사용해 요약 문구와 필터 칩 개수를 표시하도록 정리했다.
- `MockBodeulRepositoryTest`에 관리자 대시보드가 `actionOverview`와 공용 정렬 계약을 함께 반영하는 검증을 추가했고, `assembleDebug`, `testDebugUnitTest`를 다시 통과했다.

### 변경 범위

- `domain/model`: `AdminDashboard`, `AdminActionOverview`, `AdminActionContract`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionCenterCoordinator`, `AdminActionDeliveryCoordinator`, `AdminActivity`
- `docs`: `data-api-draft.md`, `implementation-status.md`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 실제 푸시 공급자 응답 스펙에 맞춰 `ADMIN_PUSH_ENDPOINT` payload 필드를 최종 고정
- 운영자 읽음 확인 주체, SLA 초과 재알림, 재시도 소진 후 수동 재발송 정책을 백오피스 작업 문서까지 확장
## 41. 2026-04-24 Firebase 개발용 기준선 초기화 절차 정리
### 구현

- Firestore에 누적된 테스트 데이터와 `merge` 기반 후속 문서 잔존 필드를 한 번에 정리할 수 있도록 [firebase-reset-baseline.md](/D:/BoDeul/docs/firebase-reset-baseline.md)를 추가해 초기화 원칙, 삭제 대상 컬렉션, 재시드 기준선을 문서로 고정했다.
- [reset-firestore-baseline.js](/D:/BoDeul/tools/firebase/reset-firestore-baseline.js)를 추가해 `appointmentRequests`, `companionSessions`, `sessionReports`, `appointmentFollowUps`, `supportInquiries`, 관리자 후속 처리 컬렉션, `appointmentReminderJobs`까지 비우고, 기존 Auth UID 기준으로 `users`, `hospitalGuides`를 다시 만드는 개발용 절차를 자동화했다.
- 스크립트는 실제 삭제 전에 기준선 이메일 4개(`admin`, `patient`, `guardian`, `manager`)가 `Firebase Authentication`에 모두 존재하는지 확인하고, `--apply`에서는 누락된 계정도 기준선으로 자동 생성하도록 구성했다.
- [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에도 기준선 초기화 문서와 실행 스크립트 링크를 연결했다.

### 변경 범위

- `tools/firebase`: `package.json`, `reset-firestore-baseline.js`
- `docs`: `firebase-reset-baseline.md`, `firebase-setup.md`, `implementation-status.md`

### 남은 범위

- 실제 Firebase 프로젝트에 적용하기 전 `dry-run`으로 현재 문서 수와 누락된 Auth 계정을 확인
- 기준선 초기화 후 환자/보호자/매니저/관리자 로그인과 예약 병원 선택, 관리자 가이드 목록을 실제 Firebase 모드에서 재검증
## 42. 2026-04-24 Firebase 운영 스크립트 디렉터리 분리
### 구현

- 개발용 기준선 초기화 스크립트를 배포 코드인 `functions/`에서 분리해 [tools/firebase](/D:/BoDeul/tools/firebase) 디렉터리로 옮겼다.
- `functions/package.json`에 붙어 있던 기준선 초기화 npm 스크립트는 제거하고, 운영 도구 전용 [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)을 추가해 `reset:baseline:dry-run`, `reset:baseline:apply`만 별도로 실행할 수 있게 정리했다.
- 새 [reset-firestore-baseline.js](/D:/BoDeul/tools/firebase/reset-firestore-baseline.js)는 `firebase-admin`이나 ADC에 기대지 않고, 로컬 `firebase login` 토큰과 REST API만으로 Auth 조회/기준선 생성, Firestore 컬렉션 초기화, `users`/`hospitalGuides` 재시드를 처리하도록 바꿨다.
- 초기화/시드/마이그레이션 같은 운영 도구는 앞으로도 `tools/firebase` 아래에 모으고, `functions/`는 실제 배포되는 백엔드 코드만 남기는 기준으로 정리했다.

### 변경 범위

- `tools/firebase`: `package.json`, `reset-firestore-baseline.js`
- `functions`: `package.json`
- `docs`: `firebase-reset-baseline.md`, `firebase-setup.md`, `implementation-status.md`

### 남은 범위

- `tools/firebase` 아래에 백업/복원, 샘플 데이터 주입, 컬렉션 상태 점검 스크립트까지 같은 규칙으로 정리
- 운영용 웹/백오피스가 분리되면 앱/Functions/운영 도구/관리자 프런트의 경계를 다시 문서화
## 43. 2026-04-24 Firebase 운영 도구 확장
### 구현

- `tools/firebase/lib` 공용 helper를 추가해 프로젝트 ID/토큰 해석, Auth 조회, Firestore 컬렉션 조회/삭제/저장 로직을 운영 스크립트들이 공통으로 쓰도록 정리했다.
- [check-firestore-state.js](/D:/BoDeul/tools/firebase/check-firestore-state.js)를 추가해 기준선 Auth 계정 존재 여부, `users` 문서 존재 여부, 관리 대상 컬렉션 문서 수를 한 번에 점검할 수 있게 했다.
- [backup-firestore-state.js](/D:/BoDeul/tools/firebase/backup-firestore-state.js)를 추가해 관리 대상 컬렉션을 JSON 백업 파일로 저장하도록 했고, 백업 파일은 `tools/firebase/backups/` 아래에 쌓이도록 정리했다.
- [restore-firestore-state.js](/D:/BoDeul/tools/firebase/restore-firestore-state.js)를 추가해 백업 파일 기준 dry-run / 실제 복원을 나눠 실행할 수 있게 했다. 복원은 Firestore 문서만 대상으로 하고 Auth 계정은 유지한다.
- [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md)를 추가해 `check/reset/backup/restore` 사용법과 디렉터리 운영 기준을 문서화했다.

### 변경 범위

- `tools/firebase`: `package.json`, `check-firestore-state.js`, `backup-firestore-state.js`, `restore-firestore-state.js`, `reset-firestore-baseline.js`, `backups/.gitkeep`
- `tools/firebase/lib`: `baseline-config.js`, `firebase-toolkit.js`
- `docs`: `firebase-setup.md`, `firebase-operations-tools.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- `tools/firebase`에 샘플 예약/세션/후속 처리 흐름을 넣는 데이터 주입 스크립트 추가
- 백업 파일 검증용 스크립트와 컬렉션 diff 도구 추가

## 44. 2026-04-24 Firebase 샘플 서비스 데이터 주입 스크립트 추가
### 구현

- [seed-sample-service-data.js](/D:/BoDeul/tools/firebase/seed-sample-service-data.js)를 추가해 기준선 Auth / `users` 문서가 준비된 상태에서 예약 대기, 진행 중 동행, 종료 후속 처리 3개 시나리오를 한 번에 Firestore에 주입할 수 있게 했다.
- 샘플 데이터는 `appointmentRequests`, `companionSessions`, `sessionReports`, `appointmentFollowUps`, `supportInquiries`, `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs`, `appointmentReminderJobs`를 고정 ID로 upsert하도록 구성해 반복 실행 시 중복 문서가 늘어나지 않게 했다.
- 요청 문서에는 예약 확장 필드(`appointmentAtEpochMillis`, `appointmentDateKey`, 결제/옵션/연결 사용자 정보)를 함께 넣고, 완료 시나리오에는 후기/정산/SOS 후속 기록과 관리자 후속 알림/전달 기록, 푸시 큐 작업까지 같이 생성하도록 맞췄다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)에 `seed:sample:dry-run`, `seed:sample:apply` 실행점을 추가하고, [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에 사용 절차를 문서화했다.
- 검증은 `npm run seed:sample:dry-run`, `npm run seed:sample:apply`, `npm run check:state`, `.\gradlew.bat assembleDebug --console=plain` 순서로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `seed-sample-service-data.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### 남은 범위

- 백업 파일 검증용 스크립트와 컬렉션 diff 도구 추가
- 샘플 데이터를 역할별 화면 진입 기준으로 스냅샷 검증하는 체크리스트 또는 자동 점검 스크립트 추가

## 45. 2026-04-24 Firebase 백업 검증 / 상태 diff 도구 추가
### 구현

- [validate-firestore-backup.js](/D:/BoDeul/tools/firebase/validate-firestore-backup.js)를 추가해 백업 파일의 `schemaVersion`, `collections`, 문서 `path`/`id`/`fields` 구조를 검사하고, 관리 대상 컬렉션 누락이나 잘못된 경로, 중복 path를 오류/경고로 알려주도록 했다.
- [diff-firestore-state.js](/D:/BoDeul/tools/firebase/diff-firestore-state.js)를 추가해 백업 파일과 현재 Firestore 상태를 비교하고, 컬렉션별 추가/삭제/변경 문서를 요약할 수 있게 했다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)에 `validate:backup`, `diff:state` 실행점을 추가하고, [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에 사용 방법을 반영했다.
- 검증은 `node --check`로 스크립트 문법을 확인한 뒤 `npm run validate:backup -- --file ...`, `npm run diff:state -- --file ...`, `.\gradlew.bat assembleDebug --console=plain`로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `validate-firestore-backup.js`, `diff-firestore-state.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### 남은 범위

- 샘플 데이터를 역할별 화면 진입 기준으로 스냅샷 검증하는 체크리스트 또는 자동 점검 스크립트 추가
- 운영 도구 결과를 한 번에 보는 간단한 HTML/CLI 리포트 묶음 검토

## 46. 2026-04-24 Firebase 역할별 화면 진입 점검 / 운영 리포트 추가
### 구현

- [check-role-screen-readiness.js](/D:/BoDeul/tools/firebase/check-role-screen-readiness.js)를 추가해 환자/보호자/매니저/관리자 기준선 계정이 현재 Firebase 샘플 데이터만으로 실제 화면 진입에 필요한 컬렉션을 갖췄는지 자동 점검할 수 있게 했다.
- 점검 기준은 현재 Firebase 저장소 코드가 읽는 조합에 맞춰 잡았고, `예약 대기`, `진행 중 동행`, `종료 후속 처리` 샘플 시나리오가 요청/세션/리포트/후속 처리/관리자 전달 기록까지 연결됐는지도 함께 확인하도록 구성했다.
- [generate-operations-report.js](/D:/BoDeul/tools/firebase/generate-operations-report.js)와 [operations-report.js](/D:/BoDeul/tools/firebase/lib/operations-report.js)를 추가해 현재 상태, 역할별 점검 결과, 기준선 계정 상태, 컬렉션 문서 수, 백업 대비 diff를 한 번에 담은 HTML 운영 리포트를 생성할 수 있게 했다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)에 `check:readiness`, `report:ops` 실행점을 추가했고, [docs/firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [docs/firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에 사용 방법을 반영했다.
- 생성 리포트는 `tools/firebase/reports/` 아래에 저장하고, [.gitignore](/D:/BoDeul/.gitignore)에 HTML 결과물을 Git 추적 대상에서 제외하도록 정리했다.
- 검증은 `npm run check:readiness`, `npm run report:ops -- --file backups/firestore-backup-20260424-015754.json`, `.\gradlew.bat assembleDebug --console=plain` 순서로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `check-role-screen-readiness.js`, `generate-operations-report.js`, `reports/.gitkeep`
- `tools/firebase/lib`: `operations-report.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 운영 리포트에 스크린샷 또는 실제 앱 네비게이션 결과를 연결하는 단계 검토
- Firebase 운영 도구를 묶어 실행하는 단일 워크플로 스크립트 또는 체크리스트 정리

## 47. 2026-04-24 Firebase 운영 워크플로 스크립트 추가
### 구현

- [backup-validator.js](/D:/BoDeul/tools/firebase/lib/backup-validator.js)로 백업 검증 로직을 공용 helper로 분리하고, [validate-firestore-backup.js](/D:/BoDeul/tools/firebase/validate-firestore-backup.js)도 같은 로직을 재사용하도록 정리했다.
- [run-operations-workflow.js](/D:/BoDeul/tools/firebase/run-operations-workflow.js)를 추가해 현재 Firebase 상태 수집, 역할별 화면 진입 점검, 백업 검증, diff 계산, HTML 리포트 생성, JSON 요약 저장을 한 번에 수행할 수 있게 했다.
- 워크플로는 [firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js:9)에서 `firebase login` 저장 토큰이 만료되면 자동으로 refresh token으로 갱신하도록 보강한 뒤 실행되도록 맞췄다. 그래서 Studio 재시작이나 시간이 지난 뒤에도 운영 스크립트가 다시 401로 끊기지 않게 했다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)에 `workflow:ops` 실행점을 추가했고, [docs/firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [docs/firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에 `--strict`, `--json` 포함 사용 절차를 반영했다.
- 워크플로 산출물인 JSON 요약도 `tools/firebase/reports/` 아래에 저장하고 [.gitignore](/D:/BoDeul/.gitignore)에 HTML/JSON 산출물을 Git 추적 대상에서 제외하도록 정리했다.
- 검증은 `npm run validate:backup -- --file backups/firestore-backup-20260424-015754.json`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json`, `.\gradlew.bat assembleDebug --console=plain` 순서로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `validate-firestore-backup.js`, `run-operations-workflow.js`
- `tools/firebase/lib`: `backup-validator.js`, `firebase-toolkit.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 운영 리포트에 스크린샷 또는 실제 앱 네비게이션 결과를 연결하는 단계 검토
- 운영 워크플로 결과를 CI나 배포 전 점검 루틴과 연결할지 결정

## 48. 2026-04-24 로컬 프리플라이트 스크립트 추가
### 구현

- [run-local-preflight.js](/D:/BoDeul/tools/firebase/run-local-preflight.js)를 추가해 Firebase 운영 워크플로, `assembleDebug`, `testDebugUnitTest`를 한 번에 실행하는 로컬 프리플라이트 루틴을 만들었다.
- 프리플라이트는 중간 단계가 실패해도 마지막까지 실행한 뒤 전체 상태를 계산하고, 워크플로가 생성한 HTML/JSON 산출물과 함께 별도의 Markdown/JSON 요약 파일을 `tools/firebase/reports/` 아래에 남기도록 구성했다.
- 워크플로 단계는 `workflow:ops`를 내부에서 재사용하고, 백업 파일 경로가 주어지면 Firebase 점검 결과와 Gradle 빌드/테스트 결과를 한 묶음으로 기록한다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)에 `preflight:local` 실행점을 추가했고, [docs/firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [docs/firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에 사용 방법과 `--skip-workflow`, `--skip-build`, `--skip-tests` 옵션을 반영했다.
- 프리플라이트가 생성하는 Markdown 요약도 운영 리포트와 마찬가지로 [.gitignore](/D:/BoDeul/.gitignore)에 Git 추적 대상에서 제외하도록 정리했다.
- 검증은 `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json` 실행으로 완료했고, Firebase 운영 워크플로(`ready`), `assembleDebug`, `testDebugUnitTest`가 모두 통과했으며 요약 파일 [local-preflight-summary-20260424-125837.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-125837.md), [local-preflight-summary-20260424-125837.json](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-125837.json)을 생성했다.

### 변경 범위

- `tools/firebase`: `package.json`, `run-local-preflight.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 운영 리포트에 스크린샷 또는 실제 앱 네비게이션 결과를 연결하는 단계 검토
- 운영 워크플로/프리플라이트 결과를 CI나 배포 전 점검 루틴과 연결할지 결정

## 49. 2026-04-24 앱 화면 증적 캡처 및 운영 리포트 연동
### 구현

- [capture-app-navigation-evidence.js](/D:/BoDeul/tools/firebase/capture-app-navigation-evidence.js)를 추가해 연결된 에뮬레이터/디바이스의 현재 화면을 캡처하고, `reports/screenshots/` 아래 PNG와 `app-navigation-evidence-latest.json` 증적 파일로 남기도록 구성했다.
- 공용 helper [android-toolkit.js](/D:/BoDeul/tools/firebase/lib/android-toolkit.js)에서 `adb` 경로 탐색, 디바이스 선택, 현재 화면 캡처, 디바이스 메타데이터 수집을 분리했고, [app-navigation-evidence.js](/D:/BoDeul/tools/firebase/lib/app-navigation-evidence.js)에서 증적 파일 로드/정규화/기본 경로 결정을 맡도록 나눴다.
- [operations-report.js](/D:/BoDeul/tools/firebase/lib/operations-report.js), [generate-operations-report.js](/D:/BoDeul/tools/firebase/generate-operations-report.js), [run-operations-workflow.js](/D:/BoDeul/tools/firebase/run-operations-workflow.js), [run-local-preflight.js](/D:/BoDeul/tools/firebase/run-local-preflight.js)에 `--app-evidence` 연결을 추가해, 증적 파일이 있으면 운영 리포트 HTML과 워크플로/프리플라이트 요약에 앱 화면 섹션과 통계가 함께 반영되도록 했다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)에 `capture:app` 실행점을 추가했고, [.gitignore](/D:/BoDeul/.gitignore)에 `reports/screenshots/*.png`를 제외하도록 정리했다.
- 증적 포맷 예시는 [app-navigation-evidence.sample.json](/D:/BoDeul/tools/firebase/templates/app-navigation-evidence.sample.json)에 남겨 두었다.
- 검증은 `node --check tools/firebase/capture-app-navigation-evidence.js`, `node --check tools/firebase/generate-operations-report.js`, `node --check tools/firebase/run-operations-workflow.js`, `node --check tools/firebase/run-local-preflight.js`로 문법을 확인했고, `npm run report:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json`, `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json` 실행으로 리포트 [firestore-operations-report-20260424-131150.html](/D:/BoDeul/tools/firebase/reports/firestore-operations-report-20260424-131150.html), 요약 [firestore-operations-summary-20260424-131150.json](/D:/BoDeul/tools/firebase/reports/firestore-operations-summary-20260424-131150.json), 프리플라이트 [local-preflight-summary-20260424-131152.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-131152.md)를 생성했다. 실제 `adb` 캡처는 연결된 디바이스가 없어 도움말 확인까지만 수행했다.

### 변경 범위

- `tools/firebase`: `capture-app-navigation-evidence.js`, `generate-operations-report.js`, `run-operations-workflow.js`, `run-local-preflight.js`, `package.json`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-evidence.js`, `operations-report.js`
- `tools/firebase/templates`: `app-navigation-evidence.sample.json`
- `tools/firebase/reports/screenshots`: `.gitkeep`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 실제 에뮬레이터/디바이스가 연결된 상태에서 역할별 화면 이동을 어디까지 자동화할지 결정
- 운영 워크플로/프리플라이트 결과를 CI나 배포 전 점검 루틴과 연결할지 결정

## 50. 2026-04-24 CI 프리플라이트 및 GitHub Actions 연동
### 구현

- [run-ci-preflight.js](/D:/BoDeul/tools/firebase/run-ci-preflight.js)를 추가해 CI 환경에서 Firebase 입력이 준비되면 전체 프리플라이트를, 준비되지 않았으면 `--skip-workflow` 모드로 빌드/테스트만 수행하도록 분기했다.
- CI 실행점은 [run-local-preflight.js](/D:/BoDeul/tools/firebase/run-local-preflight.js)를 그대로 재사용하고, `--require-firebase`가 들어오면 `FIREBASE_TOKEN` 또는 프로젝트 식별 정보가 없을 때 실패로 종료하도록 했다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)에 `preflight:ci` 스크립트를 추가했다.
- [.github/workflows/android-preflight.yml](/D:/BoDeul/.github/workflows/android-preflight.yml)을 추가해 `pull_request`, `workflow_dispatch`에서 JDK 17/Node 22를 설정한 뒤 CI 프리플라이트를 실행하고, `tools/firebase/reports/` 산출물을 아티팩트로 업로드하도록 구성했다.
- 워크플로는 `secrets.GOOGLE_SERVICES_JSON`, `secrets.FIREBASERC_JSON`, `secrets.FIREBASE_TOKEN`, `vars.FIREBASE_PROJECT_ID`가 있으면 Firebase 운영 점검까지 포함하고, 없으면 자동으로 Android 빌드/테스트만 수행한다.
- 사용 방법과 필요한 시크릿 이름은 [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에 반영했다.
- 검증은 `node --check tools/firebase/run-ci-preflight.js`로 문법을 확인했고, `npm run preflight:ci -- --app-evidence templates/app-navigation-evidence.sample.json` 실행으로 Firebase 운영 워크플로(`ready`), `assembleDebug`, `testDebugUnitTest`가 모두 통과했으며 산출물 [firestore-operations-report-20260424-131815.html](/D:/BoDeul/tools/firebase/reports/firestore-operations-report-20260424-131815.html), [firestore-operations-summary-20260424-131815.json](/D:/BoDeul/tools/firebase/reports/firestore-operations-summary-20260424-131815.json), [local-preflight-summary-20260424-131817.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-131817.md)를 생성했다.

### 변경 범위

- `tools/firebase`: `run-ci-preflight.js`, `package.json`
- `.github/workflows`: `android-preflight.yml`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### 남은 범위

- 실제 에뮬레이터/디바이스가 연결된 상태에서 역할별 화면 이동을 어디까지 자동화할지 결정
- GitHub Actions에서 Firebase 시크릿을 실제로 연결한 뒤 운영 워크플로 포함 모드까지 검증

## 51. 2026-04-24 debug 자동 진입 액티비티 및 프리셋 캡처 연동
### 구현

- [AutomationEntryActivity.java](/D:/BoDeul/app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java)와 [app/src/debug/AndroidManifest.xml](/D:/BoDeul/app/src/debug/AndroidManifest.xml)을 추가해 debug 빌드에서만 `adb`가 직접 열 수 있는 자동 진입 액티비티를 만들었다.
- 자동 진입 액티비티는 `role`, `screen`, `requestId`, `forceSignIn` extra를 받아 기준선 계정(`admin@bodeul.app`, `manager@bodeul.app`, `patient@bodeul.app`, `guardian@bodeul.app`)으로 로그인한 뒤 홈, 예약 상세, 후속 처리, 보호자 리포트, 매니저 홈/과거 이력/가이드/문의/내 페이지, 관리자 대시보드로 라우팅한다.
- [app-navigation-routes.js](/D:/BoDeul/tools/firebase/lib/app-navigation-routes.js)에 역할별 프리셋과 기대 액티비티를 정리했고, [android-toolkit.js](/D:/BoDeul/tools/firebase/lib/android-toolkit.js)에 debug 자동 진입 실행과 포커스 대기 helper를 추가했다.
- [capture-app-navigation-evidence.js](/D:/BoDeul/tools/firebase/capture-app-navigation-evidence.js)는 `--preset` 기반 자동 진입, 포커스 확인, 상태 자동 판정(`passed`/`failed`)을 지원하도록 확장했다.
- 문서에는 프리셋 목록과 예시 명령을 반영했다.
- 검증은 `node --check tools/firebase/capture-app-navigation-evidence.js`, `node --check tools/firebase/lib/android-toolkit.js`, `node --check tools/firebase/lib/app-navigation-routes.js`, `.\gradlew.bat assembleDebug --console=plain`, `node tools/firebase/capture-app-navigation-evidence.js --help`로 확인했다.

### 변경 범위

- `app/src/debug`: `AndroidManifest.xml`, `java/com/example/bodeul/debug/AutomationEntryActivity.java`
- `tools/firebase`: `capture-app-navigation-evidence.js`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-routes.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### 남은 범위

- 실제 에뮬레이터/디바이스 연결 상태에서 프리셋별 자동 진입과 캡처를 한 번씩 실측 검증
- GitHub Actions에서 Firebase 시크릿을 실제로 연결한 뒤 운영 워크플로 포함 모드까지 검증

## 52. 2026-04-24 실기기 프리셋 자동 진입 및 화면 증적 실측
### 구현

- 연결된 실기기 `SM-S921N (Android 16)`에 [installDebug](/D:/BoDeul/app/build/outputs/apk/debug/app-debug.apk) 기준 최신 debug 앱을 다시 설치한 뒤 프리셋 전체를 실측했다.
- 자동 진입 실측 과정에서 `adb shell am start`만으로는 현재 태스크에 인텐트가 재전달되며 포커스 검증이 흔들리는 문제가 있어, [android-toolkit.js](/D:/BoDeul/tools/firebase/lib/android-toolkit.js)에서 프리셋 자동 진입 시 `-S` 강제 재시작을 붙이도록 수정했다.
- [app-navigation-routes.js](/D:/BoDeul/tools/firebase/lib/app-navigation-routes.js)의 기본 대기 시간을 10초로 늘렸고, [capture-app-navigation-evidence.js](/D:/BoDeul/tools/firebase/capture-app-navigation-evidence.js)에서는 `com.example.bodeul/.MainActivity`처럼 축약된 액티비티 표기도 정상 비교하도록 포커스 판정을 보강했다.
- 프리셋 `patient-home`, `guardian-home`, `patient-booking`, `guardian-booking-status`, `patient-booking-follow-up`, `guardian-report`, `manager-home`, `manager-history`, `manager-guide`, `manager-support`, `manager-profile`, `admin-dashboard`를 모두 실행했고, [app-navigation-evidence-latest.json](/D:/BoDeul/tools/firebase/reports/app-navigation-evidence-latest.json)에 `통과 12 / 경고 0 / 실패 0`으로 기록했다.
- 실기기 증적을 반영한 운영 리포트 [firestore-operations-report-20260424-133404.html](/D:/BoDeul/tools/firebase/reports/firestore-operations-report-20260424-133404.html), 요약 [firestore-operations-summary-20260424-133404.json](/D:/BoDeul/tools/firebase/reports/firestore-operations-summary-20260424-133404.json), 프리플라이트 [local-preflight-summary-20260424-133408.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-133408.md)를 다시 생성했다.
- 검증은 `.\gradlew.bat installDebug --console=plain`, 프리셋 전체 `node tools/firebase/capture-app-navigation-evidence.js --preset ...`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence reports/app-navigation-evidence-latest.json`, `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json --app-evidence reports/app-navigation-evidence-latest.json` 순서로 수행했다.

### 변경 범위

- `tools/firebase`: `capture-app-navigation-evidence.js`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-routes.js`
- `docs`: `firebase-operations-tools.md`, `implementation-status.md`

### 남은 범위

- GitHub Actions에서 Firebase 시크릿을 실제로 연결한 뒤 운영 워크플로 포함 모드까지 검증

## 53. 2026-04-24 GitHub Actions Firebase 시크릿 반영 준비 및 토큰 호환 보강
### 구현

- GitHub 원격과 CLI 인증 상태를 점검한 결과, 원격은 `git@github.com:bodeul110/Bodeul.git`이고 SSH 키는 `bodeul110` 계정으로 인증되지만, 현재 `gh` 로그인 계정은 `21017053`이라 `repos/bodeul110/Bodeul` API 접근이 `404`로 막혀 있는 상태를 확인했다.
- Firebase 공식 문서 기준 `FIREBASE_TOKEN`은 `firebase login:ci`가 발급하는 refresh token인데, 기존 [firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js)는 이를 단순 access token처럼 사용하고 있었다. 이를 보강해 `FIREBASE_TOKEN`이 refresh token이면 access token으로 자동 교환하고, 기존 access token 입력도 그대로 허용하도록 수정했다.
- 같은 파일에 `resolveFirebaseCiToken()`과 `resolveProjectId()` export를 추가해, GitHub 시크릿 반영 스크립트가 로컬 Firebase 로그인 상태나 `.firebaserc` / `app/google-services.json` 값을 그대로 재사용할 수 있게 했다.
- [github-toolkit.js](/D:/BoDeul/tools/github/lib/github-toolkit.js)를 추가해 origin 원격 해석, `gh api` 기반 저장소 접근 점검, GitHub Actions secret/variable 반영, `workflow_dispatch` 실행을 공용 helper로 분리했다.
- [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)는 `secrets.FIREBASE_TOKEN`, `secrets.GOOGLE_SERVICES_JSON`, `secrets.FIREBASERC_JSON`, `vars.FIREBASE_PROJECT_ID`를 한 번에 반영하고, `--dispatch`가 있으면 `android-preflight.yml`까지 바로 실행하도록 구성했다.
- 문서 [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)에는 `FIREBASE_TOKEN`의 refresh token 기준과 GitHub CLI 계정 권한 전제조건을 반영했다.
- 검증은 `node --check tools/github/configure-actions-firebase.js`, `node --check tools/github/lib/github-toolkit.js`, `node --check tools/firebase/lib/firebase-toolkit.js`, `node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run --skip-access-check`, `node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run`, `npm run preflight:ci -- --app-evidence templates/app-navigation-evidence.sample.json`, `.\gradlew.bat assembleDebug --console=plain`로 진행했고, 실제 접근 점검 모드에서는 현재 `gh` 계정이 `21017053`라 저장소 API 권한 부족으로 중단되는 것을 확인했다.

### 변경 범위

- `tools/firebase/lib`: `firebase-toolkit.js`
- `tools/github`: `configure-actions-firebase.js`
- `tools/github/lib`: `github-toolkit.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### 남은 범위

- `gh`를 `bodeul110/Bodeul` 저장소 권한이 있는 계정으로 다시 로그인한 뒤 `configure-actions-firebase.js`로 시크릿/변수 반영
- GitHub Actions에서 `android-preflight.yml`을 `require_firebase_ops=true`로 실제 실행해 전체 모드 검증

## 54. 2026-04-24 GitHub Actions 시크릿 반영 완료 및 원격 워크플로 부재 확인
### 구현

- `gh` 로그인 계정을 `bodeul110`으로 다시 맞춘 뒤 `gh api repos/bodeul110/Bodeul`로 저장소 관리자 권한을 확인했다.
- [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)를 실제 실행해 GitHub Actions 시크릿과 변수를 반영했다.
  - `secrets.FIREBASE_TOKEN`
  - `secrets.GOOGLE_SERVICES_JSON`
  - `secrets.FIREBASERC_JSON`
  - `vars.FIREBASE_PROJECT_ID=bodeul-dev`
- 검증은 `gh api repos/bodeul110/Bodeul/actions/secrets`, `gh api repos/bodeul110/Bodeul/actions/variables`로 다시 조회해 시크릿 3개와 변수 1개가 생성된 것을 확인했다.
- 이어서 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master`를 시도했지만, 원격 기본 브랜치에 `.github/workflows/android-preflight.yml`이 아직 없어 `workflow ... not found on the default branch`로 중단되는 것을 확인했다.
- 즉, GitHub Actions 전체 모드의 마지막 차단점은 시크릿이 아니라 워크플로 파일이 아직 원격 저장소에 push되지 않은 상태라는 점이다.

### 변경 범위

- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 외부 상태: `bodeul110/Bodeul` 저장소 GitHub Actions 시크릿 3개, 변수 1개

### 남은 범위

- 로컬의 `.github/workflows/android-preflight.yml`을 원격 기본 브랜치에 반영
- 반영 후 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true`로 실제 전체 모드 실행 검증

## 55. 2026-04-24 GitHub Actions `app_evidence` 경로 보정
### 구현

- 첫 번째 GitHub Actions 전체 모드 실행에서 `CI 프리플라이트 실행` 단계가 `tools/firebase/tools/firebase/templates/app-navigation-evidence.sample.json`를 찾다가 실패하는 것을 확인했다.
- 원인은 [app-navigation-evidence.js](/D:/BoDeul/tools/firebase/lib/app-navigation-evidence.js)가 `--app-evidence` 입력을 현재 작업 디렉터리 기준으로만 해석해서, `tools/firebase` 내부에서 실행될 때 repo 루트 기준 경로를 중복으로 붙이던 점이었다.
- 이를 수정해 `--app-evidence`가 들어오면 현재 작업 디렉터리 기준 경로와 repo 루트 기준 경로를 모두 검사하고, 실제 존재하는 파일을 우선 사용하도록 보정했다.
- 검증은 `node --check tools/firebase/lib/app-navigation-evidence.js`, `node tools/firebase/run-ci-preflight.js --require-firebase --app-evidence tools/firebase/templates/app-navigation-evidence.sample.json`, `.\gradlew.bat assembleDebug --console=plain`로 진행했고 모두 통과했다.

### 변경 범위

- `tools/firebase/lib`: `app-navigation-evidence.js`
- `docs`: `firebase-operations-tools.md`, `implementation-status.md`

### 남은 범위

- 경로 보정 커밋을 원격에 반영
- GitHub Actions `android-preflight.yml` 전체 모드를 재실행해 성공 여부 확인

## 56. 2026-04-24 GitHub Actions 전체 모드 실검증 성공
### 구현

- 경로 보정 커밋 `340a109`를 원격 `master`에 push한 뒤 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true --field app_evidence_path=tools/firebase/templates/app-navigation-evidence.sample.json`로 GitHub Actions 전체 모드를 다시 실행했다.
- 실행 런은 [24873140407](https://github.com/bodeul110/Bodeul/actions/runs/24873140407)이며, `preflight` 잡이 `2026-04-24T05:02:31Z`에 시작해 `2026-04-24T05:07:14Z`에 성공으로 종료된 것을 확인했다.
- 이 런에서 `google-services.json` 복원, `.firebaserc` 복원, `CI 프리플라이트 실행`, `운영 리포트 아티팩트 업로드`까지 모두 성공했고, Firebase 운영 워크플로 포함 모드가 GitHub Actions에서도 실제로 동작하는 것을 검증했다.
- 현재 남은 경고는 GitHub-hosted runner의 JavaScript action 런타임이 Node 20 deprecation 경고를 띄우는 점뿐이다. 워크플로 실패 원인은 아니며, 추후 `actions/checkout`, `actions/setup-java`, `actions/setup-node`, `actions/upload-artifact`의 Node 24 호환 버전 정책만 따라가면 된다.

### 변경 범위

- `docs`: `firebase-operations-tools.md`, `implementation-status.md`
- 외부 상태: GitHub Actions run `24873140407` 성공

### 남은 범위

- Node 20 deprecation 경고 대응 시점에 맞춰 GitHub Actions 런타임 정책만 점검

## 57. 2026-04-24 다중 작업자 협업 규칙 문서화
### 구현

- 여러 작업자가 동시에 들어와도 충돌을 줄일 수 있도록 [collaboration-rules.md](/D:/BoDeul/docs/collaboration-rules.md)를 새로 추가했다.
- 문서에는 시작 전 확인 순서, 충돌 위험이 큰 파일, 담당 범위 권장안, `implementation-status.md` 갱신 규칙, Firebase 운영 작업 단일 담당 원칙, 종료 전 체크리스트를 정리했다.
- [README.md](/D:/BoDeul/README.md) 문서 목록과 협업 설정 섹션에도 협업 규칙 문서 링크를 추가해 처음 들어오는 사람이 바로 찾을 수 있게 했다.

### 변경 범위

- `docs`: `collaboration-rules.md`, `implementation-status.md`
- 루트 문서: `README.md`

### 남은 범위

- 팀 내 실제 담당 방식에 맞춰 역할 구분이나 브랜치 규칙을 필요 시 더 구체화

## 58. 2026-04-24 협업 규칙에 작업 전 확인 절차 구체화
### 구현

- [collaboration-rules.md](/D:/BoDeul/docs/collaboration-rules.md)에 `누가 최근에 작업했는지`, `로컬과 원격 중 어느 쪽이 최신인지`, `안전하게 pull --rebase 하는 방법`을 구체적인 명령과 판별 기준까지 포함해 추가했다.
- `git log --format="%h %an %ad %s" --date=short -10`, `git rev-list --left-right --count HEAD...origin/master`, `git diff --stat HEAD..origin/master`, `git stash push -u` 같은 실사용 명령을 그대로 넣어 처음 보는 작업자도 바로 따라 할 수 있게 정리했다.
- [README.md](/D:/BoDeul/README.md) 협업 절차에도 시작 전에 최근 작업자와 로컬/원격 최신 여부를 먼저 확인하라는 안내와 핵심 명령을 추가했다.

### 변경 범위

- `docs`: `collaboration-rules.md`, `implementation-status.md`
- 루트 문서: `README.md`

### 남은 범위

- 팀에서 실제 사용하는 공유 채널 기준으로 `현재 작업 선언` 위치만 필요 시 문서에 추가
