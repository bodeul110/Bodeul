# Firebase 설정

기준일: 2026-04-15

## 현재 프로젝트 상태

- Android 패키지명: `com.example.bodeul`
- Firebase 설정 파일 위치: `app/google-services.json`
- 인증: Firebase Authentication
- 데이터 저장소: Cloud Firestore
- Functions: `functions/index.js`
- Firebase 설정이 없으면 앱은 자동으로 목업 모드로 동작한다.
- 최신 기능설명서 기준으로 예약 후속, 문의, 관리자 후속 알림/전달 기록 컬렉션까지 확장 중이다.

## 소셜 로그인 로컬 설정

민감한 키는 `local.properties`에만 넣는다.

```properties
naverClientId=발급받은_클라이언트_ID
naverClientSecret=발급받은_클라이언트_시크릿
naverClientName=보들
kakaoNativeAppKey=발급받은_네이티브_앱_키
```

## 콘솔에서 먼저 할 일

1. Firebase 프로젝트 생성
2. Android 앱 등록
3. `com.example.bodeul` 패키지명으로 SHA-1, SHA-256 등록
4. `app/google-services.json` 배치
5. Authentication의 `Email/Password` 활성화
6. Firestore 생성
7. `firestore.rules`, `firestore.indexes.json` 배포

## 현재 쓰는 컬렉션

### `users`

```json
{
  "name": "김보들",
  "email": "manager@bodeul.app",
  "phone": "010-0000-0003",
  "role": "MANAGER",
  "managerDocumentSummary": "요양보호사 자격증, 신분증, 통장사본 제출 완료",
  "managerAvailabilitySummary": "평일 09:00-18:00 활동 가능"
}
```

### `appointmentRequests`

```json
{
  "patientUserId": "patient-uid",
  "guardianUserId": "guardian-uid",
  "hospitalName": "서울안과병원",
  "departmentName": "안과",
  "appointmentAt": "2026-04-22 10:30",
  "appointmentAtEpochMillis": 1776811800000,
  "appointmentDateKey": "2026-04-22",
  "meetingPlace": "본관 1층 로비",
  "specialNotes": "신분증과 복용 약 정보를 확인해 주세요.",
  "reminderStages": ["D7", "D3", "D1"],
  "status": "REQUESTED",
  "managerUserId": null
}
```

### `companionSessions`

```json
{
  "appointmentRequestId": "request-doc-id",
  "managerUserId": "manager-uid",
  "currentStepOrder": 2,
  "currentStatus": "MEETING",
  "guardianUpdate": "환자분을 만나 병원으로 이동 중입니다.",
  "locationSummary": "본관 접수처로 이동 중입니다.",
  "fieldPhotoNote": "접수표 확인 사진 업로드 예정입니다.",
  "medicationNote": "처방전 수령 예정입니다."
}
```

### `hospitalGuides`

```json
{
  "hospitalName": "서울안과병원",
  "departmentName": "안과",
  "steps": [
    {
      "order": 1,
      "title": "환자 확인",
      "description": "환자와 보호자 정보를 먼저 확인합니다."
    },
    {
      "order": 2,
      "title": "접수 진행",
      "description": "접수 창구에서 예약 정보를 확인합니다."
    }
  ]
}
```

### `sessionReports`

```json
{
  "sessionId": "session-doc-id",
  "summary": "진료 요약",
  "treatmentNotes": "진료 메모",
  "medicationNotes": "복약 메모",
  "nextVisitAt": "2026-04-29 10:00"
}
```

### `appointmentFollowUps`

```json
{
  "requestId": "request-doc-id",
  "reviewRatingCode": "SATISFIED",
  "reviewSavedAt": "2026-04-23T13:20:00Z",
  "settlementFollowUpStatus": "CONFIRMED",
  "settlementFollowUpNote": "현장 결제 확인 완료",
  "settlementFollowUpSavedAt": "2026-04-23T13:25:00Z",
  "supportEscalationStatus": "NONE",
  "supportEscalatedAt": null
}
```

### `supportInquiries`

