# 2026-06-20 인코딩 정리 후속 메모
## 구현한 내용

- 운영/테스트 기준으로 직접 참조되는 문서 6건의 한글 깨짐 구간을 정상 한국어로 복구했다.
  - `../operations/firebase/setup.md`
  - `../architecture/infrastructure.md`
  - `docs/operations/README.md`
  - `docs/planning/refactoring-roadmap-2026-06-20.md`
  - `../architecture/data-api.md`
  - `../status/implementation-status.md`
- 복구 기준은 현재 앱 동작, 기존 문서 문맥, Git 이력을 함께 확인해 맞췄다.
  - 관리자 문의 화면 구성
  - 카카오 병원/약국 실좌표 검색
  - 안심 채팅 첨부 제한
  - 리팩토링 진행 메모
  - 데이터/API 초안 최근 메모
  - 구현 상태 문서 최근 후속 기록 분리
- `../status/implementation-status.md`는 마지막 저손상 이력(`055c37d`)을 기준으로 복구하고, 손상된 최근 상세 기록은 개별 보고서 문서 참조로 정리했다.

## 변경된 범위

- 문서
  - `../operations/firebase/setup.md`
  - `../architecture/infrastructure.md`
  - `docs/operations/README.md`
  - `docs/planning/refactoring-roadmap-2026-06-20.md`
  - `../architecture/data-api.md`
  - `../status/implementation-status.md`
  - `encoding-cleanup-followup-2026-06-20.md`

## 검증 결과

- 문서 인코딩 확인 기준으로 아래 파일은 UTF-8로 정상 해석되고 `U+FFFD` 치환 문자가 없다.
  - `../operations/firebase/setup.md`
  - `../architecture/infrastructure.md`
  - `docs/operations/README.md`
  - `docs/planning/refactoring-roadmap-2026-06-20.md`
  - `../architecture/data-api.md`
  - `../status/implementation-status.md`
- `git diff --check`를 통과했다.
- 이번 배치는 문서 수정만 포함하므로 `assembleDebug`는 다시 실행하지 않았다.

## 남은 범위

- 이번에 추적한 인코딩 후보 문서는 모두 정리했다.
- 앞으로 새로 깨진 문서가 생기면 `../status/implementation-status.md`에는 요약만 남기고, 상세 배치 기록은 성격별 보고서 문서에 분리하는 기준을 유지한다.
- 앱 코드와 운영 도구 스크립트는 이번 배치에서 수정하지 않았다.
