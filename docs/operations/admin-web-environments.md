# 관리자 웹 GitHub Environment 기준

기준일: 2026-06-26

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

관리자 웹을 별도 레포로 분리하기 전에 `admin-web` 전용 GitHub Environment, 변수, secret, 배포 승인 기준을 정한다.

## 선택한 방식

기존 저장소의 `dev`, `production` Environment와 별도로 `admin-web-preview`, `admin-web-production` Environment를 둔다. 먼저 빈 Environment와 문서 기준을 만들고, 실제 secret 값과 Firebase Hosting 배포 workflow는 별도 작업에서 연결한다.

## 대안

- 기존 `dev`, `production` Environment를 그대로 공유한다.
- `admin-web` 별도 레포를 만든 뒤 Environment를 처음부터 구성한다.
- Environment를 쓰지 않고 repo-level secret과 수동 Firebase CLI 배포만 유지한다.

## 선택 이유

관리자 웹은 Firebase Hosting, App Check site key, Firebase Web config, 운영 도메인 검증이 Android 앱과 다르다. 기존 Environment를 공유하면 Android/Functions 배포 권한과 관리자 웹 배포 권한이 섞이므로, 레포 분리 전부터 `admin-web` 전용 환경 경계를 만드는 편이 안전하다.

## 리스크

- Environment만 만들어도 secret 값이 없으면 배포 workflow는 동작하지 않는다.
- preview와 production secret 이름이 같아도 값과 권한은 다를 수 있다.
- production 환경에서 required reviewer나 protected branch 기준을 잘못 설정하면 의도하지 않은 배포가 가능해진다.
- Firebase Hosting 설정 소유권이 확정되기 전에는 자동 live 배포를 켜지 않는다.

## 현재 GitHub 설정

현재 저장소에는 기존 Environment가 있다.

| Environment | 현재 용도 | 관리자 웹 분리 판단 |
| --- | --- | --- |
| `dev` | 기존 개발/운영 점검용 환경 | 관리자 웹 전용 preview와 분리한다. |
| `production` | 기존 운영 환경, required reviewer와 protected branch 정책 있음 | 관리자 웹 live 배포는 `admin-web-production`으로 분리한다. |

관리자 웹 전용 Environment 기준:

| Environment | 목적 | 보호 기준 |
| --- | --- | --- |
| `admin-web-preview` | PR 또는 수동 preview 배포 | 생성 완료. reviewer와 branch policy는 두지 않는다. |
| `admin-web-production` | Firebase Hosting live 배포 | 생성 완료. protected branch 기준과 required reviewer를 둔다. |

## 변수와 secret 기준

### 공통 변수

| 이름 | 종류 | preview | production | 비고 |
| --- | --- | --- | --- | --- |
| `FIREBASE_PROJECT_ID` | variable | `bodeul-dev` 후보 | 운영 프로젝트 확정 전까지 보류 | 현재 repo-level 변수는 `bodeul-dev`다. |
| `FIREBASE_HOSTING_SITE` | variable | `bodeul-dev` 후보 | 운영 Hosting site 확정 후 설정 | Firebase Hosting site 이름 |
| `FIREBASE_HOSTING_CHANNEL` | variable | `admin-web-preview` | `live` | preview는 channel deploy, production은 live deploy |
| `VITE_FIREBASE_AUTH_DOMAIN` | variable | dev Auth domain | production Auth domain | Firebase Web config 환경 변수화 시 사용 |
| `VITE_FIREBASE_PROJECT_ID` | variable | dev project id | production project id | Firebase Web config 환경 변수화 시 사용 |
| `VITE_FIREBASE_STORAGE_BUCKET` | variable | dev bucket | production bucket | Firebase Web config 환경 변수화 시 사용 |

### secret

