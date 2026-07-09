# bodeul-api

`bodeul-api`는 Supabase PostgreSQL 접근, Firebase ID token 검증, 관리자/민감 쓰기 검증을 담당할 얇은 API 서버다. Firebase Auth, FCM, Storage, Hosting을 대체하지 않는다.

## 현재 범위

Issue #88 1차 범위는 서버 골격, 헬스체크, Firebase ID token 검증 경계, `DATABASE_URL` 설정 검증, 관리자 웹 초기 계약 확인 API를 포함한다.

- Node 22
- TypeScript
- Node 기본 `http` 서버
- `GET /healthz`
- `GET /admin/api-contract`
- `GET /admin/hospital-guides`
- Firebase Admin SDK 기반 ID token 검증
- `DATABASE_URL` 누락/형식 검증
- `pg` 기반 PostgreSQL pool 초기화와 종료 처리
- PostgreSQL `app_users.role` 기반 관리자 인가
- 관리자 웹 브라우저 호출용 CORS preflight
- 로컬 build/typecheck/test 스크립트

Issue #113 범위에서 관리자 웹의 병원 가이드 검증 화면이 `VITE_BODEUL_DATA_BACKEND=api`일 때 이 API를 호출한다. 매니저 심사 화면의 기본 데이터 경로는 아직 Firebase다.

Issue #140/#123 댓글 기준으로 Oracle API 실행 환경, Supabase `DATABASE_URL`, Firebase Admin 인증, 로컬 관리자 웹 API 모드, 실제 병원 가이드 API 응답 비교가 통과했다. 이 검증은 production 전환이 아니라 #123의 병원 가이드 Firestore/API 응답 비교를 가능하게 하는 preview 환경 구축 기록이다. Vercel preview는 production target 생성 문제로 직접 완료 범위에서 제외됐고 후속 작업으로 분리한다.

## 로컬 실행

```bash
cd api
npm install
npm run check
npm run build
npm start
```

기본 실행 주소는 `http://127.0.0.1:8080`이다.

환경변수로 host와 port를 바꿀 수 있다.

```bash
BODEUL_API_HOST=127.0.0.1 BODEUL_API_PORT=8080 npm --prefix api start
```

PowerShell에서는 다음처럼 실행한다.

```powershell
Set-Location api
npm install
npm run check
$env:BODEUL_API_HOST = "127.0.0.1"
$env:BODEUL_API_PORT = "8080"
npm start
```

## 헬스체크

```bash
curl http://127.0.0.1:8080/healthz
```

응답 예시:

```json
{
  "status": "ok",
  "service": "bodeul-api",
  "timestamp": "2026-06-30T00:00:00.000Z"
}
```

## 관리자 API 계약 확인

```bash
curl -H "Authorization: Bearer <Firebase ID token>" http://127.0.0.1:8080/admin/api-contract
```

이 API는 Firebase Admin SDK verifier가 설정된 서버에서만 실제 Firebase ID token을 검증한다. Firebase 설정이 없는 로컬/CI 환경에서는 관리자 API 인증 요청을 `auth_not_configured` 503으로 처리한다.

응답 예시:

```json
{
  "status": "ok",
  "service": "bodeul-api",
  "resource": "admin-api-contract",
  "version": "2026-07-01",
  "timestamp": "2026-06-30T00:00:00.000Z",
  "database": {
    "status": "missing"
  },
  "authentication": {
    "type": "firebase_id_token",
    "status": "configured"
  },
  "endpoints": [
    {
      "method": "GET",
      "path": "/healthz",
      "auth": "none",
      "response": "HealthPayload",
      "description": "배포와 모니터링을 위한 최소 헬스체크입니다."
    },
    {
      "method": "GET",
      "path": "/admin/api-contract",
      "auth": "firebase_id_token",
      "response": "AdminApiContractPayload",
      "description": "관리자 웹 초기 API 응답 계약과 서버 설정 상태를 확인합니다."
    },
    {
      "method": "GET",
      "path": "/admin/hospital-guides",
      "auth": "firebase_id_token",
      "response": "HospitalGuidesPayload",
      "description": "관리자 권한으로 병원 가이드 목록을 PostgreSQL에서 조회합니다."
    }
  ]
}
```

## 병원 가이드 조회

```bash
curl -H "Authorization: Bearer <Firebase ID token>" "http://127.0.0.1:8080/admin/hospital-guides?limit=50"
```

이 API는 관리자 권한 확인 후 PostgreSQL `hospital_guides` 테이블을 조회한다. 개인정보가 거의 없는 병원 안내 데이터를 첫 read API로 사용해 관리자 웹 전환 전 API 응답 계약을 낮은 위험으로 검증한다.

응답 예시:

```json
{
  "items": [
    {
      "id": "bad67ae3-b0ef-5a63-806d-7274ac4ce3d3",
      "hospitalName": "서울내과병원",
      "departmentName": "내과",
      "steps": [],
      "createdAt": "2026-04-23T16:48:39.766Z",
      "updatedAt": "2026-04-23T16:48:39.766Z"
    }
  ],
  "limit": 50
}
```

`limit`은 기본 50, 최대 100이다.

