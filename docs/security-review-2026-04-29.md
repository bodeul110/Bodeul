# 보안 리뷰 최신화 메모

기준일: 2026-05-04

이 문서는 2026-04-29 보안 점검 메모를 현재 코드 기준으로 다시 정리한 최신판이다.

## 확인 목적

- 기존 보안 리뷰 지적 사항이 현재 코드에서 얼마나 해소됐는지 확인한다.
- 현재 시점에서 남아 있는 보안 위험을 `런타임 앱`, `관리자 웹`, `Firebase 운영 도구` 관점으로 분리한다.
- 다음 작업 우선순위를 다시 맞춘다.

## 요약 판단

- 2026-04-29 기준의 가장 큰 문제였던 `과도한 Firestore 읽기 권한`, `앱 포함 네이버 client secret`, `민감 데이터 로컬 백업/디스크 캐시`는 해소됐다.
- 현재 가장 큰 남은 위험은 `App Check 미도입`, `민감 필드의 평문 저장`, `운영 도구의 토큰 처리`, `넓은 권한 표면`이다.
- 따라서 지금 단계의 보안 우선순위는 `AES-256 도입`보다 `클라이언트/Functions 남용 방지`와 `운영 도구 경계 분리`에 더 가깝다.

## 기존 지적 대비 현재 상태

### 1. 앱 코드에 AES-256 기반 로컬 암호화가 없다

상태: `부분 해결`

- 현재도 앱 코드에는 `Android Keystore`, `EncryptedSharedPreferences`, `MasterKey`, `AES` 직접 사용이 없다.
- 다만 로컬에 남는 민감 데이터를 줄이는 조치는 이미 들어갔다.
  - [AndroidManifest.xml](/D:/BoDeul/app/src/main/AndroidManifest.xml)에서 `allowBackup="false"`
  - [backup_rules.xml](/D:/BoDeul/app/src/main/res/xml/backup_rules.xml), [data_extraction_rules.xml](/D:/BoDeul/app/src/main/res/xml/data_extraction_rules.xml)에서 백업 제외
  - [ServiceLocator.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/ServiceLocator.java)에서 Firestore를 `MemoryCacheSettings`로 고정
- 현재 지속 저장되는 앱 내부 설정은 [PermissionGuidePreferences.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuidePreferences.java) 수준의 비민감 값이 중심이다.

판단:

- `단말에 민감 원문을 반드시 저장해야 하는 요구`는 많이 줄었지만,
- `앱 자체가 AES-256으로 민감 데이터를 보호한다`고 말할 수 있는 상태는 아니다.

### 2. 민감 정보가 Firestore 문서에 평문 필드로 저장된다

상태: `미해결`

- 예약 문서는 여전히 전화번호, 이메일, 특이사항, 환자 상태, 복약 메모를 평문 필드로 저장한다.
- 세션 문서와 리포트 문서도 동행 메모, 위치 요약, 약 복용 메모 등을 평문 필드로 저장한다.

판단:

- 현재는 `Firebase 저장소 기본 암호화 + 접근 제어`에 의존하는 구조다.
- 요구사항이 `앱/백엔드 애플리케이션 레벨의 AES-256 필드 암호화`까지 포함한다면 아직 충족하지 않는다.
- 반대로 현재 제품 단계에서 `검색/매칭/관리자 검토/리포트 조회`를 유지해야 한다면, 평문 저장은 운영상 트레이드오프로 볼 수 있다.

### 3. Firestore 접근 제어가 과도하게 열려 있다

상태: `해결`

- [firestore.rules](/D:/BoDeul/firestore.rules) 기준
  - `users` 직접 읽기: 본인 / 관리자만
  - `appointmentRequests`: 예약 참여자 / 관리자만
  - `companionSessions`: 세션 매니저 / 연결 예약 참여자 / 관리자만
  - `sessionReports`: 연결 세션 참여자 / 관리자만
- 예약 연결 탐색, 소셜 중복 이메일 확인, 배정 매니저 프로필 참조는 각각
  - `resolveLinkedParticipant`
  - `findSocialDuplicateEmailProvider`
  - `resolveAssignedManagerProfile`
  callable로 중계된다.
- 관련 정리는 [firestore-security-hardening.md](/D:/BoDeul/docs/firestore-security-hardening.md)에 따로 이어서 기록돼 있다.

### 4. 네이버 클라이언트 시크릿이 앱 리소스로 들어간다

