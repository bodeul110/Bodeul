# 보들

보들은 환자, 보호자, 동행 매니저를 연결하는 병원 동행 매칭 서비스입니다.

현재 MVP는 아래 흐름을 기준으로 구성합니다.

1. 환자 또는 보호자가 병원 동행을 신청합니다.
2. 보들이 가까운 동행 매니저를 매칭합니다.
3. 방문 전 안내와 리마인드 정보를 전달합니다.
4. 매니저가 병원 및 진료과별 가이드를 따라 동행을 진행합니다.
5. 보호자가 진행 상황과 위치 공유를 확인합니다.
6. 동행 종료 후 진료 리포트를 전달합니다.

## 현재 프로젝트 상태

이 저장소는 Java, XML 레이아웃, Gradle 기반의 네이티브 Android 프로젝트입니다.

로그인, 병원 동행 신청, 보호자 리포트, 관리자 수동 매칭, 매니저 홈, 동행 가이드 화면은 동작하며 Firebase 설정이 없을 때는 데모 모드로 실행됩니다.

## 문서

- [현재 구현 상태](docs/implementation-status.md)
- [MVP 범위](docs/mvp-scope.md)
- [아키텍처 초안](docs/architecture-draft.md)
- [팀 작업 분담](docs/team-task-breakdown.md)
- [데이터 및 API 초안](docs/data-api-draft.md)
- [Firebase 설정](docs/firebase-setup.md)

## 실행

```powershell
.\gradlew.bat assembleDebug
```

## 데모 로그인

- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`
