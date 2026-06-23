# 문서 정합성 점검 기록 (2026-06-23)

## 구현한 내용

- 루트 README와 `docs/README.md`의 중복 색인 설명을 줄이고, 문서 홈을 단일 진입점으로 명확히 했다.
- `docs/status/implementation-status.md` 상단을 현재 코드 기준 요약으로 갱신했다.
- 안심 채팅 첨부/푸시/읽음, 건강정보 읽기 화면, 카카오 지도 위치 공유, 관리자 앱/웹 분리, Functions 모듈 분리 상태를 최신 기준으로 맞췄다.
- 네이버 로그인은 앱에서 비활성화 상태임을 문서에 반영했다.
- `design_refs/local/README.md`를 최신 피그마 ZIP과 해제본 기준으로 수정했다.
- 손상된 `docs/archive/보들_플랫폼_기능설명서.md`를 제거하고, 최신 기능 기준을 PDF 원본과 기획 문서로 돌렸다.
- `.tmp/`, `tools/firebase/reports/`, 로컬 바이너리/보조 산출물의 문서 판단 기준을 `docs/README.md`에 분리했다.

## 변경된 범위

- 루트 문서: `README.md`
- 문서 홈/상태: `docs/README.md`, `docs/status/README.md`, `docs/status/implementation-status.md`
- 아키텍처: `docs/architecture/overview.md`, `docs/architecture/infrastructure.md`
- 기획: `docs/planning/mvp-scope.md`, `docs/planning/screen-restructure-target.md`, `docs/planning/refactoring-roadmap-2026-06-20.md`
- 운영: `docs/operations/firebase/setup.md`
- 보관/보고서: `docs/archive/README.md`, `docs/reports/README.md`, 이 문서
- 참조/웹: `design_refs/local/README.md`, `admin-web/README.md`

## 남은 범위

- 날짜별 보고서와 `tools/firebase/reports/` 생성 로그는 당시 증적이므로 본문을 고치지 않았다.
- 디자인/보안/운영 상세 문서는 현행 링크와 기준 문서 관계만 확인했고, 정책 자체 변경은 하지 않았다.
- 이후 동작 변경이 생기면 `status/implementation-status.md` 상단 요약과 해당 상세 문서를 함께 갱신한다.

## 검증

- Markdown 링크 존재 검사: `checked=48 broken=0`
- `.\gradlew.bat assembleDebug --console=plain`: 성공
