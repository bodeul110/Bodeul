# 문서 안내

기준일: 2026-05-22

이 문서는 현재 저장소에서 어떤 문서를 먼저 보고, 어떤 문서를 기준으로 판단해야 하는지 정리한 안내서다.

## 최상위 기준

1. [보들 플랫폼 기능설명서](보들_플랫폼_기능설명서.pdf)
2. [화면 개편 목표 정리](restructure-target-map.md)
3. [현재 구현 상태](implementation-status.md)
4. [데이터 및 API 초안](data-api-draft.md)

기능 방향이 충돌하면 위 순서대로 판단한다.  
다만 기능설명서 마지막의 AI, 결제 세부 정책, 건강정보 화면 메모는 `추가 기획 메모`로 보고 별도 확정 전까지 구현 기준으로 바로 승격하지 않는다.

## 먼저 볼 문서

새로 작업을 시작할 때는 아래 순서를 기본으로 한다.

1. [현재 구현 상태](implementation-status.md)
2. [협업 규칙](collaboration-rules.md)
3. [내부 테스트 가이드](internal-test-guide.md)
4. [화면 개편 목표 정리](restructure-target-map.md)
5. [Firebase 설정](firebase-setup.md)
6. [인프라 개요](infrastructure-overview.md)

## 문서 분류

### 1. 제품 기준 문서

- [보들 플랫폼 기능설명서](보들_플랫폼_기능설명서.pdf)
  - 최신 화면/기능 요구사항 원본
- [화면 개편 목표 정리](restructure-target-map.md)
  - 기능설명서를 현재 앱 구조에 맞게 다시 푼 문서
- [MVP 범위](mvp-scope.md)
  - 최신 기능설명서를 기준으로 현재 단계에서 실제로 닫을 범위를 정리한 문서

### 2. 구현 기준 문서

- [현재 구현 상태](implementation-status.md)
  - 최근 작업 내역, 변경 파일, 남은 범위
- [인프라 개요](infrastructure-overview.md)
  - Android 앱, 관리자 웹, Firebase, 운영 도구의 실제 런타임 구성
- [아키텍처 초안](architecture-draft.md)
  - Android 앱, 관리자 웹, Firebase 도구의 현재 역할 분리 기준
- [데이터 및 API 초안](data-api-draft.md)
  - Firestore 문서 구조와 서버 계약 초안

### 3. 운영/테스트 문서

- [내부 테스트 가이드](internal-test-guide.md)
  - 테스트 계정, 더미 데이터, 역할별 테스트 순서
- [관리자 권한 QA 체크리스트](admin-access-qa-checklist.md)
  - 관리자 앱/웹 권한 테스트 기준
- [Firebase 설정](firebase-setup.md)
  - Firebase 프로젝트, 규칙, Storage, App Check 준비 상태
- [Firebase 운영 도구](firebase-operations-tools.md)
  - `tools/firebase` 사용 기준

### 4. 보안/정책 문서

- [Firestore 보안 정리](firestore-security-hardening.md)
- [보안 리뷰 최신화 메모](security-review-2026-04-29.md)
- [AES 적용 범위 판단](aes-scope-assessment.md)

### 5. 디자인/기능 비교 문서

- [디자인 레퍼런스 재정리 메모](design-reference-review-2026-05-22.md)
  - 최신 분할 화면 세트 기준으로 UI polish 우선순위를 다시 잡은 메모
- [기능서/피그마 전체 점검 메모](feature-spec-figma-audit-2026-05-22.md)
  - 최신 기능설명서와 최신 피그마를 각각 다시 읽고 차이를 분리한 문서
- [기능설명서 항목별 구현 체크리스트](feature-spec-gap-checklist-2026-05-22.md)
  - 기능설명서의 20개 항목과 추가 메모를 완료도 기준으로 자른 문서

## 현재 저장소 구성 요약

- `app/`: Android 앱
- `admin-web/`: 관리자 심사/운영 웹
- `functions/`: Firebase Functions
- `tools/firebase/`: 기준선 초기화, 점검, 리포트, 백업/복원 도구

## 현재 문서 운용 원칙

- 기능 방향은 기능설명서와 `restructure-target-map`을 먼저 본다.
- 실제로 무엇이 구현됐는지는 `implementation-status`를 기준으로 본다.
- 새 기능 또는 보안 변경이 들어가면 같은 턴에 `implementation-status`와 관련 상세 문서를 같이 갱신한다.
