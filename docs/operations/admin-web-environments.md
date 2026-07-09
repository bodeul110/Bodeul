# 관리자 웹 GitHub Environment 기준

기준일: 2026-07-10

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

관리자 웹을 별도 레포와 별도 배포 단위로 분리하기 전에 `admin-web` 전용 GitHub Environment, 변수, secret, 배포 승인 기준을 정한다.

## 선택한 방식

기존 저장소의 `dev`, `production` Environment와 별도로 `admin-web-preview`, `admin-web-production` Environment를 둔다. 별도 저장소 `bodeul-admin-web`에는 우선 `admin-web-preview` Environment만 만들고, production Environment와 live workflow는 #134 결정 이후 추가한다. 관리자 웹 Firebase Web config는 코드에 직접 넣지 않고 Vite `VITE_*` 환경변수로 주입한다.

## 대안

- 기존 `dev`, `production` Environment를 그대로 공유한다.
- 관리자 웹 별도 레포를 만든 뒤 Environment를 처음부터 구성한다.
- Environment를 쓰지 않고 repo-level secret과 Firebase CLI 배포만 유지한다.

## 선택 이유

관리자 웹은 Firebase Hosting, App Check site key, Firebase Web config, 관리자 권한 검증이 Android 앱과 다르다. 같은 Environment를 공유하면 Android/Functions 배포 권한과 관리자 웹 배포 권한이 섞이므로, 레포 분리 전부터 `admin-web` 전용 경계를 두는 편이 현재 규모에서 안전하다.

## 리스크

- Environment 값이 없으면 `admin-web` 빌드가 실패한다.
- preview와 production의 secret 이름은 같지만 실제 값과 권한은 달라야 한다.
- production Environment의 required reviewer와 protected branch 기준이 잘못되면 의도하지 않은 live 배포가 가능해진다.
- Firebase Hosting 소유권과 운영 프로젝트가 확정되기 전에는 자동 live 배포를 켜지 않는다.

## 현재 GitHub 설정

기존 Environment:

| Environment | 현재 용도 | 관리자 웹 분리 판단 |
| --- | --- | --- |
| `dev` | 기존 개발/운영 점검 환경 | 관리자 웹 preview와 분리한다. |
| `production` | 기존 운영 환경, required reviewer와 protected branch 정책 있음 | 관리자 웹 live 배포는 `admin-web-production`으로 분리한다. |

관리자 웹 전용 Environment:

| Environment | 목적 | 보호 기준 |
| --- | --- | --- |
| `admin-web-preview` | PR 또는 수동 preview 검증 | reviewer와 branch policy 없음 |
| `admin-web-production` | Firebase Hosting live 배포 | protected branch 기준과 `bodeul110` required reviewer 적용 |

2026-07-06 현재 실행 결과:

- `admin-web-preview`: 생성 완료, protection rule 없음, deployment branch policy 없음
- `admin-web-production`: 생성 완료, `bodeul110` required reviewer, protected branch policy 적용
- `admin-web-preview`: dev Firebase Web config 주입을 위한 variables/secrets 설정
- `admin-web-preview`: WIF preview 배포용 `ADMIN_WEB_WORKLOAD_IDENTITY_PROVIDER`, `ADMIN_WEB_DEPLOY_SERVICE_ACCOUNT` variable 설정
- `admin-web-preview`: 기존 Firebase refresh token 기반 `ADMIN_WEB_FIREBASE_TOKEN` secret 삭제 완료
- `admin-web-production`: 실제 운영 Firebase 프로젝트 확정 전까지 variables/secrets 미설정

2026-07-10 별도 저장소 진행 결과:

- [bodeul110/bodeul-admin-web](https://github.com/bodeul110/bodeul-admin-web) 저장소를 생성했다.
- `admin-web-preview` Environment를 새 저장소에도 생성했다.
- 원 저장소 `admin-web-preview`의 공개 variables 8개를 새 저장소로 복사했다.
- 원 저장소 Environment secret 값은 GitHub에서 읽을 수 없으므로 새 저장소에는 자동 복사하지 않았다.
- 새 저장소 `Admin Web Build` workflow는 CI용 placeholder Firebase 값으로 lint/build만 검증하며, GitHub Actions에서 성공했다.
- 새 저장소 `Admin Web Preview Deploy` workflow는 추가했지만, secret 등록과 WIF provider 허용 조건 확인 전까지 수동 실행 검증이 남아 있다.

## 변수와 secret 기준

### `admin-web-preview`

| 이름 | 종류 | 값 기준 |
| --- | --- | --- |
| `VITE_FIREBASE_AUTH_DOMAIN` | variable | dev Firebase Auth domain |
| `VITE_FIREBASE_PROJECT_ID` | variable | dev Firebase project id |
| `VITE_FIREBASE_STORAGE_BUCKET` | variable | dev Firebase Storage bucket |
| `VITE_FIREBASE_API_KEY` | secret | dev Firebase Web API key |
| `VITE_FIREBASE_APP_ID` | secret | dev Firebase Web app id |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | secret | dev Firebase sender id |
| `VITE_FIREBASE_APPCHECK_SITE_KEY` | secret, 선택 | App Check 적용 시 설정 |
| `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` | secret, 선택 | 로컬/CI 검증에만 제한적으로 사용 |

Firebase Web API key는 서버 비밀값은 아니지만, 공개 레포 노출을 줄이고 preview/production 경계를 명확히 하기 위해 Environment secret으로 관리한다.

### `admin-web-production`

| 이름 | 종류 | 설정 조건 |
| --- | --- | --- |
| `VITE_FIREBASE_AUTH_DOMAIN` | variable | 운영 Firebase 프로젝트 확정 후 |
| `VITE_FIREBASE_PROJECT_ID` | variable | 운영 Firebase 프로젝트 확정 후 |
| `VITE_FIREBASE_STORAGE_BUCKET` | variable | 운영 Firebase Storage bucket 확정 후 |
| `VITE_FIREBASE_API_KEY` | secret | 운영 Firebase Web API key 확정 후 |
| `VITE_FIREBASE_APP_ID` | secret | 운영 Firebase Web app id 확정 후 |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | secret | 운영 Firebase sender id 확정 후 |
| `VITE_FIREBASE_APPCHECK_SITE_KEY` | secret | production App Check enforcement 전 |

운영 값이 없을 때 production 배포가 성공하면 안 된다. 따라서 production workflow를 추가할 때도 필수 값 누락 시 실패하도록 둔다.

## Build workflow 기준

원 저장소의 `Admin Web Build` workflow는 `admin-web-preview` Environment 값을 사용한다.

```yaml
environment: admin-web-preview
```

현재 workflow의 책임:

1. `npm ci`
2. `npm run lint`
3. `npm run build`
4. `admin-web/dist` 산출물 업로드

이 workflow는 Firebase Hosting 배포를 수행하지 않는다. build/lint 검증과 산출물 업로드만 담당한다.

별도 저장소의 `Admin Web Build` workflow는 production 또는 preview Firebase 프로젝트에 접속하지 않도록 CI용 placeholder Firebase 값을 사용한다. 따라서 새 저장소의 기본 build 검증은 secret 등록 전에 통과할 수 있고, 실제 Firebase 값이 필요한 검증은 `Admin Web Preview Deploy`에서만 다룬다.

## 배포 workflow 기준

### preview

조건:

- 현재는 `workflow_dispatch` 수동 실행에서만 동작한다.
- `admin-web` build/lint가 통과해야 한다.
- Firebase Hosting preview channel에만 배포한다.
- 배포 URL과 만료일을 job summary에 남긴다.
- 모든 PR마다 자동 배포하는 방식은 preview channel 수명, 배포 이력, 쿼터 기준을 정한 뒤 다시 검토한다.

현재 workflow:

- 원 저장소: `.github/workflows/admin-web-preview-deploy.yml`
- 별도 저장소: `bodeul-admin-web/.github/workflows/admin-web-preview-deploy.yml`
- 이름: `Admin Web Preview Deploy`
- 기본 channel: `admin-web-preview`
- 기본 만료: 7일
- Firebase CLI: `firebase-tools@15.22.3`
- 동일 산출물이 이미 preview channel의 current active version이면 no-op 성공으로 처리

`firebase-tools@15.22.2`는 GitHub Actions WIF/ADC 인증에서 `Failed to authenticate, have you run firebase login?` 오류가 발생하는 회귀가 확인되어 사용하지 않는다. `15.22.3`은 해당 회귀 수정 후 버전으로, WIF 기반 preview 배포 검증 기준 버전으로 둔다.

권장 명령:

```bash
npm --prefix admin-web ci
npm --prefix admin-web run lint
npm --prefix admin-web run build
firebase hosting:channel:deploy "$FIREBASE_HOSTING_CHANNEL" --project "$FIREBASE_PROJECT_ID" --expires 7d
```

preview workflow가 사용하는 Environment 값:

| 이름 | 종류 | 설명 |
| --- | --- | --- |
| `FIREBASE_PROJECT_ID` | variable | preview 배포 대상 Firebase project id |
| `FIREBASE_HOSTING_CHANNEL` | variable | 기본 preview channel id |
| `FIREBASE_HOSTING_EXPIRES` | variable | 기본 preview channel 만료 기간 |
| `ADMIN_WEB_WORKLOAD_IDENTITY_PROVIDER` | variable | GitHub OIDC용 Workload Identity Provider 전체 이름 |
| `ADMIN_WEB_DEPLOY_SERVICE_ACCOUNT` | variable | preview 배포 전용 Google Cloud 서비스 계정 이메일 |
| `VITE_FIREBASE_*` | variables/secrets | 관리자 웹 빌드용 Firebase Web config |

preview workflow는 `google-github-actions/auth@v3`로 Google Cloud 인증을 수행한다. GitHub OIDC와 Workload Identity를 통해 `admin-web-preview-deployer@bodeul-dev.iam.gserviceaccount.com` 서비스 계정을 사용하며, 기존 Firebase refresh token 기반 `ADMIN_WEB_FIREBASE_TOKEN` fallback은 제거했다.

Firebase CLI가 GitHub Actions WIF external account credentials file을 직접 읽는 경로에서 인증 실패를 보였기 때문에, 현재 workflow는 WIF로 발급한 짧은 수명 OAuth access token을 Firebase CLI에 명시적으로 전달한다. 이 값은 장기 저장 secret이 아니며 job 실행 중에만 사용된다. Firebase CLI의 ADC/WIF 처리 경로가 안정적으로 검증되면 access token 전달 우회는 제거 대상으로 다시 본다.

### preview 배포 인증 전환 기준

선택한 방향:

- 초기 전환: 기존 `ADMIN_WEB_FIREBASE_TOKEN`을 `admin-web-preview` Environment secret fallback으로 유지한 채 WIF를 먼저 검증했다.
- 현재 기준: `ADMIN_WEB_WORKLOAD_IDENTITY_PROVIDER`, `ADMIN_WEB_DEPLOY_SERVICE_ACCOUNT`가 필수이며 preview 배포는 WIF 전용으로 동작한다.
- 완료 상태: WIF-only 수동 preview 배포가 성공했고 `ADMIN_WEB_FIREBASE_TOKEN` secret은 삭제했다.

근거:

- Firebase CLI는 CI 환경에서 서비스 계정 기반 Application Default Credentials를 사용할 수 있다.
- `google-github-actions/auth@v3`는 credentials file을 생성하고 `GOOGLE_APPLICATION_CREDENTIALS`를 후속 step에 export할 수 있으며, 필요하면 OAuth access token을 출력할 수 있다.
- Google Cloud Workload Identity Federation은 GitHub Actions OIDC 토큰을 Google Cloud 인증으로 교환하므로 장기 수명 JSON key나 Firebase refresh token 보관 부담을 줄인다.
- 현재 규모에서는 preview 배포 하나 때문에 즉시 레포를 쪼개기보다, 모노레포 안에서 인증 경계를 먼저 분리하고 실제 배포 로그로 권한 범위를 확인하는 편이 안전하다.

필요한 Google Cloud 권한 후보:

| 대상 | 권한 | 이유 |
| --- | --- | --- |
| Workload Identity Pool/Provider 설정자 | `roles/iam.workloadIdentityPoolAdmin` 또는 동등한 커스텀 권한 | GitHub OIDC provider 생성/수정 |
| 배포 서비스 계정에 대한 GitHub principal | `roles/iam.workloadIdentityUser` | GitHub Actions가 서비스 계정을 impersonation 하기 위해 필요 |
| 배포 서비스 계정 | `roles/firebasehosting.admin` | Firebase Hosting preview channel 생성/배포에 필요한 Hosting read/write 권한 |

`roles/firebasehosting.admin`으로 시작하고, 실제 WIF 배포 로그에서 추가 권한 부족이 확인될 때만 범위를 넓힌다. Functions, Firestore Rules, Storage Rules 배포를 이 workflow에 섞지 않기 때문에 Firebase Admin 또는 Editor 권한을 기본값으로 주지 않는다.

완료된 GCP/GitHub 설정:

1. Google Cloud Console 또는 `gcloud`가 있는 환경에서 `bodeul-dev` 프로젝트에 preview 배포 전용 서비스 계정을 만들었다.
2. GitHub Actions용 Workload Identity Pool과 OIDC Provider를 만들고, provider 조건은 `bodeul110/Bodeul` 저장소로 제한했다.
3. GitHub principal에 preview 배포 서비스 계정의 `roles/iam.workloadIdentityUser`를 부여했다.
4. preview 배포 서비스 계정에 `roles/firebasehosting.admin`을 부여했다.
5. GitHub Environment `admin-web-preview`에 `ADMIN_WEB_WORKLOAD_IDENTITY_PROVIDER`, `ADMIN_WEB_DEPLOY_SERVICE_ACCOUNT` variable을 추가했다.
6. `Admin Web Preview Deploy`를 수동 실행해 인증 모드가 `Workload Identity`인지 확인했다.
7. 성공 후 `ADMIN_WEB_FIREBASE_TOKEN` secret을 삭제했다.

새 저장소에서 추가로 필요한 설정:

1. `admin-web-preview` Environment secret을 새 저장소에 다시 등록한다.
2. Workload Identity Provider의 attribute condition 또는 principal binding이 `bodeul110/bodeul-admin-web`을 허용하는지 확인한다.
3. `Admin Web Preview Deploy`를 수동 실행해 새 저장소 기준 preview URL을 확인한다.
4. API 모드 preview가 필요하면 `VITE_BODEUL_DATA_BACKEND=api`, `VITE_BODEUL_API_BASE_URL=<bodeul-api preview URL>` variable을 추가하고 API 서버 CORS에 preview origin을 넣는다.

검증된 실행:

- WIF-only preview deploy 성공: <https://github.com/bodeul110/Bodeul/actions/runs/28229627231>
- Firebase Hosting release 작성자: `admin-web-preview-deployer@bodeul-dev.iam.gserviceaccount.com`
- preview URL: <https://bodeul-dev--admin-web-preview-b1x2s7k5.web.app>

참고 문서:

- [google-github-actions/auth Workload Identity Federation](https://github.com/google-github-actions/auth)
- [Google Cloud Workload Identity Federation for deployment pipelines](https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
- [Firebase Hosting IAM roles](https://docs.cloud.google.com/iam/docs/roles-permissions/firebasehosting)

### production

조건:

- `master` 또는 보호 브랜치 기준으로만 실행한다.
- `admin-web-production` Environment reviewer 승인을 거친다.
- App Check site key, Firebase Auth authorized domain, Hosting domain을 확인한다.
- 배포 전 preview URL 검증 결과가 PR 또는 문서에 있어야 한다.

권장 명령:

```bash
npm --prefix admin-web ci
npm --prefix admin-web run lint
npm --prefix admin-web run build
firebase deploy --only hosting --project "$FIREBASE_PROJECT_ID"
```

## 이번 단계에서 한 일

- `admin-web-preview` Environment를 build workflow에 연결했다.
- dev Firebase Web config를 `admin-web-preview` variables/secrets로 옮겼다.
- `admin-web/firebase.ts`의 하드코딩 dev 값을 제거했다.
- 로컬 개발용 `.env.example`을 추가했다.
- 수동 실행용 Firebase Hosting preview deploy workflow를 추가했다.
- preview deploy workflow를 Workload Identity 기반으로 바꿨다.
- WIF/ADC 인증 회귀를 피하기 위해 preview deploy Firebase CLI를 `15.22.3`으로 올렸다.
- WIF 경로에서는 `google-github-actions/auth@v3`가 발급한 짧은 수명 access token을 Firebase CLI에 전달하도록 바꿨다.
- WIF 수동 preview deploy 성공 후 기존 `ADMIN_WEB_FIREBASE_TOKEN` fallback 경로를 제거했다.
- 모든 PR마다 자동 preview 배포하는 단계는 보류했다.
- `bodeul-admin-web` 저장소에 `admin-web-preview` Environment를 생성하고 공개 variables를 복사했다.
- 새 저장소에는 secret 값을 자동 복사하지 않고, follow-up 이슈로 남겼다.

## 다음 작업

1. 새 저장소 `admin-web-preview` Environment secret을 등록한다.
2. GCP WIF provider가 새 저장소를 허용하는지 확인한다.
3. 새 저장소 `Admin Web Preview Deploy` workflow를 수동 실행한다.
4. 모든 PR마다 preview 배포를 자동 실행할지 재검토한다.
5. `admin-web-production` 운영 Firebase 프로젝트와 Hosting site를 확정한다.
6. production Environment 값을 설정한다.
7. production 배포용 WIF 서비스 계정과 권한 범위를 정한다.
8. App Check 적용 시점과 강제 기준을 production 배포 전에 재확인한다.
9. production live workflow는 required reviewer, protected branch, preview 검증 기록 조건이 준비된 뒤 추가한다.

## 관련 이슈

- [#74 관리자 웹 레포 분리 기준 검토](https://github.com/bodeul110/Bodeul/issues/74)