상태: `해결`

- [app/build.gradle.kts](/D:/BoDeul/app/build.gradle.kts)에서 `naver_client_secret` 주입은 제거됐다.
- [BodeulApplication.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/BodeulApplication.java)에서 네이버 SDK 초기화도 제거됐다.
- [FirebaseAuthRepository.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java), [LoginActivity.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java)에서는 네이버 로그인을 비활성화 상태로 처리한다.

판단:

- `앱에 시크릿 포함` 문제는 해결됐다.
- 대신 `네이버 로그인 기능 자체는 닫힌 상태`다. 다시 열려면 서버 중계형 OAuth 설계가 필요하다.

### 5. 로컬 캐시와 백업 설정이 민감 데이터 앱 기준으로 보수적이지 않다

상태: `해결`

- `allowBackup=false`
- 전체 백업 제외 규칙 적용
- Firestore persistent cache 제거

판단:

- 2026-04-29 기준 우려는 현재 해소됐다.
- 다만 이 선택은 `오프라인 재실행 경험`을 일부 포기한 것이다. 향후 운영 요구가 생기면 민감도별 재설계가 필요하다.

### 6. 관리자 웹이 localStorage 플래그만으로 로그인 상태를 유지한다

상태: `해결`

- [admin-web/src/App.tsx](/D:/BoDeul/admin-web/src/App.tsx)에서 관리자 웹은 이제 `onAuthStateChanged`와 `users/{uid}.role == ADMIN` 검증을 함께 사용한다.
- 로그아웃도 실제 `signOut(auth)`를 호출한다.
- [admin-access-qa-checklist.md](/D:/BoDeul/docs/admin-access-qa-checklist.md)에 검증 절차를 따로 정리했다.

### 7. 매니저 서류 Storage 권한과 파일 검증이 느슨하다

상태: `부분 해결`

- [storage.rules](/D:/BoDeul/storage.rules)에서 경로는 `manager-documents/{managerUserId}/{documentKey}/{fileName}`로 제한돼 있다.
- 읽기 권한은 `ADMIN` 전체 또는 `본인 MANAGER`로 제한돼 있다.
- 이번 최신화에서 업로드 제약도 추가했다.
  - 허용 문서 키: `idCard`, `license`, `criminalRecord`
  - 허용 MIME: `application/pdf`, `image/*`
  - 허용 최대 크기: `10MB`
- [check-manager-document-storage.js](/D:/BoDeul/tools/firebase/check-manager-document-storage.js)와 정리 흐름으로 메타데이터-실객체 불일치와 고아 파일도 점검 가능하다.

판단:

- 권한 경계는 보수적으로 정리됐다.
- 다만 백신 스캔, 이미지/PDF 구조 검증 같은 내용까지는 아직 없다.

## 현재 남아 있는 위험

### 1. App Check 미도입

심각도: `중간`

- Android 앱, 관리자 웹, Functions callable, Firestore, Storage enforcement는 아직 켜져 있지 않다.
- 현재 callable은 인증/역할 검증으로 막고 있지만, 프로젝트 설정값과 로그인 계정이 있으면 자동화된 남용 시도 표면은 여전히 존재한다.
- 특히 `resolveLinkedParticipant`, `findSocialDuplicateEmailProvider`, `resolveAssignedManagerProfile` 같은 중계 함수는 보안상 올바르게 제한돼 있어도, 남용 방지 측면에서는 `App Check`가 없는 상태다.

권장:

1. Android: Play Integrity 기반 App Check
2. 관리자 웹: reCAPTCHA Enterprise 또는 디버그/개발용 분리
3. Functions / Firestore / Storage: 단계적 enforcement

최신 상태:

- 2026-05-04 기준 1단계 준비 작업을 시작했다.
  - Android 앱: Debug provider / Play Integrity provider 설치 코드 반영
  - 관리자 웹: reCAPTCHA 사이트 키 기반 초기화 경로 반영
  - Functions callable: `ENABLE_APPCHECK_ENFORCEMENT` 전환 스위치 반영
- 따라서 현재 상태는 `완전 미도입`이 아니라 `강제 적용 전 준비 단계`다.

### 2. 평문 필드 저장 구조

심각도: `중간`

