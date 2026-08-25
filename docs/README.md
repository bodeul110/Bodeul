# 문서 홈

기준일: 2026-08-25

이 문서는 현재 저장소 문서의 단일 진입점이다. `docs/` 루트에는 이 색인만 두고 실제 문서는 주제별 하위 디렉터리에 둔다.

## 먼저 확인할 기준

1. [기획·디자인·구현 기준](planning/source-of-truth.md)
2. [현재 구현 상태](status/implementation-status.md)
3. [Notion 제품 기준 정합성](planning/notion-product-alignment.md)
4. [Figma 현행 화면 지도](design/figma-current-screen-map.md)
5. [목표 인프라 구조](architecture/target-infrastructure.md)

자료가 충돌하면 단일 순서로 판단하지 않는다. 제품 범위는 Notion, 화면 위계는 Figma, 현재 구현은 코드와 검증 기록, 기술 계약은 `architecture/`와 migration을 따른다. 자세한 규칙은 첫 번째 문서에 있다.

## 작업별 진입점

| 작업 | 먼저 볼 문서 |
| --- | --- |
| 기능 추가·범위 판단 | [MVP 범위](planning/mvp-scope.md), [Notion 제품 기준 정합성](planning/notion-product-alignment.md) |
| Android 화면 수정 | [Figma 현행 화면 지도](design/figma-current-screen-map.md), [화면 개편 목표](planning/screen-restructure-target.md) |
| API·DB 변경 | [목표 인프라 구조](architecture/target-infrastructure.md), [데이터 및 API 문서](architecture/data-api.md) |
| 인프라·배포 | [인프라 개요](architecture/infrastructure.md), [Production 인프라 기본값](operations/production-infrastructure-defaults.md) |
| Firebase 운영 | [Firebase 설정](operations/firebase/setup.md), [운영 문서](operations/README.md) |
| 보안·권한 | [보안 문서](security/README.md), [데이터 보관 및 파기 정책](operations/data-retention-policy.md) |
| 기획·법률 정책 확인 | [Notion 제품 기준 정합성](planning/notion-product-alignment.md), [2026-08-25 정책·법률 점검](reports/notion-policy-legal-alignment-2026-08-25.md) |
| 검증 결과 확인 | [현재 구현 상태](status/implementation-status.md), [보고서](reports/README.md) |

## 카테고리

| 디렉터리 | 용도 |
| --- | --- |
| [status](status/README.md) | 현재 구현 상태와 변경 이력 |
| [planning](planning/README.md) | 제품 기준, MVP 범위, 화면 구조와 미결 정책 |
| [architecture](architecture/README.md) | 시스템 구조, 인프라, 데이터·API 계약 |
| [operations](operations/README.md) | 협업, 내부 테스트, QA, Firebase와 운영 절차 |
| [security](security/README.md) | 권한, 보안 규칙, 암호화 판단과 토큰 정책 |
| [design](design/README.md) | Figma 현행 화면 지도와 과거 디자인 감사 |
| [features](features/README.md) | 기능별 구현 메모 |
| [reports](reports/README.md) | 날짜별 점검과 실행 결과 |
| [local](local/README.md) | Git에 올리지 않는 과거 원본·로컬 참조 자료 |
| [archive](archive/README.md) | 구버전·보조 문서 |

## 외부 자료 원칙

- Notion은 제품 의도와 미결 정책을 관리한다. 비공개 URL, 페이지 ID, 계정 정보와 개인정보를 공개 저장소에 기록하지 않는다.
- 답변이 작성됐더라도 확정자·확정일이 없거나 다른 정책 문서와 충돌하면 최종 승인으로 보지 않는다.
- Figma `보들 가이드`의 `Page 2(460:2)`는 현재 화면 구조와 시각 위계의 원본이다.
- `docs/local/`의 기능설명서 PDF와 `design_refs/local/`의 export는 과거 또는 임시 스냅샷이다.
- Figma나 Notion의 `구현 완료` 표기는 코드와 검증 기록 없이 현재 구현 근거로 쓰지 않는다.

## 정리 기준

- 새 설계 판단은 주제별 기준 문서에 반영하고 날짜별 실행 결과는 `reports/`에 둔다.
- 과거 판단을 설명하는 문서는 삭제하지 않고 `당시 감사 이력`으로 표시한다.
- 기능·보안·인프라 변경이 들어가면 관련 계약과 `status/implementation-status.md`를 같은 작업에서 갱신한다.
- 링크가 없는 비공개 자료는 제목과 역할만 적고 팀 워크스페이스에서 검색한다.
