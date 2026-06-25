# Firestore 선택 근거

기준일: 2026-06-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

이 문서는 Firestore 선택 이유만 따로 설명한다. 더 긴 비교는 [DB 선택 근거](database-selection.md)를 기준으로 본다.

## 결론

현재 BoDeul 데이터 구조는 예약 요청, 동행 세션, 리포트, 문의, 관리자 운영 상태처럼 문서 단위로 읽고 갱신하는 흐름이 중심이다. 그래서 초기 MVP에서는 Cloud Firestore가 MySQL/PostgreSQL보다 현재 규모에 맞는다.

## 작업 목적

“왜 Firestore인가?”, “왜 MySQL/PostgreSQL이 아닌가?”, “나중에 어떻게 바꿀 것인가?”에 답한다.

## 선택한 방식

- `users`, `appointmentRequests`, `companionSessions`, `sessionReports`, `supportInquiries` 같은 컬렉션을 도메인별 문서 계약으로 관리한다.
- Android 앱과 관리자 웹은 같은 Firestore 문서 계약을 공유한다.
- 역할 권한은 `users/{uid}.role`과 Rules 함수로 판정한다.
- 서버 검증이 필요한 알림/큐/외부 API 연동은 Cloud Functions로 분리한다.

## 대안

| 대안 | 장점 | 현재 보류 이유 |
| --- | --- | --- |
| MySQL/PostgreSQL | 조인, 정산, 통계, 트랜잭션 무결성에 강하다. | API 서버와 ORM, 배포, 운영, 권한 검증 계층이 추가된다. |
| Supabase/PostgreSQL | SQL과 RLS를 쓰면서 Realtime도 제공한다. | Firebase Auth/FCM/Storage/Functions와의 현재 결합을 다시 설계해야 한다. |
| Realtime Database | 실시간 동기화가 단순하다. | 도메인별 문서 계약, 관리자 조회, Rules 유지보수에는 Firestore가 더 적합하다. |
| BigQuery | 장기 분석과 대용량 집계에 강하다. | 운영 트랜잭션 저장소가 아니라 분석 저장소다. |

## 선택 이유

- 예약/세션/리포트/문의는 문서 단위 상태 전이가 중심이다.
- 실시간 위치, 채팅, 예약 상태 변경은 Firestore listener와 FCM 트리거로 연결하기 쉽다.
- 관리자 웹은 Firestore를 직접 읽어 매니저 심사와 운영 상태를 확인할 수 있다.
- 현재 팀 규모에서는 별도 DB 서버와 API 서버를 운영하는 비용보다 Firebase 통합 이익이 크다.
- Rules와 Storage Rules가 같은 `users/{uid}.role` 기준을 공유하므로 권한 설명이 단순하다.

## 리스크

- 관계형 조인이 필요한 정산/통계/검색이 커지면 쿼리가 복잡해진다.
- 관리자 대시보드가 컬렉션 전체 스캔에 가까워지면 read 비용과 성능 리스크가 커진다.
- Rules에서 `users/{uid}.role`을 읽는 구조는 단순하지만, 관리자 수가 늘면 custom claims 검토가 필요하다.
- 데이터 모델이 중복 저장과 집계 문서를 요구할 수 있다.

## 전환 조건

- 정산, 통계, 검색이 MVP 보조 기능이 아니라 핵심 운영 기능이 된다.
- Firestore 읽기/쓰기 비용이 반복적으로 예산 기준을 넘는다.
- 여러 컬렉션 조인이 대부분의 화면에서 필수가 된다.
- 관리자/매니저 권한 정책이 복잡해져 Rules보다 서버 API가 더 안전해진다.

