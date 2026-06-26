# 데이터 / API 초안

기준: 2026-04-15

이 문서는 현재 구현과 다음 서버 작업을 맞추기 위한 초안이다.

## 엔티티

### User

- `id`
- `role`: `PATIENT`, `GUARDIAN`, `MANAGER`, `ADMIN`
- `name`
- `email`
- `phone`
- `managerDocumentSummary` (선택, 매니저 전용)
- `managerAvailabilitySummary` (선택, 매니저 전용)

### AppointmentRequest

- `id`
- `patientUserId`
- `guardianUserId`
- `patientName`
- `patientPhone`
- `patientEmail`
- `guardianName`
- `guardianPhone`
- `guardianEmail`
- `hospitalName`
- `departmentName`
- `hospitalLatitude`
- `hospitalLongitude`
- `appointmentAt`
- `appointmentAtEpochMillis`
- `appointmentDateKey`
- `meetingPlace`
- `specialNotes`
- `patientConditionSummary`
- `medicationSummary`
- `mobilitySupportCode`
- `tripTypeCode`
- `managerGenderPreferenceCode`
- `paymentMethodCode`
- `couponCode`
- `basePrice`
- `optionSurchargePrice`
- `couponDiscountPrice`
- `finalPrice`
- `reminderStages`
- `status`: `REQUESTED`, `MATCHED`, `IN_PROGRESS`, `COMPLETED`, `CANCELED`
- `managerUserId`
- `requesterUserId`
- `requesterRole`
- `requesterName`
- `requesterPhone`
- `createdAt`
- `updatedAt`

설명:

- `patientUserId`, `guardianUserId`는 아직 계정이 연결되지 않았으면 빈 문자열일 수 있다.
- 대신 `patientName/Phone/Email`, `guardianName/Phone/Email`에는 신청 시점 입력값 또는 연결된 계정 정보가 항상 남도록 설계한다.
- 요청을 만든 환자 / 보호자는 `REQUESTED` 상태에서만 병원, 일정, 만남 장소, 연결 대상 정보를 수정할 수 있다.
- 요청 취소는 `REQUESTED`, `MATCHED` 상태에서만 가능하다.
- `MATCHED` 상태 요청을 취소하면 연결된 `companionSessions.currentStatus`도 `CANCELED`로 함께 갱신한다.
- `users` 문서가 생성되거나 연락처가 바뀌면 서버 트리거가 미연결 요청을 다시 찾아 `patientUserId` 또는 `guardianUserId`를 자동으로 채운다.
- 예약이 취소 / 삭제되거나 `appointmentAt`이 바뀌면 서버 트리거가 남아 있는 리마인더 작업을 `SKIPPED`로 정리한다.

### HospitalGuide

- `id`
- `hospitalName`
- `departmentName`
- `steps`

### CompanionSession

- `id`
- `appointmentRequestId`
- `managerUserId`
- `currentStepOrder`
- `currentStatus`: `READY`, `MEETING`, `WAITING`, `IN_TREATMENT`, `PAYMENT`, `CANCELED`, `COMPLETED`
- `guardianUpdate`
- `medicationNote`
- `pharmacySummary`
- `prescriptionCollected`
- `pharmacyCompleted`
- `medicationGuidanceCompleted`
- `sharedLatitude`
- `sharedLongitude`
- `sharedLocationUpdatedAtMillis`
- `liveLocationSharingActive`
- `liveLocationSharingStartedAt`
- `sharedLocationHistory`
- `createdAt`
- `updatedAt`

설명:

- `sharedLocationHistory`는 진행 중 위치 확인을 위한 최근 좌표 이력이며 세션당 최근 10건만 유지한다.
- `sharedLatitude`, `sharedLongitude`는 마지막 공유 좌표이고 `sharedLocationUpdatedAtMillis`는 마지막 갱신 시각이다.
- 위치 원본 이력은 장기 분석 데이터로 보관하지 않고, 보관 및 노출 기준은 [위치 이력 보관 및 노출 정책](../operations/location-history-retention-policy.md)을 따른다.

### SessionReport

- `id`
- `sessionId`
- `summary`
- `treatmentNotes`
- `medicationNotes`
- `medicationName`
- `medicationChangeSummary`
- `medicationScheduleNote`
- `nextVisitAt`
- `createdAt`

### AppointmentReminderJob

