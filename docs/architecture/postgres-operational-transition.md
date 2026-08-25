# PostgreSQL 운영 전환 결정

기준일: 2026-08-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결정

Firebase를 한 번에 제거하지 않고 관계형 데이터가 필요한 도메인부터 Supabase PostgreSQL로 옮긴다.

- Firebase Auth, FCM, Storage와 Firebase 결합 Functions는 유지한다.
- 사용자·매니저 API는 Cloud Run의 Spring Core API가 담당한다.
- 관리자 API는 Vercel의 Next.js 관리자 서버가 담당한다.
- 두 서버는 공용 PostgreSQL을 사용하되 서로를 경유하지 않는다.
- 전환 대상 예약·세션 등 Core 업무 Firestore 문서는 전환 전까지 source of truth로 유지하고, 전환 후에는 읽기 전용 rollback 기간을 거쳐 제거한다. 인증 프로필·지원·매니저 서류 심사 메타데이터는 유지한다.
- 실시간 채팅·위치·상태는 Supabase Realtime private Broadcast로 전달하되 모든 영속 쓰기는 서버를 거친다.

## 현재 상태

| 단계 | 상태 |
| --- | --- |
| Supabase 개발 DB와 private `bodeul` schema | 완료 |
| migration/core/admin role 분리 | 완료 |
| Spring Core API Cloud Run Preview 배포와 인증·DB 검증 | 개발/Preview 완료 |
| Kakao Local REST의 Core API 이전 | 완료 |
| Next.js 관리자 서버의 인증·인가·병원 가이드 조회 | Vercel Preview 완료 |
| Node API와 메인 관리자 웹 중복본 종료 | 완료 |
| 예약 요청 PostgreSQL read model 백필 | 완료 |
| 예약 생성·수정·취소 PostgreSQL 쓰기 전환 | 개발 환경 완료, Core API 단일 쓰기 |
| 매칭·동행·리포트·후속 처리 전환 | 개발 환경 완료, 관리자 배정과 Core API 경계 분리 |
| 채팅·읽음·위치와 Realtime 전환 | 개발 환경 완료, Firestore client 쓰기 차단 |
| 세션 첨부 Core API 중계 | 개발 환경과 실기기 검증 완료, production 게이트 대기 |
| 자동 파기 | Core 중첩 첨부와 Firestore 전환 문서·매니저 증빙의 개발 fixture APPLY·cleanup 완료. 정책 충돌 해소와 production 게이트 대기 |
| production 프로젝트 분리 | 완료 |
| production PostgreSQL 복원 리허설 | 완료 |
| production 유료 등급과 실제 트래픽 | 미전환 |

## 선택한 방식

1. 같은 도메인의 쓰기 source of truth는 한 곳만 둔다.
2. Firebase Auth를 인증 기준으로 유지하고 Supabase Auth를 병행하지 않는다.
3. 클라이언트는 PostgreSQL에 직접 접속하지 않는다.
4. DDL과 migration은 메인 저장소 `core-api/`의 Flyway만 소유한다.
5. runtime 계정은 migration 권한을 갖지 않는다.
6. 백필과 read model 생성만으로 source of truth를 바꾸지 않는다.
7. 실시간 위치처럼 쓰기 빈도가 높은 기능은 PostgreSQL에 최신·요약 이력만 저장하고 Realtime Broadcast로 화면 갱신을 전달한다.
8. Firestore와 PostgreSQL의 무기한 이중 쓰기를 금지한다.

## 도메인별 현재 경계

### 1. PostgreSQL로 전환한 Core 업무 도메인

- 예약 생성·수정·취소
- 관리자 서버의 매니저 배정
- 동행 세션·리포트·후속 처리
- 환자·보호자·매니저 직접 채팅과 읽음 상태
- 위치 공유와 최근 10건 이력
- 세션 채팅 첨부 메타데이터와 만료 상태

예약·동행·채팅·위치 쓰기는 Android에서 Firestore로 fallback하지 않는다. Realtime은 PostgreSQL 커밋 알림이고 재연결 시 Core API snapshot을 다시 읽는다.

매칭 배정은 관리자 Next.js 서버가 `assign_companion_session` admin-only 함수를 실행한다. 매니저 self-accept API는 현재 없다.

### 2. Firebase에 유지하는 경계

- Firebase Auth와 App Check
- 인증 프로필, 지원·문의와 매니저 서류 심사 메타데이터의 Firestore 계약
- 매니저 서류와 세션 첨부 원본의 Firebase Storage 계약
- FCM과 Firebase 결합 Functions
- 전환 전 예약·세션 문서의 제한적 rollback 비교 읽기

Firebase에 남은 도메인을 PostgreSQL로 자동 이전하지 않는다. 각 도메인에 별도 계약과 검증이 생길 때만 경계를 바꾼다.

### 3. production 전환 대기

개발 환경의 Core 도메인 전환과 실기기 검증, Core 중첩 첨부·Firestore 전환 문서·매니저 증빙 파기 리허설은 완료했다. production에서는 보관기간 충돌 해소, Kakao 키, Cloud Run·Vercel 자격 증명, App Check, custom domain, rollback과 법률 문서 대조를 Go/No-Go에서 다시 확인한다.

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
- 전환 대상 Core 도메인의 Firestore 업무 쓰기가 0건이고, 읽기 전용 rollback 기간 종료 뒤 legacy 경로를 제거한다.
- 위치, 채팅과 세션 첨부 자동 파기 job은 개발 검증을 유지하고 production fixture로 다시 확인한다.

## 관련 문서

- [목표 인프라 구조](target-infrastructure.md)
- [PostgreSQL API 경계](postgres-api-boundary.md)
- [PostgreSQL 운영 전환 런북](../operations/postgres-operational-transition-runbook.md)
- [예약 요청 read model 검증](../reports/issue-202-appointment-requests-read-model-2026-07-17.md)
- [2026년 Production 운영 전환 계획](../operations/production-transition-plan-2026.md)
- [데이터 보관 및 파기 정책](../operations/data-retention-policy.md)
