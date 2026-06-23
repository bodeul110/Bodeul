# 2026-06-20 실기기 테스트 보고서

## 구현한 내용

- 실기기 `SM-S921N`에서 최신 `debug` APK를 다시 설치하고 자동 진입 스모크를 확장 실행했다.
- 기존 핵심 화면 12건 확인에 이어, 디버그 자동화 보조 경로를 이용해 아래 저장 액션까지 실기기에서 검증했다.
  - 보호자 채팅 메시지 전송
  - 환자 후기 저장
  - 환자 정산 후속 저장
  - 환자 SOS 후속 저장
  - 환자 고객 문의 등록
  - 매니저 문의 등록
  - 매니저 원본 서류 업로드
- 실기기 확장 검증 중 환자 문의와 매니저 문의가 모두 같은 원인으로 크래시 나는 것을 확인했고 수정했다.
  - 신규 Firestore 문서 `add()` payload에 `FieldValue.delete()`를 넣고 있었다.
  - Firebase SDK가 이를 즉시 거부해 `IllegalArgumentException`으로 종료됐다.
- 디버그 자동화 업로드 경로가 실제로 동작하도록 유지했다.
  - `uploadDocumentType`만 전달해도 샘플 파일 업로드 분기를 타도록 수정
  - `file://` 임시 PNG도 MIME과 파일 크기를 판별하도록 업로드 정책 보강
- `GoogleApiManager` 보안 예외와 `NaverAdsServices` 경고는 화면 진입 성공과 분리된 잔여 리스크로 남겼다.

## 변경된 범위

- 코드
  - `app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseClientSupportRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- 문서
  - `device-test-report-2026-06-20.md`

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

### 6. 채팅 / 후속 / 문의 액션 검증

- 보호자 채팅 전송
  - 시나리오: `role=GUARDIAN`, `screen=COMPANION_CHAT`, `requestId=request-seed-progress`, `chatMessage=guardian_chat_check`
  - 결과: `채팅 메시지 저장 중 / guardian_chat_check`
  - 최종 화면: `com.example.bodeul/.ui.chat.CompanionChatActivity`

- 환자 후속 저장
  - 시나리오: `role=PATIENT`, `screen=BOOKING_FOLLOW_UP`, `requestId=request-seed-completed`
  - 입력:
    - `followUpReviewRating=excellent`
    - `followUpSettlementStatus=CONFIRMED`
    - `followUpSupportEscalation=GUIDE_VIEWED`
  - 결과:
    - `후기 저장 중 / excellent`
    - `정산 후속 저장 중 / CONFIRMED`
    - `후속 지원 저장 중 / GUIDE_VIEWED`
  - 최종 화면: `com.example.bodeul/.ui.booking.BookingFollowUpActivity`

- 환자 고객 문의 등록
  - 시나리오: `role=PATIENT`, `screen=CLIENT_SUPPORT`, `requestId=request-seed-completed`
  - 입력:
    - `clientSupportCategory=settlement`
    - `clientSupportTitle=자동화문의`
    - `clientSupportBody=문의등록확인`
  - 결과: `고객 문의 저장 중 / 자동화문의`
  - 최종 화면: `com.example.bodeul/.ui.support.ClientSupportActivity`

- 매니저 문의 등록
  - 시나리오: `role=MANAGER`, `screen=MANAGER_SUPPORT`
  - 입력:
    - `managerSupportCategory=document`
    - `managerSupportTitle=자동화매니저문의`
    - `managerSupportBody=매니저문의등록확인`
  - 결과: `매니저 문의 저장 중 / 자동화매니저문의`
  - 최종 화면: `com.example.bodeul/.ui.manager.ManagerSupportActivity`

### 7. 크래시 원인과 수정

- 최초 실패 시나리오
  - `PATIENT / CLIENT_SUPPORT`
  - `MANAGER / MANAGER_SUPPORT`
- 공통 크래시

```text
java.lang.IllegalArgumentException: Invalid data. FieldValue.delete() can only be used with update() and set() with SetOptions.merge()
```

- 원인
  - 신규 문의 문서를 `collection.add()`로 만들면서 `respondedAt`, `responseReadAt`, `responseReminderSentAt`에 `FieldValue.delete()`를 넣고 있었다.
- 수정
  - 신규 문서의 미응답 시각 필드는 삭제 센티널 대신 `0L` 초기값을 사용하도록 변경했다.
- 수정 후 재검증
  - 환자 문의 등록 통과
  - 매니저 문의 등록 통과
  - 최종 4건 배치 전체 통과

### 8. 관찰된 로그

- 공통 관찰
  - 대부분 시나리오에서 `GoogleApiManager`가 `Unknown calling package name 'com.google.android.gms'` 보안 예외를 남겼다.
  - 로그에는 `ConnectionResult{statusCode=DEVELOPER_ERROR}`와 `Phenotype.API is not available on this device`가 함께 보였다.
- 추가 관찰
  - `MANAGER / MANAGER_HOME` 진입 중 `NaverAdsServices`가 광고 ID 반사 호출 실패 경고를 남겼다.
- 현재 판단
  - 위 로그는 화면 진입 성공과 분리돼 있었고, 앱 크래시나 화면 차단으로 이어지지 않았다.
  - Google Play 서비스, 광고 ID, 외부 SDK 초기화 경로는 별도 추적 대상이다.

### 9. Firebase 샘플 데이터 복구 이력

- 기준선 계정과 `users/{uid}` 문서는 모두 존재하는 것을 재확인했다.
- readiness가 흔들린 상태를 복구하기 위해 아래 명령을 사용했다.

```powershell
cd D:\BoDeul\tools\firebase
npm run seed:sample:apply
```

- 적용 후 역할별 readiness와 샘플 시나리오 상태는 모두 `ALL PASS`로 복구됐다.

## 판단

- 최신 `debug` APK 기준 실기기 자동 진입 스모크 12건은 모두 통과했다.
- 매니저 서류 업로드 자동화는 샘플 PNG 생성, Storage 업로드, 메타데이터 저장, 프로필 화면 복귀까지 통과했다.
- 채팅, 후속 저장, 고객 문의, 매니저 문의까지 실기기 저장 액션 검증이 완료됐다.
- 문의 저장 크래시 2건은 수정 후 재검증까지 마쳤다.
- 현재 남은 이슈는 외부 서비스 로그 노이즈와 아직 손대지 않은 관리자 세부 처리/승인 시나리오다.

## 남은 범위

- 관리자 세부 처리 액션
- 매니저 서류 승인/반려 흐름
- 필요 시 채팅 송수신 양방향 확인
- `GoogleApiManager`와 광고 ID 관련 로그의 기기 환경 / 외부 SDK 초기화 경로 분리 확인