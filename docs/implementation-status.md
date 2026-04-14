# 현재 구현 상태

기준일: 2026-04-14

이 문서는 현재 저장소에서 실제로 동작하는 기능과 아직 미구현인 범위를 빠르게 파악하기 위한 협업 기준 문서다.

## 1. 현재 동작하는 기능

### 인증과 로그인

- 스플래시에서 로그인 상태를 확인하고 다음 화면으로 자동 분기
- 역할 선택 화면 제공
- 이메일 회원가입, 이메일 인증, 이메일 로그인
- 비밀번호 재설정 메일 발송
- 인증 메일 재발송
- 구글 로그인
- 카카오 로그인
- 네이버 로그인
- 소셜 로그인 시 Firebase custom token 인증
- 소셜 첫 로그인 시 `users/<uid>` 자동 생성
- 로그인 방식이 달라도 같은 이메일로 중복 계정을 새로 만드는 것은 차단
- 소셜 제공자에서 이름, 이메일, 전화번호가 더 최신 값으로 내려오면 기존 `users` 문서를 보강
- 이름이나 연락처가 비어 있으면 `프로필 보완 화면`으로 먼저 이동
- 로그아웃
- Firebase 설정이 없을 때는 데모 모드로 자동 전환

### 매니저 기능

- 매니저 홈 화면
- Firestore의 `companionSessions`, `appointmentRequests`, `users`, `hospitalGuides`, `sessionReports`를 조합해 대시보드 표시
- 매니저 동행 가이드 화면
- 단계 진행 상태 표시
- 다음 단계로 이동
- 보호자 공유 메시지 저장
- 복약 메모 저장
- 세션 리포트 저장
- 세션이 없는 경우 오류 대신 정상 빈 상태 안내 표시

### 공통 앱 흐름

- 로그인 후 역할에 따라 매니저 홈 또는 일반 홈으로 이동
- 자동 로그인 시에도 프로필 보완 필요 여부를 다시 확인
- Firebase 설정이 정상일 때 실제 Firebase 모드로 동작
- Firebase 설정이 없을 때는 목업 데이터로 실행 가능

## 2. 부분 구현 또는 플레이스홀더

### 화면은 있으나 실제 기능은 미구현

- 환자/보호자 동행 신청 화면
- 보호자 리포트 화면
- 관리자 화면
- 매니저 홈의 `서류 등록`, `스케줄 등록`

위 항목은 현재 공통 플레이스홀더 또는 안내용 버튼 상태다.

### 데이터는 있으나 운영 흐름이 미완성

- 관리자에서 직접 매칭하는 UI
- 환자/보호자가 자신의 요청 상태를 조회하는 실제 화면
- 보호자 실시간 상태 조회 화면
- 관리자용 병원 가이드 관리 화면
- 소셜 계정 연동 해제와 회원탈퇴 정책 UI
- 설정/내 정보 화면

## 3. Firebase 연동 범위

### 현재 사용 중인 Firebase 기능

- Firebase Authentication
  - Email/Password
  - Google
  - Custom Token
- Cloud Functions
  - `kakaoCustomToken`
  - `naverCustomToken`
- Cloud Firestore

### 현재 앱이 기대하는 주요 컬렉션

- `users`
  - 필드 예시: `name`, `email`, `phone`, `role`, `provider`, `providerUserId`
- `appointmentRequests`
- `companionSessions`
- `hospitalGuides`
- `sessionReports`

## 4. 구현 완료 기준으로 볼 수 있는 사용자 흐름

### 매니저

1. 역할 선택
2. 이메일/구글/카카오/네이버 로그인
3. 필요하면 프로필 보완
4. 매니저 홈 진입
5. Firestore에 연결된 세션이 있으면 동행 가이드 진입
6. 단계 진행, 보호자 공유, 복약 메모, 리포트 저장

### 환자/보호자

1. 역할 선택
2. 이메일/구글/카카오/네이버 로그인
3. 필요하면 프로필 보완
4. 일반 홈 진입
5. 이후 실제 기능 화면은 아직 플레이스홀더

## 5. 아직 구현되지 않은 핵심 기능

- 환자/보호자 신청 폼
- 요청 생성과 조회
- 매칭 생성과 변경 UI
- 보호자 진행 현황 화면
- 관리자 운영 화면
- 이미지 업로드
- 알림
- 설정/내 정보 편집 화면
- 회원탈퇴
- 외부 서비스 운영 정책 정리

## 6. 협업 시 주의할 점

- 로그인 기능 수정 시 이메일, 구글, 카카오, 네이버 4가지 로그인 방식을 같이 확인해야 한다.
- 소셜 로그인 수정 시 Firebase Functions도 함께 확인해야 한다.
- `users` 문서의 `role`, `provider`, `providerUserId`는 인증 분기와 중복 계정 정책에 직접 영향을 준다.
- 매니저 화면은 세션 1건 기준으로 동작한다.
- `assembleDebug` 기준 빌드는 현재 성공 상태다.

## 7. 최근 정리 사항

기준일: 2026-04-14

### 이번에 정리한 내용

- 로그인 SDK 키를 `local.properties` 우선, `gradle.properties` 보조 방식으로 읽도록 빌드 구성을 정리했다.
- 저장소에 남기면 안 되는 네이버 클라이언트 ID와 시크릿 기본값은 비워 두고, 값이 없을 때도 앱 빌드는 유지되도록 정리했다.
- 승인 없이 올라가 있던 Android Gradle Plugin 버전 변경은 원래 버전으로 되돌렸다.
- 로그인과 프로필 보완 화면에서 이름, 이메일, 연락처를 같은 규칙으로 정규화하도록 정리했다.
- 소셜 로그인 제공자가 이름을 주지 않은 경우 잘못된 기본 이름을 넣지 않고 프로필 보완 화면으로 보내도록 수정했다.
- 현재 개발 환경의 `local.properties` 에 네이버 로그인 값을 다시 넣어 설정 누락 상태를 복구했다.
- `testDebugUnitTest`, `assembleDebug` 재검증까지 다시 통과했다.

### 변경된 범위

- `app/build.gradle.kts`
- `app/src/main/java/com/example/bodeul/util/UserProfileSanitizer.java`
- `app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java`
- `app/src/main/java/com/example/bodeul/ui/auth/ProfileCompletionActivity.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/test/java/com/example/bodeul/UserProfileSanitizerTest.java`
- `local.properties`
- `build.gradle.kts`
- `gradle.properties`
- `docs/firebase-setup.md`
- `docs/implementation-status.md`

### 아직 남은 범위

- 각 개발 환경의 `local.properties` 에 네이버 로그인 값을 채워 실제 소셜 로그인까지 다시 검증
- 이름 또는 연락처 제공이 비어 있는 실제 카카오·네이버 계정으로 프로필 보완 분기가 기대대로 동작하는지 실기기 재확인
- 운영 배포 전 카카오 앱 키와 네이버 콘솔 설정이 최신 값인지 최종 재확인

## 8. 관련 핵심 파일

- 인증
  - `app/src/main/java/com/example/bodeul/ui/auth/`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- 매니저
  - `app/src/main/java/com/example/bodeul/ui/manager/`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- Firebase Functions
  - `functions/index.js`
- 설정 문서
  - `docs/firebase-setup.md`
