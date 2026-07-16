# GitHub self-hosted runner 운영 기준

## 목적

Android 프리플라이트를 수동으로 오래 돌리거나 반복 확인할 때 GitHub-hosted runner 대기 시간과 사용 시간을 줄인다. PR 자동 검증은 안정성을 위해 계속 GitHub-hosted runner에서 실행하고, self-hosted runner는 `workflow_dispatch` 수동 실행에서만 선택한다.

## 적용 방식

- workflow: `.github/workflows/android-preflight.yml`
- 기본 실행기: `ubuntu-latest`
- 수동 실행 선택지:
  - `github-hosted`: 기본값
  - `self-hosted-bodeul`: `self-hosted`, `bodeul`, `preflight` 라벨을 모두 가진 runner에서 실행

## runner 준비 조건

- GitHub runner 라벨
  - 기본 라벨: `self-hosted`
  - 추가 라벨: `bodeul`, `preflight`
- 권장 설치 경로
  - `D:\actions-runner\bodeul-preflight`
  - Windows 서비스 전환 시 `D:\actions-runner\bodeul-preflight-service`
- 필수 도구
  - Git
  - Node.js 22 또는 `actions/setup-node@v6`가 설치할 수 있는 환경
  - JDK 17 또는 `actions/setup-java@v5`가 설치할 수 있는 환경
  - Android SDK와 Gradle 빌드에 필요한 라이선스 승인
  - Bash, `mktemp`, `sed`, `find`, `xargs`
- Firebase 운영 점검까지 실행하려면 저장소 secrets와 variables가 준비되어 있어야 한다.
- Windows 서비스 runner는 서비스 프로세스가 사용자 환경변수를 그대로 상속하지 않을 수 있으므로 저장소 변수 `SELF_HOSTED_ANDROID_HOME`에 Android SDK 경로를 둔다.

Windows runner를 사용할 경우 Git for Windows의 Bash 도구가 PATH에 잡혀 있어야 한다. 안정성 기준으로는 Linux runner 또는 WSL 기반 runner를 우선 권장한다.

Windows self-hosted runner는 로컬 Gradle 캐시가 runner 사용자 디렉터리에 유지된다. 따라서 수동 실행에서 `self-hosted-bodeul`을 선택한 경우 `actions/setup-java`의 Gradle cache 입력을 끄고, GitHub-hosted runner에서만 Actions cache를 사용한다.

## 등록 절차

1. GitHub 저장소의 `Settings > Actions > Runners`에서 새 self-hosted runner를 만든다.
2. runner 패키지를 작업용 디렉터리에 내려받고 GitHub 안내 명령으로 등록한다.
3. 등록 시 라벨에 `bodeul`, `preflight`를 추가한다.
4. runner를 서비스 또는 장기 실행 프로세스로 시작한다.
5. GitHub Actions 화면에서 `Android Preflight`를 수동 실행하고 `runner_profile`을 `self-hosted-bodeul`로 선택한다.

등록 토큰은 짧게 만료되는 민감 값이다. 문서, 커밋, 로그에 남기지 않는다.

Windows 서비스 등록은 [install-bodeul-runner-service.ps1](../../tools/github/install-bodeul-runner-service.ps1)를 관리자 권한 PowerShell에서 실행한다. 이 스크립트는 기존 사용자 프로세스 runner를 직접 재설정하지 않고 별도 서비스용 runner 디렉터리를 구성한 뒤, 서비스가 실행 상태가 되면 Startup fallback을 제거한다.

## 보안 기준

- public fork PR이나 신뢰하지 않는 브랜치에는 self-hosted runner를 연결하지 않는다.
- runner는 전용 OS 계정으로 실행하고 개인 계정의 일반 작업 환경과 분리한다.
- runner 작업 디렉터리는 주기적으로 정리한다.
- 장기 보관 secrets는 runner 디스크에 두지 않고 GitHub Secrets를 사용한다.
- runner가 오프라인일 때는 수동 실행에서 `github-hosted`를 선택해 우회한다.
- Windows 서비스 runner는 `workflow_dispatch` 수동 실행에서만 선택하고, PR 자동 검증에는 연결하지 않는다.

