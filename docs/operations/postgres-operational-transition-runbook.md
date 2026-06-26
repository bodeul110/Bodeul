# PostgreSQL 운영 전환 런북

기준일: 2026-06-26

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Firebase 중심 운영 구조에서 `Supabase PostgreSQL + Oracle Cloud API 서버` 구조로 전환하기 위해 사용자가 만들어야 하는 외부 리소스, GitHub 설정, 초기 검증 순서를 고정한다.

## 사용자가 생성할 리소스

### Supabase

| 항목 | 값 |
| --- | --- |
| 조직 또는 팀 이름 | `bodeul` |
| 개발 프로젝트 | `bodeul-dev-rdb` |
| 운영 프로젝트 | `bodeul-prod-rdb` |
| DB 엔진 | PostgreSQL |
| 리전 | 콘솔에서 선택 가능한 한국 또는 가장 가까운 Asia Pacific 리전 |
| 초기 schema | `public` |
| 초기 테이블 접두어 | 없음. PostgreSQL 표준 snake_case 테이블명 사용 |

생성 순서:

1. 먼저 `bodeul-dev-rdb`만 만든다.
2. Firestore 백업 import와 API 서버 연결 리허설을 끝낸 뒤 `bodeul-prod-rdb`를 만든다.
3. 운영 프로젝트에는 테스트 데이터를 넣지 않는다.

### Oracle Cloud

| 항목 | 값 |
| --- | --- |
| 개발 VM | `bodeul-dev-api-01` |
| 운영 VM | `bodeul-prod-api-01` |
| 권장 리전 | 한국 사용자가 주 대상이면 Seoul 리전을 우선 검토 |
| 권장 shape | `VM.Standard.A1.Flex` |
| 개발 VM 초기 크기 | 1 OCPU / 6 GB RAM |
| 운영 VM 초기 크기 | 1 OCPU / 6 GB RAM |
| OS | Ubuntu LTS |
| 서버 프로세스 | `bodeul-api` |

Oracle Always Free의 Arm A1 자원은 계정 전체 기준으로 나뉘므로, 처음에는 개발 VM 하나만 만들고 운영 VM은 API 배포 리허설 후 만든다.

## GitHub 설정 이름

### Environment

| Environment | 용도 |
| --- | --- |
| `api-preview` | 개발 API 서버 배포/검증 |
| `api-production` | 운영 API 서버 배포 |

### `api-preview` variables

| 이름 | 값 기준 |
| --- | --- |
| `API_BASE_URL` | 개발 API URL. 예: `https://api-dev.<도메인>` |
| `FIREBASE_PROJECT_ID` | `bodeul-dev` |
| `SUPABASE_PROJECT_NAME` | `bodeul-dev-rdb` |
| `NODE_ENV` | `preview` |

### `api-preview` secrets

| 이름 | 값 기준 |
| --- | --- |
| `DATABASE_URL` | Supabase 개발 DB connection string |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | API 서버가 Firebase ID token을 검증할 서비스 계정 JSON |
| `KAKAO_REST_API_KEY` | 서버 프록시로 옮길 경우 사용할 Kakao REST API key |
| `API_DEPLOY_SSH_HOST` | Oracle 개발 VM host |
| `API_DEPLOY_SSH_USER` | 배포 사용자 |
| `API_DEPLOY_SSH_KEY` | 배포용 SSH private key |

주의:

- secret 값은 문서, Issue, PR 본문에 적지 않는다.
- `DATABASE_URL`은 서버 전용이다. Android 앱이나 관리자 웹에 노출하지 않는다.
- Supabase service role key는 초기 API 서버가 DB 직접 연결을 쓰는 동안 만들지 않는다. 필요할 때 별도 근거를 남기고 추가한다.

## API 서버 기준

| 항목 | 결정 |
| --- | --- |
| 디렉터리 | `api/` |
| 런타임 | Node 22 |
| 언어 | TypeScript |
| 서버 프레임워크 후보 | Fastify |
| DB 접근 후보 | Drizzle ORM 또는 node-postgres |
| 인증 | Firebase ID token 검증 |
| 권한 | PostgreSQL `app_users.role` 기준 |
| 헬스체크 | `GET /healthz` |

