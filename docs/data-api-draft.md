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
- `users` 문서가 생성되거나 연락처가 바뀌면 서버 트리거가 미연결 요청을 다시 찾아 `patientUserId` 또는 `guardianUserId`를 자동으로 채운다.

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
- `currentStatus`
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

## API 초안

- `POST /appointment-requests`
- `GET /appointment-requests/{id}`
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
