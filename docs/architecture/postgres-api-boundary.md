# PostgreSQL API 경계 기준

기준일: 2026-07-10

## 작업 목적

Supabase 개발 DB 준비 이후, 관리자 웹과 Android 앱이 PostgreSQL을 언제, 어떤 경로로 사용해야 하는지 1차 경계를 정한다. 2026-07-10 현재 이 경계는 `bodeul-api` 코드, 관리자 웹 API 모드, 로컬 비교 도구, Oracle preview API 실연동 기록, 병원 가이드 실제 API 응답 비교 기록까지 일부 구현됐다.

## 결론

클라이언트는 Supabase PostgreSQL에 직접 접속하지 않는다. PostgreSQL 접근은 `bodeul-api` 같은 얇은 API 서버를 통해 시작한다.

Firebase Auth, FCM, Storage, Hosting은 유지한다. PostgreSQL은 관계형 조회, 운영 감사, 정산/통계, 관리자 처리 이력처럼 Firestore보다 RDBMS가 적합한 도메인부터 맡긴다.

## 현재 구현 상태

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| API 디렉터리 | 구현 완료 | `api/` |
| 런타임 | Node 22 + TypeScript | `api/package.json` |
| CI | 구현 완료 | `.github/workflows/api.yml` |
| Health check | 구현 완료 | `GET /healthz`, `HEAD /healthz` |
| Firebase token 검증 | 구현 완료 | `api/src/firebase-admin.ts`, PR #114 |
| PostgreSQL client | 구현 완료 | `api/src/database.ts`, PR #115 |
| 관리자 role 인가 | 구현 완료 | `api/src/authorization.ts`, PR #116 |
| 첫 read API | 구현 완료 | `GET /admin/hospital-guides`, PR #117 |
| 관리자 웹 API 연결 | 1차 완료 | Issue #113에서 병원 가이드 검증 화면 연결 |
| API 배포 환경 | preview 1차 검증 완료 | #140/#123 댓글 기준으로 Oracle API 실행 환경, Supabase 연결, Firebase Admin 인증, 로컬 관리자 웹 API 모드, 병원 가이드 API 응답 비교가 통과했다. production 실행 환경은 #134 이후 별도 확정 |
| Spring Core API 인증 이관 | 개발 DB 검증 완료 | `core-api/src/main/java/com/bodeul/core/auth/`, Issue #157. `app_users` migration과 DB 권한 검증을 완료했고 OCI preview 실제 token 검증은 후속 확인 필요 |

## 선택한 방식

| 영역 | 1차 기준 |
| --- | --- |
| 인증 | Firebase Auth 유지 |
| 서버 인증 검증 | API 서버에서 Firebase ID token 검증 |
| 권한 기준 | PostgreSQL `app_users.role`과 Firebase UID 매핑 |
| DB 접근 | API 서버만 Supabase PostgreSQL connection string 사용 |
| Android 앱 | API 또는 기존 Firebase Repository를 통해 접근. DB 직접 접속 금지 |
| 관리자 웹 | `VITE_BODEUL_DATA_BACKEND=firebase|api` 전환 후보 관리 |
| Firebase Functions | FCM, Storage 보조, Firebase 인프라 연결 역할 유지 |
| Supabase Realtime | 관리자 알림처럼 빈도가 예측 가능한 읽기 구독부터 검토 |

## 대안

- 관리자 웹에서 Supabase JS client로 PostgreSQL/RLS를 직접 사용한다.
- Android 앱에서 Supabase client를 직접 붙인다.
- Firebase Functions에서 PostgreSQL 접근 API를 모두 처리한다.
- Oracle VM에서 API 서버와 PostgreSQL을 모두 직접 운영한다.

## 선택 이유

현재 MVP 규모에서는 인증, 푸시, 파일, 배포가 Firebase에 이미 연결되어 있다. 여기에 Supabase client를 앱과 웹에 직접 붙이면 Firebase Auth, Supabase Auth/RLS, 서비스 권한 정책이 동시에 얽혀 운영 기준이 흐려진다.

API 서버를 얇게 두면 다음 경계를 명확히 할 수 있다.

- DB connection string은 서버 secret으로만 둔다.
- Firebase ID token 검증과 PostgreSQL role 확인을 한 곳에서 처리한다.
- 관리자 쓰기 작업과 감사 로그 검증을 서버에서 통제한다.
- 특정 도메인을 PostgreSQL source of truth로 전환할 때 클라이언트 flag로 rollback할 수 있다.

## 현재 API 계약

