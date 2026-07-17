# 관리자 API 초기 응답 계약

기준일: 2026-07-17

> 이 문서는 종료된 Node `bodeul-api`에서 검증한 초기 계약 기록이다. 병원 가이드의 401·403·200 계약은 별도 저장소의 Next.js `GET /admin/hospital-guides`로 이관했고 메인 Node 소스는 제거했다. 현재 경계는 [관리자 웹 구조](admin-web-architecture.md)를 따른다.

## 작업 목적

관리자 웹이 Firestore 직접 접근에서 서버 경계로 전환할 때 사용한 초기 응답 형태를 기록한다.

## 선택한 방식

첫 운영 read API는 `GET /admin/hospital-guides`로 둔다. `GET /admin/api-contract`는 Firebase ID token 검증 경로와 PostgreSQL 설정 상태 응답 형식을 확인하는 계약 API로 유지한다.

## 대안

- 병원 가이드 목록을 첫 API로 만든다.
- 매니저 서류 심사 목록을 첫 API로 만든다.
- 인증 없이 공개 계약 API로 둔다.

## 선택 이유

현재 MVP 규모에서는 매니저 심사 전체를 한 번에 API로 옮기면 검증 범위가 커진다. 병원 가이드는 개인정보 노출 위험이 낮고 `steps`가 `jsonb` 배열로 확인되어 첫 실제 read API와 관리자 웹 연결 검증 대상으로 적합하다.

## 리스크

- Firebase Admin SDK 설정이 없는 로컬/CI 환경에서는 verifier가 없으면 503을 반환한다.
- PostgreSQL `hospital_guides`와 기존 Firestore 데이터가 어긋나면 관리자 웹 전환 QA에서 차이가 발생할 수 있다.
- 관리자 웹 브라우저 호출은 CORS origin 설정이 맞지 않으면 preflight에서 차단된다.
- 기본 rollback은 `VITE_BODEUL_DATA_BACKEND=firebase`로 되돌리는 방식이다.

## 공통 인증

관리자 API는 `Authorization: Bearer <Firebase ID token>` 헤더를 사용한다.

| 실패 조건 | HTTP status | `error` |
| --- | --- | --- |
| Authorization 헤더 없음 | 401 | `missing_authorization` |
| Bearer 형식 아님 | 401 | `invalid_authorization` |
| Firebase verifier 미설정 | 503 | `auth_not_configured` |
| Firebase token 검증 실패 | 401 | `invalid_firebase_token` |
| 관리자 권한 확인기 미설정 | 503 | `authorization_not_configured` |
| PostgreSQL role이 `ADMIN`이 아님 | 403 | `admin_role_required` |
| PostgreSQL role 조회 실패 | 503 | `role_lookup_failed` |
| 병원 가이드 조회기 미설정 | 503 | `hospital_guides_not_configured` |
| 병원 가이드 조회 실패 | 503 | `hospital_guides_lookup_failed` |
| 병원 가이드 `limit` 오류 | 400 | `invalid_limit` |

Firebase Admin SDK 설정은 서버 환경변수로만 주입한다.

| 이름 | 용도 |
| --- | --- |
| `FIREBASE_PROJECT_ID` | Application Default Credentials를 사용할 때 Firebase project를 지정한다. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | 서비스 계정 JSON 문자열을 주입한다. 실제 값은 문서, Issue, PR에 적지 않는다. |

## CORS

관리자 웹이 브라우저에서 `Authorization` 헤더를 붙여 호출하므로 API 서버는 `OPTIONS` preflight를 처리한다.

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `BODEUL_API_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 관리자 웹 origin 허용 목록. 쉼표로 구분한다. |

운영/preview 배포 URL은 이 목록에 명시적으로 추가한다. origin이 맞지 않으면 `cors_origin_not_allowed` 403으로 preflight를 거부한다.

환경별 `VITE_BODEUL_DATA_BACKEND`, `VITE_BODEUL_API_BASE_URL`, `BODEUL_API_ALLOWED_ORIGINS` 기준과 rollback 절차는 [관리자 웹 API 환경변수와 CORS 기준](../operations/admin-api-environments.md)을 따른다.

## `GET /healthz`

배포와 모니터링을 위한 공개 헬스체크다.

```json
{
  "status": "ok",
  "service": "bodeul-api",
  "timestamp": "2026-06-30T00:00:00.000Z"
}
```

## `GET /admin/api-contract`

관리자 웹 초기 API 계약 확인용 read API다. 운영 데이터나 secret은 반환하지 않는다.

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

`database.status`는 다음 값을 사용한다.

| 값 | 의미 |
| --- | --- |
| `missing` | `DATABASE_URL`이 설정되지 않았다. CI와 로컬 secret 없는 검증에서 허용한다. |
| `configured` | `DATABASE_URL`이 PostgreSQL URL 형식으로 설정되어 있고 서버 내부 PostgreSQL pool 초기화 대상이다. |

서버 내부 PostgreSQL client 기준은 다음과 같다.

- `DATABASE_URL`이 없으면 pool을 만들지 않는다.
- `DATABASE_URL`이 있으면 `pg` pool을 만든다.
- pool 기본값은 `max=5`, `idleTimeoutMillis=10000`, `connectionTimeoutMillis=5000`이다.
- 서버 종료 시 HTTP 서버를 닫은 뒤 pool을 닫는다.
- 연결 확인 실패는 `db_connection_failed`로만 요약하고 connection string은 응답이나 로그에 남기지 않는다.

관리자 권한 확인은 다음 SQL 기준을 사용한다.

```sql
select role from app_users where firebase_uid = $1 limit 1
```

`role`이 `ADMIN`이면 관리자 API를 허용한다. role이 없거나 `ADMIN`이 아니면 403을 반환하고, DB 장애는 503으로 구분한다.

## `GET /admin/hospital-guides`

관리자 권한으로 PostgreSQL `hospital_guides`를 조회하는 첫 실제 read API다.

```sql
select id, hospital_name, department_name, steps, created_at, updated_at
from hospital_guides
order by updated_at desc, hospital_name asc, department_name asc
limit $1
```

요청 query:

| 이름 | 기본값 | 제한 |
| --- | --- | --- |
| `limit` | `50` | 1부터 100 사이의 정수 |

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

Supabase 조회 기준으로 `hospital_guides.steps`는 `jsonb` 배열이다. 이번 PR은 기존 스키마를 읽기만 하며 Supabase schema, seed, migration은 변경하지 않는다.

## 후속 범위

- 매니저 서류 심사, 문의 조회 등 추가 read API 확장
- 병원 가이드 외 화면의 Firestore 응답과 PostgreSQL/API 응답 비교 검증
