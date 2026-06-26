# 관리자 웹 GitHub Environment 기준

기준일: 2026-06-26

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

관리자 웹을 별도 레포와 별도 배포 단위로 분리하기 전에 `admin-web` 전용 GitHub Environment, 변수, secret, 배포 승인 기준을 정한다.

## 선택한 방식

기존 저장소의 `dev`, `production` Environment와 별도로 `admin-web-preview`, `admin-web-production` Environment를 둔다. 관리자 웹 Firebase Web config는 코드에 직접 넣지 않고 Vite `VITE_*` 환경변수로 주입한다.

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

2026-06-26 현재 실행 결과:

- `admin-web-preview`: 생성 완료, protection rule 없음, deployment branch policy 없음
- `admin-web-production`: 생성 완료, `bodeul110` required reviewer, protected branch policy 적용
- `admin-web-preview`: dev Firebase Web config 주입을 위한 variables/secrets 설정
- `admin-web-production`: 실제 운영 Firebase 프로젝트 확정 전까지 variables/secrets 미설정

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

`Admin Web Build` workflow는 `admin-web-preview` Environment 값을 사용한다.

```yaml
environment: admin-web-preview
```

현재 workflow의 책임:

1. `npm ci`
2. `npm run lint`
3. `npm run build`
4. `admin-web/dist` 산출물 업로드

이 workflow는 Firebase Hosting 배포를 수행하지 않는다. build/lint 검증과 산출물 업로드만 담당한다.

## 배포 workflow 기준

### preview

조건:

- 현재는 `workflow_dispatch` 수동 실행에서만 동작한다.
- `admin-web` build/lint가 통과해야 한다.
- Firebase Hosting preview channel에만 배포한다.
- 배포 URL과 만료일을 job summary에 남긴다.
- 모든 PR마다 자동 배포하는 방식은 관리자 웹 전용 배포 인증이 분리된 뒤 다시 검토한다.

현재 workflow:

- `.github/workflows/admin-web-preview-deploy.yml`
- 이름: `Admin Web Preview Deploy`
- 기본 channel: `admin-web-preview`
- 기본 만료: 7일
- Firebase CLI: `firebase-tools@15.22.2`

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
| `FIREBASE_TOKEN` | repo-level secret | 현재 Firebase CLI 배포 인증. 관리자 웹 전용 인증으로 분리 예정 |
| `VITE_FIREBASE_*` | variables/secrets | 관리자 웹 빌드용 Firebase Web config |

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
- 모든 PR마다 자동 preview 배포하는 단계는 보류했다.

## 다음 작업

1. preview workflow를 한 번 수동 실행해 Hosting URL과 Auth authorized domain 동작을 확인한다.
2. 관리자 웹 전용 배포 인증을 repo-level `FIREBASE_TOKEN`에서 Environment 전용 인증으로 분리한다.
3. 모든 PR마다 preview 배포를 자동 실행할지 재검토한다.
4. `admin-web-production` 운영 Firebase 프로젝트와 Hosting site를 확정한다.
5. production Environment 값을 설정한다.
6. App Check 적용 시점과 강제 기준을 production 배포 전에 재확인한다.

## 관련 이슈

- [#74 관리자 웹 레포 분리 기준 검토](https://github.com/bodeul110/Bodeul/issues/74)
