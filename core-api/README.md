# BoDeul Core API

환자, 보호자, 매니저 웹과 Android 앱이 공통으로 사용하는 BoDeul Core API다. Java와 Spring Boot로 구현하고 Google Cloud Run에 배포하며, Supabase PostgreSQL을 운영 데이터 저장소로 사용한다.

## 현재 범위

- Spring Boot 3.5.16
- Java 21 LTS
- Gradle Wrapper
- 공개 `GET /health`
- Firebase ID token과 PostgreSQL `app_users.role`을 연결하는 `GET /api/auth/me`
- 인증된 사용자의 병원·약국 검색을 대행하는 `GET /api/places/search`
- 명시적으로 허용하지 않은 경로는 기본 차단
- `local` profile에서는 DB 없이 기동
- `preview`, `production` profile에서는 PostgreSQL 설정 필수

기존 `api/`의 Node `bodeul-api`는 인증, 인가, PostgreSQL 계약을 검증한 prototype이다. `core-api/`는 해당 계약을 Spring으로 옮기되 Node API를 중간 서버로 호출하지 않는다.

Android, Firebase 도구, 공통 데이터 계약과 함께 변경 내용을 검토하기 위해 메인 저장소 안에서 관리한다. 배포는 저장소 구조와 별개로 Cloud Run 서비스와 `core-api-preview` GitHub Environment를 사용한다.

## 로컬 검증

```powershell
.\gradlew.bat check --console=plain
.\gradlew.bat bootRun --console=plain
```

기본 profile은 `local`이며 DB를 초기화하지 않는다.

```powershell
curl.exe http://127.0.0.1:8080/health
```

컨테이너는 Java 21 build stage와 비루트 distroless runtime으로 구성한다.

```powershell
docker build --tag bodeul-core-api:local .
docker run --rm --publish 8080:8080 bodeul-core-api:local
```

## DB profile

