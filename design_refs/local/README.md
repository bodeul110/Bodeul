# 로컬 디자인 캐시

이 디렉터리는 Git에 올리지 않는 로컬 전용 디자인 export/cache를 둔다.

## 현재 기준

- 디자인 원본 기준은 Figma 파일이다.
- 이 디렉터리의 ZIP, PDF, PNG, 분할 화면은 기준 원본이 아니라 임시 확인용 산출물이다.
- Figma 연결이 가능하면 이 디렉터리 파일보다 Figma 원본을 먼저 확인한다.

## 보관 가능 항목

- 멘토 회의나 오프라인 확인용 일회성 Figma export
- 화면 비교를 위한 임시 PNG/PDF
- 자동 추출 스크립트가 만든 contact sheet, index, preview 파일

## 정리 원칙

- 새 export는 `figma-export-YYYY-MM-DD/`처럼 날짜가 드러나는 디렉터리에 둔다.
- 오래된 ZIP, PDF, PNG 묶음은 Figma 원본으로 다시 확인할 수 있으면 삭제한다.
- 프로젝트 기준 문서에는 이 경로를 원본으로 쓰지 않는다.
- UI polish 기준으로만 사용하고, 기능 우선순위 판단은 기능설명서를 먼저 따른다.
