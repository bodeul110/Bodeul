# Spring Core API Cloud Run 인프라 런북

기준일: 2026-07-16

이 문서는 `core-api/`를 Google Cloud Run에 배포하고 Supabase PostgreSQL, Firebase Auth, Kakao 서버 API를 연결하는 개발 환경 기준을 정한다. 실제 secret 값은 저장소와 공개 GitHub 대화에 남기지 않는다.

## 결정

- 관리자 브라우저는 Vercel의 Next.js 관리자 서버를 사용한다.
- 환자·보호자·매니저 웹과 Android 앱은 Spring Core API를 사용한다.
- 두 서버는 서로를 경유하지 않고 같은 Supabase PostgreSQL에 서로 다른 runtime role로 접근한다.
- OCI Free Tier 계정 잠금으로 중단된 Spring preview는 Cloud Run으로 교체한다.
- Cloudflare는 도메인이 생긴 뒤 DNS, WAF, DDoS 방어 계층으로 검토하며 Spring 실행 환경으로 사용하지 않는다.

Cloud Run은 현재 Spring 애플리케이션을 컨테이너로 유지하고, 요청이 없을 때 인스턴스를 0으로 줄일 수 있다. Firebase와 같은 Google Cloud 프로젝트의 서비스 계정 ADC를 사용할 수 있어 장기 서비스 계정 JSON 파일도 필요하지 않다. 단점은 첫 요청의 cold start와 결제 계정 등록이 필요하다는 점이다.

## 현재 상태

- Java 21, Spring Boot 3.5.16, `/health`, `preview` DB profile이 구현돼 있다.
- Firebase ID token 검증과 PostgreSQL `app_users.role` 인가가 구현돼 있다.
- 개발 DB의 migration, RLS, Core/Admin runtime 권한 검증이 완료됐다.
- `core-api/Dockerfile`과 `Core API Preview Deploy` workflow를 배포 기준으로 사용한다.
- Cloud Run `bodeul-core-api-preview` 배포, 외부 smoke test와 revision rollback 리허설이 완료됐다.
- 실제 Firebase ID token과 PostgreSQL role 연결은 Issue #157에서 검증했다.
- Kakao Local REST Secret 버전 `1`과 인증된 장소 검색 실호출은 Issue #158 검증 기록에서 확인했다.
- Android App Check header 전달과 Spring `off/observe/enforce` 검증을 구현했다. preview는 실제 `valid` 관측 전까지 `observe`로만 운용한다.
- production 프로젝트, 서비스, secret은 만들지 않는다.

실제 revision, image digest, 응답과 로그 검사 결과는 [Issue 156 Cloud Run preview 검증 기록](../reports/issue-156-core-api-cloud-run-preview-2026-07-16.md)에 정리한다.

## 리소스 이름

| 항목 | 개발 기준 |
| --- | --- |
| Google Cloud/Firebase project | `bodeul-dev` |
| 리전 | `asia-northeast1` (Tokyo) |
| Cloud Run 서비스 | `bodeul-core-api-preview` |
| Artifact Registry | `bodeul-core-api` |
| 컨테이너 이미지 | `bodeul-core-api` |
| 배포 서비스 계정 | `bodeul-core-preview-deployer` |
| 런타임 서비스 계정 | `bodeul-core-preview-runtime` |
| GitHub Environment | `core-api-preview` |
| WIF pool/provider | 기존 pool `github-actions`, 전용 provider `bodeul-core-api-preview` |

Supabase 개발 DB도 Tokyo이므로 Core API를 Tokyo에 둔다. production 리전은 실제 사용자 분포와 운영 DB 리전을 확인한 뒤 별도로 결정한다.

## 런타임 기준