```json
{
  "managerUserId": "manager-uid",
  "managerName": "김보들",
  "categoryCode": "PAYMENT",
  "title": "정산 문의",
  "body": "출금 신청 가능 시점을 확인하고 싶습니다.",
  "statusCode": "ANSWERED",
  "createdAt": "2026-04-23T09:00:00Z",
  "responseText": "다음 영업일에 확인 가능합니다.",
  "respondedAt": "2026-04-23T11:00:00Z",
  "respondedByName": "운영 관리자"
}
```

### `adminSettlementRecords`

```json
{
  "requestId": "request-doc-id",
  "statusCode": "CONFIRMED",
  "note": "사용자 확인 완료",
  "handledByName": "운영 관리자",
  "handledAt": "2026-04-23T13:40:00Z"
}
```

### `adminEmergencyIssues`

```json
{
  "requestId": "request-doc-id",
  "statusCode": "MONITORING",
  "note": "현장 연락 유지 중",
  "handledByName": "운영 관리자",
  "handledAt": "2026-04-23T13:45:00Z"
}
```

### `adminActionNotifications`

```json
{
  "sourceType": "EMERGENCY",
  "level": "WARNING",
  "requestId": "request-doc-id",
  "title": "긴급 이슈 확인 필요",
  "body": "관리자 확인이 필요한 긴급 후속 알림입니다.",
  "state": "unread",
  "priority": "immediate",
  "filterKeys": ["unread", "unresolved"],
  "createdAt": "2026-04-23T13:45:00Z"
}
```

### `adminAuditLogs`

```json
{
  "sourceType": "SETTLEMENT",
  "requestId": "request-doc-id",
  "actionSummary": "정산 후속 확인 저장",
  "note": "사용자 문의 확인 후 완료 처리",
  "actorName": "운영 관리자",
  "createdAt": "2026-04-23T13:50:00Z"
}
```

### `adminActionDeliveries`

```json
{
  "notificationId": "notification-doc-id",
  "sourceType": "EMERGENCY",
  "trigger": "notification_created",
  "channel": "operations_feed",
  "status": "confirmed",
  "state": "delivered",
  "priority": "monitoring",
  "filterKeys": ["completed"],
  "slaStatus": "completed",
  "attemptCount": 1,
  "maxAttemptCount": 1,
  "requestId": "request-doc-id",
  "title": "긴급 이슈 확인 필요",
  "body": "관리자 운영 피드에 노출",
  "note": "후속 알림 전달 기록",
  "confirmedAt": 1776951901000,
  "slaDueAt": 1776951901000,
  "createdAt": "2026-04-23T13:45:00Z",
  "processedAt": "2026-04-23T13:45:01Z"
}
```

### `adminActionDeliveryJobs`

```json
{
  "deliveryId": "delivery-doc-id",
  "notificationId": "notification-doc-id",
  "sourceType": "EMERGENCY",
  "trigger": "notification_created",
  "channel": "app_push",
  "recipientRole": "ADMIN",
  "recipientUserIds": [],
  "messagePreview": "긴급 이슈 확인 필요 - 관리자 확인이 필요한 긴급 후속 알림입니다.",
  "state": "PENDING",
  "deliveryAttempts": 0,
  "maxAttempts": 3,
  "lastDeliverySource": "",
  "lastError": "",
  "queuedAt": "2026-04-24T09:15:00Z",
  "updatedAt": "2026-04-24T09:15:00Z"
}
```

## D-7 / D-3 / D-1 알림 준비

현재는 `Cloud Functions`가 세 단계로 동작한다.

1. 매일 오전 9시에 `appointmentRequests`를 읽고 `appointmentReminderJobs` 작업 문서를 생성
2. 10분마다 `appointmentReminderJobs`를 읽고 재검증 후 발송 또는 시뮬레이션 처리
3. `appointmentRequests`가 취소 / 삭제 / 일정 변경되면 남아 있는 알림 작업을 즉시 `SKIPPED` 처리

### `appointmentReminderJobs`

```json
{
  "appointmentRequestId": "request-doc-id",
  "reminderStage": "D3",
  "templateKey": "appointment_d3",
  "channel": "KAKAO_ALIMTALK",
  "state": "PENDING",
  "reminderDateKey": "2026-04-19",
  "appointmentDateKey": "2026-04-22",
  "recipientUserIds": ["patient-uid", "guardian-uid"],
  "messagePreview": "서울안과병원 안과 예약이 3일 남았습니다. 보호자 연락처, 만남 장소, 이동 경로를 다시 확인해 주세요."
}
```

