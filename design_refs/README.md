# 디자인 참조 정리

기준일: 2026-07-02

## 현재 기준

- 디자인 기준 원본은 Figma 파일이다.
- Figma 원본은 [보들 가이드](https://www.figma.com/design/E2EaCod0aNjnI1uGXZQKok/%EB%B3%B4%EB%93%A4-%EA%B0%80%EC%9D%B4%EB%93%9C?node-id=0-1&t=1bVoxluZrRsPeS15-1) 파일이다.
- 기준 file key는 `E2EaCod0aNjnI1uGXZQKok`, 기준 URL node는 `0:1`이다.
- `design_refs/local/`은 Git에 올리지 않는 임시 export/cache 위치로만 쓴다.
- 이전에 다운로드해서 커밋했던 PNG 묶음은 기준 원본이 아니므로 저장소 추적 대상에서 제거했다.

## Figma 원본 등록

| 이름 | URL 또는 file key | 용도 | 상태 |
| --- | --- | --- | --- |
| 보들 가이드 | [Figma URL](https://www.figma.com/design/E2EaCod0aNjnI1uGXZQKok/%EB%B3%B4%EB%93%A4-%EA%B0%80%EC%9D%B4%EB%93%9C?node-id=0-1&t=1bVoxluZrRsPeS15-1), `E2EaCod0aNjnI1uGXZQKok` | Android 앱 화면 위계와 UI polish 기준 | 등록 완료 |

새 Figma 파일이나 별도 핵심 노드가 생기면 이 표에 먼저 등록한다. 이후 화면 작업에서는 다운로드 ZIP, PDF, PNG보다 Figma 원본을 우선한다.

`0:1`은 파일 전체에 가까운 넓은 기준 노드다. 실제 구현이나 화면별 비교를 할 때는 해당 화면의 더 좁은 node URL을 추가로 확인한다.

## 사용 원칙

- 기능 우선순위와 제품 요구사항은 `docs/local/보들_플랫폼_기능설명서.pdf`와 `../docs/planning/screen-restructure-target.md`를 먼저 따른다.
- 카드 위계, 간격, 강조 순서, CTA 배치처럼 시각 판단이 필요한 부분만 Figma 디자인을 기준으로 삼는다.
- Figma에서 확인한 화면 이름은 문서에 남기되, 원본 이미지 파일을 저장소에 커밋하지 않는다.
- 화면 검토나 멘토 회의 때문에 export가 필요하면 `design_refs/local/` 아래에 임시로 두고, 기준 문서에는 Figma 원본 기준임을 유지한다.

## 저장소 구조

- `README.md`
  - 디자인 참조 원칙과 Figma 원본 등록 위치
- `local/README.md`
  - 로컬 임시 export/cache 사용 규칙

## 정리한 이전 자산

- `assets/`
- `auth/`
- `common/`
- `manager/`
- `overview/`

위 디렉터리들은 2026-04 다운로드 PNG 참조 묶음이었다. 코드에서 직접 사용하지 않고 Figma 연결로 대체 가능한 보조 자료라 저장소에서 제거했다.
