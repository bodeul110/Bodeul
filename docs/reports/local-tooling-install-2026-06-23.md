# 로컬 개발 도구 설치 기록

## 구현한 내용

- `git`, `gh`, `python`은 기존 설치를 확인하고 사용자/시스템 `PATH` 기준으로 실행 가능 상태를 검증했다.
- Android SDK `platform-tools` 경로를 사용자 `PATH`에 추가해 `adb`를 바로 실행할 수 있게 했다.
- `winget`으로 `fd`, `firebase-tools`, `7zr`를 설치했다.
- 추가 작업 효율 도구로 `yq`, `delta`, `fzf`를 설치했다.
- 기존 설치된 `jq` 실행 경로를 확인했다.
- 관리자 권한을 요구하는 7-Zip GUI 설치 대신 공식 7-Zip extra 패키지의 standalone `7za.exe`를 사용자 로컬 경로에 배치하고 `7z.exe` 명령으로 실행되게 했다.

## 변경된 범위

- 사용자 `PATH`에 다음 경로를 보강했다.
  - `C:\Users\wlsrj\AppData\Local\Android\Sdk\platform-tools`
  - `C:\Users\wlsrj\AppData\Local\Programs\7-Zip`
  - `C:\Users\wlsrj\AppData\Local\Microsoft\WinGet\Links`
- 사용자 로컬 설치 경로에 `7z.exe`, `7za.exe`를 배치했다.
- 프로젝트 앱 코드와 Gradle 의존성 버전은 변경하지 않았다.

## 검증 결과

- `git version 2.54.0.windows.1`
- `gh version 2.85.0`
- `adb version 37.0.0-14910828`
- `firebase 14.11.0`
- `jq-1.8.1`
- `fd 10.4.2`
- `Python 3.12.10`
- `7-Zip (a) 26.01`
- `yq v4.53.3`
- `delta 0.19.2`
- `fzf 0.73.1`

## 남은 범위

- 현재 실행 중인 일부 터미널이나 IDE 프로세스가 이전 환경 변수를 물고 있으면 새 터미널을 열거나 IDE를 다시 시작해야 할 수 있다.