현재 단계에서는 작업 문서 생성, 큐 처리, 실제 발송 또는 시뮬레이션 기록, 예약 변경 시 정리까지 구현되어 있다.

### 상태 전이

- `PENDING`: 발송 대기
- `PROCESSING`: 워커가 선점한 상태
- `SENT`: 실제 발송 완료
- `SIMULATED`: 연동값이 없어 데모 발송으로 처리
- `SKIPPED`: 일정 변경, 취소, 수신 번호 없음 등으로 건너뜀
- `FAILED`: 발송 오류, 재시도 대상

### 환경 변수

실제 발송을 붙이려면 Functions 환경 변수에 아래 값을 넣는다.

```properties
KAKAO_ALIMTALK_ENDPOINT=https://your-provider.example.com/messages
KAKAO_ALIMTALK_API_KEY=발급받은_API_KEY
KAKAO_ALIMTALK_SENDER_KEY=발급받은_발신프로필키
KAKAO_ALIMTALK_AUTH_SCHEME=Bearer
```

- 값이 모두 없으면 알림은 `SIMULATED` 상태로 처리된다.
- 현재 payload는 공통 JSON 어댑터 형태라, 실제 대행사 스펙에 맞춰 필드명만 마지막에 조정하면 된다.

## 관리자 후속 알림 푸시 큐

관리자 후속 알림 푸시는 `adminActionDeliveryJobs` 큐를 통해 처리한다.

1. 앱이 `adminActionNotifications`, `adminActionDeliveries`, `adminActionDeliveryJobs`를 함께 저장
2. `deliverAdminActionDeliveryJobs`가 5분마다 `PENDING`, `FAILED` 작업을 선점
3. 연동값이 없으면 `SIMULATED`, 있으면 `SENT`, 수신 관리자 없음이면 `SKIPPED`, 오류면 `FAILED`
4. Functions가 작업 결과를 다시 `adminActionDeliveries`에 반영
5. 읽음 처리 시점에는 앱이 별도 `confirmed` 전달 기록을 남겨 SLA를 종료

### 환경 변수

```properties
ADMIN_PUSH_ENDPOINT=https://your-provider.example.com/admin-push
ADMIN_PUSH_API_KEY=발급받은_API_KEY
ADMIN_PUSH_AUTH_SCHEME=Bearer
```

- 값이 없으면 관리자 푸시는 `SIMULATED`로 처리되고 전달 기록 메모에 시뮬레이션 문구를 남긴다.
- 현재 payload는 `title`, `body`, `recipients[]`, `metadata` 공통 JSON 어댑터 형태다.

## 연동 순서

1. 앱에서 예약 생성
2. `REQUESTED` 상태에서는 앱에서 같은 요청을 수정하거나 취소 가능
3. `MATCHED` 상태에서는 수정은 막고 취소만 허용
4. `MATCHED` 요청을 취소하면 연결된 `companionSessions.currentStatus`도 `CANCELED`로 정리
5. Firestore에 `appointmentRequests` 저장 또는 수정
6. 매일 오전 9시 `syncAppointmentReminderJobs` 실행
7. 조건에 맞는 요청의 `appointmentReminderJobs` 생성
8. 10분마다 `deliverAppointmentReminderJobs`가 큐를 읽어 재검증 후 발송
9. 예약이 바뀌면 `cleanupAppointmentReminderJobs`가 기존 대기 작업을 정리
10. 관리자 계정은 `dispatchAppointmentReminderJobs` callable로 수동 발송도 가능

## 검증 체크리스트

