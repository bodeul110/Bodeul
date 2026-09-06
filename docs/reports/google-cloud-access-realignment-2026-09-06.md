# Google Cloud 접근 권한 재정비

기준일: 2026-09-06

## 작업 판단

- 작업 목적: 계정·그룹·조직 권한을 다시 대조하고, 현재 접근을 유지하면서 확인된 중복만 정리한다.
- 선택한 방식: 보들용 CLI를 재인증하고 IAM·그룹 명단을 조회한 뒤, 관리자 그룹과 동일한 직접 역할 네 건만 제거한다.
- 대안: 프로젝트 삭제·재생성, 조직 이동, 전체 IAM 덮어쓰기를 통한 초기화는 하지 않는다.
- 선택 이유: 현재 MVP 규모에서는 기존 Firebase·Cloud Run·WIF를 유지하는 편이 인증과 배포를 다시 구성하는 비용과 위험을 줄인다.
- 리스크: 그룹 가입 자체가 실효 권한 검증은 아니다. 복구 관리자 접근과 변경 전후 차이를 확인하고, 불명확한 권한은 유지한다.

## 구현한 내용

사용자가 기존 인프라를 삭제하지 않고 재정비하도록 승인했다. CLI의 `Reauthentication failed`는 사용자 재인증 후 해소됐으며, `bodeul-ops` 설정으로 개발·production 프로젝트를 다시 조회했다. 다른 계정의 인증과 Application Default Credentials는 변경하지 않았다.

기존 조직에서 주 관리자 계정에 직접 부여된 다음 네 역할은 동일한 관리자 그룹 역할과 중복이었다. Policy Troubleshooter에서 조직 IAM 변경 권한과 관리자 그룹 소속이 각각 `CAN_ACCESS`, `MEMBERSHIP_MATCHED`로 확인됐고, 별도 복구 관리자로도 두 프로젝트와 두 조직의 IAM을 조회했다.

| 제거한 직접 역할 | 유지한 권한 경로 |
| --- | --- |
| `roles/resourcemanager.organizationAdmin` | `gcp-admins@bodeul.kr` |
| `roles/billing.admin` | 같은 관리자 그룹의 조직 역할 |
| `roles/resourcemanager.projectCreator` | 같은 관리자 그룹의 조직 역할 |
| `roles/resourcemanager.projectMover` | 같은 관리자 그룹의 조직 역할 |

변경 전 정책과 변경 예정 정책을 로컬에 보관하고 기존 `etag`를 포함해 적용했다. 재조회한 정책이 네 직접 binding만 제거한 예정 정책과 정확히 일치함을 확인했다. 과거 스냅샷을 그대로 덮어쓰는 rollback은 하지 않으며, 복구가 필요하면 최신 정책과 동시 변경을 먼저 대조한다.

## 변경된 범위

- 변경 대상은 기존 조직 한 곳의 주 관리자 직접 역할 네 건뿐이다. `roles/iam.denyReviewer`와 다른 주체의 역할은 유지했다.
- 개발·production 프로젝트 IAM, 새 조직 IAM, 결제 계정 자체의 IAM과 그룹 구성원은 변경하지 않았다.
- 프로젝트·조직 이동, 계정 삭제, 서비스 계정·WIF 변경, 결제 재개·연결 변경은 하지 않았다.
- 앱·관리자 웹·Core API 코드, DB, Secret payload와 배포에는 변경이 없다.
- 원시 IAM·그룹 스냅샷은 개인정보가 포함돼 로컬에만 보관한다. 공개 문서에는 개인 이메일, 결제 식별자와 인증값을 넣지 않는다.

## 검증

| 확인 | 결과 |
| --- | --- |
| 개발·production 프로젝트 | 모두 `ACTIVE`, 기존 조직 아래 유지 |
| 관리자 그룹 | 서로 다른 소유자 두 명 유지 |
| 주 관리자 | 변경 후 기존 조직·두 프로젝트 IAM 조회 성공 |
| 복구 관리자 | 변경 전 두 조직·두 프로젝트, 변경 후 기존 조직 IAM 조회 성공 |
| 변경 정책 | 네 직접 binding 제거 외 나머지 정책 동일 |
| 프로젝트 IAM | 두 프로젝트의 변경 전후 `etag` 동일 |
| 서비스 계정 목록 | 개발 6개·production 8개 조회, 모두 활성 |
| WIF provider 목록 | 개발 2개·production 4개 조회, 모두 `ACTIVE` |

서비스 계정과 WIF는 목록·설정 조회이며, 새 배포나 토큰 교환을 실행한 검증은 아니다. 관리자 브라우저 MFA·App Check, 사용자 앱 종단 흐름, 서비스 가용성과 결제 상태는 이 권한 정리의 완료 판정에 포함하지 않는다.

## 남은 범위

- 개발자 두 명의 직접 `roles/editor`는 유지한다. 그룹 명단은 일치하지만 Troubleshooter의 그룹 경유 판정이 불명확하므로 해당 계정의 접근 검증 후 별도로 정리한다.
- 기존 조직의 다른 관리자와 결제 계정의 직접 역할은 변경하지 않았다. 소유·복구·결제 담당 경계를 확인한 뒤 판단한다.
- 공용 결제 전환은 별도 준비 후 기존 프로젝트의 연결만 변경한다. 기존 개인 결제 계정의 자동 재개나 개인 결제 수단 재등록은 하지 않는다.
- 기본 서비스 계정의 역할 축소는 Functions·빌드·예약 작업 등 실제 사용처와 필요한 권한을 검증한 뒤 진행한다.
- 조직 통합과 운영 전환은 별도 작업이다. IAM 조회 성공이나 이번 중복 제거를 전체 인프라 정상화로 기록하지 않는다.

## 관련 문서

- [Google Cloud 계정 및 IAM 운영 기준](../operations/google-cloud-access-governance.md)
- [Google Cloud IAM 상속과 그룹 권한](https://docs.cloud.google.com/iam/docs/resource-hierarchy-access-control)
- [Google Cloud 권한 오류 진단](https://docs.cloud.google.com/iam/docs/resolve-permission-errors)