- 예약, 세션, 리포트, 문의, 관리자 후속 처리 문서에 개인정보와 민감 메모가 평문으로 저장된다.
- 현재는 `규칙 축소 + 백업/캐시 최소화`로 위험을 줄인 상태지만, 필드 수준 암호화는 아니다.

권장:

- 요구사항이 진짜로 `AES-256 이상의 보안`을 뜻한다면, 어느 필드를 애플리케이션 레벨에서 암호화할지부터 데이터 계약을 다시 정의해야 한다.
- 다만 이 작업은 검색, 중복 확인, 관리자 검토, 리포트 조회 흐름과 충돌하므로 별도 설계 작업으로 다뤄야 한다.

### 3. Firebase 운영 도구의 토큰 처리

심각도: `중간`

- 현재는 refresh token 교환에 필요한 OAuth client secret을 저장소에 하드코딩하지 않고, 로컬 `local.properties` 또는 환경 변수, CI secret으로 분리하는 방향으로 정리 중이다.
- 또한 이 도구는 로컬 `firebase-tools.json`의 refresh token을 읽고 access token으로 갱신한다.

판단:

- `배포되는 앱/웹 보안`과는 계층이 다르다.
- 하지만 `운영자용 로컬 도구` 보안으로 보면 아직 개선 여지가 있다.

권장:

- 현재도 환경 변수 또는 로컬 비추적 설정 분리 방향으로 정리 중이며, CI는 `FIREBASE_OAUTH_CLIENT_SECRET` secret을 별도로 둔다.
- 장기적으로는 Firebase CLI 프로세스 호출이나 서비스 계정 기반 분리 검토

### 4. 권한 표면이 넓다

심각도: `낮음`

- 최신 기준으로는 [AndroidManifest.xml](/D:/BoDeul/app/src/main/AndroidManifest.xml)에 위험 권한을 남기지 않고 `INTERNET`만 유지한다.
- [PermissionGuideCatalog.java](/D:/BoDeul/app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java)는 현재 버전에서 시스템 권한을 미리 요청하지 않는다는 안내만 보여주고, 실제 권한 요청은 수행하지 않는다.

판단:

- 기존 우려는 상당 부분 해소됐다.
- 다만 추후 카메라, 위치, 연락처 같은 기능을 다시 넣을 때는 `기능 추가 시점 재검토` 원칙을 유지해야 한다.

권장:

- 새 기능이 생겨 권한이 필요해질 때만 manifest와 안내 문구를 함께 다시 추가
- 권한이 생기면 `필수/선택`, `수집 목적`, `대체 경로`를 같이 문서화

## 보안상 오해하지 말아야 할 항목

### Firebase API Key

- [app/google-services.json](/D:/BoDeul/app/google-services.json), [admin-web/firebase.ts](/D:/BoDeul/admin-web/firebase.ts)에 있는 Firebase API Key는 일반적으로 `프로젝트 식별자` 성격이 강하고, 그 자체를 비밀로 취급하지 않는다.
- 실제 보안은 `Authentication`, `Security Rules`, `App Check`, `Cloud Functions 권한 검증`이 담당한다.

## 최신 우선순위

1. `App Check` 도입 계획 수립과 단계적 적용
2. 운영 도구 토큰 처리 분리 방안 결정
3. Android 권한 최소화 검토
4. 필드 수준 암호화가 진짜 요구인지 제품/운영 관점에서 재정의
5. 필요 시 관리자/운영 로그 보존 기간과 민감 필드 마스킹 정책 추가

## AES 적용 범위 판단

- 최신 코드 기준 판단은 [aes-scope-assessment.md](/D:/BoDeul/docs/aes-scope-assessment.md)에 별도로 정리했다.
- 현재 구조에서 중요한 결론은 다음과 같다.
  - 앱이 직접 영속 저장하는 민감 비즈니스 데이터는 거의 없다.
  - 따라서 `지금 당장 앱 전역 AES-256 도입`보다 `민감 데이터 로컬 저장 금지`가 더 중요하다.
  - 다만 `오프라인 저장`, `문서 다운로드`, `자동저장`이 생기면 `AES-256-GCM + Android Keystore`를 필수 기준으로 본다.

## 이번 최신화 정리

- 해결된 항목과 남은 위험을 현재 코드 기준으로 다시 분리했다.
- Firestore / Storage / 관리자 웹 / 운영 도구까지 범위를 넓혀 재점검했다.
- 새로 반영한 보안 하드닝은 `Storage 업로드 MIME/크기 제한`이다.
