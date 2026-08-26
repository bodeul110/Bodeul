# Production 인프라 감사 및 운영 WIF 강화

기준일: 2026-08-26

## 작업 목적

Production 배포·DB 백업 자격 증명의 허용 범위를 현재 GitHub workflow 하나로 제한하고, 실제 Google Cloud/Firebase와 App Check metadata가 저장소의 운영 기준과 일치하는지 검증한다.

## 적용 내용

| 범위 | 적용 결과 |
| --- | --- |
| Core API 배포 provider | 저장소 이름·ID, 소유자 ID, `master`, `core-api-production`, 정확한 workflow 경로, `workflow_dispatch`를 모두 요구 |
| DB 백업 provider | 저장소 이름·ID, 소유자 ID, `master`, `core-api-migration-production`, 정확한 workflow 경로, `workflow_dispatch`를 모두 요구 |
| 배포 계정 impersonation | Environment 전체 `principalSet`을 `repo:bodeul110/Bodeul:environment:core-api-production` exact subject 하나로 교체 |
| 백업 계정 impersonation | Environment 전체 `principalSet`을 `repo:bodeul110/Bodeul:environment:core-api-migration-production` exact subject 하나로 교체 |
| 사용자 관리 서비스 계정 key | 감사·배포·런타임·백업·보존 계정 모두 0개 유지 |
| Web App Check 기반 | canonical production hostname만 허용한 reCAPTCHA Enterprise `SCORE` key, App Check 설정과 exact Auth domain 구성 |
| App Check 단계 | Web 기반 설정 완료, Android release·클라이언트 token 증거 대기 상태인 `preparing`; enforcement는 계속 `OFF` |

기존 workflow 파일과 GitHub Environment 이름은 변경하지 않았다. 따라서 정상 수동 실행의 OIDC subject는 유지하면서, 다른 workflow·event·저장소 이전으로 발급된 토큰은 provider 단계에서 거부한다.

## 적용 권한 경계

Google Cloud의 현재 사전 정의 WIF 관리자 역할과 기존 provider 갱신 API가 요구하는 권한 이름이 달라 직접 갱신이 거부됐다. 실제 오류에서 요구한 구형 provider `update` 권한 하나만 포함한 임시 custom role을 사용했다.

- custom role 생성 권한과 서비스 계정 정책 변경 권한은 만료 시간이 있는 임시 binding으로만 부여했다.
- 구형 provider `update` 권한 binding은 조건부 binding으로 유효하지 않아 적용 시간에만 무조건 binding으로 전환했다.
- 적용 직후 임시 binding 세 개가 남지 않은 것을 확인했다.
- 권한 하나짜리 임시 custom role은 삭제 상태를 확인했다.
- 관리자 역할 없이 같은 강화 스크립트를 다시 실행해 변경 없이 최종 계약 검증이 통과하는 것을 확인했다.
- App Check 감사 권한 확장에는 만료 조건을 둔 `roles/iam.roleAdmin` binding 하나만 사용했고 적용 직후 제거했다. 제거 후 개인 직접 binding과 해당 임시 조건 binding은 모두 0개였다.
- 관리자 역할 없이 감사 구성 스크립트를 다시 실행해 App Check 조회 권한을 포함한 exact custom role 계약이 유지되는 것을 확인했다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| 감사 도구 단위 테스트 | Node 22 기준 24개 통과 |
| PowerShell 구문 | 감사 구성·운영 WIF 강화·Firestore PITR·Web App Check 스크립트 모두 통과 |
| GitHub Actions YAML | `yq`, `actionlint` 통과 |
| Production metadata 감사 | baseline 36개 전체 `PASS` |
| 출시 준비 상태 | PITR 완료 후 의도된 미완료 5개를 `EXPECTED_BLOCKER` 또는 `EXPECTED_ABSENT`로 분리 |
| Storage Rules | 저장소 테스트 7개와 추가 경계 검사 4개 통과, production ruleset과 저장소 hash 일치 |
| Firestore PITR | `POINT_IN_TIME_RECOVERY_ENABLED`, version 보존 `604800s` 확인 |
| Web App Check | production debug token 0개, 제한된 key·Auth domain·TTL·기본 위험 점수 일치, 통합 단계 `preparing` |
| 임시 권한 | 적용 후 개인 직접 project binding 0개, App Check 역할 갱신용 임시 binding 0개, 기존 임시 custom role 삭제 상태 |

## GitHub WIF 실행 증적

| 실행 | 대상 commit | 결과 |
| --- | --- | --- |
| [운영 WIF 강화 검증 run 32969865527](https://github.com/bodeul110/Bodeul/actions/runs/32969865527) | `51839e1d476a4a28255f062d86dbc607ceeef0d8` | contract 11초, baseline 33초, 성공 |
| [Firestore PITR 반영 감사 run 32971183897](https://github.com/bodeul110/Bodeul/actions/runs/32971183897) | `e9c55cac63e99416719bb3d12de64d45f72658b2` | contract 9초, baseline 33초, 성공 |
| [App Check preparing 반영 감사 run 32978549310](https://github.com/bodeul110/Bodeul/actions/runs/32978549310) | `656e512f3951bd9a715b5b8227486a57c7f360ed` | contract 9초, baseline 38초, 성공 |

세 실행 모두 Google Cloud WIF 인증과 Production metadata 감사 단계를 통과했다.

## Production DB 복구와 최신화

- 자동 일시 중지됐던 Supabase project를 재개하고 `ACTIVE_HEALTHY` 상태를 확인했다.
- [readiness run 32980526711](https://github.com/bodeul110/Bodeul/actions/runs/32980526711)에서 Flyway V13과 V14 backfill 후보 0건을 확인했다.
- [migration run 32981200371](https://github.com/bodeul110/Bodeul/actions/runs/32981200371)에서 Flyway V14·V15와 계정 삭제 영향도 DB 계약 검증을 통과했다.
- [migration 후 readiness run 32981484159](https://github.com/bodeul110/Bodeul/actions/runs/32981484159)에서 Flyway V15와 실패 이력 0건을 확인했다.
- migration 전후 [backup·restore run 32980749558](https://github.com/bodeul110/Bodeul/actions/runs/32980749558), [run 32981633994](https://github.com/bodeul110/Bodeul/actions/runs/32981633994)가 모두 격리 복원과 외부 보관을 통과했다.
- migration 후 Supabase Security Advisor 경고는 0건이다. 미사용 인덱스 INFO는 업무 데이터와 쿼리 통계가 쌓인 뒤 재평가한다.

## 남은 출시 게이트

- Kakao REST API production Secret version 등록
- Core API 첫 Cloud Run production revision 승인 배포
- Android release SHA-256·Play 연결과 관리자 웹 클라이언트 token 전송 검증
- Android·Web `ALLOW`·`VALID` 요청 확인 후 App Check 관찰·단계별 enforcement 검증
- 개발 버킷 canary 후 Firebase Storage UBLA 적용

Firebase Storage Public Access Prevention은 이미 bucket 수준으로 강제했다. UBLA는 현재 조직 정책 아래 활성화하면 즉시 되돌릴 수 없으므로, 개발 버킷에서 매니저 서류·채팅 첨부·Core API·보존 정책 경로를 먼저 검증한다.

## 근거

- [GitHub Actions OIDC claim 기준](https://docs.github.com/en/actions/reference/security/oidc)
- [Google Cloud 배포 파이프라인 WIF 구성](https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
- [Google Cloud WIF provider 관리](https://docs.cloud.google.com/iam/docs/manage-workload-identity-pools-providers)
- [Production 인프라 감사 절차](../operations/production-infrastructure-audit.md)