Fastify와 TypeScript를 우선 후보로 둔다. 현재 프로젝트에 Node 기반 `admin-web`, `functions`, `tools/firebase`가 이미 있으므로 팀이 새 언어 런타임을 추가로 배워야 하는 부담이 적다.

## 초기 API 범위

1. `GET /healthz`
2. `GET /admin/managers/documents`
3. `POST /admin/managers/{id}/documents/reviews`
4. `GET /admin/hospital-guides`
5. `POST /admin/hospital-guides`

초기 범위에서 제외:

- 예약 생성/취소
- 실시간 위치
- FCM 발송 큐
- 결제/정산 확정 처리

이유:

- 관리자 서류 심사와 병원 가이드는 운영 영향이 있지만 실시간 위치나 예약 상태 전이보다 rollback이 쉽다.
- Android 앱 배포 없이 관리자 웹부터 API 전환을 검증할 수 있다.

## 마이그레이션 검증 순서

1. Firestore 백업 생성
   - `npm --prefix tools/firebase run backup`
   - 실제 명령은 현재 `tools/firebase` 스크립트 기준으로 확인한다.
2. 백업 JSON을 PostgreSQL seed 입력으로 변환하는 dry-run 실행
3. `bodeul-dev-rdb`에 seed 적용
4. Firestore와 PostgreSQL의 row count 비교
5. 관리자 서류 심사 목록 API 응답과 기존 관리자 웹 Firestore 결과 비교
6. 관리자 웹을 `BODEUL_DATA_BACKEND=api`로 preview 빌드
7. 수동 QA
8. 문제가 없으면 운영 프로젝트와 운영 API VM을 만든다.

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

- API 서버가 15분 이상 장애
- PostgreSQL seed/import 결과가 Firestore 기준과 불일치
- 관리자 승인/반려 기록이 누락되거나 중복 저장
- Firebase Auth token 검증 실패율 증가
- 실시간 알림 또는 관리자 피드 누락

Rollback 방식:

1. 클라이언트 플래그를 `firebase`로 되돌린다.
2. PostgreSQL 쓰기를 중지한다.
3. Firestore 백업과 운영 상태를 다시 비교한다.
4. 원인을 문서화한 뒤 같은 범위를 재시도한다.

## 완료 조건

운영 전환 완료는 아래 조건을 모두 만족할 때로 본다.

- `bodeul-prod-rdb`가 운영 source of truth로 사용된다.
- `bodeul-prod-api-01` 또는 동등한 운영 API 서버가 배포되어 있다.
- 관리자 웹의 핵심 운영 조회/쓰기 기능이 API 서버를 사용한다.
- Android 앱의 예약/세션 핵심 흐름이 API 서버를 사용한다.
- Firestore 백업/복원과 PostgreSQL 백업/복원 리허설 결과가 모두 문서화되어 있다.
- Firebase는 Auth, FCM, Storage, Hosting 등 유지하기로 결정한 역할만 남는다.

## 사용자가 지금 해야 할 일

1. Supabase 계정을 만들고 `bodeul-dev-rdb` 프로젝트를 생성한다.
2. Oracle Cloud 계정을 만들고 `bodeul-dev-api-01` VM 생성을 준비한다.
3. GitHub에는 아직 DB secret을 넣지 않는다. `api/` 서버 골격과 연결 방식이 PR로 준비된 뒤 넣는다.
4. Supabase 프로젝트 생성 후 아래 값만 Codex에게 알려준다.
   - 프로젝트 이름
   - 리전
   - connection string을 GitHub secret에 넣을 준비가 되었는지 여부

secret 원문은 채팅이나 Issue에 적지 않는다.

## 관련 이슈

- [#86 PostgreSQL 운영 전환 기준 확정](https://github.com/bodeul110/Bodeul/issues/86)
- [#87 Supabase/Oracle 개발 리소스 준비](https://github.com/bodeul110/Bodeul/issues/87)
- [#88 bodeul-api 서버 골격 추가](https://github.com/bodeul110/Bodeul/issues/88)
