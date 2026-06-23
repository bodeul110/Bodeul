# 협업 규칙

보들 프로젝트는 여러 작업자가 동시에 들어와도 충돌을 줄일 수 있게 아래 규칙을 기본값으로 사용한다.

## 기본 원칙

- 공용 기준 브랜치는 `master`다.
- 기능 작업은 가능하면 `feature/작업이름` 브랜치에서 진행한다.
- 같은 시간대에 같은 영역을 동시에 수정하지 않는다.
- 작업 시작 전 `../status/implementation-status.md` 최신 항목과 현재 원격 변경분을 먼저 확인한다.
- 작업 종료 전에는 자신의 변경 범위와 남은 범위를 문서에 남긴다.

## 작업 시작 전

작업을 시작하기 전에 아래 순서를 지킨다.

1. 현재 로컬 상태를 확인한다.
2. 최근 누가 무엇을 작업했는지 확인한다.
3. 로컬 브랜치와 원격 `master` 중 어느 쪽이 최신인지 확인한다.
4. [../status/implementation-status.md](../status/implementation-status.md)를 열어 가장 마지막 작업 항목과 남은 범위를 확인한다.
5. 자신의 담당 영역을 짧게 공유한다.
6. 같은 파일을 이미 다른 사람이 잡고 있으면 시간대를 조정하거나 범위를 나눈다.

### 작업 시작 전 상세 확인 명령

아래 명령을 순서대로 확인하면 된다.

```powershell
git status --short
git branch --show-current
git fetch origin
git log --format="%h %an %ad %s" --date=short -10
git log origin/master --format="%h %an %ad %s" --date=short -10
git rev-list --left-right --count HEAD...origin/master
git diff --stat HEAD..origin/master
```

확인 기준은 아래처럼 잡는다.

- `git status --short`
  - 출력이 없으면 워크트리가 깨끗한 상태다.
  - 출력이 있으면 먼저 내 로컬 변경인지 확인한다.
- `git log --format="%h %an %ad %s" --date=short -10`
  - 최근 누가 어떤 커밋을 넣었는지 본다.
  - 작업자 이름과 커밋 메시지로 최근 담당 범위를 추정한다.
- `git log origin/master --format="%h %an %ad %s" --date=short -10`
  - 원격 기준 최신 작업자를 본다.
- `git rev-list --left-right --count HEAD...origin/master`
  - 왼쪽 숫자: 내 로컬에만 있는 커밋 수
  - 오른쪽 숫자: 원격에만 있는 커밋 수
  - 예시
    - `0 0`: 로컬과 원격이 같다.
    - `0 3`: 원격이 3커밋 앞서 있다. 먼저 당겨와야 한다.
    - `2 0`: 내 로컬만 2커밋 앞서 있다. 푸시 전 상태일 수 있다.
    - `2 3`: 서로 갈라졌다. 바로 작업하지 말고 먼저 정리한다.
- `git diff --stat HEAD..origin/master`
  - 원격에서 어떤 파일이 바뀌었는지 빠르게 본다.
  - 내가 건드리려는 파일이 여기에 있으면 먼저 담당자와 겹치는지 확인한다.

### 누가 작업했는지 확인하는 기준

작업 전에는 아래를 같이 본다.

1. 최근 커밋 작성자
2. 최근 `../status/implementation-status.md` 작성 항목
3. 팀 채널이나 메신저에 남은 현재 작업 선언

가능하면 아래 정보를 짧게 공유한다.

- 지금 누가 작업 중인지
- 어떤 파일이나 화면을 잡고 있는지
- 언제까지 잡을 예정인지

## 로컬과 원격 중 어느 쪽이 최신인지 확인하는 방법

기본 판단은 아래 순서로 한다.

```powershell
git fetch origin
git rev-list --left-right --count HEAD...origin/master
git diff --stat HEAD..origin/master
git diff --stat origin/master..HEAD
```

- 원격이 앞서 있으면 `HEAD..origin/master` diff를 본다.
- 내가 앞서 있으면 `origin/master..HEAD` diff를 본다.
- 양쪽 다 커밋이 있으면 서로 다른 변경이 섞여 있으니 바로 수정하지 말고 먼저 정리한다.

## 최신 기준선 당겨오는 방법

워크트리가 깨끗하면 아래 순서로 받는다.

```powershell
git fetch origin
git pull --rebase origin master
```

워크트리가 깨끗하지 않으면 바로 `pull --rebase` 하지 않는다.

먼저 아래 셋 중 하나를 고른다.

1. 아직 커밋할 만한 작업이면 먼저 커밋
2. 잠깐 치워둘 작업이면 `git stash push -u`
3. 내 변경이 아니거나 불필요한 임시 파일이면 정리 후 진행

