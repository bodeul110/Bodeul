# Spring Core API 인프라 런북

기준일: 2026-07-12

이 문서는 `bodeul-core-api`를 OCI에 배포하고 Supabase PostgreSQL, Firebase Auth, Kakao 서버 API를 연결하기 위한 구축 순서를 정한다. 실제 secret 값은 문서와 공개 GitHub 대화에 남기지 않는다.

## 현재 상태

- [`bodeul110/bodeul-core-api`](https://github.com/bodeul110/bodeul-core-api) 저장소를 생성했다.
- Java 21, Spring Boot 3.5.16, Gradle Wrapper, `/healthz`, local/database profile, CI 초기 구성을 반영했다.
- 로컬 `./gradlew check`와 GitHub `Core API CI`가 통과했다.
- 기존 `api/` Node.js 서버는 Oracle/Supabase/Firebase Admin preview 검증에 사용됐다.
- 기존 Oracle VM을 Spring preview에 재사용할 수 있는지는 점검 전이다.
- production 도메인과 HTTPS endpoint는 아직 없다.

## 1. 저장소와 런타임

| 항목 | 기준 |
| --- | --- |
| 저장소 | `bodeul110/bodeul-core-api` |
| 언어 | Java |
| Java | LTS 버전. Initializr 생성 시점의 Spring Boot 지원 범위를 공식 문서로 확인한다. |
| 프레임워크 | Spring Boot |
| 빌드 | Gradle Wrapper |
| 기본 package | `com.bodeul.core` |
| health endpoint | `GET /healthz` |
| 운영 profile | `preview`, `production` |

초기 의존성은 Web, Validation, Actuator, Security, JDBC, PostgreSQL driver, Flyway, Firebase Admin으로 제한한다. ORM은 실제 aggregate와 query 패턴이 정리된 뒤 결정한다.

## 2. Supabase 연결

세 종류의 DB role을 분리한다.

| role | 용도 | 권한 기준 |
| --- | --- | --- |
| migration | Flyway와 schema 변경 | DDL 가능, 앱 런타임에서 사용 금지 |
| core runtime | 예약, 매칭, 세션, 리포트 | 필요한 테이블의 DML만 허용 |
| admin runtime | 관리자 조회와 운영 처리 | 관리자 기능에 필요한 DML만 허용 |

연결 기준:

- OCI는 장기 실행 서버이므로 direct connection을 우선한다.
- OCI 네트워크가 IPv4-only이면 Supavisor session mode를 사용한다.
- Vercel Next.js는 Supavisor transaction mode를 사용한다.
- runtime과 migration connection string을 분리한다.
- SSL을 강제하고 connection string을 로그에 출력하지 않는다.

## 3. Firebase 인증

1. 클라이언트가 Firebase Auth로 로그인한다.
2. `Authorization: Bearer <Firebase ID token>`을 Core API에 전달한다.
3. Core API가 Firebase Admin SDK로 token을 검증한다.
4. token의 `uid`로 PostgreSQL `app_users.firebase_uid`를 조회한다.
5. API별 role과 resource ownership을 확인한다.

서비스 계정 JSON 파일을 서버 디스크와 저장소에 고정하지 않는다. OCI workload에서 사용할 자격 증명 주입 방식은 GitHub Environment와 서버 secret 파일 중 하나로 고정하고, 파일 권한과 rotation 절차를 함께 검증한다.

## 4. OCI preview

기존 VM을 재사용하기 전에 아래를 확인한다.

- VM 이름, 리전, shape, OS와 보안 업데이트 상태
- 현재 Node API process와 열린 port
- systemd unit, 실행 사용자, 작업 디렉터리
- OCI Network Security Group과 Ubuntu firewall 규칙
- Java LTS 설치 여부
- 디스크 여유 공간과 로그 보존 설정
- HTTPS endpoint와 인증서 발급 가능 여부

Spring preview 기준:

| 항목 | 값 |
| --- | --- |
| systemd unit | `bodeul-core-api-preview.service` |
| 실행 사용자 | 로그인 계정과 분리한 `bodeul-api` 시스템 사용자 |
| 내부 port | `8080` 후보. 기존 process와 충돌하면 전환 전까지 별도 port 사용 |
| 외부 접근 | reverse proxy의 HTTPS만 허용 |
| health check | `/healthz` |
| 로그 | journald, secret과 token 원문 기록 금지 |

raw IP의 HTTP endpoint는 production으로 사용하지 않는다. 도메인이 없으면 preview 접근만 제한적으로 허용하고 production 공개는 보류한다.

## 5. GitHub Environment

### `core-api-preview`

Variables 후보:

- `OCI_REGION`
- `CORE_API_DEPLOY_HOST`
- `CORE_API_DEPLOY_USER`
- `CORE_API_SERVICE_NAME`
- `FIREBASE_PROJECT_ID`

Secrets 후보:

- `OCI_DEPLOY_SSH_KEY`
- `CORE_DATABASE_URL`
- `MIGRATION_DATABASE_URL`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `KAKAO_REST_API_KEY`
- 알림톡 provider를 확정한 뒤 필요한 provider secret

Repository secret과 Environment secret을 중복 생성하지 않는다. preview와 production은 별도 자격 증명과 DB를 사용한다.

## 6. CI와 배포

PR CI:

1. Gradle Wrapper 검증
2. compile
3. unit test
4. static analysis
5. secret scan

preview 배포:

1. `master` 또는 승인된 수동 workflow에서 artifact 생성
2. GitHub OIDC 또는 제한된 SSH 자격 증명으로 OCI에 전달
3. 새 artifact와 환경 파일 권한 검증
4. systemd restart
5. `/healthz` 확인
6. 인증이 필요한 smoke test 실행
7. 실패 시 직전 artifact로 rollback

production 자동 배포는 preview와 rollback 리허설이 끝날 때까지 만들지 않는다.

## 7. 첫 API 범위

1. `GET /healthz`
2. Firebase ID token 검증 endpoint
3. PostgreSQL role 조회
4. Kakao Local keyword proxy
5. Android 병원 검색 한 화면 전환

관리자 Next.js 전환과 Spring Core API 첫 배포를 같은 PR이나 같은 cutover로 묶지 않는다.

## 8. 검증 기록

아래 결과를 `docs/reports/` 또는 Core API 저장소의 운영 문서에 남긴다.

- 배포 일시와 commit SHA
- health check 결과
- Firebase token 정상/만료/잘못된 token 응답
- DB 연결과 role 인가 결과
- Kakao proxy 정상/429/timeout/fallback 결과
- 재시작 후 자동 기동 결과
- rollback 소요 시간과 결과

## 중단 조건

- secret이 로그, artifact, PR에 노출됨
- OCI가 raw HTTP로만 외부 공개됨
- migration과 runtime이 같은 DB owner 자격 증명을 사용함
- Node API와 Spring API가 같은 요청을 연쇄 호출함
- Firestore와 PostgreSQL 양쪽에 운영 쓰기를 하면서 source of truth가 정해지지 않음
