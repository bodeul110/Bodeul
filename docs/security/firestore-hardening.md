# Firestore 보안 정리

## 2026-05-04 1차 정리

### 반영한 내용

- `appointmentRequests` 읽기 권한을 예약 참여자와 관리자 기준으로 축소
- `companionSessions` 읽기 권한을 세션 매니저, 연결된 예약 참여자, 관리자 기준으로 축소
- `sessionReports` 읽기 권한을 연결된 세션 참여자와 관리자 기준으로 축소
- `users` 읽기 권한에서 관리자 문서는 본인과 관리자만 읽을 수 있게 축소
- 보호자 리포트 저장소 구현을 바꿔 `companionSessions`, `sessionReports`, `users`, `hospitalGuides` 전체 컬렉션 스캔을 제거

### 실제 검증

- `guardian@bodeul.app`
  - 본인 예약 조회 성공
  - 연결 세션 조회 성공
  - 연결 세션 리포트 조회 성공
- `manager@bodeul.app`
  - 본인 세션 조회 성공
  - 연결 예약 조회 성공
  - 연결 세션 리포트 조회 성공
- `patient@bodeul.app`, `guardian@bodeul.app`
  - 관리자 사용자 문서 직접 조회 `permission-denied` 확인

### 남은 범위

- `users` 읽기 권한은 아직 비관리자 문서에 대해 넓은 편이다.
- 현재 앱은 아래 경로에서 클라이언트가 직접 `users`를 검색한다.
  - 예약 연결 참여자 탐색: `email`, `phone`
  - 소셜 가입 중복 확인: `email`
- 이 부분까지 줄이려면 다음 중 하나가 필요하다.
  - 공개 검색 전용 컬렉션 분리
  - Firebase Functions 중계
  - 예약 연결 탐색 계약 재설계

## 다음 순서

1. `allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml` 정리
2. Firestore 오프라인 캐시 정책 정리
3. `users` 공개 검색 경로 제거 또는 중계 구조 설계

## 2026-05-04 2차 정리

### 반영한 내용

- `AndroidManifest.xml`에서 `android:allowBackup="false"`로 바꿔 앱 데이터 자동 백업을 끔
- `backup_rules.xml`, `data_extraction_rules.xml`에서 파일/DB/sharedpref/external 데이터를 전부 제외하도록 명시
- `ServiceLocator`에서 Firestore 인스턴스를 한 곳에서 초기화하도록 정리
- Firestore SDK 캐시를 기본 persistent cache 대신 `MemoryCacheSettings`로 고정해 앱 재시작 뒤 로컬 디스크에 문서와 mutation이 남지 않게 조정

### 남은 범위

- 메모리 캐시는 앱 재시작 뒤 데이터가 남지 않는 대신, 오프라인 재실행 경험은 줄어든다. 시연 범위에서 문제는 없지만 운영 요구가 생기면 민감도별 분리 전략이 필요하다.
- `users` 공개 검색 경로는 아직 남아 있다. 이 부분을 닫아야 Firestore 읽기 노출 범위를 한 단계 더 줄일 수 있다.

## 2026-05-04 3차 정리

### 반영한 내용

- `functions/index.js`에 `resolveLinkedParticipant`, `findSocialDuplicateEmailProvider` callable 함수를 추가했다.
- 예약 연결 참여자 탐색과 소셜 중복 이메일 확인을 Android 클라이언트의 직접 `users` 쿼리 대신 Functions 중계로 바꿨다.
- `FirebaseBookingRepository`는 `resolveLinkedParticipant` 호출 결과만 받아 연결 대상 사용자를 구성하도록 변경했다.
- `FirebaseAuthRepository`는 소셜 로그인 첫 가입 시 `findSocialDuplicateEmailProvider` 결과를 사용해 중복 이메일 계정을 판별하도록 변경했다.
- `firestore.rules`에서 `users` 컬렉션을 `get`과 `list`로 분리해 비관리자 클라이언트의 `users` 목록 쿼리를 차단했다.

### 실제 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `node --check functions/index.js`
- `firebase deploy --only functions:resolveLinkedParticipant,functions:findSocialDuplicateEmailProvider,firestore:rules --project bodeul-dev --non-interactive`
- `guardian@bodeul.app` 기준 `resolveLinkedParticipant(PATIENT, patient@bodeul.app)` 호출 성공
- `guardian@bodeul.app` 기준 `users` 컬렉션 이메일 쿼리 `PERMISSION_DENIED`
- `guardian@bodeul.app` 기준 `findSocialDuplicateEmailProvider` 호출 `PERMISSION_DENIED`

### 남은 범위

- 비관리자 사용자의 `users/{uid}` 직접 `get`은 아직 허용된다. 화면 요구사항이 정리되면 `self/admin/참여 관계` 기준으로 한 번 더 줄일 수 있다.

## 2026-05-04 4차 정리

### 반영한 내용

- `FirebaseBookingRepository`에서 예약 상세 화면을 구성할 때 `patient`, `guardian` 프로필은 `appointmentRequests` 문서에 이미 들어 있는 스냅샷 값으로 복원하도록 바꿨다.
- 예약 상세와 보호자 리포트의 배정 매니저 정보는 클라이언트가 `users/{managerUid}`를 직접 읽지 않고 callable `resolveAssignedManagerProfile`을 통해 받도록 정리했다.
- `FirebaseManagerRepository`도 대시보드와 과거 이력에서 환자/보호자 프로필을 `users` 직접 조회 대신 요청 문서 스냅샷으로 구성하도록 바꿨다.
- `firestore.rules`의 `users` 문서 직접 읽기 권한을 `self/admin`으로 축소했다.

### 실제 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `firebase deploy --only functions:resolveAssignedManagerProfile,firestore:rules --project bodeul-dev --non-interactive`
- Firebase Web SDK 실계정 검증
  - `guardian@bodeul.app` 기준 `resolveAssignedManagerProfile(request-seed-progress)` 호출 성공
  - `guardian@bodeul.app` 기준 본인 `users/{uid}` 문서 읽기 성공
  - `guardian@bodeul.app` 기준 매니저 `users/{uid}` 문서 직접 읽기 `permission-denied`
  - `patient@bodeul.app` 기준 보호자 `users/{uid}` 문서 직접 읽기 `permission-denied`

### 남은 범위

- `users` 직접 읽기는 이제 본인과 관리자만 가능하다.
- 향후 타 사용자 프로필을 더 노출해야 하는 화면이 생기면 Firestore 규칙을 다시 넓히지 말고, 같은 방식으로 요청 문서 스냅샷 또는 Functions 중계를 추가하는 쪽으로 가야 한다.
