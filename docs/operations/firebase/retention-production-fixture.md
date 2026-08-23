# Production Firebase 자동 파기 격리 픽스처

## 목적

`bodeul-prod-110`의 Firestore 전환 문서와 Storage 원본 파기 경로를 합성 데이터로 검증한다. 일반 production 데이터와 PostgreSQL은 처리하지 않으며, 운영 자격 증명과 정책 승인이 준비되기 전에는 실행하지 않는다.

## 현재 상태

- production 전용 fixture 프로필과 수동 workflow만 저장소에 준비했다.
- `firebase-retention-production` GitHub Environment는 `master` 제한, `bodeul110` 필수 승인과 관리자 우회 금지로 생성했다. `FIREBASE_PROJECT_ID=bodeul-prod-110`도 등록했다.
- 로컬 ADC 우회를 막는 Environment 전용 실행 토큰을 secret으로 등록했다. 토큰 원문은 저장소와 로그에 남기지 않고 실행기는 SHA-256 일치만 확인한다.
- WIF provider `bodeul-retention-prod`와 전용 서비스 계정 `bodeul-retention-operator`를 생성하고 Environment 변수까지 등록했다.
- provider는 저장소 불변 ID, `master`, Environment, workflow 파일과 `workflow_dispatch` event를 모두 확인한다. 서비스 계정에는 Firestore와 두 Storage 버킷의 조회 권한만 있어 현재 `status`만 허용되고 쓰기 action은 IAM에서 실패한다.
- 2026-08-23 [workflow run 32637096512](https://github.com/bodeul110/Bodeul/actions/runs/32637096512)에서 master `0106bba8a1decea4eca836d8afa72d54bbb3c493`의 `status`를 실행했다. WIF 인증과 고정 경로 조회가 통과했고 결과는 `ABSENT`였다.
- 개인정보 처리방침·위치기반서비스 이용약관 대조가 끝나지 않았으므로 `apply`는 차단 상태다.
- production Firebase와 Storage에는 이 문서의 합성 fixture를 만들거나 변경하지 않았다.

## 판단 근거

- 선택한 방식: production 전용 marker와 allowlist를 가진 도구를 보호된 수동 workflow에서만 실행한다.
- 대안: 일반 production 파기 작업을 잠시 켜거나 개발 도구의 프로젝트 제한을 완화하는 방식은 대상 전체를 처리하거나 개발·운영 경계를 약하게 만든다.
- 선택 이유: 현재 MVP의 빈 production 환경에서도 앞으로 실제 데이터가 들어올 가능성을 전제로, 합성 fixture 외 문서를 조회·변경하는 경로를 만들지 않는 것이 안전하다.
- 리스크: Firestore IAM은 문서 단위로 제한되지 않는다. GitHub Environment 승인, WIF 조건, 전용 서비스 계정, 코드 allowlist와 경합 조건을 모두 통과해야 실행하도록 보완한다.

## 실행 경계

- Firebase 프로젝트는 `bodeul-prod-110`, 버킷은 `bodeul-prod-110.firebasestorage.app`만 허용한다.
- 개발 프로젝트와 Emulator 환경변수가 설정된 실행은 거부한다.
- fixture ID는 `issue-222-production-v1`로 고정한다.
- production 전용 문서 ID 4개, Storage 객체 경로 4개와 marker `bodeul-retention-firebase-production-v1`만 다룬다.
- 개발 fixture와 production fixture의 marker, 문서 ID와 객체 경로는 서로 다르다.
- Firestore 생성은 `create`, Storage 생성은 generation 0 조건을 사용해 기존 경로를 덮어쓰지 않는다.
- cleanup은 marker를 다시 확인하고 Firestore update time과 Storage generation 조건이 맞는 항목만 삭제한다.
- `setup`, `apply`, `cleanup`은 프로젝트와 fixture ID를 각각 두 번 확인한다.
- `setup`과 `apply`에는 검증된 복구 증적이 필요하다. 증적은 같은 시점의 Firestore export와 Storage 객체 inventory 또는 동등한 복구 기준을 가리켜야 한다.
- Firestore 증적은 `gs://bodeul-prod-110-db-backups/firestore/verified/...export_metadata`, Storage inventory는 `gs://bodeul-prod-110-db-backups/storage-inventory/verified/...json` 형식으로 분리한다. workflow는 WIF 인증 뒤 두 객체가 실제로 존재하는지 확인한다.
- `apply`에는 별도 APPLY 확인 문구와 승인된 정책 검토 증적이 추가로 필요하다.
- 정기 예약 함수의 `RETENTION_APPLY_ENABLED`와 production DB role은 변경하지 않는다.
- CLI도 `GITHUB_ACTIONS`, 저장소, `master`, commit SHA, Environment 이름과 실행 토큰을 확인하므로 권한 있는 로컬 ADC만으로는 실행할 수 없다.

일반 `retention:apply`는 모든 만료 production 후보를 처리한다. 격리 fixture 검증에는 절대 사용하지 않는다.

## GitHub Environment 준비

workflow는 `.github/workflows/firebase-retention-production.yml`과 `firebase-retention-production` Environment를 사용한다. Environment에는 `master`만 허용하고 `bodeul110` 승인을 요구하며 관리자 우회를 허용하지 않는다.

다음 Environment variable을 등록한다.

| 변수 | 값 |
| --- | --- |
| `FIREBASE_PROJECT_ID` | `bodeul-prod-110` (등록 완료) |
| `FIREBASE_RETENTION_WORKLOAD_IDENTITY_PROVIDER` | `github-actions/bodeul-retention-prod` provider의 전체 리소스명 (등록 완료) |
| `FIREBASE_RETENTION_OPERATOR_SERVICE_ACCOUNT` | `bodeul-retention-operator@bodeul-prod-110.iam.gserviceaccount.com` (등록 완료) |

Environment secret `FIREBASE_RETENTION_EXECUTION_TOKEN`은 등록 완료 상태다. 값은 운영자가 조회하거나 문서에 복사하지 않고, 교체할 때 새 난수와 저장소의 SHA-256 기준을 같은 PR에서 갱신한다.

서비스 계정 JSON key는 만들지 않는다. GCP service account ID는 30자, WIF provider ID는 32자 제한을 지키며, 실제 등록값을 workflow에서도 동일하게 확인한다. WIF provider는 다음 조건을 모두 만족하는 OIDC token만 허용한다.

- 저장소: `bodeul110/Bodeul`
- 저장소 불변 ID: `1209358990`
- ref: `refs/heads/master`
- Environment: `firebase-retention-production`
- workflow: `bodeul110/Bodeul/.github/workflows/firebase-retention-production.yml@refs/heads/master`
- event: `workflow_dispatch`

서비스 계정 impersonation은 `repo:bodeul110/Bodeul:environment:firebase-retention-production` exact subject 하나에만 `roles/iam.workloadIdentityUser`를 부여한다.

현재 전용 서비스 계정의 권한은 다음 조회 범위로 제한한다.

- Firestore 문서 읽기: 프로젝트 `roles/datastore.viewer`
- Google API 사용: 프로젝트 `roles/serviceusage.serviceUsageConsumer`
- `bodeul-prod-110.firebasestorage.app` 버킷의 객체 읽기
- `bodeul-prod-110-db-backups` 버킷의 Firestore export metadata와 Storage inventory 객체 읽기

Firestore IAM은 컬렉션이나 문서 ID 단위로 좁힐 수 없으므로, workflow 승인과 코드 allowlist를 함께 적용한다. Core API 배포 계정, migration 계정과 예약 파기 runtime 계정을 재사용하지 않는다. `setup`, `apply`, `cleanup` 전에 정책 승인과 복구 증적을 확인하고 별도 쓰기 권한 경계를 결정해야 한다. 현재 조회 전용 계정에 쓰기 역할을 추가하지 않는다.

## 실행 순서

모든 action은 GitHub Actions의 `Firebase Retention Production Fixture` workflow에서 각각 실행한다.

현재는 1번 `status`까지만 실행한다. 2번 이후 쓰기 action은 정책 승인과 별도 쓰기 권한 결정 전까지 실행하지 않는다.

1. `status`: 최초 상태가 `ABSENT`인지 확인한다.
2. `setup`: 프로젝트·fixture ID 재확인, Firestore export metadata와 Storage inventory 객체 경로를 입력한다.
3. `dry-run`: 후보와 legal hold 집계가 기대값과 같은지 확인한다.
4. `apply`: 같은 두 복구 증적을 다시 확인하고, 정책 검토 승인 후에만 APPLY 확인 문구와 정책 검토 증적을 입력한다.
5. `status`: 결과가 `APPLIED`인지 확인한다.
6. `cleanup`: 합성 문서와 객체를 정리한다.
7. `status`: 최종 상태가 `ABSENT`인지 확인한다.

모든 실행에서 현재 `master` commit SHA 40자, `bodeul-prod-110`과 `issue-222-production-v1`을 직접 입력한다. 예상 상태는 `ABSENT -> READY -> READY -> APPLIED -> APPLIED -> ABSENT -> ABSENT`다.

## 기대 결과

| 대상 | dry-run | apply 후 |
| --- | --- | --- |
| 만료 동행 세션 | 본문 1, 첨부 1, 위치 1 | 본문 비식별화, 첨부 참조와 정밀 위치 제거, 원본 삭제 |
| legal hold 동행 세션 | 제외 3 | 본문, 첨부, 위치와 원본 유지 |
| 만료 매니저 증빙 | 후보 1 | Firestore 경로 참조와 Storage 원본 제거 |
| legal hold 매니저 증빙 | 제외 1 | Firestore 참조와 Storage 원본 유지 |
| 삭제 실패 | 0 | 한 건이라도 있으면 workflow 실패 |

## 중단 기준

- 최초 `status`가 `ABSENT`가 아니면 setup하지 않는다.
- 어느 단계든 `PARTIAL`이면 apply를 다시 실행하지 않는다.
- marker, 문서 update time, Storage generation 또는 예상 집계가 다르면 자동 정리를 중단한다.
- 정책 검토 증적이 없거나 기술 보관 기간과 승인 문구가 다르면 apply하지 않는다.
- cleanup 뒤 문서나 객체가 남으면 #222에 상태와 복구 조치를 기록하고 정기 파기 배포를 진행하지 않는다.

검증 결과는 [#222 개인정보 자동 파기 구현 기록](../../reports/issue-222-data-retention-2026-07-19.md)에 남긴다.
