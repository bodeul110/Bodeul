# BoDeul Core API

환자, 보호자, 매니저 웹과 Android 앱이 공통으로 사용하는 BoDeul Core API다. Java와 Spring Boot로 구현하고 Oracle Cloud에 배포하며, Supabase PostgreSQL을 운영 데이터 저장소로 사용한다.

## 현재 범위

- Spring Boot 3.5.16
- Java 21 LTS
- Gradle Wrapper
- 공개 `GET /healthz`
- 그 외 요청은 인증 구현 전까지 기본 차단
- `local` profile에서는 DB 없이 기동
- `preview`, `production` profile에서는 PostgreSQL 설정 필수

기존 `api/`의 Node `bodeul-api`는 인증, 인가, PostgreSQL 계약을 검증한 prototype이다. `core-api/`는 해당 계약을 Spring으로 옮기되 Node API를 중간 서버로 호출하지 않는다.

Android, Firebase 도구, 공통 데이터 계약과 함께 변경 내용을 검토하기 위해 메인 저장소 안에서 관리한다. 배포는 저장소 구조와 별개로 OCI의 독립 systemd 서비스와 GitHub Environment를 사용한다.

## 로컬 검증

```powershell
.\gradlew.bat check --console=plain
.\gradlew.bat bootRun --console=plain
```

기본 profile은 `local`이며 DB를 초기화하지 않는다.

```powershell
curl.exe http://127.0.0.1:8080/healthz
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

실제 값은 로컬 비공개 설정, OCI 서버 환경 파일 또는 GitHub Environment secret으로만 주입한다. `.env`와 접속 문자열을 커밋하지 않는다.

## 연결 원칙

- OCI처럼 장기 실행되는 서버는 direct connection을 우선 사용한다.
- OCI가 IPv4-only이면 Supabase Supavisor session mode의 5432 포트를 사용한다.
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

1. Firebase Admin SDK ID token 검증
2. PostgreSQL `app_users` role 인가
3. Flyway migration 실행 경로와 migration 전용 role
4. OCI preview systemd 배포
5. Kakao Local REST proxy

## 보안

취약점은 공개 이슈에 실제 공격 정보나 secret을 적지 말고 메인 저장소의 private vulnerability reporting 경로로 제보한다.
