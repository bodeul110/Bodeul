# Issue 156 Spring Core API Cloud Run preview 검증

기준일: 2026-07-16

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다. 현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

OCI Free Tier 계정 잠금과 무관하게 Spring Core API를 반복 배포할 수 있는 개발 실행 환경을 만들고, 공용 Supabase PostgreSQL과 Firebase 인증을 서버 경계에서 연결할 기반을 검증한다.

## 선택한 방식

- `bodeul-dev`의 Tokyo 리전에 Cloud Run preview 서비스를 둔다.
- GitHub Actions는 OIDC와 전용 Workload Identity Federation provider로만 배포한다.
- 배포 계정과 runtime 계정을 분리하고 사용자 관리 서비스 계정 key는 만들지 않는다.
- DB 접속 정보는 Google Secret Manager의 숫자 version을 Cloud Run 환경변수에 참조한다.
- Java 21 비루트 distroless 컨테이너를 Artifact Registry에 commit SHA tag로 게시한다.
- 최소 인스턴스 0, 최대 인스턴스 1, DB pool 5로 개발 비용과 연결 수를 제한한다.

## 검토한 대안

| 대안 | 판단 |
| --- | --- |
| 잠긴 OCI Free Tier 계정 복구 대기 | 일정과 계정 상태에 개발 배포가 종속되므로 제외했다. |
| Cloudflare Workers에서 Spring 실행 | JVM 컨테이너 실행 환경이 아니므로 현재 Spring 코드와 맞지 않는다. Cloudflare는 향후 DNS와 WAF 계층으로 검토한다. |
| Vercel Functions에 Core API 배치 | 관리자 Next.js 서버와 사용자 Core API의 배포·권한 경계가 다시 섞이므로 제외했다. |
| 장기 GCP 서비스 계정 JSON을 GitHub secret으로 저장 | 회전과 유출 범위가 커지므로 WIF를 사용했다. |

## 선택 이유

현재 MVP 규모에서는 VM 운영보다 요청 기반 관리형 컨테이너가 운영 부담이 작다. Cloud Run은 현재 Spring 애플리케이션을 그대로 배포하면서 유휴 시 0개 인스턴스로 줄일 수 있고, 같은 Google Cloud 프로젝트의 Firebase Admin ADC와 Secret Manager를 장기 key 없이 사용할 수 있다. 관리자 서버는 Vercel, 사용자 Core API는 Cloud Run으로 나누는 목표 경계도 유지된다.

## 생성한 개발 자산

| 항목 | 값 |
| --- | --- |
| Project | `bodeul-dev` |
| Region | `asia-northeast1` |
| Cloud Run service | `bodeul-core-api-preview` |
| Artifact Registry | `bodeul-core-api` |
| Deploy service account | `bodeul-core-preview-deployer` |
| Runtime service account | `bodeul-core-preview-runtime` |
| WIF provider | `github-actions/bodeul-core-api-preview` |
| GitHub Environment | `core-api-preview` |
| Secret Manager | JDBC URL, DB 사용자명, DB 비밀번호 각각 별도 secret version 1 |

WIF provider는 `bodeul110/Bodeul`, `master`, `core-api-preview` environment 조건으로 제한했다. runtime 계정에는 세 DB secret의 accessor만 부여했다. 일회 이전에 사용한 deploy 계정의 Secret Manager 추가·조회 권한은 이전 직후 제거했다.

## 배포 결과

| 항목 | 결과 |
| --- | --- |
| 기준 commit | `3921228d11b187fbeca91e9692b28c8b6f9f7883` |
| GitHub Actions | [Core API Preview Deploy #29476652742](https://github.com/bodeul110/Bodeul/actions/runs/29476652742) 성공 |
| Cloud Run revision | `bodeul-core-api-preview-00002-xc6` |
| Image digest | `sha256:c6309c5eb724e8de0c8d063ee4a37e23f71ef5fe296c153c8212ecc0d01e3a30` |
| Service URL | `https://bodeul-core-api-preview-cyvvxy3kia-an.a.run.app` |
| Traffic | 최신 revision 100% |
| Runtime | 1 vCPU, 1 GiB, concurrency 20, timeout 30초, min 0, max 1 |

## 실제 검증

| 검증 | 결과 |
| --- | --- |
| `GET /health` | 200, `status=UP` |
| `GET /health/liveness` | 200, `status=UP` |
| `GET /health/readiness` | 200, `status=UP` |
| 무인증 `GET /api/auth/me` | 401, `missing_authorization` |
| PostgreSQL 초기화 | Hikari pool 시작과 전체 health `UP` 확인 |
| 컨테이너 사용자 | distroless nonroot UID `65532` 로컬 확인 |
| Secret 참조 | 세 환경변수 모두 Secret Manager version 1 참조 확인 |

처음 사용한 `/healthz`는 로컬에서는 동작했지만 Cloud Run frontend에서 404로 차단됐다. Cloud Run은 일부 `z` 종결 경로를 예약하고 있으므로 공식 [Reserved URL paths](https://cloud.google.com/run/docs/known-issues#reserved_url_paths) 기준에 따라 Core API 상태 경로를 `/health`로 변경했다. 기존 Node prototype의 `/healthz`와 과거 Oracle 검증 기록은 별도 계약으로 보존했다.

새 revision의 Cloud Run 로그 33건을 별도로 조회했다. 애플리케이션과 DB pool 기동을 확인했고, warning 2건은 의도적으로 보낸 무인증 401 요청이었다. JDBC URL, Bearer token, JWT 형식, DB 비밀번호 할당 형태는 로그에서 발견되지 않았다.

## 롤백 리허설

1. 트래픽 100%를 이전 revision `bodeul-core-api-preview-00001-xf2`로 전환했다.
2. 이전 revision의 무인증 인증 경로가 401을 반환하는지 확인했다.
3. 트래픽 100%를 현재 revision `bodeul-core-api-preview-00002-xc6`로 복구했다.
4. 복구 후 `/health` 200과 무인증 `/api/auth/me` 401을 다시 확인했다.

리허설 종료 시 현재 revision이 트래픽 100%를 받고 있음을 확인했다. DB migration은 애플리케이션 배포와 분리돼 있으므로 revision rollback이 schema rollback을 실행하지 않는다.

## 남은 범위

- 정상, 만료, 변조, 다른 Firebase project의 실제 ID token 검증은 Issue #157에서 진행한다.
- 기존 GitHub Environment의 `CORE_DB_*` secret은 실제 ID token과 DB role 조회가 통과할 때까지만 보존한다. 이후 Secret Manager를 단일 runtime 기준으로 삼고 중복 secret을 삭제한다.
- production project, service, database, secret, domain은 만들지 않았다.
- GitHub Actions 실행에는 일부 action의 Node.js 20 사용 중단 경고가 남았다. 배포 실패 원인은 아니며 기존 Dependabot PR을 각각 검증한 뒤 별도 갱신한다.

## 관련 변경

- [PR #170 Core API preview Cloud Run 전환](https://github.com/bodeul110/Bodeul/pull/170)
- [PR #175 Core API DB secret 일회 이전](https://github.com/bodeul110/Bodeul/pull/175)
- [PR #176 Cloud Run 헬스 체크 경로 충돌 해소](https://github.com/bodeul110/Bodeul/pull/176)
