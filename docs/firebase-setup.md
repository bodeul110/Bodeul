# Firebase 설정

보들은 두 가지 모드를 지원합니다.

- 데모 모드: `google-services.json` 없이 로컬 목업 데이터로 실행
- Firebase 모드: `app/google-services.json`이 있고 Firebase가 설정되면 자동 활성화

## Android 설정

1. Firebase Console에서 패키지명 `com.example.bodeul` 로 Android 앱을 생성합니다.
2. `google-services.json` 파일을 내려받습니다.
3. 파일을 `app/google-services.json` 경로에 넣습니다.
4. `Authentication > Email/Password` 로그인을 활성화합니다.
5. Firestore를 Native 모드로 생성합니다.

## 필요한 컬렉션

### `users`

문서 ID는 Firebase Auth의 `uid`와 같아야 합니다.

```json
{
  "name": "김승민",
  "email": "manager@bodeul.app",
  "phone": "010-0000-0003",
  "role": "MANAGER"
}
```

가능한 `role` 값:

- `PATIENT`
- `GUARDIAN`
- `MANAGER`
- `ADMIN`

### `appointmentRequests`

```json
{
  "patientUserId": "patient-uid",
  "guardianUserId": "guardian-uid",
  "hospitalName": "서울내과병원",
  "departmentName": "신경과",
  "appointmentAt": "2026-04-15 10:30",
  "meetingPlace": "본관 1층 안내 데스크",
  "specialNotes": "어지럼 증상과 복용 중인 약 정보를 함께 확인해주세요.",
  "status": "MATCHED",
  "managerUserId": "manager-uid"
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
  "medicationNote": "처방전 수령 전입니다."
}
```

### `hospitalGuides`

```json
{
  "hospitalName": "서울내과병원",
  "departmentName": "신경과",
  "steps": [
    { "order": 1, "title": "환자 접촉", "description": "환자분을 만나 상태를 확인합니다." },
    { "order": 2, "title": "간편 등록", "description": "접수 창구에서 예약 정보를 확인합니다." }
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
  "nextVisitAt": "2026-04-22 10:00"
}
```

## 데모 계정

Firebase가 설정되지 않았을 때는 아래 계정으로 로그인할 수 있습니다.

- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`
