# 보들

보들은 환자, 보호자, 동행 매니저를 연결하는 병원 동행 플랫폼입니다.

현재 프로젝트의 최상위 기능 기준은 최신 `보들 플랫폼 기능설명서`를 따른다. 문서와 구현은 아래 다섯 축을 기준으로 정렬한다.

1. 진입 및 계정 설정
2. 서비스 요청 및 예약
3. 매칭 및 홈 화면
4. 실시간 동행
5. 서비스 종료 및 정산

## 현재 프로젝트 상태

이 저장소는 Java, XML 레이아웃, Gradle 기반의 네이티브 Android 프로젝트입니다.

로그인, 예약, 보호자 진행 현황, 매니저 홈과 가이드, 관리자 운영 화면은 동작하며 Firebase 설정이 없을 때는 데모 모드로 실행됩니다. 일부 외부 연동 기능은 기능설명서 구조를 먼저 맞추고, 실제 서비스 연동은 Firebase 또는 후속 API 계약에 맞춰 확장합니다.

## 문서

- [현재 구현 상태](docs/implementation-status.md)
- [협업 규칙](docs/collaboration-rules.md)
- [전면 개편 목표 정리](docs/restructure-target-map.md)
- [MVP 범위](docs/mvp-scope.md)
- [아키텍처 초안](docs/architecture-draft.md)
- [팀 작업 분담](docs/team-task-breakdown.md)
- [데이터 및 API 초안](docs/data-api-draft.md)
- [Firebase 설정](docs/firebase-setup.md)

## 실행

```powershell
.\gradlew.bat assembleDebug
```

## 협업 설정

- 공용 원격 저장소는 `origin`(`git@github.com:bodeul110/Bodeul.git`) 기준으로 사용합니다.
- GitHub 저장소에서 팀원을 `Collaborator`로 추가한 뒤 같은 저장소를 함께 사용합니다.
- `master` 브랜치는 기준 브랜치로 유지하고, 기능 개발은 가능하면 별도 브랜치에서 진행합니다.
- 여러 작업자가 동시에 들어올 때의 상세 규칙은 [협업 규칙](docs/collaboration-rules.md)을 기준으로 맞춥니다.

## 협업 절차

새로 작업을 시작할 때는 아래 순서로 진행합니다.

```powershell
git clone git@github.com:bodeul110/Bodeul.git
cd BoDeul
git checkout -b feature/작업이름
```

작업 전에 `누가 최근에 작업했는지`, `로컬과 원격 중 어느 쪽이 최신인지`부터 확인합니다. 상세 명령과 판별 기준은 [협업 규칙](docs/collaboration-rules.md)에 정리돼 있습니다.

작업 중에는 기준 브랜치 변경 사항을 먼저 반영하고, 푸시 전 빌드를 확인합니다.

```powershell
git fetch origin
git log --format="%h %an %ad %s" --date=short -10
git rev-list --left-right --count HEAD...origin/master
git pull --rebase origin master
.\gradlew.bat assembleDebug
git add .
git commit -m "기능 설명"
git push -u origin feature/작업이름
```

브랜치를 푸시한 뒤에는 GitHub에서 Pull Request를 생성하고, 리뷰 후 `master`에 머지합니다.

## 데모 로그인

- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`
