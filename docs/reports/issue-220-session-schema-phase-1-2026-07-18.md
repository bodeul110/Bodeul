# Issue 220 매칭·동행·리포트 스키마 1단계 기록

기준일: 2026-07-18

## 구현한 내용

- Flyway V5에 `companion_sessions`, `session_reports`, `appointment_follow_ups`, `companion_session_assignment_audits`를 추가했다.
- 관리자 배정은 role·상태·예약 버전을 검증하는 `assign_companion_session` 함수로 제한했다.
- V5 DDL rollback을 추가했다.
- Firestore 백업의 FK, 상태 코드와 시각을 검증하는 세션 전용 seed SQL 생성기와 rollback 경로를 추가했다.
- `nextVisitAt`의 날짜·자유 텍스트 혼용을 확인하고 정규화 시각과 원문을 함께 보존했다.

## 변경된 범위

- 이번 단계는 PostgreSQL schema, 최소 read/함수 권한, 백필 도구만 다룬다.
- Android와 관리자 웹의 현재 Firestore 쓰기 경로는 아직 바꾸지 않았다.
- `chatMessages`, 위치 좌표·이력·읽음 시각은 #221 범위로 제외했다.

## 검증

| 항목 | 결과 |
| --- | --- |
| 최신 백업 seed dry-run | 세션 2, 리포트 2, 후속 처리 1, 오류 0 |
| Firebase 도구 테스트 | 29건 통과 |
| V5 migration 계약 테스트 | 통과 |
| PostgreSQL 17 V1~V5 적용 | 통과 |
| 잘못된 예약 버전의 배정 | 거부 확인 |
| 관리자 배정 함수 | 예약 `MATCHED`, version 1, 세션 `READY`, 감사 1건 확인 |
| Core runtime의 배정 함수 실행 | 권한 거부 확인 |
| V5 rollback | 신규 테이블 4개 제거 확인 |
| `git diff --check` | 통과 |

PR #228 병합 후 개발 DB migration run `29638503856`에서 Flyway V5 적용을 완료했다. V5 이력 성공, 신규 테이블 4개의 owner `bodeul_migration`, RLS 활성화, Core/Admin SELECT 정책 7개를 확인했다. 배정 함수는 `security definer`이고 Admin runtime만 실행 가능하며 Core·Supabase client role은 실행할 수 없다.

로컬 PostgreSQL 리허설에는 임시 데이터만 사용했고 컨테이너는 종료 후 삭제했다. 최신 Firestore 백업과 생성 SQL은 Git 제외 경로에 있으며 커밋하지 않는다.

## 남은 범위

- 개발 DB에 세션·리포트·후속 처리 백필을 적용하고 row/FK/권한/advisor를 확인한다.
- Core API의 매니저 세션 조회·진행·리포트와 매칭 후 취소 트랜잭션을 구현한다.
- 별도 관리자 서버의 배정 API를 새 함수에 연결한다.
- Android 실기기와 관리자 Preview에서 같은 PostgreSQL 상태를 보는지 검증한다.
- 검증 완료 후 해당 Firestore 쓰기를 중지한다.
