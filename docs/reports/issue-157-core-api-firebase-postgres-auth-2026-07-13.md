# Issue 157 Spring Firebase/PostgreSQL 인가 이관 기록

기준일: 2026-07-13

실환경 검증 갱신: 2026-07-16

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다. 현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Firebase Auth를 유지하면서 사용자 서비스 요청의 인증과 역할 판정을 Spring Core API 경계로 옮긴다. 클라이언트나 Firebase custom claim이 서비스 역할을 결정하지 않게 하고, 검증된 Firebase UID와 PostgreSQL 역할을 연결한다.

## 선택한 방식

- Firebase Admin Java SDK `9.10.0`의 `verifyIdToken`을 사용한다.
- `FIREBASE_PROJECT_ID`를 명시하고 Cloud Run에서는 전용 runtime 서비스 계정의 ADC를 사용한다. 로컬 검증에서만 ADC 표준 경로를 사용한다.
- 검증된 UID만 `bodeul.app_users.firebase_uid` 조회에 사용한다.
- Spring Security authority는 PostgreSQL의 `PATIENT`, `GUARDIAN`, `MANAGER`, `ADMIN` 역할에서 만든다.
- 원본 token은 SecurityContext credentials, 응답, 로그에 보관하지 않는다.
- 서버 전용 `bodeul.app_users`는 private schema, runtime SELECT grant, RLS SELECT policy를 함께 사용한다.

## 대안

| 대안 | 제외 이유 |
| --- | --- |
| Firebase custom claim을 최종 역할로 사용 | token이 갱신되기 전까지 역할 변경이 반영되지 않고 PostgreSQL 운영 권한과 기준이 갈라진다. |
| Android 앱이 Supabase를 직접 조회 | DB 자격 증명과 인가 정책이 클라이언트까지 확장되어 서버 경계가 약해진다. |
| 기존 Node API를 Spring이 다시 호출 | 서버에서 서버를 거치는 중복 경로가 생기고 목표 구조와 맞지 않는다. |
| 서비스 계정 JSON 원문을 애플리케이션 환경변수나 파일로 배포 | 회전과 원문 노출 범위가 넓어진다. Cloud Run runtime 서비스 계정 ADC를 사용한다. |

## 선택 이유

현재 MVP 규모에서는 Firebase 로그인과 PostgreSQL 운영 역할을 한 번에 교체할 필요가 없다. Firebase는 신원 확인을 담당하고 PostgreSQL은 서비스 권한을 담당하게 나누면 기존 앱 로그인을 유지하면서도 서버 중심 인가로 전환할 수 있다. 역할 조회는 요청마다 단일 UID 인덱스를 사용하므로 첫 범위의 복잡도도 낮다.

## 구현한 내용

- `GET /api/auth/me` 인증 사용자 확인 endpoint
- Bearer token 단일 헤더 파싱과 중복 헤더 거부
- Firebase Admin SDK 초기화 및 UID 검증 adapter
- `app_users` JDBC repository와 Spring Security authority 연결
- 401, 403, 503 오류 계약 분리
- `V1__create_app_users.sql`과 수동 rollback SQL
- Core/Admin runtime SELECT grant와 RLS policy

## 검증

