# Google Cloud 계정 및 IAM 운영 기준

기준일: 2026-08-23

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

## 확인된 현재 상태

- Cloud Identity API를 `bodeul-dev`에서 활성화했다.
- `bodeul.kr` Cloud Identity 디렉터리에 세 보안 그룹을 만들었다.
- `gcp-admins@bodeul.kr`에는 서로 다른 두 Google 계정이 `OWNER`와 `MEMBER`로 등록돼 있다.
- 관리자 그룹에는 두 Google Cloud 조직과 개발·production 프로젝트에서 기존 관리자 주체와 같은 권한을 병행 부여했다.
- `developers@bodeul.kr`에는 현재 활동 중인 개발자 계정을 등록하고 `bodeul-dev`의 `roles/editor`를 병행 부여했다.
- `prod-operators@bodeul.kr`에는 `bodeul-prod-110`의 Logging Viewer, Monitoring Viewer, Cloud Run Viewer, Secret Manager Viewer만 부여했다. Secret payload, Firestore 데이터와 Storage 객체 읽기 권한은 포함하지 않는다.
- 관리자 그룹의 두 소유자 계정으로 개발·production 프로젝트와 두 조직의 IAM 조회를 검증했다.
- 검증 뒤 `scp@bodeul.kr`의 중복 직접 관리자 IAM binding을 제거했으며, 관리자 권한은 `gcp-admins@bodeul.kr`로만 부여한다.
- 개발자 개인 계정의 기존 `bodeul-dev` 직접 binding은 각 계정의 그룹 경유 접근을 확인하기 전까지 유지한다.
- 저장소의 로컬 Git 작성자 정보는 공용 Gmail이 아니라 GitHub `bodeul110`의 비공개 noreply 주소를 사용한다.

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
6. 검증이 끝난 뒤에만 기존 직접 IAM binding을 제거한다. 관리자 binding은 2026-08-23에 이 절차를 완료했다.

계정 삭제, 이메일 별칭 제거와 프로젝트 조직 이동을 먼저 실행하지 않는다. `bodeul.official@gmail.com` 로그인 시 다른 Workspace 계정으로 귀결되는 원인은 Google Admin Console의 사용자 별칭, 도메인, 미관리 계정 이전 상태를 확인하기 전까지 확정하지 않는다.

## 점검 명령

```powershell
gcloud identity groups memberships list --group-email=gcp-admins@bodeul.kr --view=full
gcloud identity groups memberships list --group-email=developers@bodeul.kr --view=full
gcloud projects get-iam-policy bodeul-dev
gcloud projects get-iam-policy bodeul-prod-110
gcloud organizations list
```

명령 결과를 공개 문서나 Issue에 붙일 때는 개인 이메일과 credential 정보를 제거한다.

## 근거

- [Google Cloud 계정 및 조직 계획 권장사항](https://cloud.google.com/architecture/identity/best-practices-for-planning)
- [Google Workspace 관리자 계정 보안 권장사항](https://knowledge.workspace.google.com/admin/users/security-best-practices-for-administrator-accounts)
- [Google Cloud 서비스 계정 보안 권장사항](https://cloud.google.com/iam/docs/best-practices-service-accounts)
- [Google Cloud 프로젝트 조직 간 이동](https://cloud.google.com/resource-manager/docs/project-migration)
