# bodeul-api

`bodeul-api`는 Supabase PostgreSQL 접근, Firebase ID token 검증, 관리자/민감 쓰기 검증을 담당할 얇은 API 서버다. Firebase Auth, FCM, Storage, Hosting을 대체하지 않는다.

## 현재 범위

Issue #88 1차 범위는 서버 골격, 헬스체크, Firebase ID token 검증 경계 초안, `DATABASE_URL` 설정 검증, 관리자 웹 초기 계약 확인 API를 포함한다.

- Node 22
- TypeScript
- Node 기본 `http` 서버
- `GET /healthz`
- `GET /admin/api-contract`
- Firebase ID token 검증 유틸과 관리자 API 연결
- `DATABASE_URL` 누락/형식 검증
- 로컬 build/typecheck/test 스크립트

Firebase Admin SDK 실제 초기화, PostgreSQL client 초기화, 운영 데이터 조회 API는 후속 PR에서 추가한다.

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

현재 구현은 Firebase ID token verifier 주입 경로를 고정하기 위한 초안이다. 로컬 실행 서버에는 아직 Firebase Admin SDK가 연결되어 있지 않으므로 실제 토큰 검증은 후속 범위다.

응답 예시:

```json
{
  "status": "ok",
  "service": "bodeul-api",
  "resource": "admin-api-contract",
  "version": "2026-06-30",
  "timestamp": "2026-06-30T00:00:00.000Z",
  "database": {
    "status": "missing"
  },
  "authentication": {
    "type": "firebase_id_token",
    "status": "draft"
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
    }
  ]
}
```

## 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `BODEUL_API_HOST` | `127.0.0.1` | 로컬 서버 바인딩 host |
| `BODEUL_API_PORT` | `8080` | 로컬 서버 port |
| `DATABASE_URL` | 없음 | PostgreSQL connection string. 누락은 `missing` 상태로 처리하고, 값이 있으면 `postgres` 또는 `postgresql` URL 형식만 허용한다. |

## 보류 범위

- PostgreSQL client 초기화와 실제 query
- Firebase Admin SDK 초기화
- PostgreSQL role 기반 관리자 권한 확인
- 관리자 웹/Android 연동
- Oracle VM 배포
