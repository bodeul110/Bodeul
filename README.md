# 보들

보들은 환자, 보호자, 매니저를 연결하는 병원 동행 플랫폼입니다.

## 기준 문서

- 최신 기능 기준: [보들 플랫폼 기능설명서](docs/보들_플랫폼_기능설명서.pdf)
- 화면/흐름 재구성 기준: [화면 개편 목표 정리](docs/restructure-target-map.md)
- 실제 구현 기준: [현재 구현 상태](docs/implementation-status.md)

## 현재 저장소 구성

- `app/`: Android 앱 본체
- `admin-web/`: 관리자 서류 심사 및 운영 웹
- `functions/`: Firebase Functions
- `tools/firebase/`: 기준선 초기화, 상태 점검, 백업/복원, 운영 리포트 도구

## 기능 축

1. 진입 및 계정 설정
2. 서비스 요청 및 예약
3. 매칭 및 홈 화면
4. 실시간 동행 중
5. 서비스 종료 및 정산

최신 기능설명서에는 AI 음성 정리, OCR 기반 복약 정보 정리, 건강정보 화면, 결제 세부 정책 같은 후속 기획 메모도 포함되어 있습니다. 이 항목들은 별도 확정 전까지 `기획 메모`로 취급합니다.

## 먼저 볼 문서

1. [문서 안내](docs/document-guide.md)
2. [현재 구현 상태](docs/implementation-status.md)
3. [내부 테스트 가이드](docs/internal-test-guide.md)
4. [화면 개편 목표 정리](docs/restructure-target-map.md)
5. [협업 규칙](docs/collaboration-rules.md)

## 주요 문서

- [문서 안내](docs/document-guide.md)
- [현재 구현 상태](docs/implementation-status.md)
- [내부 테스트 가이드](docs/internal-test-guide.md)
- [협업 규칙](docs/collaboration-rules.md)
- [관리자 권한 QA 체크리스트](docs/admin-access-qa-checklist.md)
- [화면 개편 목표 정리](docs/restructure-target-map.md)
- [MVP 범위](docs/mvp-scope.md)
- [아키텍처 초안](docs/architecture-draft.md)
- [데이터 및 API 초안](docs/data-api-draft.md)
- [Firebase 설정](docs/firebase-setup.md)
- [Firebase 운영 도구](docs/firebase-operations-tools.md)
- [Firestore 보안 정리](docs/firestore-security-hardening.md)
- [보안 리뷰 최신화 메모](docs/security-review-2026-04-29.md)
- [AES 적용 범위 판단](docs/aes-scope-assessment.md)
- [디자인 레퍼런스 검토 메모](docs/design-reference-review-2026-05-05.md)
- [기능서/피그마 전체 점검 메모](docs/feature-spec-figma-audit-2026-05-22.md)
- [기능설명서 항목별 구현 체크리스트](docs/feature-spec-gap-checklist-2026-05-22.md)

## 실행

```powershell
.\gradlew.bat assembleDebug
```

## 작업 시작 순서

```powershell
git fetch origin
git pull --rebase origin master
.\gradlew.bat assembleDebug
```

작업 전 동기화와 최근 작업자 확인 절차는 [협업 규칙](docs/collaboration-rules.md)을 기준으로 합니다.

## 데모 로그인

- 관리자: `admin@bodeul.app` / `bodeul1234`
- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`

더미 데이터와 테스트 순서는 [내부 테스트 가이드](docs/internal-test-guide.md)에 정리되어 있습니다.
