# 관리자 API 초기 응답 계약

기준일: 2026-06-30

## 작업 목적

관리자 웹이 Firestore 직접 접근에서 `bodeul-api` 경계로 전환될 때 사용할 초기 응답 형태를 고정한다.

## 선택한 방식

첫 read API는 운영 데이터를 반환하지 않는 `GET /admin/api-contract`로 둔다. 이 엔드포인트는 Firebase ID token 검증 경로와 PostgreSQL 설정 상태 응답 형식을 먼저 고정한다.

## 대안

- 병원 가이드 목록을 첫 API로 만든다.
- 매니저 서류 심사 목록을 첫 API로 만든다.
- 인증 없이 공개 계약 API로 둔다.

## 선택 이유

현재 MVP 규모에서는 PostgreSQL query와 관리자 화면 전환을 한 PR에 묶으면 검증 범위가 커진다. 계약 확인 API를 먼저 두면 관리자 웹이 기대할 공통 응답, 인증 실패, DB 설정 상태를 낮은 위험으로 검증할 수 있다.

## 리스크

- 실제 운영 데이터 조회 API가 아니므로 관리자 웹 전환 자체를 완료하지는 못한다.
- Firebase Admin SDK 설정이 없는 로컬/CI 환경에서는 verifier가 없으면 503을 반환한다.
- PostgreSQL role 기반 인가는 후속 API에서 별도로 구현해야 한다.

## 공통 인증

관리자 API는 `Authorization: Bearer <Firebase ID token>` 헤더를 사용한다.

| 실패 조건 | HTTP status | `error` |
| --- | --- | --- |
| Authorization 헤더 없음 | 401 | `missing_authorization` |
| Bearer 형식 아님 | 401 | `invalid_authorization` |
| Firebase verifier 미설정 | 503 | `auth_not_configured` |
| Firebase token 검증 실패 | 401 | `invalid_firebase_token` |

Firebase Admin SDK 설정은 서버 환경변수로만 주입한다.

| 이름 | 용도 |
| --- | --- |
| `FIREBASE_PROJECT_ID` | Application Default Credentials를 사용할 때 Firebase project를 지정한다. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | 서비스 계정 JSON 문자열을 주입한다. 실제 값은 문서, Issue, PR에 적지 않는다. |

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
  "version": "2026-06-30",
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

## 후속 범위

- PostgreSQL `app_users.role` 기반 관리자 권한 확인
- 병원 가이드, 매니저 서류 심사, 문의 조회 중 하나를 실제 read API로 승격
- 관리자 웹의 `VITE_BODEUL_DATA_BACKEND=api` 전환
