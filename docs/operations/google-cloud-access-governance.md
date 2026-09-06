# Google Cloud 계정 및 IAM 운영 기준

IAM 재확인일: 2026-09-06. Google Workspace 사용자·라이선스 항목은 별도 표시한 2026-08-23 확인 기록이다.

이 문서는 BoDeul의 Google Cloud, Firebase, Cloud Identity 접근 주체를 사람, 역할 그룹, 자동화 계정으로 분리하는 기준과 현재 전환 상태를 기록한다. 공개 사이트의 문의 주소를 개발 또는 운영 로그인으로 사용하지 않는다.

## 역할 경계

| 주체 | 기준 | 사용 범위 |
| --- | --- | --- |
| 사이트 문의 주소 | 메일 또는 공유 받은편지함 전용 | 외부 문의 수신. Google Cloud IAM 권한 없음 |
| 개인별 개발자 계정 | 사용자별 MFA와 식별 가능한 계정 | 개발 프로젝트 접근 |
| 개인별 운영자 계정 | 사용자별 MFA와 별도 복구 수단 | production 조회와 승인된 운영 작업 |
| 관리자 보안 그룹 | `gcp-admins@bodeul.kr` | 조직, 결제, 프로젝트 IAM 복구와 관리 |
| 개발자 보안 그룹 | `developers@bodeul.kr` | `bodeul-dev` 개발 접근 |
| 운영 담당자 보안 그룹 | `prod-operators@bodeul.kr` | production 로그, 모니터링, Cloud Run과 Secret 메타데이터 조회 |
| CI와 런타임 | GitHub OIDC/WIF와 용도별 서비스 계정 | 배포, 실행, 백업, 보존 작업 |

Google 계정 비밀번호를 여러 사람이 공유하지 않는다. 사람의 일상 작업은 개인별 계정으로 추적하고, GitHub Actions와 Cloud Run은 사람 계정 대신 기존 WIF와 서비스 계정을 유지한다.

한 계정이 여러 그룹에 속하면 부여된 권한이 합산된다. `prod-operators`의 읽기 전용 역할이 같은 계정의 `gcp-admins` 관리자 권한을 제한하지는 않는다. 그룹별 역할과 사용자별 실효 권한을 구분해서 점검한다.

## 확인된 현재 상태

- Cloud Identity API를 `bodeul-dev`에서 활성화했다.
- `bodeul.kr` Cloud Identity 디렉터리에 세 보안 그룹을 만들었다.
- `gcp-admins@bodeul.kr`에는 서로 다른 두 Google 계정이 `OWNER`와 `MEMBER`로 등록돼 있다.
- 관리자 그룹에는 두 Google Cloud 조직과 개발·production 프로젝트에서 기존 관리자 주체와 같은 권한을 병행 부여했다.
- `developers@bodeul.kr`에는 현재 활동 중인 개발자 계정을 등록하고 `bodeul-dev`의 `roles/editor`를 병행 부여했다.
- `prod-operators@bodeul.kr`에는 `bodeul-prod-110`의 Logging Viewer, Monitoring Viewer, Cloud Run Viewer, Secret Manager Viewer만 부여했다. Secret payload, Firestore 데이터와 Storage 객체 읽기 권한은 포함하지 않는다.
- 2026-09-06 주 관리자와 복구용 관리자에 해당하는 두 그룹 소유자 계정으로 개발·production 프로젝트와 두 조직의 IAM 조회를 각각 다시 검증했다.
- 같은 날 기존 조직에서 `scp@bodeul.kr`에 직접 부여된 관리자 역할 4건을 발견해, 동일한 `gcp-admins` 역할과 복구 계정의 접근을 확인한 뒤 제거했다. 조직 관리자, 결제 관리자, 프로젝트 생성자·이동자 역할의 그룹 binding은 유지했다.
- 현재 두 프로젝트와 두 조직에서 해당 계정의 직접 관리자 역할 중복은 없다. 기존 조직의 별도 조회 역할 `roles/iam.denyReviewer`는 유지했다. 이 결과를 결제 계정 자체의 IAM이나 다른 사용자 권한까지 모두 정리됐다는 뜻으로 확대하지 않는다.
- 팀에서 제외된 이전 개발자 계정의 `bodeul-dev` 직접 `roles/editor` binding을 제거했다.
- 현재 활동 중인 개발자 두 명의 기존 `bodeul-dev` 직접 `roles/editor` binding은 유지했다. 그룹 명단은 일치하지만 Policy Troubleshooter의 해당 그룹 판정이 `MEMBERSHIP_UNKNOWN_INFO`이므로, 직접 권한이 포함된 `CAN_ACCESS` 결과만으로 그룹 경유 검증이 끝났다고 판단하지 않는다.
- 저장소의 로컬 Git 작성자 정보는 공용 Gmail이 아니라 GitHub `bodeul110`의 비공개 noreply 주소를 사용한다.