- `id`
- `appointmentRequestId`
- `reminderStage`: `D7`, `D3`, `D1`
- `templateKey`
- `channel`
- `state`
- `reminderDateKey`
- `appointmentDateKey`
- `recipientUserIds`
- `recipientPhones`
- `messagePreview`
- `deliveryAttempts`
- `skipReason`
- `providerResult`
- `createdAt`
- `updatedAt`
- `skippedAt`

설명:

- `appointmentRequests` 문서가 취소 / 삭제 / 일정 변경되면 아직 발송되지 않은 작업은 `SKIPPED` 상태로 정리한다.
- 이미 `SENT` 또는 `SIMULATED`로 끝난 작업은 이 정리 대상에서 제외한다.

## API 초안

- `POST /appointment-requests`
- `GET /appointment-requests/{id}`
- `PATCH /appointment-requests/{id}`
- `POST /appointment-requests/{id}/cancel`
- `PATCH /appointment-requests/{id}/status`
- `POST /appointment-requests/{id}/match`
- `GET /manager/sessions`
- `GET /sessions/{id}`
- `PATCH /sessions/{id}/progress`
- `POST /sessions/{id}/location-updates`
- `POST /sessions/{id}/report`
- `GET /guardian/sessions/{id}/report`
- `GET /hospital-guides?hospitalName=&departmentName=`
- `POST /admin/hospital-guides`
- `PATCH /admin/hospital-guides/{id}`
- `DELETE /admin/hospital-guides/{id}`
- `POST /admin/managers/{id}/document-review`

## 추가 메모

- `users` 문서에는 `managerDocumentStatus`, `managerDocumentReviewNote`가 함께 저장된다.
- 매니저가 서류 요약을 다시 저장하면 `managerDocumentStatus`는 `PENDING_REVIEW`로 재설정된다.
- 관리자는 `APPROVED`, `REJECTED` 두 상태만 직접 저장한다.
- `managerDocumentReviewNote`는 승인 메모 또는 보완 요청 사유를 담는다.
- 서류 요약을 저장하면 `managerDocumentUpdatedAt`이 갱신되고, 관리자가 검토를 저장하면 `managerDocumentReviewedAt`, `managerDocumentReviewedByName`이 함께 기록된다.
- `managerDocumentHistory` 배열에는 `eventType`, `happenedAt`, `actorName`, `summary`, `reviewNote`가 누적되어 관리자 검토 이력 다이얼로그에 바로 사용된다.
### 2026-04-22 예약 확장 메모

- 건강 프로필과 결제/쿠폰 정보는 요청 시점 스냅샷으로 함께 저장해, 매칭 이후에도 같은 기준으로 확인한다.
- `mobilitySupportCode`, `tripTypeCode`, `managerGenderPreferenceCode`, `paymentMethodCode`, `couponCode`는 앱 내부 열거형 이름을 그대로 저장한다.

### 2026-04-23 동행 진행 메모

- `companionSessions` 문서에는 `guardianUpdate`, `medicationNote` 외에 `locationSummary`, `fieldPhotoNote`를 함께 저장한다.
- `locationSummary`는 도착 위치와 이동 진행 상황을 짧게 공유하는 텍스트 스냅샷이다.
- `fieldPhotoNote`는 접수표, 안내문, 현장 사진 확인 메모를 텍스트로 정리한 값이다.

### 2026-04-23 예약 병원 선택/완료 메모

- 병원 선택 화면은 별도 컬렉션을 추가하지 않고 기존 `hospitalGuides` 문서를 병원명 기준으로 묶어 사용한다.
- 하나의 병원에 여러 진료과 가이드가 있으면 예약 화면에서는 병원 카드 하나로 보여주고, 선택 시 진료과 목록을 다시 제시한다.
- 예약 완료 화면은 별도 저장 없이 `appointmentRequests`에 이미 저장된 병원/일정/결제 스냅샷을 그대로 사용한다.

### 2026-04-23 예약 위치/결제 승인 메모

- 만남 위치는 별도 컬렉션을 저장하지 않고, 병원/진료과 기준 추천 후보 중 사용자가 선택한 `meetingPlace` 문자열만 `appointmentRequests`에 저장한다.
- 결제 승인 단계는 `paymentStatusCode`, `paymentApprovalCode`, `paymentApprovedAt`, `paymentProviderLabel` 네 필드를 요청 스냅샷으로 함께 저장한다.
- 카드/간편결제는 `AUTHORIZED`, 현장 결제는 `DEFERRED` 상태로 저장해 예약 상세와 완료 화면에서 같은 기준으로 표시한다.
### 2026-04-23 종료 후 후기/정산/SOS 메모

