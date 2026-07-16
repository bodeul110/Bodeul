# PostgreSQL 운영 전환 런북

기준일: 2026-07-16

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Firebase 인프라는 유지하면서 운영 DB를 Supabase PostgreSQL로 옮기기 위해 인프라 담당자가 만들어야 하는 외부 리소스, GitHub 설정, 초기 검증 순서를 고정한다. 2026-07-10 기준 Oracle Cloud API 서버는 #140의 preview 실연동 검증에서 사용됐지만, production 서버나 live 전환 기준은 아니다.

2026-07-12 멘토링 이후 운영 목표는 Next.js 관리자 서버와 Spring Core API가 공용 PostgreSQL에 각각 접근하는 구조로 갱신됐다. 이 문서의 Node `api/` 절차는 이미 수행한 전환 검증 기록으로 유지한다. 새 Core API 구축은 [Spring Core API 인프라 런북](core-api-infrastructure-runbook.md)을 따른다.

## 인프라 담당자가 생성할 리소스

### Supabase

| 항목 | 값 |
| --- | --- |
| 조직 또는 팀 이름 | `bodeul` |
| 개발 프로젝트 | `bodeul-dev-rdb` |
| 운영 프로젝트 | `bodeul-prod-rdb` |
| DB 엔진 | PostgreSQL |
| 개발 리전 | `ap-northeast-2` |
| 운영 리전 | 개발 리허설 후 한국 또는 가장 가까운 Asia Pacific 리전으로 결정 |
| 초기 schema | `public` |
| 초기 테이블 접두어 | 없음. PostgreSQL 표준 snake_case 테이블명 사용 |

생성 순서:

1. 먼저 `bodeul-dev-rdb`만 만든다.
2. Firestore 백업 import, schema 검증, 비교 리포트가 끝난 뒤 `bodeul-prod-rdb`를 만든다.
3. 운영 프로젝트에는 테스트 데이터를 넣지 않는다.

2026-06-29 기준 `bodeul-dev-rdb`는 생성됐고 schema 적용, seed 적용, row count/FK/주요 필드 spot check까지 완료됐다.

### Core API 실행 환경

Oracle Free Tier VM은 #140의 Node API 계약 검증에 사용한 과거 preview 자산이다. `/healthz`, Supabase 조회, Firebase Admin 인증과 병원 가이드 응답 비교 결과는 보존하지만 Spring production 목표로 확장하지 않는다.

현재 Spring Core API 개발 실행 환경은 Google Cloud Run이다.

| 항목 | 개발 기준 |
| --- | --- |
| Google Cloud project | `bodeul-dev` |
| Cloud Run 서비스 | `bodeul-core-api-preview` |
| 리전 | `asia-northeast1` |
| 컨테이너 | Java 21, 비루트 distroless runtime |
| DB 연결 | Supavisor session mode, pool max 5 |
| 배포 인증 | GitHub OIDC + WIF |
| runtime secret | Google Secret Manager |

상세 생성, 배포, rollback 절차는 [Spring Core API Cloud Run 인프라 런북](core-api-infrastructure-runbook.md)을 따른다.

## GitHub 설정 이름

### Environment 후보

| Environment | 용도 | 생성 시점 |
| --- | --- | --- |
| `core-api-preview` | Cloud Run 개발 Core API 배포/검증 | 생성 완료 |
| `core-api-production` | 운영 Core API 배포 | 운영 project와 비용·도메인 결정 후 |

DB schema migration은 `core-api-migration-preview` Environment로 분리한다. Runtime DB 값은 Google Secret Manager에 두고 `core-api-preview`에는 WIF와 서비스 식별용 Variables만 둔다.

### `core-api-preview` Variables

| 이름 | 값 기준 |
| --- | --- |
| `GCP_PROJECT_ID` | `bodeul-dev` |
| `GCP_REGION` | `asia-northeast1` |
| `CLOUD_RUN_SERVICE` | `bodeul-core-api-preview` |
| `CLOUD_RUN_ARTIFACT_REPOSITORY` | `bodeul-core-api` |
| `CLOUD_RUN_WORKLOAD_IDENTITY_PROVIDER` | `github-actions/bodeul-core-api-preview` provider resource name |
| `CLOUD_RUN_DEPLOY_SERVICE_ACCOUNT` | preview deployer 서비스 계정 email |
| `CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT` | preview runtime 서비스 계정 email |
| `CORE_DB_JDBC_URL_SECRET_VERSION` | JDBC URL secret의 배포 대상 숫자 version |
| `CORE_DB_USERNAME_SECRET_VERSION` | DB username secret의 배포 대상 숫자 version |
| `CORE_DB_PASSWORD_SECRET_VERSION` | DB password secret의 배포 대상 숫자 version |
| `FIREBASE_PROJECT_ID` | `bodeul-dev` |