## 검증 방법

- GitHub `Settings > Actions > Runners`에서 runner가 `Idle` 상태인지 확인한다.
- `Android Preflight` 수동 실행에서 `self-hosted-bodeul`을 선택한다.
- 실행 로그의 `CI 범위 요약 작성` 단계에서 `실행기: self-hosted-bodeul`이 기록되는지 확인한다.

## 2026-06-23 검증 기록

- runner 이름: `DESKTOP-1B62HQD-bodeul-preflight`
- runner 버전: `2.335.1`
- runner OS/라벨: `Windows`, `X64`, `self-hosted`, `bodeul`, `preflight`
- 설치 경로: `D:\actions-runner\bodeul-preflight`
- 실행 방식: 사용자 프로세스. Windows 서비스로는 등록하지 않았다.
- 자동 시작:
  - 현재 Windows runner 패키지에는 `svc.cmd`가 포함되어 있지 않다.
  - 작업 스케줄러 등록은 현재 사용자 권한에서 `Access is denied`로 실패했다.
  - 현재 적용 방식은 사용자 Startup 폴더의 `BoDeul GitHub Actions Runner.vbs`가 `D:\actions-runner\bodeul-preflight\start-bodeul-runner.ps1`을 숨김 실행하는 방식이다.
- 검증 workflow: `Android Preflight`
- 검증 run:
  - `28031156324`: 최초 self-hosted 프리플라이트 통과
  - `28032579258`: self-hosted 전용 Gradle Actions cache 비활성화 후 프리플라이트 통과
- 검증 결과: `workflow_dispatch`에서 `runner_profile=self-hosted-bodeul`로 실행한 프리플라이트가 통과했다.
- 당시 `actions/checkout@v4`에서 발생한 Node.js 20 deprecation annotation은 2026-07-16의 Node 24 기반 Action 전환으로 해소됐다. 현재 workflow는 `actions/checkout@v7`을 사용한다.

## Windows 서비스 전환 기록

- runner 이름: `DESKTOP-1B62HQD-bodeul-preflight-service`
- runner 버전: `2.335.1`
- runner OS/라벨: `Windows`, `X64`, `self-hosted`, `bodeul`, `preflight`
- 설치 경로: `D:\actions-runner\bodeul-preflight-service`
- 서비스 이름: `actions.runner.bodeul110-Bodeul.DESKTOP-1B62HQD-bodeul-preflight-service`
- 서비스 계정: `NT AUTHORITY\NETWORK SERVICE`
- 서비스 시작 유형: `Automatic`
- 기존 사용자 프로세스 runner:
  - `DESKTOP-1B62HQD-bodeul-preflight`는 GitHub에서 `offline` 상태로 남겨 fallback 재구성 여지를 둔다.
  - Startup 폴더의 `BoDeul GitHub Actions Runner.vbs`는 서비스 등록 후 제거했다.
- 환경 보정:
  - `SELF_HOSTED_ANDROID_HOME`: `C:\Users\wlsrj\AppData\Local\Android\Sdk`
  - `NT AUTHORITY\NETWORK SERVICE`가 `AppData\Local\Android\Sdk` 경로를 읽을 수 있도록 상위 경로 통과 권한과 SDK 읽기 권한을 부여했다.
  - Windows PowerShell `LocalMachine` 실행 정책: `RemoteSigned`
- 검증 workflow: `Android Preflight`
  - `28036304130`: 서비스 runner 최초 실행. PowerShell 실행 정책 때문에 실패했다.
  - `28036518189`: 실행 정책 보정 후 서비스 runner가 작업을 처리했으나, 서비스 프로세스 환경에 Android SDK 경로가 없어 실패했다.
  - `28037516201`: 저장소 변수 `SELF_HOSTED_ANDROID_HOME` 주입 후에도 SDK ACL이 부족해 실패했다.
  - `28038588751`: SDK ACL 보정 후 `runner_profile=self-hosted-bodeul` 프리플라이트가 통과했다.
