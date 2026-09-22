# 기능 메모

특정 기능의 구현 판단, 운영 흐름, 후속 정리 사항을 기능 단위로 모아둔다.

## 현재 기능 계약

- [예약 API](../architecture/appointment-core-api.md)
- [동행·리포트 API](../architecture/companion-session-core-api.md)
- [관리자 역할·자격 심사](../architecture/admin-rbac.md)
- [무통장입금 상태](../architecture/admin-bank-transfer-payment-contract.md)
- [현재 화면 구현 지도](../design/figma-mvp-implementation-map-2026-08-29.md)

## 과거 구현 메모

아래는 당시 기록이다. 현재 자격 증빙·서버 권한 계약을 대신하지 않는다.

- [매니저 서류 등록 인증 화면 메모](manager-document-registration-2026-05-05.md)

## 갱신 기준

- 기능 단위로 오래 참고해야 하는 구현 메모만 둔다.
- 날짜별 검증 결과는 `../reports/`로 분리한다.
- 보안 판단은 `../security/`로 분리한다.
