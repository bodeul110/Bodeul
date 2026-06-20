# 2026-06-20 실기기 테스트 보고서

## 구현한 내용

- 실기기 `SM-S921N`에서 최신 `debug` APK를 다시 설치하고 자동 진입 스모크를 확장 실행했다.
- 기존 핵심 화면 6건에 더해 `CLIENT_HOME`, `BOOKING_FOLLOW_UP`, `MANAGER_HISTORY`, `MANAGER_SUPPORT`까지 포함한 12개 화면을 확인했다.
- 확장 점검 중 실기기에 남아 있던 구버전 설치본에는 `AutomationEntryActivity`가 없어 자동 진입이 실패하는 것을 확인했고, 최신 APK 재설치 후 동일 배치를 다시 수행했다.
- 디버그 자동화 업로드 경로가 실제로 동작하도록 두 군데를 보강했다.
  - `uploadDocumentType`만 전달해도 샘플 파일 업로드 분기를 타도록 수정
  - `file://` 임시 PNG도 MIME과 파일 크기를 판별하도록 업로드 정책 보강
- `GoogleApiManager` 보안 예외와 `NaverAdsServices` 경고를 별도로 기록해, 화면 진입 성공과 분리된 잔여 리스크로 남겼다.

## 변경된 범위

- 코드
  - `app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java`
  - `app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java`
- 문서
  - `docs/device-test-report-2026-06-20.md`

## 테스트 환경

