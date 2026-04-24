# 협업 규칙

보들 프로젝트는 여러 작업자가 동시에 들어와도 충돌을 줄일 수 있게 아래 규칙을 기본값으로 사용한다.

## 기본 원칙

- 공용 기준 브랜치는 `master`다.
- 기능 작업은 가능하면 `feature/작업이름` 브랜치에서 진행한다.
- 같은 시간대에 같은 영역을 동시에 수정하지 않는다.
- 작업 시작 전 `implementation-status.md` 최신 항목과 현재 원격 변경분을 먼저 확인한다.
- 작업 종료 전에는 자신의 변경 범위와 남은 범위를 문서에 남긴다.

## 작업 시작 전

작업을 시작하기 전에 아래 순서를 지킨다.

1. `git pull --rebase origin master`로 최신 기준선을 받는다.
2. [implementation-status.md](/D:/BoDeul/docs/implementation-status.md)를 열어 가장 마지막 작업 항목과 남은 범위를 확인한다.
3. 자신의 담당 영역을 짧게 공유한다.
4. 같은 파일을 이미 다른 사람이 잡고 있으면 시간대를 조정하거나 범위를 나눈다.

## 충돌 위험이 큰 파일

아래 파일과 경로는 동시에 수정하지 않는 것을 원칙으로 한다.

- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `functions/index.js`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`
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

- [implementation-status.md](/D:/BoDeul/docs/implementation-status.md)는 작업이 끝난 사람이 마지막에만 갱신한다.
- 같은 날 여러 작업이 있으면 최신 항목 아래에 순서대로 새 섹션을 추가한다.
- 작업 중간 메모는 채팅이나 별도 공유 채널에 남기고, `implementation-status.md`에는 완료 기준만 기록한다.
- 구조나 기준이 바뀌면 관련 문서도 함께 맞춘다.
  - [restructure-target-map.md](/D:/BoDeul/docs/restructure-target-map.md)
  - [firebase-setup.md](/D:/BoDeul/docs/firebase-setup.md)
  - [firebase-operations-tools.md](/D:/BoDeul/docs/firebase-operations-tools.md)

## Firebase 작업 규칙

- 기준선 초기화, 샘플 데이터 주입, 백업/복원, CI 시크릿 변경은 동시에 두 사람이 하지 않는다.
- `tools/firebase`와 GitHub Actions 설정은 한 번에 한 명만 수정한다.
- Firestore 개발 데이터 정리는 [firebase-reset-baseline.md](/D:/BoDeul/docs/firebase-reset-baseline.md) 절차를 따른다.

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

## 권장 검증

- Android 코드 변경: `.\gradlew.bat assembleDebug`
- 테스트 변경 또는 저장소/도메인 변경: `.\gradlew.bat testDebugUnitTest`
- Firebase 운영 도구 변경: 관련 `tools/firebase` 스크립트 재실행
- CI/워크플로 변경: GitHub Actions 실행 결과 확인

## 빠른 체크리스트

- 시작 전에 최신 `master`를 받았는가
- 같은 파일을 다른 사람이 수정 중인지 확인했는가
- `implementation-status.md` 최신 항목을 읽었는가
- Firebase 운영 작업 담당이 겹치지 않는가
- 종료 전에 문서와 검증 결과를 남겼는가
