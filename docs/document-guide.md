# 문서 안내

기준일: 2026-06-19

이 문서는 현재 저장소에서 어떤 문서를 먼저 봐야 하는지, 어떤 카테고리로 문서가 나뉘는지 정리한 색인 문서다.

## 최상위 기준

1. [보들 플랫폼 기능설명서](보들_플랫폼_기능설명서.pdf)
2. [화면 개편 목표 정리](restructure-target-map.md)
3. [현재 구현 상태](implementation-status.md)
4. [데이터 및 API 초안](data-api-draft.md)

기능 방향이 충돌하면 위 순서대로 판단한다.

## 먼저 볼 문서

새로 작업을 시작할 때는 아래 순서를 기본으로 쓴다.

1. [현재 구현 상태](implementation-status.md)
2. [협업 규칙](collaboration-rules.md)
3. [내부 테스트 가이드](internal-test-guide.md)
4. [화면 개편 목표 정리](restructure-target-map.md)
5. [Firebase 설정](firebase-setup.md)
6. [인프라 개요](infrastructure-overview.md)

## 문서 디렉터리 원칙

- `docs/` 루트에는 자주 직접 여는 핵심 문서를 둔다.
- 카테고리별 탐색은 각 하위 디렉터리의 `README.md`를 진입점으로 쓴다.
- 기존 링크 안정성을 위해 현재 문서 본문 파일은 대부분 `docs/` 루트에 유지한다.
- 구버전 메모, 추출본, 보조 문서는 `docs/archive/`로 분리한다.

## 문서 분류

### 1. 기획/구조 문서

- [기획/구조 문서 색인](planning/README.md)
- [보들 플랫폼 기능설명서](보들_플랫폼_기능설명서.pdf)
- [화면 개편 목표 정리](restructure-target-map.md)
- [MVP 범위](mvp-scope.md)

### 2. 아키텍처 문서

- [아키텍처 문서 색인](architecture/README.md)
- [현재 구현 상태](implementation-status.md)
- [인프라 개요](infrastructure-overview.md)
- [아키텍처 초안](architecture-draft.md)
- [데이터 및 API 초안](data-api-draft.md)

### 3. 운영/테스트 문서

- [운영/테스트 문서 색인](operations/README.md)
- [내부 테스트 가이드](internal-test-guide.md)
- [관리자 권한 QA 체크리스트](admin-access-qa-checklist.md)
- [Firebase 설정](firebase-setup.md)
- [Firebase 운영 도구](firebase-operations-tools.md)
- [Firebase 기준선 초기화](firebase-reset-baseline.md)
- [매니저 서류 등록 메모](manager-document-registration-2026-05-05.md)

### 4. 보안 문서

- [보안 문서 색인](security/README.md)
- [Firestore 보안 정리](firestore-security-hardening.md)
- [보안 리뷰 최신화 메모](security-review-2026-04-29.md)
- [AES 적용 범위 판단](aes-scope-assessment.md)

### 5. 디자인/기능 비교 문서

- [디자인/기능 비교 문서 색인](design/README.md)
- [디자인 레퍼런스 재정리 메모](design-reference-review-2026-05-22.md)
- [기능서/피그마 전체 점검 메모](feature-spec-figma-audit-2026-05-22.md)
- [기능설명서 항목별 구현 체크리스트](feature-spec-gap-checklist-2026-05-22.md)

### 6. 보관 문서

- [보관 문서 색인](archive/README.md)

## 현재 저장소 구성 요약

- `app/`: Android 앱
- `admin-web/`: 관리자 심사/운영 웹
- `functions/`: Firebase Functions
- `tools/firebase/`: 기준선 초기화, 점검, 리포트, 백업/복원 도구
- `docs/`: 문서 루트와 카테고리 색인

## 운영 원칙

- 기능 방향은 기능설명서와 `restructure-target-map`을 먼저 본다.
- 실제 구현 상태는 `implementation-status`를 기준으로 본다.
- 새 기능이나 보안 변경이 들어가면 같은 턴에 `implementation-status`와 관련 상세 문서를 같이 갱신한다.
