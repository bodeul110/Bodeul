# 구현 ?�태

기�?: 2026-06-06

## 1. ?�재 ?�작?�는 기능

### ?�증

- ?�메??로그??/ ?�원가??/ 비�?번호 ?�설??- Google, Kakao, Naver 로그??- ?�메???�증, ?�로??보완
- Firebase 미설????목업 모드 ?�동 ?�환

### ?�자 / 보호??
- 병원 ?�행 ?�청 ?�성
- ???�청 목록 조회
- `REQUESTED` ?�태 ?�청 ?�정 / 취소
- `MATCHED` ?�태 ?�청 취소
- 권한 ?�음 / 로그???�요 / 불러?�기 ?�패 ?�태 ?�널 ?�시
- ?�청 ?�계?�서 ?�자-보호???�결 ?�보 ?�력
- 기존 계정???�으�??�메???�는 ?�화번호 기�? ?�동 ?�결
- 계정???�어???�청 ?�점 ?�름 / ?�화번호 / ?�메???�냅???�??- 보호??진행 ?�황 조회 �?카카?�맵 기반 ?�시�??�치 ?�인
- 최종 진료 리포??조회

### 매니?�

- 매니?� ??- ?�류 ?�록 ?�약 ?�??- ?�동 가???�정 ?�??- 매니?� ??권한 / 로그??/ 불러?�기 ?�패 ?�태 ?�널 ?�시
- 병원 ?�행 가?�드 진행 �?카카?�맵 기반 ?�시�??�치 ?�송
- ?�행 가?�드 권한 / 로그??/ 불러?�기 ?�패 ?�태 ?�널 ?�시
- 보호??공유 메시지 ?�??- 복약 메모 ?�??- 진료 리포???�??
### 관리자

- 미배???�청 조회
- ?�동 매칭
- 병원 가?�드 ?�록
- 병원 가?�드 ?�정 / ??��
- ?�영 ?�력 ?�태�??�터
- ?�영 ?�력 ?�짜�??�터
- ?�영 ?�청 ?�세 ?�보 ?�침
- ?�영 �??�청 조회
- 권한 ?�음 / 로그???�요 / 불러?�기 ?�패 ?�태 ?�널 ?�시

### ?�림 / ?�버

- ?�약 ??`appointmentAtEpochMillis`, `appointmentDateKey`, `reminderStages` ?�??- 매일 ?�전 9??기�? `D7`, `D3`, `D1` ?�림 ?�업 ?�성
- ?�림 ?�업 ??처리 �??��??�이??/ ?�발???�태 기록
- ?�용??문서 ?�성 / ?�정 ??기존 ?�청 문서 ?�동 ?�연�?- ?�약 취소 / ??�� / ?�정 변�????�아 ?�는 `appointmentReminderJobs` ?�동 ?�리

## 2. ?�번 ?�업?�서 구현???�용

- 관리자 ?�영 ?�력??`?�늘`, `?��??�는 ?�정`, `지???�정` ?�짜 ?�터�?추�??�다.
- ?�영 카드마다 ?�세 ?�보 ?�침 ?�역??추�????�청 ID, ?�션 ?�태, 계정 ?�결 ?�태, 보호??공유 메시지, 복약 메모�?바로 ?�인?????�게 ?�다.
- ?�태 ?�터?� ?�짜 ?�터�??�께 ?�용???�재 ?�시 건수?� 집계�??�시??보도�??�리?�다.
- 관??문자?�과 ?�재 ?�업 ?�태 문서�??�데?�트?�다.

## 3. 변경된 범위

- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/res/layout/activity_admin.xml`
- `app/src/main/res/layout/item_admin_request.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

## 4. ?��? 범위

### 기능

- ?�영 ?�력 ?�용 별도 ?�세 ?�면?� ?�직 ?�다.
- 매니?� ?�류 ?�일 ?�로?? 증빙 ?��?지 첨�?, 관리자 ?�인 ?�태???�직 ?�다.
- `IN_PROGRESS` ?�후 ?�청 변�?/ 취소 ?�책?� ?�직 ?�에??막기�??�고 별도 ?�영 ?�???�면?� ?�다.

### UI

- ?�제 ?�셜 ?�이�?리소??교체
- ?�짜 / ?�간 ?�택�?주�? 빠른 ?�택 UX 보강 ?��? 검??
## 5. ?�음 권장 ?�서

1. 매니?� ?�류 ?�일 ?�로??/ ?�인 ?�태 ?�장
2. 관리자 ?�영 ?�력 ?�용 ?�세 ?�면 검??3. ?�짜 / ?�간 ?�택�?주�? 빠른 ?�택 UX 검??
## 6. 검�?
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`

## 7. 2026-04-15 추�? ?�데?�트

### 구현

- 매니?� ?�에 `?�류 검???�태`, `관리자 메모` ?�시�?추�??�다.
- 매니?�가 ?�류 ?�약???�시 ?�?�하�??�태가 `PENDING_REVIEW`�?바뀌고 기존 관리자 메모??초기?�된??
- 관리자 ?�면??`매니?� ?�류 검?? ?�션??추�??�다.
- 관리자 ?�면?�서 매니?��?`?�류 ?�약`, `가???�정`, `검??메모`�?보고 `?�인`, `보완 ?�청`???�?�할 ???�다.
- 목업 ?�?�소?� Firebase ?�?�소 모두 같�? ?�태 모델???�용?�도�?맞췄??

### 변�?범위

- `domain/model`: `ManagerDocumentStatus`, `ManagerDocumentOverview`, `ManagerHomeProfile`, `AdminDashboard`
- `data`: `AdminRepository`, `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`, `ManagerActivity`
- `layout`: `activity_admin.xml`, `activity_manager_home.xml`, `item_admin_manager_document.xml`, `dialog_admin_document_review.xml`
- `test`: `MockBodeulRepositoryTest`

### ?��? 범위

- 매니?� ?�류 `?�제 ?�일 ?�로???� `증빙 ?��?지 미리보기`???�직 ?�다.
- 관리자 검???�력??`?�?�라??�?`?�당??로그`???�직 ?�다.
- ?�짜/?�간 ?�택기의 `빠른 ?�택 UX`??1�?반영??마쳤�? 추�? 보강 ??��???�아 ?�다.

## 8. 2026-04-15 추�? ?�데?�트

### 구현

- ?�행 ?�청 ?�면??`빠른 ?�짜 ?�택`, `빠른 ?�간 ?�택` 버튼??추�??�다.
- `?�늘`, `?�일`, `모레`, `?�전 10??, `?�후 2??, `?�후 4??�?바로 ?�택?????�다.
- 빠른 ?�짜?� 빠른 ?�간?� ?�적 ?�용?��?�??�짜�?먼�? 고르�??�간???�어??맞출 ???�다.
- 기존 `MaterialDatePicker`, `MaterialTimePicker` ?�름?� 그�?�??��??�다.

### 변�?범위

- `ui`: `BookingActivity`
- `layout`: `activity_booking.xml`
- `values`: `strings.xml`

### ?��? 범위

- 매니?� ?�류 `?�제 ?�일 ?�로???� `증빙 ?��?지 미리보기`
- 관리자 검???�력??`?�?�라??, `?�당??로그`
- 빠른 ?�택 버튼??`?�택 ?�태 강조`, `주말/?�간 ?�리?? 같�? 추�? UX

## 9. 2026-04-15 추�? ?�데?�트

### 구현

- 관리자 ?�류 검??카드??`?�류 ?�출 ?�각`, `최근 검???�각`, `?�당??�??�께 보여주는 ?�?�라?�을 추�??�다.
- 매니?� ???�로??모델???�류 ?�출/검???�각�?검???�당???�름???�?�하?�록 ?�장?�다.
- 매니?�가 ?�류 ?�약???�시 ?�?�하�?기존 검???�력?� 초기?�되�? 관리자가 ?�인 ?�는 보완 ?�청???�?�하�?검???�각�??�당???�름???�께 기록?�다.

### 변�?범위

- `domain/model`: `ManagerHomeProfile`
- `data`: `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`
- `layout`: `item_admin_manager_document.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### ?��? 범위

- 매니?� ?�류 `?�제 ?�일 ?�로??, `증빙 ?��?지 미리보기`
- 관리자 검???�력??별도 `로그/?�?�라???�면`
- 빠른 ?�택 버튼??`?�택 ?�태 강조`, `주말/?�간 ?�리??

## 10. 2026-04-15 추�? ?�데?�트

### 구현

- ?�행 ?�청??빠른 ?�짜/?�간 버튼???�재 ?�택 ?�태�?바로 보여주는 강조 ?��??�을 추�??�다.
- ?�짜가 `?�늘`, `?�일`, `모레` �??�나?� ?�치?�면 ?�당 버튼???�택 ?�태�??��??�다.
- ?�간??`?�전 10??, `?�후 2??, `?�후 4?? �??�나?� ?�치?�면 ?�당 ?�간 버튼???�께 강조?�다.
- ?�력/?�간 ?�택�? 빠른 ?�택 버튼, ?�정 모드 진입/?�제 모두 같�? ?�택 ?�태 계산???�사?�한??

### 변�?범위

- `ui`: `BookingActivity`
- `docs`: `implementation-status.md`

### ?��? 범위

- 매니?� ?�류 `?�제 ?�일 ?�로??, `증빙 ?��?지 미리보기`
- 관리자 검???�력??별도 `로그/?�?�라???�면`
- 빠른 ?�택 버튼??`주말/?�간 ?�리??

## 11. 2026-04-15 추�? ?�데?�트

### 구현

- 관리자 ?�류 카드??`검???�력 보기` 버튼??추�??�고, 별도 ?�이?�로그에???�출/?�인/보완 ?�청 기록???�간?�으�??�인?????�게 ?�다.
- 목업 ?�?�소?� Firebase ?�?�소 모두 `managerDocumentHistory` 배열???��??�도�??�장?�다.
- 매니?�가 ?�류 ?�약???�?�하�?`SUBMITTED` ?�력???�이�? 관리자가 ?�인 ?�는 보완 ?�청???�?�하�?`APPROVED`, `REJECTED` ?�력???�어???�적?�다.

### 변�?범위

- `domain/model`: `ManagerDocumentHistoryEntry`, `ManagerDocumentHistoryEventType`, `ManagerDocumentOverview`
- `data`: `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`
- `layout`: `dialog_admin_document_history.xml`, `item_admin_document_history.xml`, `item_admin_manager_document.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`
- `docs`: `data-api-draft.md`, `implementation-status.md`

### ?��? 범위

- 매니?� ?�류 `?�제 ?�일 ?�로??, `증빙 ?��?지 미리보기`
- 관리자 검???�력??`?�터/검??, `?�영 메모 고정` 같�? 2�?기능
- 빠른 ?�택 버튼??`주말/?�간 ?�리??

## 12. 2026-04-22 추�? ?�데?�트

### 구현

- 기능 ?�명?��? ?�그�?캡처 기�??�로 ?�면 개편??목표 구조 문서 `docs/restructure-target-map.md`�?추�??�다.
- ?�업 규칙??`?�티비티/?�래그먼?�에???�름 ?�어�??�기�???���?객체�?분리?�는 객체지???�칙`??명시?�다.
- ?�플?�시 진입 분기�?`EntryFlowCoordinator`�?분리??`?�플?�시 -> 권한 ?�내 -> ?�형 ?�택 -> 로그?? ?�름??조정?????�게 바꿨??
- `PermissionGuideActivity`, `PermissionGuideCatalog`, `PermissionGuideItem`, `PermissionGuideItemBinder`, `PermissionGuidePreferences`�?추�???권한 ?�내 ?�면�?권한 ?�청/?�??로직??객체�?분리?�다.
- ?�형 ?�택 ?�면???�그�?카드 구조??맞게 ?�시 구성?�고 `RoleOptionCardBinder`�??�택 강조 로직??분리?�다.
- 로그???�면?�서??매니?� ?�독 진입 ????�� 칩을 ?�겨 ?�그�??�름�?겹치??중복 ?�택??줄�???

### 변�?범위

- `AGENTS.md`
- `docs`: `implementation-status.md`, `restructure-target-map.md`
- `manifest`: `AndroidManifest.xml`
- `ui/auth`: `SplashActivity`, `RoleSelectionActivity`, `LoginActivity`, `EntryFlowCoordinator`, `PermissionGuideActivity`, `PermissionGuideCatalog`, `PermissionGuideItem`, `PermissionGuideItemBinder`, `PermissionGuidePreferences`, `RoleOptionCardBinder`
- `layout`: `activity_permission_guide.xml`, `item_permission_guide.xml`, `activity_role_selection.xml`
- `values`: `strings.xml`

### ?��? 범위

- 로그???�면???��? ?�각 ?�소?� ?�원가???�름???�그�??�계�??�면????가깝게 ?�리
- ?�자/보호???? ?�약 ?�청, 결제 ?�름??기능 ?�명??구조�??�분??- 매니?� ?�을 `?�시�?AI 매칭`, `주�? ?��??�자`, `경력/문의` 중심 구조�??�환
- ?�행 �??�치/채팅/?�장 ?�진/?�국 ?�계?� 종료 ???�기/?�산/긴급 ?�고 ?�면 추�?

## 13. 2026-04-22 추�? ?�데?�트

### 구현

- 기존 `MainActivity`???�시 ?�브 구조�??�거?�고 ?�자/보호???�용 ?�비???�으�?교체?�다.
- `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `ClientHomeNotice`, `ClientHomeNoticeProvider`�?추�??????�이??조합, ?�면 모델, �?바인?? ?�내 카드 구성??객체�?분리?�다.
- ???�면??`?�행 ?�청`, `진행 ?�황/보호??리포??, `최근 ?�수 ?�역`, `?�비???�내` ?�션??추�???기능 ?�명?�의 ?�자/보호??메인 ??구조�?반영?�다.
- 보호?�는 진행 �??�청???�으�?`보호??리포?? 중심?�로, ?�자??`?�청 ?�황` 중심?�로 ???�션�??�어�?카드 문구가 바뀌도�?구성?�다.
- 로그????관리자 계정?????�상 `MainActivity`�??�어?��? ?�도�?`AuthFlowRouter`�??�정??`AdminActivity`�?직접 분기?�도�??�리?�다.
- `assembleDebug --console=plain`?�로 빌드 검증을 ?�료?�다.

### 변�?범위

- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `ClientHomeNotice`, `ClientHomeNoticeProvider`
- `ui/auth`: `AuthFlowRouter`
- `ui/root`: `MainActivity`
- `layout`: `activity_main.xml`, `item_client_home_notice.xml`
- `values`: `strings.xml`
- `docs`: `implementation-status.md`

### ?��? 범위

- ?�자 건강 ?�로???�록, 병원 검?? ?�망 매니?� ?�별, ?�복/?�도, ?�상 비용, 결제/쿠폰 ?�름 추�?
- 매니?� ?�을 기능 ?�명??기�???`?�시�?매칭 On/Off`, `주�? ?��??�자`, `주요 메뉴 버튼` 구조�??�설�?- ?�시�??�치/?�심 채팅, ?�장 ?�진 ?�드, ?�국 ?�행/복약 ?�내, 최종 ?�기/?�산/SOS ?�면 추�?
- 관리자 ?�영 ?�면??최종 기능 ?�명??기�????�인·?�영·?�산 구조�??�장
## 14. 2026-04-22 추�? ?�데?�트

### 구현

- `BookingRequestDraft`, `BookingPriceSummary`?� ?�약 ?�션 ?�거?�을 추�????�약 ?�청 ?�이?��? 객체 ?�나�??�달?�도�?바꿨??
- `AppointmentRequest`??건강 ?�로?? ?�동 보조, ?�복/?�도, ?�망 매니?� ?�별, 결제 ?�단, 쿠폰, 비용 ?�약 ?�드�??�장?�다.
- `BookingCoordinator`, `BookingDashboard`, `BookingDashboardBinder`, `BookingFormBinder`, `BookingRequestCardBinder`, `BookingAppointmentSelector`, `BookingPriceEstimator`, `BookingPresentationFormatter`�?추�????�약 ?�면???�이??조합, ??검�? ?�짜 ?�택, ?�청 카드 ?�현??객체�?분리?�다.
- `BookingActivity`???�증 ?�인, ?�?�소 ?�출, ?�면 ?�환 같�? ?�름 ?�어�??�당?�도�??�시 구성?�다.
- ?�약 ?�면??`?�자 건강 ?�로??-> ?�결 ?�보 -> 방문 ?�정 -> ?�비???�션 -> 결제 �?쿠폰 -> 비용 ?�약` 구조�??�구?�했�? ?�청 카드?�도 건강 ?�로?�과 결제 ?�약???�께 보여주도�?바꿨??
- `testDebugUnitTest`, `assembleDebug --console=plain` 검증을 마쳤??

### 변�?범위

- `domain/model`: `AppointmentRequest`, `BookingRequestDraft`, `BookingPriceSummary`, `BookingTripType`, `BookingMobilitySupport`, `BookingManagerGenderPreference`, `BookingPaymentMethod`, `BookingCouponType`
- `data`: `BookingRepository`, `MockBodeulRepository`, `MockBookingRepository`, `FirebaseBookingRepository`
- `ui/booking`: `BookingActivity`, `BookingCoordinator`, `BookingDashboard`, `BookingDashboardBinder`, `BookingFormBinder`, `BookingRequestCardBinder`, `BookingAppointmentSelector`, `BookingPriceEstimator`, `BookingPresentationFormatter`, `BookingOptionGroupBinder`
- `layout`: `activity_booking.xml`, `item_booking_request.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### ?��? 범위

- 병원 검?? 지??기반 ?�치 ?�택, ?�제 결제 ?�료 ?�면�?쿠폰 ?�세 ?�용 규칙
- ?�자/보호?????�후??결제 ?�료, 매칭 ?��? ?�약 ?�세 ?�름 ?�결
- 매니?� ?�시�?매칭 ?? ?�행 �??�치/?�진/?�국 ?�계, 종료 ???�기/?�산/SOS ?�면
## 15. 2026-04-23 추�? ?�데?�트

### 구현

- `ManagerActivity`�??�증/?�태 ?�환/?�?�소 ?�출�??�당?�는 ?�름 ?�어기로 ?�리?�고, 매니?� ???�더링�? ?�용 객체???�임?�다.
- `ManagerHomeCoordinator`, `ManagerHomeScreenModel`, `ManagerHomeHeroModel`, `ManagerHomeActionCardModel`, `ManagerHomePromoCardModel`, `ManagerHomeLiveFeedModel`??추�????�기형 ?�과 진행???�을 같�? ?�면 모델 체계�?분리?�다.
- `ManagerHomeDashboardBinder`, `ManagerHomeActionCardBinder`, `ManagerHomePromoCardBinder`�?추�????�어�?카드, 빠른 ?�션, ?�개 카드, 진행 �??�행 카드�?객체 기�??�로 바인?�하?�록 바꿨??
- 매니?� ???�이?�웃??최신 ?�그�?기�??�로 ?�구?�해 `?�시�?AI 매칭 ?��?, `?�류 ?�록`, `?��?�??�록`, `과거 경력`, `문의?�기`, `?�늘 ?�결???�행 ?�정` 구조�?반영?�다.
- 진행 �??�행???�으�??�어�?버튼??바로 가?�드�??�고, ?��??�태?�서??매칭 ?�청???�시 ?�인?�는 ?�름?�로 분기?�도�??�리?�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `ui/manager`: `ManagerActivity`, `ManagerHomeCoordinator`, `ManagerHomeDashboardBinder`, `ManagerHomeActionCardBinder`, `ManagerHomePromoCardBinder`, `ManagerHomeScreenModel`, `ManagerHomeHeroModel`, `ManagerHomeActionCardModel`, `ManagerHomePromoCardModel`, `ManagerHomeLiveFeedModel`, `ManagerHomePresentationFormatter`, `ManagerHomeActionType`
- `layout`: `activity_manager_home.xml`, `item_manager_home_action_card.xml`, `item_manager_home_promo_card.xml`
- `values`: `strings.xml`

### ?��? 범위

- 병원 검?? 지??기반 ?�치 ?�택, ?�제 결제 ?�료 ?�면�??�약 ?�세/매칭 ?��??�름 ?�결
- ?�행 �??�시�??�치, ?�장 ?�진, ?�국/복약 ?�계, 종료 ???�기/?�산/SOS ?�면 개편
- 과거 경력, 문의?�기, ???�이지, 가?�드 ????�� ?�제 ?�면�??�이???�름?�로 ?�결
- 관리자 ?�영 ?�면??최종 기능 ?�명??기�???배정/?�산/모니?�링 구조�??�장

## 16. 2026-04-23 추�? ?�데?�트

### 구현

- `CompanionSession`??`locationSummary`, `fieldPhotoNote` ?�드�?추�????�치 공유?� ?�장 ?�진/?�류 메모�??�션 ?�이?�로 별도 관리하?�록 ?�장?�다.
- `ManagerRepository`?� 목업/Firebase ?�?�소???�치 메모, ?�장 메모 ?�??메서?��? 추�???매니?� 진행 ?�면???�력???�제 ?�션 ?�이?�로 반영?�도�??�리?�다.
- `ManagerGuideCoordinator`, `ManagerGuideScreenModel`, `ManagerGuideFocusModel`, `ManagerGuideStageModel`, `ManagerGuideDashboardBinder`, `ManagerGuideStageItemBinder`�?추�????�행 진행 ?�면???�계 계산, ?�커??카드 조합, ?�일 ?�더링을 ?�티비티 밖으�?분리?�다.
- `ManagerGuideActivity`???�증 ?�인, ?�?�소 ?�출, ?�???�션�??�당?�도�??�작?�했�? ?�이?�웃??최신 ?�그�?기�???`?�단 ?�행 ?�약 -> ?�계 ?�일 + ?�재 ?�계 ?�커??-> ?�치 공유/보호??공유 -> ?�장 ?�진/복약 -> 최종 리포?? 구조�?교체?�다.
- ?�자/보호???�약 ?�세??같�? ?�션 ?�이?��? 보도�?`BookingStatusCoordinator`???�치 메모?� ?�장 ?�진 메모 ?�인??추�??�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `domain/model`: `CompanionSession`
- `data`: `ManagerRepository`, `MockBodeulRepository`, `MockManagerRepository`, `FirebaseManagerRepository`, `FirebaseAdminRepository`, `FirebaseBookingRepository`, `FirebaseGuardianReportRepository`
- `ui/manager`: `ManagerGuideActivity`, `ManagerGuideCoordinator`, `ManagerGuideDashboardBinder`, `ManagerGuideStageItemBinder`, `ManagerGuideScreenModel`, `ManagerGuideFocusModel`, `ManagerGuideStageModel`, `ManagerGuideStageState`, `ManagerGuidePresentationFormatter`
- `ui/booking`: `BookingStatusCoordinator`
- `layout`: `activity_manager_guide.xml`, `item_manager_guide_stage.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### ?��? 범위

- 병원 검?? 지??기반 ?�치 ?�택, ?�제 결제 ?�료 ?�면 ?�결
- ?�자/보호???�약 ?�세??매칭 ?��??�후 ?�계?� 보호??리포???�면??최신 ?�그�??�름?�로 ?�렬
- ?�행 종료 ???�기/?�산/SOS, 매니?� 과거 경력/문의/???�이지, 관리자 ?�영 ?�면 개편

## 17. 2026-04-23 추�? ?�데?�트

### 구현

- `GuardianReportCoordinator`, `GuardianReportScreenModel`, `GuardianReportHighlightModel`, `GuardianReportEntryCardModel`, `GuardianReportLineItem`, `GuardianReportDashboardBinder`, `GuardianReportEntryCardBinder`, `GuardianReportPresentationFormatter`�?추�???보호??진행 ?�면??문자??조합�?카드 ?�더링을 ?�티비티 밖으�?분리?�다.
- `GuardianReportActivity`???�증 ?�인, ?�?�소 ?�출, 로딩/?�러 ?�태, ?�약 ?�세 ?�면 ?�동�??�당?�도�??�작?�했??
- 보호???�면 ?�이?�웃??최신 구조??맞춰 `?�단 계정 ?�약 -> ?�??진행 ?�황 -> ?�청�?진행 카드`�??�구?�했�? �?카드?�서 `진행 ?�태`, `?�치 공유 메모`, `?�장 ?�진 메모`, `?�장 복약 메모`, `최종 리포??�??�께 ?�인?????�게 바꿨??
- 보호??카드?� ?�??진행 ?�황 버튼?�서 바로 `BookingStatusActivity`�??�동?�도�??�결??진행 ?�면�??�약 ?�세 ?�름???�연?�럽�??�어지?�록 ?�리?�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `ui/report`: `GuardianReportActivity`, `GuardianReportCoordinator`, `GuardianReportDashboardBinder`, `GuardianReportEntryCardBinder`, `GuardianReportScreenModel`, `GuardianReportHighlightModel`, `GuardianReportEntryCardModel`, `GuardianReportLineItem`, `GuardianReportPresentationFormatter`
- `layout`: `activity_guardian_report.xml`, `item_guardian_report.xml`, `item_guardian_report_line.xml`
- `values`: `strings.xml`

### ?��? 범위

- 병원 검?? 지??기반 ?�치 ?�택, ?�제 결제 ?�료 ?�면 ?�결
- ?�자/보호???�약 ?�세??매칭 ?��??�후 ?�계?� 보호??메인 진행 ?�름????촘촘?�게 ?�렬
- ?�행 종료 ???�기/?�산/SOS, 매니?� 과거 경력/문의/???�이지, 관리자 ?�영 ?�면 개편

## 18. 2026-04-23 추�? ?�데?�트

### 구현

- `BookingRepository`??병원 ?�택??조회 메서?��? 추�??�고, 목업/Firebase ?�?�소 모두 `hospitalGuides`�?병원�??�보 목록?�로 묶어 반환?�도�??�장?�다.
- `BookingHospitalSelectorActivity`, `BookingHospitalSelectorCoordinator`, `BookingHospitalCatalog`, `BookingHospitalOptionAdapter`�?추�???병원/진료�?검?�과 ?�택???�약 ??밖의 별도 ?�름?�로 분리?�다.
- ?�약 ?��? 병원/진료과�? 직접 ?�력?��? ?�고 ?�택 결과�?반영?�도�?바꿨�? ?�택 직후 만남 ?�소가 비어 ?�으�?기본 만남 ?�치 문구�??�동 ?�안?�도�??�리?�다.
- `BookingCompletionActivity`, `BookingCompletionCoordinator`, `BookingCompletionBinder`, `BookingCompletionSnapshot`, `BookingCompletionScreenModel`??추�????�수/?�정 ???�스???�???�료 ?�약 ?�면?�로 ?�어지?�록 ?�구?�했??
- `BookingActivity`???�출 ?�공 ????초기?��? ?�?�보??갱신�??�당?�고, 병원 ?�택�??�료 ?�면 ?�동?� ?�용 객체?� ?�티비티�??�임?�도�??�리?�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `data`: `BookingRepository`, `MockBookingRepository`, `FirebaseBookingRepository`
- `domain/model`: `BookingHospitalOption`, `BookingHospitalSelection`
- `ui/booking`: `BookingActivity`, `BookingFormBinder`, `BookingHospitalSelectorActivity`, `BookingHospitalSelectorCoordinator`, `BookingHospitalCatalog`, `BookingHospitalOptionAdapter`, `BookingCompletionActivity`, `BookingCompletionCoordinator`, `BookingCompletionBinder`, `BookingCompletionSnapshot`, `BookingCompletionScreenModel`
- `layout`: `activity_booking.xml`, `activity_booking_hospital_selector.xml`, `item_booking_hospital_option.xml`, `activity_booking_completion.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`
- `test`: `MockBodeulRepositoryTest`

### ?��? 범위

- 지??기반 ?�치 ?�택�??�제 결제 ?�인/?�료 ?�계�??�약 ?�름???�결
- ?�자/보호???�약 ?�세??매칭 ?��??�후 ?�계?� 보호??메인 진행 ?�름????촘촘?�게 ?�렬
- ?�행 종료 ???�기/?�산/SOS, 매니?� 과거 경력/문의/???�이지, 관리자 ?�영 ?�면 개편

## 19. 2026-04-23 추�? ?�데?�트

### 구현

- `BookingLocationSelectorActivity`, `BookingLocationSelectorCoordinator`, `BookingLocationMapView`, `BookingLocationOptionAdapter`�?추�???만남 ?�소�??�유 ?�력???�니??지??기반 ?�택 ?�름?�로 분리?�다.
- ?�치 ?�택?� 병원/진료�??�택 결과�?기�??�로 `?�문 ?�내 ?�스??, `?�래 ?��??�운지`, `주차???�하�?구역`, `?�국 ?�결 지?? ?�보�?객체�?조합?�고, ?�약 ?�에???�택 결과�?반영?�도�??�리?�다.
- `BookingPaymentApprovalActivity`, `BookingPaymentApprovalCoordinator`, `BookingPaymentApprovalBinder`, `BookingPaymentCheckoutSnapshot`??추�????�약 ?�출 직전??결제 ?�인 ?�는 ?�장 결제 ?�정 ?�계�?별도 ?�면?�로 분리?�다.
- `BookingRequestDraft`?� `AppointmentRequest`??결제 ?�인 ?�태, ?�인 번호, ?�인 ?�각, ?�인 ?�단 ?�냅?�을 추�??�고, 목업/Firebase ?�?�소 모두 같�? ?�드�??�고 ?�도�??�장?�다.
- ?�약 ?�세?� ?�료 ?�면?�도 결제 ?�인 ?�태가 보이?�록 `BookingStatusCoordinator`, `BookingCompletionSnapshot`, `BookingCompletionCoordinator`, `BookingPresentationFormatter`�??�께 갱신?�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `domain/model`: `BookingMeetingLocationSelection`, `BookingMeetingPointOption`, `BookingPaymentApproval`, `BookingPaymentStatus`, `BookingRequestDraft`, `AppointmentRequest`
- `data`: `MockBodeulRepository`, `FirebaseBookingRepository`, `FirebaseManagerRepository`, `FirebaseGuardianReportRepository`, `FirebaseAdminRepository`
- `ui/booking`: `BookingActivity`, `BookingFormBinder`, `BookingLocationSelectorActivity`, `BookingLocationSelectorCoordinator`, `BookingLocationSelectorScreenModel`, `BookingLocationMapView`, `BookingLocationOptionAdapter`, `BookingPaymentApprovalActivity`, `BookingPaymentApprovalCoordinator`, `BookingPaymentApprovalBinder`, `BookingPaymentCheckoutSnapshot`, `BookingPaymentApprovalScreenModel`, `BookingStatusCoordinator`, `BookingCompletionSnapshot`, `BookingCompletionCoordinator`, `BookingPresentationFormatter`
- `layout`: `activity_booking.xml`, `activity_booking_location_selector.xml`, `item_booking_location_option.xml`, `activity_booking_payment_approval.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`

### ?��? 범위

- ?�자/보호???�약 ?�세??매칭 ?��??�후 ?�계?� 보호??메인 진행 ?�름????촘촘?�게 ?�렬
- ?�행 종료 ???�기/?�산/SOS, 매니?� 과거 경력/문의/???�이지, 관리자 ?�영 ?�면 개편
- 관리자 ?�영 ?�면??최종 기능 ?�명??기�???배정/?�산/모니?�링 구조�??�장
## 20. 2026-04-23 추�? ?�데?�트

### 구현

- `AppointmentProgressComposer`, `AppointmentProgressOverviewModel`, `AppointmentProgressStageModel`, `AppointmentProgressStageItemBinder`�?추�????�약 진행 ?�태�?공통 로드�?객체�??�석?�도�??�리?�다.
- `BookingStatusCoordinator`, `BookingStatusScreenModel`, `BookingStatusBinder`, `activity_booking_status.xml`??갱신???�약 ?�세�?`?�재 ?�태 ?�약 -> 진행 ?�계 -> ?�음 ?�내 -> 참여???�약 ?�약 -> 리포?? 구조�??�장?�다.
- `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `MainActivity`, `activity_main.xml`??갱신???�자·보호??메인 ?�에?�도 ?�???�청??진행 로드맵을 같�? 기�??�로 보여주도�?맞췄??
- 보호???��? `GuardianReportEntry`???�시�??�션/리포???�이?��? ?�선 ?�용?�고, ?�자 ?��? ?�청 ?�태만으로도 ?�일???�계 ?�석???��??�도�??�리?�다.
- `assembleDebug --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `ui/common`: `AppointmentProgressComposer`, `AppointmentProgressOverviewModel`, `AppointmentProgressStageModel`, `AppointmentProgressStageState`, `AppointmentProgressStageItemBinder`
- `ui/booking`: `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingStatusBinder`, `BookingStatusScreenModel`
- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`
- `app`: `MainActivity`
- `layout`: `activity_booking_status.xml`, `activity_main.xml`, `item_appointment_progress_stage.xml`
- `values`: `strings.xml`

### ?��? 범위

- 종료 ???�기/?�산/SOS ?�면�??�약 ?�료 ?�후 ?�속 ?�름 ?�리
- 매니?� `과거 경력`, `문의?�기`, `???�이지`�??�제 기능 ?�면�??�이???�름?�로 ?�결
- 관리자 ?�영 ?�면??최종 기능 ?�명??기�???배정/?�산/모니?�링 구조�??�장
## 21. 2026-04-23 추�? ?�데?�트

### 구현

- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`??추�????�료???�행 기�???`?�기 -> ?�산 ?�인 -> SOS ?�내` ?�속 ?�름??별도 ?�면?�로 분리?�다.
- ?�기 ?�택?� `BookingFollowUpRating`, `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`�?분리?�고, ?�약�?만족?��? ?�???�각??기기 로컬 ?�?�소???��??�도�??�리?�다.
- ?�료???�약 ?�세??기본 ?�션??`?�기·?�산·SOS 보기`�?바꾸�? 보호??계정?� 보조 ?�션?�로 기존 보호??리포?�도 계속 ?????�게 ?��??�다.
- SOS ?�역?�는 ?�당 매니?� ?�이???�결, `119` ?�이???�결, 긴급 ?�내 ?�이?�로그�? 추�????�료 ???�면?�서??바로 ?�???�름???�인?????�게 ?�다.
- `assembleDebug --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `BookingFollowUpRating`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`, `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`, `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingStatusActionType`
- `layout`: `activity_booking_follow_up.xml`, `item_booking_follow_up_rating.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`

### ?��? 범위

- 매니?� `과거 경력`, `문의?�기`, `???�이지`�??�제 기능 ?�면�??�이???�름?�로 ?�결
- 관리자 ?�영 ?�면??최종 기능 ?�명??기�???배정/?�산/모니?�링 구조�??�장
- ?�기/?�산/SOS???�버 ?�??API?� 관리자 ?�속 처리 ?�름??백엔??모델???�결
## 22. 2026-04-23 매니?� 과거 ?�력 / 문의?�기 / ???�이지 반영

