# 운영 문서

개발 협업, 내부 테스트, QA, Firebase 운영 절차를 모아둔다.

## 협업/테스트

- [협업 규칙](collaboration-rules.md)
  - 작업 시작 전 확인, 충돌 방지, 문서 갱신 원칙
- [내부 테스트 가이드](internal-test-guide.md)
  - 테스트 계정, 더미 데이터, 역할별 테스트 순서
- [관리자 권한 QA 체크리스트](admin-access-qa-checklist.md)
  - 관리자 앱/웹 권한 검증 시나리오
- [GitHub self-hosted runner 운영 기준](github-self-hosted-runner.md)
  - 수동 프리플라이트용 자체 실행기 등록, 라벨, 보안 기준
- [App Check 적용 로드맵](app-check-enforcement-roadmap.md)
  - Android, 관리자 웹, Functions, Firestore, Storage enforcement 전환 기준
- [관리자 웹 레포 분리 준비 계획](admin-web-repository-split.md)
  - 관리자 웹 분리 전 데이터 계약, 배포 경계, 소유권 정리
- [관리자 웹 GitHub Environment 기준](admin-web-environments.md)
  - 관리자 웹 preview/production 환경, 변수, secret, 배포 승인 기준
- [인프라 운영 기준](infrastructure-operations-baseline.md)
  - 관리자 웹 배포 방식, 비용 리스크, App Check, 인덱스, 백업/복원, Functions, 운영 명령어

## Firebase 운영

- [Firebase 운영 문서](firebase/README.md)
- [Firebase 설정](firebase/setup.md)
- [Firebase 운영 도구](firebase/tools.md)
- [Firebase 기준선 초기화](firebase/reset-baseline.md)

## 관련 문서

- [FCM 토큰 수명주기 정책](../security/fcm-token-lifecycle-policy.md)
- [카카오 지도 실좌표 설정 메모](firebase/setup.md#카카오-병원약국-실좌표-검색-메모)
- [실기기 테스트 보고서](../reports/device-test-report-2026-06-20.md)