- 현재 앱은 완료된 예약의 후기, 정산 후속, SOS 기록을 `appointmentFollowUps` 저장소 흐름 기준으로 조회/저장한다.
- 서버 연동 시 초안 API:
  - `GET /appointments/{id}/follow-up`
  - 응답:
    - `reviewRatingCode`
    - `reviewSavedAt`
    - `settlementStatusCode`
    - `paymentApprovalCode`
    - `supportEscalationStatus`
    - `emergencyGuideVersion`
  - `POST /appointments/{id}/follow-up/review`
  - 본문:
    - `reviewRatingCode`
    - `reviewComment`
  - `POST /appointments/{id}/follow-up/emergency`
  - 본문:
    - `issueType`
    - `note`
    - `requestedCallback`
### 2026-04-23 매니저 과거 이력 / 문의하기 메모

- 매니저 과거 이력 화면은 저장소 기준으로 `List<AppointmentRequestDetail>`을 사용한다.
- Firebase 초안 조회 흐름:
  - `GET /managers/{id}/history`
  - 응답 항목:
    - `appointmentRequest`
    - `patient`
    - `guardian`
    - `session`
    - `sessionReport`
    - `hospitalGuide`
- 매니저 내 페이지는 `ManagerDocumentOverview` 기준으로 계정 정보, 서류 상태, 서류 검토 이력을 함께 내려준다.
- 문의하기는 현재 `ManagerSupportPreferences` 기반 로컬 저장이며, 서버 연동 시 초안 API는 아래와 같이 잡는다.
  - `GET /managers/{id}/support-inquiries`
  - 응답 항목:
    - `id`
    - `categoryCode`
    - `title`
    - `body`
    - `statusCode`
    - `createdAt`
    - `answeredAt`
    - `answerBody`
  - `POST /managers/{id}/support-inquiries`
  - 본문:
    - `categoryCode`
    - `title`
    - `body`
### 2026-04-23 관리자 운영 모니터링/정산 메모

- 관리자 운영 화면은 `AdminRequestOverview` 기준으로 `appointmentRequest`, `patient`, `guardian`, `manager`, `session`, `sessionReport`, `hasGuide`, `hasLinkedParticipants`를 함께 사용한다.
- Firebase 관리자 대시보드 초안 조회는 아래 컬렉션을 한 번에 조합하는 형태를 유지한다.
  - `appointmentRequests`
  - `users`
  - `companionSessions`
  - `sessionReports`
  - `hospitalGuides`
- 운영 모니터링 카드에서 바로 확인하는 필드:
  - `session.currentStatus`
  - `session.currentStepOrder`
  - `session.guardianUpdate`
  - `session.locationSummary`
  - `session.fieldPhotoNote`
  - `session.medicationNote`
- 정산 확인 카드에서 바로 확인하는 필드:
  - `appointmentRequest.finalPrice`
  - `appointmentRequest.paymentMethodCode`
  - `appointmentRequest.paymentStatusCode`
  - `appointmentRequest.paymentApprovalCode`
  - `appointmentRequest.paymentApprovedAt`
  - `sessionReport.nextVisitAt`
  - `sessionReport.summary`
- 후속 API 초안:
  - `POST /admin/appointments/{id}/settlement-actions`
  - 본문:
    - `actionCode`
    - `note`
    - `resolvedBy`
  - `POST /admin/appointments/{id}/monitoring-notes`
  - 본문:
    - `noteType`
    - `body`
    - `visibilityCode`
### 2026-04-23 관리자 요청 카드 조합 메모

- 관리자 요청 카드 모델은 `AdminRequestOverview` 기준으로 아래 필드를 그대로 사용한다.
  - `appointmentRequest`
  - `patient`
  - `guardian`
  - `manager`
  - `session`
  - `sessionReport`
  - `hasGuide`
  - `hasLinkedParticipants`
- `관리 중 요청` 필터는 현재 클라이언트에서 아래 기준으로 계산한다.
  - 상태 필터: `AppointmentStatus`
  - 날짜 필터: `appointmentRequest.appointmentAt`
- 추후 서버 집계 API가 들어오면 아래 응답 구조로 대체 가능하다.
  - `GET /admin/managed-requests/summary`
  - 응답 항목:
    - `matchedCount`
    - `inProgressCount`
    - `completedCount`
    - `canceledCount`
    - `todayCount`
    - `upcomingCount`
    - `pastCount`
### 2026-04-23 의존성/관리자 가이드 메모

