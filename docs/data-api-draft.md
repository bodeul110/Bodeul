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
- `appointmentAt`
- `appointmentAtEpochMillis`
- `appointmentDateKey`
- `meetingPlace`
- `specialNotes`
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
- `createdAt`
- `updatedAt`

### SessionReport

- `id`
- `sessionId`
- `summary`
- `treatmentNotes`
- `medicationNotes`
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
