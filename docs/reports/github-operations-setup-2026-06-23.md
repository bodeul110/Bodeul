# GitHub 운영 기반 구축 보고서

작성일: 2026-06-23

## 구현한 내용

- PR 템플릿을 추가해 변경 범위, 검증, 배포/운영 확인 항목을 PR마다 기록하도록 했다.
- 버그 제보, 기능 요청, 작업 항목용 GitHub Issue Form을 추가했다.
- CODEOWNERS를 추가해 GitHub Actions, Firebase, Functions, Android 앱, 관리자 웹 변경의 기본 확인 대상을 명시했다.
- Dependabot 설정을 추가해 GitHub Actions, Gradle, 관리자 웹 npm, Functions npm 업데이트 PR을 주 단위로 생성하도록 했다.
- `master-pr-ci-protection` ruleset을 활성화해 `master` 삭제, non-fast-forward 업데이트, 직접 반영을 막고 `preflight` 상태 체크를 요구하도록 했다.
- `dev`, `production` GitHub Environment를 만들고 protected branch 정책을 적용했다. `production`은 `bodeul110` 승인자를 요구한다.
- 병합 후 브랜치 자동 삭제를 활성화했다.
- 제품/운영 후속 범위를 GitHub Issue #27-#34로 옮겨 추적할 수 있게 했다.

## 변경된 범위

- `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/ISSUE_TEMPLATE/bug_report.yml`
- `.github/ISSUE_TEMPLATE/feature_request.yml`
- `.github/ISSUE_TEMPLATE/task.yml`
- `.github/ISSUE_TEMPLATE/config.yml`
- `.github/CODEOWNERS`
- `.github/dependabot.yml`
- `docs/reports/github-operations-setup-2026-06-23.md`

앱 코드, Firebase 런타임 코드, Gradle 의존성 버전은 변경하지 않았다.

## 검증

- `yq e '.' .github/dependabot.yml`
- `yq e '.' .github/ISSUE_TEMPLATE/*.yml`
- `git diff --check` 대상 `.github` 파일
- GitHub PR #26 `preflight` 체크 성공
- `./gradlew.bat assembleDebug --console=plain` 성공

## 남은 범위

- GitHub Projects 세부 보드 구성은 현재 `gh` 토큰에 `read:project` 권한이 없어 보류했다. 필요 시 `gh auth refresh -s read:project` 실행 후 진행한다.
- Dependabot PR은 자동 병합하지 않는다. 생성 후 변경 범위와 검증 결과를 확인해 병합한다.
- 배포 워크플로를 추가할 때는 `dev`, `production` Environment와 Firebase 프로젝트 분리 정책을 연결한다.
