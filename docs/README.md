# 문서 홈

기준일: 2026-07-19

이 문서는 현재 저장소 문서의 단일 진입점이다. `docs/` 루트에는 이 색인만 두고, 실제 문서는 주제별 하위 디렉터리에 둔다.

## 최상위 기준

1. `local/보들_플랫폼_기능설명서.pdf`
2. [화면 개편 목표 정리](planning/screen-restructure-target.md)
3. [현재 구현 상태](status/implementation-status.md)
4. [데이터 및 API 문서](architecture/data-api.md)

기능 방향이 충돌하면 위 순서대로 판단한다.

## 먼저 볼 문서

새 작업을 시작할 때는 아래 순서를 기본으로 쓴다.

1. [현재 구현 상태](status/implementation-status.md)
2. [기획 정책 및 공용 계정 준비 기준](planning/product-policy-and-shared-account-readiness.md)
3. [협업 규칙](operations/collaboration-rules.md)
4. [내부 테스트 가이드](operations/internal-test-guide.md)
5. [화면 개편 목표 정리](planning/screen-restructure-target.md)
6. [Firebase 설정](operations/firebase/setup.md)
7. [인프라 개요](architecture/infrastructure.md)
8. [Production 인프라 기본값](operations/production-infrastructure-defaults.md)
9. [2026년 Production 운영 전환 계획](operations/production-transition-plan-2026.md)
10. [데이터 보관 및 파기 정책](operations/data-retention-policy.md)
11. [Production 인프라 구축 기록](reports/production-infrastructure-bootstrap-2026-07-17.md)

## 카테고리

| 디렉터리 | 용도 |
| --- | --- |
| [status](status/README.md) | 현재 구현 상태와 변경 이력 |
| [planning](planning/README.md) | 기능 기준, MVP 범위, 화면 구조, 리팩토링 계획 |
| [architecture](architecture/README.md) | 시스템 구조, 인프라, 데이터/API 계약 |
| [operations](operations/README.md) | 협업, 내부 테스트, QA, Firebase 운영 |
| [security](security/README.md) | 권한, 보안 규칙, 암호화 판단, FCM 토큰 정책 |
| [design](design/README.md) | 디자인 레퍼런스, 기능설명서/피그마 gap |
| [features](features/README.md) | 기능별 구현 메모 |
| [reports](reports/README.md) | 날짜별 점검, 테스트, 정리 보고서 |
| [local](local/README.md) | Git에 올리지 않거나 원본성 강한 로컬 참조 자료 |
| [archive](archive/README.md) | 구버전/보조 문서 |

## 정리 기준

- 루트 `docs/`에는 색인만 둔다.
- 중복되는 문서 안내는 이 문서로 병합했다.
- 작업 기록성 문서는 `reports/`로 분리한다.
- 기능별 일회성 메모는 `features/`로 분리한다.
- Firebase 운영 세부 문서는 `operations/firebase/` 아래에 모은다.
- 구버전이지만 참고 가치가 있는 문서는 `archive/`에 둔다.

## 제외 기준

- `.tmp/` 아래 파일은 임시 산출물이므로 현재 기준 문서로 보지 않는다.
- `tools/firebase/reports/` 아래 프리플라이트 결과는 생성 시점의 증적이며, 최신 판단은 `reports/`와 `status/`를 우선한다.
- `docs/local/`의 원본성 강한 자료는 로컬 참조로만 보고, 정리 대상 문서 본문은 `docs/local/README.md`다.
- `design_refs/local/` 아래 파일은 Figma 원본을 확인하기 어려울 때 쓰는 임시 export/cache이며, 현재 디자인 기준 원본은 Figma 파일이다.
- `design_refs/local/automated_income_tool_report.md`처럼 프로젝트와 무관한 자동 산출물은 기준 문서로 보지 않는다.

## 운영 원칙

- 기능 방향은 기능설명서와 `planning/screen-restructure-target.md`를 먼저 본다.
- 실제 구현 상태는 `status/implementation-status.md`를 기준으로 본다.
- 새 기능이나 보안 변경이 들어가면 같은 턴에 `status/implementation-status.md`와 관련 상세 문서를 함께 갱신한다.
- 날짜별 점검 결과는 `reports/`에 추가하고, 이 색인이나 해당 카테고리 README에서 연결한다.
