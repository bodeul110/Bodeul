# 디렉터리 구조 점검 기록 (2026-06-23)

## 확인한 내용

- 루트 디렉터리는 Android 앱, 관리자 웹, Firebase Functions, Firebase 운영 도구, 문서, 디자인 참조, Gradle/CI 설정으로 분리돼 있다.
- Android 앱은 `app/src/main/java/com/example/bodeul` 아래 `data`, `domain`, `firebase`, `ui`, `util` 축으로 나뉜다.
- UI 계층은 `admin`, `auth`, `booking`, `chat`, `common`, `health`, `home`, `manager`, `report`, `support`로 기능별 분리돼 있다.
- 데이터 계층은 공통 인터페이스와 `firebase`, `mock`, `map` 구현으로 나뉜다.
- Functions는 `functions/index.js` 집계 파일과 `functions/src/` 기능별 모듈로 분리돼 있다.
- 운영 도구는 `tools/firebase` 아래 실행 스크립트, `lib`, `templates`, `backups`, `reports`로 분리돼 있다.
- 문서는 `docs/` 아래 `architecture`, `planning`, `operations`, `security`, `design`, `features`, `reports`, `status`, `archive`, `local`로 정리돼 있다.
- 디자인 참조는 `design_refs/` 아래 공용 참조와 `local/` 원본 자산으로 분리돼 있다.

## 핵심 구조

```text
BoDeul
  app/                    Android 앱
  admin-web/              관리자 웹
  functions/              Firebase Functions
  tools/firebase/         Firebase 운영 도구
  docs/                   프로젝트 문서
  design_refs/            디자인 참조
  gradle/                 Gradle wrapper와 버전 카탈로그
  .github/workflows/      CI 워크플로
  firestore.rules         Firestore 권한 규칙
  storage.rules           Storage 권한 규칙
  firebase.json           Firebase 배포 설정
```

## 로컬/생성물

- `.gradle/`, `build/`, `functions/node_modules/`는 빌드/의존성 생성물이다.
- `.tmp/`는 과거 점검, GitHub issue 본문, Playwright 실행 등 임시 산출물이 섞인 공간이다.
- `.idea/`, `.vscode/`는 개인 개발 환경 파일이다.
- `docs/local/`과 `design_refs/local/`은 원본성 강한 로컬 참조 파일을 담으며, `README.md`만 기준 문서로 본다.
- 루트 `package-lock.json`은 내용이 빈 npm lock 파일이고 `.gitignore`에서 제외 대상으로 지정돼 있다.

## 판단

- 현재 핵심 디렉터리 경계는 프로젝트 역할과 맞다.
- 문서에서 누락됐던 `.github`, `design_refs`, `gradle`, Firebase 루트 설정 파일 설명을 보강했다.
- 당장 이동하거나 삭제해야 할 핵심 소스 디렉터리는 없다.

## 남은 범위

- `.tmp/`와 루트 `package-lock.json`은 정리 후보지만, 사용자 승인 없이 삭제하지 않았다.
- `docs/status/implementation-status.md`는 누적 이력이 커진 상태라 추후 별도 이력 분리 대상으로 볼 수 있다.
- Git CLI가 현재 PATH에 없어 추적/미추적 파일 기준 확인은 하지 못했고, `.gitignore` 기준으로만 로컬 생성물을 판단했다.

## 검증

- 루트/주요 하위 디렉터리 파일 수를 확인했다.
- Markdown 링크 존재 검사: `checked=49 broken=0`