### 구현

- `ManagerRepository`??`getManagerDocumentOverview`, `getManagerHistoryDetails`�?추�???매니?� ???�의 ?�제 ?�세 ?�면??같�? ?�?�소 계약?�로 ?�결?�다.
- `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`?� 관??카드 모델??추�????�료???�행 ?�션�?최종 리포?��? `과거 ?�행 ?�력` ?�면?�로 분리?�다.
- `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerProfileBinder`?� ?�류 ?�력 카드 바인?��? 추�???계정 ?�보, ?�류 ?�태, 검??메모, ?�동 가???�정, ?�류 검???�력??`???�이지` ?�면?�로 구성?�다.
- `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportPreferences`�?추�???`문의?�기` ?�면?�서 로컬 ?�??기반 문의 ?�수?� 최근 문의 ?�역 ?�인??가?�하?�록 ?�리?�다.
- `ManagerQuickNoteDialogController`, `ManagerQuickNoteType`?�로 ?�류 ?�약 / ?�정 ?�약 ?�정 ?�?�상?��? 공용 객체�?분리?�고, 매니?� ??빠른 ?�션�???`???�이지` ?�면??같�? ?�집 ?�름???�사?�하?�록 맞췄??
- `ManagerActivity`, `activity_manager_home.xml`, `AndroidManifest.xml`, `strings.xml`??갱신??빠른 ?�션�??�단 ?�비게이?�이 `과거 ?�력`, `가?�드`, `???�이지`, `문의?�기` ?�화면으�??�결?�게 ?�리?�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `data`: `ManagerRepository`
- `data/mock`: `MockManagerRepository`
- `data/firebase`: `FirebaseManagerRepository`
- `ui/manager`: `ManagerActivity`, `ManagerQuickNoteDialogController`, `ManagerQuickNoteType`, `ManagerInfoLineItem`, `ManagerDocumentHistoryItemModel`, `ManagerDocumentHistoryItemBinder`, `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerProfileBinder`, `ManagerProfileScreenModel`, `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`, `ManagerHistoryEntryCardModel`, `ManagerHistoryScreenModel`, `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportPreferences`, `ManagerSupportCategory`, `ManagerSupportInquiry`, `ManagerSupportInquiryStatus`, `ManagerSupportInquiryCardModel`, `ManagerSupportInquiryCardBinder`, `ManagerSupportScreenModel`
- `layout`: `activity_manager_home.xml`, `activity_manager_profile.xml`, `activity_manager_history.xml`, `activity_manager_support.xml`, `item_manager_info_line.xml`, `item_manager_document_history.xml`, `item_manager_history_entry.xml`, `item_manager_support_inquiry.xml`
- `manifest`: `AndroidManifest.xml`
- `values`: `strings.xml`

### ?��? 범위

- 관리자 ?�영 ?�면??최종 기능 ?�명??기�???배정 / ?�산 / 모니?�링 구조�??�장
- `문의?�기`???�버 ?�??API?� 관리자 ?�답 ?�름 ?�결
- 매니?� 과거 ?�력???�서비스 기�? ?�터 / ?�산 ?�역 / ?��? ?�이??추�?
## 23. 2026-04-23 관리자 ?�영 ?�면 모니?�링/?�산 ?�장

### 구현

- `AdminRequestOverview`??`sessionReport`�?추�??�고, `MockAdminRepository`, `FirebaseAdminRepository`가 ?�청�??�션 리포?�까지 ?�께 조합?�도�??�장?�다.
- `ui/admin` ?�래??`AdminOperationsPresentationFormatter`, `AdminOperationsCoordinator`, `AdminOperationCardBinder`?� ?�영 카드 모델?�을 추�???`?�시�??�영 모니?�링`, `?�산 ?�인` ?�션???�티비티 로직?�서 분리?�다.
- `activity_admin.xml`???�영/?�산 ?�약�?카드 컨테?�너�?추�??�고, `AdminActivity`?????�?�보??모델??받아 ?�션 ?�더링만 ?�당?�도�??�결?�다.
- 모니?�링 카드??`?�당 매니?�`, `?�재 ?�계`, `보호??공유`, `?�치 공유`, `?�장 ?�진`, `복약 메모`�???번에 보여주고, ?�산 카드??`최종 금액`, `결제 ?�단`, `?�인 ?�태`, `?�인 번호`, `?�인 ?�각`, `?�음 방문`, `최종 리포??�?묶어 보여준??
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `domain/model`: `AdminRequestOverview`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminOperationLineItem`, `AdminOperationCardModel`, `AdminOperationsDashboardModel`, `AdminOperationsPresentationFormatter`, `AdminOperationsCoordinator`, `AdminOperationCardBinder`
- `layout`: `activity_admin.xml`, `item_admin_operation_card.xml`, `item_admin_operation_line.xml`
- `values`: `strings.xml`

### ?��? 범위

- 관리자 ?�영 ?�면??배정/가?�드/관리중 ?�청??같�? ?��???객체?�로 추�? 분리
- 관리자 ?�면?�서 ?�산 ?�속 처리, 문의 ?�답, 긴급 ?�슈 ?�?�까지 ?�제 ?�버 ?�션 ?�결
- 매니?� 문의?�기, ?�기/?�산/SOS???�버 ?�??API ?�결
## 24. 2026-04-23 관리자 ?�청 카드 객체 분리

### 구현

- `ui/admin` ?�래??`AdminManagedRequestFilter`, `AdminManagedRequestDateFilter`, `AdminRequestPresentationFormatter`, `AdminRequestCoordinator`, `AdminRequestCardBinder`?� ?�청 카드/?�터 모델?�을 추�??�다.
- `AdminActivity`??`배정 ?��??� `관�?�??�청` ?�역?� ?�제 ?�티비티 ?�에??문자?�과 ?�세 ?�널??직접 조합?��? ?�고, 코디?�이?��? 만든 카드 모델??받아 ?�더링만 ?�도�?바꿨??
- `관�?�??�청`???�태 ?�터, ?�짜 ?�터, ?�약 집계??`AdminManagedRequestSectionModel` 기�??�로 분리?�서 ?�티비티 ?��? 조건문을 줄�???
- ?�청 카드???�태 배�?, 참여???�시, ?�세 ?�영 ?�널, 배정 버튼 목록?� `AdminRequestCardModel`�?`AdminRequestCardBinder`�??�해 ?��??�게 처리?�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `ui/admin`: `AdminActivity`, `AdminManagedRequestFilter`, `AdminManagedRequestDateFilter`, `AdminManagedFilterChipModel`, `AdminManagedDateFilterChipModel`, `AdminRequestAssignActionModel`, `AdminRequestCardModel`, `AdminManagedRequestSectionModel`, `AdminRequestPresentationFormatter`, `AdminRequestCoordinator`, `AdminRequestCardBinder`

### ?��? 범위

- 관리자 ?�면??병원 가?�드 목록/?�집 ?�역??같�? ?��???바인?��? 코디?�이?�로 분리
- 관리자 ?�면???�산 ?�속 처리, 문의 ?�답, 긴급 ?�슈 ?�???�션???�제 ?�버 ?�결
- 매니?� 문의?�기?� ?�기/?�산/SOS???�버 ?�??API ?�결
## 25. 2026-04-23 ?�존??버전 중앙관�?+ 관리자 가?�드 ?�역 분리

### 구현

- 루트/??빌드 ?�크립트???�드코딩???�러그인�??�이브러�?버전??[gradle/libs.versions.toml](/D:/BoDeul/gradle/libs.versions.toml:1) 기�???version catalog�???��??
- [build.gradle.kts](/D:/BoDeul/build.gradle.kts:1), [app/build.gradle.kts](/D:/BoDeul/app/build.gradle.kts:1)??catalog alias�??�용?�도�?바꿨�? ?�제 버전 값�? ?��??�다.
- 관리자 병원 가?�드 ?�역?� `AdminGuideCoordinator`, `AdminGuideCardBinder`, `AdminGuideFormBinder`?� 가?�드 카드/??모델?�로 분리?�다.
- [AdminActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java:68)???�제 병원 가?�드 목록 카드?� ??모드 문자?�을 직접 조합?��? ?�고, 가?�드 코디?�이?��? 바인?��? ?�해 ?�더링한??
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `gradle`: `libs.versions.toml`
- `build`: `build.gradle.kts`, `app/build.gradle.kts`
- `ui/admin`: `AdminActivity`, `AdminGuideCardModel`, `AdminGuideFormModel`, `AdminGuidePresentationFormatter`, `AdminGuideCoordinator`, `AdminGuideCardBinder`, `AdminGuideFormBinder`

### ?��? 범위

- 관리자 매니?� ?�류 검???�역??같�? ?��???코디?�이??바인??구조�?분리
- 관리자 ?�산 ?�속 처리, 문의 ?�답, 긴급 ?�슈 ?�???�션???�제 ?�버 ?�결
- 매니?� 문의?�기?� ?�기/?�산/SOS???�버 ?�??API ?�결
## 26. 2026-04-23 관리자 매니?� ?�류 검???�역 분리

### 구현

- `ui/admin` ?�래??`AdminManagerDocumentCardModel`, `AdminManagerDocumentHistoryItemModel`, `AdminManagerDocumentPresentationFormatter`, `AdminManagerDocumentCoordinator`, `AdminManagerDocumentCardBinder`, `AdminManagerDocumentHistoryItemBinder`�?추�??�서 ?�류 검??카드?� 검???�력 ?�더링을 객체�?분리?�다.
- `AdminActivity`???�제 ?�류 ?�태 문구, 배�? ?�상, ?�?�라??문구, ?�력 본문??직접 조합?��? ?�고, 코디?�이?��? 만든 모델??받아 카드 ?�더링과 ?�릭 처리�?맡는??
- `검???�력 보기` ?�이?�로그도 ?�일???�력 모델�?바인?��? ?�용?�도�?바꿔??카드 ?�면�??�력 ?�이?�로그�? 같�? ?�현 규칙??공유?�게 ?�리?�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `ui/admin`: `AdminActivity`, `AdminManagerDocumentCardModel`, `AdminManagerDocumentHistoryItemModel`, `AdminManagerDocumentPresentationFormatter`, `AdminManagerDocumentCoordinator`, `AdminManagerDocumentCardBinder`, `AdminManagerDocumentHistoryItemBinder`

### ?��? 범위

- 관리자 ?�류 검?�의 `?�인/보완 ?�청` ?�속 ?�션???�버 감사 로그???�림 ?�송�??�결
- 관리자 ?�산 ?�속 처리, 문의 ?�답, 긴급 ?�슈 ?�???�션???�제 ?�버 ?�동
- 매니?� 문의?�기?� ?�기/?�산/SOS ?�??API ?�결
## 27. 2026-04-23 관리자 ?�속 처리 / 문의 ?�???�름 ?�결

### 구현

- `AdminRepository`, `ManagerRepository` 계약??관리자 ?�산 ?�속 처리, 긴급 ?�슈 처리, 매니?� 문의 ?�답/조회 메서?��? 추�??�고 목업/Firebase 구현까�? ?�결?�다.
- 관리자 ?�면?� `AdminOperationsCoordinator`, `AdminSupportCoordinator` 기�??�로 `?�산 ?�속 처리`, `긴급 ?�슈 ?�??, `문의 ?�답` 버튼???�제 ?�???�름�??�결?�다.
- 매니?� 문의?�기??`ManagerSupportActivity`, `ManagerSupportCoordinator` 기�??�로 로컬 ?�시 ?�???�???�?�소 기반 조회/?�록 구조�??�환?�다.
- ???�상 ?�용?��? ?�는 `ManagerSupportPreferences`, 구형 문의 ?�용 로컬 모델?�을 ?�거??문의 ?�메?�을 `SupportInquiry`�??�원?�했??
- `MockBodeulRepositoryTest`??문의 ?�록/?��?, 관리자 ?�션 ?�코???�성 검증을 추�??�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `data`: `AdminRepository`, `ManagerRepository`, `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`, `MockManagerRepository`
- `data/firebase`: `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `domain/model`: `AdminSettlementRecord`, `AdminEmergencyIssueRecord`, `AdminRequestActionOverview`, `SupportInquiry` 관??모델
- `ui/admin`: `AdminActivity`, `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`, `AdminSupportCoordinator`, `AdminSupportInquiryPresentationFormatter`, `AdminOperationCardBinder`, `AdminSupportInquiryCardBinder`
- `ui/manager`: `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportInquiryCardBinder`
- `values/layout/test`: `strings_admin_manager_extension.xml`, `activity_manager_support.xml`, `item_admin_operation_card.xml`, `item_admin_support_inquiry.xml`, `item_manager_support_inquiry.xml`, `MockBodeulRepositoryTest`

### ?��? 범위

- ?�기 / ?�산 / SOS ?�속 ?�이?��? ?�제 ?�버 API?� ?�전???�기??- 관리자 ?�면???�속 처리 ?�션???�림, 감사 로그, ?�영 ?�력 계층 추�?
- 매니?� 과거 ?�력???��? / ?�산 ?�터?� ?�데?�터 기�? 집계 추�?
## 28. 2026-04-23 ?�약 ?�속 ?�기 ?�?�소 ?�동

### 구현

- `AppointmentFollowUpReviewRating`, `AppointmentFollowUpRecord`�?추�????�료???�약???�기 값을 UI 로컬 객체가 ?�닌 ?�메??모델�?분리?�다.
- `BookingRepository`??`getAppointmentFollowUp`, `saveAppointmentFollowUpReview` 계약??추�??�고, `MockBookingRepository`, `FirebaseBookingRepository`가 같�? 방식?�로 ?�기 조회/?�?�을 처리?�도�??�결?�다.
- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`?????�상 `BookingFollowUpPreferences`�??�용?��? ?�고 ?�?�소?�서 받�? ?�속 ?�코?��? 기�??�로 ?�면???�더링한??
- 기존 로컬 ?�시 ?�???�용 ?�래?�인 `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`, `BookingFollowUpRating`?� ?�거?�다.
- `MockBodeulRepositoryTest`???�료 ?�약 기�? ?�기 조회/?�???�스?��? 추�??�다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 ?�료?�다.

### 변�?범위

- `domain/model`: `AppointmentFollowUpReviewRating`, `AppointmentFollowUpRecord`
- `data`: `BookingRepository`, `MockBodeulRepository`
- `data/mock`: `MockBookingRepository`
- `data/firebase`: `FirebaseBookingRepository`
- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`
- `values/test`: `strings_booking_follow_up_extension.xml`, `MockBodeulRepositoryTest`

### ?��? 범위

- SOS ?�수?� ?�산 ?�속 ?�인??같�? ?�약 ?�속 ?�??모델�??�장
- ?�료 ?�약 ?�세?� ???�약???�?�된 ?�기 ?�태�??�께 ?�출
- ?�기 / SOS ?�속 ?�이?�의 관리자 ?�영 ?�면 반영
## 29. 2026-04-23 ?�약 ?�속 ?�산/SOS ?�???�장

### 구현

- `AppointmentFollowUpRecord`�?`?�기`, `?�산 ?�인`, `SOS 기록`???�께 ?�는 구조�??�장?�고, `AppointmentFollowUpSettlementStatus`, `AppointmentFollowUpSupportEscalationStatus` ?�메??값을 추�??�다.
- `BookingRepository`??`saveAppointmentFollowUpSettlement`, `saveAppointmentFollowUpSupportEscalation` 계약??추�??�고, `MockBookingRepository`, `FirebaseBookingRepository`가 같�? 문서??병합 ?�?�하?�록 맞췄??
- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `activity_booking_follow_up.xml`??갱신??`?�산 ?�인 ?�??, `?�산 문의 기록`, `SOS 기록 ?�태`�??�제 ?�???�름?�로 ?�결?�다.
- ?�료???�약 ?�세??`BookingStatusActivity`, `BookingStatusCoordinator`?�서 ?�속 ?�코?��? ?�께 ?�어 `?�기`, `?�산 ?�인`, `SOS 기록`??리포??카드???�출?�도�?변경했??
- ?�자/보호???��? `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder` 기�??�로 ?�료 ?�약???�속 ?�태�?최근 ?�청/?�어�??�약???�께 보이?�록 ?�리?�다.
- 관리자 ?�영 ?�면?� `AdminRequestActionOverview`, `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`, `FirebaseAdminRepository`, `MockBodeulRepository`�??�해 ?�료 ?�청???�용???�속 ?�태�?`?�산 ?�인` 카드???�께 ?�기?�도�??�장?�다.
- `MockBodeulRepositoryTest`???�산/SOS ?�??보존 ?�스?��? 관리자 ?�션 ?�버�??�속 ?�출 ?�스?��? 추�??�고, `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 마쳤??

### 변�?범위

- `domain/model`: `AppointmentFollowUpRecord`, `AppointmentFollowUpSettlementStatus`, `AppointmentFollowUpSupportEscalationStatus`, `AdminRequestActionOverview`
- `data`: `BookingRepository`, `MockBodeulRepository`
- `data/mock`: `MockBookingRepository`
- `data/firebase`: `FirebaseBookingRepository`, `FirebaseAdminRepository`
- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingPresentationFormatter`
- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`
- `ui/admin`: `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`
- `values/layout/test`: `activity_booking_follow_up.xml`, `strings_follow_up_status_extension.xml`, `MockBodeulRepositoryTest`

### ?��? 범위

- ?�약 ?�속 ?�이?�의 ?�제 ?�버 API 명세�?`review`, `settlement`, `support escalation` ?�위�?분리??백엔??계약까�? ?�정
- 관리자 ?�영 ?�면?�서 ?�용???�속 ?�태�?기�??�로 ?�터/?�선?�위 ?�렬 추�?
- ?�기/?�산/SOS ?�속 ?�이?�에 ?�??관리자 ?�림, 감사 로그, ?�영 ?�력 계층 ?�결

## 30. 2026-04-23 관리자 ?�영 ?�속 ?�선?�위/?�터 ?�리

### 구현

- `AdminOperationsCoordinator`???�제 `AdminMonitoringFilter`, `AdminSettlementFilter`�?받아 ?�영 ?�?�과 ?�산 ?�?�을 각각 ?�터링하�? ?�속 ?�태 기�? ?�선?�위 ?�렬??먼�? ?�용?�다.
- `AdminOperationsDashboardModel`?� `?�약 -> 경고 문구 -> ?�터 �?-> 카드 목록` 구조�??�장?�고, `AdminOperationCardModel`, `AdminOperationBadgeModel`, `AdminOperationBadgeTone`?�로 ?�태 배�??� ?�선 ?�인 배�?�?분리?�다.
- `AdminActivity`, `activity_admin.xml`, `AdminOperationCardBinder`, `item_admin_operation_card.xml`?� ?�시�??�영/?�산 ?�션??경고 ?�약, ?�터 �? 최근 처리 문구�?추�???`긴급 ?�??, `?�용??문의`, `관리자 미처�? 건을 바로 좁�? �????�게 바꿨??
- `AdminOperationsPresentationFormatter`???�터 ?�벨, 경고 ?�약, 최근 기록 문구�??�담?�고, `strings_admin_operation_extension.xml`�??�영 ?�장 문구�?분리?�다.
- 검증�? `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`�??�시 ?�과?�다.

### 변�?범위

- `ui/admin`: `AdminActivity`, `AdminOperationsCoordinator`, `AdminOperationsDashboardModel`, `AdminOperationsPresentationFormatter`, `AdminOperationCardModel`, `AdminOperationCardBinder`, `AdminMonitoringFilter`, `AdminSettlementFilter` �?관??�?배�? 모델
- `layout/values`: `activity_admin.xml`, `item_admin_operation_card.xml`, `strings_admin_operation_extension.xml`, `strings_admin_manager_extension.xml`

### ?��? 범위

- 관리자 ?�속 처리 ?�션???�림 ?�송, 감사 로그, ?�영 ?�력 ?�??계층 ?�결
- ?�속 ?�태 기반 ?�터 규칙???�버 쿼리/API 계약까�? ?�어?�려 ?�이?�량??커져??같�? ?�렬 규칙???��?
- 매니?� 과거 ?�력 ?�면???�기/?�산 결과?� ?�터�??�결???�료 ?�후 ?�력까�? 같�? 축으�??�리

## 31. 2026-04-23 매니?� 과거 ?�력 ?�속 ?�태/?�터 반영

### 구현

- `AppointmentRequestDetail`??`followUpRecord`�??�함?�키�? `MockBodeulRepository`, `MockManagerRepository`, `FirebaseManagerRepository`가 ?�료???�청???�기/?�산/SOS ?�속 기록???�께 조합?�도�??�장?�다.
- `ManagerHistoryCoordinator`, `ManagerHistoryScreenModel`, `ManagerHistoryEntryCardModel`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`�?기�??�로 과거 ?�력 ?�면??`?�약 -> ?�터 �?-> ?�료 카드 목록` 구조�??�정리했??
- ?�료 카드?�는 `?�행 ?�료` 배�? ?�에 `?�속 미기�?, `?�기 ?�??, `?�산 ?�인/문의`, `SOS 기록` ?�속 배�?�??�께 ?�출?�고, 최근 ?�속 기록 ?�각�??�세 ?�인??같이 보여주도�?구성?�다.
- `strings_manager_history_extension.xml`�?관??문구�?분리?�고, `MockBodeulRepositoryTest`??매니?� ?�력 조회 ???�속 기록???�함?�는 ?�스?��? 추�??�다.
- 검증�? `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`�??�시 ?�과?�다.

### 변�?범위

- `domain/model`: `AppointmentRequestDetail`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockManagerRepository`
- `data/firebase`: `FirebaseManagerRepository`
- `ui/manager`: `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`, `ManagerHistoryScreenModel`, `ManagerHistoryEntryCardModel`, `ManagerHistoryFilter`, `ManagerHistoryFilterChipModel`, `ManagerHistoryBadgeModel`, `ManagerHistoryBadgeTone`
- `layout/values/test`: `activity_manager_history.xml`, `item_manager_history_entry.xml`, `strings_manager_history_extension.xml`, `MockBodeulRepositoryTest`

### ?��? 범위

- 관리자 ?�속 처리 ?�션???�림/감사 로그/?�영 ?�력 ?�??계층 ?�결
- ?�속 ?�태 기반 ?�선?�위?� ?�터 규칙???�버 ?�답 계약?�로 ?�격
- 매니?� 과거 ?�력 ?�면???��? ?�균, ?�산 금액, 기간 ?�터 같�? ?�영??지???�장

## 32. 2026-04-23 관리자 ?�속 ?�림/감사 로그 ?�??계층 ?�결

### 구현

- `AdminActionNotification`, `AdminAuditLogEntry`, `AdminActionSourceType`, `AdminActionNotificationLevel` ?�메?�을 추�??�고 `AdminDashboard`???�속 ?�림/감사 로그 목록???�함?�켰??
- `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`???�제 `?�산 ?�속 ?�??, `긴급 ?�???�??, `문의 ?�답 ?�?? ???�림�?감사 로그�?같이 ?�기�? 관리자 ?�?�보??조회 ?�도 ?�께 불러?�다.
- 관리자 ?�면?�는 `?�속 ?�림 �?감사 로그` ?�션??추�??�고, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryBinder` 기�??�로 ?�림�?감사 로그�??�간??카드 ?�?�라?�으�??�출?�다.
- `activity_admin.xml`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`???�약/�??�태/카드 문구�?반영?�고, `MockBodeulRepositoryTest`???�??직후 ?�림/감사 로그가 ?�성?�는 ?�스?��? 추�??�다.
- 검증�? `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`�??�시 ?�과?�다.

### 변�?범위

- `domain/model`: `AdminDashboard`, `AdminActionNotification`, `AdminAuditLogEntry`, `AdminActionSourceType`, `AdminActionNotificationLevel`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterScreenModel`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`, `AdminActionCenterTone`
- `layout/values/test`: `activity_admin.xml`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`, `MockBodeulRepositoryTest`

### ?��? 범위

- ?�속 ?�태 기반 ?�선?�위?� ?�터 규칙???�버 ?�답 계약?�로 ?�격
- 관리자 ?�속 ?�림???�음/?�결 ?�태?� ?�영 ?�스?�리 ?�터 추�?
- 매니?� 과거 ?�력 ?�면???��? ?�균, ?�산 금액, 기간 ?�터 같�? ?�영??지???�장

## 33. 2026-04-23 관리자 ?�속 ?�림 ?�태/매니?� ?�력 지???�존???��?

### 구현

- `AdminActionNotification`??`isRead`, `readAt`, `isResolved`, `resolvedAt`, `resolvedByName` ?�드�?추�??�고, `AdminRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `MockBodeulRepository`???�음 처리?� ?�결 ?�료/?�오???�???�름???�결?�다.
- 관리자 ?�션?�터??`AdminActionCenterActionType`, `AdminActionCenterActionModel`, ?�장??`AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder` 기�??�로 `미확???�음/?�결 ?�료` ?�태 배�??� `?�음 처리/?�결 ?�료/?�시 ?�기` ?�션??가�?카드 구조�?바꿨??
- ?�션?�터 목록?�는 `?�체/미확???�결 ?��??�결 ?�료/감사 로그` ?�터 칩을 추�??�서 ?�영?��? ?�요???�속 ?�력�?빠르�?추려보게 ?�다.
- `AdminActivity`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`???�태 버튼�??�???�스?��? 반영?�고, ?�션 ?�행 ??최신 ?�?�보?��? ?�시 바인?�하?�록 ?�리?�다.
- 매니?� 과거 ?�력?� `ManagerHistoryMetricModel`, `ManagerHistoryMetricBinder`, `item_manager_history_metric.xml`??추�??�서 `?�료 ?�행`, `?�속 기록�?, `?�산 문의`, `SOS 기록` 지?��? ?�단???�출?�도�??�장?�다.
- `MockBodeulRepositoryTest`??관리자 ?�속 ?�림 `?�음 -> ?�결 ?�료 -> ?�시 ?�기` ?�태 ?�이 ?�스?��? 추�??�고, ?�스??결과??`20 tests, 0 failures, 0 errors`�??�인?�다.
- ?�존?��? version catalog 기�??�로 ?��???`Android Gradle Plugin 9.2.0`, `Gradle 9.4.1`, `AppCompat 1.7.1`, `androidx.credentials 1.5.0`?�로 ?�렸�? `sdkmanager`�?`platforms;android-35`�??�치????`compileSdk 35`?�서 빌드 ?�과�??�인?�다.

### 변�?범위

- `domain/model`: `AdminActionNotification`
- `data`: `AdminRepository`, `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`, `AdminActionCenterActionType`, `AdminActionCenterActionModel`, `AdminActionCenterFilter`, `AdminActionCenterFilterChipModel`
- `ui/manager`: `ManagerHistoryActivity`, `ManagerHistoryBinder`, `ManagerHistoryCoordinator`, `ManagerHistoryScreenModel`, `ManagerHistoryMetricModel`, `ManagerHistoryMetricBinder`
- `layout/values/test`: `item_admin_action_center_entry.xml`, `activity_manager_history.xml`, `item_manager_history_metric.xml`, `strings_admin_action_center_extension.xml`, `strings_manager_history_extension.xml`, `MockBodeulRepositoryTest`
- `gradle`: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`

### ?��? 범위

- 관리자 ?�속 ?�림???�버 �??�선?�위/?�터 규칙??API 계약?�로 ?�리�?- 관리자 ?�속 ?�림 ?�태 변�????�시/?�영 ?�림 계층 ?�결
- 매니?� 과거 ?�력??기간 ?�터, ?�산 금액, ?��? ?�균 같�? ?�영 지??추�?

## 34. 2026-04-23 관리자 ?�속 ?�림 ?�버 계약??
### 구현

- `AdminActionNotificationState`, `AdminActionNotificationPriority`, `AdminActionNotificationFilterKey`, `AdminActionNotificationContract`�?추�??�서 관리자 ?�속 ?�림???�태, ?�선?�위, ?�터 ?�그 계산 규칙???�메??객체�?분리?�다.
- `AdminActionNotification`?� ?�제 `state`, `priority`, `filterKeys`�??�께 보�??�고, 기존 `isRead`, `isResolved`�??�는 ?�이?�도 ?�성?�에??같�? 계약?�로 보정?�다.
- `FirebaseAdminRepository`???�속 ?�림 ?�성, ?�음 처리, ?�결 ?�료/?�오????`state`, `priority`, `filterKeys`�?Firestore 문서???�께 ?�?�하�? ?�?�보??로드 ?�에????값을 ?�선 ?�용?�도�?맞췄??
- `MockBodeulRepository`??같�? 계약값으�??�렬�??�태 ?�이�?맞췄�? 관리자 ?�션?�터??`AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder` 기�??�로 로컬 조건�??�??계약값만 ?�용?�도�?바꿨??
- ?�션?�터 카드??`?�태 배�? + ?�선?�위 배�?`�??�께 보여주고, `미확???�결 ?��??�결 ?�료` ?�터 ??�� `filterKeys` 기�??�로 계산?�다.
- 검증�? `assembleDebug --console=plain`, `testDebugUnitTest --console=plain --rerun-tasks`�??�시 ?�행?�고, `MockBodeulRepositoryTest` 결과??`20 tests, 0 failures, 0 errors`??

### 변�?범위

- `domain/model`: `AdminActionNotification`, `AdminActionNotificationContract`, `AdminActionNotificationState`, `AdminActionNotificationPriority`, `AdminActionNotificationFilterKey`
- `data`: `MockBodeulRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`
- `layout/values/test`: `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`, `MockBodeulRepositoryTest`

### ?��? 범위

- 관리자 ?�속 ?�림 ?�태 변경을 ?�시/?�영 ?�림 ?�송 계층�??�결
- `filterKeys` 기반 ?�버 쿼리?� ?�선?�위 ?�답 규격???�제 API 명세�?고정
- 매니?� 과거 ?�력??기간 ?�터, ?�산 금액, ?��? ?�균 같�? ?�영 지?��? 추�?
## 35. 2026-04-23 관리자 ?�속 ?�림 ?�달 기록 ?�동
### 구현

- `AdminActionDeliveryChannel`, `AdminActionDeliveryStatus`, `AdminActionDeliveryTrigger`, `AdminActionDeliveryRecord`�?추�????�속 ?�림 ?�성/?�음/?�결/?�오???�점???�달 ?�력??별도 ?�메?�으�?분리?�다.
- `AdminDashboard`가 `actionDeliveries`�??�께 보유?�도�??�장?�고, `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`가 ?�일???�달 기록 컬렉?�을 조합?�도�?맞췄??
- ?�속 ?�림 ?�성 ??`???�시`, `?�영 ?�드` ?�달 기록??같이 ?�기�? ?�음 처리/?�결 ?�료/?�오???�에??추�? ?�시 ?�략 ?��??� ?�영 ?�드 반영 ?��?�?각각 기록?�도�??�?�소 로직???�리?�다.
- 관리자 ?�면?�는 `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryCardBinder`�?추�???`?�속 ?�림 ?�달 기록` ?�션??별도�??�더링하?�록 구성?�다.
- `activity_admin.xml`, `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`??추�?/?�정??채널, ?�리�? ?�태, 최근 처리 ?�각, ?�달 메모�?카드 ?�태�??�출?�다.
- `MockBodeulRepositoryTest`???�달 기록 ?�성/?�태 ?�이 검증을 추�??�고, `assembleDebug`, `testDebugUnitTest --rerun-tasks`�?모두 ?�시 ?�과?�켰??

### 변�?범위

- `domain/model`: `AdminDashboard`, `AdminActionDeliveryChannel`, `AdminActionDeliveryStatus`, `AdminActionDeliveryTrigger`, `AdminActionDeliveryRecord`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryDashboardModel`, `AdminActionDeliveryCardModel`, `AdminActionDeliveryCardBinder`
- `layout/values/test`: `activity_admin.xml`, `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`, `MockBodeulRepositoryTest`

### ?��? 범위

- ?�달 기록???�제 ?�시 발송 ???�영 ?�림 채널�??�결
- ?�달 ?�패 ?�시?? ?�음 ?�인, ?�결 ?�료 ???�속 SLA 규칙???�버 계약?�로 고정
- 관리자 ?�션?�터???�터?� ?�달 기록 ?�션???�일???�버 ?�답 모델�??�치�?## 36. 2026-04-23 관리자 ?�속 ?�림 ?�달 계약/SLA 반영
### 구현

- `AdminActionDeliveryContract`, `AdminActionDeliveryState`, `AdminActionDeliveryPriority`, `AdminActionDeliveryFilterKey`, `AdminActionDeliverySlaStatus`�?추�????�달 기록???�태, ?�선?�위, ?�터 ?�그, SLA, ?�시??규칙???�메??객체�?고정?�다.
- `AdminActionDeliveryRecord`???�제 `attemptCount`, `maxAttemptCount`, `confirmedAtMillis`, `nextRetryAtMillis`, `slaDueAtMillis`?� ?�생 ?�드�??�께 보유?�고, 기존 ?�이?�는 ?�성?�에??같�? 규칙?�로 보정?�도�??�리?�다.
- `MockBodeulRepository`, `FirebaseAdminRepository`???�음 처리 ??`APP_PUSH=confirmed`, ?�오????`APP_PUSH=sent`, ?�영 ?�드 반영 ??`OPERATIONS_FEED=confirmed`�??�기?�록 바꿔 ?�제 채널 반영 ?�름�??�달 기록????가깝게 맞췄??
- Firebase ?�달 문서???�제 `state`, `priority`, `filterKeys`, `slaStatus`, `attemptCount`, `maxAttemptCount`, `confirmedAt`, `nextRetryAt`, `slaDueAt`�??�께 ?�?�하?�록 ?�장?�다.
- 관리자 ?�달 기록 ?�션?� `?�인 ?��?조치 ?�요/?�료` ?�약�?`채널 + ?�달 결과 + 처리 ?�태` 배�?�??�께 보여주고, 본문?�는 ?�음 ?�인 마감/?�인 ?�각/?�시???�정/SLA ?�태�??�께 ?�출?�도�??�리?�다.
- `MockBodeulRepositoryTest`??SLA 초과 ??`follow_up_required`�??�격?�는 규칙??추�??�고, ?�음 처리 ?????�시 ?�달 기록??`confirmed`�??�는 ?�름??검증했??
- 검증�? `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`�??�시 ?�행?�다.

### 변�?범위

- `domain/model`: `AdminActionDeliveryStatus`, `AdminActionDeliveryRecord`, `AdminActionDeliveryContract`, `AdminActionDeliveryState`, `AdminActionDeliveryPriority`, `AdminActionDeliveryFilterKey`, `AdminActionDeliverySlaStatus`
- `data`: `MockBodeulRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryCardModel`, `AdminActionDeliveryCardBinder`
- `layout/values`: `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`
- `docs`: `data-api-draft.md`, `firebase-setup.md`
- `test`: `MockBodeulRepositoryTest`

