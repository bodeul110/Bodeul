# Issue 112 관리자 role 기반 인가 기록

기준일: 2026-07-01

## 작업 목적

Firebase ID token 인증 이후 PostgreSQL `app_users.role` 기준으로 관리자 API 접근 권한을 검증한다.

## 선택한 방식

Firebase token 검증으로 얻은 `uid`를 PostgreSQL `app_users.firebase_uid`와 매핑해 role을 조회한다.

```sql
select role from app_users where firebase_uid = $1 limit 1
```

- `ADMIN` role이면 관리자 API를 허용한다.
- role이 없거나 `ADMIN`이 아니면 403을 반환한다.
- 권한 확인기가 없으면 503을 반환한다.
- role 조회 중 DB 오류가 발생하면 503을 반환한다.

## 대안

- Firebase custom claims로 관리자 여부를 판단한다.
- Firestore `users/{uid}.role`을 계속 읽는다.
- API 서버에서는 인증만 하고 관리자 앱에서 role을 판단한다.

## 선택 이유

#88 기준은 PostgreSQL 접근 경계이며, 서버 API가 PostgreSQL role 정보를 기준으로 인가하는 방향이다. 관리자 웹 레포 분리 작업과 충돌하지 않도록 `admin-web/` 코드는 수정하지 않고 API 서버 내부 인가만 고정했다.

## 리스크

- Firestore role과 PostgreSQL role 동기화가 느슨하면 관리자 접근이 잘못 차단될 수 있다.
- DB 장애와 권한 없음은 모두 접근 실패로 보이므로 응답 코드를 구분해 운영 원인 파악이 가능하게 해야 한다.
- 후속 read API에서도 같은 인가 유틸을 재사용해야 한다.

## 검증

| 항목 | 결과 |
| --- | --- |
| `npm --prefix api run check` | 통과, 테스트 38개 성공 |
| ADMIN role | 관리자 API 통과 |
| MANAGER role | 403 `admin_role_required` |
| 권한 확인기 없음 | 503 `authorization_not_configured` |
| role 조회 실패 | 503 `role_lookup_failed` |
| role 조회 SQL | `app_users.firebase_uid` 기준 확인 |
| `npm --prefix api audit --json` | moderate 6건, #110의 `firebase-admin` 하위 의존성 경고와 동일 |
| 민감값 패턴 검색 | 실제 secret 없음 |

## 남은 범위

- 실제 PostgreSQL read API의 인가 유틸 재사용
- 관리자 웹 API 호출 연결
- Firestore role과 PostgreSQL role 동기화 운영 점검
