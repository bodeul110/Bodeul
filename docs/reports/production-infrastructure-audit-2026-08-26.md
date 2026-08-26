# Production 인프라 감사 및 운영 WIF 강화

기준일: 2026-08-26

## 작업 목적

Production 배포·DB 백업 자격 증명의 허용 범위를 현재 GitHub workflow 하나로 제한하고, 실제 Google Cloud/Firebase metadata가 저장소의 운영 기준과 일치하는지 검증한다.

## 적용 내용

| 범위 | 적용 결과 |
| --- | --- |
| Core API 배포 provider | 저장소 이름·ID, 소유자 ID, `master`, `core-api-production`, 정확한 workflow 경로, `workflow_dispatch`를 모두 요구 |
| DB 백업 provider | 저장소 이름·ID, 소유자 ID, `master`, `core-api-migration-production`, 정확한 workflow 경로, `workflow_dispatch`를 모두 요구 |
| 배포 계정 impersonation | Environment 전체 `principalSet`을 `repo:bodeul110/Bodeul:environment:core-api-production` exact subject 하나로 교체 |
| 백업 계정 impersonation | Environment 전체 `principalSet`을 `repo:bodeul110/Bodeul:environment:core-api-migration-production` exact subject 하나로 교체 |
| 사용자 관리 서비스 계정 key | 감사·배포·런타임·백업·보존 계정 모두 0개 유지 |

기존 workflow 파일과 GitHub Environment 이름은 변경하지 않았다. 따라서 정상 수동 실행의 OIDC subject는 유지하면서, 다른 workflow·event·저장소 이전으로 발급된 토큰은 provider 단계에서 거부한다.

## 적용 권한 경계

Google Cloud의 현재 사전 정의 WIF 관리자 역할과 기존 provider 갱신 API가 요구하는 권한 이름이 달라 직접 갱신이 거부됐다. 실제 오류에서 요구한 구형 provider `update` 권한 하나만 포함한 임시 custom role을 사용했다.

- custom role 생성 권한과 서비스 계정 정책 변경 권한은 만료 시간이 있는 임시 binding으로만 부여했다.
- 구형 provider `update` 권한 binding은 조건부 binding으로 유효하지 않아 적용 시간에만 무조건 binding으로 전환했다.
- 적용 직후 임시 binding 세 개가 남지 않은 것을 확인했다.
- 권한 하나짜리 임시 custom role은 삭제 상태를 확인했다.
- 관리자 역할 없이 같은 강화 스크립트를 다시 실행해 변경 없이 최종 계약 검증이 통과하는 것을 확인했다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| 감사 도구 단위 테스트 | Node 22 기준 11개 통과 |
| PowerShell 구문 | 감사 구성·운영 WIF 강화 스크립트 모두 통과 |
| GitHub Actions YAML | `yq`, `actionlint` 통과 |
| Production metadata 감사 | baseline 33개 전체 `PASS` |
| 출시 준비 상태 | PITR 완료 후 의도된 미완료 4개를 `EXPECTED_BLOCKER` 또는 `EXPECTED_ABSENT`로 분리 |
| Storage Rules | 저장소 테스트 7개와 추가 경계 검사 4개 통과, production ruleset과 저장소 hash 일치 |
| Firestore PITR | `POINT_IN_TIME_RECOVERY_ENABLED`, version 보존 `604800s` 확인 |
| 임시 권한 | 적용 후 project binding 0개, 임시 custom role 삭제 상태 |

## GitHub WIF 실행 증적

- 실행: [Production Infrastructure Audit run 32969865527](https://github.com/bodeul110/Bodeul/actions/runs/32969865527)
- 대상 commit: `51839e1d476a4a28255f062d86dbc607ceeef0d8`
- `contract`: 11초, 성공
- `baseline-drift`: 33초, 성공
- Google Cloud WIF 인증과 Production metadata 감사 단계 모두 성공

## 남은 출시 게이트

- Kakao REST API production Secret version 등록
- Core API 첫 Cloud Run production revision 승인 배포
- Android·Web App Check provider와 enforcement 실검증
- 개발 버킷 canary 후 Firebase Storage UBLA 적용

Firebase Storage Public Access Prevention은 이미 bucket 수준으로 강제했다. UBLA는 현재 조직 정책 아래 활성화하면 즉시 되돌릴 수 없으므로, 개발 버킷에서 매니저 서류·채팅 첨부·Core API·보존 정책 경로를 먼저 검증한다.

## 근거

- [GitHub Actions OIDC claim 기준](https://docs.github.com/en/actions/reference/security/oidc)
- [Google Cloud 배포 파이프라인 WIF 구성](https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
- [Google Cloud WIF provider 관리](https://docs.cloud.google.com/iam/docs/manage-workload-identity-pools-providers)
- [Production 인프라 감사 절차](../operations/production-infrastructure-audit.md)