### ?��? 범위

- ?�달 ?�패 ?�시???��? ?�제 ?�시 발송 결과�?`adminActionDeliveries`???�결
- 관리자 ?�션?�터?� ?�달 기록 ?�션??같�? ?�터/?�렬 ?�답 모델�????�합
- ?�영???�음 ?�인 주체, ?�시???�진 ?�책, SLA 초과 ?�림 ?�책???�버 ?�업 ??문서까�? ?�장
## 37. 2026-04-24 관리자 ?�속 ?�림 ?�달 ???�동
### 구현

- `FirebaseAdminRepository`가 ???�시 채널??`sent` ?�달 기록??만들 ??`adminActionDeliveryJobs` ??문서�??�께 ?�성?�도�?바꿨?? ?�성/?�오???�림?� ?�에 ?�고, ?�음 ?�인/?�결 ?�료처럼 즉시 종료?�는 기록?� 기존처럼 바로 ?�?�한??
- Firebase ?�달 기록 로딩 ?�에??문서???�?�된 ?�생 ?�태�?그�?�?믿�? ?�고 ?�재 ?�점 기�??�로 `AdminActionDeliveryRecord`�??�시 계산?�게 바꿔, SLA 마감 ?�각??지?�면 ?�에??바로 `조치 ?�요`�?보이?�록 맞췄??
- `functions/index.js`??`deliverAdminActionDeliveryJobs`, `dispatchAdminActionDeliveryJobs`�?추�??�고, `adminActionDeliveryJobs`??`PENDING/FAILED` ?�업???�점???�제 발송 ?�는 ?��??�이??처리????결과�?`adminActionDeliveries`???�시 반영?�도�?구성?�다.
- Functions???�신 관리자 계정??`ADMIN` ??�� ?�용??기�??�로 ?�석?�고, ?�동값이 ?�으�?`SIMULATED`, ?�신???�음?�면 `SKIPPED`, ?�류�?`FAILED`, ?�공?�면 `SENT` ?�업 ?�태�??�긴??
- `firestore.rules`??`appointmentFollowUps`, `supportInquiries`, `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs`, `appointmentReminderJobs` ?�근 규칙??추�???Firebase 관리자 ?�?�소?� ??문서가 ?�제 권한 범위 ?�에???�작?�도�??�리?�다.
- `data-api-draft.md`, `firebase-setup.md`??`adminActionDeliveryJobs` 컬렉?? Functions ?�트�? ?�경 변?? 처리 ?�름??문서?�했??

### 변�?범위

- `data/firebase`: `FirebaseAdminRepository`
- `functions`: `functions/index.js`
- `firebase`: `firestore.rules`
- `docs`: `data-api-draft.md`, `firebase-setup.md`

### ?��? 범위

- ?�제 ?�시 공급???�답 ?�펙??맞춰 `ADMIN_PUSH_ENDPOINT` payload ?�드�?최종 고정
- ?�달 ??결과?� 관리자 ?�션?�터 ?�터�?같�? ?�버 ?�답 모델�????�합
- ?�영???�음 ?�인 주체, SLA 초과 ?�알�? ?�시???�진 ???�동 ?�발???�책??백오?�스 ?�업 문서까�? ?�장

## 38. 2026-04-24 Gradle ?�능/?�정???�정 ?��?
### 구현

- 루트 `gradle.properties`??Gradle ?�몬 JVM 메모리�? `-Xmx4096m`?�로 ?�향?�고 `daemon`, `parallel`, `caching`, `configuration-cache`�?명시??Android Studio + Codex ?�업 ?�경?�서 빌드 ?�사?�성???��???
- 기존 `org.gradle.jvmargs=-Xmx2048m` ?�정?� ??메모�??�정�?충돌?��?�??�거?�고 ?�일 값으�??�리?�다.
- 루트 `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`???�시 ?��???deprecated ?�정, 중복 repository ?�언, 불필???�존?? 과도??컴파???�션 ?��?�??�인?�다.
- repository ?�언?� `settings.gradle.kts`??중앙 관�???곳으�??��??�고 ?�고, `app` 모듈??`compileOptions`??Java 17 ?�일 ?�정�??�용 중이??추�? 간소?��? ?�요?��? ?�음???�인?�다.
- `app/build.gradle.kts`??`buildFeatures.resValues = true`??`defaultConfig.resValue(...)` ?�용 ?�문???�수???��??�다.
- 검증�? `help`, `assembleDebug`, `testDebugUnitTest`�?모두 ?�시 ?�행?�고, `configuration-cache`가 ?�?????�사?�되??것까지 ?�인?�다.

### 변�?범위

- `gradle`: `gradle.properties`
- `docs`: `implementation-status.md`

### ?��? 범위

- Android Studio IDE ??메모�??�정??별도�???�� ?��? ?�다�?Gradle�?별개�?IDE ?�도 ?�향 검??- CI가 ?�다�?CI ?�경?�서??`configuration-cache` ?�사???��?�???�????�인

## 39. 2026-04-24 Gradle 병목 분석 ?��?
### 구현

- 루트/모듈 Gradle ?�크립트?� ?�제 빌드 결과�??�시 ?��???결과, ?�재 ?�로?�트?�는 `kapt`, `annotationProcessor`, `ksp`, `productFlavors`가 ?�고 모듈??`:app` ?�나뿐이???�한 Gradle 병목 ?�보???�노?�이??처리, flavor 조합 ??��, 모듈 �?중복 ?�존??문제???�음???�인?�다.
- `settings.gradle.kts`??repository ?�언?� `pluginManagement`?� `dependencyResolutionManagement` ??블록?�로�??��??�고 ?�고, 모듈�?`repositories {}` 반복 ?�언?� ?�어??repository 중복?�로 ?�한 sync ??��???�다.
- `app/build.gradle.kts`?�는 `debug` ??별도 build type ?�장?�나 flavor가 ?�고, `buildFeatures.resValues = true`�?`defaultConfig.resValue(...)` ?�문???��??�고 ?�어 debug 빌드?�서 불필?�하�?무거???�용???�의 ?�정?� ?�는 ?�태??
- `assembleDebug --profile` 기�? 주요 ?�간?� ?�정 ?�계가 ?�니??Android 기본 ?�스?�에 몰려 ?�었?? ?��??�으�??�게 보인 ??��?� `checkDebugDuplicateClasses`, `checkDebugAarMetadata`, `mergeDebugAssets`, `processDebugNavigationResources`, `mergeDebugNativeLibs`, `mergeDebugResources`, `processDebugResources`?��? `compileDebugKotlin`?� `NO-SOURCE` ?�태�???`0.185s` ?��??�었??
- `help --no-configuration-cache --profile` 기�? ?�정 ?�계???�체 `1.748s`?��? 그중 `:app` 구성 `1.140s`, 루트 ?�로?�트 구성 `0.608s`�?측정?�다. `configuration-cache`�?�??�태?�서??같�? 구성???�사?�되??`help`, `assembleDebug`, `testDebugUnitTest` 모두 `Configuration cache entry reused`�??�인?�다.
- `:app:properties` 기�? ?�제 ?�용값�? `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`, `org.gradle.daemon=true`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `android.nonTransitiveRClass=true`?�??
- AGP 9???�장 Kotlin ?�문??Kotlin ?�스가 ?�어??`compileDebugKotlin`, `compileDebugUnitTestKotlin` ?�스?��? `NO-SOURCE`�??�성?�는 것을 ?�인?�다. ?�는 AGP 9 기본 ?�작?�며, ?�재 ?�로?�트?�서???�능 ?�향???�고 opt-out?� ?�정??리스?��? ?�어 추�? 변경�? ?��? ?�았??

### 변�?범위

- `docs`: `implementation-status.md`

### ?��? 범위

- 빌드 ?�간????문제?�면 Android 기본 ?�스??비중?????�존??묶음(`Firebase Auth + Credentials + Google ID`)�?리소???��? 기능 ?�위�?줄이??방향 검??- AGP 9??`android.enableAppCompileTimeRClass` 같�? 추�? 최적???�래그는 리소???�수 ?�용 방식 ?��? ??별도 브랜치에??검�?
## 40. 2026-04-24 관리자 ?�션?�터/?�달 기록 공용 ?�답 모델 ?�리
### 구현

- `AdminActionOverview`, `AdminActionContract`�?추�????�션?�터?� ?�달 기록 ?�션???�께 참조?�는 공용 ?�약 카운?��? ?�렬 규칙???�메??계약?�로 ?�렸??
- `AdminDashboard`???�제 `actionOverview`�??�께 보유?�고, `MockAdminRepository`, `FirebaseAdminRepository`가 ?�?�보??조합 ??`actionNotifications`, `auditLogs`, `actionDeliveries`?� ?�께 같�? ?�약 ?�답???�성?�도�?맞췄??
- `MockBodeulRepository`, `FirebaseAdminRepository`??관리자 ?�속 처리 목록 ?�렬??`AdminActionContract`�??�용?�도�??�일??목업/?�데?�터 모드 모두 `?�림=priority -> createdAt`, `감사 로그=createdAt`, `?�달 기록=priority -> processedAt` 기�???공유?�게 ?�다.
- `AdminActionCenterCoordinator`, `AdminActionDeliveryCoordinator`?????�상 UI ?�에???�약 카운?��? ?�시 ?��? ?�고, ?�?�보?�의 `actionOverview`�?그�?�??�용???�약 문구?� ?�터 �?개수�??�시?�도�??�리?�다.
- `MockBodeulRepositoryTest`??관리자 ?�?�보?��? `actionOverview`?� 공용 ?�렬 계약???�께 반영?�는 검증을 추�??�고, `assembleDebug`, `testDebugUnitTest`�??�시 ?�과?�다.

### 변�?범위

- `domain/model`: `AdminDashboard`, `AdminActionOverview`, `AdminActionContract`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionCenterCoordinator`, `AdminActionDeliveryCoordinator`, `AdminActivity`
- `docs`: `data-api-draft.md`, `implementation-status.md`
- `test`: `MockBodeulRepositoryTest`

### ?��? 범위

- ?�제 ?�시 공급???�답 ?�펙??맞춰 `ADMIN_PUSH_ENDPOINT` payload ?�드�?최종 고정
- ?�영???�음 ?�인 주체, SLA 초과 ?�알�? ?�시???�진 ???�동 ?�발???�책??백오?�스 ?�업 문서까�? ?�장
## 41. 2026-04-24 Firebase 개발??기�???초기???�차 ?�리
### 구현

- Firestore???�적???�스???�이?��? `merge` 기반 ?�속 문서 ?�존 ?�드�???번에 ?�리?????�도�?[firebase-reset-baseline.md](/D:/BoDeul/docs/firebase-reset-baseline.md)�?추�???초기???�칙, ??�� ?�??컬렉?? ?�시??기�??�을 문서�?고정?�다.
- [reset-firestore-baseline.js](/D:/BoDeul/tools/firebase/reset-firestore-baseline.js)�?추�???`appointmentRequests`, `companionSessions`, `sessionReports`, `appointmentFollowUps`, `supportInquiries`, 관리자 ?�속 처리 컬렉?? `appointmentReminderJobs`까�? 비우�? 기존 Auth UID 기�??�로 `users`, `hospitalGuides`�??�시 만드??개발???�차�??�동?�했??
- ?�크립트???�제 ??�� ?�에 기�????�메??4�?`admin`, `patient`, `guardian`, `manager`)가 `Firebase Authentication`??모두 존재?�는지 ?�인?�고, `--apply`?�서???�락??계정??기�??�으�??�동 ?�성?�도�?구성?�다.
- [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)?�도 기�???초기??문서?� ?�행 ?�크립트 링크�??�결?�다.

### 변�?범위

- `tools/firebase`: `package.json`, `reset-firestore-baseline.js`
- `docs`: `firebase-reset-baseline.md`, `firebase-setup.md`, `implementation-status.md`

### ?��? 범위

- ?�제 Firebase ?�로?�트???�용?�기 ??`dry-run`?�로 ?�재 문서 ?��? ?�락??Auth 계정???�인
- 기�???초기?????�자/보호??매니?�/관리자 로그?�과 ?�약 병원 ?�택, 관리자 가?�드 목록???�제 Firebase 모드?�서 ?��?�?## 42. 2026-04-24 Firebase ?�영 ?�크립트 ?�렉?�리 분리
### 구현

- 개발??기�???초기???�크립트�?배포 코드??`functions/`?�서 분리??[tools/firebase](/D:/BoDeul/tools/firebase) ?�렉?�리�???��??
- `functions/package.json`??붙어 ?�던 기�???초기??npm ?�크립트???�거?�고, ?�영 ?�구 ?�용 [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??추�???`reset:baseline:dry-run`, `reset:baseline:apply`�?별도�??�행?????�게 ?�리?�다.
- ??[reset-firestore-baseline.js](/D:/BoDeul/tools/firebase/reset-firestore-baseline.js)??`firebase-admin`?�나 ADC??기�?지 ?�고, 로컬 `firebase login` ?�큰�?REST API만으�?Auth 조회/기�????�성, Firestore 컬렉??초기?? `users`/`hospitalGuides` ?�시?��? 처리?�도�?바꿨??
- 초기???�드/마이그레?�션 같�? ?�영 ?�구???�으로도 `tools/firebase` ?�래??모으�? `functions/`???�제 배포?�는 백엔??코드�??�기??기�??�로 ?�리?�다.

### 변�?범위

- `tools/firebase`: `package.json`, `reset-firestore-baseline.js`
- `functions`: `package.json`
- `docs`: `firebase-reset-baseline.md`, `firebase-setup.md`, `implementation-status.md`

### ?��? 범위

- `tools/firebase` ?�래??백업/복원, ?�플 ?�이??주입, 컬렉???�태 ?��? ?�크립트까�? 같�? 규칙?�로 ?�리
- ?�영????백오?�스가 분리?�면 ??Functions/?�영 ?�구/관리자 ?�런?�의 경계�??�시 문서??## 43. 2026-04-24 Firebase ?�영 ?�구 ?�장
### 구현

- `tools/firebase/lib` 공용 helper�?추�????�로?�트 ID/?�큰 ?�석, Auth 조회, Firestore 컬렉??조회/??��/?�??로직???�영 ?�크립트?�이 공통?�로 ?�도�??�리?�다.
- [check-firestore-state.js](/D:/BoDeul/tools/firebase/check-firestore-state.js)�?추�???기�???Auth 계정 존재 ?��?, `users` 문서 존재 ?��?, 관�??�??컬렉??문서 ?��? ??번에 ?��??????�게 ?�다.
- [backup-firestore-state.js](/D:/BoDeul/tools/firebase/backup-firestore-state.js)�?추�???관�??�??컬렉?�을 JSON 백업 ?�일�??�?�하?�록 ?�고, 백업 ?�일?� `tools/firebase/backups/` ?�래???�이?�록 ?�리?�다.
- [restore-firestore-state.js](/D:/BoDeul/tools/firebase/restore-firestore-state.js)�?추�???백업 ?�일 기�? dry-run / ?�제 복원???�눠 ?�행?????�게 ?�다. 복원?� Firestore 문서�??�?�으�??�고 Auth 계정?� ?��??�다.
- [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md)�?추�???`check/reset/backup/restore` ?�용법과 ?�렉?�리 ?�영 기�???문서?�했??

### 변�?범위

- `tools/firebase`: `package.json`, `check-firestore-state.js`, `backup-firestore-state.js`, `restore-firestore-state.js`, `reset-firestore-baseline.js`, `backups/.gitkeep`
- `tools/firebase/lib`: `baseline-config.js`, `firebase-toolkit.js`
- `docs`: `firebase-setup.md`, `firebase-operations-tools.md`, `implementation-status.md`
- 루트 ?�정: `.gitignore`

### ?��? 범위

- `tools/firebase`???�플 ?�약/?�션/?�속 처리 ?�름???�는 ?�이??주입 ?�크립트 추�?
- 백업 ?�일 검증용 ?�크립트?� 컬렉??diff ?�구 추�?

## 44. 2026-04-24 Firebase ?�플 ?�비???�이??주입 ?�크립트 추�?
### 구현

- [seed-sample-service-data.js](/D:/BoDeul/tools/firebase/seed-sample-service-data.js)�?추�???기�???Auth / `users` 문서가 준비된 ?�태?�서 ?�약 ?��? 진행 �??�행, 종료 ?�속 처리 3�??�나리오�???번에 Firestore??주입?????�게 ?�다.
- ?�플 ?�이?�는 `appointmentRequests`, `companionSessions`, `sessionReports`, `appointmentFollowUps`, `supportInquiries`, `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs`, `appointmentReminderJobs`�?고정 ID�?upsert?�도�?구성??반복 ?�행 ??중복 문서가 ?�어?��? ?�게 ?�다.
- ?�청 문서?�는 ?�약 ?�장 ?�드(`appointmentAtEpochMillis`, `appointmentDateKey`, 결제/?�션/?�결 ?�용???�보)�??�께 ?�고, ?�료 ?�나리오?�는 ?�기/?�산/SOS ?�속 기록�?관리자 ?�속 ?�림/?�달 기록, ?�시 ???�업까�? 같이 ?�성?�도�?맞췄??
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`seed:sample:dry-run`, `seed:sample:apply` ?�행?�을 추�??�고, [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)???�용 ?�차�?문서?�했??
- 검증�? `npm run seed:sample:dry-run`, `npm run seed:sample:apply`, `npm run check:state`, `.\gradlew.bat assembleDebug --console=plain` ?�서�??�시 ?�인?�다.

### 변�?범위

- `tools/firebase`: `package.json`, `seed-sample-service-data.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### ?��? 범위

- 백업 ?�일 검증용 ?�크립트?� 컬렉??diff ?�구 추�?
- ?�플 ?�이?��? ??���??�면 진입 기�??�로 ?�냅??검증하??체크리스???�는 ?�동 ?��? ?�크립트 추�?

## 45. 2026-04-24 Firebase 백업 검�?/ ?�태 diff ?�구 추�?
### 구현

- [validate-firestore-backup.js](/D:/BoDeul/tools/firebase/validate-firestore-backup.js)�?추�???백업 ?�일??`schemaVersion`, `collections`, 문서 `path`/`id`/`fields` 구조�?검?�하�? 관�??�??컬렉???�락?�나 ?�못??경로, 중복 path�??�류/경고�??�려주도�??�다.
- [diff-firestore-state.js](/D:/BoDeul/tools/firebase/diff-firestore-state.js)�?추�???백업 ?�일�??�재 Firestore ?�태�?비교?�고, 컬렉?�별 추�?/??��/변�?문서�??�약?????�게 ?�다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`validate:backup`, `diff:state` ?�행?�을 추�??�고, [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)???�용 방법??반영?�다.
- 검증�? `node --check`�??�크립트 문법???�인????`npm run validate:backup -- --file ...`, `npm run diff:state -- --file ...`, `.\gradlew.bat assembleDebug --console=plain`�??�시 ?�인?�다.

### 변�?범위

