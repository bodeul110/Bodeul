# Issue 348 Firestore 사용자 문서 부분 영향도

기준일: 2026-08-26

관련 이슈: [#348 탈퇴·삭제와 법정 보존 분리 구현](https://github.com/bodeul110/Bodeul/issues/348)

## 구현 결과

- 계정 삭제 준비도 API가 인증된 `AppUser.firebaseUid`로 `users/{firebaseUid}` 문서 한 건만 정확 조회한다.
- 사용자 문서, 정규화된 FCM token, token metadata, 매니저 증빙 metadata와 경로 참조는 건수만 반환한다.
- token metadata 불일치는 누락, 고아 token, 잘못된 metadata, 동일 token 중복과 Base64 URL·padding 제거 key 불일치를 합산한다.
- 성공 상태도 `FIRESTORE=PARTIAL`이며 삭제 실행, 삭제 판단과 전체 완료 상태는 계속 비활성이다.
- 구성 또는 SDK 오류는 원문 없이 `ERROR`와 `SOURCE_UNAVAILABLE`로 닫힌다.
- UID, token, metadata key, 파일명, Storage 경로와 사용자 문서 원문은 응답과 로그에 포함하지 않는다.

## 검증

- Firestore 저장소·서비스 집중 테스트와 인증 통합 테스트 통과
- 중복·공백·비문자 token, 누락·고아·잘못된·중복 metadata와 정상·오류 key 조합 검증
- `core-api` 전체 `check` 223건 통과
- `git diff --check` 통과

이번 검증은 모의 Firestore SDK와 통합 테스트 대역으로 수행했다. 실제 개발 또는 운영 Firebase 프로젝트를 호출하지 않았으므로 실데이터의 문서 형태, 런타임 서비스 계정 권한과 네트워크 동작은 아직 검증되지 않았다.

## 남은 범위

- 개발 Firebase의 비식별 테스트 계정으로 읽기 전용 실호출 검증
- 사용자 지원·심사·전환 잔존 Firestore 문서 영향도
- Firebase Storage 객체, Firebase Auth 사용자와 백업 영향도
- 실제 탈퇴 승인, 삭제 순서, 부분 실패 복구와 삭제 ledger