| 항목 | 값 | 이유 |
| --- | --- | --- |
| CPU | 1 vCPU | 단일 Spring API 초기 기준 |
| Memory | 1 GiB | Spring, Firebase Admin, JDBC의 512 MiB OOM 위험 완화 |
| 최소 인스턴스 | 0 | 개발 환경 유휴 비용 제한 |
| 최대 인스턴스 | 1 | 비용과 DB 연결 수 상한 고정 |
| Concurrency | 20 | 초기 저부하 검증 기준 |
| DB pool | 최대 5 | Admin, migration, Supabase 관리 연결 여유 확보 |
| Request timeout | 30초 | 외부 API 지연 무제한 대기 방지 |
| Port | Cloud Run `PORT`, 기본 8080 | 플랫폼 계약 준수 |
| 실행 사용자 | distroless `nonroot` | 컨테이너 root 실행 방지 |

컨테이너 파일 시스템은 영속 저장소로 사용하지 않는다. 파일 원본은 Firebase Storage에, 운영 데이터는 PostgreSQL에 둔다.

## Supabase 연결

Cloud Run에서 사용하는 값은 다음 세 개다.

- `CORE_DB_JDBC_URL`
- `CORE_DB_USERNAME`
- `CORE_DB_PASSWORD`

Cloud Run의 외부 PostgreSQL 연결은 IPv4가 가능한 Supavisor session mode 5432를 우선 사용한다. `bodeul_core_service` 로그인과 `bodeul_core_runtime` 권한 경계를 유지하고, migration 계정은 런타임에 주입하지 않는다.

DB role은 다음처럼 분리한다.

| role | 용도 | 연결 상한 |
| --- | --- | ---: |
| `bodeul_migration` / `bodeul_migrator` | Flyway와 schema 변경 | 2 |
| `bodeul_core_runtime` / `bodeul_core_service` | 사용자 서비스 | 5 |
| `bodeul_admin_runtime` / `bodeul_admin_service` | Next.js 관리자 서버 | 5 |

## Firebase 인증과 App Check

1. 클라이언트가 Firebase Auth로 로그인한다.
2. Firebase ID token을 `Authorization: Bearer`로 Core API에 보낸다.
3. Cloud Run runtime 서비스 계정의 ADC로 Firebase Admin SDK를 초기화한다.
4. 검증된 Firebase UID를 `bodeul.app_users.firebase_uid`와 연결한다.
5. PostgreSQL role과 resource ownership으로 최종 권한을 판정한다.

Cloud Run은 Firebase project와 같은 `bodeul-dev`에서 실행한다. 서비스 계정 JSON, `GOOGLE_APPLICATION_CREDENTIALS`, Firebase Admin private key를 만들거나 배포하지 않는다. `FIREBASE_PROJECT_ID=bodeul-dev`를 명시해 다른 project token을 거부한다.

App Check는 Firebase Auth와 PostgreSQL role 인가를 대체하지 않는 별도 앱 무결성 신호다.

1. Android가 App Check token을 발급받아 `X-Firebase-AppCheck` 헤더로 보낸다.
2. Core API는 Firebase App Check JWKS로 RS256 서명을 검증한다.
3. `FIREBASE_PROJECT_NUMBER`로 issuer와 audience를 고정하고 `typ=JWT`, 만료, app ID를 확인한다.
4. `off`는 검증하지 않고, `observe`는 판정만 기록하며, `enforce`는 누락·위조 요청을 거부한다.

Cloud Run preview에는 `BODEUL_APP_CHECK_MODE=observe`를 고정한다. `enforce`는 Issue #190에서 Android debug/Play Integrity token의 `valid`를 확인하고 Issue #192에서 롤백까지 재현한 뒤 적용한다. Java Admin SDK 9.10.0은 App Check 검증 API를 제공하지 않으므로 Spring Security의 JWT/JWKS 검증기를 사용한다.