| 이름 | preview | production | 비고 |
| --- | --- | --- | --- |
| `FIREBASE_TOKEN` 또는 배포용 Workload Identity 설정 | 필요 | 필요 | 현재 repo-level `FIREBASE_TOKEN`은 존재하지만 전용 Environment로 분리해야 한다. |
| `VITE_FIREBASE_API_KEY` | 필요 | 필요 | Firebase Web API key는 서버 비밀값은 아니지만 repo 공개 노출과 환경 분리를 위해 Environment 기준으로 관리한다. |
| `VITE_FIREBASE_APP_ID` | 필요 | 필요 | Firebase Web config |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | 필요 | 필요 | Firebase Web config |
| `VITE_FIREBASE_APPCHECK_SITE_KEY` | 선택 | 운영 전 필수 | App Check enforcement 전 production에 필수 |
| `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` | 로컬/CI 검증에만 제한 | 사용하지 않음 | production에는 두지 않는다. |

현재 `admin-web/firebase.ts`에는 dev Firebase Web config 값이 직접 들어 있다. Environment 배포를 자동화하기 전에는 Firebase Web config를 Vite 환경 변수 기반으로 바꿀지 별도 PR에서 결정해야 한다.

## 배포 workflow 기준

### preview

조건:
- PR 또는 수동 실행에서만 동작한다.
- `admin-web` build/lint가 통과해야 한다.
- Firebase Hosting preview channel에만 배포한다.
- 배포 URL과 만료일을 PR 코멘트 또는 job summary에 남긴다.

권장 명령:

```bash
npm --prefix admin-web ci
npm --prefix admin-web run lint
npm --prefix admin-web run build
firebase hosting:channel:deploy "$FIREBASE_HOSTING_CHANNEL" --project "$FIREBASE_PROJECT_ID" --expires 7d
```

### production

조건:
- `master` 또는 보호 브랜치 기준으로만 실행한다.
- `admin-web-production` Environment reviewer 승인을 거친다.
- App Check site key, Firebase Auth authorized domain, Hosting domain이 확인돼야 한다.
- 배포 전 preview URL 검증 결과가 문서 또는 PR에 있어야 한다.

권장 명령:

```bash
npm --prefix admin-web ci
npm --prefix admin-web run lint
npm --prefix admin-web run build
firebase deploy --only hosting --project "$FIREBASE_PROJECT_ID"
```

## 보호 규칙 기준

| Environment | reviewer | branch policy | admins bypass |
| --- | --- | --- | --- |
| `admin-web-preview` | 없음 | branch policy 없음 | 허용 |
| `admin-web-production` | 저장소 owner 1명 이상 | protected branch만 허용 | 허용 |

팀원이 늘면 `admin-web-production`은 `prevent_self_review=true`와 팀 reviewer를 검토한다. 현재는 1인 운영 계정 기준이라 self review 방지는 적용하지 않는다.

## 이번 단계에서 하는 일

- `admin-web-preview` Environment 생성
- `admin-web-production` Environment 생성
- secret 값은 생성하지 않음
- Firebase Console 설정은 변경하지 않음
- Hosting 배포 workflow는 추가하지 않음

2026-06-26 실행 결과:
- `admin-web-preview`: 생성 완료, protection rule 없음, deployment branch policy 없음
- `admin-web-production`: 생성 완료, `bodeul110` required reviewer, protected branch policy 적용
- 두 Environment 모두 현재 secret 0건, variable 0건이다.

## 다음 작업

1. Firebase Web config를 `VITE_*` 환경 변수 기반으로 전환할지 결정한다.
2. `admin-web-preview` 배포 workflow를 추가한다.
3. `admin-web-production` live 배포 workflow와 승인 기준을 추가한다.
4. Firebase Hosting 설정 소유권을 `Bodeul`에 유지할지, 분리 레포로 옮길지 결정한다.

## 관련 이슈

- [#74 관리자 웹 레포 분리 기준 검토](https://github.com/bodeul110/Bodeul/issues/74)
