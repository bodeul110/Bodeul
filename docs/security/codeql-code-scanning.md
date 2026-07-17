# CodeQL/code scanning 운영 기준

기준일: 2026-07-17
관련 이슈: #49

## 구현한 내용

- `.github/workflows/codeql.yml`을 추가해 CodeQL code scanning을 별도 GitHub Actions workflow로 구성했다.
- Android 앱은 `java-kotlin` 분석 대상으로 두고, `assembleDebug` 수동 빌드 후 분석한다.
- Firebase Functions와 Firebase 운영 도구는 `javascript-typescript` 분석 대상으로 둔다. 관리자 웹은 별도 저장소의 CodeQL이 담당한다.
- `.github/codeql/codeql-config.yml`에 생성물과 로컬 운영 산출물 제외 경로를 명시했다.

## 변경된 범위

- `.github/workflows/codeql.yml`
- `.github/codeql/codeql-config.yml`
- `docs/security/codeql-code-scanning.md`
- `docs/security/README.md`

## 실행 조건

| 이벤트 | 실행 기준 | 목적 |
| --- | --- | --- |
| `pull_request` | `master` 대상 PR | 보안 취약점이 기본 브랜치에 들어가기 전에 확인 |
| `push` | `master` push | 기본 브랜치의 code scanning 결과를 Security 탭에 반영 |
| `schedule` | 매주 화요일 03:23 KST | 코드 변경이 없어도 최신 CodeQL 쿼리 기준으로 재분석 |
| `workflow_dispatch` | 수동 실행 | 설정 변경 또는 장애 확인 시 재실행 |

`pull_request`와 `push`에서는 변경 파일을 분류해 필요한 언어 분석만 실행한다. `schedule`과 `workflow_dispatch`는 전체 분석을 수행한다.

## 분석 대상

| 구분 | CodeQL 언어 | 대상 | 빌드 방식 |
| --- | --- | --- | --- |
| Android 앱 | `java-kotlin` | `app/`, Gradle 설정 | `./gradlew assembleDebug --console=plain` 수동 빌드 |
| Firebase Functions | `javascript-typescript` | `functions/` | CodeQL 정적 분석. 배포 검증은 별도 운영 절차가 담당 |
| Firebase 운영 도구 | `javascript-typescript` | `tools/firebase/` | CodeQL 정적 분석. 스크립트 문법/프리플라이트는 기존 workflow가 담당 |

## 제외 대상

| 경로 | 제외 이유 |
| --- | --- |
| `app/build/**`, `build/**` | Gradle 빌드 산출물 |
| `functions/node_modules/**` | 설치 의존성 |
| `tools/firebase/backups/**` | 운영 백업 파일 |
| `tools/firebase/reports/**` | 로컬/CI 운영 리포트 산출물 |

## preflight와의 관계

- `android-preflight.yml`은 빌드, 테스트, Firebase 운영 도구 검증을 담당한다.
- 관리자 웹 lint/build와 CodeQL은 `bodeul-admin-web` 저장소가 담당한다.
- `codeql.yml`은 보안 정적 분석과 GitHub Security 탭 업로드를 담당한다.
- CodeQL workflow는 기존 preflight를 대체하지 않는다. PR에서는 preflight와 CodeQL이 모두 통과해야 병합 근거가 된다.

## 운영 기준

- CodeQL action은 현재 GitHub 공식 `github/codeql-action@v4`를 사용한다.
- Android 분석은 `google-services.json` secret이 있으면 복원하고, 없으면 현재 Gradle fallback 구조로 빌드한다.
- CodeQL alert가 실제 취약점이면 일반 Issue/PR 댓글에 민감한 세부값을 남기지 않고 보안 문서 또는 private vulnerability reporting 경로를 사용한다.
- 의존성 취약점은 Dependabot, 코드 패턴 취약점은 CodeQL 기준으로 나누어 본다.

## 검증

- GitHub Actions YAML 파싱 확인: `yq e '.' .github/workflows/codeql.yml`
- CodeQL 설정 YAML 파싱 확인: `yq e '.' .github/codeql/codeql-config.yml`
- Android 수동 분석 빌드 경로 확인: `.\gradlew.bat assembleDebug --console=plain`
- 관리자 웹 검증은 별도 저장소의 test/lint/Next.js build/Vite build workflow에서 수행한다.
- PR과 `master` push에서 `CodeQL` workflow가 반복해서 통과했다.
- 2026-07-16 기준 최신 `master` 실행([run 29488654755](https://github.com/bodeul110/Bodeul/actions/runs/29488654755))이 성공했다.
- 같은 시점의 GitHub Security code scanning 열린 alert는 0건이다.

## 운영 후속

- PR, `master` push, 주간 schedule 결과를 계속 확인한다.
- 새 alert가 생기면 실제 취약점과 false positive를 구분하고, 민감한 세부정보는 private vulnerability reporting 경로에서 다룬다.
