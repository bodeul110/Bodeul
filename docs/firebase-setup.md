# Firebase 설정

## 현재 프로젝트 상태

프로젝트는 Firebase 연동 준비가 끝난 상태입니다.

- Android 패키지명: `com.example.bodeul`
- 앱 설정 파일 위치: `app/google-services.json`
- 인증 방식: Firebase Authentication `Email/Password`
- 데이터 저장소: Cloud Firestore
- 보안 규칙 파일: `firestore.rules`
- 인덱스 파일: `firestore.indexes.json`
- Firebase CLI 설정 파일: `firebase.json`

`google-services.json` 이 없으면 앱은 자동으로 데모 모드로 동작합니다.

## 콘솔에서 해야 할 일

### 1. Firebase 프로젝트 생성

1. Firebase Console에 로그인합니다.
2. 새 프로젝트를 생성합니다.
3. 프로젝트 이름과 프로젝트 ID를 정합니다.
4. 필요하면 팀원을 `Users and permissions` 에서 초대합니다.

### 2. Android 앱 등록

1. Firebase 프로젝트에서 Android 앱을 추가합니다.
2. 패키지명에 반드시 `com.example.bodeul` 을 입력합니다.
3. 필요하면 앱 닉네임을 입력합니다.
4. `google-services.json` 을 다운로드합니다.
5. 파일을 `app/google-services.json` 경로에 넣습니다.

파일명에 `(2)` 같은 접미사가 붙으면 안 됩니다.

### 3. Authentication 활성화

1. `Build > Authentication` 으로 이동합니다.
2. `Get started` 를 누릅니다.
3. `Sign-in method` 탭에서 `Email/Password` 를 활성화합니다.
4. 필요하면 `Templates` 탭에서 인증 메일과 비밀번호 재설정 메일 문구를 한국어 기준으로 조정합니다.

현재 앱에서 동작하는 인증 기능은 다음과 같습니다.

- 이메일/비밀번호 회원가입
- 회원가입 직후 이메일 인증 메일 발송
- 이메일 인증 후 로그인
- 비밀번호 재설정 메일 발송
- 자동 로그인 시 이메일 인증 상태 재확인

### 4. Firestore 생성

1. `Build > Firestore Database` 로 이동합니다.
2. `Create database` 를 누릅니다.
3. `Native mode` 를 선택합니다.
4. 리전을 선택합니다.
5. 생성 직후에는 아래 보안 규칙을 바로 배포합니다.

## 보안 규칙 적용

### 방법 1. Firebase Console에서 직접 적용

1. `Build > Firestore Database > Rules` 로 이동합니다.
2. 프로젝트 루트의 `firestore.rules` 내용을 그대로 붙여넣습니다.
3. 게시합니다.

### 방법 2. Firebase CLI로 적용

1. Firebase CLI를 설치합니다.
2. 프로젝트 루트에서 로그인합니다.
3. Firebase 프로젝트를 연결합니다.
4. 규칙과 인덱스를 배포합니다.

예시 명령:

```powershell
firebase login
firebase use <firebase-project-id>
firebase deploy --only firestore:rules,firestore:indexes
```

`firebase.json`, `firestore.rules`, `firestore.indexes.json` 은 이미 프로젝트에 포함되어 있습니다.

## 필요한 컬렉션 구조

### `users`

문서 ID는 반드시 Firebase Authentication의 `uid` 와 같아야 합니다.

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
    {
      "order": 1,
      "title": "환자 접촉",
      "description": "환자분을 만나 상태를 확인합니다."
    },
    {
      "order": 2,
      "title": "간편 등록",
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
  "nextVisitAt": "2026-04-22 10:00"
}
```

## 권장 시드 순서

### 방법 1. 앱에서 계정 생성

가장 안전한 방식입니다.

1. 앱에서 매니저 계정을 회원가입합니다.
2. 앱에서 환자 계정을 회원가입합니다.
3. 앱에서 보호자 계정을 회원가입합니다.
4. Firestore `users` 컬렉션에서 생성된 문서의 `role` 값을 확인합니다.

이 방식은 `users` 문서 ID가 Firebase Auth `uid` 와 자동으로 맞습니다.

### 방법 2. 콘솔에서 직접 계정 생성

1. `Authentication > Users` 에서 사용자를 생성합니다.
2. 생성된 사용자 `uid` 를 확인합니다.
3. 같은 `uid` 값으로 `users` 문서를 만듭니다.

### 공통 후속 작업

계정 준비가 끝났으면 아래 문서를 수동으로 추가합니다.

1. `hospitalGuides`
2. `appointmentRequests`
3. `companionSessions`

`sessionReports` 는 매니저가 앱에서 리포트를 전송하면 자동으로 생성됩니다.

## 첫 테스트 체크리스트

1. `app/google-services.json` 을 넣습니다.
2. 앱을 실행합니다.
3. 환자 또는 보호자 계정을 회원가입합니다.
4. 인증 메일을 열어 이메일 인증을 완료합니다.
5. 다시 로그인합니다.
6. Firestore `users` 컬렉션에 사용자 문서가 생겼는지 확인합니다.
7. 매니저 계정으로도 같은 방식으로 가입합니다.
8. Firestore에 `hospitalGuides`, `appointmentRequests`, `companionSessions` 를 넣습니다.
9. 매니저 로그인 후 홈과 가이드 화면이 열리는지 확인합니다.
10. 보호자 메시지 저장, 복약 메모 저장, 리포트 전송이 Firestore에 반영되는지 확인합니다.

## 데모 계정

Firebase가 설정되지 않았을 때는 아래 계정으로 로그인할 수 있습니다.

- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`

## 자주 틀리는 지점

- Firebase Console에 등록한 Android 패키지명이 `com.example.bodeul` 이 아니면 설정 파일이 맞지 않습니다.
- `google-services.json` 은 반드시 `app/google-services.json` 경로에 있어야 합니다.
- `users` 문서 ID와 Firebase Auth `uid` 가 다르면 로그인 후 프로필 조회가 실패합니다.
- Email/Password가 비활성화되어 있으면 회원가입과 로그인이 모두 실패합니다.
- Firestore 규칙을 너무 강하게 잠가두면 매니저 화면에서 환자/보호자 데이터를 읽지 못할 수 있습니다.
