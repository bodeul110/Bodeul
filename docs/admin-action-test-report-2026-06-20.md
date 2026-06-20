# 2026-06-20 관리자 액션/심사 검증 보고서
## 구현한 내용

- `AutomationEntryActivity`에 관리자 후속 처리 자동화 extra를 추가했다.
  - 정산 후속 저장
  - 긴급 이슈 저장
  - 매니저 문의 응답 저장
  - 고객 문의 응답 저장
  - 액션 센터 읽음 처리
  - 액션 센터 해결 처리
- 관리자 웹에서 매니저 서류 반려/승인 흐름을 headless 브라우저로 재현하고 Firestore 반영을 확인했다.
- Firebase 기준 데이터 소스인 `tools/firebase/lib/baseline-config.js`의 한글 오염을 정상 한국어로 복구했다.
- 실기기 검증 후 개발용 주요 문서 상태를 다시 테스트 기준값으로 돌려놓았다.
  - `adminSettlementRecords/request-seed-completed`
  - `adminEmergencyIssues/request-seed-completed`
  - `supportInquiries/support-seed-received`
  - `clientSupportRequests/client-support-seed-patient-received`
  - `adminActionNotifications/admin-notification-seed-support`

## 변경된 범위

- 코드
  - `app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java`
  - `tools/firebase/lib/baseline-config.js`
- 문서
  - `docs/admin-action-test-report-2026-06-20.md`

## 검증 결과

### 1. Android 빌드 검증

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

- `assembleDebug` 성공
- `testDebugUnitTest` 성공

### 2. 관리자 웹 매니저 서류 심사 검증

대상 계정
- `admin@bodeul.app`
- `manager@bodeul.app`

확인한 흐름
1. 관리자 웹 로그인 성공
2. 매니저 승인 목록 진입 성공
3. 매니저 서류 원본 3종 미리보기 확인
4. 반려 사유 입력 후 반려 저장 성공
5. 체크리스트 3개 선택 후 최종 승인 저장 성공

Firestore 확인 결과
- 반려 후
  - `managerDocumentStatus = REJECTED`
  - `managerDocumentReviewNote = 실기기/웹 자동화 반려 사유`
  - `managerDocumentReviewedByName = 관리자`
  - `managerDocumentHistory` 마지막 이벤트 `REJECTED`
- 승인 후
  - `managerDocumentStatus = APPROVED`
  - `managerDocumentReviewNote = ""`
  - `managerDocumentReviewedByName = 관리자`
  - `managerDocumentHistory` 마지막 이벤트 `APPROVED`

추가 확인
- 관리자 웹에 표시되던 `manager@bodeul.app` 이름 `???`는 코드 문제가 아니라 기준 데이터 한글 오염이었다.
- `baseline-config.js`를 정상 한국어로 고쳤고, 개발용 Firestore 사용자 이름도 다시 맞췄다.

### 3. 실기기 관리자 후속 처리 검증

기기
- `SM-S921N`

실행 방식
- 최신 `debug` APK 재설치
- `AutomationEntryActivity`를 `role=ADMIN`, `screen=ADMIN_DASHBOARD`로 호출
- 각 액션 뒤 `topResumedActivity`가 `com.example.bodeul/.ui.admin.AdminActivity`로 돌아오는지 확인
- Firestore 실제 저장 상태를 함께 확인

확인한 액션
1. 정산 후속 저장
   - 입력: `adminSettlementStatus=CONFIRMED`
   - 결과: `adminSettlementRecords/request-seed-completed.status = CONFIRMED`
2. 긴급 이슈 저장
   - 입력: `adminEmergencyStatus=RESOLVED`
   - 결과: `adminEmergencyIssues/request-seed-completed.status = RESOLVED`
3. 매니저 문의 응답 저장
   - 입력: `adminSupportInquiryId=support-seed-received`
   - 결과: `supportInquiries/support-seed-received.status = ANSWERED`
4. 고객 문의 응답 저장
   - 입력: `adminClientSupportRequestId=client-support-seed-patient-received`
   - 결과: `clientSupportRequests/client-support-seed-patient-received.status = ANSWERED`
5. 액션 센터 읽음 처리
   - 입력: `adminNotificationId=admin-notification-seed-support`, `adminActionOperation=READ`
   - 결과: `isRead = true`, `readAt > 0`
6. 액션 센터 해결 처리
   - 입력: `adminNotificationId=admin-notification-seed-support`, `adminActionOperation=RESOLVE`
   - 결과: `isResolved = true`, `resolvedAt > 0`, `resolvedByName = 관리자`

실행 후 정리
- 위 5개 기본 문서는 다시 테스트 기준 상태로 복구했다.
- 다만 관리자 후속 처리 과정에서 생성된 추가 감사 로그/전달 기록 문서는 남아 있을 수 있다.

## 남은 범위

- `tools/firebase/seed-sample-service-data.js`와 일부 운영 문서에는 아직 한글 오염 문자열이 남아 있다.
- 이번 배치에서는 주요 기준 사용자/가이드 텍스트만 복구했고, 샘플 서비스 데이터 전체 문자열 정리는 하지 않았다.
- 관리자 후속 처리 자동화가 생성한 `adminAuditLogs`, `adminActionDeliveries` 등의 추가 산출물까지 완전 원복하려면 별도 정리 배치가 필요하다.
- 관리자 요청 배정(`assignManager`)과 서류 심사 앱 경로는 이번 배치 자동화 범위에 포함하지 않았다.