| Method | Path | 인증 | 용도 |
| --- | --- | --- | --- |
| `GET` | `/healthz` | 없음 | 배포와 모니터링용 최소 상태 확인 |
| `HEAD` | `/healthz` | 없음 | health check 헤더 전용 |
| `GET` | `/admin/api-contract` | Firebase ID token + `ADMIN` role | 관리자 API 응답 계약과 DB 설정 상태 확인 |
| `HEAD` | `/admin/api-contract` | Firebase ID token + `ADMIN` role | 계약 API 접근 가능 여부 확인 |
| `GET` | `/admin/hospital-guides` | Firebase ID token + `ADMIN` role | PostgreSQL `hospital_guides` 목록 조회 |

상세 응답은 [관리자 API 초기 응답 계약](admin-api-contract.md)을 기준으로 본다.

Spring Core API의 첫 인증 계약은 다음과 같다.

| Method | Path | 인증 | 용도 |
| --- | --- | --- | --- |
| `GET` | `/healthz` | 없음 | Spring 배포와 모니터링 상태 확인 |
| `GET` | `/api/auth/me` | Firebase ID token + 등록된 PostgreSQL role | 검증된 Firebase UID와 서버 역할 연결 확인 |

Spring은 Firebase custom claim을 서비스 역할로 채택하지 않는다. Admin SDK가 검증한 UID를 `bodeul.app_users.firebase_uid`와 연결하고, endpoint 권한은 PostgreSQL 역할에서 만든 Spring Security authority로 판정한다.

## 환경 변수 경계

| 이름 | 위치 | 공개 여부 | 설명 |
| --- | --- | --- | --- |
| `DATABASE_URL` | API 서버 secret, GitHub Environment secret 후보 | 비공개 | Supabase PostgreSQL connection string |
| `FIREBASE_PROJECT_ID` | API 서버 env, GitHub Environment variable 후보 | 제한 공개 가능 | Firebase token 검증 project 지정 |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | API 서버 secret, GitHub Environment secret 후보 | 비공개 | Firebase Admin SDK 서비스 계정 JSON |
| `GOOGLE_APPLICATION_CREDENTIALS` | Spring Core API systemd env | 비공개 경로 | 배포 단계에서 만든 제한된 서비스 계정 파일의 절대 경로 |
| `BODEUL_API_ALLOWED_ORIGINS` | API 서버 env 후보 | 공개 가능 | 관리자 웹 브라우저 호출을 허용할 origin allow-list |
| `VITE_BODEUL_API_BASE_URL` | 관리자 웹 env 후보 | 공개 가능 | 관리자 웹이 호출할 API base URL |

Node prototype은 `FIREBASE_SERVICE_ACCOUNT_JSON`을 직접 읽지만 Spring 운영 경로는 JSON 원문을 직접 받지 않는다. 배포 단계가 secret으로 제한된 파일을 만든 뒤 ADC 표준 경로만 애플리케이션에 전달한다.
| `VITE_BODEUL_DATA_BACKEND` | 관리자 웹 env 후보 | 공개 가능 | `firebase` 또는 `api` 전환 flag |

`DATABASE_URL`, service account JSON, Supabase service role key는 문서, 공개 이슈, PR 본문에 기록하지 않는다.

## 도메인별 1차 경계

| 도메인 | 현재 source of truth | PostgreSQL 전환 판단 | API 필요도 |
| --- | --- | --- | --- |
| 병원 가이드 | Firestore와 PostgreSQL 병행 검증 후보 | 첫 read API 구현 완료 | 중간 |
| 매니저 서류 메타데이터 | Firestore + Storage | 원본 파일은 Storage 유지, 메타데이터/심사 이력은 PostgreSQL 후보 | 높음 |
| 문의/후속 처리 | Firestore | 관리자 운영 조회와 상태 변경을 PostgreSQL 후보로 둠 | 높음 |
| 관리자 감사 로그 | Firestore/admin 컬렉션 | PostgreSQL에 적합. 관리자 작업 API와 함께 확정 | 높음 |
| 예약 요청 | Firestore | Android 앱 영향이 커서 후순위 | 높음 |
| 동행 세션/리포트 | Firestore | 예약 전환 이후 검토 | 높음 |
| 실시간 위치 | Firestore | 마지막 전환 대상. 부하 테스트 전 유지 | 높음 |
| FCM 발송 큐 | Firebase/Functions | DB 전환과 분리. 발송은 Firebase 인프라 유지 | 중간 |

## 첫 API 후보와 현재 판단

| 후보 | 이유 | 현재 상태 |
| --- | --- | --- |
| `GET /healthz` | 배포와 모니터링 최소 기준 | 구현 완료 |
| `GET /admin/hospital-guides` | row 수가 작고 개인정보 노출 위험이 낮아 비교 검증이 쉽다. | 구현 완료 |
| `GET /admin/manager-document-reviews` | 관리자 운영 가치가 크고 PostgreSQL seed 검증 범위와 연결된다. | 후속 후보 |
| `GET /admin/support-requests` | 문의 통합 테이블 검증과 연결된다. | 후속 후보 |