- 기기: `SM-S921N`
- OS: Android `16`
- SDK: `36`
- 테스트 APK: `app/build/outputs/apk/debug/app-debug.apk`
- applicationId: `com.example.bodeul`
- adb 경로: `C:\Users\wlsrj\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## 실행한 테스트

### 1. 최신 APK 재설치 및 기본 실행 확인

- `adb install -r D:\BoDeul\app\build\outputs\apk\debug\app-debug.apk` 성공
- 재설치 전 상태
  - 실기기 설치본에는 `com.example.bodeul.debug.AutomationEntryActivity`가 없어 자동 진입이 실패했다.
- 재설치 후 상태
  - `am start -W -n com.example.bodeul/com.example.bodeul.debug.AutomationEntryActivity ...`가 정상 동작했다.

### 2. 빌드 검증

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

- 결과
  - `assembleDebug` 성공
  - `testDebugUnitTest` 성공

### 3. 계측 테스트

```powershell
./gradlew.bat connectedDebugAndroidTest
```

- 결과
  - `BUILD SUCCESSFUL`
  - `ExampleInstrumentedTest.useAppContext` 1건 통과

- 결과 파일
  - `app/build/outputs/androidTest-results/connected/debug/TEST-SM-S921N - 16-_app-.xml`
  - `app/build/reports/androidTests/connected/debug/index.html`

### 4. 자동 진입 확장 스모크

- 실행 방식
  - `AutomationEntryActivity`에 `role`, `screen`, `forceSignIn=true` extra를 전달했다.
  - 각 시나리오마다 앱 강제 종료 후 재로그인시키고, 최종 `topResumedActivity`를 확인했다.

- 확인 결과
  - `PATIENT / HOME` -> `com.example.bodeul/.MainActivity`
  - `PATIENT / CLIENT_HOME` -> `com.example.bodeul/.MainActivity`
  - `PATIENT / BOOKING` -> `com.example.bodeul/.ui.booking.BookingActivity`
  - `GUARDIAN / BOOKING_STATUS` -> `com.example.bodeul/.ui.booking.BookingStatusActivity`
  - `GUARDIAN / BOOKING_FOLLOW_UP` -> `com.example.bodeul/.ui.booking.BookingFollowUpActivity`
  - `GUARDIAN / GUARDIAN_REPORT` -> `com.example.bodeul/.ui.report.GuardianReportActivity`
  - `MANAGER / MANAGER_HOME` -> `com.example.bodeul/.ui.manager.ManagerActivity`
  - `MANAGER / MANAGER_HISTORY` -> `com.example.bodeul/.ui.manager.ManagerHistoryActivity`
  - `MANAGER / MANAGER_GUIDE` -> `com.example.bodeul/.ui.manager.ManagerGuideActivity`
  - `MANAGER / MANAGER_SUPPORT` -> `com.example.bodeul/.ui.manager.ManagerSupportActivity`
  - `MANAGER / MANAGER_PROFILE` -> `com.example.bodeul/.ui.manager.ManagerProfileActivity`
  - `ADMIN / ADMIN_DASHBOARD` -> `com.example.bodeul/.ui.admin.AdminActivity`

### 5. 매니저 서류 업로드 자동화 검증

- 실행 방식
  - `role=MANAGER`, `screen=MANAGER_PROFILE`, `uploadDocumentType=CRIMINAL_RECORD`만 전달했다.
  - `uploadDocumentPath` 없이 디버그 액티비티가 샘플 PNG를 생성해 업로드하도록 확인했다.

- 확인 결과
  - `원본 파일 업로드 중 / CRIMINAL_RECORD / automation-criminalRecord.png`
  - `서류 메타데이터 저장 중 / automation-criminalRecord.png`
  - 최종 화면: `com.example.bodeul/.ui.manager.ManagerProfileActivity`

- 판단
  - 디버그 자동화 업로드 보조 경로는 현재 정상 동작한다.
  - 이번 수정 전에는 업로드 분기를 타지 못하거나 MIME 판별 실패로 차단됐다.

### 6. 관찰된 로그

- 공통 관찰
  - 대부분 시나리오에서 `GoogleApiManager`가 `Unknown calling package name 'com.google.android.gms'` 보안 예외를 남겼다.
  - 로그에는 `ConnectionResult{statusCode=DEVELOPER_ERROR}`와 `Phenotype.API is not available on this device`가 함께 보였다.
- 추가 관찰
  - `MANAGER / MANAGER_HOME` 진입 중 `NaverAdsServices`가 광고 ID 반사 호출 실패 경고를 남겼다.
- 현재 판단
  - 위 로그는 화면 진입 성공과 분리돼 있었고, 앱 크래시나 화면 차단으로 이어지지 않았다.
  - 다만 Google Play 서비스, 광고 ID, 외부 SDK 초기화 경로는 별도 추적 대상이다.

### 7. Firebase 샘플 데이터 복구 이력

- 기준선 계정과 `users/{uid}` 문서는 모두 존재하는 것을 재확인했다.
- readiness가 흔들린 상태를 복구하기 위해 아래 명령을 사용했다.

```powershell
cd D:\BoDeul\tools\firebase
npm run seed:sample:apply
```

- 적용 후 역할별 readiness와 샘플 시나리오 상태는 모두 `ALL PASS`로 복구됐다.

## 판단

- 최신 `debug` APK 기준 실기기 자동 진입 스모크 12건은 모두 통과했다.
- 매니저 서류 업로드 자동화도 샘플 PNG 생성, Storage 업로드, 메타데이터 저장, 프로필 화면 복귀까지 통과했다.
- 이전에 수정한 `FirebaseClientSupportRepository`, `FirebaseAuthRepository` 보강은 실기기 기준으로도 효과가 유지됐다.
- 현재 남은 이슈는 화면 진입 실패가 아니라 외부 서비스 로그 노이즈와 더 깊은 업무 시나리오 미검증이다.

## 남은 범위

- 자동 진입 스모크는 화면 도달 여부 중심이다. 아래 업무 시나리오는 별도 실기기 검증이 더 필요하다.
  - 예약 완료 후 후속 처리
  - 채팅 송수신
  - 매니저 지원 액션 상세 처리
  - 관리자 세부 처리 액션
  - 매니저 서류 승인/반려 흐름
- `GoogleApiManager`와 광고 ID 관련 로그는 기기 환경과 SDK 초기화 경로를 분리해서 다시 확인할 필요가 있다.