세부 변경과 검증 범위는 [2026-09-06 재정비 기록](../reports/google-cloud-access-realignment-2026-09-06.md)을 따른다.

### Google Workspace 확인 결과 (2026-08-23)

아래 항목은 당시 Google Admin Console에서 확인한 기록이며, 이번 IAM 재정비에서는 사용자·별칭·라이선스를 다시 조회하거나 변경하지 않았다.

- Google Admin Console에는 관리 사용자 `scp@bodeul.kr` 한 명과 Google Workspace Business Standard 라이선스 한 개만 있다.
- `scp@bodeul.kr`에는 보조 이메일 주소와 이메일 별칭이 등록돼 있지 않다.
- 관리 도메인은 기본 도메인 `bodeul.kr`, 시스템 게스트 도메인, 가입 시 생성된 테스트 도메인 별칭뿐이다.
- `bodeul.kr`로 이전할 비관리 Google 계정은 0건이다.
- `bodeul.official@gmail.com`은 두 프로젝트, 두 조직과 세 역할 그룹에서 IAM 주체로 사용되지 않는다. 따라서 이 주소는 사이트용 외부 계정으로 유지하고 Google Cloud 권한을 부여하지 않는다.

## 조직 경계

Google Cloud에는 `bodeul326-org`와 `bodeul.kr` 두 조직이 보인다. 현재 `bodeul-dev`와 `bodeul-prod-110`의 parent는 모두 기존 `bodeul326-org`이며, 새 `bodeul.kr` 조직으로 이동하지 않았다.

프로젝트 이동은 IAM과 조직 정책 상속, 결제, WIF, 서비스 계정과 배포 검증을 포함하는 별도 변경이다. 계정 정리와 동시에 실행하지 않으며 다음 조건을 모두 확인한 뒤 수행한다.

1. 출발지와 도착지 조직의 관리자 및 Project Mover 권한 확인
2. 두 조직의 IAM과 Organization Policy 차이 대조
3. 결제 계정 연결과 budget 알림 영향 확인
4. WIF provider, 서비스 계정, Cloud Run, Firebase와 Secret Manager 목록 저장
5. 이동 후 배포, Firebase Auth, 백업과 rollback smoke test 준비

## 계정 전환 절차

1. 새 개인별 계정을 그룹에 추가한다.
2. MFA, 복구 이메일과 복구 코드를 설정한다.
3. 새 계정으로 Google Cloud Console, Firebase Console과 필요한 CLI 조회를 검증한다.
4. production 쓰기 권한은 필요한 역할만 별도로 승인한다.
5. 감사 로그에서 새 계정 또는 그룹 경유 접근을 확인한다.
6. 검증이 끝난 뒤에만 기존 직접 IAM binding을 제거한다. 2026-08-23 최초 전환 이후에도 변경이 생길 수 있으므로, 실제 IAM과 그룹 구성원을 다시 대조한다.

