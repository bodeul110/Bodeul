# 관리자 웹 API 환경변수와 CORS 기준

기준일: 2026-07-07

## 작업 목적

관리자 웹이 `bodeul-api`를 호출하는 검증 경로를 환경별로 안전하게 켜고 끌 수 있도록 환경변수, CORS origin, rollback 기준을 고정한다.

## 선택한 방식

기존 관리자 웹의 기본 데이터 경로는 `firebase`로 유지한다. 병원 가이드 API 검증이 필요한 환경에서만 `VITE_BODEUL_DATA_BACKEND=api`와 `VITE_BODEUL_API_BASE_URL`을 설정한다. 2026-07-07 기준 #140은 Oracle API, Supabase, Firebase Admin 인증, Vercel preview 관리자 웹을 묶어 #123의 실연동 검증 blocker를 해소하는 preview 환경 구축 이슈다.

API 서버는 `BODEUL_API_ALLOWED_ORIGINS` allow-list에 있는 관리자 웹 origin만 브라우저 호출로 허용한다.

## 대안

- 관리자 웹을 API 전용으로 고정한다.
- API 서버 CORS를 모든 origin 허용으로 둔다.
- preview와 production을 같은 API URL로 공유한다.

## 선택 이유

현재 MVP 규모에서는 매니저 심사 등 기존 Firebase 관리자 기능을 유지하면서 병원 가이드 read API만 선택적으로 검증하는 편이 운영 리스크가 가장 작다. CORS 전체 허용은 편하지만 관리자 API 경계 검증 목적과 맞지 않으므로 환경별 origin allow-list를 명시한다.

## 환경별 설정 기준

| 환경 | 관리자 웹 origin | `VITE_BODEUL_DATA_BACKEND` | `VITE_BODEUL_API_BASE_URL` | `BODEUL_API_ALLOWED_ORIGINS` | 상태 |
| --- | --- | --- | --- | --- | --- |
| local 기본 | `http://localhost:5173`, `http://127.0.0.1:5173` | `firebase` | `http://127.0.0.1:8080` | 기본값 사용 | 기본 개발 경로 |
| local API 검증 | `http://localhost:5173`, `http://127.0.0.1:5173` | `api` | `http://127.0.0.1:8080` | `http://localhost:5173,http://127.0.0.1:5173` | 즉시 검증 가능 |
| Firebase Hosting preview | Firebase Hosting preview channel URL | `firebase` 기본, API 검증 시 `api` | preview API URL 확정 후 설정 | preview channel origin을 쉼표 목록에 추가 | WIF preview 배포 workflow로 검증 가능 |
| Vercel preview API 검증 | Vercel preview URL | `api` | Oracle API preview URL | Vercel preview origin을 쉼표 목록에 추가 | #140 범위에서 진행 |
| production | #134에서 확정할 운영 관리자 웹 URL | `firebase` | 운영 API URL 확정 후 설정 | 운영 관리자 웹 origin만 추가 | #134 완료 전까지 보류 |

preview와 production의 실제 URL은 배포 시점에 확정되는 값이므로 공개 이슈나 PR 본문에 secret과 함께 적지 않는다. URL 자체가 공개 가능한 값이어도, 운영 전환 전에는 GitHub Environment, Vercel Environment, API 배포 서버 설정 위치만 문서화한다.

## 설정 위치

### 관리자 웹 로컬

로컬에서는 `admin-web/.env.local`에 둔다. 이 파일은 커밋하지 않는다.

```env
VITE_BODEUL_DATA_BACKEND=firebase
VITE_BODEUL_API_BASE_URL=http://127.0.0.1:8080
```

API 검증이 필요할 때만 다음처럼 바꾼다.

```env
VITE_BODEUL_DATA_BACKEND=api
VITE_BODEUL_API_BASE_URL=http://127.0.0.1:8080
```

### 관리자 웹 GitHub Environment

| Environment | 이름 | 종류 | 기준 |
| --- | --- | --- | --- |
| `admin-web-preview` | `VITE_BODEUL_DATA_BACKEND` | variable | 기본 `firebase`, API 검증 배포에서만 `api` |
| `admin-web-preview` | `VITE_BODEUL_API_BASE_URL` | variable | Firebase Hosting preview에서 API 검증을 할 때 preview API URL 설정 |
| `admin-web-production` | `VITE_BODEUL_DATA_BACKEND` | variable | 운영 전환 전까지 `firebase` |
| `admin-web-production` | `VITE_BODEUL_API_BASE_URL` | variable | 운영 API URL 확정 후 설정 |

`VITE_*` 값은 브라우저 번들에 포함될 수 있는 값이다. 다만 preview/production 경계를 명확히 하기 위해 GitHub Environment variable로 관리한다.

Vercel preview를 사용하는 #140 검증에서는 같은 이름의 Vercel Environment Variable을 사용한다. 이 값도 브라우저 번들에 들어갈 수 있으므로 secret 원문을 넣지 않고, API base URL과 전환 flag처럼 공개 가능한 설정만 둔다.

### API 서버

