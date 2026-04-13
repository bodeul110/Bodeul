# 데이터 및 API 초안

이 문서는 팀 논의를 위한 초안입니다. 최종 구현이 달라지더라도 아래 엔티티를 기준으로 백엔드를 시작할 수 있습니다.

## 핵심 엔티티

### User

- `id`
- `role`: `PATIENT`, `GUARDIAN`, `MANAGER`, `ADMIN`
- `name`
- `email`
- `phone`

### PatientProfile

- `id`
- `userId`
- `birthYear`
- `notes`
- `guardianUserId`

### ManagerProfile

- `id`
- `userId`
- `serviceArea`
- `certifications`
- `available`

### AppointmentRequest

- `id`
- `patientUserId`
- `guardianUserId`
- `hospitalName`
- `departmentName`
- `appointmentAt`
- `meetingPlace`
- `specialNotes`
- `status`: `REQUESTED`, `MATCHED`, `IN_PROGRESS`, `COMPLETED`, `CANCELED`
- `managerUserId`

### HospitalGuide

- `id`
- `hospitalName`
- `departmentName`
- `title`
- `steps`

### CompanionSession

- `id`
- `appointmentRequestId`
- `managerUserId`
- `currentStep`
- `currentStatus`
- `guardianUpdate`
- `medicationNote`
- `startedAt`
- `completedAt`

### LocationUpdate

- `id`
- `sessionId`
- `latitude`
- `longitude`
- `label`
- `createdAt`

### SessionReport

- `id`
- `sessionId`
- `summary`
- `treatmentNotes`
- `medicationNotes`
- `nextVisitAt`
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
