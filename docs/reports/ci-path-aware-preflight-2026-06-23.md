# CI 경로 기반 preflight 최적화 보고서

## 구현한 내용

- `preflight` 필수 체크 이름은 유지하면서 PR 변경 파일을 Android, 관리자 웹, Functions, Firebase 도구, 문서 전용 범위로 분류하도록 변경했다.
- Android 영향이 있는 변경에서만 JDK 설정, `google-services.json` 복원, Android 프리플라이트를 실행하도록 했다.
- 관리자 웹 변경은 `npm ci`와 `npm run build`를 실행하도록 했다.
- Functions 변경은 `npm ci`로 lockfile 기반 설치 검증을 실행하도록 했다.
- Firebase 도구와 GitHub 설정 변경은 Firebase 도구 JavaScript 문법 검사와 가벼운 프리플라이트 요약 생성을 실행하도록 했다.
- 문서 전용 변경은 무거운 빌드를 건너뛰고 CI 범위 요약 artifact만 남기도록 했다.
- PR별 중복 실행을 줄이기 위해 workflow concurrency를 설정했다.
- Android 빌드와 단위 테스트를 한 번의 Gradle 호출로 묶어 중복 초기화 비용을 줄였다.

## 변경된 범위

- `.github/workflows/android-preflight.yml`
- `tools/firebase/run-local-preflight.js`
- `docs/reports/ci-path-aware-preflight-2026-06-23.md`

## 검증

- `yq e '.' .github/workflows/android-preflight.yml` 성공
- `node --check tools/firebase/run-local-preflight.js` 성공
- `node --check tools/firebase/run-ci-preflight.js` 성공
- `node tools/firebase/run-ci-preflight.js --skip-workflow --skip-build --skip-tests --summary tools/firebase/reports/firebase-tools-light-preflight` 성공
- `ANDROID_HOME`을 지정한 상태에서 `.\gradlew.bat assembleDebug testDebugUnitTest --console=plain` 성공
- `npm --prefix admin-web ci` 성공
- `npm --prefix admin-web run build` 성공
- `npm --prefix functions ci` 성공
- `tools/firebase` JavaScript 파일 `node --check` 성공
- `git diff --check -- .github/workflows/android-preflight.yml tools/firebase/run-local-preflight.js docs/reports/ci-path-aware-preflight-2026-06-23.md` 성공

## 남은 범위

- PR 반영 후 실제 GitHub Actions에서 workflow 자체 변경 PR의 전체 preflight가 통과하는지 확인한다.
- Dependabot PR은 의존성 버전 변경이므로 preflight 통과 후에도 별도 검토와 사용자 승인 기준으로 병합한다.