2026-07-17 [Core API Preview Deploy #29518038972](https://github.com/bodeul110/Bodeul/actions/runs/29518038972)로 commit `000afc350fa3654cb97c9d23a539e45322322e95`를 배포했다. `asia-northeast1`의 리비전 `bodeul-core-api-preview-00007-8hk`가 트래픽 100%를 처리하며 `FIREBASE_PROJECT_ID=bodeul-dev`, `FIREBASE_PROJECT_NUMBER=533563500316`, `BODEUL_APP_CHECK_MODE=observe`를 사용한다. health 200과 무인증 auth/place search 401 smoke test는 통과했다. 배포 직후에는 인증된 요청이 없어 `app_check_verdict` 실제 로그가 없었으며, 정상 token 관측 전에는 enforce로 바꾸지 않는다.

## Secret Manager

preview에서 다음 secret ID를 사용한다.

| Secret Manager ID | Cloud Run 환경변수 |
| --- | --- |
| `bodeul-core-api-preview-db-jdbc-url` | `CORE_DB_JDBC_URL` |
| `bodeul-core-api-preview-db-username` | `CORE_DB_USERNAME` |
| `bodeul-core-api-preview-db-password` | `CORE_DB_PASSWORD` |
| `bodeul-core-api-preview-kakao-local-rest-api-key` | `KAKAO_LOCAL_REST_API_KEY` |

런타임 서비스 계정에 각 secret의 `roles/secretmanager.secretAccessor`만 부여한다. 배포 workflow와 애플리케이션 로그에는 secret 원문을 출력하지 않는다.

Cloud Run 환경변수는 `latest` 대신 숫자 version을 참조한다. 회전할 때 새 version을 등록하고 해당 GitHub Environment의 version 변수만 바꾼 뒤 재배포한다.

2026-07-16 실제 Firebase ID token과 PostgreSQL role 조회를 확인한 뒤 기존 GitHub Environment의 `CORE_DB_*` secret을 삭제했다. Secret Manager가 runtime source of truth이며 GitHub Environment에는 숫자 secret version 변수만 둔다.

같은 날 Kakao Local REST 키를 `bodeul-core-api-preview-kakao-local-rest-api-key` 버전 `1`로 등록하고 런타임 서비스 계정에 accessor 권한만 부여했다. Cloud Run 리비전 `bodeul-core-api-preview-00006-hdk`가 이 버전을 참조한다.

## Google Cloud 최초 설정

결제 계정 연결과 Cloud Run API 사용 가능 여부는 Google Cloud Console에서 먼저 확인한다. 이후 프로젝트 소유자 권한이 있는 로컬 `gcloud` 또는 Cloud Shell에서 아래 순서로 설정한다.

```powershell
$ProjectId = "bodeul-dev"
$Region = "asia-northeast1"
$Repository = "bodeul-core-api"
$DeployAccount = "bodeul-core-preview-deployer@$ProjectId.iam.gserviceaccount.com"
$RuntimeAccount = "bodeul-core-preview-runtime@$ProjectId.iam.gserviceaccount.com"

gcloud config set project $ProjectId
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com

gcloud artifacts repositories create $Repository `
  --repository-format=docker `
  --location=$Region `
  --description="BoDeul Core API images"

gcloud iam service-accounts create bodeul-core-preview-deployer `
  --display-name="BoDeul Core API preview deployer"
gcloud iam service-accounts create bodeul-core-preview-runtime `
  --display-name="BoDeul Core API preview runtime"

gcloud iam workload-identity-pools providers create-oidc bodeul-core-api-preview `
  --workload-identity-pool=github-actions `
  --location=global `
  --issuer-uri="https://token.actions.githubusercontent.com" `
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner,attribute.ref=assertion.ref,attribute.environment=assertion.environment,attribute.actor=assertion.actor,attribute.workflow=assertion.workflow" `
  --attribute-condition="assertion.repository == 'bodeul110/Bodeul' && assertion.ref == 'refs/heads/master' && assertion.environment == 'core-api-preview'"
```

이미 존재하는 리소스의 create 명령은 다시 실행하지 않는다. `describe` 또는 Google Cloud Console에서 현재 상태를 먼저 확인한다.

### 배포 계정 권한

```powershell
$ProjectNumber = gcloud projects describe $ProjectId --format="value(projectNumber)"
$WifMember = "principalSet://iam.googleapis.com/projects/$ProjectNumber/locations/global/workloadIdentityPools/github-actions/attribute.environment/core-api-preview"

gcloud projects add-iam-policy-binding $ProjectId `
  --member="serviceAccount:$DeployAccount" `
  --role="roles/run.developer"

gcloud artifacts repositories add-iam-policy-binding $Repository `
  --location=$Region `
  --member="serviceAccount:$DeployAccount" `
  --role="roles/artifactregistry.writer"

gcloud iam service-accounts add-iam-policy-binding $RuntimeAccount `
  --member="serviceAccount:$DeployAccount" `
  --role="roles/iam.serviceAccountUser"

gcloud iam service-accounts add-iam-policy-binding $DeployAccount `
  --member=$WifMember `
  --role="roles/iam.workloadIdentityUser"
```

기존 `bodeul-repo` provider는 `admin-web-preview` 환경으로 제한되어 있으므로 조건을 넓히지 않는다. `bodeul-core-api-preview` provider를 별도로 만들고 저장소, `master` ref, `core-api-preview` environment를 모두 조건으로 고정한다. 서비스 계정 key JSON은 발급하지 않는다.

### Secret 생성과 권한

```powershell
$SecretIds = @(
  "bodeul-core-api-preview-db-jdbc-url",
  "bodeul-core-api-preview-db-username",
  "bodeul-core-api-preview-db-password",
  "bodeul-core-api-preview-kakao-local-rest-api-key"
)

foreach ($SecretId in $SecretIds) {
  gcloud secrets create $SecretId --replication-policy=automatic --project=$ProjectId
  gcloud secrets add-iam-policy-binding $SecretId `
    --project=$ProjectId `
    --member="serviceAccount:$RuntimeAccount" `
    --role="roles/secretmanager.secretAccessor"
}
```

secret 값은 콘솔에서 입력하거나 `core-api/deploy/cloud-run/set-preview-secrets.ps1`을 사용한다. 스크립트는 보안 입력을 프로세스 표준 입력으로만 전달하고 파일이나 shell history에 남기지 않는다.

Kakao 키만 회전할 때는 DB 자격 증명을 다시 입력하지 않고 대상 secret을 지정한다.

```powershell
.\core-api\deploy\cloud-run\set-preview-secrets.ps1 `
  -SecretIds "bodeul-core-api-preview-kakao-local-rest-api-key"
```

