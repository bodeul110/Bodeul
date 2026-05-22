# 디자인 참조 정리

기준일: 2026-05-22

## 현재 기준 파일

- `bodeul_original_resolution_screens.zip`
  - 최신 원본 화면 묶음
  - `common/`, `manager/`, `patient/` 그룹으로 정리된 PNG 51장 포함
- `bodeul_split_screens/`
  - 최신 원본을 화면 단위로 분할한 작업 폴더
  - `original/`: 원본 크기 분할 화면
  - `upscaled_4x/`: 확대본
  - `previews/contact_sheet.png`: 전체 화면 빠른 검토용 시트
  - `index.csv`: 그룹, 순서, 잘라낸 좌표 메타데이터

## 보조 참조

- `auth/`
  - 로그인, 역할 선택, 스플래시 빠른 참조용
- `common/`
  - 권한 안내 화면 빠른 참조용
- `manager/`
  - 매니저 홈, 가이드 흐름 빠른 참조용
- `overview/all-boards-20260422.png`
  - 이전 정리본 전체 보드

## 사용 원칙

- 최신 디자인 기준은 `bodeul_original_resolution_screens.zip`과 `bodeul_split_screens/`이다.
- `auth/`, `common/`, `manager/`, `overview/`는 빠른 검토용 보조 자료로만 쓴다.
- 기능 우선순위와 제품 요구사항은 `docs/보들_플랫폼_기능설명서.pdf`와 `docs/restructure-target-map.md`를 먼저 따른다.
- 디자인 자료는 확정 명세가 아니라 화면 위계와 UI polish 참고본으로 쓴다.

## 참고하지 않는 파일

- `automated_income_tool_report.md`
  - 보들 프로젝트 디자인 기준 문서가 아니다.
  - 현재 UI/기획 판단 기준으로 사용하지 않는다.
