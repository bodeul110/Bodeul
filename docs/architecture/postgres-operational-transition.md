# PostgreSQL 운영 전환 결정

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결정

Firebase를 한 번에 제거하지 않고 관계형 데이터가 필요한 도메인부터 Supabase PostgreSQL로 옮긴다.

- Firebase Auth, FCM, Storage와 Firebase 결합 Functions는 유지한다.
- 사용자·매니저 API는 Cloud Run의 Spring Core API가 담당한다.
- 관리자 API는 Vercel의 Next.js 관리자 서버가 담당한다.
- 두 서버는 공용 PostgreSQL을 사용하되 서로를 경유하지 않는다.
- Firestore는 전환 전 도메인의 source of truth로 유지한다.

## 현재 상태

| 단계 | 상태 |
| --- | --- |
| Supabase 개발 DB와 private `bodeul` schema | 완료 |
| migration/core/admin role 분리 | 완료 |
| Spring Core API Cloud Run 배포와 인증·DB 검증 | 완료 |
| Kakao Local REST의 Core API 이전 | 완료 |
| Next.js 관리자 서버의 인증·인가·병원 가이드 조회 | 완료 |
| Node API와 메인 관리자 웹 중복본 종료 | 완료 |
| 예약 요청 PostgreSQL read model 백필 | 완료, source of truth는 Firestore |
| 도메인별 쓰기 전환 | 진행 전/부분 준비 |
| production 프로젝트 분리 | 미완료 |

## 선택한 방식

1. 같은 도메인의 쓰기 source of truth는 한 곳만 둔다.
2. Firebase Auth를 인증 기준으로 유지하고 Supabase Auth를 병행하지 않는다.
3. 클라이언트는 PostgreSQL에 직접 접속하지 않는다.
4. DDL과 migration은 메인 저장소 `core-api/`의 Flyway만 소유한다.
5. runtime 계정은 migration 권한을 갖지 않는다.
6. 백필과 read model 생성만으로 source of truth를 바꾸지 않는다.
7. 실시간 위치처럼 쓰기 빈도가 높은 기능은 부하와 운영 비용을 확인한 뒤 마지막에 판단한다.

## 도메인 전환 순서

### 1. 낮은 위험 read model

병원 가이드와 예약 요청처럼 비교 가능한 조회 모델을 PostgreSQL에 만든다. row 수, 필수 필드, 관계와 API 응답을 기존 Firebase 데이터와 비교한다.

### 2. 관리자 쓰기

병원 가이드 수정, 문의 처리, 매니저 심사 메타데이터를 관리자 서버 API로 옮긴다. 쓰기 전에는 감사 로그, 세분화한 DB 권한과 rollback을 준비한다.

### 3. 예약·매칭·리포트

Spring Core API 계약, migration, backfill, read 비교와 Android feature flag를 함께 준비한다. cutover 시 Firestore 쓰기는 중단하거나 종료 조건이 있는 shadow write로 제한한다.

### 4. 실시간 기능

위치, 채팅, 상태 스트림은 Firestore 유지, Supabase Realtime, 서버 WebSocket/SSE를 부하 테스트한 뒤 선택한다. PostgreSQL 전환 자체를 이유로 즉시 옮기지 않는다.

## 대안

| 대안 | 판단 |
| --- | --- |
| Firebase 전체 유지 | 구현 부담은 작지만 관계형 무결성·감사·통계 확장에 한계가 있다. |
| Supabase 전체 전환 | Auth·Storage·푸시까지 동시에 흔들어 현재 규모의 위험이 크다. |
| PostgreSQL 직접 운영 | 패치·백업·장애 대응 부담이 커 관리형 Supabase보다 우선하지 않는다. |
| Neon | DB 분리는 가능하지만 현재 팀이 검증한 Supabase 운영 경로를 바꿀 근거가 없다. |

## 리스크

| 리스크 | 대응 |
| --- | --- |
| Firestore/PostgreSQL 불일치 | 도메인별 source of truth와 비교 리포트 유지 |
| 서버별 schema 해석 차이 | 공용 Flyway와 계약 문서 사용 |
| DB 연결 고갈 | 서버별 role과 connection limit, 작은 pool 유지 |
| role 동기화 지연 | UID 연결과 권한 변경 절차·감사 로그 추가 |
| rollback 중 데이터 손실 | cutover 전 backup/restore와 역방향 보정 절차 리허설 |

## 전환 완료 조건

- production 프로젝트와 자격 증명이 개발과 분리된다.
- 각 전환 도메인의 쓰기 주체가 하나로 정해진다.
- migration, backfill, 결과 비교, rollback과 restore가 반복 가능하다.
- 역할별 실제 401·403·정상 응답과 감사 로그를 확인한다.
- 비용·연결 수·오류율을 관측하고 장애 담당을 정한다.

## 관련 문서

- [목표 인프라 구조](target-infrastructure.md)
- [PostgreSQL API 경계](postgres-api-boundary.md)
- [PostgreSQL 운영 전환 런북](../operations/postgres-operational-transition-runbook.md)
- [예약 요청 read model 검증](../reports/issue-202-appointment-requests-read-model-2026-07-17.md)