| 환경 | 이름 | 설정 위치 | 기준 |
| --- | --- | --- | --- |
| local | `BODEUL_API_ALLOWED_ORIGINS` | 로컬 shell 또는 `.env` 로더를 쓰는 실행 환경 | 미설정 시 로컬 관리자 웹 origin 기본 허용 |
| preview | `BODEUL_API_ALLOWED_ORIGINS` | preview API 배포 서버 환경변수 | Firebase Hosting preview 또는 Vercel preview 관리자 웹 origin만 추가 |
| production | `BODEUL_API_ALLOWED_ORIGINS` | 운영 API 배포 서버 환경변수 | #134에서 확정한 운영 관리자 웹 origin만 추가 |

`BODEUL_API_ALLOWED_ORIGINS`는 쉼표로 구분한 origin 목록이다. 경로가 붙은 URL은 허용하지 않는다.

올바른 예:

```text
https://admin-preview.example.com,http://localhost:5173
```

잘못된 예:

```text
https://admin-preview.example.com/path
```

## 로컬 preflight 검증

로컬 API 검증은 다음 순서로 진행한다.

1. `api` 서버를 실행한다.
2. `admin-web`을 `VITE_BODEUL_DATA_BACKEND=api`로 실행한다.
3. 병원 가이드 화면에서 요청을 보낸다.
4. 브라우저 Network 탭에서 `OPTIONS /admin/hospital-guides?limit=50` 응답에 CORS 헤더가 있는지 확인한다.

PowerShell에서 서버 응답만 확인할 때는 다음 명령을 쓴다.

```powershell
Invoke-WebRequest `
  -Method Options `
  -Uri "http://127.0.0.1:8080/admin/hospital-guides?limit=50" `
  -Headers @{
    Origin = "http://localhost:5173"
    "Access-Control-Request-Method" = "GET"
    "Access-Control-Request-Headers" = "authorization"
  }
```

성공 기준:

- HTTP status가 `204`다.
- `Access-Control-Allow-Origin`이 요청 origin과 같다.
- `Access-Control-Allow-Headers`에 `authorization`이 포함된다.

실패 기준:

- 허용되지 않은 origin이면 `403`과 `cors_origin_not_allowed`가 반환된다.
- API URL이 잘못됐거나 서버가 떠 있지 않으면 브라우저에서 network error로 보인다.

## rollback 기준

관리자 웹 API 검증에서 장애가 발생하면 서버 코드를 되돌리기 전에 관리자 웹 환경값부터 되돌린다.

| 증상 | 우선 조치 | 확인 |
| --- | --- | --- |
| preflight 403 | API 서버의 `BODEUL_API_ALLOWED_ORIGINS`에 관리자 웹 origin이 있는지 확인 | origin에 path가 붙지 않았는지 확인 |
| 브라우저 network error | `VITE_BODEUL_API_BASE_URL`이 실제 API URL인지 확인 | `/healthz` 접근 확인 |
| 401 `invalid_firebase_token` | Firebase Auth 로그인 상태와 ID token 발급 확인 | 관리자 웹 재로그인 |
| 403 `admin_role_required` | PostgreSQL `app_users.role` 확인 | 운영 DB 값은 공개 문서에 적지 않음 |
| 503 설정 오류 | `DATABASE_URL`, Firebase Admin SDK 설정 확인 | secret 원문 노출 금지 |

rollback 값:

```env
VITE_BODEUL_DATA_BACKEND=firebase
```

이 값으로 되돌리면 병원 가이드 검증 화면은 API를 호출하지 않는다. 기존 매니저 심사 등 Firebase 기반 관리자 기능은 유지된다.

## 완료 판단

- local 환경의 API 모드와 CORS preflight 기준을 문서화했다.
- Firebase Hosting preview는 WIF 수동 workflow로 검증할 수 있다.
- Vercel preview API 검증은 #140 범위에서만 `api` 모드를 켠다.
- production은 #134에서 URL, Auth domain, App Check, WIF live deploy 조건이 확정되기 전까지 `firebase` 기본값을 유지한다.
- API URL과 origin을 둘 위치를 GitHub Environment와 API 서버 환경변수로 분리했다.
- 장애 시 rollback 기준은 `VITE_BODEUL_DATA_BACKEND=firebase`로 고정했다.

## 관련 문서

- [관리자 API 초기 응답 계약](../architecture/admin-api-contract.md)
- [관리자 웹 GitHub Environment 기준](admin-web-environments.md)
- [PostgreSQL API 경계 기준](../architecture/postgres-api-boundary.md)
- [Issue 113 관리자 웹 병원 가이드 API 연결 기록](../reports/issue-113-admin-web-api-connection-2026-07-02.md)
- [Issue 123 병원 가이드 Firestore/API 응답 비교 기록](https://github.com/bodeul110/Bodeul/issues/123)
- [Issue 134 관리자 웹 production 배포 기준 확정](https://github.com/bodeul110/Bodeul/issues/134)
- [Issue 140 Oracle/Vercel 기반 관리자 웹 API 모드 실연동 검증 환경 구축](https://github.com/bodeul110/Bodeul/issues/140)