| 항목 | 결과 |
| --- | --- |
| Core API 전체 테스트 | `gradlew check` 성공, 23개 테스트 실패 0 |
| Firebase Admin 의존성 | runtime classpath에서 `9.10.0` 확인 |
| 정상 UID와 역할 연결 | 단위/통합 테스트와 Cloud Run 실제 token 검증 통과 |
| 변조 token | 자동 테스트와 Cloud Run 실제 token 검증에서 401 확인 |
| 만료, 다른 project token | Firebase 검증 실패를 401로 변환하고 원문을 숨기는 자동 테스트 통과 |
| 역할 없음 | 403 `role_not_found` 확인 |
| 권한 없음 | 403 `permission_denied` 확인 |
| DB 장애 | 503 `role_lookup_failed` 확인 |
| 서비스 계정 초기화 오류 | 고정 로그와 503만 사용하고 자격 증명 원문 미포함 확인 |
| 개발 DB migration | [preview 실행](https://github.com/bodeul110/Bodeul/actions/runs/29226479527) 성공 |
| 실제 Firebase token | 개발용 일회성 사용자를 생성해 역할 미등록 403, 역할 연결 200, 정리 후 403 확인 |
| 비밀값 저장 경로 | GitHub Environment의 중복 DB secret 3개 제거 후 Secret Manager만으로 재배포 성공 |

만료와 다른 project token 테스트는 Firebase Admin adapter가 검증 실패를 반환한 이후의 API 응답 계약을 확인한 것이다. 실제 만료 token이나 다른 project의 정상 서명 token을 사용한 검증으로 과장하지 않는다. 변조 token은 2026-07-16 Cloud Run에서 실제로 추가 검증했다.

### Cloud Run 실제 token 검증 결과

- 대상: `bodeul-dev`, `asia-northeast1`, `bodeul-core-api-preview`
- 검증 endpoint: `GET /api/auth/me`
- Firebase Auth에 개발용 일회성 사용자를 만들고 API key, 비밀번호, ID token은 파일이나 출력에 남기지 않고 단일 프로세스 메모리에서만 사용했다.
- 같은 실제 ID token으로 `app_users` 역할 행 추가 전 HTTP 403, `PATIENT` 역할 연결 후 HTTP 200과 발급된 사용자 UUID, 역할 행 삭제 후 다시 HTTP 403을 확인했다.
- 실제 ID token의 서명을 변경한 token은 HTTP 401을 반환했다.
- 검증 후 `app_users` 임시 행과 Firebase Auth 테스트 사용자를 삭제했다. 후속 조회에서 `app_users` 전체 0행, 테스트 UID 0행을 확인했다.
- 최근 Cloud Run 로그 13건에서 Authorization 헤더, Bearer token, JWT 형태 문자열, 테스트 UID와 이메일 표식, Firebase API key, ID/refresh token 필드가 모두 0건임을 확인했다.
- `bodeul` 스키마의 `PUBLIC`, `anon`, `authenticated`, `service_role` table grant는 0건이고 Supabase Security Advisor도 0건이다.
- GitHub Environment의 `CORE_DB_JDBC_URL`, `CORE_DB_USERNAME`, `CORE_DB_PASSWORD`를 제거하고 Secret Manager 버전 변수만 남겼다. Action 런타임 갱신까지 반영한 [최종 재배포](https://github.com/bodeul110/Bodeul/actions/runs/29479781864)가 성공했으며, `bodeul-core-api-preview-00005-2fg` 리비전이 트래픽 100%를 처리하면서 health, liveness, readiness 200과 무인증 요청 401을 반환한다.

### 개발 DB 적용 결과

- 첫 실행은 비웹 migration 컨텍스트에 Firebase 인증 필터가 등록돼 시작 단계에서 실패했다. [PR #168](https://github.com/bodeul110/Bodeul/pull/168)에서 필터를 servlet 전용으로 제한하고 비웹 컨텍스트 회귀 테스트를 추가한 뒤 재실행했다.
- `bodeul.app_users` 소유자는 `bodeul_migration`이고 RLS가 활성화됐다.
- `bodeul_core_runtime`, `bodeul_admin_runtime`은 SELECT만 가능하며 INSERT, UPDATE, DELETE는 불가능하다.
- `anon`, `authenticated`, `service_role`은 schema와 table에 접근할 수 없다.
- Core/Admin SELECT policy, Firebase UID unique constraint, 서비스 역할 check constraint를 확인했다.
- Flyway 이력에는 V1 성공과 `installed_by=bodeul_migration`이 기록됐다.
- Supabase Security Advisor는 0건이다. Performance Advisor의 유일한 INFO는 아직 사용 이력이 없는 Flyway history 인덱스이며 migration 잠금과 이력 조회에 필요한 관리 인덱스이므로 유지한다. [Advisor 기준](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index)

## 리스크

- 기본 `verifyIdToken`은 token 폐기 여부를 추가 조회하지 않는다. 즉시 차단은 PostgreSQL 역할 제거를 우선 사용하고, 폐기 확인 옵션은 지연 시간과 요청량을 측정한 뒤 결정한다.
- 첫 migration은 인증에 필요한 최소 필드만 만든다. 이름, 연락처, 매니저 심사 정보는 해당 도메인을 이관할 때 별도 migration으로 추가한다.
- 실제 만료 token은 발급 후 1시간이 지나야 하며, 현재 계정에서 접근 가능한 Firebase project는 `bodeul-dev` 하나뿐이다. 따라서 만료 token과 다른 project의 정상 서명 token에 대한 실환경 재현은 즉시 수행하지 않았고 자동 테스트로 응답 계약을 유지한다. 향후 별도 비운영 Firebase project를 만들거나 장시간 인증 리허설을 운영할 때 보강한다.
- production DB와 production secret에는 이 작업을 적용하지 않는다.

## 참고

- [Firebase Admin SDK 서버 설정](https://firebase.google.com/docs/admin/setup)
- [Firebase ID token 검증](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Supabase API 보안 기준](https://supabase.com/docs/guides/api/securing-your-api)
