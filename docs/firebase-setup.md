# Firebase 설정

기준일: 2026-04-15

## 현재 프로젝트 상태

- Android 패키지명: `com.example.bodeul`
- Firebase 설정 파일 위치: `app/google-services.json`
- 인증: Firebase Authentication
- 데이터 저장소: Cloud Firestore
- Functions: `functions/index.js`
- Firebase 설정이 없으면 앱은 자동으로 목업 모드로 동작한다.

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
  "role": "MANAGER"
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

## 연동 순서

1. 앱에서 예약 생성
2. `REQUESTED` 상태에서는 앱에서 같은 요청을 수정하거나 취소 가능
3. Firestore에 `appointmentRequests` 저장 또는 수정
4. 매일 오전 9시 `syncAppointmentReminderJobs` 실행
5. 조건에 맞는 요청의 `appointmentReminderJobs` 생성
6. 10분마다 `deliverAppointmentReminderJobs`가 큐를 읽어 재검증 후 발송
7. 예약이 바뀌면 `cleanupAppointmentReminderJobs`가 기존 대기 작업을 정리
8. 관리자 계정은 `dispatchAppointmentReminderJobs` callable로 수동 발송도 가능

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
10. `REQUESTED` 요청 카드에서 수정 / 취소 버튼이 보이고, 저장 후 목록이 즉시 갱신되는지 확인

## 데모 계정

- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`