- 빌드 의존성 버전은 `gradle/libs.versions.toml` 기준으로 중앙관리한다.
- 관리자 병원 가이드 UI는 현재 클라이언트에서 아래 모델 조합으로 동작한다.
  - `AdminGuideCardModel`
  - `AdminGuideFormModel`
  - `AdminGuideCoordinator`
- 병원 가이드 서버 계약은 기존과 동일하다.
  - 저장: `saveHospitalGuide(currentUser, hospitalName, departmentName, stepLines, callback)`
  - 삭제: `deleteHospitalGuide(currentUser, guideId, callback)`
- 추후 관리자 가이드 API가 분리되면 아래 응답 구조로 분리 가능하다.
  - `GET /admin/hospital-guides`
  - 응답 항목:
    - `id`
    - `hospitalName`
    - `departmentName`
    - `steps[]`
### 2026-04-23 관리자 서류 검토 메모

- 관리자 서류 검토 UI는 현재 `ManagerDocumentOverview` 기준으로 아래 필드를 그대로 사용한다.
  - `manager.id`
  - `manager.name`
  - `profile.documentSummary`
  - `profile.availabilitySummary`
  - `profile.documentStatus`
  - `profile.documentReviewNote`
  - `profile.documentUpdatedAtMillis`
  - `profile.documentReviewedAtMillis`
  - `profile.documentReviewedByName`
  - `historyEntries[].eventType`
  - `historyEntries[].happenedAtMillis`
  - `historyEntries[].actorName`
  - `historyEntries[].summary`
  - `historyEntries[].reviewNote`
- 관리자 화면에서 앞으로 서버 연동이 필요한 액션:
  - `reviewManagerDocument(currentUser, managerUserId, status, reviewNote, callback)`
  - 승인/보완 요청 이후 감사 로그 저장
  - 승인/보완 요청 이후 매니저 알림 발송
- 서버 API를 분리하면 아래 구조로 옮길 수 있다.
  - `POST /admin/managers/{id}/documents/reviews`
  - 본문:
    - `statusCode`
    - `reviewNote`
    - `reviewedBy`
  - `GET /admin/managers/{id}/documents/history`
  - 응답 항목:
    - `eventType`
    - `happenedAt`
    - `actorName`
    - `summary`
    - `reviewNote`
### 2026-04-23 관리자 후속 처리 / 문의 응답 연동 메모

- 문의 도메인은 `SupportInquiry` 단일 모델로 정리했고, 목업과 Firebase 저장소 모두 같은 구조를 사용한다.
- Firebase 기준 저장 컬렉션은 아래처럼 정리했다.
  - `supportInquiries`
  - `adminSettlementRecords`
  - `adminEmergencyIssues`
- 관리자 후속 처리 저장 필드 초안:
  - `adminSettlementRecords/{requestId}`
    - `statusCode`
    - `note`
    - `handledByName`
    - `handledAt`
  - `adminEmergencyIssues/{requestId}`
    - `statusCode`
    - `note`
    - `handledByName`
    - `handledAt`
  - `supportInquiries/{inquiryId}`
    - `managerUserId`
    - `managerName`
    - `categoryCode`
    - `title`
    - `body`
    - `statusCode`
    - `createdAt`
    - `responseText`
    - `respondedAt`
    - `respondedByName`
- 현재 앱 계약 기준 메서드:
  - `GET /managers/{id}/support-inquiries`
  - `POST /managers/{id}/support-inquiries`
- `POST /admin/appointments/{id}/settlement-actions`
- `POST /admin/appointments/{id}/emergency-issues`
- `POST /admin/support-inquiries/{id}/response`

### 2026-06-19 이용자 문의 접수 분리 메모

- 이용자 문의는 매니저 전용 `supportInquiries`를 재사용하지 않고 `clientSupportRequests` 컬렉션으로 분리한다.
- 현재 앱 계약 메서드:
  - `GET /clients/{id}/support-requests`
  - `POST /clients/{id}/support-requests`
- 저장 필드 초안:
  - `userId`
  - `userName`
  - `userRole`
  - `appointmentRequestId`
  - `category`
  - `title`
  - `body`
  - `status`
  - `createdAt`
  - `responseText`
  - `respondedAt`
  - `respondedByName`
- 관리자 응답 화면 통합은 후속 범위로 남긴다.

### 2026-04-23 예약 후속 후기 저장 연동 메모