## GitHub Environment

`core-api-preview`에는 다음 Variables만 등록한다.

- `GCP_PROJECT_ID=bodeul-dev`
- `GCP_REGION=asia-northeast1`
- `CLOUD_RUN_SERVICE=bodeul-core-api-preview`
- `CLOUD_RUN_ARTIFACT_REPOSITORY=bodeul-core-api`
- `CLOUD_RUN_WORKLOAD_IDENTITY_PROVIDER=projects/533563500316/locations/global/workloadIdentityPools/github-actions/providers/bodeul-core-api-preview`
- `CLOUD_RUN_DEPLOY_SERVICE_ACCOUNT=bodeul-core-preview-deployer@bodeul-dev.iam.gserviceaccount.com`
- `CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT=bodeul-core-preview-runtime@bodeul-dev.iam.gserviceaccount.com`
- `CORE_DB_JDBC_URL_SECRET_VERSION=1`
- `CORE_DB_USERNAME_SECRET_VERSION=1`
- `CORE_DB_PASSWORD_SECRET_VERSION=1`
- `KAKAO_LOCAL_REST_API_KEY_SECRET_VERSION=1`
- `FIREBASE_PROJECT_ID=bodeul-dev`
- `FIREBASE_PROJECT_NUMBER=533563500316`

`FIREBASE_PROJECT_NUMBER`는 token issuer와 audience를 제한하기 위한 공개 project 식별자이며 secret으로 저장하지 않는다. 배포 workflow가 `BODEUL_APP_CHECK_MODE=observe`를 Cloud Run 환경변수로 주입한다.

`OCI_REGION`, `CORE_API_SERVICE_NAME` 같은 OCI 변수는 제거한다. production Environment는 변경하지 않는다.

## 최초 서비스 공개