예시:

```powershell
git status --short
git stash push -u -m "작업 전 기준선 동기화"
git pull --rebase origin master
git stash pop
```

주의:

- `git pull --rebase origin master` 전에 내 변경이 무엇인지 모르는 상태로 stash하지 않는다.
- 공용 파일 충돌이 예상되면 stash/pop보다 담당자 확인이 먼저다.
- 같은 파일에서 충돌이 나면 억지로 넘기지 말고 누가 최신 의도인지 먼저 확인한다.

## 충돌 위험이 큰 파일

아래 파일과 경로는 동시에 수정하지 않는 것을 원칙으로 한다.

- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `functions/index.js`
- `app/src/main/res/values/strings.xml`
- `../status/implementation-status.md`
- `tools/firebase/**`

이 구간을 수정할 때는 작업 전에 `누가 언제까지 잡는지`를 먼저 정한다.

## 담당 범위 권장안

가능하면 아래처럼 기능 축으로 나눠서 작업한다.

- 환자/보호자/예약
- 매니저 화면
- 관리자 화면
- Firebase 운영 도구/CI/문서

한 사람이 두 영역 이상을 동시에 잡아도 되지만, 겹치는 파일이 생기면 우선 담당자를 하나로 정한다.

## 문서 갱신 규칙

- [../status/implementation-status.md](../status/implementation-status.md)는 작업이 끝난 사람이 마지막에만 갱신한다.
- 같은 날 여러 작업이 있으면 최신 항목 아래에 순서대로 새 섹션을 추가한다.
- 작업 중간 메모는 채팅이나 별도 공유 채널에 남기고, `../status/implementation-status.md`에는 완료 기준만 기록한다.
- 구조나 기준이 바뀌면 관련 문서도 함께 맞춘다.
  - [../planning/screen-restructure-target.md](../planning/screen-restructure-target.md)
  - [firebase/setup.md](firebase/setup.md)
  - [firebase/tools.md](firebase/tools.md)

## Firebase 작업 규칙

- 기준선 초기화, 샘플 데이터 주입, 백업/복원, CI 시크릿 변경은 동시에 두 사람이 하지 않는다.
- `tools/firebase`와 GitHub Actions 설정은 한 번에 한 명만 수정한다.
- Firestore 개발 데이터 정리는 [firebase/reset-baseline.md](firebase/reset-baseline.md) 절차를 따른다.

## 작업 중 규칙

- 큰 공용 파일을 수정 중이면 바로 공유한다.
- 오래 작업할수록 중간에 한 번씩 `git fetch` 또는 `git pull --rebase`로 기준선을 다시 확인한다.
- 공용 문자열, 공용 레이아웃, 운영 스크립트는 작은 변경이라도 겹치기 쉬우므로 먼저 알린다.

## 작업 종료 전

작업을 마치기 전에 아래를 확인한다.

1. 필요한 검증을 수행한다.
2. 변경 범위에 맞게 문서를 갱신한다.
3. `git status`로 불필요한 임시 파일이 없는지 확인한다.
4. 커밋 메시지는 작업 범위를 바로 알 수 있게 적는다.
5. push 전 `git fetch origin`으로 원격 기준이 다시 바뀌지 않았는지 확인한다.

### push 전 최소 확인 명령

```powershell
git status --short
git fetch origin
git rev-list --left-right --count HEAD...origin/master
git log --oneline --decorate -5
```

- `git status --short`가 비어 있어야 한다.
- `git rev-list --left-right --count HEAD...origin/master` 결과가 `내 로컬만 앞선 상태`인지 확인한다.
- 원격이 앞서 있으면 먼저 `git pull --rebase origin master`로 다시 맞춘다.

## 권장 검증

- Android 코드 변경: `.\gradlew.bat assembleDebug`
- 테스트 변경 또는 저장소/도메인 변경: `.\gradlew.bat testDebugUnitTest`
- Firebase 운영 도구 변경: 관련 `tools/firebase` 스크립트 재실행
- CI/워크플로 변경: GitHub Actions 실행 결과 확인

## 빠른 체크리스트

- 시작 전에 최근 작업자와 최신 `master` 상태를 확인했는가
- 로컬과 원격 중 어느 쪽이 최신인지 확인했는가
- 같은 파일을 다른 사람이 수정 중인지 확인했는가
- `../status/implementation-status.md` 최신 항목을 읽었는가
- Firebase 운영 작업 담당이 겹치지 않는가
- pull 전 내 로컬 변경을 안전하게 정리했는가
- 종료 전에 문서와 검증 결과를 남겼는가
