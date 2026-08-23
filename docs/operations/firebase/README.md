# Firebase 운영 문서

Firebase 프로젝트 설정, 개발 기준선 초기화, 운영 도구 사용법을 모아둔다.

## 문서

- [Firebase 설정](setup.md)
  - 프로젝트 연결, 규칙, App Check, Storage, 지도 API 설정
- [Firebase 운영 도구](tools.md)
  - `tools/firebase` 스크립트와 preflight/backup/restore/report 실행법
- [Firebase 기준선 초기화](reset-baseline.md)
  - Firestore 개발 데이터 리셋과 재시드 기준
- [개발 Firebase 자동 파기 픽스처](retention-development-fixture.md)
  - 전환 문서와 매니저 증빙의 격리 setup/dry-run/apply/status/cleanup 절차
- [Production Firebase 자동 파기 격리 픽스처](retention-production-fixture.md)
  - 보호된 workflow, 전용 WIF와 정책 승인 뒤 합성 데이터만 검증하는 절차

## 같이 볼 문서

- [내부 테스트 가이드](../internal-test-guide.md)
- [Firestore 보안 정리](../../security/firestore-hardening.md)
- [FCM 토큰 수명주기 정책](../../security/fcm-token-lifecycle-policy.md)
