# Production PostgreSQL V13 migration·복원 검증

기준일: 2026-07-19

## 작업 목적

빈 production PostgreSQL에 개발 환경에서 검증한 Flyway V4~V13을 적용하고, 적용 전후의 복구 지점을 확보한 뒤 최종 schema와 권한이 격리 환경에 그대로 복원되는지 확인한다.

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다. 현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 실행 순서

1. [V3 기준 백업·복원 run 29669460542](https://github.com/bodeul110/Bodeul/actions/runs/29669460542)에서 원본과 격리 복원 manifest 일치를 확인했다.
2. [첫 production migration run 29669663928](https://github.com/bodeul110/Bodeul/actions/runs/29669663928)에서 V4~V12를 적용했다. V13은 사전 bootstrap role 부재로 트랜잭션 롤백됐다.
3. production Supabase에 `bodeul_retention_runtime`과 `bodeul_retention_service`를 `NOLOGIN`으로 생성하고 최소 권한 membership과 연결 제한을 확인했다.
4. [V12 기준 백업·복원 run 29669805767](https://github.com/bodeul110/Bodeul/actions/runs/29669805767)로 중간 복구 지점을 확보했다.
5. [V13 production migration run 29669867122](https://github.com/bodeul110/Bodeul/actions/runs/29669867122)를 실행해 현재 migration까지 적용했다.
6. [V13 최종 백업·복원 run 29670197027](https://github.com/bodeul110/Bodeul/actions/runs/29670197027)에서 owner, ACL, RLS, 정책, 인덱스, 제약, row 수와 Flyway 이력이 일치하는지 다시 확인했다.

## 최종 결과

- 실행 commit: `319aa9f3b7fcd11084d48aa1f562ec68b150d71e`
- PostgreSQL: 17.6
- Flyway 성공 이력: 13건
- 최신 Flyway version: V13
- 업무·이력 테이블: 13개
- RLS 정책: 33개
- dump 크기: 147,817 bytes
- SHA-256: `beb03721c4b598aeee6f52892fa0c3bcb83069bbeae162c45bc3fe539561ad8a`
- 원본·격리 복원 manifest 일치: 예
- GCS object: `gs://bodeul-prod-110-db-backups/postgres/verified/2026/07/19/20260719T022706Z-bodeul-production/20260719T022706Z-bodeul-production.dump`

Supabase Security Advisor는 경고 0건이었다. Performance Advisor의 알림은 실제 production 트래픽이 없는 상태에서 발생한 미사용 인덱스 INFO뿐이므로, 조회 패턴을 측정하기 전에는 인덱스를 제거하지 않는다.

## 권한 경계

- `bodeul_retention_runtime`은 `NOLOGIN`, `NOINHERIT`이며 V13 파기 함수 실행 권한만 받는다.
- `bodeul_retention_service`는 `NOLOGIN`, `INHERIT`, connection limit 2로 유지한다.
- `PUBLIC`, `anon`, `authenticated`, `service_role`에는 `bodeul` 업무 테이블 grant가 없다.
- production 로그인 비밀번호와 Firebase `RETENTION_DATABASE_URL` Secret은 실제 production 파기 함수를 배포할 때 별도로 생성한다. 현재는 운영 전환 전 오작동을 막기 위해 로그인과 스케줄 실행을 활성화하지 않는다.

## 남은 인프라 범위

- Kakao production REST API key의 실제 Secret version을 등록한 뒤 첫 Core API Cloud Run production revision을 배포한다.
- Vercel Production 관리자 DB 자격 증명과 도메인을 연결하고 rollback을 리허설한다.
- production 파기 함수 배포 시 전용 DB login, Supabase transaction pooler URL, CA Secret을 생성하고 dry-run부터 검증한다.
- 실제 사용자 데이터 투입 전 Supabase Pro, spend cap과 제공자 일일 백업을 활성화한다.
