# 아키텍처 개요

기준일: 2026-07-17

## 저장소 구성

```text
BoDeul/
  app/                 Android 앱
  core-api/            Spring Core API와 PostgreSQL migration
  functions/           Firebase Functions
  tools/firebase/      점검, 리포트, 백업/복원 도구
  docs/                공용 설계·운영·검증 문서
  design_refs/         디자인 참조 자산
  .github/workflows/   메인 CI/CD

bodeul-admin-web/      별도 저장소의 Next.js 관리자 웹/서버
```

루트 `firebase.json`, `firestore.rules`, `storage.rules`, `firestore.indexes.json`은 Android·Functions와 Firebase 권한 설정이다. 관리자 웹 build와 Vercel 배포 설정은 별도 저장소가 소유한다.

## Android 계층

- Activity/Fragment는 화면 흐름과 연결만 맡는다.
- 화면 조합은 Coordinator가 맡는다.
- 뷰 반영은 Binder가 맡는다.
- 표현 변환은 PresentationFormatter가 맡는다.
- Firebase와 Core API 데이터 접근은 Repository/Service가 맡는다.

## 서버 경계

- 환자·보호자·매니저: Android/웹 → Spring Core API → PostgreSQL
- 관리자: 브라우저 → Next.js 관리자 서버 → PostgreSQL
- 공통 인증: Firebase Auth ID token
- 파일: Firebase Storage
- 푸시: Firebase Functions + FCM
- 외부 서버 API: Spring Core API

상세 구조는 [현재 인프라 구성도](infra-overview.md), 화면·도메인 역할은 [Android 앱 구조](app-architecture.md)를 따른다.
