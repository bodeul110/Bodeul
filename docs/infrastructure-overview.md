# 인프라 개요

기준일: 2026-06-19

이 문서는 현재 `BoDeul` 프로젝트가 어떤 실행 구성으로 동작하는지 빠르게 파악하기 위한 인프라 기준 문서다. 화면 설계나 기능 범위는 기능설명서와 구현 상태 문서를 따르고, 이 문서는 런타임 구성과 운영 경계를 설명한다.

## 1. 전체 구성

```text
Android 앱(app/)
  ├─ Firebase Auth
  ├─ Firestore
  ├─ Firebase Storage
  ├─ Cloud Functions
  └─ Kakao SDK(로그인, 지도)

관리자 웹(admin-web/)
  ├─ Firebase Auth
  ├─ Firestore
  └─ Firebase Storage

운영 도구(tools/firebase/)
  ├─ 기준선 초기화
  ├─ 샘플 데이터 주입
  ├─ 상태 점검
  ├─ 백업/복원
  └─ 운영 리포트/프리플라이트

배포/검증
  └─ GitHub Actions(.github/workflows/)
```

핵심은 별도 상시 백엔드 서버를 두는 구조가 아니라, `Firebase 중심 BaaS + Android 앱 + 관리자 웹 + 로컬 운영 도구` 구조라는 점이다.

## 2. 클라이언트 구성

### 2-1. Android 앱

- 위치: `app/`
- 기술 스택: `Java + XML`
- 화면 구조: `Activity -> Coordinator -> Binder -> ScreenModel/Formatter -> Repository`
- 역할:
  - 환자/보호자/매니저 사용자 흐름
  - 예약, 동행, 리포트, 후기, 문의, 서류 등록
  - 실시간 위치 확인, 안심 채팅, 카카오 지도 연동

앱은 [`ServiceLocator.java`](../app/src/main/java/com/example/bodeul/data/ServiceLocator.java)에서 실제 Firebase 구현과 Mock 구현을 분기한다.

### 2-2. 관리자 웹

- 위치: `admin-web/`
- 기술 스택: `React + Vite`
- 역할:
  - 관리자 로그인
  - 매니저 서류 심사/미리보기
  - 운영 상태 확인
  - 관리자 민감정보 마스킹과 유휴 세션 종료

웹은 [`firebase.ts`](../admin-web/firebase.ts)에서 Firebase App/Auth/Firestore/Storage를 직접 초기화한다.

## 3. Firebase 구성

### 3-1. Authentication

- 용도: 사용자 로그인, 역할 구분, 관리자 인증
- 사용 위치:
  - Android 앱 로그인/세션 유지
  - 관리자 웹 로그인
  - Functions callable 인증

### 3-2. Firestore

- 용도: 주요 서비스 데이터 저장
- 대표 컬렉션:
  - `users`
  - `appointmentRequests`
  - `companionSessions`
  - `sessionReports`
  - `appointmentFollowUps`
  - `supportInquiries`
  - 관리자 운영 컬렉션들

앱에서는 Firestore 디스크 캐시를 끄고 메모리 캐시만 사용한다. 이 설정은 [`ServiceLocator.java`](../app/src/main/java/com/example/bodeul/data/ServiceLocator.java)에서 적용한다.

### 3-3. Firebase Storage

- 용도: 매니저 원본 서류 저장
- 대표 경로:
  - `manager-documents/{managerUserId}/{documentKey}/{fileName}`

현재 문서 키는 아래를 기준으로 운영한다.

- `idCard`
- `license`
- `healthCertificate`
- `criminalRecord`

### 3-4. Cloud Functions

- 위치: `functions/`
- 런타임: Firebase Functions v2
- 기본 리전: `asia-northeast3`
- 역할:
  - Kakao/Naver custom token 발급
  - 연결 사용자 조회 보조
  - 관리자/리마인더 전달 작업
  - 예약/유저 동기화 보조

App Check 강제는 함수 옵션으로 준비돼 있지만, 현재는 환경 변수 기반 스위치로만 열어둔 상태다.

