# 2026-06-20 실기기 테스트 보고서

## 구현한 내용

- 실기기 `SM-S921N`에서 Android `debug` APK를 재설치하고 앱 실행 가능 여부를 확인했다.
- 연결 기기에서 `connectedDebugAndroidTest`를 실행해 계측 테스트 1건을 통과시켰다.
- `AutomationEntryActivity`를 이용해 역할별 핵심 화면 스모크를 수행하고, 통과 화면과 차단 원인을 분리했다.
- 환자 홈 실기기 크래시 원인이던 `clientSupportRequests.createdAt` 타입 불일치를 수정했다.
- Firebase 샘플 서비스 데이터를 다시 주입해 역할별 화면 진입 readiness를 모두 복구했다.

## 변경된 범위

- 코드
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseClientSupportRepository.java`
- 문서
  - `docs/device-test-report-2026-06-20.md`

## 테스트 환경

- 기기: `SM-S921N`
- OS: Android `16`
- SDK: `36`
- 테스트 APK: `app/build/outputs/apk/debug/app-debug.apk`
- applicationId: `com.example.bodeul`

## 실행한 테스트

### 1. 설치 및 기본 실행

- `adb install --user 0 -r D:\BoDeul\app\build\outputs\apk\debug\app-debug.apk` 성공
- 런처 실행 후 최상단 액티비티 확인
  - `com.example.bodeul/.ui.auth.PermissionGuideActivity`

### 2. 계측 테스트

- 실행 명령

```powershell
./gradlew.bat connectedDebugAndroidTest
```

- 결과
  - `BUILD SUCCESSFUL`
  - `ExampleInstrumentedTest.useAppContext` 1건 통과

- 결과 파일
  - `app/build/outputs/androidTest-results/connected/debug/TEST-SM-S921N - 16-_app-.xml`
  - `app/build/reports/androidTests/connected/debug/index.html`

### 3. 자동 진입 스모크

- 실행 방식
  - `AutomationEntryActivity`에 `role`, `screen`, `forceSignIn` extra를 전달해 실기기에서 화면 진입 여부를 확인했다.

- 화면 진입 확인
  - `PATIENT / HOME` -> `com.example.bodeul/.MainActivity`
  - `PATIENT / BOOKING` -> `com.example.bodeul/.ui.booking.BookingActivity`
  - `GUARDIAN / BOOKING_STATUS` -> `com.example.bodeul/.ui.booking.BookingStatusActivity`
  - `GUARDIAN / GUARDIAN_REPORT` -> `com.example.bodeul/.ui.report.GuardianReportActivity`
  - `MANAGER / MANAGER_HOME` -> `com.example.bodeul/.ui.manager.ManagerActivity`
  - `MANAGER / MANAGER_GUIDE` -> `com.example.bodeul/.ui.manager.ManagerGuideActivity`
  - `MANAGER / MANAGER_PROFILE` -> `com.example.bodeul/.ui.manager.ManagerProfileActivity`
  - `ADMIN / ADMIN_DASHBOARD` -> `com.example.bodeul/.ui.admin.AdminActivity`

### 4. Firebase 샘플 데이터 복구

- 기준선 상태 점검 결과
  - Auth 4계정과 `users/{uid}` 문서는 모두 존재
  - `check-role-screen-readiness --json` 기준 역할별 readiness 최종 `ALL PASS`
- 실행 명령

```powershell
cd D:\BoDeul\tools\firebase
npm run seed:sample:apply
```

- 적용 후 확인
  - `request-seed-progress` -> `status=IN_PROGRESS`
  - `session-seed-progress` -> `currentStatus=WAITING`
  - 보호자 실시간 진행 카드 readiness 복구

## 판단

- 앱 설치, 런처 실행, 알림 권한 안내 진입, 계측 테스트 1건은 실기기에서 정상 동작했다.
- 초기 환자 홈 이탈 원인은 Firebase 기준선 데이터가 아니라 `FirebaseClientSupportRepository`의 타입 캐스팅 크래시였다.
- `createdAt` 등 시간 필드를 `Timestamp`뿐 아니라 정수 epoch millis도 읽도록 보강한 뒤, 환자/보호자/매니저/관리자 핵심 화면이 모두 실기기에서 열렸다.
- `FirebaseAuthRepository`는 로그인 직후 빈 캐시 miss를 줄이도록 서버 기준 재조회 1회를 허용했다.
- Firebase 샘플 데이터도 재주입 후 readiness가 전부 통과했다.

## 남은 범위

- 실기기 스모크는 핵심 진입 화면 위주로만 확인했다. 예약 완료, 후속 처리, 채팅, 매니저 지원, 관리자 세부 액션은 별도 시나리오가 필요하다.
- 이번 후속 작업은 실기기 테스트 외에 Firebase 샘플 데이터 복구와 앱 코드 보강도 포함했다.