### Secret Manager

| 이름 | 값 기준 |
| --- | --- |
| `bodeul-core-api-preview-db-jdbc-url` | Supabase 개발 DB JDBC connection string |
| `bodeul-core-api-preview-db-username` | Core runtime 로그인 role |
| `bodeul-core-api-preview-db-password` | Core runtime 비밀번호 |

주의:

- secret 값은 문서, Issue, PR 본문에 적지 않는다.
- DB 연결값은 서버 전용이다. Android 앱이나 관리자 웹에 노출하지 않는다.
- Supabase service role key는 기본값으로 만들지 않는다. 필요할 때 별도 근거를 남기고 추가한다.
- Firebase Admin은 Cloud Run runtime 서비스 계정 ADC를 사용하며 JSON key를 만들지 않는다.

## 기존 Node API 검증 기준

| 항목 | 결정 |
| --- | --- |
| 디렉터리 후보 | `api/` |
| 런타임 | Node 22 |
| 언어 후보 | TypeScript |
| 서버 프레임워크 후보 | 1차는 Node 기본 `http`, 라우팅 확장 시 Fastify 검토 |
| DB 접근 후보 | Drizzle ORM 또는 node-postgres |
| 인증 | Firebase ID token 검증 |
| 권한 | PostgreSQL `app_users.role` 기준 |
| 헬스체크 | `GET /healthz` |

API 서버는 Firebase 전체 대체 서버가 아니다. PostgreSQL 접근, 서버 검증이 필요한 쓰기 작업, 관리자 권한 검증을 담당하는 얇은 경계로 시작한다.

이 Node API는 Spring Core API와 Next.js 관리자 서버가 같은 인증·인가·DB 계약을 구현할 때 참고하는 프로토타입이다. production 목표 런타임으로 확장하지 않고, 새 서버의 parity 검증 후 종료한다.

세부 경계는 [PostgreSQL API 경계 기준](../architecture/postgres-api-boundary.md)을 따른다.

첫 API 후보:

| 후보 | 목적 |
| --- | --- |
| `GET /healthz` | 배포와 모니터링 최소 기준 |
| `GET /admin/hospital-guides` | 낮은 위험의 관리자 read API 검증 |
| `GET /admin/manager-document-reviews` | 매니저 서류 심사 메타데이터 조회 검증 |
| `GET /admin/support-requests` | 문의 통합 테이블 조회 검증 |

write API는 read API의 인증, 권한, row 비교가 통과한 뒤 별도 PR에서 진행한다.

## 초기 검증 범위

1. PostgreSQL schema 초안 보완
2. Firestore 백업 JSON을 PostgreSQL seed 입력으로 변환하는 dry-run
3. `bodeul-dev-rdb`에 seed 적용
4. Firestore와 PostgreSQL의 row count 비교
5. 주요 도메인별 필드 누락 비교
6. 관리자 웹에서 먼저 전환할 후보 도메인 선정

2026-06-29 기준 1~5번은 완료됐다. 6번은 첫 API 후보를 병원 가이드, 매니저 서류 심사 메타데이터, 문의 조회로 좁힌 상태다.

초기 범위에서 제외:

- 예약 생성/취소
- 실시간 위치
- FCM 발송 큐
- 결제/정산 확정 처리
- 운영 API VM 배포

이유:

- DB 전환 기준을 검증하기 전에 서버 배포와 클라이언트 전환까지 동시에 진행하면 rollback이 어렵다.
- 관리자 서류 심사와 병원 가이드는 운영 영향이 있지만 실시간 위치나 예약 상태 전이보다 rollback이 쉽다.

## 마이그레이션 검증 순서

1. Firestore 백업 생성
   - `npm --prefix tools/firebase run backup`
   - 실제 명령은 현재 `tools/firebase` 스크립트 기준으로 확인한다.
2. 백업 JSON을 PostgreSQL seed 입력으로 변환하는 dry-run 실행
3. `bodeul-dev-rdb`에 seed 적용
4. Firestore와 PostgreSQL의 row count 비교
5. 관리자 서류 심사 목록 또는 병원 가이드 조회 결과 비교
6. 전환 후보 도메인의 source of truth 변경 조건 기록
7. API 서버가 필요한 경우 `api/` 골격과 `GET /healthz`를 추가한다.
8. #140/#123처럼 preview API 응답 JSON을 저장하고 Firestore/API 비교를 마무리한다.
9. production 전환은 #134의 production 기준 확정 후 별도 이슈로 준비한다.

Issue #87 실행 결과:

| 항목 | 상태 |
| --- | --- |
| Supabase 개발 DB 생성 | 완료 |
| schema 적용 | 완료 |
| 실제 Firestore 백업 검증 | 오류 0건, 경고 0건 |
| seed 입력 JSON 생성 | 완료 |
| rollback SQL 검증 | 완료 |
| 적용 SQL 실행 | 완료 |
| row count 비교 | 일치 |
| FK spot check | 누락 0건 |
| 주요 필드 spot check | 통과 |

상세 결과는 [PostgreSQL seed dry-run 기준 기록](../reports/postgres-seed-dry-run-plan-2026-06-29.md)에 둔다.

## 전환 플래그

| 이름 | 값 | 의미 |
| --- | --- | --- |
| `BODEUL_DATA_BACKEND` | `firebase` | 기존 Firestore 직접 접근 |
| `BODEUL_DATA_BACKEND` | `api` | API 서버 접근 |

관리자 웹과 Android 앱 모두 같은 개념을 쓰되, 실제 환경변수 이름은 플랫폼 규칙에 맞춘다.

- 관리자 웹: `VITE_BODEUL_DATA_BACKEND`
- Android: `BuildConfig.BODEUL_DATA_BACKEND`

## Rollback 기준

아래 중 하나라도 발생하면 해당 도메인의 source of truth를 Firestore로 되돌린다.

- PostgreSQL seed/import 결과가 Firestore 기준과 불일치
- 관리자 승인/반려 기록이 누락되거나 중복 저장
- Firebase Auth token 검증 실패율 증가
- API 서버가 15분 이상 장애
- 실시간 알림 또는 관리자 피드 누락

Rollback 방식:

1. 클라이언트 플래그를 `firebase`로 되돌린다.
2. PostgreSQL 쓰기를 중지한다.
3. Firestore 백업과 운영 상태를 다시 비교한다.
4. 원인을 문서화한 뒤 같은 범위를 재시도한다.

## 완료 조건

운영 DB 전환 완료는 아래 조건을 모두 만족할 때로 본다.

- `bodeul-prod-rdb`가 전환된 도메인의 운영 source of truth로 사용된다.
- 관리자 웹의 핵심 운영 조회/쓰기 기능이 PostgreSQL/API 경계를 사용한다.
- Android 앱의 예약/세션 핵심 흐름이 PostgreSQL/API 경계를 사용한다.
- Firestore 백업/복원과 PostgreSQL 백업/복원 리허설 결과가 모두 문서화되어 있다.
- Firebase는 Auth, FCM, Storage, Hosting 등 유지하기로 결정한 역할을 계속 맡는다.
- Firestore는 전환 완료 도메인에서 legacy read-only, 캐시, shadow, 백업 용도로만 남는다.

## 인프라 담당자에게 넘길 작업

1. 완료: Supabase 계정을 만들고 `bodeul-dev-rdb` 프로젝트를 생성한다.
2. 완료: 프로젝트 이름과 리전만 팀에 공유한다.
3. 유지: DB connection string 원문은 채팅, Issue, PR에 적지 않는다.
4. 진행: Core API DB 접속 정보는 Google Secret Manager에 등록하고 GitHub Actions에는 OIDC/WIF용 공개 식별자만 둔다. 기존 GitHub Environment의 중복 DB secret은 첫 Cloud Run 배포 확인 뒤 제거한다.
5. 진행: 개발 Spring 서비스는 `bodeul-dev`의 Cloud Run `bodeul-core-api-preview`로 고정한다. production 서비스는 production GCP project와 전환 조건을 확정한 뒤 별도로 만든다.

인프라 담당자가 공유해야 하는 값:

| 항목 | 공유 방식 |
| --- | --- |
| Supabase 프로젝트 이름 | Issue 댓글 또는 팀 채팅에 공개 가능 |
| Supabase 리전 | Issue 댓글 또는 팀 채팅에 공개 가능 |
| DB connection string | 공개 공유 금지. GitHub Environment secret 입력 시점에만 사용 |
| Supabase anon/service role key | 공개 공유 금지. 필요 여부를 별도 이슈에서 결정 |

## 관련 이슈

- [#86 PostgreSQL 운영 전환 기준 확정](https://github.com/bodeul110/Bodeul/issues/86)
- [#87 Supabase 개발 DB 준비 및 API 경계 검토](https://github.com/bodeul110/Bodeul/issues/87)
- [#88 bodeul-api 서버 골격 추가](https://github.com/bodeul110/Bodeul/issues/88)
- [#123 병원 가이드 Firestore/API 응답 비교 기록](https://github.com/bodeul110/Bodeul/issues/123)
- [#134 관리자 웹 production 배포 기준 확정](https://github.com/bodeul110/Bodeul/issues/134)
- [#140 Oracle/Vercel 기반 관리자 웹 API 모드 실연동 검증 환경 구축](https://github.com/bodeul110/Bodeul/issues/140)
