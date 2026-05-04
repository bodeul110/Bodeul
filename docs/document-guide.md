# 문서 안내

기준일: 2026-05-05

이 문서는 현재 저장소에서 어떤 문서를 먼저 보고, 어떤 상황에서 어느 문서를 기준으로 삼아야 하는지 정리한 안내서다.

## 먼저 볼 문서

새로 작업을 시작할 때는 아래 순서로 보는 것을 기준으로 한다.

1. [현재 구현 상태](implementation-status.md)
2. [협업 규칙](collaboration-rules.md)
3. [전면 개편 목표 정리](restructure-target-map.md)
4. [Firebase 설정](firebase-setup.md)

## 문서 우선순위

기능 방향과 작업 기준이 충돌하면 아래 순서로 판단한다.

1. 최신 기능설명서
2. [전면 개편 목표 정리](restructure-target-map.md)
3. [현재 구현 상태](implementation-status.md)
4. [데이터 및 API 초안](data-api-draft.md)

## 문서 분류

### 1. 현재 작업 기준

- [현재 구현 상태](implementation-status.md)
  - 가장 최근 작업 범위, 변경 파일, 남은 범위를 기록한다.
- [협업 규칙](collaboration-rules.md)
  - 여러 작업자가 동시에 수정할 때의 시작 절차, 동기화, 충돌 방지 규칙을 다룬다.
- [관리자 권한 QA 체크리스트](admin-access-qa-checklist.md)
  - 관리자 앱/웹, 권한 실패 시나리오, 매니저 서류 검토 흐름을 반복 점검할 때 쓴다.

### 2. 제품 구조와 범위

- [전면 개편 목표 정리](restructure-target-map.md)
  - 기능설명서를 현재 앱 화면과 도메인 구조로 풀어놓은 기준 문서다.
- [MVP 범위](mvp-scope.md)
  - 초기 MVP 기준을 정리한 문서다. 현재 구현보다 범위가 좁으므로 역사적 참고용에 가깝다.
- [아키텍처 초안](architecture-draft.md)
  - 프로젝트 초반 구조 설명 문서다. 현재는 역할 분리 원칙 참고용으로 본다.
- [팀 작업 분담](team-task-breakdown.md)
  - 팀별 역할 분담과 초기 계획을 확인할 때 쓴다.

### 3. 데이터와 Firebase 운영

- [데이터 및 API 초안](data-api-draft.md)
  - Firestore 문서 구조, 서버 계약 초안, 관리자/후속 처리 응답 모델 기준을 다룬다.
- [Firebase 설정](firebase-setup.md)
  - Firebase 프로젝트 연결, 컬렉션 구조, Storage/App Check 운영 메모를 모아둔다.
- [Firebase 운영 도구](firebase-operations-tools.md)
  - `tools/firebase` 스크립트 사용법, 기준선 초기화, 백업/복원, 운영 리포트 흐름을 다룬다.
- [Firestore 보안 정리](firestore-security-hardening.md)
  - Firestore rules 축소, callable 전환, 권한 검증 기록을 다룬다.
- [Firebase 기준선 초기화](firebase-reset-baseline.md)
  - Firestore 기준선 리셋 절차만 따로 빠르게 볼 때 쓴다.

### 4. 보안 기준

- [보안 리뷰 최신화 메모](security-review-2026-04-29.md)
  - 현재 남은 보안 위험과 우선순위를 요약한다.
- [AES 적용 범위 판단](aes-scope-assessment.md)
  - `AES-256 이상` 요구를 현재 구조에서 어떻게 해석할지 정리한다.

## 현재 저장소 구성 요약

- `app/`
  - Android 앱 본체
- `admin-web/`
  - 관리자 승인/운영 웹
- `functions/`
  - Firebase Functions
- `tools/firebase/`
  - 기준선 초기화, 상태 점검, 리포트, 백업/복원 같은 로컬 운영 도구

## 현재 상태 한 줄 요약

- Android 앱, 관리자 웹, Firebase 운영 도구, CI 프리플라이트까지 기준선이 잡혀 있다.
- Play Console 준비 전에는 App Check 실제 enforcement만 보류 상태다.
- 현재 남은 큰 일은 신규 기능 구현보다 QA, 운영 절차, 보안 강제 적용 준비에 가깝다.
