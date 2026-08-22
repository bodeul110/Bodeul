# 디자인 참조 정리

기준일: 2026-08-22

## 현재 기준

- 디자인 원본은 [Figma 보들 가이드](https://www.figma.com/design/E2EaCod0aNjnI1uGXZQKok/%EB%B3%B4%EB%93%A4-%EA%B0%80%EC%9D%B4%EB%93%9C?node-id=460-2)다.
- file key는 `E2EaCod0aNjnI1uGXZQKok`, 기준 페이지 node는 `460:2`다.
- 현재 파일은 `Page 2` 한 페이지와 최상위 모바일 화면 38개로 구성된다.
- 화면별 node와 prototype 흐름은 [Figma 현행 화면 지도](../docs/design/figma-current-screen-map.md)에 정리한다.
- `design_refs/local/`은 Git에 올리지 않는 일회성 export·비교 캐시로만 쓴다.

## 사용 원칙

- 화면 배치, 정보 위계, CTA와 시각 상태는 Figma를 기준으로 판단한다.
- 제품 범위와 정책은 [기획·디자인·구현 기준](../docs/planning/source-of-truth.md)을 따른다.
- Figma 문구만으로 권한 필수 여부, 인증 제공자, AI·OCR 동작, 결제·환불이나 서버 구조를 확정하지 않는다.
- 구현 또는 리뷰에는 전체 페이지보다 해당 화면의 좁은 node ID를 남긴다.
- 원본 이미지·ZIP·PDF를 저장소에 다시 커밋하지 않는다.

## 로컬 자산 상태

- 과거 `assets/`, `auth/`, `common/`, `manager/`, `overview/` PNG 묶음은 저장소에서 제거됐다.
- 현재 `design_refs/local/`에는 사용 규칙을 설명하는 README만 추적한다.
- 새 export가 필요하면 날짜가 드러나는 로컬 디렉터리에 두고 작업이 끝나면 정리한다.
