# 프로젝트 전체 점검 기록 (2026-06-23)

## 검증 결과
- Android `assembleDebug` 성공
- Android `testDebugUnitTest` 성공
- Android `lintDebug --rerun-tasks` 성공
- Android lint 결과: `No issues found.`
- `admin-web` `npm run lint` 성공
- `admin-web` `npm run build` 성공
- Firebase Functions 저장소 소스 문법 검사 성공
- Firestore Rules dry run 컴파일 성공

## 주요 findings
### 1. Firestore 업데이트 규칙이 일부 컬렉션에서 과도하게 넓음
- GitHub issue: https://github.com/bodeul110/Bodeul/issues/17
- 처리 상태: 수정 완료
- `appointmentRequests`는 배정 매니저가 문서 전체를 업데이트할 수 있다.
- `companionSessions`는 세션 참여자 또는 예약 참여자가 문서 전체를 업데이트할 수 있다.
- `appointmentFollowUps`는 예약 참여자가 문서 전체를 생성하거나 업데이트할 수 있다.
- 앱 UI가 특정 필드만 쓰더라도, 클라이언트 권한을 가진 사용자가 직접 SDK를 호출하면 상태, 참여자 ID, 정산/후속 필드 같은 다른 필드까지 바꿀 수 있다.
- 관련 파일
  - `firestore.rules`
- 조치 내용
  - `appointmentRequests`는 생성, 예약 수정, 예약 취소, 매니저 상태 전환을 각각 분리했다.
  - `companionSessions`는 매니저 진행/기록/위치/채팅 필드와 환자/보호자 채팅/취소 필드를 분리했다.
  - `appointmentFollowUps`는 환자/보호자가 작성할 수 있는 후기, 정산 후속, SOS 후속 필드만 허용했다.
  - 각 경로에 `diff().affectedKeys().hasOnly(...)` 기반 필드 제한을 추가했다.

### 2. Android 13+ 알림 권한이 사실상 1회성 선택으로 고정됨
- GitHub issue: https://github.com/bodeul110/Bodeul/issues/18
- 처리 상태: 수정 완료
- `PermissionGuideActivity`는 안내를 닫거나 권한 요청 결과가 돌아온 직후 항상 `markCompleted()`와 `markNotificationPromptCompleted()`를 함께 기록한다.
- `EntryFlowCoordinator`는 `hasCompletedNotificationPrompt()`가 `true`가 되면 다시는 알림 권한 안내를 열지 않는다.
- 앱 내부에는 `POST_NOTIFICATIONS`를 다시 요청하거나 시스템 알림 설정으로 보내는 경로가 없다.
- 결과적으로 첫 실행에서 사용자가 안내를 닫거나 권한을 거부하면, 이후에는 앱 기능만 계속 열리고 푸시 알림은 비활성 상태로 남는다.
- 관련 파일
  - `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideActivity.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/EntryFlowCoordinator.java`
  - `app/src/main/java/com/example/bodeul/util/NotificationPermissionSupport.java`
- 조치 내용
  - 알림을 실제로 게시할 수 있을 때만 알림 프롬프트 완료 상태를 저장하도록 변경했다.
  - 기존 저장값이 완료 상태여도 실제 알림 게시가 불가능하면 프롬프트 상태를 다시 미완료로 보정한다.
  - 권한 거부 또는 앱 알림 비활성 상태에서 시스템 알림 설정으로 이동하는 버튼을 추가했다.
  - 설정 화면에서 돌아온 뒤 알림 게시 가능 상태를 다시 확인하고 안내 문구를 표시한다.