- 예약 후속 후기는 `AppointmentFollowUpRecord`로 정리했고, 현재 저장 필드는 아래와 같다.
  - `requestId`
  - `reviewRatingCode`
  - `reviewSavedAt`
- Firebase 저장 컬렉션은 `appointmentFollowUps`를 사용한다.
  - 문서 키: `{requestId}`
  - 저장 필드:
    - `requestId`
    - `reviewRatingCode`
    - `reviewSavedByUserId`
    - `reviewSavedAt`
    - `updatedAt`
- 현재 앱 계약 기준 메서드:
  - `GET /appointments/{id}/follow-up`
  - `POST /appointments/{id}/follow-up/review`
- 다음 확장 후보:
  - `supportEscalationStatus`
  - `supportEscalatedAt`
  - `settlementFollowUpStatus`
  - `settlementFollowUpNote`
### 2026-04-23 예약 후속 정산/SOS 저장 연동 메모

- 예약 후속 저장 필드는 이제 아래처럼 확장한다.
  - `requestId`
  - `reviewRatingCode`
  - `reviewSavedAt`
  - `settlementFollowUpStatus`
  - `settlementFollowUpNote`
  - `settlementFollowUpSavedAt`
  - `supportEscalationStatus`
  - `supportEscalatedAt`
- Firebase 저장 문서는 계속 `appointmentFollowUps/{requestId}`를 사용하고, 화면별 부분 저장은 `merge` 방식으로 누적한다.
  - 후기 저장: `reviewRatingCode`, `reviewSavedByUserId`, `reviewSavedAt`
  - 정산 저장: `settlementFollowUpStatus`, `settlementFollowUpNote`, `settlementFollowUpSavedByUserId`, `settlementFollowUpSavedAt`
  - SOS 저장: `supportEscalationStatus`, `supportEscalatedByUserId`, `supportEscalatedAt`
- 현재 앱 기준 저장 계약 메서드는 아래와 같다.
  - `GET /appointments/{id}/follow-up`
  - `POST /appointments/{id}/follow-up/review`
  - `POST /appointments/{id}/follow-up/settlement`
  - `POST /appointments/{id}/follow-up/support-escalation`
- 관리자 운영 화면은 `GET /admin/appointments/actions` 혹은 기존 대시보드 응답 안에 사용자 후속 상태를 포함해 내려주는 구조가 필요하다.

### 2026-04-23 관리자 운영 필터/우선순위 메모

- 현재 관리자 운영 화면의 필터는 클라이언트 조합 기준이다.
  - 모니터링 필터: `ALL`, `EMERGENCY`, `PAYMENT`, `MATCHED`, `IN_PROGRESS`
  - 정산 필터: `ALL`, `USER_HELP`, `ADMIN_PENDING`, `NEEDS_REVIEW`, `CONFIRMED`
- 클라이언트 우선순위 규칙은 아래와 같다.
  - 모니터링: `긴급 이슈 보고` > `수납 단계` > `현장 진행` > `매칭 완료`
  - 정산: `SOS 연락 시도/119` > `정산 문의 필요` > `관리자 재확인` > `관리자 미처리` > `확인 완료`
- 서버 API로 끌어올릴 때는 `GET /admin/appointments/actions` 응답에 아래 파생 필드를 포함하는 방식을 권장한다.
  - `monitoringPriority`
  - `settlementPriority`
  - `monitoringFilterKeys[]`
  - `settlementFilterKeys[]`
  - `latestActionSummary`
- 이렇게 내려주면 클라이언트와 관리자 백오피스가 같은 우선순위 규칙을 공유할 수 있다.

### 2026-04-23 매니저 과거 이력 후속 데이터 메모

- 매니저 `과거 동행 이력` 화면은 완료된 요청 상세를 조합할 때 후속 데이터까지 함께 받아야 한다.
  - 권장 응답 구조: `AppointmentRequestDetail.followUpRecord`
  - 포함 필드: `reviewRatingCode`, `reviewSavedAt`, `settlementFollowUpStatus`, `settlementFollowUpNote`, `settlementFollowUpSavedAt`, `supportEscalationStatus`, `supportEscalatedAt`
- Firebase 기준으로는 기존 `appointmentFollowUps/{requestId}` 문서를 그대로 읽고, 요청 상세 조합 시 `requestId` 기준으로 1:1 매핑하면 된다.
- 서버 API로 정리할 때는 매니저 전용 이력 목록 응답에 아래 수준의 파생 값도 함께 내려주는 편이 안전하다.
  - `followUpSummaryLabel`
  - `latestFollowUpLabel`
  - `latestFollowUpAt`
  - `hasPendingFollowUp`
