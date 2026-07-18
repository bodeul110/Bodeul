# Production 인프라 최초 구축 기록

기준일: 2026-07-17

## 작업 목적

개발 자격 증명을 재사용하지 않고 Google Cloud/Firebase, Supabase PostgreSQL과 GitHub 배포 경계를 production 전용으로 분리한다.

## 선택한 방식

- Google Cloud와 Firebase는 `bodeul-prod-110` 하나의 프로젝트에서 운영한다.
- Core API는 Tokyo Cloud Run, 공용 DB는 Tokyo Supabase PostgreSQL을 사용한다.
- GitHub Actions는 서비스 계정 key 없이 저장소·`master`·Environment로 제한한 WIF를 사용한다.
- DB migration은 runtime과 분리한 GitHub Environment에서 사전 dump를 확인한 뒤 실행한다.

## 구축 결과

| 범위 | 결과 |
| --- | --- |
| Google Cloud | `bodeul-prod-110`, project number `649312328770`, 결제 연결 |
| 비용 | `BoDeul production monthly budget`, 월 30,000 KRW, 50%·80%·100% 알림 |
| Firebase | 기존 Google Cloud 프로젝트에 Firebase 활성화 |
| Firestore | `(default)`, `asia-northeast1`, Native mode, 삭제 방지 활성화, PITR 미활성 |
| Storage | `bodeul-prod-110.firebasestorage.app`, `ASIA-NORTHEAST1` |
| Auth | Email/Password와 improved email privacy 활성화 |
| Firebase 앱 | Android `com.example.bodeul`, 관리자 Web 앱 등록 |
| Rules | 현재 저장소의 Firestore·Storage Rules를 production release로 배포 |
| Artifact Registry | `asia-northeast1/bodeul-core-api` Docker repository |
| 서비스 계정 | `bodeul-core-deployer`, `bodeul-core-runtime`, `bodeul-db-backup` |
| WIF | `github-actions/bodeul-core-api-production`, `github-actions/bodeul-db-backup-production` |
| Supabase | `bodeul-prod`, ref `aoijbzgozbopsxzrasbb`, `ap-northeast-1`, PostgreSQL 17 |

WIF provider 조건은 `bodeul110/Bodeul`, `refs/heads/master`, `core-api-production`을 모두 요구한다. 배포 계정에는 Cloud Run 배포, 해당 Artifact Registry 쓰기와 runtime 서비스 계정 사용 권한만 부여했다. 사용자 관리 서비스 계정 key는 만들지 않았다.

DB 백업 provider는 같은 저장소와 `master`에 더해 `core-api-migration-production` Environment를 요구한다. `bodeul-db-backup`에는 production DB backup bucket의 object 생성·조회 권한만 부여했으며 삭제 권한과 서비스 계정 key는 부여하지 않았다.

## DB migration과 검증

1. `database_access_foundation`, `database_access_hardening` bootstrap을 적용했다.
2. `bodeul_migrator`와 `bodeul_core_service`만 production 로그인 role로 활성화했다. `bodeul_admin_service`는 Vercel Production 연결 전까지 `NOLOGIN`이다.
3. Flyway 전 `bodeul` schema dump를 비공개 GCS bucket에 저장했다.
   - object: `gs://bodeul-prod-110-db-backups/pre-migration/20260717T094147Z-pre-flyway-schema.dump`
   - SHA-256: `d4dfa0f251fdf9ff595254071062f7ce130d75513f8ca3bdcb0e61b79e76af21`
   - bucket 정책: 공개 접근 차단, uniform access, 28일 retention, 7일 soft delete
4. [Core API DB Migration run 29570950189](https://github.com/bodeul110/Bodeul/actions/runs/29570950189)에서 보호 승인 뒤 Flyway V1~V3를 실행했다.

| 검증 | 결과 |
| --- | --- |
| Flyway | V1·V2·V3 성공, `installed_by=bodeul_migration` |
| 테이블 owner | `app_users`, `hospital_guides`, `appointment_requests`, history 모두 `bodeul_migration` |
| RLS | 업무 테이블 3개 활성화 |
| 정책 | 6개 |
| 공개 role table grant | 0건 |
| 초기 row | 세 업무 테이블 모두 0건 |
| Supabase Security Advisor | lint 0건 |
| Performance Advisor | 빈 DB에서 예상되는 unused index 정보만 존재 |

## GitHub Environment

`core-api-production`에는 production project, region, Artifact Registry, WIF, 서비스 계정, Firebase 식별자, App Check `observe`와 DB Secret Manager version `1`을 등록했다. Kakao production secret version은 아직 없으므로 배포 workflow는 인증 전에 실패한다.

`core-api-migration-production`에는 production migration 전용 JDBC URL, 사용자명과 비밀번호를 등록했다. 원문은 저장소, 문서와 Actions 출력에 남기지 않았다.

같은 Environment에는 production project, backup bucket, DB 백업 WIF provider와 서비스 계정을 변수로 등록했다. `.github/workflows/postgres-production-backup-restore.yml`은 owner와 ACL을 포함한 dump를 격리 PostgreSQL에 복원하고 manifest가 일치한 경우에만 외부 bucket에 업로드한다.

## 남은 출시 게이트

- Kakao production REST key를 별도 secret version으로 등록한다.
- Cloud Run 첫 production revision을 배포하고 public invoker, health 200, 무인증 401과 rollback을 검증한다.
- Vercel Production에 production Firebase 공개 설정과 SELECT-only 관리자 DB URL을 등록하고 관리자 role을 활성화한다.
- Google 로그인용 release SHA-256·OAuth 설정, App Check Play Integrity·Web provider와 authorized domain을 구성한다.
- Supabase를 실제 사용자 데이터 수용 전에 일일 backup이 있는 등급 또는 동등한 보호 수준으로 전환하고 restore를 리허설한다.
- 기준 도메인, 실명 운영자 2명, 출시 일정과 장애·rollback 담당자를 정한다.

## 리스크

- 예산 알림은 지출을 자동 차단하지 않는다.
- Firestore PITR은 비용과 보존 기준을 확정한 뒤 출시 전에 활성화한다.
- 빈 DB에서 unused index 경고는 사용량 근거가 없으므로 지금 제거하지 않는다.
- Firebase Rules release는 배포했지만 production 앱의 실제 인증·App Check 흐름은 아직 검증하지 않았다.

## 근거

- [기존 Google Cloud 프로젝트에 Firebase 추가](https://firebase.google.com/docs/projects/use-firebase-with-existing-cloud-project)
- [Google Cloud budget](https://docs.cloud.google.com/billing/docs/how-to/budgets)
- [Firebase 기본 Storage bucket 생성](https://firebase.google.com/docs/reference/rest/storage/rest/v1alpha/projects.defaultBucket/create)
- [Firebase Security Rules 관리와 배포](https://firebase.google.com/docs/rules/manage-deploy)