### 3. 파일 크기를 알 수 없는 URI는 앱 단 검증을 우회함
- GitHub issue: https://github.com/bodeul110/Bodeul/issues/19
- 처리 상태: 수정 완료
- `ManagerDocumentUploadPolicy`와 `CompanionChatAttachmentUploadPolicy`는 파일 크기 조회에 실패하면 `-1L`을 반환한다.
- 두 정책 모두 `fileSize > MAX_FILE_SIZE_BYTES`일 때만 차단하므로, 크기를 알 수 없는 문서는 앱 단에서 통과한다.
- Storage Rules에는 10MB 제한이 있어 서버 측 방어는 남아 있지만, 사용자는 업로드 단계에서 늦게 실패할 수 있다.
- 영향 범위는 매니저 서류 업로드와 안심 채팅 첨부다.
- 관련 파일
  - `app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java`
  - `app/src/main/java/com/example/bodeul/data/CompanionChatAttachmentUploadPolicy.java`
  - `storage.rules`
- 조치 내용
  - 두 업로드 정책이 같은 `UploadFileSizePolicy`를 사용하도록 공통화했다.
  - `OpenableColumns.SIZE`가 없으면 `file://` 실제 파일 길이를 확인하고, 그래도 알 수 없으면 스트림을 최대 제한 초과 지점까지만 읽어 크기를 판정한다.
  - 스트림도 열 수 없어 크기를 확인할 수 없는 파일은 업로드 전에 차단한다.
  - 메타데이터 없는 파일의 허용, 차단, 읽기 실패 fallback 단위 테스트를 추가했다.

### 4. Firestore enum 파싱이 일부 경로에서 예외에 취약함
- GitHub issue: https://github.com/bodeul110/Bodeul/issues/20
- 처리 상태: 수정 완료
- 최근 문의 저장소는 안전한 fallback 파싱을 쓰지만, 핵심 경로 여러 곳은 여전히 `Enum.valueOf()`를 직접 호출한다.
- `users.role` 같은 외부 데이터가 예상값과 다르면 `IllegalArgumentException`으로 로그인, 예약, 매니저 화면 진입이 중단될 수 있다.
- 시드 스크립트, 관리자 웹, 수동 데이터 보정이 함께 존재하는 구조라면 방어적으로 처리하는 편이 맞다.
- 관련 파일
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java`
- 조치 내용
  - `SafeEnumParser`를 추가해 외부 enum 문자열을 예외 없이 `null` 또는 기본값으로 파싱하도록 공통화했다.
  - 로그인, 인증, 예약, 매니저, 보호자 리포트, 관리자, 문의 매퍼의 직접 enum 파싱을 fallback 기반으로 교체했다.
  - 필수 enum 값이 잘못된 Firestore 문서는 모델 변환 단계에서 `null`로 안전 차단하고, 채팅 발신자/문의 상태처럼 기존 기본값이 있던 경로는 기본값을 유지한다.
  - 잘못된 enum 문자열에 대한 단위 테스트를 추가했다.

## 테스트 공백
- Firestore Rules의 필드 단위 업데이트 허용/차단 테스트가 없다.
- 알림 권한 `닫기`, `거부`, `설정에서 다시 허용` 시나리오는 자동화 범위에서 직접 확인되지 않았다.

## 변경 범위
- Firestore 보안 규칙 변경
  - `firestore.rules`
- Android 알림 권한 복구 경로 변경
  - `app/src/main/java/com/example/bodeul/ui/auth/EntryFlowCoordinator.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideActivity.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuidePreferences.java`
  - `app/src/main/java/com/example/bodeul/util/NotificationPermissionSupport.java`
  - `app/src/main/res/layout/activity_permission_guide.xml`
  - `app/src/main/res/values/strings.xml`
- Android 업로드 크기 정책 변경
  - `app/src/main/java/com/example/bodeul/data/UploadFileSizePolicy.java`
  - `app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java`
  - `app/src/main/java/com/example/bodeul/data/CompanionChatAttachmentUploadPolicy.java`
  - `app/src/test/java/com/example/bodeul/data/UploadFileSizePolicyTest.java`
- Android Firestore enum fallback 변경
  - `app/src/main/java/com/example/bodeul/util/SafeEnumParser.java`
  - `app/src/test/java/com/example/bodeul/util/SafeEnumParserTest.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseCompanionSessionMapper.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminSupportMapper.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseClientSupportRepository.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java`
- 문서 추가 및 갱신 1건
  - `docs/project-check-2026-06-23.md`
