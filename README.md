# 보들

보들은 환자, 보호자, 매니저를 연결하는 병원 동행 플랫폼입니다.

## 기준 문서

- 문서 홈: [docs/README.md](docs/README.md)
- 최신 기능 기준: `docs/local/보들_플랫폼_기능설명서.pdf`
- 화면/흐름 재구성 기준: [화면 개편 목표 정리](docs/planning/screen-restructure-target.md)
- 실제 구현 기준: [현재 구현 상태](docs/status/implementation-status.md)

## 현재 저장소 구성

- `app/`: Android 앱 본체
- `admin-web/`: 관리자 서류 심사 및 운영 웹
- `functions/`: Firebase Functions
- `tools/firebase/`: 기준선 초기화, 상태 점검, 백업/복원, 운영 리포트 도구
- `docs/`: 문서 홈과 주제별 문서 디렉터리
- `design_refs/`: Figma/디자인 참조 자산
- `.github/workflows/`: CI 프리플라이트 워크플로
- `gradle/`: Gradle 버전 카탈로그와 wrapper
- 루트 `firestore.rules`, `storage.rules`, `firebase.json`: Firebase 배포/권한 설정

## 기능 축

1. 진입 및 계정 설정
2. 서비스 요청 및 예약
3. 매칭 및 홈 화면
4. 실시간 동행 중
5. 서비스 종료 및 정산

최신 기능설명서에는 AI 음성 정리, OCR 기반 복약 정보 정리, 건강정보 프로필 영속 저장, 결제 세부 정책 같은 후속 기획 메모도 포함되어 있습니다. 이 항목들은 별도 확정 전까지 `기획 메모`로 취급합니다.

## 먼저 볼 문서

1. [문서 홈](docs/README.md)
2. [현재 구현 상태](docs/status/implementation-status.md)
3. [내부 테스트 가이드](docs/operations/internal-test-guide.md)
4. [화면 개편 목표 정리](docs/planning/screen-restructure-target.md)
5. [협업 규칙](docs/operations/collaboration-rules.md)
6. [인프라 개요](docs/architecture/infrastructure.md)

문서 디렉터리와 정리 기준은 [문서 홈](docs/README.md)을 기준으로 확인합니다.

## 실행

```powershell
.\gradlew.bat assembleDebug
```

## 작업 시작 순서

```powershell
git fetch origin
git pull --rebase origin master
.\gradlew.bat assembleDebug
```

작업 전 동기화와 최근 작업자 확인 절차는 [협업 규칙](docs/operations/collaboration-rules.md)을 기준으로 합니다.

## 데모 로그인

- 관리자: `admin@bodeul.app` / `bodeul1234`
- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`

더미 데이터와 테스트 순서는 [내부 테스트 가이드](docs/operations/internal-test-guide.md)에 정리되어 있습니다.