- 이렇게 맞추면 매니저 앱, 관리자 운영 화면, 백오피스 리포트가 같은 후속 상태 해석 규칙을 공유할 수 있다.

### 2026-04-23 관리자 후속 알림/감사 로그 메모

- 관리자 후속 처리 저장 시 아래 두 컬렉션을 함께 유지하는 구조를 권장한다.
  - `adminActionNotifications/{notificationId}`
  - `adminAuditLogs/{auditLogId}`
- 후속 알림 문서 권장 필드는 아래와 같다.
  - `sourceType`
  - `level`
  - `requestId`
  - `inquiryId`
  - `title`
  - `body`
  - `actorName`
  - `createdAt`
- 감사 로그 문서 권장 필드는 아래와 같다.
  - `sourceType`
  - `requestId`
  - `inquiryId`
  - `actionSummary`
  - `note`
  - `actorName`
  - `createdAt`
- 서버 API로 승격할 때는 관리자 대시보드 응답에 아래 항목을 포함하는 방식이 안전하다.
  - `actionNotifications[]`
  - `auditLogs[]`
  - `latestActionNotification`
  - `latestAuditLog`

### 2026-04-23 관리자 후속 알림 상태 필드 메모

- 관리자 후속 알림 문서는 읽음/해결 상태를 직접 담도록 확장했다.
  - `isRead`
  - `readAt`
  - `isResolved`
  - `resolvedAt`
  - `resolvedByName`
- 클라이언트 액션은 우선 아래 두 개를 기준으로 맞췄다.
  - `POST /admin/action-notifications/{id}/read`
  - `POST /admin/action-notifications/{id}/resolve`
- `resolve`는 요청 본문에 `resolved: true|false`를 받아 해결 완료와 재오픈을 같은 계약으로 처리하는 구조가 단순하다.
- 감사 로그는 알림 상태 변경도 별도 엔트리로 남긴다.
  - `actionSummary`: `후속 알림 해결 처리` 또는 `후속 알림 다시 열기`
  - `note`: 운영자가 남긴 상태 전이 설명

### 2026-04-23 관리자 후속 알림 서버 계약 메모

- 후속 알림 문서에는 상태 토글용 보조 필드 외에 서버 필터와 우선순위 계산 결과도 함께 저장한다.
  - `state`: `unread | read | resolved`
  - `priority`: `immediate | action_required | monitoring | archived`
  - `filterKeys[]`: `unread`, `unresolved`, `resolved`
- 생성/상태 변경 시 클라이언트와 서버가 같은 규칙을 쓰도록 `AdminActionNotificationContract`와 같은 계산 규칙을 API 문서에도 고정해야 한다.
- 현재 앱 기준 규칙은 아래와 같다.
  - `resolved`면 `state=resolved`, `priority=archived`, `filterKeys=[resolved]`
  - `read=true && resolved=false`면 `state=read`, `priority=monitoring`, `filterKeys=[unresolved]`
  - `read=false && resolved=false`면서 `sourceType=EMERGENCY` 또는 `level=WARNING`이면 `priority=immediate`
  - 나머지 미확인 알림은 `priority=action_required`, `filterKeys=[unread, unresolved]`
- 서버 조회 초안은 아래 형태로 잡는다.
  - `GET /admin/action-notifications?filterKey=unresolved`
  - `GET /admin/action-notifications?filterKey=unread`
  - 응답 정렬 기본값: `priority desc, createdAt desc`
### 2026-04-23 관리자 후속 알림 전달 기록 메모

- 후속 알림 전달 상태는 `adminActionNotifications`와 분리된 `adminActionDeliveries/{deliveryId}` 컬렉션으로 관리한다.
- 앱 푸시 채널의 실제 발송 작업은 `adminActionDeliveryJobs/{jobId}` 큐 문서로 분리하고, 작업 결과가 다시 `adminActionDeliveries/{deliveryId}`를 갱신하는 구조로 정리한다.
- 권장 필드는 아래와 같다.
  - `notificationId`
  - `sourceType`
  - `trigger`: `notification_created | notification_read | notification_resolved | notification_reopened`
  - `channel`: `app_push | operations_feed`
  - `status`: `sent | confirmed | skipped | failed`
  - `state`: `pending_confirmation | follow_up_required | delivered | skipped`
  - `priority`: `immediate | action_required | monitoring | archived`
  - `filterKeys[]`: `pending_confirmation | follow_up_required | completed`
  - `slaStatus`: `on_track | attention_required | completed`
  - `attemptCount`
  - `maxAttemptCount`
  - `requestId`
  - `inquiryId`
  - `title`
  - `body`
  - `targetLabel`
  - `note`
  - `confirmedAt`
  - `nextRetryAt`
  - `slaDueAt`
  - `createdAt`
  - `processedAt`