- `tools/firebase`: `package.json`, `validate-firestore-backup.js`, `diff-firestore-state.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### ?��? 범위

- ?�플 ?�이?��? ??���??�면 진입 기�??�로 ?�냅??검증하??체크리스???�는 ?�동 ?��? ?�크립트 추�?
- ?�영 ?�구 결과�???번에 보는 간단??HTML/CLI 리포??묶음 검??
## 46. 2026-04-24 Firebase ??���??�면 진입 ?��? / ?�영 리포??추�?
### 구현

- [check-role-screen-readiness.js](/D:/BoDeul/tools/firebase/check-role-screen-readiness.js)�?추�????�자/보호??매니?�/관리자 기�???계정???�재 Firebase ?�플 ?�이?�만?�로 ?�제 ?�면 진입???�요??컬렉?�을 갖췄?��? ?�동 ?��??????�게 ?�다.
- ?��? 기�??� ?�재 Firebase ?�?�소 코드가 ?�는 조합??맞춰 ?�았�? `?�약 ?��?, `진행 �??�행`, `종료 ?�속 처리` ?�플 ?�나리오가 ?�청/?�션/리포???�속 처리/관리자 ?�달 기록까�? ?�결?�는지???�께 ?�인?�도�?구성?�다.
- [generate-operations-report.js](/D:/BoDeul/tools/firebase/generate-operations-report.js)?� [operations-report.js](/D:/BoDeul/tools/firebase/lib/operations-report.js)�?추�????�재 ?�태, ??���??��? 결과, 기�???계정 ?�태, 컬렉??문서 ?? 백업 ?��?diff�???번에 ?��? HTML ?�영 리포?��? ?�성?????�게 ?�다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`check:readiness`, `report:ops` ?�행?�을 추�??�고, [docs/firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [docs/firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)???�용 방법??반영?�다.
- ?�성 리포?�는 `tools/firebase/reports/` ?�래???�?�하�? [.gitignore](/D:/BoDeul/.gitignore)??HTML 결과물을 Git 추적 ?�?�에???�외?�도�??�리?�다.
- 검증�? `npm run check:readiness`, `npm run report:ops -- --file backups/firestore-backup-20260424-015754.json`, `.\gradlew.bat assembleDebug --console=plain` ?�서�??�시 ?�인?�다.

### 변�?범위

- `tools/firebase`: `package.json`, `check-role-screen-readiness.js`, `generate-operations-report.js`, `reports/.gitkeep`
- `tools/firebase/lib`: `operations-report.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 ?�정: `.gitignore`

### ?��? 범위

- ?�영 리포?�에 ?�크린샷 ?�는 ?�제 ???�비게이??결과�??�결?�는 ?�계 검??- Firebase ?�영 ?�구�?묶어 ?�행?�는 ?�일 ?�크?�로 ?�크립트 ?�는 체크리스???�리

## 47. 2026-04-24 Firebase ?�영 ?�크?�로 ?�크립트 추�?
### 구현

- [backup-validator.js](/D:/BoDeul/tools/firebase/lib/backup-validator.js)�?백업 검�?로직??공용 helper�?분리?�고, [validate-firestore-backup.js](/D:/BoDeul/tools/firebase/validate-firestore-backup.js)??같�? 로직???�사?�하?�록 ?�리?�다.
- [run-operations-workflow.js](/D:/BoDeul/tools/firebase/run-operations-workflow.js)�?추�????�재 Firebase ?�태 ?�집, ??���??�면 진입 ?��?, 백업 검�? diff 계산, HTML 리포???�성, JSON ?�약 ?�?�을 ??번에 ?�행?????�게 ?�다.
- ?�크?�로??[firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js:9)?�서 `firebase login` ?�???�큰??만료?�면 ?�동?�로 refresh token?�로 갱신?�도�?보강?????�행?�도�?맞췄?? 그래??Studio ?�시?�이???�간??지???�에???�영 ?�크립트가 ?�시 401�??�기지 ?�게 ?�다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`workflow:ops` ?�행?�을 추�??�고, [docs/firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [docs/firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)??`--strict`, `--json` ?�함 ?�용 ?�차�?반영?�다.
- ?�크?�로 ?�출물인 JSON ?�약??`tools/firebase/reports/` ?�래???�?�하�?[.gitignore](/D:/BoDeul/.gitignore)??HTML/JSON ?�출물을 Git 추적 ?�?�에???�외?�도�??�리?�다.
- 검증�? `npm run validate:backup -- --file backups/firestore-backup-20260424-015754.json`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json`, `.\gradlew.bat assembleDebug --console=plain` ?�서�??�시 ?�인?�다.

### 변�?범위

- `tools/firebase`: `package.json`, `validate-firestore-backup.js`, `run-operations-workflow.js`
- `tools/firebase/lib`: `backup-validator.js`, `firebase-toolkit.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 ?�정: `.gitignore`

### ?��? 범위

- ?�영 리포?�에 ?�크린샷 ?�는 ?�제 ???�비게이??결과�??�결?�는 ?�계 검??- ?�영 ?�크?�로 결과�?CI??배포 ???��? 루틴�??�결?��? 결정

## 48. 2026-04-24 로컬 ?�리?�라?�트 ?�크립트 추�?
### 구현

- [run-local-preflight.js](/D:/BoDeul/tools/firebase/run-local-preflight.js)�?추�???Firebase ?�영 ?�크?�로, `assembleDebug`, `testDebugUnitTest`�???번에 ?�행?�는 로컬 ?�리?�라?�트 루틴??만들?�다.
- ?�리?�라?�트??중간 ?�계가 ?�패?�도 마�?막까지 ?�행?????�체 ?�태�?계산?�고, ?�크?�로가 ?�성??HTML/JSON ?�출물과 ?�께 별도??Markdown/JSON ?�약 ?�일??`tools/firebase/reports/` ?�래???�기?�록 구성?�다.
- ?�크?�로 ?�계??`workflow:ops`�??��??�서 ?�사?�하�? 백업 ?�일 경로가 주어지�?Firebase ?��? 결과?� Gradle 빌드/?�스??결과�???묶음?�로 기록?�다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`preflight:local` ?�행?�을 추�??�고, [docs/firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [docs/firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)???�용 방법�?`--skip-workflow`, `--skip-build`, `--skip-tests` ?�션??반영?�다.
- ?�리?�라?�트가 ?�성?�는 Markdown ?�약???�영 리포?��? 마찬가지�?[.gitignore](/D:/BoDeul/.gitignore)??Git 추적 ?�?�에???�외?�도�??�리?�다.
- 검증�? `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json` ?�행?�로 ?�료?�고, Firebase ?�영 ?�크?�로(`ready`), `assembleDebug`, `testDebugUnitTest`가 모두 ?�과?�으�??�약 ?�일 [local-preflight-summary-20260424-125837.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-125837.md), [local-preflight-summary-20260424-125837.json](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-125837.json)???�성?�다.

### 변�?범위

- `tools/firebase`: `package.json`, `run-local-preflight.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 ?�정: `.gitignore`

### ?��? 범위

- ?�영 리포?�에 ?�크린샷 ?�는 ?�제 ???�비게이??결과�??�결?�는 ?�계 검??- ?�영 ?�크?�로/?�리?�라?�트 결과�?CI??배포 ???��? 루틴�??�결?��? 결정

## 49. 2026-04-24 ???�면 증적 캡처 �??�영 리포???�동
### 구현

- [capture-app-navigation-evidence.js](/D:/BoDeul/tools/firebase/capture-app-navigation-evidence.js)�?추�????�결???��??�이???�바?�스???�재 ?�면??캡처?�고, `reports/screenshots/` ?�래 PNG?� `app-navigation-evidence-latest.json` 증적 ?�일�??�기?�록 구성?�다.
- 공용 helper [android-toolkit.js](/D:/BoDeul/tools/firebase/lib/android-toolkit.js)?�서 `adb` 경로 ?�색, ?�바?�스 ?�택, ?�재 ?�면 캡처, ?�바?�스 메�??�이???�집??분리?�고, [app-navigation-evidence.js](/D:/BoDeul/tools/firebase/lib/app-navigation-evidence.js)?�서 증적 ?�일 로드/?�규??기본 경로 결정??맡도�??�눴??
- [operations-report.js](/D:/BoDeul/tools/firebase/lib/operations-report.js), [generate-operations-report.js](/D:/BoDeul/tools/firebase/generate-operations-report.js), [run-operations-workflow.js](/D:/BoDeul/tools/firebase/run-operations-workflow.js), [run-local-preflight.js](/D:/BoDeul/tools/firebase/run-local-preflight.js)??`--app-evidence` ?�결??추�??? 증적 ?�일???�으�??�영 리포??HTML�??�크?�로/?�리?�라?�트 ?�약?????�면 ?�션�??�계가 ?�께 반영?�도�??�다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`capture:app` ?�행?�을 추�??�고, [.gitignore](/D:/BoDeul/.gitignore)??`reports/screenshots/*.png`�??�외?�도�??�리?�다.
- 증적 ?�맷 ?�시??[app-navigation-evidence.sample.json](/D:/BoDeul/tools/firebase/templates/app-navigation-evidence.sample.json)???�겨 ?�었??
- 검증�? `node --check tools/firebase/capture-app-navigation-evidence.js`, `node --check tools/firebase/generate-operations-report.js`, `node --check tools/firebase/run-operations-workflow.js`, `node --check tools/firebase/run-local-preflight.js`�?문법???�인?�고, `npm run report:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json`, `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json` ?�행?�로 리포??[firestore-operations-report-20260424-131150.html](/D:/BoDeul/tools/firebase/reports/firestore-operations-report-20260424-131150.html), ?�약 [firestore-operations-summary-20260424-131150.json](/D:/BoDeul/tools/firebase/reports/firestore-operations-summary-20260424-131150.json), ?�리?�라?�트 [local-preflight-summary-20260424-131152.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-131152.md)�??�성?�다. ?�제 `adb` 캡처???�결???�바?�스가 ?�어 ?��?�??�인까�?�??�행?�다.

### 변�?범위

- `tools/firebase`: `capture-app-navigation-evidence.js`, `generate-operations-report.js`, `run-operations-workflow.js`, `run-local-preflight.js`, `package.json`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-evidence.js`, `operations-report.js`
- `tools/firebase/templates`: `app-navigation-evidence.sample.json`
- `tools/firebase/reports/screenshots`: `.gitkeep`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- 루트 ?�정: `.gitignore`

### ?��? 범위

- ?�제 ?��??�이???�바?�스가 ?�결???�태?�서 ??���??�면 ?�동???�디까�? ?�동?�할지 결정
- ?�영 ?�크?�로/?�리?�라?�트 결과�?CI??배포 ???��? 루틴�??�결?��? 결정

## 50. 2026-04-24 CI ?�리?�라?�트 �?GitHub Actions ?�동
### 구현

- [run-ci-preflight.js](/D:/BoDeul/tools/firebase/run-ci-preflight.js)�?추�???CI ?�경?�서 Firebase ?�력??준비되�??�체 ?�리?�라?�트�? 준비되지 ?�았?�면 `--skip-workflow` 모드�?빌드/?�스?�만 ?�행?�도�?분기?�다.
- CI ?�행?��? [run-local-preflight.js](/D:/BoDeul/tools/firebase/run-local-preflight.js)�?그�?�??�사?�하�? `--require-firebase`가 ?�어?�면 `FIREBASE_TOKEN` ?�는 ?�로?�트 ?�별 ?�보가 ?�을 ???�패�?종료?�도�??�다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`preflight:ci` ?�크립트�?추�??�다.
- [.github/workflows/android-preflight.yml](/D:/BoDeul/.github/workflows/android-preflight.yml)??추�???`pull_request`, `workflow_dispatch`?�서 JDK 17/Node 22�??�정????CI ?�리?�라?�트�??�행?�고, `tools/firebase/reports/` ?�출물을 ?�티?�트�??�로?�하?�록 구성?�다.
- ?�크?�로??`secrets.GOOGLE_SERVICES_JSON`, `secrets.FIREBASERC_JSON`, `secrets.FIREBASE_TOKEN`, `vars.FIREBASE_PROJECT_ID`가 ?�으�?Firebase ?�영 ?��?까�? ?�함?�고, ?�으�??�동?�로 Android 빌드/?�스?�만 ?�행?�다.
- ?�용 방법�??�요???�크�??�름?� [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)??반영?�다.
- 검증�? `node --check tools/firebase/run-ci-preflight.js`�?문법???�인?�고, `npm run preflight:ci -- --app-evidence templates/app-navigation-evidence.sample.json` ?�행?�로 Firebase ?�영 ?�크?�로(`ready`), `assembleDebug`, `testDebugUnitTest`가 모두 ?�과?�으�??�출�?[firestore-operations-report-20260424-131815.html](/D:/BoDeul/tools/firebase/reports/firestore-operations-report-20260424-131815.html), [firestore-operations-summary-20260424-131815.json](/D:/BoDeul/tools/firebase/reports/firestore-operations-summary-20260424-131815.json), [local-preflight-summary-20260424-131817.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-131817.md)�??�성?�다.

### 변�?범위

- `tools/firebase`: `run-ci-preflight.js`, `package.json`
- `.github/workflows`: `android-preflight.yml`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### ?��? 범위

- ?�제 ?��??�이???�바?�스가 ?�결???�태?�서 ??���??�면 ?�동???�디까�? ?�동?�할지 결정
- GitHub Actions?�서 Firebase ?�크릿을 ?�제�??�결?????�영 ?�크?�로 ?�함 모드까�? 검�?
## 51. 2026-04-24 debug ?�동 진입 ?�티비티 �??�리??캡처 ?�동
### 구현

- [AutomationEntryActivity.java](/D:/BoDeul/app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java)?� [app/src/debug/AndroidManifest.xml](/D:/BoDeul/app/src/debug/AndroidManifest.xml)??추�???debug 빌드?�서�?`adb`가 직접 ?????�는 ?�동 진입 ?�티비티�?만들?�다.
- ?�동 진입 ?�티비티??`role`, `screen`, `requestId`, `forceSignIn` extra�?받아 기�???계정(`admin@bodeul.app`, `manager@bodeul.app`, `patient@bodeul.app`, `guardian@bodeul.app`)?�로 로그?�한 ???? ?�약 ?�세, ?�속 처리, 보호??리포?? 매니?� ??과거 ?�력/가?�드/문의/???�이지, 관리자 ?�?�보?�로 ?�우?�한??
- [app-navigation-routes.js](/D:/BoDeul/tools/firebase/lib/app-navigation-routes.js)????���??�리?�과 기�? ?�티비티�??�리?�고, [android-toolkit.js](/D:/BoDeul/tools/firebase/lib/android-toolkit.js)??debug ?�동 진입 ?�행�??�커???��?helper�?추�??�다.
- [capture-app-navigation-evidence.js](/D:/BoDeul/tools/firebase/capture-app-navigation-evidence.js)??`--preset` 기반 ?�동 진입, ?�커???�인, ?�태 ?�동 ?�정(`passed`/`failed`)??지?�하?�록 ?�장?�다.
- 문서?�는 ?�리??목록�??�시 명령??반영?�다.
- 검증�? `node --check tools/firebase/capture-app-navigation-evidence.js`, `node --check tools/firebase/lib/android-toolkit.js`, `node --check tools/firebase/lib/app-navigation-routes.js`, `.\gradlew.bat assembleDebug --console=plain`, `node tools/firebase/capture-app-navigation-evidence.js --help`�??�인?�다.

### 변�?범위

- `app/src/debug`: `AndroidManifest.xml`, `java/com/example/bodeul/debug/AutomationEntryActivity.java`
- `tools/firebase`: `capture-app-navigation-evidence.js`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-routes.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### ?��? 범위

- ?�제 ?��??�이???�바?�스 ?�결 ?�태?�서 ?�리?�별 ?�동 진입�?캡처�???번씩 ?�측 검�?- GitHub Actions?�서 Firebase ?�크릿을 ?�제�??�결?????�영 ?�크?�로 ?�함 모드까�? 검�?
## 52. 2026-04-24 ?�기�??�리???�동 진입 �??�면 증적 ?�측
### 구현

- ?�결???�기�?`SM-S921N (Android 16)`??[installDebug](/D:/BoDeul/app/build/outputs/apk/debug/app-debug.apk) 기�? 최신 debug ?�을 ?�시 ?�치?????�리???�체�??�측?�다.
- ?�동 진입 ?�측 과정?�서 `adb shell am start`만으로는 ?�재 ?�스?�에 ?�텐?��? ?�전?�되�??�커??검증이 ?�들리는 문제가 ?�어, [android-toolkit.js](/D:/BoDeul/tools/firebase/lib/android-toolkit.js)?�서 ?�리???�동 진입 ??`-S` 강제 ?�시?�을 붙이?�록 ?�정?�다.
- [app-navigation-routes.js](/D:/BoDeul/tools/firebase/lib/app-navigation-routes.js)??기본 ?��??�간??10초로 ?�렸�? [capture-app-navigation-evidence.js](/D:/BoDeul/tools/firebase/capture-app-navigation-evidence.js)?�서??`com.example.bodeul/.MainActivity`처럼 축약???�티비티 ?�기???�상 비교?�도�??�커???�정??보강?�다.
- ?�리??`patient-home`, `guardian-home`, `patient-booking`, `guardian-booking-status`, `patient-booking-follow-up`, `guardian-report`, `manager-home`, `manager-history`, `manager-guide`, `manager-support`, `manager-profile`, `admin-dashboard`�?모두 ?�행?�고, [app-navigation-evidence-latest.json](/D:/BoDeul/tools/firebase/reports/app-navigation-evidence-latest.json)??`?�과 12 / 경고 0 / ?�패 0`?�로 기록?�다.
- ?�기�?증적??반영???�영 리포??[firestore-operations-report-20260424-133404.html](/D:/BoDeul/tools/firebase/reports/firestore-operations-report-20260424-133404.html), ?�약 [firestore-operations-summary-20260424-133404.json](/D:/BoDeul/tools/firebase/reports/firestore-operations-summary-20260424-133404.json), ?�리?�라?�트 [local-preflight-summary-20260424-133408.md](/D:/BoDeul/tools/firebase/reports/local-preflight-summary-20260424-133408.md)�??�시 ?�성?�다.
- 검증�? `.\gradlew.bat installDebug --console=plain`, ?�리???�체 `node tools/firebase/capture-app-navigation-evidence.js --preset ...`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence reports/app-navigation-evidence-latest.json`, `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json --app-evidence reports/app-navigation-evidence-latest.json` ?�서�??�행?�다.

### 변�?범위

- `tools/firebase`: `capture-app-navigation-evidence.js`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-routes.js`
- `docs`: `firebase-operations-tools.md`, `implementation-status.md`

### ?��? 범위

- GitHub Actions?�서 Firebase ?�크릿을 ?�제�??�결?????�영 ?�크?�로 ?�함 모드까�? 검�?
## 53. 2026-04-24 GitHub Actions Firebase ?�크�?반영 준�?�??�큰 ?�환 보강
### 구현

- GitHub ?�격�?CLI ?�증 ?�태�??��???결과, ?�격?� `git@github.com:bodeul110/Bodeul.git`?�고 SSH ?�는 `bodeul110` 계정?�로 ?�증?��?�? ?�재 `gh` 로그??계정?� `21017053`?�라 `repos/bodeul110/Bodeul` API ?�근??`404`�?막�? ?�는 ?�태�??�인?�다.
- Firebase 공식 문서 기�? `FIREBASE_TOKEN`?� `firebase login:ci`가 발급?�는 refresh token?�데, 기존 [firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js)???��? ?�순 access token처럼 ?�용?�고 ?�었?? ?��? 보강??`FIREBASE_TOKEN`??refresh token?�면 access token?�로 ?�동 교환?�고, 기존 access token ?�력??그�?�??�용?�도�??�정?�다.
- 같�? ?�일??`resolveFirebaseCiToken()`�?`resolveProjectId()` export�?추�??? GitHub ?�크�?반영 ?�크립트가 로컬 Firebase 로그???�태??`.firebaserc` / `app/google-services.json` 값을 그�?�??�사?�할 ???�게 ?�다.
- [github-toolkit.js](/D:/BoDeul/tools/github/lib/github-toolkit.js)�?추�???origin ?�격 ?�석, `gh api` 기반 ?�?�소 ?�근 ?��?, GitHub Actions secret/variable 반영, `workflow_dispatch` ?�행??공용 helper�?분리?�다.
- [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)??`secrets.FIREBASE_TOKEN`, `secrets.GOOGLE_SERVICES_JSON`, `secrets.FIREBASERC_JSON`, `vars.FIREBASE_PROJECT_ID`�???번에 반영?�고, `--dispatch`가 ?�으�?`android-preflight.yml`까�? 바로 ?�행?�도�?구성?�다.
- 문서 [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)?�는 `FIREBASE_TOKEN`??refresh token 기�?�?GitHub CLI 계정 권한 ?�제조건??반영?�다.
- 검증�? `node --check tools/github/configure-actions-firebase.js`, `node --check tools/github/lib/github-toolkit.js`, `node --check tools/firebase/lib/firebase-toolkit.js`, `node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run --skip-access-check`, `node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run`, `npm run preflight:ci -- --app-evidence templates/app-navigation-evidence.sample.json`, `.\gradlew.bat assembleDebug --console=plain`�?진행?�고, ?�제 ?�근 ?��? 모드?�서???�재 `gh` 계정??`21017053`???�?�소 API 권한 부족으�?중단?�는 것을 ?�인?�다.

### 변�?범위

- `tools/firebase/lib`: `firebase-toolkit.js`
- `tools/github`: `configure-actions-firebase.js`
- `tools/github/lib`: `github-toolkit.js`
- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`

### ?��? 범위

- `gh`�?`bodeul110/Bodeul` ?�?�소 권한???�는 계정?�로 ?�시 로그?�한 ??`configure-actions-firebase.js`�??�크�?변??반영
- GitHub Actions?�서 `android-preflight.yml`??`require_firebase_ops=true`�??�제 ?�행???�체 모드 검�?
## 54. 2026-04-24 GitHub Actions ?�크�?반영 ?�료 �??�격 ?�크?�로 부???�인
### 구현

- `gh` 로그??계정??`bodeul110`?�로 ?�시 맞춘 ??`gh api repos/bodeul110/Bodeul`�??�?�소 관리자 권한???�인?�다.
- [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)�??�제 ?�행??GitHub Actions ?�크릿과 변?��? 반영?�다.
  - `secrets.FIREBASE_TOKEN`
  - `secrets.GOOGLE_SERVICES_JSON`
  - `secrets.FIREBASERC_JSON`
  - `vars.FIREBASE_PROJECT_ID=bodeul-dev`
- 검증�? `gh api repos/bodeul110/Bodeul/actions/secrets`, `gh api repos/bodeul110/Bodeul/actions/variables`�??�시 조회???�크�?3개�? 변??1개�? ?�성??것을 ?�인?�다.
- ?�어??`gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master`�??�도?��?�? ?�격 기본 브랜치에 `.github/workflows/android-preflight.yml`???�직 ?�어 `workflow ... not found on the default branch`�?중단?�는 것을 ?�인?�다.
- �? GitHub Actions ?�체 모드??마�?�?차단?��? ?�크릿이 ?�니???�크?�로 ?�일???�직 ?�격 ?�?�소??push?��? ?��? ?�태?�는 ?�이??

### 변�?범위

- `docs`: `firebase-operations-tools.md`, `firebase-setup.md`, `implementation-status.md`
- ?��? ?�태: `bodeul110/Bodeul` ?�?�소 GitHub Actions ?�크�?3�? 변??1�?
### ?��? 범위

- 로컬??`.github/workflows/android-preflight.yml`???�격 기본 브랜치에 반영
- 반영 ??`gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true`�??�제 ?�체 모드 ?�행 검�?
## 55. 2026-04-24 GitHub Actions `app_evidence` 경로 보정
### 구현

- �?번째 GitHub Actions ?�체 모드 ?�행?�서 `CI ?�리?�라?�트 ?�행` ?�계가 `tools/firebase/tools/firebase/templates/app-navigation-evidence.sample.json`�?찾다가 ?�패?�는 것을 ?�인?�다.
- ?�인?� [app-navigation-evidence.js](/D:/BoDeul/tools/firebase/lib/app-navigation-evidence.js)가 `--app-evidence` ?�력???�재 ?�업 ?�렉?�리 기�??�로�??�석?�서, `tools/firebase` ?��??�서 ?�행????repo 루트 기�? 경로�?중복?�로 붙이???�이?�다.
- ?��? ?�정??`--app-evidence`가 ?�어?�면 ?�재 ?�업 ?�렉?�리 기�? 경로?� repo 루트 기�? 경로�?모두 검?�하�? ?�제 존재?�는 ?�일???�선 ?�용?�도�?보정?�다.
- 검증�? `node --check tools/firebase/lib/app-navigation-evidence.js`, `node tools/firebase/run-ci-preflight.js --require-firebase --app-evidence tools/firebase/templates/app-navigation-evidence.sample.json`, `.\gradlew.bat assembleDebug --console=plain`�?진행?�고 모두 ?�과?�다.

### 변�?범위

- `tools/firebase/lib`: `app-navigation-evidence.js`
- `docs`: `firebase-operations-tools.md`, `implementation-status.md`

### ?��? 범위

- 경로 보정 커밋???�격??반영
- GitHub Actions `android-preflight.yml` ?�체 모드�??�실?�해 ?�공 ?��? ?�인

## 56. 2026-04-24 GitHub Actions ?�체 모드 ?��?�??�공
### 구현

- 경로 보정 커밋 `340a109`�??�격 `master`??push????`gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true --field app_evidence_path=tools/firebase/templates/app-navigation-evidence.sample.json`�?GitHub Actions ?�체 모드�??�시 ?�행?�다.
- ?�행 ?��? [24873140407](https://github.com/bodeul110/Bodeul/actions/runs/24873140407)?�며, `preflight` ?�이 `2026-04-24T05:02:31Z`???�작??`2026-04-24T05:07:14Z`???�공?�로 종료??것을 ?�인?�다.
- ???�에??`google-services.json` 복원, `.firebaserc` 복원, `CI ?�리?�라?�트 ?�행`, `?�영 리포???�티?�트 ?�로??까�? 모두 ?�공?�고, Firebase ?�영 ?�크?�로 ?�함 모드가 GitHub Actions?�서???�제�??�작?�는 것을 검증했??
- ?�재 ?��? 경고??GitHub-hosted runner??JavaScript action ?��??�이 Node 20 deprecation 경고�??�우???�뿐?�다. ?�크?�로 ?�패 ?�인?� ?�니�? 추후 `actions/checkout`, `actions/setup-java`, `actions/setup-node`, `actions/upload-artifact`??Node 24 ?�환 버전 ?�책�??�라가�??�다.

### 변�?범위

- `docs`: `firebase-operations-tools.md`, `implementation-status.md`
- ?��? ?�태: GitHub Actions run `24873140407` ?�공

### ?��? 범위

- Node 20 deprecation 경고 ?�???�점??맞춰 GitHub Actions ?��????�책�??��?

## 57. 2026-04-24 ?�중 ?�업???�업 규칙 문서??### 구현

- ?�러 ?�업?��? ?�시???�어?�??충돌??줄일 ???�도�?[collaboration-rules.md](/D:/BoDeul/docs/collaboration-rules.md)�??�로 추�??�다.
- 문서?�는 ?�작 ???�인 ?�서, 충돌 ?�험?????�일, ?�당 범위 권장?? `implementation-status.md` 갱신 규칙, Firebase ?�영 ?�업 ?�일 ?�당 ?�칙, 종료 ??체크리스?��? ?�리?�다.
- [README.md](/D:/BoDeul/README.md) 문서 목록�??�업 ?�정 ?�션?�도 ?�업 규칙 문서 링크�?추�???처음 ?�어?�는 ?�람??바로 찾을 ???�게 ?�다.

### 변�?범위

- `docs`: `collaboration-rules.md`, `implementation-status.md`
- 루트 문서: `README.md`

### ?��? 범위

- ?� ???�제 ?�당 방식??맞춰 ??�� 구분?�나 브랜�?규칙???�요 ????구체??
## 58. 2026-04-24 ?�업 규칙???�업 ???�인 ?�차 구체??### 구현

- [collaboration-rules.md](/D:/BoDeul/docs/collaboration-rules.md)??`?��? 최근???�업?�는지`, `로컬�??�격 �??�느 쪽이 최신?��?`, `?�전?�게 pull --rebase ?�는 방법`??구체?�인 명령�??�별 기�?까�? ?�함??추�??�다.
- `git log --format="%h %an %ad %s" --date=short -10`, `git rev-list --left-right --count HEAD...origin/master`, `git diff --stat HEAD..origin/master`, `git stash push -u` 같�? ?�사??명령??그�?�??�어 처음 보는 ?�업?�도 바로 ?�라 ?????�게 ?�리?�다.
- [README.md](/D:/BoDeul/README.md) ?�업 ?�차?�도 ?�작 ?�에 최근 ?�업?��? 로컬/?�격 최신 ?��?�?먼�? ?�인?�라???�내?� ?�심 명령??추�??�다.

### 변�?범위

- `docs`: `collaboration-rules.md`, `implementation-status.md`
- 루트 문서: `README.md`

### ?��? 범위

- ?�?�서 ?�제 ?�용?�는 공유 채널 기�??�로 `?�재 ?�업 ?�언` ?�치�??�요 ??문서??추�?

## 59. 2026-04-25 README 관리자 ?�모 계정 ?�기 보완
### 구현

- [README.md](/D:/BoDeul/README.md)??`?�모 로그?? ?�션??빠져 ?�던 관리자 계정 `admin@bodeul.app / bodeul1234`�?추�??�다.
- 기존 기�???문서?�는 관리자 계정???�었지�? ?�?�소 �?진입 문서??README?�는 ?�락???�어 ?�?�이 바로 ?�인?????�게 맞췄??

### 변�?범위

- 루트 문서: `README.md`
- `docs`: `implementation-status.md`

### ?��? 범위

- ?�음

## 60. 2026-04-25 관리자 로그????�� ?�택 경로 보완
### 구현

- ?�른 ?�?�이 관리자 계정 로그????`?�택???�용???�형�?계정 ?�형???�치?��? ?�습?�다.` ?�류�??�현?�고, ?�인??로그??검증이 ?�니???�증 UI??관리자 ??�� ?�택 경로가 ?�던 ?�임???�인?�다.
- [RoleSelectionActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionActivity.java)?� [activity_role_selection.xml](/D:/BoDeul/app/src/main/res/layout/activity_role_selection.xml)??관리자 카드?� ?�택 ?�태 바인?�을 추�???관리자????�� ?�트�?`ADMIN`?�로 ?�길 ???�게 ?�리?�다.
- [LoginActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java)?�서 관리자 ??��??고정 로그????���?처리?�고, 관리자 진입?�서???�원가???�환�??�셜 로그??버튼???�기�??�메??로그?�만 ?�용?�도�?보완?�다.
- [activity_login.xml](/D:/BoDeul/app/src/main/res/layout/activity_login.xml), [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml)??관리자 로그???�용 문구?� 관리자 ??��??�?ID�?추�??�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/ui/auth`: `RoleSelectionActivity.java`, `LoginActivity.java`
- `app/src/main/res/layout`: `activity_role_selection.xml`, `activity_login.xml`
- `app/src/main/res/values`: `strings.xml`
- `docs`: `implementation-status.md`

### ?��? 범위

- ?�재 ?�업 ?�경?�는 ?�결??`adb` ?�바?�스가 ?�어 ?�제 ?�말 ?�동 로그?�까지???�번 ?�에???��?증하지 못했??
- ?�?��? 최신 `master`�??�겨??관리자 카드 ?�택 ??`admin@bodeul.app / bodeul1234`�??�시 ?�인?�면 ?�다.

## 61. 2026-04-25 관리자 로그???��? 진입 ?�환
### 구현

- 공개 ??�� ?�택 ?�면???�출?�던 관리자 카드???�반 ?�용??관?�에??불필?�하�?관리자 진입 경로�??�러?��?�??�거?�다.
- [RoleSelectionActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionActivity.java)?� [activity_role_selection.xml](/D:/BoDeul/app/src/main/res/layout/activity_role_selection.xml)???�리????�� ?�택 ?�면?�는 ?�시 `매니?�`, `?�자/보호?? 카드�??�겼??
- ?�????�� ?�택 ?�면 ?�단 로고�?`1.5�??�에 5?????�면 관리자 로그???�면?�로 ?�동?�는 ?��? 진입??추�??�다.
- 관리자 로그???�면 ?�체??계속 [LoginActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java)??`ADMIN` 고정 모드�??�용?�며, ?�메??로그?�만 ?�용?�고 ?�원가???�셜 로그???�출?� 막아 ?�반 ?�용???�로?��? 분리?�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/ui/auth`: `RoleSelectionActivity.java`
- `app/src/main/res/layout`: `activity_role_selection.xml`
- `app/src/main/res/values`: `strings.xml`
- `docs`: `implementation-status.md`

### ?��? 범위

- ?�제 ?�말?�서????�� ?�택 ?�면 로고�?5????�� 관리자 로그?�으�??�어가???�작�???�????�러???�인?�면 ?�다.
- 관리자 권한 ?�체??보안 ?�단?� ?��? 진입???�니??기존 `Auth + users/{uid}.role == ADMIN + Firebase 권한 규칙`??계속 ?�당?�다.

## 62. 2026-05-04 관리자 ???�증/?�사 계약 ?�리
### 구현

- `admin-web` 브랜치의 관리자 ?�이 `localStorage` ?�래그만?�로 로그???�태�??��??�고 ?�제 Firebase ?�션??종료?��? ?�던 문제�??�리?�다.
- [admin-web/firebase.ts](/D:/BoDeul/admin-web/firebase.ts)?�서 `auth` ?�스?�스�??�께 ?�보?�고, [admin-web/src/App.tsx](/D:/BoDeul/admin-web/src/App.tsx)??`onAuthStateChanged`�??�제 관리자 ?�션??검증하?�록 바꿨??
- 로그???�에??`users/{uid}.role == ADMIN`???�시 ?�인?�고, 관리자가 ?�니�?즉시 `signOut()` 처리?�도�?보강?�다.
- 로그?�웃??`localStorage` ?�???�제 Firebase Auth `signOut()`???�출?�도�??�정?�다.
- 매니?� ?�인/반려 ?�?��? 기존 ??계약??맞춰 `managerDocumentStatus`, `managerDocumentReviewNote`, `managerDocumentReviewedAt`, `managerDocumentReviewedByName`, `managerDocumentHistory`�??�께 ?�?�하?�록 맞췄??
- ?�직 Firebase Storage가 ?�결?��? ?�았?��?�? 관리자 ?�에?�는 ?�류 ?�본 미리보기 ?�??`?�출 ?�약 + 체크리스?? 기�? ?�사?�을 명시?�다.

### 변�?범위

- `admin-web`: `firebase.ts`, `src/App.tsx`
- `docs`: `implementation-status.md`

### ?��? 범위

- Firebase Storage ?�결 ???�류 ?�본 미리보기?� 체크리스?��? ?�제 ?�로???�일 기�??�로 묶어???�다.
- ??번들 ?�기가 `500kB` 경고�??�기므�? 관리자 ?�을 ?�제 배포 ?�계�?가?�갈 ?�는 코드 ?�플리팅??검?�해???�다.

## 63. 2026-05-04 관리자 ???�류 Storage 미리보기 ?�동
### 구현

- [admin-web/firebase.ts](/D:/BoDeul/admin-web/firebase.ts)??`storage` ?�스?�스�?추�??�고, [admin-web/src/App.tsx](/D:/BoDeul/admin-web/src/App.tsx)??매니?� ?�사 모달?�서 `Storage` ?�본??직접 ?�어 미리보기 ?�도�??�장?�다.
- 관리자 ?��? `users/{uid}.managerDocumentFiles` 메�??�이?��? ?�으�??�당 `fullPath`�??�선 ?�용?�고, ?�으�?`manager-documents/{managerUserId}/{documentKey}/?�일�? ?�더 규약??기�??�로 최신 ?�일???�색?�다.
- ?��?지 ?�일?� ?�라??미리보기, PDF??`iframe` 미리보기, �????�식?� `?�본 ?�기` 링크�??�리???�영?��? ?�류 ?�본??바로 검?�할 ???�게 맞췄??
- `ManagerApproval`?� ???�상 별도 Firestore 리스?��? 만들지 ?�고, ?�위 `App`??구독??매니?� 목록�??�일 메�??�이?��? 그�?�?받아 ?�용?�도�??�리?�다.
- [storage.rules](/D:/BoDeul/storage.rules), [firebase.json](/D:/BoDeul/firebase.json)??`manager-documents/{managerUserId}/{documentKey}/{fileName}` 경로 규칙??추�??? 관리자 ?�기 / 본인 매니?� ?�기 ?�책???�?�소 ?�정?�로 버전 관리하�?바꿨??

### 변�?범위

- `admin-web`: `firebase.ts`, `src/App.tsx`
- Firebase ?�정: `firebase.json`, `storage.rules`
- `docs`: `implementation-status.md`, `data-api-draft.md`, `firebase-setup.md`

### ?��? 범위

- Android 매니?� ?�에???�직 ?�제 ?�일 ?�로??UI?� `managerDocumentFiles` 메�??�이???�?�이 ?�다. ?�재 관리자 ?��? 메�??�이?��? ?�을 ???�더 규약만으�??�일??찾는??
- `storage.rules`???�?�소 ?�일�??�리�????�태?��?�? ?�제 Firebase ?�로?�트?�는 별도 배포가 ?�요?�다.
- 관리자 ??번들 ?�기 `500kB` 경고??그�?�??�아 ?�어, 배포 ?�계�?가?�갈 ?�는 코드 ?�플리팅??검?�해???�다.

## 64. 2026-05-04 storage.rules ?�제 배포
### 구현

- [storage.rules](/D:/BoDeul/storage.rules)�?Firebase ?�로?�트 `bodeul-dev`???�제 배포?�다.
- `firebase deploy --only storage --project bodeul-dev --non-interactive` 명령?�로 Storage Rules 컴파?�과 릴리?��? ?�인?�다.
- 관리자 ?�이 ?�용?�는 `manager-documents/{managerUserId}/{documentKey}/{fileName}` 경로 규칙???�제 콘솔 ?�정???�니??배포??Storage Rules 기�??�로 ?�용?�다.

### 변�?범위

- Firebase Storage ?�로?�트 ?�정
- `docs`: `implementation-status.md`

### ?��? 범위

- 매니?� ?�에 ?�제 ?�일 ?�로?��? `managerDocumentFiles` 메�??�이???�?�을 붙여??관리자 ?�이 ?�더 ?�색 ?�??명시 경로�??�선 ?�용?????�다.
- ?�요?�면 Storage??기�????�스???�일???�려 관리자 ??미리보기�??�데?�터�???�???검증해???�다.

## 65. 2026-05-04 storage.rules 권한 범위 축소
### 구현

- Firebase Rules API�?`projects/bodeul-dev/releases/firebase.storage/bodeul-dev.firebasestorage.app` 릴리?��? 직접 조회?? ?�격 Storage 규칙??로컬�??�일???�태�?배포???�음??먼�? ?�인?�다.
- [storage.rules](/D:/BoDeul/storage.rules)??`currentUserExists()`, `isManager()`, `isAllowedDocumentKey()`�?추�???권한 범위�?좁혔??
- ?�제 `manager-documents/{managerUserId}/{documentKey}/{fileName}` 경로???�래 조건?�로�??�근?�다.
  - ?�기: 관리자 ?�체 ?�는 본인 매니?�
  - ?�기: 본인 매니?� + ?�용??`documentKey(idCard, license, criminalRecord)`�?가??- ?�정 ??`firebase deploy --only storage --project bodeul-dev --non-interactive`�??�시 배포?�고, Firebase Rules API�???ruleset 반영까�? ?�확?�했??

### 변�?범위

- Firebase ?�정: `storage.rules`
- `docs`: `implementation-status.md`

### ?��? 범위

- Android 매니?� ?�에 ?�제 ?�로?��? 붙일 ?? Storage 경로?� `managerDocumentFiles` 메�??�이???�??규약??같�? 기�??�로 맞춰???�다.
- ?�요?�면 ?�제 매니?� 계정?�로 ?�로??관리자 계정?�로 ?�기까�? 권한 ?�나리오�???�????�측 검증하�??�다.
## 66. 2026-05-04 매니?� ???�본 ?�류 ?�로???�동
### 구현

- [ManagerProfileActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java)?�서 `?�본 ?�일 ?�로?? 버튼�?SAF 문서 ?�택 ?�름??추�??�다.
- ?�로???�?��? `?�분�?, `?�격�?, `범죄경력 조회?? 3종으�??�한?�고, ?�택 가?�한 MIME?� `application/pdf`, `image/*`�?묶었??
- [FirebaseManagerDocumentStorageUploader](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java), [MockManagerDocumentStorageUploader](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java)�?추�???Storage ?�로?��? 목업 메�??�이???�성??분리?�다.
- [FirebaseManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java), [MockManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java), [MockBodeulRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java)??`managerDocumentFiles` 메�??�이???�???�름??추�??�다.
- Firestore ?�???�식?� `managerDocumentFiles.{documentKey}`, `managerDocumentFilePaths.{documentKey}`, ?�거??경로 ?�드(`managerIdCardStoragePath` ??�??�께 갱신?�도�?맞췄??
- 매니?� ???�이지 문서 카드?�는 ?�본 ?�일 ?�약 ?�인??추�??�서 ?�로???��??� 최신 ?�일명을 바로 �????�게 ?�다.
- [MockBodeulRepositoryTest](/D:/BoDeul/app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java)???�로??메�??�이???�?????�사 ?�태 초기?��? ?�일�?반영??검증하???�스?��? 추�??�다.

### 변�?범위

- `app`
  - `data`: `ManagerRepository`, `ManagerDocumentStorageUploader`, `ServiceLocator`, `MockBodeulRepository`
  - `data/firebase`: `FirebaseManagerRepository`, `FirebaseManagerDocumentStorageUploader`, `FirebaseAdminRepository`
  - `data/mock`: `MockManagerRepository`, `MockManagerDocumentStorageUploader`
  - `domain/model`: `ManagerHomeProfile`, `ManagerDocumentFileType`, `ManagerDocumentFileMetadata`
  - `ui/manager`: `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerHomePresentationFormatter`
  - `res`: `activity_manager_profile.xml`, `strings.xml`
  - `test`: `MockBodeulRepositoryTest`
- `docs`: `implementation-status.md`, `data-api-draft.md`, `firebase-setup.md`

### 검�?
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`

### ?��? 범위

- ?�제 매니?� 계정?�로 ?�일 ?�로????관리자 ??미리보기?� 같�? 경로�??�는지 ?�데?�터 ?�나리오�???�????�인?�야 ?�다.
- Storage ?�로???�공 ??Firestore ?�?�이 ?�패?�을 ???�리 ?�책(?�시???�는 고아 ?�일 ?�리)?� ?�직 ?�영 ?�구�??�동?�하지 ?�았??
## 67. 2026-05-04 매니?� ?�류 Storage 감사 ?�구 추�?
### 구현

- [check-manager-document-storage.js](/D:/BoDeul/tools/firebase/check-manager-document-storage.js)�?추�???`users/{uid}.managerDocumentFiles`, `managerDocumentFilePaths`, ?�거??경로 ?�드?� `manager-documents/` ?�제 Storage 객체�?비교?�도�??�다.
- [seed-manager-document-storage-sample.js](/D:/BoDeul/tools/firebase/seed-manager-document-storage-sample.js)�?추�???`manager@bodeul.app` 기�? ?�플 PNG 3종을 ?�로?�하�?같�? 경로�?Firestore 메�??�이?�에 반영?�도�??�다.
- 공용 ?�구 [firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js)??Storage 조회/목록/?�로??API?� Firestore `updateMask.fieldPaths` 기반 부�??�데?�트�?추�??�다.
- ?�플 ?�로??직후 매니?� ?�용??문서 ?��? ?�드가 ?�락?�는 문제가 ?�인?? `patchDocumentFields()`�?부�??�데?�트�?고친 ??`manager@bodeul.app` ?�용??문서??`name/email/phone/role/provider/providerUserId`�?복구?�다.
- ?�제 Firebase 검�?결과 `manager@bodeul.app` 기�? 참조 ?�일 3�? ?�치 객체 3�? ?�락 0�? 경로 불일�?0건으�??�인?�다.

### 변�?범위

- `tools/firebase`
  - `check-manager-document-storage.js`
  - `seed-manager-document-storage-sample.js`
  - `lib/firebase-toolkit.js`
  - `package.json`
- `docs`
  - `implementation-status.md`
  - `firebase-setup.md`
  - `firebase-operations-tools.md`

### 검�?
- `node --check tools/firebase/lib/firebase-toolkit.js`
- `node --check tools/firebase/check-manager-document-storage.js`
- `node --check tools/firebase/seed-manager-document-storage-sample.js`
- `npm run seed:manager-docs:dry-run`
- `npm run seed:manager-docs:apply`
- `npm run check:manager-storage -- --json`

### ?��? 범위

- ?�제 Android 매니?� ?�에???�로?�한 ?�일??관리자 ?�에??직접 ?�어보는 UI ?�나리오???�직 ?�동 ?�인???�아 ?�다.
- 고아 ?�일 ??��???�구 ?�션?�로�??�어?�었�? ?�식 ?�영 ?�차??CI ?�동 ??��로는 ?�직 ?�결?��? ?�았??

## 68. 2026-05-04 ?�버�??�동 ?�로?�로 매니?� ?�본 ?�일 ?�기�?검�?### 구현

- [AutomationEntryActivity](/D:/BoDeul/app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java)??`uploadDocumentType`, `uploadDocumentPath` extra�?추�????�버�??�동 진입?�서 매니?� ?�본 ?�일 ?�로?��? Firestore 메�??�이???�?�까지 같�? ??코드 경로�??�행?????�게 ?�다.
- ?�바?�스 ?�일 경로가 ?�거???�근??막히??경우�??�비해 ?�버�?캐시??1x1 PNG ?�플 ?�일???�성???�로?�하?�록 보강?�다.
- ?�기기에??`MANAGER / MANAGER_PROFILE / idCard` ?�동 ?�로?��? ?�행????매니?� ?�로???�면?�로 복�??�는 것까지 ?�인?�다.
- ?�기�??�면 ?�프 기�? `?�본 ?�일` ??��??`?�분�? automation-idCard.png (2026-05-04 17:10)`?�로 갱신??것을 ?�인?�다.

### 변�?범위

- `app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java`
- `docs/implementation-status.md`

### 검�?
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat installDebug --console=plain`
- `adb shell am start -S -W -n com.example.bodeul/com.example.bodeul.debug.AutomationEntryActivity --es role MANAGER --es screen MANAGER_PROFILE --ez forceSignIn true --es uploadDocumentType idCard --es uploadDocumentPath /no-such-file.png`
- `npm run check:manager-storage -- --json`

### ?��? 범위

- 관리자 ?�에??같�? 매니?� 계정???�본 ?�일 미리보기가 `automation-idCard.png` 기�??�로 ?�리?��? ?�동 ?�인???�아 ?�다.

## 69. 2026-05-04 관리자 ???�인/미리보기 ?�정??### 구현

- [App.tsx](/D:/BoDeul/admin-web/src/App.tsx)?�서 Storage 메�??�이??경로가 ?�긴 경우 ?�더???�른 ?�일�??�동 ?�체하지 ?�고 ?�류 ?�태�?멈추?�록 ?�정?�다.
- 문서 미리보기 로딩??`Promise.allSettled` 기반?�로 바꿔 ?��? 문서 미리보기 ?�패가 ?�체 모달 무한 로딩?�로 ?�어지지 ?�도�?보강?�다.
- 반려 버튼??가�?2?�계 ?�작???�거?�고 즉시 반려 ?�??로직�??�?�록 ?�리?�다.
- 매니?� Firestore 구독???�러 콜백�??�단 ?�류 배너�?추�???권한/?�트?�크 ?�패�??�면?�서 바로 ?�인?????�게 ?�다.
- Firebase Console Storage 링크가 ?�로?�트/버킷 ?�드코딩 문자?�에 ?�존?��? ?�도�?[firebase.ts](/D:/BoDeul/admin-web/firebase.ts) ?�정값을 ?�용?�게 바꿨??

### 변�?범위

- `admin-web/src/App.tsx`
- `admin-web/firebase.ts`
- `docs/implementation-status.md`

### 검�?
- `npm --prefix admin-web run lint`
- `npm --prefix admin-web run build`
- `.\gradlew.bat assembleDebug --console=plain`

### ?��? 범위

- 관리자 ??번들 ?�기 경고(`>500kB`)??그�?�??�아 ?�어, ?�후 코드 ?�플리팅?�나 메뉴 ?�위 lazy loading 검?��? ?�요?�다.

## 70. 2026-05-04 관리자 ??번들 �?�� 분리
### 구현

- [vite.config.ts](/D:/BoDeul/admin-web/vite.config.ts)??`manualChunks`�?추�???`firebase`?� `react` 계열 ?�존?�을 별도 vendor �?���?분리?�다.
- 관리자 ??메인 �?���?줄여 초기 로드 ?�일??가볍게 ?�고, 빌드 ??`500kB` 초과 경고가 ?�시 ?��? ?�도�??�리?�다.

### 변�?범위

- `admin-web/vite.config.ts`
- `docs/implementation-status.md`

### 검�?
- `npm --prefix admin-web run build`
- `.\gradlew.bat assembleDebug --console=plain`

### ?��? 범위

- ?�재??vendor 분리까�? 반영???�태�? ?�후 ?�면 ?��? ???�어?�면 메뉴 ?�위 lazy loading까�? 검?�할 ???�다.

## 71. 2026-05-04 users 공개 검???�거 1�?### 구현

- [FirebaseBookingRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java)?�서 ?�약 ?�결 참여???�색??직접 `users` 쿼리 ?�??Firebase callable `resolveLinkedParticipant`�??�환?�다.
- [FirebaseAuthRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)?�서 ?�셜 로그??�?가????중복 ?�메???�인??callable `findSocialDuplicateEmailProvider`�???��??
- [functions/index.js](/D:/BoDeul/functions/index.js)????callable ?�수�?추�????�약 ?�결 ?�색�??�셜 중복 ?�메???�별??관리자 SDK 쿼리�?중계?�도�?구현?�다.
- [firestore.rules](/D:/BoDeul/firestore.rules)?�서 `users` 컬렉??규칙??`get`/`list`�?분리??비�?리자 ?�라?�언?�의 `users` 목록 조회�?차단?�다.
- 보안 ?�업 ?�리??[firestore-security-hardening.md](/D:/BoDeul/docs/firestore-security-hardening.md)???�어??기록?�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `functions/index.js`
- `firestore.rules`
- `docs/firestore-security-hardening.md`
- `docs/implementation-status.md`

### 검�?
- `node --check functions/index.js`
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `firebase deploy --only functions:resolveLinkedParticipant,functions:findSocialDuplicateEmailProvider,firestore:rules --project bodeul-dev --non-interactive`
- `guardian@bodeul.app` 기�? `resolveLinkedParticipant` ?�출 ?�공
- `guardian@bodeul.app` 기�? `users` ?�메??쿼리 `PERMISSION_DENIED`
- `guardian@bodeul.app` 기�? `findSocialDuplicateEmailProvider` ?�출 `PERMISSION_DENIED`

### ?��? 범위

- 비�?리자 ?�용?�의 `users/{uid}` 직접 조회???�직 ?�용?��?�? ?�면 ?�구?�항???�리?�면 self/admin/참여 관�?기�??�로 ??�???줄일 ???�다.

## 72. 2026-05-04 ?�이�?로그?????�크�??�거
### 구현

- [app/build.gradle.kts](/D:/BoDeul/app/build.gradle.kts)?�서 `naver_client_secret` 리소??주입???�거?�다.
- ?�이�?로그?��? ?�버 중계 ?�로?��? 준비될 ?�까지 비활?�화?�도�?`naver_login_enabled=false` 빌드 리소?��? 추�??�다.
- [BodeulApplication.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/BodeulApplication.java)?�서 ?�이�?SDK 초기?��? ?�거?�다.
- [FirebaseAuthRepository.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)?�서 ?�이�?로그??가???��? ?�단??`R.bool.naver_login_enabled` 기�??�로 바꾸�? 비활?�화 ?�태 ?�내 메시지�??�리?�다.
- [LoginActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java)?�서 ?�이�?로그??버튼???�기�? 코드 경로�??�출?�더?�도 ?�내 ?�스?�만 ?�시?�도�?막았??

### 변�?범위

- `app/build.gradle.kts`
- `app/src/main/java/com/example/bodeul/BodeulApplication.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### 검�?
- `.\gradlew.bat assembleDebug --console=plain`

### ?��? 범위

- ?�이�?로그?��? ?�재 ?�에??비활?�화???�태?? ?�시 ?�려�??�라?�언???�크릿을 ?�에 ?��? ?�는 ?�버 중계??OAuth ?�름??별도�??�계?�야 ?�다.

## 73. 2026-05-04 users 직접 조회 self/admin ?�한
### 구현

- [FirebaseBookingRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java)?�서 ?�약 ?�세???�자/보호???�로?�을 `appointmentRequests` 문서 ?�냅?�으�?복원?�도�?바꾸�? 배정 매니?� ?�보??callable `resolveAssignedManagerProfile`�?가?�오�??�리?�다.
- [FirebaseGuardianReportRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java)?�서??보호??리포?�의 매니?� ?�로?�을 직접 `users/{uid}` ?�기 ?�??callable�??�환?�다.
- [FirebaseManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java)?�서 매니?� ?�?�보?��? 과거 ?�력???�자/보호???�로?�을 ?�청 문서 ?�냅?�으�?구성?�도�?바꿨??
- [firestore.rules](/D:/BoDeul/firestore.rules)?�서 `users` 직접 ?�기 권한??본인�?관리자�??�용?�도�?축소?�다.
- 보안 ?�업 ?�리??[firestore-security-hardening.md](/D:/BoDeul/docs/firestore-security-hardening.md)???�어??기록?�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `firestore.rules`
- `docs/firestore-security-hardening.md`
- `docs/implementation-status.md`

### 검�?
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `firebase deploy --only functions:resolveAssignedManagerProfile,firestore:rules --project bodeul-dev --non-interactive`
- Firebase Web SDK ?�계??검�?  - `guardian@bodeul.app` 기�? `resolveAssignedManagerProfile(request-seed-progress)` ?�출 ?�공
  - `guardian@bodeul.app` 기�? 본인 `users/{uid}` 문서 ?�기 ?�공
  - `guardian@bodeul.app` 기�? 매니?� `users/{uid}` 문서 직접 ?�기 `permission-denied`
  - `patient@bodeul.app` 기�? 보호??`users/{uid}` 문서 직접 ?�기 `permission-denied`

### ?��? 범위

- ?� ?�용???�로?�이 ???�요???�면?� Firestore 규칙???�시 ?�히지 말고, ?�청 문서 ?�냅?�이??Functions 중계�??�?�야 ?�다.

## 74. 2026-05-04 매니?� ?�류 Storage 고아 ?�일 ?�리 ?�름 보강
### 구현

- [check-manager-document-storage.js](/D:/BoDeul/tools/firebase/check-manager-document-storage.js)??고아 ?�일 ?�리??`dry-run -> apply` ?�름??추�??�다.
- `--delete-orphans`만으로는 ??���??�행?��? ?�고, `--apply`가 ?�께 ?�을 ?�만 ?�제 ??���??�행?�도�?바꿨??
- ?�락 객체??경로 불일치�? ?�으�?기본?�으�???���?차단?�고, ?�외 ?�황?�서�?`--force`�??�회?????�게 ?�다.
- ?�????�� 방�?�??�해 기본 최�? ??�� ??20�??�한??추�??�고, `--max-delete`로만 조정?�게 ?�다.
- [tools/firebase/package.json](/D:/BoDeul/tools/firebase/package.json)??`cleanup:manager-storage:dry-run`, `cleanup:manager-storage:apply` ?�행?�을 추�??�다.
- ?�영 ?�차??[firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)??반영?�다.

### 변�?범위

- `tools/firebase/check-manager-document-storage.js`
- `tools/firebase/package.json`
- `docs/firebase-operations-tools.md`
- `docs/firebase-setup.md`
- `docs/implementation-status.md`

### 검�?
- `node tools/firebase/check-manager-document-storage.js --help`
- `npm run check:manager-storage -- --json`
- `npm run cleanup:manager-storage:dry-run`
- `npm run cleanup:manager-storage:apply`

### ?��? 범위

- ?�제 ?�영 기�??�로???�리 ??마�?�?백업 ?�성 ?��??� 리포??보�? 기간�??� 규칙?�로 ?�하�??�다.

## 75. 2026-05-04 관리자 권한 QA 체크리스???�리
### 구현

- [admin-access-qa-checklist.md](/D:/BoDeul/docs/admin-access-qa-checklist.md)�?추�???관리자 ???��? 진입, 관리자 ??로그?? 매니?� ?�류 검?? 권한 ?�패 ?�나리오�???문서?�서 ?��??????�게 ?�리?�다.
- [README.md](/D:/BoDeul/README.md) 문서 목록??관리자 권한 QA 체크리스??링크�?추�??�다.
- [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md)??관리자 권한 검�?기�? 문서 ?�결??추�??�다.

### 변�?범위

- `docs/admin-access-qa-checklist.md`
- `README.md`
- `docs/firebase-operations-tools.md`
- `docs/implementation-status.md`

### 검�?
- 문서 ?�리 ?�업?�라 별도 빌드 ?�이 ?�용�?링크 ?�결�??��??�다.

### ?��? 범위

- ?�???�제 QA�??�리면서 ?�패 ?��?가 ?�이�?`?�패 ??기록 ??��` ?�래??반복?�는 ?�형??추�??�면 ?�다.

## 76. 2026-05-04 보안 리뷰 최신?��? Storage ?�로???�약 강화
### 구현

- [security-review-2026-04-29.md](/D:/BoDeul/docs/security-review-2026-04-29.md)�??�재 코드 기�??�로 ?�면 최신?�했??
- 기존 지???�항??`?�결`, `부�??�결`, `미해�?�??�시 분류?�고, ?��?????/ 관리자 ??/ Firebase ?�영 ?�구 기�? ?��? ?�험???�정리했??
- [storage.rules](/D:/BoDeul/storage.rules)??매니?� ?�류 ?�로???�약??추�??�다.
  - ?�용 MIME: `application/pdf`, `image/*`
  - 최�? ?�기: `10MB`
- [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)??Storage ?�로???�약??문서?�했??

### 변�?범위

- `docs/security-review-2026-04-29.md`
- `storage.rules`
- `docs/firebase-setup.md`
- `docs/implementation-status.md`

### 검�?
- `firebase deploy --only storage --project bodeul-dev --non-interactive`
- `npm --prefix tools/firebase run check:manager-storage -- --strict`

### ?��? 범위

- ?�재 가?????��? ?�험?� `App Check 미도??, `?�문 ?�드 ?�??, `?�영 ?�구 ?�큰 처리`, `권한 최소??검????

## 77. 2026-05-04 AES ?�용 범위 ?�단 ?�리
### 구현

- [aes-scope-assessment.md](/D:/BoDeul/docs/aes-scope-assessment.md)�?추�???`AES-256 ?�상??보안` ?�구�??�재 ?�로?�트 구조 기�??�로 ?�시 ?�석?�다.
- ?�제 코드 기�??�로 로컬 ?�속 ?�??지?�을 ?�시 ?�인?�다.
  - [PermissionGuidePreferences.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuidePreferences.java): 권한 ?�내 ?�료 ?��?�??�??  - [ServiceLocator.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/ServiceLocator.java): Firestore ?�스??캐시 비활?�화
  - [FirebaseManagerDocumentStorageUploader.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java): ?�본 ?�류�?로컬 복사 ?�이 바로 Storage ?�로??  - [admin-web/firebase.ts](/D:/BoDeul/admin-web/firebase.ts), [App.tsx](/D:/BoDeul/admin-web/src/App.tsx): 관리자 ?��? Firebase Auth ?�션�??�용
- 결론?� `지�?릴리??경로?�는 ?�이 직접 ?�속 ?�?�하??민감 비즈?�스 ?�이?��? 거의 ?�으므�? ?�면 AES ?�입보다 로컬 ?�??금�? ?�칙�?App Check가 ?�선`?�라???�으�??�리?�다.
- [security-review-2026-04-29.md](/D:/BoDeul/docs/security-review-2026-04-29.md)??AES ?�용 범위 ?�단 링크�?추�??�다.

### 변�?범위

- `docs/aes-scope-assessment.md`
- `docs/security-review-2026-04-29.md`
- `docs/implementation-status.md`

### 검�?
- 문서 ?�리 ?�업?�라 별도 빌드 ?�이 코드 참조 경로?� ?�단 기�?�?교차 ?�인?�다.

### ?��? 범위

- `?�프?�인 ?�??, `문서 ?�운로드`, `?�동?�?? 기능??추�??�면 ??문서�?기�??�로 `AES-256-GCM + Android Keystore` ?�용 범위�?바로 구체?�해???�다.

## 78. 2026-05-04 App Check 1?�계 ?�용 ?�작
### 구현

- Android ?�에 App Check 초기??경로�?추�??�다.
  - [BodeulApplication.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/BodeulApplication.java)?�서 ?�작 ??App Check�??�치?�다.
  - `debug` 변?��? [app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java](/D:/BoDeul/app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java)?�서 Debug provider�??�용?�다.
  - `release` 변?��? [app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java](/D:/BoDeul/app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java)?�서 Play Integrity provider�??�용?�다.
- [app/build.gradle.kts](/D:/BoDeul/app/build.gradle.kts), [libs.versions.toml](/D:/BoDeul/gradle/libs.versions.toml)??App Check ?�존?�을 추�??�다.
- 관리자 ?�에 ?�택??App Check 초기??경로�?추�??�다.
  - [admin-web/src/appCheck.ts](/D:/BoDeul/admin-web/src/appCheck.ts)
  - [admin-web/src/main.tsx](/D:/BoDeul/admin-web/src/main.tsx)
  - [admin-web/firebase.ts](/D:/BoDeul/admin-web/firebase.ts)
  - `VITE_FIREBASE_APPCHECK_SITE_KEY`가 ?�을 ?�만 reCAPTCHA 기반 App Check�??�성?�하�? 로컬 개발?�서???�버�??�큰???�용?�다.
- [functions/index.js](/D:/BoDeul/functions/index.js)??callable 공통 ?�션 `CALLABLE_FUNCTIONS_OPTIONS`�?추�??�고, `ENABLE_APPCHECK_ENFORCEMENT=true`???�만 `enforceAppCheck`�?켜게 ?�리?�다.
- [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md), [security-review-2026-04-29.md](/D:/BoDeul/docs/security-review-2026-04-29.md)??App Check 1?�계 메모�?반영?�다.

### 변�?범위

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/bodeul/BodeulApplication.java`
- `app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java`
- `app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java`
- `admin-web/firebase.ts`
- `admin-web/src/appCheck.ts`
- `admin-web/src/main.tsx`
- `functions/index.js`
- `docs/firebase-setup.md`
- `docs/security-review-2026-04-29.md`
- `docs/implementation-status.md`

### ?��? 범위

- Firebase Console?�서 Android ??App Check ?�록, ?�버�??�큰 allowlist, 관리자 ?�용 reCAPTCHA ?�이?????�록???�요?�다.
- Firestore / Storage / Functions enforcement???�라?�언???�큰???�정?�된 ???�계?�으�?켜야 ?�다.

## 79. 2026-05-04 Firebase ?�영 ?�구 OAuth secret 분리
### 구현

- [firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js)?�서 refresh token 교환??OAuth client secret ?�드코딩???�거?�다.
- ?�제 Firebase ?�영 ?�구???�래 ?�선?�위�?OAuth client secret???�는??
  - `FIREBASE_OAUTH_CLIENT_SECRET` ?�경 변??  - `local.properties`??`firebaseOauthClientSecret`
- OAuth client id??비�?값이 ?�니므�?기본값을 코드???�고, ?�요?�면 `FIREBASE_OAUTH_CLIENT_ID` ?�는 `local.properties`??`firebaseOauthClientId`�???��?????�게 ?�다.
- [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)??`FIREBASE_TOKEN`??refresh token????`FIREBASE_OAUTH_CLIENT_SECRET`???�께 GitHub Actions secret?�로 반영?�게 바꿨??
- [.github/workflows/android-preflight.yml](/D:/BoDeul/.github/workflows/android-preflight.yml)??`secrets.FIREBASE_OAUTH_CLIENT_SECRET` ?�경 변?��? 추�??�다.
- [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md), [security-review-2026-04-29.md](/D:/BoDeul/docs/security-review-2026-04-29.md)???�영 ?�구 secret 분리 기�???반영?�다.

### 변�?범위

- `tools/firebase/lib/firebase-toolkit.js`
- `tools/github/configure-actions-firebase.js`
- `.github/workflows/android-preflight.yml`
- `docs/firebase-operations-tools.md`
- `docs/firebase-setup.md`
- `docs/security-review-2026-04-29.md`
- `docs/implementation-status.md`

### ?��? 범위

- GitHub Actions ?�?�소 ?�크릿에 `FIREBASE_OAUTH_CLIENT_SECRET` ?�제 값을 ?�어 기존 refresh token 기반 ?�영 ?�크?�로가 계속 ?�작?�도�?맞춰???�다.
- ?�기?�으로는 Firebase CLI refresh token ?�존 ?�체�?줄이거나, ?�비??계정 기반 ?�영 경로�?분리?�는 쪽이 ???�다.

## 80. 2026-05-04 Android 권한 ?�면 최소??### 구현

- [AndroidManifest.xml](/D:/BoDeul/app/src/main/AndroidManifest.xml)?�서 ?�재 기능???�제�??��? ?�는 ?�험 권한???�거?�다.
  - ?�거 ?�?? ?�치, 카메?? ?��? ?�?�소 ?�기, 블루?�스, ?�화, ?�락�?- [PermissionGuideCatalog.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java)�??�재 버전 ?�내 기�??�로 바꿨??
  - ?�스??권한???�제�??�청?��? ?�음
  - ?�버 중심 ?�이??처리, ?�스??문서 ?�택�??�용, 추후 기능 추�? ???�요�??�칙�??�내
- [PermissionGuideActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideActivity.java) 주석�?[strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml) 문구�??�재 구조??맞게 ?�리?�다.
- [security-review-2026-04-29.md](/D:/BoDeul/docs/security-review-2026-04-29.md)??권한 ?�면 ?�슈 최신 ?�태�?반영?�다.

### 변�?범위

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideActivity.java`
- `app/src/main/res/values/strings.xml`
- `docs/security-review-2026-04-29.md`
- `docs/implementation-status.md`

### ?��? 범위

- 추후 ?�제 카메???�치/?�락�?기능??추�??�면 �??�점?�만 권한???�시 ?�언?�고, 기능/문구/검�??�차�??�께 추�??�야 ?�다.

## 81. 2026-05-04 매니?� ?�류 ?�로???�전 검�?보강
### 구현

- [ManagerDocumentUploadPolicy.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java)�?추�???매니?� ?�본 ?�류 ?�로???�에 ?�일 ?�식�??�량??먼�? 검?�하?�록 ?�리?�다.
- [FirebaseManagerDocumentStorageUploader.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java), [MockManagerDocumentStorageUploader.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java)?�서 공통 ?�책???�용??`PDF` ?�는 `image/*`�??�용?�고, `10MB` 초과 ?�일?� ?�로???�에 바로 차단?�다.
- ?�버 규칙?�서 막히�??�에 ?�에??같�? 기�??�로 먼�? ?�내?? 매니?�가 ?�로???�패 ?�유�?바로 ?�해?????�게 맞췄??

### 변�?범위

- `app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java`
- `docs/implementation-status.md`

### ?��? 범위

- ?�재???�일 ?�식�??�량�??�전 검증한?? 추후 ?�류 종류�?추�? ?�약???�요?�면 같�? ?�책 객체???�장?�는 쪽이 맞다.

## 82. 2026-05-04 미사??placeholder 경로 ?�리
### 구현

- ???�상 ?�떤 ?�면?�서???��? ?�는 [FeaturePlaceholderActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/common/FeaturePlaceholderActivity.java)?� ?�용 ?�이?�웃???�거?�다.
- ?�께 ?�아 ?�던 미사???�내 문구 `social_login_pending`, `toast_placeholder`???�리???�재 로그???�외 ?�름�?맞�? ?�는 dead resource�?줄�???
- ?�제 ?�용??경로?�는 ?��? 구체?�인 ?�태 ?�면�??�류 메시지가 ?�어가 ?�으므�? 공용 placeholder�??��????�유가 ?�다�??�단?�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/ui/common/FeaturePlaceholderActivity.java`
- `app/src/main/res/layout/activity_feature_placeholder.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ?��? 범위

- ?�후 ??기능??추�????�는 공용 placeholder�??�회?��? 말고, ?�제 ?�태 ?�널 ?�는 ??���??�류 ?�름 ?�에???�는 쪽이 맞다.

## 83. 2026-05-04 ?�증 ?�외 문구?� 미사??문의 문구 ?�리
### 구현

- [FirebaseAuthRepository.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)?�서 카카???�이�?Firebase Functions 로그???�외�??�문 메시지 그�?�??�출?��? ?�고, 코드 기�? ?�용???�내 문구�??�리?�다.
- Functions가 직접 ?�려주는 `details.message`??계속 ?�선 ?�용?�되, �??�에??`INVALID_ARGUMENT`, `PERMISSION_DENIED`, `UNAUTHENTICATED`, `UNAVAILABLE` 기�??�로 ?�국??문구�?고정?�다.
- ?�이�?SDK ?�청 ?�패???��? `errorDesc`�?그�?�?붙이지 ?�고, 취소/?�트?�크/?�반 ?�패�??�눠 ?�내?�다.
- ???�상 ?��? ?�는 `manager_support_hero_body` 문구�??�거??문의 ?�면 문자?�도 ?�재 구조??맞췄??

### 변�?범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ?��? 범위

- ?�제 ?�영 ???�셜 로그??공급?�별 ?�세 ?�패 ?�유가 ???�요?�면 ?�버 `details.message` ?�전�??�리�? SDK ?�문 메시지�?그�?�??�출?�는 방향?�로???�아가지 ?�는 ?�이 맞다.

## 84. 2026-05-04 ?�이�?로그??deprecated SDK 경로 ?�리
### 구현

- [FirebaseAuthRepository.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)?�서 `NaverIdLoginSDK` ?�용 경로�?`NidOAuth` 기�??�로 교체?�다.
- ?�이�?로그???�청, ?�큰 조회, 로그?�웃 모두 ?�일???�작???��??�면??deprecated SDK ?�퍼 ?�존�??�거?�다.
- ??변경으�?빌드 로그???�던 `FirebaseAuthRepository` 경고 ?�인 ?�나�??�재 SDK 권장 경로�?맞췄??

### 변�?범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `docs/implementation-status.md`

### ?��? 범위

- ?�증 ?�역???�머지 경고???�었�? 추후 SDK 버전 ?�책??바뀌면 ?�이�?로그??경로??같�? 방식?�로 공급??권장 API�??�선 ?�용?�는 쪽이 맞다.

## 85. 2026-05-05 관리자 ??민감?�보 마스?�과 ?�휴 ?�션 종료
### 구현

- [admin-web/src/App.tsx](/D:/BoDeul/admin-web/src/App.tsx)?�서 매니?� ?�인 목록???�메?�과 ?�화번호�?기본 마스???�태�?바꿨??
- ?�세 ?�사 모달?�서??기존처럼 ?�문???��????�제 검???�무??그�?�?가?�하�??�고, 목록 ?�면??기본 ?�출 범위�?줄�???
- 관리자 ?��? 15�??�안 ?�력?�나 ?�크�????�동???�으�??�동?�로 로그?�웃?�도�??�션 ?�?�머�?추�??�다.
- [security-review-2026-04-29.md](/D:/BoDeul/docs/security-review-2026-04-29.md)??관리자 ???�션/마스??최신 ?�태�?반영?�다.

### 변�?범위

- `admin-web/src/App.tsx`
- `docs/security-review-2026-04-29.md`
- `docs/implementation-status.md`

### ?��? 범위

- ?�재 마스?��? 목록 ?�면?�만 ?�용?�다. 추후 관리자 ?�세 ?�면, 리포???�보?�기, 감사 로그까�? 같�? 기�????�장?��? 결정?�면 ?�다.

## 86. 2026-05-05 ?�로?�트 문서 ?�덱?��? ?�래???�명 ?�리
### 구현

- [document-guide.md](/D:/BoDeul/docs/document-guide.md)�?추�???문서 ?�선?�위, ?�작 ?�서, 문서 분류, ?�재 ?�?�소 구성 ?�약????곳에 모았??
- [README.md](/D:/BoDeul/README.md)??문서 목록???�재 구조??맞게 ?�시 ?�리?�고, ?�로 ?�어???�업?��? 먼�? �??�서�?명시?�다.
- [admin-web/README.md](/D:/BoDeul/admin-web/README.md)�??�제 관리자 ??구조?� ?�재 기능 기�??�로 ?�시 ?�성?�다.
- [data-api-draft.md](/D:/BoDeul/docs/data-api-draft.md)?�서 ?��? 구현???�기 ?�?�소 ?�름�?매니?� ?�본 ?�류 ?�로?��? ?�직 미래 계획처럼 ?�어??문장???�재?�으�??�정?�다.
- [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md)???�재 ?�영 ?�구 범위?� ?�작 ?�름??추�???문서 ??��????분명?�게 맞췄??

### 변�?범위

- `docs/document-guide.md`
- `README.md`
- `admin-web/README.md`
- `docs/data-api-draft.md`
- `docs/firebase-operations-tools.md`
- `docs/implementation-status.md`

### ?��? 범위

- 문서 구조???�재 기�??�로 ?�리?�고, ?�후?�는 ??기능??추�?????`implementation-status`?� 관???�세 문서�?같�? ?�에 ?�께 갱신?�는 규칙�??��??�면 ?�다.

## 87. 2026-05-05 최신 ?�자???�퍼?�스 검??메모 ?�리
### 구현

- `design_refs/보들 가?�드.zip`???�본 ?��?지?� 개별 ?�면 PNG�??�재 ?�기�?캡처, 관리자 ??구현�??�조해 [design-reference-review-2026-05-05.md](/D:/BoDeul/docs/archive/design-reference-review-2026-05-05.md)�??�리?�다.
- ??문서?�는 `?�정 명세가 ?�닌 참고 ?�자???�라???�제�??�고, ?�재 구현�?맞는 �? 바로 반영 가치�? ?�는 차이, 지금�? ?��??�는 것이 맞는 차이�?구분???�었??
- [README.md](/D:/BoDeul/README.md) 문서 목록?�도 ?�자??검??메모�?추�????�중???�면 polish ?�업??????바로 찾을 ???�게 맞췄??

### 변�?범위

- `docs/archive/design-reference-review-2026-05-05.md`
- `README.md`
- `docs/implementation-status.md`

### ?��? 범위

- ??검?�는 ?�면 ?�성??보강 ?�선?�위�??�리???�계??
- ?�제 ?�용?� `Firebase ?�동 모드` ?�시 축소, 권한 ?�내 polish, 매니?� ?�류 ?�로??카드 ?�리 같�? ??��부???�차?�으�??�어가�??�다.

## 88. 2026-05-05 ?�용?�·매?��? ?�경 배�? ?��? ?�리
### 구현

- ?�용?��? 매니?� 경로?�서??`Firebase ?�동 모드`/`?�모 ?�이??기반` 배�?�?기본?�로 ?�기�? 관리자 ?�면�??��??�도�?[EnvironmentModeBadgeHelper.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/util/EnvironmentModeBadgeHelper.java)�?추�??�다.
- [MainActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/MainActivity.java), [BookingActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingActivity.java)?�서 ?�단 모드 배�?�?공통 helper 기�??�로 바꿨??
- ?�약 ?�세/?�속, 보호??리포?? 매니?� ??가?�드/?�력/???�이지/문의 ?�면??binder?� coordinator가 �?모드 ?�벨??받으�?배�?�??�동?�로 ?�기?�록 ?�리?�다.
- 관리자 ?�면?� ?��? ?�영 ?�격??강하므�?기존 ?�경 배�?�?그�?�??��??�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/util/EnvironmentModeBadgeHelper.java`
- `app/src/main/java/com/example/bodeul/MainActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpBinder.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHomeCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHomeDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerSupportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerSupportBinder.java`
- `docs/implementation-status.md`

### ?��? 범위

- ?��? ?�스?�에???�경 배�?가 ?�시 ?�요?��?�?관리자 ?�면�?같�? 별도 조건부 ?�출 ?�위치�? 추�??�면 ?�다.
- ?�음 ?�면 polish??권한 ?�내 ?�면�?매니?� ?�류 ?�로??카드 ?�계 ?�리 쪽이 ?�선?�다.

## 89. 2026-05-05 권한 ?�내 ?�면 ?�계 polish
### 구현

- [activity_permission_guide.xml](/D:/BoDeul/app/src/main/res/layout/activity_permission_guide.xml)???�단 ?�약 카드�?추�???`?�요???�점?�만 최소 권한 ?�청` ?�칙??먼�? 보여주도�??�리?�다.
- [item_permission_guide.xml](/D:/BoDeul/app/src/main/res/layout/item_permission_guide.xml)??카드 주제 배�?�?추�???`?�이??보호`, `문서 ?�로??, `추후 ?�장` 구분??바로 보이�?바꿨??
- [PermissionGuideItem.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItem.java), [PermissionGuideCatalog.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java), [PermissionGuideItemBinder.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItemBinder.java)�?배�? ?�스?�까지 ?�루?�록 ?�장?�다.
- [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml)??권한 ?�내 ?�약 카드?� 배�? 문구�?추�??�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItem.java`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItemBinder.java`
- `app/src/main/res/layout/activity_permission_guide.xml`
- `app/src/main/res/layout/item_permission_guide.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ?��? 범위

- 권한 ?�내 ?�면?� ?�재 ?�책�?????맞게 ?�리?�고, ?�후?�는 ?�제 ?�치/카메???�락�?기능???�어????권한 ?�명 카드�?같�? 구조�??�장?�면 ?�다.

## 90. 2026-05-05 매니?� ???�이지 ?�류 ?�로???�계 polish
### 구현

- [activity_manager_profile.xml](/D:/BoDeul/app/src/main/res/layout/activity_manager_profile.xml)???�류 ?�역??`?�로??준�??�내 -> ?�본 ?�일 ?�황 -> ?�약/?�?�라??검??메모` ?�서�??�구?�했??
- ?�로??CTA�??�단 강조 블록?�로 ?�리�? ?�분�??�격�?범죄경력 조회?��? 각각 ?�태 카드�?분리???�재 ?�로???��??� 최근 ?�로???�각??바로 보이�?바꿨??
- [ManagerProfileScreenModel](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileScreenModel.java), [ManagerProfileCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java), [ManagerProfileBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileBinder.java)???�로??강조 문구?� ?�본 ?�일 카드 모델??추�??�다.
- [ManagerDocumentFileCardModel](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardModel.java), [ManagerDocumentFileCardBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardBinder.java), [item_manager_document_file_status.xml](/D:/BoDeul/app/src/main/res/layout/item_manager_document_file_status.xml)�??�일 ?�태 ?�시�?분리?�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardBinder.java`
- `app/src/main/res/layout/activity_manager_profile.xml`
- `app/src/main/res/layout/item_manager_document_file_status.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ?��? 범위

- 매니?� ???�이지??기본 ?�계 ?�리가 ?�났�? ?�후 ?��? polish??카드 간격�?문구 미세 조정 ?�도??
- ?�제 ?�연 ?�에??관리자 ?�에??보이??문서 ?�태?� 매니?� ???�로??카드 문구가 과하�??�긋?��? ?�는지�???�???같이 ?�인?�면 ?�다.

## 91. 2026-05-05 관리자 ???�사 목록�??�세 모달 ?�계 polish
### 구현

- [admin-web/src/App.tsx](/D:/BoDeul/admin-web/src/App.tsx)??매니?� ?�류 ?�인 ?�면 ?�단??`?�체 ?�??, `?�약 ?�출`, `?�본 3�??�료`, `검??메모 ?�음` ?�약 카드�?추�??�다.
- ?�사 목록 ?�는 `매니?� / ?�락�?/ ?�류 ?�약 / ?�본 ?�일 / ?�태 / 관�? 기�??�로 ?�배치하�? �??�에???�본 ?�일 ?�로???��? 최근 보완 메모 ?��?가 바로 보이?�록 ?�리?�다.
- ?�세 모달?� ?�단 ?�약 ?��? 추�????�재 ?�태, ?�본 ?�일 ?? 체크리스??진행, ?�약 ?�출 ?�태�?먼�? 보여주도�?바꿨??
- 문서 ??? ?�일명과 ?�태가 같이 보이??카드???�택 UI�?바꾸�? ?�측 검???�널?� 진행 ?�치?� ?�션 버튼??묶어 ?�크�?중에???�단 기�????��??�게 ?�리?�다.

### 변�?범위

- `admin-web/src/App.tsx`
- `docs/implementation-status.md`

### ?��? 범위

- 관리자 ?��? 기본 ?�사 ?�계 ?�리가 ?�났�? ?�후 ?��? polish???�이�?반응??처리??검???�터 추�? 같�? ?�장 ?�업?�다.
- 지�??�계?�서???�데?�터 기�??�로 목록 길이가 길어졌을 ?�도 ?�캔?�이 ?��??�는지�?추�? QA ?�면 ?�다.
## 92. 2026-05-05 ?��? ?�스??가?�드?� ?�영 문서 ?�결 ?�리
### 구현

- [internal-test-guide.md](/D:/BoDeul/docs/internal-test-guide.md)�?추�???기획/?��? QA가 바로 ?�용???�스??계정, ?��? ?�이?? ??���??�스???�서�???문서???�리?�습?�다.
- ?�플 ?�약 ?�나리오 `request-seed-requested`, `request-seed-progress`, `request-seed-completed`?� 매니?� ?�류 ?�플 ?�태�??��? ?�스??기�??�으�?명시?�습?�다.
- 관리자 ???��? 진입 방식(??�� ?�택 ?�면 로고 1.5�??�에 5????�?관리자 ??로컬 ?�행 주소�??��? ?�스??가?�드???�께 ?�었?�니??
- [README.md](/D:/BoDeul/README.md), [document-guide.md](/D:/BoDeul/docs/document-guide.md), [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md), [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md)???��? ?�스??가?�드 링크?� ?�영 ?�구 ?�행 ?�제 조건??반영?�습?�다.
- `check:state`, `check:readiness`, `preflight:local` 같�? ?�영 명령??`firebaseOauthClientSecret` ?�는 `FIREBASE_OAUTH_CLIENT_SECRET` ?�정???�으�??�행?��? ?�는 ?�을 문서??분리???�었?�니??

### 변�?범위

- `README.md`
- `docs/document-guide.md`
- `docs/firebase-setup.md`
- `docs/firebase-operations-tools.md`
- `docs/implementation-status.md`
- `docs/internal-test-guide.md`

### ?��? 범위

- 기획�??��? ?�스?��? ?�제�??�작?�면 ?�주 ?�온 질문?�나 ?�패 ?��?�?`internal-test-guide.md`??FAQ ?�태�?계속 ?�적?�면 ?�니??
- Firebase ?�영 ?�구�?직접 ?�릴 개발??PC?�는 `local.properties`??`firebaseOauthClientSecret` ?�는 `FIREBASE_OAUTH_CLIENT_SECRET` ?�경 변?��? 별도�?맞춰???�니??

## 93. 2026-05-05 매니?� ?�류 ?�록 간호???�양보호???�격�??�합

### 구현

- ?�류 ?�록 ?�이지?�서 `간호???�격�?�?`?�양보호???�격�????�나??`간호???�양보호???�격�? ??��?�로 ?�합?�다.
- ?�수 ?�류 체크 로직???�정?�여 ???�격�?�??�나�??�로?�해??`검???�청`??가?�하?�록 변경했??
- ?�합????��???�로??버튼 ?�릭 ?? ?�로?�할 ?�격�?종류�??�택?????�는 ?�이?�로그�? 추�??�다.
- 매니?� ?�의 ?�류 ?�약 ?�보?�서???�합????��명으�??�시?�도�?`ManagerHomePresentationFormatter`�??�데?�트?�다.
- `ManagerDocumentRegistrationItemModel`???�드명과 Getter 메서??불일치로 ?�한 컴파???�러�??�정?�다.

### 변�?범위

- `ui/manager`: `ManagerDocumentRegistrationActivity`, `ManagerDocumentRegistrationCoordinator`, `ManagerDocumentRegistrationBinder`, `ManagerDocumentRegistrationItemModel`, `ManagerHomePresentationFormatter`
- `values`: `strings.xml`
- `docs`: `implementation-status.md`

### ?��? 범위

- ?�음

## 94. 2026-05-05 간호???�격�?Storage 규칙�?관리자 ???�동 보정

### 구현

- [storage.rules](/D:/BoDeul/storage.rules)??`healthCertificate` ?��? 추�???간호???�격�??�일???�제 Firebase Storage ?�로???�용 ?�?�에 ?�함?�습?�다.
- [admin-web/src/App.tsx](/D:/BoDeul/admin-web/src/App.tsx)?�서 관리자 ?�의 `?�격�? ?�롯??`license`�??�니??`healthCertificate` 메�??�이?��? Storage ?�더???�께 ?�도�?보정?�습?�다.
- 관리자 ?�의 Storage 콘솔 링크?� 경로 ?�내???�제 메�??�이??경로??`license / healthCertificate` ?��?경로�?기�??�로 보이�?맞췄?�니??
- [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)?� [manager-document-registration-2026-05-05.md](/D:/BoDeul/docs/manager-document-registration-2026-05-05.md)???�동 ?�태�?반영?�습?�다.

### 변�?범위

- `admin-web/src/App.tsx`
- `storage.rules`
- `docs/firebase-setup.md`
- `docs/implementation-status.md`
- `docs/manager-document-registration-2026-05-05.md`

### ?��? 범위

- 관리자 ?��? ?�전??`?�격�????�일 카드�?보여주�?�? 간호???�양보호???�격증을 ?�시???�렸???????�일??모두 ?�출?�는 UI???�직 ?�습?�다.

## 95. 2026-05-05 건강 ?�격�??��?�?매니?� ???�이지 ?�로???�합

### 구현

- [check-manager-document-storage.js](/D:/BoDeul/tools/firebase/check-manager-document-storage.js)?�서 `healthCertificate`�??��? ?�??문서 ?�에 ?�함?? 간호???�격�??�일??고아 ?�일�??�못 분류?��? ?�도�?보정?�다.
- [ManagerProfileActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java)?�서 기존 ???�이지 ?�로??경로�????�류 ?�록 규칙�?맞췄?? `?�격�????�르�?`간호???�격�?�?`?�양보호???�격�?????�???고르�??�고, ?�택 결과???�라 `HEALTH_CERTIFICATE` ?�는 `LICENSE`�??�로?�한??
- [ManagerProfileCoordinator.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java)?�서 ?�본 ?�일 카드????규칙??반영??`간호???�격�? ?�는 `?�양보호???�격�? �??�제 ?�로?�된 ?�일???�선 보여주도�??�리?�다.
- [data-api-draft.md](/D:/BoDeul/docs/data-api-draft.md), [security-review-2026-04-29.md](/D:/BoDeul/docs/security-review-2026-04-29.md)??`healthCertificate`�??�재 ?�영 기�? 문서 ?�로 반영?�다.

### 변�?범위

- `tools/firebase/check-manager-document-storage.js`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java`
- `docs/data-api-draft.md`
- `docs/security-review-2026-04-29.md`
- `docs/implementation-status.md`

### ?��? 범위

- 관리자 ?��? ?�전??`?�격�????�일 ?�롯?�로 보여준?? 간호???�격증과 ?�양보호???�격증을 ?�시??보�??????�을 별도 카드�?분리??보여줄�????�후 ?�영 UX 결정???�요?�다.
- `tools/firebase` ?��? 명령?� 계속 `firebaseOauthClientSecret` ?�는 `FIREBASE_OAUTH_CLIENT_SECRET` ?�정???�어???�계??검증까지 ?�행?????�다.

## 96. 2026-05-22 최신 기능?�명??기�? 문서 ?�정??
### 구현

- `./local/보들_?�랫??기능?�명??pdf`�?기�??�로 문서 ?�선?�위�??�시 맞췄??
- [README.md](/D:/BoDeul/README.md), [document-guide.md](/D:/BoDeul/docs/document-guide.md)??최신 기능?�명?��? 최상??기�??�로 명시?�고 문서 진입 ?�서�??�리?�다.
- [restructure-target-map.md](/D:/BoDeul/docs/restructure-target-map.md)�?최신 기능?�명?�의 20�???�� 기�??�로 ?�시 ?�리?�고, ?�재 구현 ?�태�?`구현 ?�료`, `부�?구현`, `?�속 ?�계`�?구분?�다.
- [mvp-scope.md](/D:/BoDeul/docs/mvp-scope.md)�?최신 기능?�명??기�? MVP 범위?� ?�속 범위�??�시 ?�리?�다.
- [architecture-draft.md](/D:/BoDeul/docs/architecture-draft.md)??Android ?? 관리자 ?? Firebase ?�영 ?�구�??�함???�재 ?�키?�처 경계�?최신 기능 �?기�??�로 ?�시 ?�리?�다.

### 변�?범위

- `README.md`
- `docs/document-guide.md`
- `docs/restructure-target-map.md`
- `docs/mvp-scope.md`
- `docs/architecture-draft.md`
- `docs/implementation-status.md`

### ?��? 범위

- 최신 기능?�명?�의 추�? 메모??AI ?�성 ?�리, OCR 복약 비교, 건강?�보 ?�면, 초과 ?�간 ?�동 ?�산?� ?�직 `?�속 ?�계` 범위??
- 지??API, ?�결?? ?�시�?GPS/채팅, ?�국 ?�행 ?�세 ?�름?� 계속 `부�?구현` ?�태�?관리해???�다.

## 97. 2026-05-22 최신 기능?�명?��? ?�그�??�체 ?�점검

### 구현

- `docs/local/보들_?�랫??기능?�명??pdf`?� ?�시 ?�그�?ZIP??각각 ?�시 ?�인??기�???분리?�다.
- ??문서 [feature-spec-figma-audit-2026-05-22.md](/D:/BoDeul/docs/feature-spec-figma-audit-2026-05-22.md)�?추�???기능?�명?�의 20�???��, 추�? 기획 메모, GPS/지???�구?� ?�그�??�면 보드 범위�??�로 ?�리?�다.
- [README.md](/D:/BoDeul/README.md)?� [document-guide.md](/D:/BoDeul/docs/document-guide.md)?????��? 문서 링크�?추�??? 기능 기�?�??�자??기�????�동?��? ?�게 진입 경로�??�리?�다.

### 변�?범위

- `README.md`
- `docs/document-guide.md`
- `docs/implementation-status.md`
- `docs/feature-spec-figma-audit-2026-05-22.md`

### ?��? 범위

- 기능 기�??� 계속 기능?�명?��? ?�선?�고, ?�자??ZIP?� ?�면 polish 참고본으�??��??�다.
- ?�시�?GPS/지???�심 채팅/?�국 지??건강?�보/AI-OCR 계열?� 문서??계속 `부�?구현 ?�는 ?�속 ?�계` 범위�?관리해???�다.

## 98. 2026-05-22 기능?�명????���?구현 체크리스???�리

### 구현

- ??문서 [feature-spec-gap-checklist-2026-05-22.md](/D:/BoDeul/docs/feature-spec-gap-checklist-2026-05-22.md)�?추�???기능?�명?�의 20�???��??`?�료`, `부�??�료`, `미구??, `?�속 ?�계`�??�시 ?�랐??
- ??체크리스?�는 [restructure-target-map.md](/D:/BoDeul/docs/restructure-target-map.md)??구조 ?�리?� [feature-spec-figma-audit-2026-05-22.md](/D:/BoDeul/docs/feature-spec-figma-audit-2026-05-22.md)???�문 ?�점검 결과�?바탕?�로 ?�성?�다.
- [README.md](/D:/BoDeul/README.md)?� [document-guide.md](/D:/BoDeul/docs/document-guide.md)????체크리스??링크�?추�??? 기능 기�? gap??바로 ?�인?????�게 ?�리?�다.

### 변�?범위

- `README.md`
- `docs/document-guide.md`
- `docs/implementation-status.md`
- `docs/feature-spec-gap-checklist-2026-05-22.md`

### ?��? 범위

- 기능?�명??기�? ?��? ??gap?� `미구???�면`보다 `부�??�료 ??��???�제 ?�동`?�다.
- ?�히 지??API, ?�시�?GPS/채팅, 결제/PG, ?�국 ?�세, ?�산 규칙?� 계속 별도 ?�선?�위�?관리해???�다.

## 99. 2026-05-22 ?�행 가?�드 병원 지??fallback 추�?

### 구현

- [ManagerGuideMapActionModel](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionModel.java), [ManagerGuideMapActionBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionBinder.java), [ManagerGuideMapFallbackLauncher](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java)�?추�????�행 가?�드???��? 지??fallback ?�션???�면 모델�??�처�?분리?�다.
- [ManagerGuideCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java)?�서 병원�? 진료�? 만남 ?�치�?기�??�로 `병원 ?�내??, `만남 ?�치`, `?�근 ?�국` 3�?fallback ?�션??조합?�도�??�장?�다.
- [activity_manager_guide.xml](/D:/BoDeul/app/src/main/res/layout/activity_manager_guide.xml)??`병원 지??fallback` ?�션??추�??�고, [ManagerGuideDashboardBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java)가 ?�적?�로 ?�션 카드�??�더링하?�록 바꿨??
- [ManagerGuideActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java)???�션 ?�릭 ???��? 지???? Google Maps 검?? ?�반 ??검???�서�??�행�??�당?�도�??�리?�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/layout/item_manager_guide_map_action.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ?��? 범위

- ?�재??지??API ?�???��? ??검??기반 fallback�??�공?�다.
- 보호???�약 ?�세???�자 ?�면까�? 같�? fallback???�장?��????�음 ?�계?�서 별도�?결정?�야 ?�다.

## 100. 2026-05-22 ?�국 진행 ?�약�??�료 ?�태 보강

### 구현

- [CompanionSession](/D:/BoDeul/app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java)??`pharmacySummary`, `pharmacyCompleted` ?�드�?추�????�국 ?�계 진행 메모?� ?�료 ?��?�??�션 ?�태�?같이 관리하?�록 ?�장?�다.
- [ManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/ManagerRepository.java), [FirebaseManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java), [MockManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java), [MockBodeulRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java)???�국 진행 ?�???�료 ?��? 경로�?추�??�다.
- [ManagerGuideActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java), [ManagerGuideDashboardBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java), [ManagerGuideCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java), [activity_manager_guide.xml](/D:/BoDeul/app/src/main/res/layout/activity_manager_guide.xml)??`?�국 진행 ?�약` ?�력�?`?�국 ?�계 ?�료` ?��???추�??�다.
- [BookingStatusCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java), [GuardianReportCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java)???�장 ?�국 진행 ?�약�??�계 ?�태�??�께 ?�출?�도�?반영?�다.
- Firebase ?�션 ?�기/?�성 경로([FirebaseBookingRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java), [FirebaseGuardianReportRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java), [FirebaseAdminRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java))??같�? ?�드�??�도�?맞췄??

### 변�?범위

- `app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java`
- `app/src/main/java/com/example/bodeul/data/ManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuidePresentationFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ?��? 범위

- ?�재???�국 진행 ?�태�??�스???�약�??�료 ?��?로만 관리한??
- ?�국 지??API, 복약 ?�력 구조?? ?�수�??�품 목록 OCR?� 계속 ?�속 ?�계 범위??

## 101. 2026-05-22 ?�자 건강?�보 ?�면 추�?

### 구현

- [HealthInfoActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/health/HealthInfoActivity.java), [HealthInfoCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/health/HealthInfoCoordinator.java), [HealthInfoBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/health/HealthInfoBinder.java), [HealthInfoScreenModel](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/health/HealthInfoScreenModel.java)�?추�????�자/보호?��? 최근 ?�는 진행 중인 ?�약 기�? 건강 ?�로?�을 ?�는 ?�용 ?�면??구현?�다.
- [MainActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/MainActivity.java)?�서 ?�자 ?�의 보조 ?�션??`건강?�보` ?�면?�로 ?�결?�고, 보호?�는 기존처럼 리포???�선???��??�다.
- ?�면?� ?�약???��? ?�?�된 `건강 메모`, `복약 ?�보`, `?�동 보조`, `?�행 ?�형`, `?�호 매니?�`, `?�약 ?�결 ?�보`�??�기 ?�용?�로 보여준??
- [AndroidManifest.xml](/D:/BoDeul/app/src/main/AndroidManifest.xml), [activity_health_info.xml](/D:/BoDeul/app/src/main/res/layout/activity_health_info.xml), [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml)??건강?�보 ?�면 진입�??�국??문구�?반영?�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/MainActivity.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoActivity.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoBinder.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoLineItem.java`
- `app/src/main/res/layout/activity_health_info.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `docs/feature-spec-gap-checklist-2026-05-22.md`
- `docs/restructure-target-map.md`
- `docs/implementation-status.md`

### ?��? 범위

- ?�재 건강?�보 ?�면?� 최근 ?�는 진행 중인 ?�약 문서�?기�??�로 ?�는??
- ?�용???�로?�에 ?�립??건강?�보 ?�속 ?�?? 병력/?�레르기 구조?? 건강?�보 ?�집 ?�용 ?�면?� ?�속 범위??

## 102. 2026-05-22 ���� �ļ� ���� ����ȭ

### ����

- [AppointmentFollowUpSettlementStatus](/D:/BoDeul/app/src/main/java/com/example/bodeul/domain/model/AppointmentFollowUpSettlementStatus.java)�� `OVERTIME_REVIEW`, `REFUND_REVIEW`�� �߰��ϰ�, ������ �ļ� ó���� �ʿ����� �Ǵ��ϴ� ���� ��Ģ�� �����ο� �÷ȴ�.
- [BookingFollowUpActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpActivity.java), [BookingFollowUpCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpCoordinator.java), [BookingPresentationFormatter](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingPresentationFormatter.java)�� ������ �Ϸ� �� �ļ� ȭ�鿡�� `���� Ȯ��`, `�ʰ� �ð� ����`, `ȯ��/���� ����`, `��Ÿ ����`�� ���� �����ϵ��� �ٲ��?
- �ļ� ���� ī�忡�� �ֱ� ������ ���� ó�� ������ �޸� �ٽ� ���̵��� �����ߴ�.
- [AdminOperationsCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/admin/AdminOperationsCoordinator.java), [ManagerHistoryCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryCoordinator.java)�� �� �̻� `NEEDS_HELP`�� ���� �ʰ�, ���� �ļ��� �ʿ��� ���?���¸� ���� ������ �ٷ絵�� �����ߴ�.
- ���ڿ� ���ҽ�([strings_follow_up_status_extension.xml](/D:/BoDeul/app/src/main/res/values/strings_follow_up_status_extension.xml), [strings_admin_operation_extension.xml](/D:/BoDeul/app/src/main/res/values/strings_admin_operation_extension.xml), [strings_manager_history_extension.xml](/D:/BoDeul/app/src/main/res/values/strings_manager_history_extension.xml))�� ���� ���� �б⿡ ���� �����߰�, [MockBodeulRepositoryTest](/D:/BoDeul/app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java)�� `�ʰ� �ð� ����` ���� ���� �׽�Ʈ�� �߰��ߴ�.

### ���� ����

- `app/src/main/java/com/example/bodeul/domain/model/AppointmentFollowUpSettlementStatus.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingPresentationFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminOperationsCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryCoordinator.java`
- `app/src/main/res/values/strings_follow_up_status_extension.xml`
- `app/src/main/res/values/strings_admin_operation_extension.xml`
- `app/src/main/res/values/strings_manager_history_extension.xml`
- `app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java`
- `docs/feature-spec-gap-checklist-2026-05-22.md`
- `docs/implementation-status.md`

### ���� ����

- �̹� �ܰ��?`�ļ� ���� ���� ����ȭ`������.
- ���� �ʰ� �ð� �ڵ� ���? PG �߰� ����, ȯ�� ó�� ��ũ�÷δ� ������ �ļ� ������.

## 103. 2026-05-22 �ǽð� ��ġ Ȯ�� ȭ�� �߰�

### ����

- [BookingStatusCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java), [BookingStatusActionType](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActionType.java), [BookingStatusActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActivity.java)�� ������ ���� ���� �� ������ �⺻ ������ `�ǽð� ��ġ ����`�� �ٲ��?
- �� ȭ�� [BookingLiveLocationActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java), [BookingLiveLocationCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java), [BookingLiveLocationBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java), [BookingLiveLocationScreenModel](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java)�� �߰��ߴ�.
- �� ȭ���� ���� `CompanionSession` ���?�����͸� �о� `���� ����`, `���� �ܰ�`, `���� ��ġ �޸�`, `��ȣ�� ���� �޸�`, `���� ���� �޸�`, `����/�౹ �޸�`�� �� ȭ�鿡�� �����ش�.
- ���� API�� ������ ����Ǳ�?�������� [BookingLiveLocationMapFallbackLauncher](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java)�� `���� ���� ��ġ`, `���� ��ġ`, `���� ��ġ`, `�α� �౹` �˻��� �ܺ� ���� ���̳� �������� �ѱ⵵�� �ߴ�.
- ���ҽ��� [activity_booking_live_location.xml](/D:/BoDeul/app/src/main/res/layout/activity_booking_live_location.xml), [item_booking_live_location_map_action.xml](/D:/BoDeul/app/src/main/res/layout/item_booking_live_location_map_action.xml), [AndroidManifest.xml](/D:/BoDeul/app/src/main/AndroidManifest.xml), [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml)�� �ݿ��ߴ�.

### ���� ����

- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActionType.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapActionModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapActionBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java`
- `app/src/main/res/layout/activity_booking_live_location.xml`
- `app/src/main/res/layout/item_booking_live_location_map_action.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `docs/implementation-status.md`

### ���� ����

- �̹� �ܰ��?`�ǽð� ��ġ Ȯ�� ���� ȭ�� + ���� fallback`������.
- ���� GPS ��ǥ �ǽð� ����, �Ƚ� ä��, ���� SDK ���?��Ŀ/���?ǥ�ô� ������ �ļ� ������.
## 104. 2026-05-22 ?? ?? ?? ?? ??
### ??
- [CompanionChatActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/chat/CompanionChatActivity.java), [CompanionChatCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/chat/CompanionChatCoordinator.java), [CompanionChatBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/chat/CompanionChatBinder.java)? ??? ??????????? ?? ?? ?? ??? ? ? ?? ?? ?? ??? ????.
- [CompanionSession](/D:/BoDeul/app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java), [CompanionChatMessage](/D:/BoDeul/app/src/main/java/com/example/bodeul/domain/model/CompanionChatMessage.java), [BookingRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/BookingRepository.java), [ManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/ManagerRepository.java)? ??? ?? ?? ?? ???? ????? ??.
- Firebase/Mock ???? chatMessages ??? ?? ??? ???, ??? ?? ??? ??? ??? ???? ?? [CompanionChatActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/chat/CompanionChatActivity.java)? ??? ? ?? ??? ????.
- [MockBodeulRepositoryTest](/D:/BoDeul/app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java)? ???/??? ?? ?? ???? ????.
### ?? ??
- pp/src/main/java/com/example/bodeul/domain/model/CompanionChatMessage.java
- pp/src/main/java/com/example/bodeul/domain/model/CompanionSession.java
- pp/src/main/java/com/example/bodeul/data/BookingRepository.java
- pp/src/main/java/com/example/bodeul/data/ManagerRepository.java
- pp/src/main/java/com/example/bodeul/data/MockBodeulRepository.java
- pp/src/main/java/com/example/bodeul/data/mock/MockBookingRepository.java
- pp/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java
- pp/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java
- pp/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java
- pp/src/main/java/com/example/bodeul/ui/chat/CompanionChatActivity.java
- pp/src/main/java/com/example/bodeul/ui/chat/CompanionChatCoordinator.java
- pp/src/main/java/com/example/bodeul/ui/chat/CompanionChatBinder.java
- pp/src/main/java/com/example/bodeul/ui/chat/CompanionChatScreenModel.java
- pp/src/main/java/com/example/bodeul/ui/chat/CompanionChatMessageItemModel.java
- pp/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java
- pp/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java
- pp/src/main/res/layout/activity_companion_chat.xml
- pp/src/main/res/layout/item_companion_chat_message.xml
- pp/src/main/res/layout/activity_booking_live_location.xml
- pp/src/main/res/layout/activity_manager_guide.xml
- pp/src/main/res/values/strings.xml
- pp/src/main/AndroidManifest.xml
- pp/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java
- docs/implementation-status.md
### ?? ??
- ?? ??? ?? ?? ??? ?????.
- ?? ?? ?? ??, ?? ??, ??/?? ??, GPS ?? ??? ??? ?? ?? ???.

## 105. 2026-05-22 카카??지??fallback ?�선??
### 구현

- [KakaoMapExternalLauncher](/D:/BoDeul/app/src/main/java/com/example/bodeul/util/KakaoMapExternalLauncher.java)�?추�????��? 지??fallback 검?�을 카카?�맵 ??-> 카카?�맵 모바?�웹 -> 카카??지????링크 -> ?�반 지??검???�서�??�일?�다.
- [ManagerGuideMapFallbackLauncher](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java)?� [BookingLiveLocationMapFallbackLauncher](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java)가 공용 카카??지???�처�??�용?�도�?바꿨??
- [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml)�?[feature-spec-gap-checklist-2026-05-22.md](/D:/BoDeul/docs/feature-spec-gap-checklist-2026-05-22.md)??지??fallback??카카??기�??�라???�을 반영?�다.

### 변�?범위

- pp/src/main/java/com/example/bodeul/util/KakaoMapExternalLauncher.java`r
- pp/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java`r
- pp/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java`r
- pp/src/main/res/values/strings.xml`r
- docs/feature-spec-gap-checklist-2026-05-22.md`r
- docs/implementation-status.md`r

### ?��? 범위

- ?�번 ?�계???��? 지??fallback ?�선?�위�?카카??기�??�로 맞춘 것이??
- ?�제 카카??지??API ?�장 지?? 좌표 기반 마커, ?�시�?GPS ?�트�??�시???�직 ?�속 범위??

## 106. 2026-05-22 매니?� ?�재 ?�치 공유?� 카카??좌표 ?�기 ?�결

### 구현

- [CompanionSession](/D:/BoDeul/app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java)??sharedLatitude, sharedLongitude, sharedLocationUpdatedAtMillis�?추�????�션???�제 ?�치 좌표?� 갱신 ?�각??같이 보�??�도�??�장?�다.
- [ManagerCurrentLocationSharer](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerCurrentLocationSharer.java)�?추�???매니?� 가?�드 ?�면?�서 기기 ?�재 ?�치�???�??�어 ?�치 ?�약�?좌표�??�께 ?�?�하?�록 만들?�다.
- [ManagerGuideActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java), [activity_manager_guide.xml](/D:/BoDeul/app/src/main/res/layout/activity_manager_guide.xml), [ManagerGuideDashboardBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java)???�재 ?�치 공유 버튼�??�치 권한 ?�청 ?�름??추�??�다.
- [FirebaseManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java), [FirebaseBookingRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java), [FirebaseGuardianReportRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java), [FirebaseAdminRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java), [MockBodeulRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java), [MockManagerRepository](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java)가 공유 좌표�??�?�하�??�도�?맞췄??
- [BookingLiveLocationCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java)가 좌표가 ?�을 ??kakaomap://look 링크�??�선 ?�어 보호???�자 ?�면?�서 공유 ?�치�?바로 카카?�맵?�로 보낼 ???�게 ?�다.
- [AndroidManifest.xml](/D:/BoDeul/app/src/main/AndroidManifest.xml)??ACCESS_FINE_LOCATION??추�??�고, [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml), [feature-spec-gap-checklist-2026-05-22.md](/D:/BoDeul/docs/feature-spec-gap-checklist-2026-05-22.md)???�재 ?��???반영?�다.

### 변�?범위

- app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java
- app/src/main/java/com/example/bodeul/data/ManagerRepository.java
- app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java
- app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerCurrentLocationSharer.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java
- app/src/main/res/layout/activity_manager_guide.xml
- app/src/main/res/values/strings.xml
- app/src/main/AndroidManifest.xml
- docs/feature-spec-gap-checklist-2026-05-22.md
- docs/implementation-status.md

### ?��? 범위

- ?�번 ?�계??매니?� 1???�치 공유 + 카카??좌표 ?�기까�???
- ?�속 GPS ?�트�?추적, 좌표 변�??�력, ?�시???�치 ?�림, 카카??지??SDK ?�장 마커 ?�시???�전???�속 범위??
## 107. 2026-05-22 공유 ?�치 ?�각 ?�출�?가?�드 직행 링크 보강

### 구현

- [ManagerGuideCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java)가 공유 ?�치 메모??좌표가 ?�을 ???�재 공유 ?�치 바로 ?�기 카드�?먼�? ?�출?�고, 좌표가 ?�으�?카카?�맵 look 링크�?직접 ?�도�?보강?�다.
- [BookingLiveLocationCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java)??최근 ?�치 공유 ?�각 ?�태 줄을 추�???보호???�자 ?�면?�서 마�?�??�치 공유 ?�각??바로 ?�인?????�게 ?�다.
- [GuardianReportCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java)?�도 같�? ?�각 ?�보�??�출??보호??리포??카드?�서 ?�치 공유 최신?�을 바로 ?�단?????�게 ?�다.
- [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml)??공유 ?�치 직행 카드?� ?�치 공유 ?�각 문구�?추�??�다.

### 변�?범위

- app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java
- app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java
- app/src/main/res/values/strings.xml
- docs/implementation-status.md

### ?��? 범위

- ?�재??최근 1??좌표 공유 + ?�각 ?�출 ?�계??
- ?�속 ?�치 ?�트�? 백그?�운??추적, 카카??지??SDK ?�장 ?�면?� ?�전???�속 범위??
## 108. 2026-05-22 ?�시�??�치 ?�동 ?�로고침 보강

### 구현

- [BookingLiveLocationActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java)?� [activity_booking_live_location.xml](/D:/BoDeul/app/src/main/res/layout/activity_booking_live_location.xml)??`?�재 ?�치 ?�시 불러?�기` 버튼??추�???보호???�자가 ?�시�??�치 ?�인 ?�면?�서 ?�동 ?�조?�할 ???�게 ?�다.
- [BookingLiveLocationBinder](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java), [BookingLiveLocationScreenModel](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java), [BookingLiveLocationCoordinator](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java)�??�장???�로고침 버튼 문구�??�면 모델?�서 같이 관리하?�록 맞췄??
- [GuardianReportActivity](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/report/GuardianReportActivity.java)?� [activity_guardian_report.xml](/D:/BoDeul/app/src/main/res/layout/activity_guardian_report.xml)??`진행 ?�황 ?�시 불러?�기` 버튼??추�???보호??리포?�도 같�? 기�??�로 ?�조?�할 ???�게 ?�다.
- [strings.xml](/D:/BoDeul/app/src/main/res/values/strings.xml)???�치 ?�인/보호??리포???�로고침 문구�?추�??�다.

### 변�?범위

- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java
- app/src/main/java/com/example/bodeul/ui/report/GuardianReportActivity.java
- app/src/main/res/layout/activity_booking_live_location.xml
- app/src/main/res/layout/activity_guardian_report.xml
- app/src/main/res/values/strings.xml
- docs/implementation-status.md

### ?��? 범위

- ?�속 GPS 좌표 ?�트림과 ?�치 변�??�력?� ?�직 ?�다.
- 카카??지??SDK ?�장 지?��? 백그?�운???�치 추적?� ?�속 범위??

## 109. 2026-05-22 최신 ?�자???�트 기�? 문서?� UI ?�선?�위 ?�정�?
### 구현

- `design_refs/local/`???�리??`bodeul_original_resolution_screens.zip`, `bodeul_split_screens/`, `index.csv`, `contact_sheet.png`�?기�??�로 ?�자??참조 구조�??�시 ?�리?�다.

## 117. 2026-06-19 로컬 참조 ?�산 ?�치 ?�리

### 구현

- Git???�리지 ?�는 로컬 ?�용 참조 ?�산 ?�치�??�리?�다.
- 최신 기능?�명??PDF??`docs/local/`�? 최신 ?�자???�본�?분할 ?�면 ?�트??`design_refs/local/`�?모았??
- `.gitignore`??`docs/local/*`, `design_refs/local/*`, 루트 `package-lock.json` ?�외 규칙??추�??�다.
- 관??문서 링크????로컬 기�? 경로??맞게 ?�리?�다.

### 변�?범위

- `.gitignore`
- `README.md`
- `design_refs/README.md`
- `docs/local/README.md`
- `design_refs/local/README.md`
- `docs/architecture-draft.md`
- `docs/document-guide.md`
- `docs/design-reference-review-2026-05-22.md`
- `docs/feature-spec-figma-audit-2026-05-22.md`
- `docs/feature-spec-gap-checklist-2026-05-22.md`
- `docs/infrastructure-overview.md`
- `docs/mvp-scope.md`
- `docs/restructure-target-map.md`
- `docs/planning/README.md`
- `docs/archive/design-reference-review-2026-05-05.md`

### ?��? 범위

- 로컬 참조 ?�산?� ?�치�??�리?�고, ?�?�소?�는 계속 ?�함?��? ?�는??
- ?� 공용?�로 공유?�야 ???�본?� 별도 링크??버전 관�?가?�한 추출�??�책???�한 ??반영?�다.

## 118. 2026-06-19 구형 ?�자??ZIP 추적 ?�거

### 구현

- ???�상 최신 기�??�로 ?��? ?�는 `design_refs/보들 가?�드.zip`???�?�소 추적 ?�?�에???�거?�다.
- 최신 ?�자??기�??� 계속 `design_refs/local/bodeul_original_resolution_screens.zip`�?`design_refs/local/bodeul_split_screens/`�??��??�다.
- `design_refs/README.md`???�재 기�? ?�산�?구형 ZIP 처리 ?�칙??맞게 ?�시 ?�리?�다.

### 변�?범위

- `design_refs/보들 가?�드.zip`
- `design_refs/README.md`
- `docs/implementation-status.md`

### ?��? 범위

- ?�후 ?�자???�본?� `design_refs/local/` ?�래�?추�??�다.
- 구형 ?�산?� archive 문서?�서�??�력?�로 ?�기�? ?�?�소 기�? ?�산 목록?�서???�외?�다.
- [design_refs/README.md](/D:/BoDeul/design_refs/README.md)�?최신 ?�본/분할 ?�면/보조 참조/비사???�일 기�??�로 ?�면 ?�리?�다.
- [design-reference-review-2026-05-22.md](/D:/BoDeul/docs/design-reference-review-2026-05-22.md)�??�로 추�????�증/공통, ?�자 ?? ?�자 진행 ?�면, 매니?� ??가?�드, ?�류 ?�록 기�???UI polish ?�선?�위�??�시 ?�했??
- [feature-spec-figma-audit-2026-05-22.md](/D:/BoDeul/docs/feature-spec-figma-audit-2026-05-22.md)�?최신 분할 ?�면 ?�트 기�??�로 ?�시 ?�성??기능 기�?�??�자??기�???명확??분리?�다.
- [README.md](/D:/BoDeul/README.md), [document-guide.md](/D:/BoDeul/docs/document-guide.md)???�자??문서 링크�?최신 메모 기�??�로 교체?�다.

### 변�?범위

- README.md
- design_refs/README.md
- docs/design-reference-review-2026-05-22.md
- docs/feature-spec-figma-audit-2026-05-22.md
- docs/document-guide.md
- docs/implementation-status.md

### ?��? 범위

- 기능 ?�선?�위 ?�체??바꾸지 ?�았??
- ?�음 UI polish??`?�증/공통 -> ?�자/보호????진행 -> 매니?� ??가?�드 -> ?�류/???�이지 -> ?�정` ?�서�?진행?�다.
- GPS, 결제, ?�산, OCR/AI???�자?�보???�제 ?�동 범위�?먼�? ?�정?�는 축으�??��??�다.

## 110. 2026-05-22 ?�류 ?�일 미리보기?� ?�열�??�결

### 구현

- `ManagerDocumentFileMetadata`??`previewUri`�?추�??�고 `ManagerDocumentPreviewResolver` 계층???�입?? ?�?�된 ?�류 메�??�이?�만?�로 ?�시 ?????�는 미리보기 URI�??�석?�도�??�리?�다.
- 매니?� ?�류 ?�록 ?�면??`?�일 ?�기` 버튼??추�????�로?�된 PDF/?��?지 ?�본??바로 ?�시 ?�인?????�게 ?�다.
- 관리자 ?�류 검??카드??`?�출 ?�일 보기`�?추�???매니?�가 ?�린 ?�분�??�격�?범죄경력 조회?��? 목록?�서 ?�택??바로 ?????�게 ?�다.
- 목업 모드?�서??SAF `content://` URI�??�께 ?�?�하�? Firebase 모드?�서??Storage 경로�??�운로드 URI�??�석??같�? ?�면 ?�름?�로 미리보기�??�도�?맞췄??
- 관??문자?�과 ?�류 ?�록 문서�??�데?�트?�다.

### 변�?범위

- app/src/main/java/com/example/bodeul/domain/model/ManagerDocumentFileMetadata.java
- app/src/main/java/com/example/bodeul/data/ManagerDocumentPreviewResolver.java
- app/src/main/java/com/example/bodeul/data/ServiceLocator.java
- app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java
- app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentPreviewResolver.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentPreviewResolver.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationActivity.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationBinder.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationCoordinator.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemBinder.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemModel.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCoordinator.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCardBinder.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCardModel.java
- app/src/main/java/com/example/bodeul/util/DocumentPreviewLauncher.java
- app/src/main/res/layout/activity_manager_document_registration.xml
- app/src/main/res/layout/item_manager_document_registration.xml
- app/src/main/res/layout/item_admin_manager_document.xml
- app/src/main/res/values/strings.xml
- app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java
- docs/manager-document-registration-2026-05-05.md
- docs/implementation-status.md

### ?��? 범위

- ?�로?�한 ?�일 ??��?� Storage ?�리 ?�책?� ?�직 ?�다.
- 과거 목업 ?�이?�처??`previewUri`가 ?�는 기존 ?�플 ?�일?� 목업 모드?�서 ?�시 ?????�다.

# 2026-05-22 ?�속 ?�치 공유?� 좌표 변�??�력 구현

## 구현

- `CompanionSession`, `CompanionLocationHistoryEntry`, `FirebaseCompanionSessionMapper`???�시�?공유 ?�태?� 좌표 변�??�력??추�???목업/Firebase가 같�? 구조�??�고 ?�?�하?�록 맞췄??
- `ManagerLiveLocationTracker`, `ManagerLocationSupport`, `ManagerGuideActivity`�?추�?·?�장??가?�드 ?�면?�서 ?�시�??�치 공유 ?�작/중�??� ?�속 GPS ?�데?�트 ?�송??처리?�다.
- `ManagerGuideCoordinator`, `BookingLiveLocationCoordinator`, `GuardianReportCoordinator`, `AdminOperationsCoordinator`???�시�?공유 ?�태?� 최근 좌표 ?�력 ?�시�?추�??�다.
- `activity_manager_guide.xml`, `strings.xml`???�시�?공유 ?�어 UI?� ?�내 문구�?반영?�다.

## 변�?범위

- `app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java`
- `app/src/main/java/com/example/bodeul/domain/model/CompanionLocationHistoryEntry.java`
- `app/src/main/java/com/example/bodeul/data/ManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseCompanionSessionMapper.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerLocationSupport.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerLiveLocationTracker.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerCurrentLocationSharer.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminOperationsCoordinator.java`
- `app/src/main/java/com/example/bodeul/util/CompanionLocationDisplayHelper.java`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/values/strings.xml`

## ?��? 범위

- 카카??지??SDK ?�장 지??마커 ?�동?� ?�직 ?�아 ?�다.
- 백그?�운???�치 추적�??�시???�치 ?�림?� ?�번 범위???�함?��? ?�았??
- ?�치 ?�력?� ?�션 문서 기�? 최근 10건만 ?��??�며, ?�기 보�? ?�책?� 별도 ?�계가 ?�요?�다.

## 111. 2026-06-05 ?�로?�트 ?�키?�처 개선 �?보안 ?�스

## 구현

- **?�키?�처 개선**: 기존 ?�티비티??밀집되???�던 비즈?�스 로직(?�히 ?�시�??�치 공유 ????`ViewModel` 기반 AAC ?�턴?�로 마이그레?�션?�여 ?�면 ?�전 ???�태 ?�실 �??�래??문제�??�결?�다.
- **백그?�운???�치 추적**: `ManagerLocationService` ?�그?�운???�비?��? ?�입?�여, 매니?�가 ?�면???�거???�른 ?�을 ?�용???�도 ?�치 추적??중단?��? ?�도�?개선?�다. `AndroidManifest.xml`??`POST_NOTIFICATIONS` 권한??추�??�여 Android 13 ?�상?�서 ?�림??지?�했??
- **?�시�?리스???�환**: ?�동 ?�로고침???�존?�던 ?�행 ?�황 조회�?Firestore `addSnapshotListener` 기반?�로 변경하???�시�??�이???�기?��? 구현?�다 (`BookingRepository` �?`FirebaseBookingRepository`).
- **보안 �??�증 규칙 강화**: `firestore.rules`�??�정?�여 매니?� 본인??직접 ?�인 ?�태(`managerDocumentStatus`)�?조작?�는 Self-Approval ?�뷰징을 ?�천 차단?�다. ?�한 ?�자/보호?��? ?�행 ?�션?�서 메시지�?보낼 ??발생?�던 `PERMISSION_DENIED` ?�러�??�결?�기 ?�해 `isAppointmentParticipant` 권한??추�??�다.
- **버그 ?�스**: 백그?�운???�비???�작 ??`null` 콜백?�로 ?�한 `NullPointerException` ???�래??문제�??�결?�다.

## 변�?범위

- `app/src/main/AndroidManifest.xml`
- `firestore.rules`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideViewModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerLocationService.java`
- `app/src/main/java/com/example/bodeul/data/BookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`

## ?��? 범위

- 리포?�에??지?�받?� 주요 ?�키?�처 �?보안 결함 ?�정 ?�료.

## 112. 2026-06-05 추�? ?�데?�트 (카카??�?SDK ?�이?�브 ?�동)

### 구현

- ?�자 ?�시�??�행 ?�면(`BookingLiveLocationActivity`)�?매니?� 가?�드 ?�면(`ManagerGuideActivity`)??**카카???�이?�브 �?SDK (v2.13.2)**�??�입?�여 ???��? ?�동 ?�이 지?��? �????�게 개선?�다.
- 백그?�운???�치 ?�비?�나 ?�시�??�기?�로 갱신?�는 `sharedLatitude`, `sharedLongitude` ?�보�?기반?�로 카카?�맵 마커 ?�치?� 카메??중심???�적?�로 변경되�??�동?�다.
- 카카?�맵 ?�이?�사?�클??맞춰 Activity??`onResume()`, `onPause()` ?�점??지?��? ?�개/?��? ?�도�?처리??메모�??�수�?방�??�다.

### 변�?범위

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/bodeul/BodeulApplication.java`
- `app/src/main/res/layout/activity_booking_live_location.xml`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`

### ?��? 범위

- ???�시 ?��? 카카???�랫?�에 미등록되?�을 ?�의 ?�외 처리 (?�재??미등�???지?��? ???��??????�음)
## 113. 2026-06-05 ߰ Ʈ (īī Ŀ   )

## 113. 2026-06-05 추�? ?�데?�트 (카카?�맵 마커 �?추적 개선)

### 구현

- 카카?�맵 SDK가 XML 벡터 ?�로?�블??직접 지?�하지 ?�아 로고 마커가 비정?�적?�로 ?�거??보이지 ?�던 문제�??�정?�다.
- `ic_map_marker`(빨간 ?�)?� `ic_tracking_dot`(?��? ?? 벡터�??��??�에 `Bitmap`?�로 변?�해 카카?�맵 `LabelStyle`???�용?�도�?로직??개선?�다.
- ?�치 권한 ?�청 ?�로?�스�?추�??�고 권한???�용?�면 카카?�맵??`TrackingManager`�??�성?�하???�시�????�치(?��? ??�??�시?�도�?기능???�성?�다.
- ?��? 지?????�백 버튼???��??�여 ?�이?�브 지?�에 문제가 ?�을 ?�도 기존 기능 ?�용??지?�이 ?�도�??�전?�치�?마련?�다.

### 변�?범위

- `app/src/main/res/drawable/ic_map_marker.xml`
- `app/src/main/res/drawable/ic_tracking_dot.xml`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`

### ?��? 범위

- ?�음

## 114. 2026-06-19 ?�프??개요 문서 추�?

### 구현

- ?�재 ?��???기�? ?�프??구성??별도 문서�??�리?�다.
- Android ?? 관리자 ?? Firebase Auth/Firestore/Storage/Functions, `tools/firebase`, GitHub Actions????���?경계�???번에 �????�게 ?�리?�다.
- Mock/Firebase 분기, Firestore 메모�?캐시 ?�책, App Check 준�??�태, ?�치 공유/?�심 채팅/카카??지???�동 ?�치??문서???�함?�다.

### 변�?범위

- `docs/infrastructure-overview.md`
- `README.md`
- `docs/document-guide.md`

### ?��? 범위

- ??문서???�재 `master` 기�? ?�프???�약?�다.
- App Check ?�제 강제 ?�용, ?�영 ?�경 분리, 배포 ?�차 변경이 ?�기�?같�? 문서�?기�??�로 갱신?�다.

## 115. 2026-06-19 ?�스??검�?�?개발?�경 ?�일 ?�리

### 구현

- 최신 `master` 기�??�로 `testDebugUnitTest`�??�시 ?�행???�위 ?�스?�까지 ?�과�??�인?�다.
- ?�?�소???�못 ?�함??`temp.txt`�??�거 ?�?�으�??�리?�다.
- ?� 공용?�로 ?��????�요가 ?�는 개인 개발?�경 ?�일??`.gitignore` 기�??�로 분리?�다.
  - `.vscode/settings.json`
  - `.idea/deploymentTargetSelector.xml`
  - `.idea/deviceManager.xml`
  - `.idea/appInsightsSettings.xml`
  - `.idea/git_toolbox_prj.xml`
  - `.idea/easycode.ignore`
  - `.idea/easycode/`

### 변�?범위

- `.gitignore`
- `docs/implementation-status.md`

### ?��? 범위

- `.idea` ?�래 ?�른 ?�일?��? ?�재 ?�로?�트 공용 ?�정?��? ?��?�??�인?????�요???�만 추�? ?�리?�다.
- ?�번 ?�리???�치 ?�택, ?�러그인 ?�태, 개인 ?�디???�정처럼 ?�용?�별 ?�차가 ???�일�??�?�으�??�다.

## 116. 2026-06-19 문서 ?�인 구조 ?�리

### 구현

- 문서 경로�???번에 ?�게 ??��지 ?�고, 카테고리 ?�렉?�리?� ?�인 `README.md`�?추�???문서 진입 구조�??�리?�다.
- `docs/architecture`, `docs/planning`, `docs/operations`, `docs/security`, `docs/design`, `docs/archive` ?�렉?�리�?추�??�다.
- 루트 `README.md`?� `docs/document-guide.md`�?카테고리 진입 기�??�로 ?�시 ?�리?�다.
- 구버???�자??검??메모, ?� ?�업 분해 초안, 기능?�명??추출본�? `docs/archive/`�??�동?�다.

### 변�?범위

- `README.md`
- `docs/document-guide.md`
- `docs/architecture/README.md`
- `docs/planning/README.md`
- `docs/operations/README.md`
- `docs/security/README.md`
- `docs/design/README.md`
- `docs/archive/README.md`
- `docs/archive/design-reference-review-2026-05-05.md`
- `docs/archive/team-task-breakdown.md`
- `docs/archive/보들_?�랫??기능?�명??md`

### ?��? 범위

- 링크 ?�급????본문 문서???�재 경로�??��??�다.
- ?�으�???문서�?추�????�는 먼�? 카테고리 ?�인부??맞추�? ?�제 ?�일 ?�동?� 링크 ?�급??검?�한 ???�계?�으�??�다.

## 117. 2026-06-19 최신 ?�그�?ZIP 기�???갱신

### 구현

- ?�로 받�? `design_refs/보들 가?�드.zip`??`design_refs/local/보들 가?�드.zip`?�로 ??���?`design_refs/local/latest_figma_2026-06-19/`???�제?�다.
- 최신 ?�자??기�???기존 `bodeul_original_resolution_screens.zip` 중심?�서 `보들 가?�드.zip` ?�제�?중심?�로 갱신?�다.
- 기존 `bodeul_original_resolution_screens.zip`, `bodeul_split_screens/`??보조 비교 ?�트�?격하?�다.
- ?�자??비교 문서?� 기�? 문서 링크�????�산 기�??�로 ?�시 맞췄??

### 변�?범위

- `design_refs/README.md`
- `design_refs/local/README.md`
- `docs/design-reference-review-2026-05-22.md`
- `docs/feature-spec-figma-audit-2026-05-22.md`
- `docs/implementation-status.md`

### ?��? 범위

- ??ZIP 기�? 분할 ?�면 ?�트가 ?�로 ?�요?�면 `latest_figma_2026-06-19/`�?기�??�로 ?�시 ?�성?�다.
- ?�재??보드??PNG ?�트만으로도 UI polish ?�선?�위 ?�단?� 가?�하??

## 118. 2026-06-19 ?�증/공통 ?�단 ?�약 카드 추�?

### 구현

- ??�� ?�택 ?�면 ?�단???�택 경로?� ?�음 ?�계�?먼�? 보여주는 ?�약 카드�?추�??�다.
- 로그???�면 ?�단???�재 ??���?로그???�원가??모드???�라 바뀌는 ?�약 카드�?추�??�다.
- ??�� ?�택�?로그?�이 같�? 카드 ?�이?�웃???�도�?공용 ?�약 카드 뷰�? 바인???�매?��? 분리?�다.
- 권한 ?�내 ?�면?� ?�재 구조가 최신 ?�그마�? ?�게 ?�긋?��? ?�아 ?�번 묶음?�서???��??�다.

### 변�?범위

- `app/src/main/java/com/example/bodeul/ui/auth/AuthSummaryCardModel.java`
- `app/src/main/java/com/example/bodeul/ui/auth/AuthSummaryCardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionSummaryFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/auth/LoginSummaryFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionActivity.java`
- `app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java`
- `app/src/main/res/layout/view_auth_summary_card.xml`
- `app/src/main/res/layout/activity_role_selection.xml`
- `app/src/main/res/layout/activity_login.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ?��? 범위

- ?�증/공통 polish ?�음 ?�서???�자/보호???�과 진행 ?�면 ?�계 ?�리??
- 관리자 ?�과 매니?� ??가?�드??최신 ?�그�?기�??�로 별도 묶음?�서 ?�어??조정?�다.

## 119. 2026-06-19 ?�자/보호?????�션 구획 ?�리

### 구현

- ?�자/보호???�에???�제 ?�작???�던 ??번째 ?�션 줄을 ?�거?�다.
- ???�면 ?�션 ?�서�?`?�단 ?�태 카드 -> 빠른 ?�행 -> 진행 로드�?-> 최근 ?�수 -> ?�비???�내`�??�시 ?�리?�다.
- 기존 기능 진입 경로???��??�고, ?�제�??��? ???�는 카드�??�겨 ?�면 밀?��? 줄�???

### 변�?범위

- `app/src/main/res/layout/activity_main.xml`
- `docs/implementation-status.md`

### ?��? 범위

- ?�자/보호??진행 ?�세?� 리포???�면?� 카드 ?�계?� ?�보 묶음?????�듬???��?가 ?�다.
- 매니?� ??가?�드 polish??별도 묶음?�로 ?�어??진행?�다.

## 120. 2026-06-19 최신 ?�그�??�락 보드 반영

### 구현

- 최신 로컬 ?�그�??�트�??�시 ?�인??문서?�서 빠진 보드�?반영?�다.
- `?�자 ???�면 (?�약 ?�료 ??-1`, `Main`, `Body`, `보들 가?�드.pdf`�?최신 ?�자??기�? ?�명??추�??�다.
- `?�행가?�드1`~`12`�??�순 묶음???�니???�속 ?�퀀??기�??�로 ?�시 명시?�다.

### 변�?범위

- `design_refs/README.md`
- `docs/design-reference-review-2026-05-22.md`
- `docs/feature-spec-figma-audit-2026-05-22.md`
- `docs/implementation-status.md`

### ?��? 범위

- 최신 ?�그�?기�??�서 빠진 기능 축�? ?�재 문서보다 구현 쪽에 ??많�? ?�다.
- ?�음 ?�인 ?�인?�는 개별 보드 기�? UI polish ?�선?�위�??�제 ?�면???�마??반영?�는지??

## 121. 2026-06-19 ȯ�� ���� �󼼿� ��ȣ�� ����Ʈ ���� ����

### ����

- ���� ���� �� ȭ�鿡�� `���� �⺻ ����`, `���� ��Ȳ�� ���� �޸�`, `���� �� �� ���� ��ȭ`�� ���� ���� ī��� �и��ߴ�.
- ���� �� ���� �޸�� �Ϸ� ����Ʈ�� �� ī�忡 ���� �ִ� ������ ����, �ǽð� Ȯ�� ������ ���� �� Ȯ�� ������ �����ߴ�.
- ��ȣ�� ����Ʈ ��û ī�嵵 `���� ��Ȳ`, `���� �޸�`, `���� �� �� ���� ��ȭ` �������� �ٽ� ���� �д� ������ �����ߴ�.
- ������ ���� ������ �����ϰ�, ȭ�� �𵨰� ���δ������� ���� ������ �籸���ߴ�.

### ���� ����

- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportEntryCardModel.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportEntryCardBinder.java`
- `app/src/main/res/layout/activity_booking_status.xml`
- `app/src/main/res/layout/item_guardian_report.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ���� ����

- ȯ��/��ȣ�� �� ���� polish�� ���� �� ���� ȭ��� �ǰ�����Ʈ ī�� ������ ������.
- ��ġ ����, ä��, ���� ��ȭ�� ȭ�� ���� ������ ������ �ǽð� �˸��� ÷�� ����� ���� �ļ� ������.

## 122. 2026-06-19 ���� �� ���� ���� ���� ����

### ����

- �ǽð� ��ġ Ȯ�� ȭ���� ���� ī�忡 `���� ���� ��ġ`, `����`, `�ֱ� ���� �ð�`�� ���� �����ִ� ��� ������ �߰��ߴ�.
- ���� �並 ���� �׼� ī�� ���� �÷�, ���� �� ���� Ȯ�� �帧�� ���� ���̵��� ������ �����ߴ�.
- ���� ��ǥ�� ���� ���� ���� ��ġ�� ���� ������ �������� ���� �ȳ� ������ �ڿ������� �̾������� �����ߴ�.
- ������ ���� ���¿��� ���� ��Ŀ ���� �� `NullPointerException`�� �� �� �ִ� ��ε� ���� ���Ҵ�.

### ���� ����

- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java`
- `app/src/main/res/layout/activity_booking_live_location.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ���� ����

- īī�� ���� SDK �ȿ��� ���� ����/�ǳ� �������� ���� �ٷ�� ������ ���� �ƴϴ�.
- ���� polish�� �ǰ�����Ʈ ī�� �������̳� �Ŵ��� Ȩ/���̵� ���� ���� ���� �����ϴ�.

## 123. 2026-06-19 �ǰ�����Ʈ ���� ���� ���� ��ȭ �и�

### ����

- ���� ���� �� ����Ʈ ī�� �ȿ��� `���� ��`, `���� ��ȭ`, `���� Ȯ��`�� ���� ���� ���� �������� ������.
- ��ȣ�� ����Ʈ ī�嵵 `���� ��`�� `���� ��ȭ`�� �и���, ���� ���� ���� �޸� �Ѵ��� ������ �� �ְ� �����ߴ�.
- ����Ʈ ������ ������ �ٲ��� �ʰ�, ȭ�� �𵨰� ���δ����� ���� ������ ���� �����ֵ��� �����ߴ�.

### ���� ����

- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusSectionModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportSectionModel.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportEntryCardModel.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportEntryCardBinder.java`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ���� ����

- �ǰ�����Ʈ�� ���� ���� ȭ������ Ȯ���ϰų� ���� ��/���� ��ȭ �̹����� ���� ��� �ܰ�� ���� �ƴϴ�.
- ���� polish�� �Ŵ��� Ȩ/���̵� ���� ������ ���� ȭ�� ���� ������ �����ϴ�.

## 124. 2026-06-19 �Ŵ��� Ȩ�� ���డ�̵� ���� ����

### ����

- �Ŵ��� Ȩ���� `�ǽð� ����/��� ī��`�� ���� ����, `���� ����` �׼��� �� �Ʒ����� �̾������� ī�� ������ ���ġ�ߴ�.
- ���డ�̵� ȭ���� `���� �ܰ�`�� `���� �� ����`���� ���� ��ġ�� ���� �ܰ� Ȯ�� �� ������ ���� ������� �������� �帧�� �����.
- ���� ī��� ����� ���������� `fallback` ǥ���� �����ϰ�, īī���� �������� ���� ��ġ�� ������ Ȯ���ϴ� �ȳ��� �����ߴ�.
- ���� ��� �������� ��ġ, ��ȣ�� ����, ����, ���� �޸� ������� ����� ������ ª�� �ȳ� ������ �߰��ߴ�.

### ���� ����

- `app/src/main/res/layout/activity_manager_home.xml`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ���� ����

- �Ŵ��� Ȩ�� ���̵��� ū ���� ����� ����������, īī�� ���� SDK �ȿ��� ����/�ǳ� �ȳ��� ���� ��ġ �����丮���� �����ִ� �ܰ�� ���� �ƴϴ�.
- ���� polish�� ����/���������� ���� ������, �Ŵ��� Ȩ ī�� ���ݰ� ���� �̼� ������ �����ϴ�.

## 125. 2026-06-19 �ǰ����� ȭ���� ����� ���� �������� ������

### ����

- �ǰ����� ȭ�鿡 `�� ������ ���� ���` ī�带 �߰��� �̸�, ����, �α��� ����, ����ó�� ���� ����� ȯ��/��ȣ�� ������ ���� ������ �����ߴ�.
- ���� `�ǰ� ������`, `���� ���� ����` ī�忡�� ���� ���� ���� ������ �߰��� � ������ Ȯ���ϴ� �������� �ٷ� �������� �����ߴ�.
- ������ ���� ������ �ٲ��� �ʰ�, �ǰ����� ȭ�� �𵨰� ���δ������� ������ �籸���ߴ�.

### ���� ����

- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoActivity.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoBinder.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoScreenModel.java`
- `app/src/main/res/layout/activity_health_info.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

### ���� ����

- ����� ���� ���� ���� ȭ���̳� �̿� ����/���Ǳ��� ��ģ ������ ������������ ���� �ƴϴ�.
- ���� polish�� ����� ������ ����Ǵ� ���� �̷�/���� ���� Ȯ���̳�, ������ �� �� �ļ� polish�� �����ϴ�.
## 126. 2026-06-19 �ǰ����� ȭ�鿡 �̿� ���� ���� �߰�
- `HealthInfoActivity`, `HealthInfoCoordinator`, `HealthInfoBinder`, `HealthInfoScreenModel`, `activity_health_info.xml`�� Ȯ���� �ǰ����� ȭ�� �ȿ� `�̿� ����` ī�带 �߰��ߴ�.
- ȯ��/��ȣ�ڰ� ���� ȭ�鿡�� `���� �̷� �� ��û ����`, `���� ���� Ȯ��`, `��ȣ�� ����Ʈ` �������� �ٷ� �̵��� �� �ְ� �����ߴ�. ��ȣ�� ����Ʈ ��ư�� ��ȣ�� ���������� ����ȴ�.
- ���� �ǰ����� ī�� ����� �����ϰ�, ���� ����/�ǰ� ������/���� ���� ���� �Ʒ��� ���� ��� �帧�� �����ϴ� �׼� ī�常 �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest`, `npm --prefix admin-web run build`
- ���� ����: ����� ���� ���� ȭ��, �̿� ����/���Ǹ� �� �а� ���� ������ ���������� ����, ����/��ġ/�˸� �ļ� ����ȭ.
## 127. 2026-06-19 �ǰ����� �̿� ���� ī�忡 ���� ��� �߰�
- `HealthInfoCoordinator`, `HealthInfoScreenModel`, `HealthInfoBinder`, `HealthInfoActivity`, `activity_health_info.xml`�� Ȯ���� `�̿� ����` ī�忡 ���� ��� ������ �߰��ߴ�.
- ��ü ��û ��, ���� ��/��� ��û ��, �Ϸ� ��û ��, ��� ��û ���� ���� ������ ���� ��� �������� ������ ���� ȭ�鿡�� �ٷ� ���̰� �����ߴ�.
- �̿� ���� ī���� �̵� �׼��� `���� �̷� �� ��û ����`, `���� ���� Ȯ��`, `��ȣ�� ����Ʈ`�� �����ϰ�, �ܼ� �̵� ��ư�� �ƴ϶� ���� �̿� ���°� ���̴� ���������� ī�� ���ҷ� �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest`, `npm --prefix admin-web run build`
- ���� ����: ����� ���� ���� ȭ��, �̿� ����/���Ǳ��� ������ ������ ����� ����������, ����/��ġ/�˸� �ļ� ����ȭ.
## 128. 2026-06-19 ����� Ȩ ���� ����� ��ư ���� ����
- `MainActivity`, `ClientHomeDashboardBinder`, `strings.xml`�� ������ ����� Ȩ�� �� ��° ���� ���� ī�带 ���� ���� `�ǰ����� �� �̿� ����`�� �����ߴ�.
- ��ȣ�� Ȩ���� `��ȣ�� ����Ʈ ����`�� ���̴� ��ư�� ���� �󼼷� �̵��ϴ� ����ġ�� �����ߴ�. ���� ��ȣ�� ���� ���� ��ư, ���� ī�� ��ư, �ֱ� ��û ��ư�� ��� ��ȣ�� ����Ʈ�� ����ȴ�.
- �ǰ����� ȭ���� ���� �̷�/����/����Ʈ �̵��� �Բ� ǰ�� �Ǹ鼭, Ȩ������ `��û`�� `�̿� ����` ������ �и��ϰ� ����Ʈ�� ���� �帧 �ȿ��� Ȯ���ϵ��� ������ �����.
- ����: `assembleDebug`, `testDebugUnitTest`
- ���� ����: ����� ���� ���� ȭ��, ������ ����� ���������� ����, ��ġ/����/�˸� �ļ� ����ȭ.
## 129. 2026-06-19 ����� Ȩ �̿� ���� ī�忡 ���� ��� �ݿ�
- `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `strings.xml`�� Ȯ���� Ȩ�� `�ǰ����� �� �̿� ����` ī�忡 ���� �Ǽ� ����� �ݿ��ǵ��� �����ߴ�.
- ��ü ���� ��, ���ࡤ��� ��, �Ϸ� ���� Ȩ ī�� ������ �Բ� �����ֵ��� �ٲ� Ȩ������ ���� �̿� ���¸� �ٷ� ���� �� �ְ� �ߴ�.
- ��ȣ�� Ȩ������ ���� ī�尡 ��ȣ�� ����Ʈ �帧�� ����ȴٴ� ���� �����ϸ鼭, ���� �Ը�� ���� ��Ȳ�� ���� �����ִ� �������� ������ �����.
- ����: `assembleDebug`, `testDebugUnitTest`
- ���� ����: ����� ���� ���� ȭ��, ������ ����� ���������� ����, ��ġ/����/�˸� �ļ� ����ȭ.
## 130. 2026-06-19 �ǰ����� ��ǥ ��û ���ذ� ���� Ȯ�� �ȳ� ����
- `MainActivity`���� �ǰ����� ȭ�� ���� �� ���� Ȩ�� ��ǥ ��û ID�� �Բ� �ѱ⵵�� �ٲ�, Ȩ�� �ǰ������� ���� ���� �������� ������ �����ߴ�.
- `HealthInfoCoordinator`�� `strings.xml`�� Ȯ���� �̿� ���� ī�忡 `��ǥ ��û ����`, `���� Ȯ��` ������ �߰��ߴ�.
- ȯ�ڴ� ���� ���� �Ǵ� �ֱ� �̿� �󼼸�, ��ȣ�ڴ� ��ȣ�� ����Ʈ�� ���� Ȯ�� �������� �а� ����� ����� Ȩ�� ���������� ���� ������ �� ��Ȯ�� �����.
- ����: `assembleDebug`, `testDebugUnitTest`
- ���� ����: ����� ���� ���� ȭ��, ������ ����� ���������� ����, ��ġ/����/�˸� �ļ� ����ȭ.
## 131. 2026-06-19 �ǰ����� ��ǥ ��ư�� ���Һ� ���� Ȯ�� �������� ����
- `HealthInfoPrimaryActionType`�� �߰��ϰ� `HealthInfoScreenModel`, `HealthInfoCoordinator`, `HealthInfoActivity`�� Ȯ���� �ǰ����� ��� ��ǥ ��ư�� ���Ұ� ���� ���¿� �´� ȭ������ �̵��ϵ��� �����ߴ�.
- ��ȣ�ڴ� �ǰ����� ��� ��ư���� �ٷ� ��ȣ�� ����Ʈ�� ����, ȯ�ڴ� ���� �� �����̸� ���� ���� �帧��, �Ϸ�� �����̸� �ֱ� �̿� �󼼸� �ٽ� ���� �������.
- `MainActivity`���� �ǰ����� ���� �� ��ǥ ��û ID�� �ѱ⵵�� �� ���� �۾��� ������, Ȩ�� �ǰ������� ���� ��û ���ذ� ���� ���� Ȯ�� ������ �����ϰ� �����.
- ����: `assembleDebug`, `testDebugUnitTest`, `npm --prefix admin-web run build`
- ���� ����: ����� ���� ���� ȭ��, ������ ����� ���������� ����, ��ġ/����/�˸� �ļ� ����ȭ.
## 132. 2026-06-19 ����� ���� ȭ��� ���� ���� ��� �߰�
- `ClientSupportRequest`, `ClientSupportCategory`, `ClientSupportStatus`, `ClientSupportRepository`�� �߰��� ȯ��/��ȣ�� ���Ǹ� �Ŵ��� ���� `supportInquiries`�� �и��ߴ�.
- `FirebaseClientSupportRepository`, `MockClientSupportRepository`, `firestore.rules`�� ������ `clientSupportRequests` �÷����� ����/������ �б� �������� �����ߴ�.
- `ClientSupportActivity`, `ClientSupportCoordinator`, `ClientSupportBinder`, `activity_client_support.xml`�� �߰��� ���� ������ �ֱ� ���� Ȯ�� ȭ���� �����ߴ�.
- `HealthInfoActivity`, `HealthInfoCoordinator`, `HealthInfoBinder`, `activity_health_info.xml`�� ������ �ǰ������� `�̿� ����` ī�忡�� �ٷ� ���� ������ �����ϵ��� �����ߴ�.
- `tools/firebase/lib/baseline-config.js`, `tools/firebase/seed-sample-service-data.js`, `docs/firebase-setup.md`, `docs/data-api-draft.md`, `docs/firebase-reset-baseline.md`�� �Բ� ������ � ������ ���� ���ص� ���� �÷��� ������ �����.
- ����: `assembleDebug`, `testDebugUnitTest`, `node --check tools/firebase/seed-sample-service-data.js`, `node --check tools/firebase/reset-firestore-baseline.js`, `node --check tools/firebase/check-firestore-state.js`
- ���� ����: ������ ���� ���� ȭ�鿡 `clientSupportRequests`�� �����ϴ� �۾�, ����� ������������ ����/�̿볻�� �ϼ�, ��ġ/����/�˸� �ļ� ����ȭ.

## 133. 2026-06-19 ������ ���� ���信 ����� ���� ����
- `AdminDashboard`, `AdminRepository`, `FirebaseAdminRepository`, `MockAdminRepository`�� ������ `clientSupportRequests`�� ������ ��ú��� ���� ���ǿ� �Բ� �����ߴ�.
- `AdminActivity`, `AdminSupportCoordinator`, `AdminSupportInquiryCardBinder`, `AdminSupportInquiryPresentationFormatter`�� ������ �Ŵ��� ���ǿ� �̿��� ���Ǹ� �� ��Ͽ��� �����ϰ�, ���� ��ó�� ���� ���� ���� ��θ� �б��ߴ�.
- `strings_admin_manager_extension.xml`, `docs/data-api-draft.md`, `docs/firebase-setup.md`�� �ֽ� ������ ���� ��� �������� �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: ������ ȭ�鿡�� �̿��� ���� ī�װ����� ���͸� ����, �̿��� ���� ���� �˸��� ���� Ǫ�÷� �������� �ļ� ��å���� ���ܵд�.

## 134. 2026-06-19 ������ ���� ��ó ���� �߰�
- `AdminSupportFilter`, `AdminSupportFilterChipModel`, `AdminSupportDashboardModel`, `AdminSupportCoordinator`�� �߰�/Ȯ���� ������ ���� ���� ���ǿ� `��ü / �Ŵ��� ���� / �̿��� ����` ���͸� �־���.
- `activity_admin.xml`, `AdminActivity`, `strings_admin_manager_extension.xml`�� ������ ��ó�� ���� ��ư�� ���� ���� ��� ���� �����ϰ�, ������ ���� �������� ī�� ��ϸ� ���� ���̵��� �����ߴ�.
- ����: `assembleDebug` ���, `testDebugUnitTest`�� ���� ���� �� `processDebugResources` ���� �浹�� �־� ���� ��������� ��� Ȯ���ߴ�.
- ���� ����: ���� ���ͱ��� �߰�����, �̿��� ���� ���� �� Ǫ�� �˸��� �������� �ļ� ��å���� ���ܵд�.

## 135. 2026-06-19 ������ ���� ���� ���� �߰�
- `AdminSupportFilter`�� Ȯ���� ������ ���� ���ǿ� `�亯 ���`, `�亯 �Ϸ�` ���͸� �߰��ߴ�.
- `AdminSupportCoordinator`, `AdminSupportInquiryPresentationFormatter`, `strings_admin_manager_extension.xml`�� ������ ��ó ���Ϳ� �Բ� �� �ٿ��� ���� ���͵� ������ �� �ְ� �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: ��ó ���Ϳ� ���� ���͸� ���ÿ� �����ϴ� 2�ܰ� ���ʹ� ���� ���� �ʾҰ�, �ʿ��ϸ� �ļ����� Ȯ���Ѵ�.

## 136. 2026-06-19 �ǰ����� ȭ�鿡 ���� �̷� ��� ī�� �߰�
- `HealthInfoActivity`, `HealthInfoCoordinator`, `HealthInfoBinder`, `HealthInfoScreenModel`, `activity_health_info.xml`, `strings.xml`�� ������ �ǰ����� ȭ�鿡 `���� �̷�` ī�带 �߰��ߴ�.
- `ClientSupportRepository`�� ���� �ֱ� ���� ����� �Բ� �ҷ�����, �� ���� �Ǽ�, �ֱ� ���� ����, �з�, ����, �ֱ� ó�� �ð��� ���� ȭ�鿡�� �ٷ� Ȯ���� �� �ְ� �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: �̿� ������ ���� �̷��� ������ �и��� ���� ����� ���������� �� ������ ���� �ļ��̴�.

## 137. 2026-06-19 �ǰ����� ȭ�鿡�� ���� �帧 �и� ����
- `activity_health_info.xml`, `strings.xml`�� ������ `���� �̷� �� ���� ����` ��ư�� �̿� ���� ī�尡 �ƴ϶� ���� �̷� ī�� �Ʒ��� �̵����״�.
- �̷ν� �ǰ����� ȭ�鿡�� ����/���� �帧�� ���� �帧�� �ð������� �и��ǵ��� �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: ���� �̷°� ���� �̷��� ������ �и��� ���� ����� ���������� �� ������ ���� �ļ��̴�.
## 138. 2026-06-19 �ǰ����� ȭ�鿡 �ֱ� �̿� �̷� ī�� �߰�
- `HealthInfoCoordinator`, `HealthInfoScreenModel`, `HealthInfoBinder`, `HealthInfoActivity`, `activity_health_info.xml`, `strings.xml`�� ������ �ǰ����� ȭ�鿡 `�ֱ� �̿� �̷�` ī�带 �߰��ߴ�.
- ���� �ε��� ���� ��� �������� �ֱ� Ȯ�� ������ ���� �� �Ǳ��� ����, ����, ���¸� ���� ī�忡�� Ȯ���� �� �ְ� �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: ���� �̷� ���� ȭ��� ����/�̿볻���� ������ �и��� ����� ���������� ������ ���� �ļ��̴�.
## 139. 2026-06-19 �ǰ����� ȭ�鿡 �ֱ� ���� �亯 ��� �߰�
- `HealthInfoCoordinator`, `strings.xml`�� ������ �ֱ� ���ǰ� �亯 �Ϸ� ������ �� `�ֱ� �亯 ���`�� �ǰ����� ���� �̷� ī�忡�� �ٷ� Ȯ���� �� �ְ� �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: ���� �亯 Ǫ�� �˸��� �̿볻��/���� �� �и��� ���� �ļ��̴�.
## 140. 2026-06-19 ����� ���� �̷� ���� ȭ�� �߰�
- `ClientBookingHistoryActivity`, `ClientBookingHistoryCoordinator`, `ClientBookingHistoryBinder`, `ClientBookingHistoryScreenModel`, `ClientBookingHistoryEntryModel`, `activity_client_booking_history.xml`, `AndroidManifest.xml`, `strings.xml`�� �߰�/������ �ǰ����� ȭ�鿡�� �б� ���� `���� �̷�` ȭ������ ������ �� �ְ� �����ߴ�.
- �ǰ����� ȭ���� `�ֱ� �̿� �̷�` ī�� �Ʒ��� ���� ���� ��ư�� �߰��ϰ�, �� ȭ�鿡���� �ֱ� ���� ����� ī�������� Ȯ���ϰ� �ʿ� �� ���� `BookingActivity`�� �̵��� ��û ���� �帧�� �̾�� �ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: ���� �̷°� ���� �̷��� ���� ������������ ������ �и��ϴ� ������ �̿��� ���� ���� Ǫ�� �˸��� ���� �ļ��̴�.
## 141. 2026-06-19 �ǰ����� ȭ���� ���� ������������ ����
- `HealthInfoActivity`, `HealthInfoBinder`, `HealthInfoCoordinator`, `HealthInfoScreenModel`, `HealthInfoTab`, `activity_health_info.xml`, `strings.xml`�� ������ �ǰ����� ȭ���� `�̿� ���� / �ǰ� ������ / ���� �̷�` 3�� ������ ������.
- ī�� ��ġ�� �����ϰ�, ������ �ǿ� �´� ī�屺�� ������ �� ȭ�鿡�� ���������� ������ �� �и��ϰ� �巯������ �����ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: �̿��� ���� ���� Ǫ�� �˸��� ������ ���� ������ ��ó+���� ���� ������ ���� �ļ��̴�.
## 142. 2026-06-19 ������ ���� ���͸� ��ó�� ���·� �и�
- `AdminSupportSourceFilter`, `AdminSupportStatusFilter`, `AdminSupportCoordinator`, `AdminSupportDashboardModel`, `AdminSupportInquiryPresentationFormatter`, `AdminActivity`, `activity_admin.xml`�� ������ ������ ���� ���ǿ��� `��ó`�� `�亯 ����` ���͸� ���ÿ� ������ �� �ְ� �����ߴ�.
- ���� ���� ���� ������ ���� `AdminSupportFilter` / `AdminSupportFilterChipModel`�� �����ϰ�, ��ó ���Ϳ� ���� ���͸� ���� ���� Ĩ �𵨷� �и��ߴ�.
- ����: `assembleDebug`, `testDebugUnitTest` ���.
- ���� ����: �̿��� ���� ���� �� Ǫ�� �˸��� ���� �ļ��̴�.
## 143. 2026-06-19 �̿��� ���� �亯 ��Ȯ�� ���� �߰�
- `ClientSupportRequest`, `ClientSupportRepository`, `FirebaseClientSupportRepository`, `MockClientSupportRepository`, `MockBodeulRepository`, `FirebaseAdminRepository`�� ������ �̿��� ���� �亯�� `��Ȯ��/Ȯ�� �Ϸ�` ���¸� �����ϵ��� �����ߴ�.
- ������ �亯 ���� �� `responseReadByUser=false`�� �ǵ�����, �̿��� ���� ȭ���� ���� `markClientSupportResponsesRead(...)`�� �亯 Ȯ�� ���¸� �ݿ��ϵ��� �����ߴ�.
- `ClientSupportCoordinator`, `ClientSupportActivity`, `HealthInfoCoordinator`, `strings.xml`�� ������ `�� �亯`, `��Ȯ�� �亯 �Ǽ�`, `�ֱ� �亯 ����` ����� ����� ȭ�鿡 �����ߴ�.
- ����: `assembleDebug --no-build-cache --rerun-tasks`, `testDebugUnitTest --no-build-cache --rerun-tasks` ���.
- ���� ����: FCM ��ū ��ϰ� �̿��� ���� �亯 Ǫ�� �˸��� ���� �ļ��̴�.
## 144. 2026-06-19 Android FCM ��ū ��� ��� �߰�
- `Firebase Messaging` �������� ���� Firebase BOM ���� �ȿ��� �߰��ߴ�.
- `NotificationTokenRegistrar` �������̽��� Firebase/Mock ������ ����, ��Ƽ��Ƽ�� ��ū ����ȭ ȣ�⸸ �ϵ��� �����ߴ�.
- �� ���� �� ���� ������ ������ `BodeulApplication`���� ��ū�� ����ȭ�ϰ�, �α��� ���Ŀ��� `LoginActivity`���� ���� ��ϱ⸦ �ٽ� ȣ���Ѵ�.
- `BodeulFirebaseMessagingService`�� �߰��� FCM ��ū�� ���ŵǸ� `users/{uid}` ������ `notificationTokens`, `notificationTokenUpdatedAt`, `notificationTokenPlatform`�� �����ϵ��� �����ߴ�.

## 145. 2026-06-19 �̿��� ���� �亯 Ǫ�� �˸� Ʈ���� �߰�
- `functions/index.js`�� `notifyClientSupportAnswered` Firestore Ʈ���Ÿ� �߰��ߴ�.
- `clientSupportRequests/{id}` ������ `ANSWERED` ���·� �ٲ�� �亯 ������ ���� ���� ��쿡�� �߼��ϵ���, ���� ���¿� `respondedAt`�� ���ϴ� ������ �־���.
- ����� ������ `notificationTokens`�� �о� FCM ��Ƽĳ��Ʈ�� ������, ��ū�� ������ �α׸� ����� �ǳʶڴ�.
- �̿��� ���� �亯 Ǫ�ô� `responseReadByUser`�� ���� `false`�� ���¿����� �߼۵ǵ��� �����.
## 146. 2026-06-19 �Ŵ��� ��ġ ���� deprecated API ����
- `ManagerLocationService`�� `stopForeground(true)` ȣ���� `stopForegroundCompat()`�� ���ΰ�, Android 13 �̻󿡼��� `STOP_FOREGROUND_REMOVE`�� ����ϵ��� �ٲ��.
- ��ġ ���� ���׶��� �˸� ����, ä�� �̸�, ä�� ������ ���ڿ� ���ҽ��� �и��ߴ�.
- �Ŵ��� ��ġ ���� �˸� ������ �ڵ忡 ���� ���� �ִ� ��θ� ���ҽ� ������ �����ߴ�.
## 147. 2026-06-19 �̿��� ���� �亯 Ǫ�� ��ȿ ��ū ����
- 
otifyClientSupportAnswered���� ��Ƽĳ��Ʈ ������ Ȯ���� messaging/invalid-registration-token, messaging/registration-token-not-registered ��ū�� ����� ������ 
otificationTokens �迭���� �����ϵ��� �����ߴ�.
- ��ȿ ��ū�� �����Ǹ� 
otificationTokens�� updatedAt�� �����ϰ�, ���� ��ū�� �״�� �����Ѵ�.
- �߼� �α׿� invalidTokenCount�� �߰��� ��ȿ ��ū ���� ���θ� ���� ������ �� �ְ� �ߴ�.
## 148. 2026-06-19 �̿��� ���� �亯 Ǫ�� foreground ó��
- BodeulFirebaseMessagingService�� onMessageReceived�� �߰��� client_support_answered ������ �޽����� ���� ó���ϵ��� �����ߴ�.
- ClientSupportActivity, HealthInfoActivity�� �� ���� ��ε�ĳ��Ʈ�� ������ ���� �亯 ���� �� ȭ���� ��� �ٽ� �ҷ������� �����ߴ�.
- ���� ȭ���� ����/�ǰ������� �ƴ� ���� �ý��� �˸��� ����, ���� ȭ���� ���� ���� ���� �� �� ���Ÿ� �����ϵ��� AppActivityTracker�� �߰��ߴ�.
## 149. 2026-06-19 �α׾ƿ� �� FCM ��ū ����
- NotificationTokenRegistrar�� ���� ����� ��ū ���� ��θ� �߰��ϰ�, FirebaseAuthRepository.signOut()���� �α׾ƿ� ���� ȣ���ϵ��� �����ߴ�.
- Firebase ��忡���� ���� ����� UID�� ���� Ȯ���� �� 
otificationTokens �迭���� ���� ��� ��ū�� rrayRemove�� �����Ѵ�.
- �� ��忡���� ���� �������̽��� no-op���� ������ �α���/�α׾ƿ� �帧�� ���� �ʰ� �����.
## 150. 2026-06-19 �̿��� ���� �亯 foreground �ȳ� ����
- HealthInfoActivity�� ClientSupportActivity�� ���� �亯 ��ε�ĳ��Ʈ ���� ���� Snackbar �ȳ��� �߰��ߴ�.
- �ǰ����� ȭ�鿡���� �� �亯 ���� �� ���� ȭ������ �ٷ� �� �� �ִ� �׼��� �����ϰ�, ���� ȭ�鿡���� ����� �ٽ� �ҷ��� �� ���� �޽����� ��� �����ֵ��� �����ߴ�.
- ClientSupportPushContract�� ����/���� extra�� �߰��� foreground �ȳ��� ���� �亯 ������ �켱 ����ϵ��� �����.
## 151. 2026-06-19 �̿��� ���� �亯 ī�� ��Ŀ�� ����
- ClientSupportActivity�� supportRequestId extra�� �߰��ϰ�, push ���� �Ǵ� �˸� ���� �� �ش� ���� ī�带 �켱 �����ϵ��� �����ߴ�.
- ClientSupportCoordinator, ClientSupportRequestCardModel, ClientSupportRequestCardBinder�� ������ �亯�� ������ ���� ī�忡 ���� stroke�� �����ϰ� ����� �ش� ī�� ��ġ�� �̵���Ű���� �����.
- ClientSupportPushNotifier�� HealthInfoActivity�� ���� ȭ�� ���Ե� ���� supportRequestId�� �����ϵ��� �����ߴ�.
## 152. 2026-06-19 �ǰ����� ���� �� �ڵ� ��ȯ
- HealthInfoActivity�� �̿��� ���� �亯 ��ε�ĳ��Ʈ�� �����ϸ� selectedTab�� SUPPORT�� ��ȯ�� �� �ٽ� �ҷ������� �����ߴ�.
- �� �亯�� ������ ��Ȳ���� �ǰ����� ȭ���� ���� ������, ���� ���� ���� �ٷ� ���� �̷� �� ���¿� �ֽ� �亯 ����� Ȯ���� �� �ִ�.
## 153. 2026-06-19 �ǰ����� ���� �� ��Ȯ�� ���� ����
- HealthInfoCoordinator�� HealthInfoScreenModel�� ������ ��Ȯ�� �亯 ������ ���� �� ���� �̷� �� �󺧿� ���ڸ� ���� �����ϵ��� �����ߴ�.
- HealthInfoBinder�� ���� ���� ���õ��� ���� ���¿����� ��Ȯ�� �亯�� ������ ����� ������� �����ϵ��� �����.
- �̷ν� �ǰ����� ȭ�鸸 ���� ���� �亯 Ȯ���� �ʿ����� ��� �� �� �ִ�.
## 154. 2026-06-19 Ȩ ȭ�� ���� ��Ȯ�� ���� ����
- ClientHomeCoordinator�� ClientSupportRepository�� �߰��� ������ Ȩ ��ú��带 ���� �� �̿��� ������ ��Ȯ�� �亯 ������ �Բ� ����ϵ��� �����ߴ�.
- ClientHomeDashboard�� ClientHomeDashboardBinder�� ������ �ǰ����� �� �̿� ���� ī�� ������ ��Ȯ�� ���� �亯 ������ �ٷ� �����ϵ��� �����.
- MainActivity�� Ȩ �ڵ������ ���� �� ���� ���� ����Ҹ� ������ �ǰ����� �ǰ� Ȩ ī�尡 ���� ���� ���¸� �����ϵ��� �����ߴ�.
## 155. 2026-06-19 ��� ��Ȯ�� ���� �亯 ǥ�� �߰�
- ClientSupportRequest�� 24�ð� ���� ��� ��Ȯ�� �亯 �Ǻ� helper�� �߰��ߴ�.
- ClientSupportCoordinator�� ClientSupportRequestCardBinder�� ������ ��� ��Ȯ�� �亯�� ��Ͽ��� ���� ���� ������ ���� ���� stroke�� ���̰� �����ߴ�.
- HealthInfoCoordinator, ClientHomeCoordinator, ClientHomeDashboard, ClientHomeDashboardBinder�� ������ �ǰ������� Ȩ ȭ�鿡���� 24�ð� �̻� ��Ȯ�� �亯 ���� �Բ� �����ϵ��� �����.
## 156. 2026-06-19 Ȩ ȭ�� ���� ���� ���� �߰�
- activity_main.xml�� �ǰ����� �� �̿� ���� ī�� ���� ���� �並 �߰��ߴ�.
- ClientHomeDashboardBinder�� ��Ȯ�� �亯�� ������ `�� �亯 N`, 24�ð� �̻� ��� ��Ȯ�� �亯�� ������ `24�ð�+ N` ������ ���� �����ϵ��� �����ߴ�.
- Ȩ ī�� ���� ������ �Բ� �����ε� ��� Ȯ���� �� �ְ� ��, ���� �亯 Ȯ�� ������ �ǰ����� ȭ������ �� ���� ����ǵ��� �����.

## 157. 2026-06-19 �̿��� ���� �亯 ��˸� ��å �߰�
- clientSupportRequests ������ 
esponseReminderCount, 
esponseReminderSentAt ���¸� �߰��� ��� ��Ȯ�� �亯 ��˸� �̷��� �����ϵ��� �����ߴ�.
- sendClientSupportAnswerReminders ������ �Լ��� �߰��� �亯 �� 24�ð��� ���� ��Ȯ�� ���ǿ� �Ϸ� ���� �ִ� 3ȸ���� ��˸��� �������� �����ߴ�.
- �ʱ� �亯 Ǫ�ÿ� ��˸� Ǫ�ô� ���� �߼� helper�� ����ϰ�, ��ȿ FCM ��ū ���� ��Ģ�� �����ϰ� �����Ѵ�.

## 158. 2026-06-19 Ȩ/�ǰ����� ���� ��� �߰�
- �̿��� ���� �亯 ��Ȯ�� ���¸� Ȩ�� �ǰ����� ��ܿ��� ���� ��å���� �����ִ� ���� ��ʸ� �߰��ߴ�.
- �� �亯, 24�ð� �̻� ��Ȯ�� �� �ܰ�� �����ϰ�, �� ȭ�� ��� ���� ȭ������ �ٷ� �̵��� �� �ְ� �����ߴ�.
- ���� Ȩ ī��� �ǰ����� �� ��ġ ���� ���� ��� ��ʸ� ���� ���� �亯 ���ü��� ��ȭ�ߴ�.

## 159. 2026-06-19 ���� ī�� ���� ��ġ�� �߰�
- ClientSupport ȭ�鿡�� Ư�� ���� ī���� �亯�� �ٷ� ���� ���� �ٽ� ���� �� �ְ� ȭ�� ���¸� �߰��ߴ�.
- push�� ���޵� supportRequestId�� �ش� ī�� ��Ŀ�̿� �̾� �ڵ� ��ħ���� ����ǵ��� �����ߴ�.
- Ȩ/�ǰ����� ��ʿ� ���� ȭ���� ���� ���� ī�� ���� �帧���� �̾������� �����.

## 160. 2026-06-19 Ȩ ���� ��Ȯ�� ī��Ʈ ���� ��ȭ
- Ȩ�� �ǰ��������̿� ���� ī�忡 ���� ��Ȯ�� ���� ��� ���� �� �ִ� ī��Ʈ ������ �߰��ߴ�.
- �Ϲ� ��Ȯ���� ����, 24�ð� �̻� ��Ȯ���� 24h+ ������ ������ ī�� �������� ���� ���̰� �����ߴ�.
- ���� ��� ���� ��ʿ� �Բ� Ȩ ù ȭ�鿡���� �亯 Ȯ�� �켱������ �� �и��������� �����.

## 161. 2026-06-19 ���� ���� Ȯ�� ��� �߰�
- push�� ��ʸ� ���� Ư�� ���Ƿ� ���� �� �ش� ī�常 ���� �����ִ� ���� Ȯ�� ��带 �߰��ߴ�.
- ���� ��忡���� ��� ���� ī�带 �ڵ� ��Ŀ���ϰ� �亯�� ��ģ ���·� �����ϸ�, ��ü ���� ����� ������ ������ �� �ִ�.
- ���� ȭ���� �˸� ���� ��ο� �� �ڿ������� ����ǵ��� ��� �ȳ� ������ ī�� ǥ�� ��å�� �Բ� �����ߴ�.

## 162. 2026-06-19 FCM ��� �̻�� ��ū ���� ��å �߰�
- notificationTokens �迭�� �����ϰ�, ��ū�� ������ ����ȭ �ð��� notificationTokenEntries ��Ÿ�����ͷ� �Բ� �����ϵ��� �����ߴ�.
- notifyClientSupportAnswered�� ��˸� �Լ����� ��ȿ ��ū ���� �� ��Ÿ�����͵� ���� ���쵵�� �����.
- cleanupStaleNotificationTokens ������ �Լ��� �߰��� 60�� �̻� ���ŵ��� ���� ��ū�� ���� ��Ÿ�����͸� �Ϸ� 1ȸ �����ϵ��� �����ߴ�.


## 163. 2026-06-19 Ȩ ��� ���� ���� �߰�
- Ȩ ��� ����� ���� ��Ȯ�� �亯 ������ �߰��� �� ���� ���Ŀ��� �亯 ���¸� �ٷ� Ȯ���� �� �ְ� �����ߴ�.
- �Ϲ� ��Ȯ���� ���� ����, 24�ð� �̻� ��Ȯ���� 24h+�� ������ �Բ� �����ְ� ���� ī��/��ʿ� ���� �������� ������ �����.


## 164. 2026-06-19 ���� ���� ��� ��Ÿ ���� ���
- ���� ���� ��忡���� ��ǥ ��û ���, �ֱ� ���� ���, �� ���� ���� ��, ��� ����/������ ����� �亯 Ȯ�� ī�常 ���� ���̵��� �����ߴ�.
- �˸� ���� ��ο��� �ֺ� �������� �亯 Ȯ�ο� �ٷ� ���ߵǵ��� ȭ�� ���踦 �ܼ�ȭ�ߴ�.


## 165. 2026-06-19 ���� ���� ī�� ���� ��Ÿ ���
- ���� ���� Ȯ�� ��忡�� ī�� ������ �з�, ����, ���� �ð�, �亯 ��ġ�� ����� ����� ����/�亯 ���� Ȯ�ο��� ���ߵǵ��� �����ߴ�.
- �Ϲ� ���� ��� ��忡���� ���� ī�� ��Ÿ�� ��� ������ �״�� ������ Ž������ ���� Ȯ�� �帧�� �и��ߴ�.


## 166. 2026-06-19 FCM ��ū �����ֱ� ��å ����ȭ
- notificationTokens �迭 ����, notificationTokenEntries ��Ÿ������, ��ȿ ��ū ����, 60�� ��� �̻�� ��ū ���� ������ ���� ������ �����ߴ�.
- ���� ���ΰ� � ���ο��� ���� ��å ������ �������� ��ũ�� �����ߴ�.

## 167. 2026-06-19 �౹ ���� �ܰ� ����ȭ
- �Ŵ��� ���̵忡 ó���� ����, �� ����, ���� �ȳ� 3�ܰ� ���� ���� �Ϸ� ��ư�� �߰��ߴ�.
- CompanionSession�� Firebase/Mock ����ҿ� prescriptionCollected, medicationGuidanceCompleted�� �߰��ϰ� ���� pharmacyCompleted�� �� ���� �Ϸ� �������� �����ߴ�.
- ���� ���� ��, ��ȣ�� ����Ʈ, �ǽð� ��ġ Ȯ�� ȭ�鿡�� �౹ ���� �ܰ� ����� �Բ� �����ϵ��� �����.
- ���� ������ ��ǰ ���� ����ȭ, �౹ ���� ����ȭ, OCR ���� �� �ļ� �����

## 168. 2026-06-19 ���� ��ȭ ����Ʈ ����ȭ
- �Ŵ��� ���̵� ���� ����Ʈ �Է¿� ��ǰ��, ���� ����, ���� ���� �ʵ带 �߰��ߴ�.
- sessionReports ���� ������ medicationName, medicationChangeSummary, medicationScheduleNote���� Ȯ���ߴ�.
- ���� ���� �󼼿� ��ȣ�� ����Ʈ�� ���� ��ȭ ���ǿ��� ����ȭ�� ���� ������ �Բ� �����ϵ��� �����.
- ���� ������ �౹ ���� ����ȭ�� OCR ��� ���� �� �ļ� �����.
## 169. 2026-06-19 ���� ���� ��� �� ��� �߰�
- ���� �ܰ��� `medicationSummary`�� ���� `SessionReport`�� ���ϴ� `MedicationComparisonDisplayHelper`�� �߰��ߴ�.
- ���� ���� �󼼿� ��ȣ�� ����Ʈ�� ���� ���ǿ� `�� ���`, `�߰� Ȯ��` ���� ������ OCR ���̵� ����/����/��Ȯ�� �帧�� ���� �����ְ� �ߴ�.
- ��ɼ����� üũ����Ʈ�� �౹/���� �׸��� �⺻ �� ��� �������� �����ߴ�.

## 170. 2026-06-19 �౹ �ȳ� �̴ϸʰ� �౹ ������ ǥ��
- BookingMeetingPointCatalog�� HospitalMapPreviewModel�� �߰��� ���� ��ġ ����, �ǽð� ��ġ Ȯ��, �Ŵ��� ���̵尡 ���� ���� �� �ȳ� ���� ��Ģ�� �����ϵ��� �����ߴ�.
- BookingLocationMapView�� Ȯ���� ���� ���� ��ġ�� �౹ �������� ���ÿ� ������ �� �ְ� �߰�, �ǽð� ��ġ Ȯ�� ȭ��� �Ŵ��� ���̵� �౹ ���ǿ� �̴ϸ��� �߰��ߴ�.
- ���� ������ ����/�౹�� ����ǥ ��� ���� ������ OCR/�ڵ� ��ǰ �񱳴�.

## 171. 2026-06-19 īī�� ����ǥ ����/�౹ ��Ŀ ����
- `kakaoRestApiKey`�� ������ ȯ�濡���� īī�� ���� REST API�� ������ �α� �౹�� ����ǥ�� ��ȸ�� �ǽð� ��ġ Ȯ�� ȭ��� �Ŵ��� ���̵� ������ ��Ŀ�� �Բ� ǥ���ϵ��� �����ߴ�.
- ����ǥ �˻��� `KakaoLocalPlaceSearchClient`�� �и��ϰ�, ȭ�� ��Ƽ��Ƽ���� ��ȸ ����� ���� ��Ŀ�� �ݿ��ϴ� �帧�� �����.
- Ű�� ���� ȯ�濡���� ���� ���� �� �ȳ� �̴ϸʰ� �ܺ� ���� fallback�� ������ ����� ������ �ʵ��� ó���ߴ�.
- ���� ������ OCR ��� ���� �񱳿� ��ǰ ���� �ڵ� �� ��Ģ ����ȭ��.

## 172. 2026-06-20 ��ǰ�� ���� ���� �� �� �߰�
- ���� �ܰ� `medicationSummary`�� ���� ����Ʈ `medicationName`�� ��ǰ�� ������� ���� ����, �߰� �ĺ�, ���� ��ϸ� �ִ� �׸��� �⺻ ���� �����ֵ��� �����ߴ�.
- ���� ���� �󼼿� ��ȣ�� ����Ʈ�� `��ǰ ��` ���� �߰��� OCR ���̵� ���� ��ȭ�� �뷫���� ���̸� �� ���� Ȯ���� �� �ְ� �ߴ�.
- ���� ������ OCR ��� �񱳿� ��ǰ ���� �ڵ� �Ǻ� ��Ģ ����ȭ��.

## 173. 2026-06-20 ���� ���� ���� �ڵ� �� ����
- ���� �ܰ� ���� �޸�� ���� ����Ʈ�� ���� ���� Ű���带 �Բ� ���� ����, �߰�, ���� ���¸� `��ǰ ��` �ٿ� ���� �����ֵ��� �����ߴ�.
- ��ǰ�� ��ȭ�� ��� ���� ������ �޶����� `���� ���� ����`�� ��Ȯ�� �ȳ��� ���̵��� �⺻ ��Ģ�� ������.
- ���� ������ OCR ��� �񱳿� ��ǰ ���� �ڵ� �Ǻ� ��Ģ ����ȭ��.

## 174. 2026-06-20 īī�� ����ǥ �˻� ĳ�� �߰�
- ����/�౹ ����ǥ �˻� ����� ����/����� �������� 6�ð� �޸� ĳ���� ���� ȭ�� �������̳� ���� ȭ�� �̵����� īī�� ���� REST ȣ���� �ݺ����� �ʵ��� �����ߴ�.
- Ű�� ���� ȯ�濡���� ������ �����ϰ� ĳ�� ���� fallback�� �����ȴ�.
- ���� ������ OCR ��� �񱳿� ��ǰ ���� �ڵ� �Ǻ� ��Ģ ����ȭ��.

## 175. 2026-06-20 ���� ���� �Ǵ� �Է� �߰�
- �Ŵ��� ���̵� ���� ����Ʈ�� ���� ���� �Ǵܰ� ���� �޸� �Է��� �߰��� ���� Ȯ���ڰ� ����, ����, ��Ȯ�� �ʿ並 ���� ������ �� �ְ� �ߴ�.
- SessionReport ���� ������ medicationComparisonDecision, medicationComparisonNote���� Ȯ���ϰ� Firebase/Mock ����ҿ� ������/����� ��ȸ ��θ� �Բ� �����.
- ���� ���� �󼼿� ��ȣ�� ����Ʈ�� ���� ���� �Ǵܰ� �޸� �Բ� �����ϵ��� �����ߴ�.
- ���� ������ OCR ��� �񱳿� ��ǰ ���� �ڵ� �Ǻ� ��Ģ ����ȭ��.

## 176. 2026-06-20 ���뷮 ���� �ڵ� �� ����
- ���� ���� �޸�� ���� ����Ʈ���� 1��, 2ĸ��, 500mg ���� ���뷮 ǥ���� ������ ����, �߰�, ���� ���¸� �Բ� ���ϵ��� �����ߴ�.
- ��ǰ�� ��ȭ�� ��� ���뷮 ���̰� ������ ���� ���� ������ ���뷮 ��Ȯ�� �ȳ��� ���̵��� �ڵ� �� ��Ģ�� ������.
- ���� ������ OCR ��� �񱳿� ��ǰ ���� �ڵ� �Ǻ� ��Ģ ����ȭ��.

## 177. 2026-06-20 ���� ���� �Ǵ� ����ġ ��� �߰�
- �ڵ� ���� �� ����� �Ŵ����� ������ ���� ���� �Ǵ��� �ٸ��� �ļ� �ȳ� �ٿ� ���� �Ǵ� ���� ����� �Բ� �����ϵ��� �����ߴ�.
- ���뷮 �񱳴� 1��, 2ĸ��, 500mg ���� ǥ������ ������ ����, �߰�, ���� ���¸� �ڵ����� ���Ѵ�.
- ���� ������ OCR ��� �񱳿� ��ǰ ���� �ڵ� �Ǻ� ��Ģ ����ȭ��.

## 178. 2026-06-20 �Ƚ� ä�� Ǫ�� �˸� �߰�
- `companionSessions` ������ �� `chatMessages`�� �߰��Ǹ� Cloud Functions�� �߽��ڸ� ������ �����ڿ��� FCM ������ �޽����� �������� �����ߴ�.
- ���� `BodeulFirebaseMessagingService`, `CompanionChatPushNotifier`, `CompanionChatPushContract`�� ���� �Ƚ� ä�� ȭ�� ���Ű� �ý��� �˸��� �и� ó���Ѵ�.
- �Ƚ� ä�� ȭ���� ���� ���� ���� ��ε�ĳ��Ʈ�� ��� �ٽ� �ҷ�����, �ٸ� ȭ�鿡���� ä�� ȭ������ �ٷ� ���� �˸��� ��쵵�� �����ߴ�.

## 179. 2026-06-20 �Ƚ� ä�� ���� ���� �߰�
- `companionSessions`�� ȯ��/��ȣ��/�Ŵ����� ������ ä�� Ȯ�� �ð��� �����ϰ�, ä�� ȭ���� �� �� �� ������ ���� �ð��� �����ϵ��� �����ߴ�.
- ���������� ���� �� �޽������� ��� ������ �ش� �ð� ���Ŀ� ä���� Ȯ������ �� `��� Ȯ��` ǥ�ð� �ٵ��� �����ߴ�.
- ��ɼ����� üũ����Ʈ�� �Ƚ� ä�� �׸񿡼� ���� ���¸� ���� �Ϸ� ������ �����ߴ�.
## 180. 2026-06-20 ��ġ ��� �ڵ� �˸� �߰�
- �Ŵ��� ���� ��ġ ���� ���񽺰� ������ �౹ ���� ���¸� �ڵ� �Ǻ��� `companionSessions.locationAlertStage`�� �ܰ������� �����ϵ��� �����ߴ�.
- `companionSessions`�� ��ġ �ڵ� �˸� �ܰ谡 �ٲ�� Cloud Functions�� ȯ��/��ȣ�ڿ��� FCM ������ �޽����� ������, ���� �ǽð� ��ġ Ȯ�� ȭ���� ���� ���� ���� ���� �ý��� �˸��� ��쵵�� �и� ó���ߴ�.
- ���� ������ ��� ��ġ �̷� �ð�ȭ, ÷��, ��ǥ ��� �˸� ��å ����ȭ��.
## 181. 2026-06-20 �ֱ� ��ġ �̵� Ÿ�Ӷ��� �߰�
- ȯ��/��ȣ�� �ǽð� ��ġ Ȯ�� ȭ��� ��ȣ�� ����Ʈ ī�忡 �ֱ� ��ġ �̵� 6���� ���� �������� �и��� �ð��� ��ġ �޸� ������� Ȯ���� �� �ְ� �����ߴ�.
- ���� �� �� ��� ��� �ֱ� �̵� �̷��� Ÿ�Ӷ��� ���·� �и��� ��� ��ġ �̷� Ȯ�� �帧�� �����ߴ�.
- ���� ������ ���� ����� �̷� �ð�ȭ�� ÷�δ�.
## 182. 2026-06-20 ���� ����� ��ġ �̷� �ð�ȭ �߰�
- ȯ��/��ȣ�� �ǽð� ��ġ Ȯ�� ȭ���� īī�� ������ �ֱ� ��ġ ���� ��ǥ�� ��μ����� �̾ �̵� �帧�� �Ѵ��� ���̰� �����ߴ�.
- �ֱ� ��ġ �̵� Ÿ�Ӷ��ΰ� ���� ��μ��� �Բ� ������ �ؽ�Ʈ Ȯ�ΰ� ���� Ȯ���� ���� ȭ�鿡�� �̾������� �����.
- ���� ������ ä�� ÷�ο� ��ġ �˸� � ��å ����ȭ��.


## 183. 2026-06-20 실시간 위치 이력과 안심 채팅 첨부 추가
- 안심 채팅에 이미지/PDF 단일 첨부를 추가하고, 채팅 화면에 첨부 선택, 미리보기, 선택 해제, 메시지 내부 첨부 열기 흐름을 붙였다.
- Firebase/Mock 저장소 모두 첨부 메타데이터를 같은 구조로 저장하게 맞췄고, Cloud Functions 알림 본문도 이미지/PDF/첨부 파일 기준으로 다르게 보내게 정리했다.
- Storage 규칙에 `companion-chat-attachments/{sessionId}/...` 경로를 추가해 세션 참여자만 읽기/쓰기 가능하게 배포했다.
- 최근 위치 이동 타임라인과 지도 경로형 위치 이력도 같은 턴에서 함께 반영했다.
## 184. 2026-06-20 안심 채팅 이미지 썸네일 미리보기 추가
- 안심 채팅 입력창에서 이미지 첨부를 고르면 전송 전에 썸네일을 바로 보여주고, PDF는 기존 미리보기/열기 흐름을 유지하도록 정리했다.
- 전송된 이미지 첨부도 채팅 말풍선 안에서 썸네일을 바로 보여주도록 확장했고, Firebase 다운로드 URL과 로컬 URI를 같은 로더에서 처리하게 맞췄다.
- 이미지 첨부는 시각적으로 먼저 확인하고, PDF는 버튼으로 여는 현재 운영 방식에 맞춰 첨부 UX를 구분했다.
## 185. 2026-06-20 안심 채팅 다건 첨부 지원 추가
- 안심 채팅에서 이미지/PDF 첨부를 최대 3개까지 한 번에 선택하고, 전송 전 목록에서 개별 미리보기와 개별 제거를 할 수 있게 확장했다.
- 채팅 메시지는 `attachments[]` 배열을 우선 저장하고, 기존 단일 `attachment` 필드도 첫 첨부 기준으로 함께 남겨 하위 호환을 유지했다.
- 채팅 말풍선에서도 첨부를 개별 행으로 렌더링하고, 이미지 썸네일과 파일 열기 버튼을 각각 제공하도록 정리했다.

## 186. 2026-06-20 위치 자동 알림 운영 기준 정리
- 자동 위치 알림은 세션 상태가 `만남 진행`, `대기 중`, `진료 진행`, `약국/결제 진행`일 때만 단계 판정을 하도록 제한했다.
- `동행 준비`, `취소`, `동행 완료` 상태에서는 병원/약국 근접 좌표가 들어와도 새 자동 알림 단계를 저장하지 않는다.
- 환자/보호자 실시간 위치 확인 화면과 보호자 리포트에 마지막 자동 위치 알림 단계와 시각을 함께 노출해, 푸시를 놓쳐도 화면에서 확인할 수 있게 정리했다.

## 187. 2026-06-20 리팩토링 로드맵과 GitHub 이슈 분리
- 현재 코드베이스에서 즉시 손대야 하는 리팩토링 범위를 `Functions`, `관리자 앱/저장소`, `Mock 저장소`, `관리자 웹` 4축으로 정리했다.
- `docs/planning/refactoring-roadmap-2026-06-20.md`에 우선순위, 분할 기준, 완료 기준, 제외 범위를 고정했다.
- 이 문서를 기준으로 추적용 상위 이슈와 실행용 하위 이슈를 GitHub에 등록해 다음 작업자가 바로 집을 수 있게 정리했다.
