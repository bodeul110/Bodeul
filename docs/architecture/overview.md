# 아키텍처 초안

기준일: 2026-06-23

## 기준 문서

- 최신 기능 기준: `../local/보들_플랫폼_기능설명서.pdf` (로컬 참조 파일)
- 화면 구조 기준: [화면 개편 목표 정리](../planning/screen-restructure-target.md)
- 실제 구현 기준: [현재 구현 상태](../status/implementation-status.md)

## 저장소 구성

```text
BoDeul
  app/                 Android 앱
  admin-web/           관리자 운영/심사 웹
  functions/           Firebase Functions
  tools/firebase/      기준선 초기화, 점검, 리포트, 백업/복원 도구
  docs/                주제별 프로젝트 문서
  design_refs/         디자인 참조 자산
  gradle/              Gradle wrapper와 버전 카탈로그
  .github/workflows/   CI 프리플라이트
```

루트의 `firestore.rules`, `storage.rules`, `firebase.json`, `firestore.indexes.json`은 Firebase 배포/권한 설정 파일이다. `.tmp/`, `.gradle/`, `build/`, IDE 설정, `node_modules`는 로컬 생성물 또는 개인 환경 파일로 보고 기준 구조에서 제외한다.

## 앱 계층 원칙

- Activity/Fragment는 흐름 제어만 맡는다.
- 화면 조합은 `Coordinator`가 맡는다.
- 뷰 반영은 `Binder`가 맡는다.
- 문구/상태 표현은 `PresentationFormatter`가 맡는다.
- 데이터 접근은 `Repository`가 맡는다.

이 원칙은 Android 앱 전체에 유지한다.

## 기능 경계

### 1. 진입 및 계정 설정

- `ui.auth`
- 스플래시, 로그인, 회원가입, 권한 안내, 역할 선택, 프로필 보완

### 2. 서비스 요청 및 예약

- `ui.booking`
- 건강 프로필, 예약 생성, 병원 선택, 위치 선택, 결제 확인, 예약 상세, 후속 처리, 건강정보 읽기

### 3. 매칭 및 홈 화면

- `ui.home`
- `ui.manager`
- 환자/보호자 홈, 매니저 홈, 매칭 대기/확정, 준비사항

### 4. 실시간 동행 중

- `ui.manager`, `ui.report`, `ui.booking`
- 진료 가이드, 진행 상태, 보호자 공유, 위치/현장 메모, 안심 채팅, 최종 리포트

### 5. 서비스 종료 및 정산

- `ui.booking`, `ui.manager`, `ui.admin`
- 후기, 후속 처리, 마이페이지, 정산, 문의, SOS, 관리자 운영

### 6. 관리자 웹 및 운영 도구

- `admin-web/`
  - 관리자 로그인, 서류 심사, 운영 상태 확인
- `tools/firebase/`
  - 기준선 초기화, 더미 데이터 주입, 점검, 백업/복원, 운영 리포트

## 데이터 경계

- 인증: Firebase Auth
- 앱/운영 데이터: Firestore
- 문서 원본 파일: Firebase Storage
- 운영 보조 로직: Cloud Functions
- 개발/오프라인 대체: Mock Repository

## 현재 구조 메모

- Android 앱과 관리자 웹은 같은 저장소 안에 있지만 런타임은 분리돼 있다.
- Storage 원본 파일과 Firestore 메타데이터는 함께 관리한다.
- 최신 기능설명서의 AI/STT/OCR/건강정보 프로필 영속 저장 메모는 아직 별도 모듈로 분리하지 않는다.
- 해당 항목이 실제 범위로 승격되면 `app/` 안에 별도 도메인 경계를 추가하는 방식이 맞다.