- 앱 푸시 채널은 알림 생성/재오픈 시 `status=sent`로 남기고, 읽음 처리 시점에는 `status=confirmed` 전달 기록을 추가해 읽음 확인 이력을 별도 엔트리로 남긴다.
- 운영 피드 채널은 화면 반영이 끝난 시점을 `status=confirmed`로 기록해, 푸시 대기 상태와 운영 피드 반영 완료를 같은 목록에서 구분할 수 있게 한다.
- 해결 완료처럼 추가 푸시가 필요 없는 경우에도 `status=skipped` 전달 기록을 남겨서 운영 이력이 끊기지 않게 한다.
- 서버와 클라이언트는 `AdminActionDeliveryContract`와 같은 규칙으로 아래 파생 값을 맞춘다.
  - `APP_PUSH + SENT`이고 `slaDueAt` 이전이면 `state=pending_confirmation`
  - `APP_PUSH + SENT`인데 `slaDueAt`이 지났거나 `FAILED`면 `state=follow_up_required`
  - `CONFIRMED` 또는 `OPERATIONS_FEED + SENT/CONFIRMED`는 `state=delivered`
  - `SKIPPED`는 `state=skipped`, `priority=archived`
- `FAILED`면서 `attemptCount < maxAttemptCount`면 `nextRetryAt`을 채우고, 대시보드와 백오피스는 이를 재시도 예정 시간으로 함께 노출한다.
- 관리자 대시보드 응답은 `actionNotifications[]`, `auditLogs[]`와 함께 `actionDeliveries[]`를 포함하고, 기본 정렬은 `processedAt desc, createdAt desc`가 안전하다.
- 실제 조회 계약은 아래처럼 고정하는 편이 안전하다.
  - `GET /admin/action-deliveries?filterKey=pending_confirmation`
  - `GET /admin/action-deliveries?filterKey=follow_up_required`
  - `GET /admin/action-deliveries?notificationId={id}`
  - 기본 정렬: `priority desc, processedAt desc`
- 전달 큐 문서 초안은 아래처럼 둔다.
  - `deliveryId`
  - `notificationId`
  - `sourceType`
  - `trigger`
  - `channel`
  - `recipientRole`
  - `recipientUserIds[]`
  - `messagePreview`
  - `state`: `PENDING | PROCESSING | SENT | SIMULATED | SKIPPED | FAILED`
  - `deliveryAttempts`
  - `maxAttempts`
  - `lastDeliverySource`
  - `lastError`
  - `queuedAt`
  - `claimedAt`
  - `sentAt`
  - `simulatedAt`
  - `skippedAt`
  - `failedAt`
- Functions 기준 엔트리는 아래처럼 고정한다.
  - `deliverAdminActionDeliveryJobs` 스케줄러
  - `dispatchAdminActionDeliveryJobs` callable

### 2026-04-24 관리자 액션센터/전달 기록 공용 응답 모델 메모

- 관리자 대시보드 응답에는 액션센터와 전달 기록 섹션이 함께 참조하는 `actionOverview`를 추가하는 구성이 안전하다.
- 권장 필드는 아래와 같다.
  - `notificationCount`
  - `unreadNotificationCount`
  - `unresolvedNotificationCount`
  - `resolvedNotificationCount`
  - `auditLogCount`
  - `deliveryCount`
  - `pendingDeliveryCount`
  - `followUpDeliveryCount`
  - `completedDeliveryCount`
  - `appPushDeliveryCount`
  - `operationsFeedDeliveryCount`
- 정렬 계약도 대시보드 응답 기준으로 같이 고정한다.
  - `actionNotifications[]`: `priority desc, createdAt desc`
  - `auditLogs[]`: `createdAt desc`
  - `actionDeliveries[]`: `priority desc, max(processedAt, createdAt) desc`
- 클라이언트는 액션센터 필터 칩 카운트와 전달 기록 요약 문구를 위 `actionOverview`만 보고 렌더링하고, 개별 카드 렌더링에만 원본 목록을 사용하면 저장소 구현이 바뀌어도 같은 운영 기준을 유지하기 쉽다.