Android와 사용자 웹은 Google Cloud IAM token이 아니라 Firebase ID token을 사용한다. 따라서 Cloud Run IAM 단계에서는 서비스 호출을 공개하고 Spring Security가 `/health` 외 요청을 인증해야 한다.

Cloud Run은 일부 `z`로 끝나는 URL 경로를 예약하므로 Core API 상태 경로는 `/healthz`가 아니라 `/health`를 사용한다. 이는 [Cloud Run 알려진 문제의 Reserved URL paths](https://cloud.google.com/run/docs/known-issues#reserved_url_paths) 회피 기준이다.

최초 image 배포 후 프로젝트 소유자가 한 번만 실행한다.

```powershell
gcloud run services add-iam-policy-binding bodeul-core-api-preview `
  --project=bodeul-dev `
  --region=asia-northeast1 `
  --member=allUsers `
  --role=roles/run.invoker
```

배포 workflow는 Cloud Run IAM policy를 변경하지 않는다. 공개 호출을 허용하더라도 `/api/auth/me` 무인증 요청은 Spring에서 401을 반환해야 한다.

## 배포

`Core API Preview Deploy` workflow는 다음 순서로 실행한다.

1. `master`와 확인 입력값을 검사한다.
2. Gradle test를 실행한다.
3. GitHub OIDC와 WIF로 배포 계정에 인증한다.
4. Java 21 비루트 컨테이너를 빌드해 Artifact Registry에 commit SHA tag로 게시한다.
5. Secret Manager reference와 runtime 서비스 계정을 연결한다.
6. 최신 revision에 트래픽 100%를 보낸다.
7. `/health` 200과 `/api/auth/me` 무인증 401을 검사한다.

production 자동 배포 workflow는 만들지 않는다. production DB, Firebase project, domain, 비용 한도와 rollback 리허설이 확정된 뒤 별도로 추가한다.

## Rollback

Cloud Run revision은 immutable image digest를 참조한다. 장애 시 직전 정상 revision으로 트래픽을 되돌린다.

```powershell
gcloud run revisions list `
  --service=bodeul-core-api-preview `
  --project=bodeul-dev `
  --region=asia-northeast1

gcloud run services update-traffic bodeul-core-api-preview `
  --project=bodeul-dev `
  --region=asia-northeast1 `
  --to-revisions="<정상-revision>=100"
```

DB migration은 배포 workflow와 분리돼 있으므로 애플리케이션 rollback이 schema rollback을 자동 실행하지 않는다.

## 검증 기록

배포와 rollback 결과는 `docs/reports/`와 Issue #156에, 실제 token과 PostgreSQL role 연결 결과는 Issue #157에 기록한다. Kakao Local Secret 주입과 실호출 결과는 [Issue 158 Kakao Local Core API preview 실검증](../reports/issue-158-kakao-local-core-api-2026-07-16.md)에 기록한다.

- commit SHA, Cloud Run revision, 리전과 서비스 URL
- `/health` 200
- 무인증 `/api/auth/me` 401
- 정상, 만료, 변조, 다른 Firebase project token 응답
- DB role 조회와 secret/token 로그 비노출
- 인증된 `GET /api/places/search` 200과 Kakao 콘솔 쿼터 반영
- 임시 Firebase 사용자와 PostgreSQL 역할 행 정리
- cold start 시간과 정상 기동 여부
- 직전 revision rollback 결과

## 중단 조건

- Google Cloud 결제 계정이나 프로젝트 소유권이 확인되지 않음
- WIF provider가 `bodeul110/Bodeul`로 제한되지 않음
- 서비스 계정 JSON key를 발급하거나 저장소에 넣어야만 배포 가능함
- DB owner 또는 migration 자격 증명을 runtime에 사용함
- Cloud Run 최대 인스턴스와 DB pool 상한이 설정되지 않음
- secret, Firebase token, DB 연결 문자열이 로그나 GitHub 출력에 노출됨
- Firebase와 PostgreSQL 양쪽에 운영 쓰기를 하면서 source of truth가 정해지지 않음