## 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `BODEUL_API_HOST` | `127.0.0.1` | 로컬 서버 바인딩 host |
| `BODEUL_API_PORT` | `8080` | 로컬 서버 port |
| `BODEUL_API_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 관리자 웹에서 브라우저로 호출할 수 있는 origin 목록. 쉼표로 구분한다. 빈 문자열이면 CORS origin을 허용하지 않는다. |
| `DATABASE_URL` | 없음 | PostgreSQL connection string. 누락은 `missing` 상태로 처리하고, 값이 있으면 `postgres` 또는 `postgresql` URL 형식만 허용한다. |
| `FIREBASE_PROJECT_ID` | 없음 | Application Default Credentials를 사용할 때 Firebase project를 지정한다. 값이 없고 서비스 계정 JSON도 없으면 Firebase verifier를 만들지 않는다. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | 없음 | Firebase Admin SDK 서비스 계정 JSON 문자열. 공개 문서, Issue, PR 본문에 실제 값을 적지 않는다. |

Firebase 인증 설정은 다음 기준을 따른다.

- `FIREBASE_SERVICE_ACCOUNT_JSON`이 있으면 해당 서비스 계정으로 Admin SDK를 초기화한다.
- `FIREBASE_SERVICE_ACCOUNT_JSON`이 없고 `FIREBASE_PROJECT_ID`가 있으면 Application Default Credentials 기준으로 Admin SDK를 초기화한다.
- 둘 다 없으면 서버는 실행되지만 관리자 API 인증 요청은 503을 반환한다.
- 서비스 계정 JSON 형식이 잘못되면 서버 시작 단계에서 실패한다.

PostgreSQL 설정은 다음 기준을 따른다.

- `DATABASE_URL`이 없으면 PostgreSQL client를 만들지 않는다.
- `DATABASE_URL`이 있으면 `pg` pool을 만든다.
- pool은 `max=5`, `idleTimeoutMillis=10000`, `connectionTimeoutMillis=5000`으로 시작한다.
- 서버 종료 신호를 받으면 HTTP 서버 종료 후 PostgreSQL pool도 닫는다.
- 연결 확인은 `select 1` 기준이며 실패 시 connection string을 노출하지 않고 `db_connection_failed`만 반환한다.

관리자 인가는 다음 기준을 따른다.

- Firebase ID token 검증 후 `uid`를 확보한다.
- PostgreSQL `app_users.firebase_uid`로 사용자 role을 조회한다.
- `app_users.role`이 `ADMIN`일 때만 관리자 API를 허용한다.
- role이 없거나 `ADMIN`이 아니면 403을 반환한다.
- DB 장애나 role 조회 실패는 503으로 구분한다.

## 관리자 웹 연결

관리자 웹은 `VITE_BODEUL_DATA_BACKEND=api`와 `VITE_BODEUL_API_BASE_URL`이 설정됐을 때 병원 가이드 검증 화면에서 `GET /admin/hospital-guides?limit=50`을 호출한다.

브라우저 호출 흐름:

1. Firebase Auth로 로그인한 관리자 사용자의 ID token을 가져온다.
2. `Authorization: Bearer <Firebase ID token>` 헤더를 붙여 `bodeul-api`를 호출한다.
3. 서버는 Firebase token 검증 후 PostgreSQL `app_users.role == 'ADMIN'`인지 확인한다.
4. 병원 가이드 목록을 PostgreSQL에서 읽어 관리자 웹에 표시한다.

관리자 웹 origin이 API 서버의 `BODEUL_API_ALLOWED_ORIGINS`에 없으면 preflight가 실패한다.

환경별 관리자 웹 API 전환값과 CORS allow-list 기준은 [관리자 웹 API 환경변수와 CORS 기준](../docs/operations/admin-api-environments.md)을 따른다.

## 보류 범위

- 매니저 서류 심사, 문의 조회 등 추가 read API 확장
- 병원 가이드 외 화면의 Firestore 응답과 PostgreSQL/API 응답 비교 검증
- Android 연동
- production Oracle VM 배포와 live 운영 전환

## preview 실연동 검증

#140에서는 production 전환이 아닌 preview 범위에서 아래 조건을 검증했다.

| 항목 | 기준 |
| --- | --- |
| API 실행 환경 | Oracle Free Tier 또는 동등 preview 실행 환경 |
| DB | Supabase PostgreSQL `DATABASE_URL`을 서버 환경변수로만 주입 |
| 인증 | Firebase Admin SDK로 Firebase ID token 검증 |
| 관리자 인가 | PostgreSQL `app_users.firebase_uid`, `role == 'ADMIN'` 기준 |
| 관리자 웹 | 로컬 `admin-web`에서 `VITE_BODEUL_DATA_BACKEND=api` |
| CORS | 로컬 관리자 웹 origin을 허용하고, preview URL 검증은 후속 작업으로 분리 |
| rollback | 관리자 웹 환경값을 `VITE_BODEUL_DATA_BACKEND=firebase`로 되돌림 |

2026-07-08 GitHub 이슈 댓글 기준 확인 결과:

- Oracle Free Tier VM에서 `bodeul-api` 실행
- 외부 `/healthz` HTTP 200
- Supabase PostgreSQL 연결과 `hospital_guides` 1건 조회
- Firebase ID token 기반 인증과 PostgreSQL `ADMIN` role 인가 통과
- 인증된 `GET /admin/hospital-guides?limit=50` 호출 성공
- 로컬 관리자 웹 API 모드에서 병원 가이드 1건 표시
- `compare:hospital-guides` 결과 `passed`
- Vercel preview는 production target 생성 문제로 후속 검증으로 분리

`DATABASE_URL`, Firebase service account JSON, Firebase ID token, Supabase password는 공개 문서, Issue, PR 본문에 남기지 않는다.