## 4. 운영 도구 구성

위치: `tools/firebase/`

주요 역할:

- Firestore 기준선 초기화
- 샘플 데이터 주입
- 매니저 서류 Storage 점검/정리
- 백업/복원
- 상태 점검, readiness 점검
- 운영 리포트 생성
- 로컬/CI 프리플라이트

이 도구들은 개발자와 운영 확인용이며, 앱 런타임에는 포함되지 않는다.

## 5. 배포 및 검증

### 5-1. Android 앱

- 기본 검증: `assembleDebug`
- 필요 시 `testDebugUnitTest`
- 내부 테스트 기준 계정/더미 데이터는 [`internal-test-guide.md`](internal-test-guide.md)를 따른다.

### 5-2. 관리자 웹

- 기본 검증:
  - `npm --prefix admin-web run lint`
  - `npm --prefix admin-web run build`

### 5-3. GitHub Actions

- 위치: `.github/workflows/`
- 역할:
  - Android 빌드 프리플라이트
  - Firebase 운영 점검 스크립트 실행
  - 리포트 산출물 업로드

## 6. 실시간 기능 구성

### 6-1. 위치 공유

- 매니저 앱이 위치를 공유하면 세션 문서에 위치와 시각을 저장한다.
- 사용자 앱은 해당 세션을 다시 읽어 `실시간 위치 확인` 화면에 반영한다.
- 카카오 지도는 현재
  - 외부 앱/링크 fallback
  - 네이티브 지도 SDK 화면
  를 함께 사용한다.

### 6-2. 안심 채팅

- 세션 단위 `chatMessages`를 기준으로 동작한다.
- 현재는 텍스트 메시지 중심이며, 푸시/읽음/첨부는 후속 범위다.

## 7. 환경 구분

### 7-1. Firebase 모드

- `google-services.json` 등 Firebase 설정이 있으면 실제 Firebase 저장소를 사용한다.
- Android 앱, 관리자 웹, Functions, 운영 도구가 같은 Firebase 프로젝트를 기준으로 연결된다.

### 7-2. Mock 모드

- Firebase 설정이 없으면 Android 앱은 Mock Repository로 동작한다.
- 기능 데모나 구조 검증은 가능하지만, 운영 검증 기준은 Firebase 모드다.

## 8. 보안 기준

- Firestore 권한: `firestore.rules`
- Storage 권한: `storage.rules`
- 백업 제한: Android `allowBackup=false`
- Firestore 디스크 캐시 비활성화
- 관리자 웹 민감정보 마스킹 및 유휴 세션 종료
- App Check:
  - Android/웹 초기화 코드는 준비됨
  - Play Console 준비 전까지는 강제 적용 보류

현재 보안 상태의 상세 내용은 아래 문서를 기준으로 본다.

- [`firestore-security-hardening.md`](firestore-security-hardening.md)
- [`security-review-2026-04-29.md`](security-review-2026-04-29.md)
- [`aes-scope-assessment.md`](aes-scope-assessment.md)

## 9. 현재 운영상의 주의점

- 실제 운영 인프라는 Firebase 프로젝트와 규칙 설정에 강하게 의존한다.
- App Check는 아직 준비 단계라, 최종 운영 전에 강제 적용 검증이 필요하다.
- 위치 추적, 카카오 지도, 관리자 심사 흐름은 Android 앱과 관리자 웹, Storage 규칙이 함께 맞아야 한다.
- 개발자 로컬 환경 파일(`design_refs`, `package-lock.json` 등)은 인프라 기준 문서에 포함하지 않는다.

## 10. 같이 봐야 할 문서

1. `local/보들_플랫폼_기능설명서.pdf`
2. [`restructure-target-map.md`](restructure-target-map.md)
3. [`implementation-status.md`](implementation-status.md)
4. [`architecture-draft.md`](architecture-draft.md)
5. [`firebase-setup.md`](firebase-setup.md)
6. [`firebase-operations-tools.md`](firebase-operations-tools.md)