1. `google-services.json`이 `app/` 아래에 있는지 확인
2. 이메일 로그인과 소셜 로그인 키를 로컬에 입력
3. Firestore Rules와 Indexes 배포
4. 환자, 보호자, 매니저 계정 생성
5. 예약 생성 후 `appointmentAtEpochMillis`, `appointmentDateKey`가 저장되는지 확인
6. Functions 배포 후 `appointmentReminderJobs`가 생성되는지 확인
7. 연동값이 없으면 작업이 `SIMULATED`로 바뀌는지 확인
8. 연동값이 있으면 `SENT` 또는 `FAILED`로 기록되는지 확인
9. 예약을 취소하거나 시간을 바꾸면 기존 대기 작업이 `SKIPPED`로 바뀌는지 확인
10. `REQUESTED` 요청 카드에서는 수정 / 취소, `MATCHED` 요청 카드에서는 취소 버튼만 보이는지 확인
11. `MATCHED` 요청을 취소하면 연결된 세션이 `CANCELED`로 바뀌고 매니저가 다시 가용 상태로 보이는지 확인

## 데모 계정

- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`

## 개발용 기준선 초기화

- Firestore를 비우고 `users`, `hospitalGuides`만 기준선으로 다시 맞추는 절차는 [firebase-reset-baseline.md](/D:/BoDeul/docs/firebase-reset-baseline.md)에 정리했다.
- 실행 스크립트는 [reset-firestore-baseline.js](/D:/BoDeul/tools/firebase/reset-firestore-baseline.js)이며, `tools/firebase` 폴더에서 `npm run reset:baseline:dry-run`, `npm run reset:baseline:apply`로 사용할 수 있다.
- 기준선만으로 화면 검증이 어려울 때는 [seed-sample-service-data.js](/D:/BoDeul/tools/firebase/seed-sample-service-data.js)로 `npm run seed:sample:dry-run`, `npm run seed:sample:apply`를 실행해 예약/세션/후속 처리 샘플을 함께 주입할 수 있다.
- 이 스크립트는 배포 대상인 `functions/`가 아니라 운영 도구 디렉터리에서 관리한다.
- 이 스크립트는 `Firebase Authentication`은 삭제하지 않고, 기준선 Auth 계정을 확인한 뒤 기존 Auth UID에 맞춰 `users/{uid}` 문서를 다시 만든다.
- 백업 구조 점검과 현재 상태 diff가 필요할 때는 `npm run validate:backup -- --file ...`, `npm run diff:state -- --file ...`로 관리 대상 컬렉션 변화를 비교할 수 있다.
- 샘플 데이터를 넣은 뒤 역할별 화면 진입 가능 여부는 `npm run check:readiness`로, 전체 상태를 HTML로 남길 때는 `npm run report:ops -- --file ...`로 확인할 수 있다.
- 점검부터 리포트 생성까지 한 번에 실행하려면 `npm run workflow:ops -- --file ...`를 사용하면 된다.
- Firebase 점검과 Android 빌드/테스트까지 한 번에 확인하는 로컬 프리플라이트는 `npm run preflight:local -- --file ...`로 실행할 수 있다.
- 실제 앱 화면을 운영 리포트에 붙이려면 `npm run capture:app -- --screen-id ... --title ...` 또는 `npm run capture:app -- --preset manager-home`처럼 증적 파일을 만든 뒤 `--app-evidence` 옵션으로 `report:ops`, `workflow:ops`, `preflight:local`에 전달한다.
- CI에서는 `npm run preflight:ci` 또는 [.github/workflows/android-preflight.yml](/D:/BoDeul/.github/workflows/android-preflight.yml)로 같은 점검 루틴을 재사용한다. Firebase 시크릿이 없으면 자동으로 운영 워크플로를 건너뛰고 빌드/테스트만 수행한다.
- CI에서 쓰는 `FIREBASE_TOKEN`은 `firebase login:ci` refresh token을 기준으로 보고, [firebase-toolkit.js](/D:/BoDeul/tools/firebase/lib/firebase-toolkit.js)에서 access token으로 교환해 사용한다.
- GitHub Actions 시크릿/변수는 [configure-actions-firebase.js](/D:/BoDeul/tools/github/configure-actions-firebase.js)로 한 번에 반영할 수 있다. 현재 GitHub CLI 계정이 저장소 API 접근 권한이 있어야 하며, 권한이 없으면 `gh auth switch` 또는 `gh auth login`으로 계정을 먼저 맞춰야 한다.
- 실제 `workflow_dispatch`까지 성공시키려면 `.github/workflows/android-preflight.yml`이 원격 기본 브랜치에도 있어야 한다.
- 운영 도구 전체 목록은 [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md)에 정리했다.