### 2026-05-04 관리자 서류 Storage 메모

- `users` 문서에는 선택적으로 `managerDocumentFiles` 맵을 둘 수 있다.
- 권장 구조는 아래와 같다.
  - `managerDocumentFiles.idCard.fullPath`
  - `managerDocumentFiles.idCard.fileName`
  - `managerDocumentFiles.idCard.contentType`
  - `managerDocumentFiles.idCard.uploadedAt`
  - `managerDocumentFiles.license.*`
  - `managerDocumentFiles.healthCertificate.*`
  - `managerDocumentFiles.criminalRecord.*`
- 관리자 웹은 `managerDocumentFiles`가 있으면 해당 `fullPath`를 우선 읽고, 없으면 `manager-documents/{managerUserId}/{documentKey}/{fileName}` Storage 폴더를 탐색한다.
- 현재 관리자 웹은 `idCard`, `criminalRecord`를 그대로 사용하고, `자격증` 슬롯에서는 `license`, `healthCertificate`를 함께 해석한다.
- 현재 매니저 앱은 실제 Storage 업로드 후 같은 `users/{uid}` 문서에 `managerDocumentFiles` 메타데이터를 함께 저장한다.
### 2026-05-04 매니저 앱 서류 업로드 반영 메모

- 매니저 앱 내 페이지에서 `원본 파일 업로드` 버튼으로 `application/pdf`, `image/*` 파일을 선택해 바로 Storage 업로드를 시작한다.
- 업로드 성공 후 `users/{uid}` 문서에는 아래 필드를 함께 저장한다.
  - `managerDocumentFiles.{documentKey}.fullPath`
  - `managerDocumentFiles.{documentKey}.fileName`
  - `managerDocumentFiles.{documentKey}.contentType`
  - `managerDocumentFiles.{documentKey}.uploadedAt`
  - `managerDocumentFilePaths.{documentKey}`
  - 레거시 호환 경로: `managerIdCardStoragePath`, `managerLicenseStoragePath`, `managerCriminalRecordStoragePath`
- 업로드 후에는 `managerDocumentStatus=PENDING_REVIEW`, `managerDocumentReviewNote=""`, `managerDocumentReviewedAt` 삭제, `managerDocumentReviewedByName=""`, `managerDocumentUpdatedAt` 갱신으로 심사 상태를 다시 대기 상태로 돌린다.
- 업로드 전제 조건은 `managerDocumentSummary`가 비어 있지 않은 상태다. 요약이 없으면 앱과 저장소 모두 업로드 메타데이터 저장을 거부한다.

### 2026-06-19 사용자 문의 관리자 통합

- `AdminDashboard`에 `clientSupportRequests`를 포함해 관리자 문의 응답 섹션에서 매니저 문의와 함께 정렬한다.
- 관리자 응답 저장 시 `clientSupportRequests/{id}` 문서의 `status`, `responseText`, `respondedByName`, `respondedAt`를 갱신한다.
- 응답 후 관리자 액션 아티팩트는 기존 문의 응답 흐름과 같은 기준으로 남긴다.

### 2026-06-24 병원 지도/검색 고도화 및 Fallback 메모

- 예약 시 병원 검색은 카카오 로컬 REST API를 우선 사용하며, 병원 검색 범주는 `HP8`, 약국 검색 범주는 `PM9`를 사용한다.
- 선택 시 실좌표는 `appointmentRequests.hospitalLatitude`, `appointmentRequests.hospitalLongitude`에 저장한다.
- 카카오 REST API 키가 없거나 통신/검색에 실패하면 앱 크래시 없이 `hospitalGuides`에 등록된 병원명 기반 검색 결과로 대체한다.
- 카카오 병원 결과가 관리자 가이드의 병원명과 정확히 일치하면 해당 `hospitalName`과 `departmentName` 목록을 연결한다.
- 카카오 병원 결과가 있지만 일치하는 진료과가 없거나 검색 결과가 없으면 병원명/진료과 직접 입력을 허용한다.
- 좌표가 없는 직접 입력 또는 로컬 가이드 fallback 선택은 `hospitalLatitude=0.0`, `hospitalLongitude=0.0`으로 저장한다.
- 관리자 가이드는 `hospitalGuides.hospitalName`과 `hospitalGuides.departmentName` 조합으로 연결한다.
- 연결되는 가이드가 없으면 공통 기본 병원 동행 스텝을 노출해 매니저/보호자 상세 화면이 중단되지 않게 한다.