`preview`와 `production`은 `database` profile을 포함한다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "preview"
$env:CORE_DB_JDBC_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:CORE_DB_USERNAME = "<runtime-role>"
$env:CORE_DB_PASSWORD = "<runtime-password>"
.\gradlew.bat bootRun --console=plain
```

실제 값은 로컬 비공개 설정 또는 Google Secret Manager로만 주입한다. `.env`와 접속 문자열을 커밋하지 않는다.

## 인증과 인가

클라이언트는 `Authorization: Bearer <Firebase ID token>`을 전달한다. Core API는 Firebase Admin SDK `verifyIdToken`으로 서명, 만료, 발급 project를 확인하고 검증된 `uid`만 `bodeul.app_users.firebase_uid` 조회에 사용한다. Firebase custom claim은 서비스 역할의 최종 근거로 사용하지 않는다.

정상 인증은 `GET /api/auth/me`에서 내부 사용자 ID와 PostgreSQL 역할만 반환한다. 원본 ID token은 응답, 로그, Spring Security credentials에 보관하지 않는다.

| 상태 | HTTP | 오류 코드 |
| --- | ---: | --- |
| Authorization 누락 또는 잘못된 형식 | 401 | `missing_authorization`, `invalid_authorization` |
| 만료, 변조, 다른 Firebase project token | 401 | `invalid_firebase_token` |
| `app_users` 역할 미등록 | 403 | `role_not_found` |
| 인증됐지만 endpoint 권한 부족 | 403 | `permission_denied` |
| Firebase 또는 DB 설정 누락 | 503 | `auth_not_configured`, `authorization_not_configured` |
| PostgreSQL 역할 조회 장애 | 503 | `role_lookup_failed` |

Cloud Run에서는 전용 runtime 서비스 계정의 Application Default Credentials를 사용한다. 서비스 계정 JSON과 `GOOGLE_APPLICATION_CREDENTIALS` 파일을 만들지 않으며, `FIREBASE_PROJECT_ID`를 명시해 다른 project token을 거부한다.

첫 범위는 Firebase Admin SDK의 기본 `verifyIdToken`을 사용하므로 token 폐기 여부를 추가 조회하지 않는다. ID token 만료 전 즉시 차단이 필요하면 PostgreSQL 역할을 제거하고, 계정 폐기 확인을 매 요청에 적용할지는 네트워크 비용과 캐시 전략을 정한 뒤 별도 반영한다.

## Kakao Local 장소 검색

`GET /api/places/search`는 `query`와 `category=HOSPITAL|PHARMACY`를 받고 Kakao Local 결과 중 이름과 좌표만 반환한다. Firebase 인증과 PostgreSQL 역할 확인을 통과해야 하며, 사용자별 분당 60회 제한과 6시간·최대 1,000건 서버 캐시를 적용한다.

로컬 또는 배포 환경에는 다음 값을 비공개 경로로 주입한다.

```powershell
$env:KAKAO_LOCAL_REST_API_KEY = "<Kakao REST API key>"
```

Cloud Run에서는 `bodeul-core-api-preview-kakao-local-rest-api-key` Secret Manager secret을 사용한다. 키 값과 Kakao 원본 오류 본문은 응답이나 로그에 남기지 않는다. 자세한 계약과 확장 조건은 [Kakao Local Core API 경계](../docs/architecture/kakao-local-core-api.md)를 따른다.

## 연결 원칙

- Cloud Run은 IPv4가 가능한 Supabase Supavisor session mode의 5432 포트를 우선 사용한다.
- Vercel 관리자 서버는 Supavisor transaction mode의 6543 포트를 사용한다.
- migration 계정과 runtime 계정을 분리한다.
- application pool은 최대 5개 연결로 시작한다.
- Firebase ID token 검증 후 PostgreSQL role과 리소스 소유권을 확인한다.
- Kakao Local REST와 알림톡처럼 서버 key가 필요한 연동은 이 API 뒤에 둔다.

## DB 권한 bootstrap

`db/bootstrap/001_database_access.sql`은 다음 기반만 만든다.

- Data API에 노출하지 않는 `bodeul` schema
- `bodeul_migration`, `bodeul_core_runtime`, `bodeul_admin_runtime` 권한 role
- 비밀번호 설정 전까지 접속할 수 없는 `bodeul_migrator`, `bodeul_core_service`, `bodeul_admin_service` role
- runtime role의 DDL 차단과 최대 연결 수 제한
- `public` schema 신규 객체의 Data API 자동 노출 차단

bootstrap은 개발 DB에서 `postgres` 권한으로 먼저 적용한다. 비밀번호는 SQL 파일에 추가하지 않고 보안 경로에서 별도로 설정한 뒤 각 로그인 role을 활성화한다.

Flyway는 runtime profile에서 실행하지 않는다. migration 전용 자격 증명을 준비한 환경에서만 다음처럼 실행한다. migration profile은 연결 직후 `SET ROLE bodeul_migration`을 실행해 history와 업무 객체의 소유자를 로그인 계정이 아닌 migration 권한 role로 통일한다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "migration"
$env:MIGRATION_DB_JDBC_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:MIGRATION_DB_USERNAME = "bodeul_migrator"
$env:MIGRATION_DB_PASSWORD = "<migration-password>"
.\gradlew.bat migrateDatabase --console=plain
```

runtime 서버에는 `MIGRATION_DB_*` 값을 주입하지 않는다.
GitHub에서는 `Core API DB Migration` workflow를 수동 실행하고 대상 Environment의 승인을 거친다.

## 다음 작업

1. Android의 Core API 장소 검색과 로컬 병원 목록 fallback을 실기기에서 검증
2. Firestore 직접 접근 도메인의 단계별 PostgreSQL 이관

## 보안

취약점은 공개 이슈에 실제 공격 정보나 secret을 적지 말고 메인 저장소의 private vulnerability reporting 경로로 제보한다.