write API는 read API의 인증, 권한, row 비교가 통과한 뒤 진행한다.

## API 배포 환경 생성 조건

#140의 preview 검증에서는 `bodeul-dev-api-01` 또는 동등 Oracle Free Tier 실행 환경을 만들 수 있다. 이 환경은 production cutover가 아니라 병원 가이드 API 응답 비교와 관리자 웹 API 모드 검증을 위한 것이다.

- `api/` 서버 골격과 `GET /healthz`가 PR로 준비됐다.
- `DATABASE_URL`, Firebase service account, 배포 SSH key를 어디에 넣을지 문서화됐다.
- 관리자 웹 또는 Android 앱이 API를 실제로 호출하는 PR이 준비됐다.
- 장애 시 `VITE_BODEUL_DATA_BACKEND=firebase`로 되돌리는 rollback 기준이 있다.
- API 로그에 secret, ID token 원문, DB connection string을 남기지 않는 기준이 있다.

2026-07-08 GitHub 이슈 댓글 기준으로 Oracle Free Tier VM의 `bodeul-api`, Supabase PostgreSQL, Firebase Admin 인증, 인증된 `GET /admin/hospital-guides?limit=50`, 로컬 관리자 웹 `api` 모드 표시, `compare:hospital-guides` 결과가 통과했다. 병원 가이드 read API의 현재 1건 데이터 기준으로 Firestore 기준 데이터와 PostgreSQL/API 응답이 일치한다.

남은 검증은 Vercel 또는 Firebase Hosting preview URL에서 팀원이 공유 가능한 관리자 웹 API 모드 화면을 확인하는 것이다. #140에서는 Vercel CLI 초기 프로젝트 생성 흐름이 production target을 만드는 문제가 있어 Vercel preview 검증을 직접 완료 범위에서 제외했다.

production API 실행 환경은 #140에서 확정하지 않는다. 운영 Firebase project, live 관리자 웹 도메인, Auth domain, App Check enforcement, WIF live deploy 조건은 #134에서 결정한다.

## source of truth 전환 조건

특정 도메인을 PostgreSQL source of truth로 바꾸려면 아래 조건을 모두 만족해야 한다.

- Firestore 백업과 PostgreSQL seed/import row count가 일치한다.
- 주요 필드 spot check가 통과한다.
- FK 누락이 0건이다.
- read API 응답이 기존 관리자 웹 또는 Android 화면 모델과 비교 가능하다.
- write 전환 대상이면 Firestore 쓰기 중단 또는 shadow write 정책이 문서화되어 있다.
- rollback flag와 복구 절차가 있다.
- 운영 로그와 감사 로그에서 사용자 개인정보와 secret을 노출하지 않는다.

## GitHub 기준 주의점

- [Issue #88](https://github.com/bodeul110/Bodeul/issues/88)은 API 골격과 초기 경계 구축 기준으로 완료 처리했다.
- [Issue #113](https://github.com/bodeul110/Bodeul/issues/113)은 PR #121 병합 후 관리자 웹 1차 read API 연결 기준으로 완료 처리했다.
- [Issue #122](https://github.com/bodeul110/Bodeul/issues/122)는 관리자 웹 API 환경변수와 CORS origin 설정 기준을 확정하고 종료됐다.
- [Issue #123](https://github.com/bodeul110/Bodeul/issues/123)는 병원 가이드 Firestore/API 응답 비교 기록을 추적한다. 2026-07-08 댓글 기준 실제 배포 API 응답 비교는 `passed`로 기록됐다.
- [Issue #134](https://github.com/bodeul110/Bodeul/issues/134)는 관리자 웹 production 배포 기준을 확정한다.
- [Issue #140](https://github.com/bodeul110/Bodeul/issues/140)은 Oracle/Supabase/Firebase Admin/로컬 관리자 웹 API 모드 1차 검증을 기록했다. Vercel preview 기반 팀 공유 화면 검증은 후속 분리 대상이다.
- 2026-07-02 기준 code scanning open alert는 0건이다.
- `uuid` 전이 취약점 검토는 [Issue #103](https://github.com/bodeul110/Bodeul/issues/103)에서 처리됐고, PR #136 병합 후 종료됐다.

## 관련 문서

- [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
- [관리자 API 초기 응답 계약](admin-api-contract.md)
- [PostgreSQL 운영 전환 런북](../operations/postgres-operational-transition-runbook.md)
- [PostgreSQL seed dry-run 기준 기록](../reports/postgres-seed-dry-run-plan-2026-06-29.md)
- [PostgreSQL schema 초안](postgres-schema-draft.sql)