계정 삭제, 이메일 별칭 제거와 프로젝트 조직 이동을 먼저 실행하지 않는다. Google Admin Console 검증 결과 `bodeul.official@gmail.com`과 `scp@bodeul.kr` 사이의 Workspace 별칭이나 비관리 계정 충돌은 없다. 로그인 시 `scp@bodeul.kr`로 전환되는 현상은 두 계정을 합친 서버 설정이 아니라 브라우저 프로필과 Google 계정 선택 세션을 분리해 확인한다.

## 공용 결제 전환 경계

공용 계정으로 로그인하는 것과 Cloud Billing 계정·결제 수단을 이전하는 것은 별개다. 이번 접근 권한 정리를 이유로 기존 개인 결제 계정을 자동 재개하거나 개인 결제 수단을 다시 등록하지 않는다.

팀의 청구·납부 주체와 승인된 결제 수단을 준비한 뒤 공용 관리 체계의 결제 계정을 별도로 구성한다. 관리자·복구 담당자의 접근을 확인하고, 기존 `bodeul-dev`와 `bodeul-prod-110`의 연결만 변경한다. 프로젝트 자체를 다시 만들거나 이동하는 작업과 묶지 않는다.

전환 완료는 결제 계정의 `open=true`, 각 프로젝트의 예상 결제 계정 연결과 `billingEnabled=true`를 확인한 뒤 판단한다. 그 후 필요한 개발 배포와 서비스 접근을 별도로 검증한다. 결제 등록·약관·카드 입력은 담당자가 직접 완료하며, 결제 계정과 Google Payments 권한을 동일한 것으로 취급하지 않는다.

## 점검 명령

현재 로컬 보들 설정 이름은 `bodeul-ops`다. 기본 계정이나 기본 프로젝트를 추정하지 않고 설정 이름과 대상 프로젝트를 명시한다. 복구 관리자 점검은 `--account=<복구 관리자 계정>`을 추가해 수행하며, 활성 계정이나 다른 프로젝트의 인증을 교체하지 않는다.

```powershell
gcloud config configurations describe bodeul-ops
gcloud identity groups memberships list --group-email=gcp-admins@bodeul.kr --view=full --configuration=bodeul-ops
gcloud identity groups memberships list --group-email=developers@bodeul.kr --view=full --configuration=bodeul-ops
gcloud projects get-iam-policy bodeul-dev --configuration=bodeul-ops
gcloud projects get-iam-policy bodeul-prod-110 --configuration=bodeul-ops
gcloud organizations list --configuration=bodeul-ops
```

`Reauthentication failed`는 먼저 해당 계정의 재인증으로 확인한다. 이 오류만으로 IAM 손상이나 프로젝트 재생성 필요성을 판단하지 않는다. `gcloud auth login`은 기존 보들 설정과 명시한 계정으로 실행하고, `--update-adc`를 추가하거나 다른 계정의 인증을 일괄 폐기하지 않는다. 브라우저 자동 실행이 불가능하면 공식 `--no-launch-browser` 흐름을 사용하며 인증 코드는 해당 터미널에만 입력한다.

명령 결과를 공개 문서나 Issue에 붙일 때는 개인 이메일과 credential 정보를 제거한다. IAM 조회 성공은 결제 활성 상태, 앱 로그인, 배포와 서비스 가용성의 검증을 대신하지 않는다.

## 근거

- [Google Cloud 계정 및 조직 계획 권장사항](https://cloud.google.com/architecture/identity/best-practices-for-planning)
- [Google Workspace 관리자 계정 보안 권장사항](https://knowledge.workspace.google.com/admin/users/security-best-practices-for-administrator-accounts)
- [Google Cloud 서비스 계정 보안 권장사항](https://cloud.google.com/iam/docs/best-practices-service-accounts)
- [Google Cloud 프로젝트 조직 간 이동](https://cloud.google.com/resource-manager/docs/project-migration)
- [Cloud Billing과 Google Payments 접근 권한](https://docs.cloud.google.com/billing/docs/how-to/billing-access)
- [기존 프로젝트의 Cloud Billing 계정 변경](https://docs.cloud.google.com/billing/docs/how-to/modify-project)
