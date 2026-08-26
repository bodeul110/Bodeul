# Production DB V15 migration·복원 검증

기준일: 2026-08-26

## 작업 목적

자동 일시 중지됐던 production Supabase를 재개한 뒤 실제 연결 대상과 데이터 상태를 읽기 전용으로 확인하고, 복구 지점을 확보한 상태에서 Flyway V14·V15를 적용해 계정 삭제 영향도 계약까지 검증한다.

## 선택한 방식

- `core-api-migration-production` Environment의 보호 승인과 저장소의 production 대상 검증을 그대로 사용했다.
- migration 전후에 읽기 전용 readiness를 실행하고, 각 시점의 logical dump를 별도 PostgreSQL 17 컨테이너에 복원한 경우에만 비공개 GCS에 보관했다.
- DB host, project ref, 사용자명, 비밀번호, dump 경로와 checksum 원문은 문서와 Issue에 기록하지 않는다.

## 실행 결과

| 순서 | 실행 | 결과 |
| --- | --- | --- |
| 1 | [migration 전 readiness run 32980526711](https://github.com/bodeul110/Bodeul/actions/runs/32980526711) | Flyway V13, 실패 이력 0건, 병원 가이드·동행 세션·활성 세션·V14 backfill 후보 모두 0건 |
| 2 | [migration 전 backup·restore run 32980749558](https://github.com/bodeul110/Bodeul/actions/runs/32980749558) | logical dump 생성, 격리 복원과 manifest 대조, 비공개 GCS 업로드 성공 |
| 3 | [V14·V15 migration run 32981200371](https://github.com/bodeul110/Bodeul/actions/runs/32981200371) | V14 guide snapshot 고정과 V15 계정 삭제 영향도 계약 적용, `verifyAccountDeletionInventory` 통과 |
| 4 | [migration 후 readiness run 32981484159](https://github.com/bodeul110/Bodeul/actions/runs/32981484159) | Flyway V15, 실패 이력 0건, 병원 가이드·동행 세션·활성 세션 모두 0건 |
| 5 | [migration 후 backup·restore run 32981633994](https://github.com/bodeul110/Bodeul/actions/runs/32981633994) | V15 logical dump 생성, 격리 복원과 manifest 대조, 비공개 GCS 업로드 성공 |

Supabase project는 재개 후 `ACTIVE_HEALTHY` 상태를 확인했다. migration 후 Security Advisor 경고는 0건이었다. Performance Advisor의 미사용 인덱스 INFO 25건은 아직 업무 데이터와 쿼리 통계가 없는 상태이므로 삭제 근거로 사용하지 않고 실제 트래픽 이후 다시 판단한다.

## 대안

- SQL Editor에서 직접 적용하면 빠르지만 승인, 대상 확인, 백업 참조와 실행 로그가 분리된다.
- V14·V15를 한 번에 적용하지 않고 각 버전 사이에 수동 점검할 수 있지만 현재 production 업무 데이터가 0건이고 개발 DB에서 두 migration의 연속 적용과 rollback을 이미 검증해 추가 중단 지점의 이익이 작다.

## 선택 이유

현재 production DB에는 사용자 데이터가 없지만 migration 자격 증명은 실제 쓰기 권한을 가진다. 따라서 프로젝트 재개만으로 정상이라고 판단하지 않고, 적용 전 복구 지점, 보호된 migration, 적용 후 읽기 전용 집계와 새 복구 지점을 한 묶음으로 남겼다.

## 남은 범위

- Supabase 조직을 실제 사용자 데이터 투입 전에 Pro로 전환하고 일일 백업·spend cap을 확인한다.
- Kakao production Secret과 첫 Cloud Run revision을 준비한 뒤 Core API smoke·rollback을 검증한다.
- production 쓰기 전환과 자동 파기 fixture는 정책·약관 승인 후 별도 게이트에서 실행한다.

이전 연결 실패 run은 project 일시 중지 기간에 발생한 연결 실패 이력으로 유지한다. 현재 V14·V15 migration과 DB 복원 차단은 해소됐지만, 전체 production 출시가 완료됐다는 뜻은 아니다.
