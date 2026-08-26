# Production DB migration 사전 점검 준비 및 실행 결과

기준일: 2026-08-26

## 확인 결과

- GitHub Actions 기록상 마지막 production migration은 Flyway V13이며, V14와 V15는 preview DB에서만 검증됐다.
- 마지막 production 백업·격리 복원 성공 기록은 2026-07-19이다.
- 최신 백업을 만들기 위해 실행한 [run 32953973126](https://github.com/bodeul110/Bodeul/actions/runs/32953973126)은 dump 시작 전에 production Supabase pooler가 등록된 tenant/user를 찾지 못해 실패했다.
- 실패 실행에서는 dump 생성, GCS 업로드와 Flyway migration이 수행되지 않았다.
- 문서에 기록된 production project endpoint는 DNS에서 확인되지 않았고 같은 환경의 개발 project endpoint는 정상 해석됐다. production project가 일시 중지·삭제·교체됐거나 GitHub Environment 연결 정보가 오래된 상태일 수 있어 dashboard 확인 전에는 원인을 확정하지 않는다.
- [읽기 전용 사전 점검 run 32958462879](https://github.com/bodeul110/Bodeul/actions/runs/32958462879)은 project, commit, Environment 설정과 JDBC 대상 형식 검증을 통과한 뒤 실제 JDBC 연결 또는 첫 조회에서 실패했다.
- 따라서 저장된 URL·사용자명 형식과 기대 project ref의 일치는 확인됐지만 production DB의 가용성, Flyway version과 데이터 건수는 확인되지 않았다.

## 반영 내용

- 별도 수동 workflow에서 production DB 연결 대상과 Flyway V13~V15 상태를 읽기 전용 transaction으로 점검한다.
- V13은 V14 backfill 대상 건수, V14·V15는 `UNRESOLVED_LEGACY` snapshot 잔여 건수를 구분한다.
- migration과 backup workflow도 Flyway·dump 전에 production Supabase ref, host, port, database, TLS와 migration login role을 검증한다.
- JDBC URL은 단일 host와 명시 port를 사용한 `/postgres?sslmode=require` 형태만 허용한다. URL query의 endpoint·credential override와 다중 host는 PGJDBC 처리 전에 차단한다.
- GitHub Environment `core-api-migration-production`에 `PRODUCTION_SUPABASE_PROJECT_REF` 기대값을 등록했다.

## 검증

- Core API 전체 `check` 통과
- 대상 검증과 읽기 전용 집계 단위 테스트 통과
- 비밀번호 없이 실행한 target-only Gradle 검증 통과
- 변경 workflow YAML 파싱과 `actionlint` 통과
- JDBC override, TLS 비활성화, 잘못된 login role, 다중 host와 로그 노출 회귀 테스트 통과

로컬·CI 검증은 mock JDBC와 가상 connection string으로 수행했다. 병합 후 실제 production Environment를 사용한 읽기 전용 점검은 연결 또는 첫 조회에서 닫힌 방식으로 실패했다. 저장된 production connection secret은 변경하지 않았고, Flyway migration, dump, GCS 업로드와 배포는 수행하지 않았다.

## 차단 상태

Production Supabase dashboard에서 project의 실제 상태와 ref, 접근 권한과 자격 증명 변경 여부를 확인한다. 확인 결과에 따라 `MIGRATION_DB_*`를 복구하거나 교체한 뒤 읽기 전용 사전 점검, 최신 백업과 격리 복원을 순서대로 통과하기 전에는 V14·V15 production migration을 실행하지 않는다.
