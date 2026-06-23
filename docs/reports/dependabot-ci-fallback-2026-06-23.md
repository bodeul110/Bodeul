# Dependabot CI fallback 적용 보고서

## 구현한 내용

- `google-services.json`이 없는 CI 환경에서도 Android 앱이 컴파일되도록 Google 로그인 서버 클라이언트 ID 조회를 런타임 리소스 조회 방식으로 변경했다.
- `default_web_client_id` 생성 리소스가 없으면 Google 로그인 옵션에 서버 클라이언트 ID를 넣지 않고 진행하도록 처리했다.
- 이 변경은 Dependabot PR의 `pull_request` 워크플로에서 비밀값을 사용할 수 없어 `google-services.json`을 복원하지 못하는 상황을 대상으로 한다.

## 변경된 범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `docs/reports/dependabot-ci-fallback-2026-06-23.md`

## 검증

- `app/google-services.json`을 임시로 제외한 상태에서 `.\gradlew.bat assembleDebug --console=plain` 성공
- `app/google-services.json`을 복구한 일반 상태에서 `.\gradlew.bat assembleDebug --console=plain` 성공
- `git diff --check -- AGENTS.md app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java` 성공

## 남은 범위

- 열린 Dependabot PR은 의존성 버전 변경이므로 별도 검토와 사용자 승인 후 병합한다.
- 이번 변경 반영 후 각 Dependabot PR의 `preflight` 재실행 결과를 확인한다.
