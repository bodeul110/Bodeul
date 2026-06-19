# 디자인 참조 정리

기준일: 2026-06-19

## 현재 기준 자산

- `local/bodeul_original_resolution_screens.zip`
  - 최신 원본 화면 묶음
- `local/bodeul_split_screens/`
  - 최신 원본을 화면 단위로 분할한 세트
  - `previews/contact_sheet.png`, `index.csv` 포함

## 사용 원칙

- 최신 디자인 기준은 `local/bodeul_original_resolution_screens.zip`과 `local/bodeul_split_screens/`이다.
- 기능 우선순위와 제품 요구사항은 `docs/local/보들_플랫폼_기능설명서.pdf`와 `docs/restructure-target-map.md`를 먼저 따른다.
- 디자인 자산은 확정 명세가 아니라 UI polish와 위계 판단 기준으로만 사용한다.

## 보조 자산

- `auth/`
  - 로그인, 역할 선택, 스플래시 보조 참조
- `common/`
  - 공통 화면 보조 참조
- `manager/`
  - 매니저 흐름 보조 참조
- `overview/`
  - 예전 전체 보드 이미지
- `assets/`
  - 개별 추출 자산

## 저장소에서 제외한 자산

- `local/automated_income_tool_report.md`
  - 프로젝트 기준 문서가 아님
- `보들 가이드.zip`
  - 초기에 사용한 구형 피그마 산출물
  - 현재 저장소 기준 최신 디자인 자산이 아니므로 Git 추적 대상에서 제거했다.
  - 당시 검토 메모는 `docs/archive/design-reference-review-2026-05-05.md`에 보관한다.
