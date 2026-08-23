# #222 개인정보 자동 파기 구현 기록

기준일: 2026-07-19
최종 갱신: 2026-08-23

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

위치 원본, 채팅 본문과 첨부, 매니저 증빙 원본에 확정한 보관 기간을 실제 개발 인프라에서 자동 집행한다. Firestore 전환 데이터와 PostgreSQL 원본이 동시에 남는 기간에도 동일한 기간을 적용하고, 파기 이력에는 원문 개인정보를 남기지 않는다.

## 선택한 방식

- `asia-northeast3`의 2세대 Firebase 예약 함수가 매일 04:45에 파기 작업을 실행한다.
- 예약 함수는 Supavisor transaction mode 6543에 파기 전용 `bodeul_retention_service`로 직접 연결한다. 연결 시 Supabase CA를 별도 Secret으로 주입하고 서버 인증서 검증을 강제한다.
- DB 계정은 테이블 DML을 직접 받지 않고 `bodeul_retention_runtime`에 허용된 파기 함수만 실행한다.
- PostgreSQL 첨부는 `ACTIVE -> DELETE_PENDING -> DELETED` 순서로 처리한다. Storage 삭제가 실패하면 `DELETE_PENDING`에 남겨 다음 실행에서 재시도한다.
- Firestore 전환 세션은 종료 시각을 `completedAt`, `canceledAt`, `updatedAt` 순서로 결정한다. 정밀 위치는 24시간, 첨부는 30일, 채팅 본문은 180일 후 정리한다.
- 매니저 증빙은 승인 또는 반려 심사 후 30일이 지나고, 제출 갱신 시각이 심사 시각보다 늦지 않으며, legal hold가 없을 때만 삭제한다.
- 매월 1일 05:15에 직전 달의 만료 후보 관측·성공·실패·legal hold 건수만 Cloud Logging에 기록한다. 재시도 후보는 실행일마다 다시 집계되므로 고유 레코드 수가 아니라 운영 부하 지표로 본다.
- `RETENTION_APPLY_ENABLED`는 문자열 `true`를 명시한 런타임에서만 활성화된다. 값이 없거나 다른 값이면 `false`다. 개발 환경에서도 격리 fixture 리허설 때만 일시적으로 켰고, 검증 직후 다시 `false`로 배포했다.

## 대안

| 대안 | 장점 | 현재 선택하지 않은 이유 |
| --- | --- | --- |
| Core API 내부 스케줄러 | Java 코드와 DB 접근을 한 곳에서 관리 | 여러 Cloud Run 인스턴스의 중복 실행 제어가 필요하고, Storage·Firestore 운영 작업이 사용자 API 생명주기와 결합된다. |
| 별도 Cloud Run Job | 실행 격리와 배치 운영이 명확함 | 현재 MVP 규모에서는 별도 이미지, Job, Scheduler, IAM 배포 경로가 추가되어 운영 부담이 더 크다. 처리량이나 실행 시간이 Functions 한도를 넘으면 이 방식으로 전환한다. |
| Supabase Cron만 사용 | PostgreSQL 데이터 파기가 단순함 | Firebase Storage와 Firestore 메타데이터의 성공 순서를 한 트랜잭션으로 처리할 수 없다. |

## 선택 이유

현재 MVP 규모에서는 하루 한 번 최대 500건씩 처리하는 작업이면 충분하고, Firebase Admin 권한이 필요한 Firestore·Storage 정리가 작업의 절반을 차지한다. 따라서 Firebase 예약 함수를 운영 경계로 두고 PostgreSQL에는 최소 권한 함수만 직접 호출하는 구성이 배포 수와 자격 증명 범위를 가장 적게 늘린다. 서버리스 연결은 Supavisor transaction mode를 사용하며, 클라이언트의 named prepared statement는 비활성화한다.

## 구현 범위

### PostgreSQL

- `core-api/db/bootstrap/004_retention_runtime.sql`
  - `bodeul_retention_runtime` 권한 role
  - 기본 `NOLOGIN`으로 생성하고 환경별 준비가 끝난 뒤에만 활성화하는 `bodeul_retention_service` 로그인 role
  - schema `USAGE`와 connection limit 2
- Flyway V13
  - 비식별 `retention_job_runs` 집계
  - 만료 후보 dry-run 함수
  - 첨부 삭제 claim과 완료 함수
  - 채팅 본문 비식별화와 위치 원본 삭제 함수
  - 월간 집계 함수
- rollback SQL과 Java 계약 테스트

### Firebase

- `cleanupExpiredData`: 매일 04:45, 기본 dry-run
- `reportMonthlyRetention`: 매월 1일 05:15, 직전 달 집계
- `RETENTION_DATABASE_URL`과 `RETENTION_DATABASE_CA_CERT`는 Secret Manager에서 함수별로 바인딩한다.
- PostgreSQL 집계 payload는 DB 계약의 20개 키만 허용하고 모든 값을 0 이상의 안전한 정수로 정규화한다.
- PostgreSQL 첨부와 Firestore 전환 첨부는 Storage 삭제 성공 후에만 메타데이터를 정리한다.
- 매니저 본인은 `managerDocumentLegalHoldUntil`을 수정할 수 없고 관리자만 수정할 수 있다.
- 로그에는 건수, 모드, 실패 단계 코드만 남기고 사용자 ID, 메시지, 좌표, 파일 경로를 남기지 않는다.

## 개발 환경 적용 순서

1. `004_retention_runtime.sql`을 개발 DB의 `postgres` 권한으로 적용한다.
2. `bodeul_retention_service`에 개발 전용 비밀번호를 보안 경로에서 설정하고 `LOGIN`을 활성화한다.
3. Flyway V13을 `Core API DB Migration` workflow의 preview 대상으로 적용한다.
4. transaction pooler URL을 `RETENTION_DATABASE_URL` Secret Manager 값으로 등록한다.
5. Supabase 프로젝트 CA를 `RETENTION_DATABASE_CA_CERT` Secret Manager 값으로 등록한다.
6. `cleanupExpiredData`, `reportMonthlyRetention`을 개발 Firebase 프로젝트에 배포한다.
7. 다음 명령으로 dry-run 집계를 확인한다.

```powershell
npm --prefix functions run retention:dry-run -- --project bodeul-dev
```

8. 격리된 만료 fixture에서 다음 명령으로 apply를 한 번 실행하고 DB·Storage·Firestore 결과를 대조한다.

```powershell
npm --prefix functions run retention:apply -- --project bodeul-dev --confirm-project bodeul-dev
```

9. 실패 재시도와 legal hold 결과가 맞더라도 정기 apply는 개인정보 처리방침과 위치기반서비스 이용약관 대조가 끝난 뒤 활성화한다.

로컬 실행 시 두 Secret 값은 셸 환경변수로만 주입하며 파일이나 명령 이력에 값을 남기지 않는다. 일반 운영 검증은 Secret 본문을 로컬로 꺼내지 않고 Firebase 런타임 바인딩과 Cloud Scheduler 수동 실행을 사용한다.

### 반복 가능한 Core 첨부 fixture 작업

- `Core API DB Migration` workflow의 `retention_fixture_action`은 `setup`, `status`, `cleanup`만 허용한다.
- 이 작업은 `master`의 `preview` 환경과 확인값 `bodeul-dev`에서만 실행되며, JDBC URL 또는 migration 사용자명에서도 개발 Supabase project ref를 다시 확인한다.
- fixture는 고정 UUID와 `retention-fixture-core-*` 표식만 사용한다. 임의 SQL, production 대상, 동행 세션 백필과의 동시 실행은 거부한다.
- workflow는 PostgreSQL fixture만 다룬다. Firebase Storage 객체 준비·상태 대조와 단일 파기 APPLY는 별도 단계이며, `RETENTION_APPLY_ENABLED` 값을 변경하지 않는다.
- `status`는 fixture 행 상태와 전체 첨부 만료 후보·legal hold 건수만 출력하고 DB 자격 증명이나 사용자 원문은 출력하지 않는다.

## 검증 기록

| 검증 | 결과 |
| --- | --- |
| Functions Node 테스트 | 16개 통과 |
| Functions production dependency audit | 알려진 취약점 0건 |
| Core API `check` | 통과 |
| Firestore/Storage Rules Emulator | 7/7 통과 |
| 개발 Supabase V13 적용 | GitHub Actions run `29654617496` 성공, Flyway 이력과 7개 retention 함수 확인 |
| 파기 전용 DB 계정 | `bodeul_retention_service` LOGIN, connection limit 2, 전용 runtime role 상속 확인 |
| TLS 신뢰 체인 | Supabase CA Secret 바인딩 후 `rejectUnauthorized=true` 연결 성공 |
| Firebase Functions 배포 | `cleanupExpiredData`, `reportMonthlyRetention`을 `asia-northeast3` Node.js 22로 배포 |
| 예약 dry-run | Cloud Scheduler 수동 실행 성공, 후보와 실패 0건, `COMPLETED` 집계 행 확인 |
| 개발 Supabase·Storage APPLY 리허설 | 만료 메시지 1건 비식별화, 위치 1건 삭제, 첨부 1건 Storage 삭제 및 메타데이터 종료, legal hold 3건 보존 확인 |
| 월간 집계 | Cloud Scheduler 수동 실행 성공 |
| 적용 플래그 복구 | 리허설 직후 배포 환경의 `RETENTION_APPLY_ENABLED=false` 확인 |
| 비대화형 재배포 | 로컬 `.env.bodeul-dev` 없이 두 함수를 다시 배포하고 dry-run 성공 확인 |
| Supabase Security Advisor | 보안 lint 0건 |
| Supabase Performance Advisor | 미사용 인덱스 INFO 8건만 확인, 개발 초기 사용량 기준이라 즉시 삭제하지 않음 |

## 2026-07-28 Core API 첨부 경로 후속 검증

- Core API 첨부 경로가 기존 `세션 ID/파일`에서 `세션 ID/클라이언트 메시지 UUID/파일` 구조로 확장됐지만 파기 함수의 Storage 경로 허용 목록에는 반영되지 않은 문제를 확인했다.
- PR #276에서 legacy 경로와 UUID 하위 경로를 함께 허용하고, 임의 하위 디렉터리와 추가 중첩은 계속 거부하도록 수정했다.
- Android Preflight에 Node 22 Functions 테스트 단계를 추가했고 run `30356833407`에서 16개 테스트가 통과했다. JavaScript CodeQL도 같은 PR에서 통과했다.
- 개발 Firebase의 `cleanupExpiredData`, `reportMonthlyRetention`을 제한 재배포했다. 리비전은 각각 `cleanupexpireddata-00007-jad`, `reportmonthlyretention-00005-jun`이다.
- 두 함수 모두 Node.js 22, DB URL Secret v2, CA Secret v1과 `RETENTION_APPLY_ENABLED=false`를 유지한다.
- 2026-07-28 21:03 KST Scheduler 수동 실행은 HTTP 200으로 완료됐다. `DRY_RUN`, 모든 후보 0건과 삭제 실패 0건을 확인했다.
- PR #278에서 preview 전용 고정 fixture 실행기를 추가했다. Core API와 preflight CI 통과 후 merge commit `96e9b3a135e2731186806679a016fc4504df4271`로 반영했다.
- DB setup run `30360287210`은 fixture 7행을 만들었다. 만료 첨부 후보 1건과 legal hold skip 2건을 확인했고, Storage에는 DB와 같은 경로·크기의 PDF 객체 50B와 53B를 준비했다.
- 2026-07-28 21:49 KST dry-run은 `postgresAttachmentCandidates=1`, `postgresLegalHoldSkips=2`, 나머지 후보와 실패 0건으로 완료됐다.
- 리비전 `cleanupexpireddata-00008-bek`에서 단일 APPLY를 실행했다. Scheduler HTTP 200, 첨부 삭제 1건, legal hold skip 2건과 실패 0건을 확인했다.
- DB status run `30361215537`에서 만료 첨부는 `DELETED`, Storage 경로·삭제 시각·크기 비식별화 완료 상태였다. legal hold 첨부는 `ACTIVE`, 원래 경로와 53B 객체를 유지했다.
- legal hold 객체를 수동 삭제한 뒤 cleanup run `30361372405`에서 fixture 7행을 정리했다. 고정 Storage prefix에도 남은 객체가 없음을 확인했다.
- 리비전 `cleanupexpireddata-00009-cil`로 `RETENTION_APPLY_ENABLED=false`를 복구했다. 2026-07-28 22:01 KST 최종 dry-run의 모든 후보와 실패는 0건이다.

## 2026-08-22 실패·재시도 단위 검증 보강

- PostgreSQL 첨부 두 건 중 한 건의 Storage 삭제가 실패하는 fixture로, 성공 건만 파기 완료로 기록되고 실패 건은 다음 실행에서 다시 처리되는지 확인했다.
- Firestore 전환 세션은 만료 첨부 두 건 중 성공한 참조만 제거하고, 실패한 참조를 유지한 뒤 다음 실행 성공 때 제거하는지 실제 `FirebaseLegacyCompanionStore.applySession()` 경로로 확인했다.
- 매니저 증빙은 Storage 삭제 실패 시 Firestore 참조를 지우지 않고 다음 실행 성공 뒤에만 참조를 제거하는지 확인했다.
- 저장소별 부분 성공은 작업 상태를 `COMPLETED`로 두되 성공·실패 건수를 분리해 `finishJob` 집계에 전달한다. 작업을 중단시키는 예외는 그 전까지의 집계를 보존하고 `FAILED`와 `PURGE_FIRESTORE` 실패 단계를 기록한다.
- Node.js 22로 Functions 전체 테스트 19개가 통과했다. 운영·개발 Firebase 데이터, Storage 객체와 정기 apply 설정은 변경하지 않았다.
- 이 검증은 상태 기반 단위 fixture다. Firestore 전환 문서와 매니저 증빙의 실제 개발 fixture APPLY 및 Storage 결과 대조는 계속 남아 있다.

## 2026-08-22 Firestore·Storage Emulator 통합 검증

- 고정 테스트 프로젝트 `bodeul-retention-emulator`에서만 실행되는 통합 테스트를 추가했다. 실제 Firebase 프로젝트 ID나 자격 증명을 사용하지 않는다.
- Firestore 전환 세션의 만료 채팅 본문·정밀 위치·첨부 2건과 매니저 증빙 2건을 만들고, Storage 첨부와 증빙을 실제 Emulator bucket에 저장했다.
- 첫 APPLY에서 경로별 한 건의 Storage 실패를 주입해 성공 객체와 참조만 제거되고 실패 객체와 참조는 유지되는지 확인했다. 두 번째 APPLY에서는 남은 객체와 참조가 제거되고 실패 집계가 0으로 돌아오는지 확인했다.
- legal hold가 설정된 세션의 본문·위치·첨부와 매니저 증빙은 두 실행 모두 Firestore 참조와 Storage 원본을 유지했다.
- Node.js 22.23.2에서 파기 Emulator 테스트 1/1, 기존 Firestore·Storage Rules 테스트 7/7과 Functions 일반 테스트 19개가 통과했다.
- 이 결과는 transaction과 Storage 객체 삭제 경로를 로컬 격리 환경에서 검증한 것이다. 개발 Firebase의 실제 전환 문서·매니저 증빙 fixture APPLY와 결과 대조를 대신하지 않는다.

## 2026-08-23 개발 Firebase 픽스처 실행 경계

- `bodeul-dev`와 기본 Storage 버킷만 허용하는 Firestore·Storage 픽스처 실행기를 추가했다.
- 고정 합성 문서 4개와 객체 4개만 allowlist로 다루며, production 프로젝트, Emulator 환경변수와 다른 프로젝트 환경값은 실행 전에 거부한다.
- 쓰기 작업은 프로젝트 확인값을 요구하고, 문서와 객체의 픽스처 이름·저장소 소유자·이슈 번호가 모두 맞아야 정리한다.
- Storage 생성은 generation 0, 삭제는 확인한 generation을 전제로 실행한다. Firestore 생성은 `create`, 정리는 확인한 update time을 전제로 실행해 같은 경로의 동시 변경을 덮어쓰거나 삭제하지 않는다.
- 실제 파기 클래스는 scoped adapter 뒤에서 재사용한다. PostgreSQL adapter는 후보 0건으로 고정하며, 일반 개발 문서를 전체 조회하거나 정기 apply 플래그를 바꾸지 않는다.
- Node.js 22.23.2에서 Functions 일반 테스트 28개가 통과했고, 같은 실행의 Emulator 통합 테스트 2개는 Emulator 환경변수가 없어 건너뛰었다. Firestore·Storage Emulator를 별도로 실행한 기존 실패·재시도와 새 픽스처 생명주기 테스트는 2/2 통과했다. Firebase 도구 단위 테스트도 33/33 통과했다.
- 실행 순서와 중단 기준은 [개발 Firebase 자동 파기 픽스처](../operations/firebase/retention-development-fixture.md)에 고정했다.
- 이 단계에서는 실행기와 로컬 격리 검증을 먼저 완료했다. 같은 날 수행한 실제 `bodeul-dev` 리허설 결과는 다음 절에 분리해 기록한다.

## 2026-08-23 개발 Firebase 실제 리허설

- 장기 서비스 계정 key 없이 운영자 ADC로 인증했고, 토큰이나 계정 식별자는 보고서에 남기지 않았다. quota project 권한은 확대하지 않고 비활성화한 채 `bodeul-dev` 리소스 접근 권한만 사용했다.
- 최초 `status`는 고정 합성 문서 4개와 Storage 객체 4개가 모두 없는 `ABSENT`였다. `setup`은 기존 경로를 덮어쓰지 않고 8개 fixture를 만든 뒤 `READY`로 종료했다.
- dry-run은 Firestore 채팅 본문·첨부·위치 후보 각 1건, 매니저 증빙 후보 1건을 집계했다. 세션 legal hold 제외 3건과 매니저 증빙 legal hold 제외 1건이었고 PostgreSQL 후보와 삭제·실패 건수는 모두 0이었다.
- `2026-08-23T09:40:32.134Z` 단일 APPLY에서 Firestore 본문 비식별화 1건, 첨부 원본 삭제와 참조 제거 1건, 정밀 위치 제거 1건, 매니저 증빙 원본 삭제와 참조 제거 1건을 확인했다. 두 Storage 삭제 경로의 실패는 0건이었다.
- APPLY 직후 상태는 `APPLIED`였다. legal hold 세션의 본문·첨부·위치와 매니저 증빙 참조 및 두 Storage 원본은 그대로 유지됐다.
- fixture 표식과 경합 조건을 다시 확인한 뒤 cleanup을 실행했다. 최종 `status`는 문서와 객체 8개가 모두 없는 `ABSENT`였으며 정기 `RETENTION_APPLY_ENABLED` 값과 일반 개발 데이터는 변경하지 않았다.

## 리스크와 전환 조건

- 하루 500건보다 만료 적재가 빠르면 backlog가 생길 수 있다. 7일 연속 backlog가 줄지 않으면 배치 반복 또는 Cloud Run Job 전환을 검토한다.
- legal hold 설정과 Storage 삭제가 같은 순간에 경합하면 외부 Storage 삭제를 DB 트랜잭션으로 되돌릴 수 없다. 운영에서는 legal hold 변경 중 파기 job을 일시 중지하고, 장기적으로 hold 변경용 관리자 API와 claim 잠금을 연결한다.
- Firestore 전환 문서는 문서 ID cursor로 500개씩 조회한다. 실행 시간이 Functions 한도에 가까워지면 페이지별 checkpoint 또는 Cloud Run Job으로 전환한다.
- Firebase 기본 Storage bucket 이름은 `{projectId}.firebasestorage.app` 규약을 사용한다. 환경별 bucket이 다르면 함수 설정을 분리해야 한다.
- Supabase CA가 교체되면 Secret의 새 버전을 등록하고 두 함수를 재배포해야 한다. CA 검증 실패 시 자동으로 우회하지 않는다.
- Core API 중첩 첨부 경로는 개발 PostgreSQL·Storage fixture APPLY까지 확인했다. Firestore 전환 문서와 매니저 증빙도 실제 개발 fixture APPLY와 cleanup까지 확인했다. production에서는 별도 자격 증명과 격리 fixture로 같은 순서를 다시 검증해야 한다.

## 2026-08-23 production 격리 경로 준비

- production 전용 marker, 문서 ID 4개와 Storage 객체 경로 4개를 개발 fixture와 분리했다.
- `.github/workflows/firebase-retention-production.yml`은 `master`의 실제 commit SHA, production 프로젝트, fixture ID 재확인과 `firebase-retention-production` Environment 승인을 요구한다.
- setup과 apply에는 `bodeul-prod-110` 전용 Firestore export metadata와 Storage inventory 객체가 각각 필요하고, workflow가 WIF 인증 뒤 실제 존재를 확인한다. apply는 별도 확인 문구와 승인된 정책 검토 증적이 없으면 실행되지 않는다.
- workflow는 일반 `retention:apply`를 호출하지 않고 production allowlist adapter만 사용한다. PostgreSQL과 일반 production 문서는 처리하지 않는다.
- CLI 자체도 GitHub Actions 저장소·브랜치·commit과 Environment 전용 실행 토큰을 확인해 로컬 ADC로 승인 경계를 우회하지 못하게 했다.
- Node.js 22.23.2에서 일반 테스트 37개가 통과했고 Emulator 전용 3개는 일반 실행에서 건너뛰었다. Firestore·Storage Emulator를 실행한 생명주기·재시도 검증은 개발·production 프로필을 포함해 3/3 통과했다. 신규 workflow는 `yq`와 `actionlint` 검사를 통과했다.
- `firebase-retention-production` GitHub Environment는 `master` 제한, `bodeul110` 승인과 관리자 우회 금지로 생성했다. production 프로젝트 ID와 원문을 노출하지 않는 실행 토큰 secret을 등록했다.
- WIF provider `bodeul-retention-prod`는 저장소 불변 ID, `master`, Environment, workflow 파일과 수동 실행 event를 모두 확인한다. 서비스 계정 `bodeul-retention-operator`의 impersonation은 Environment exact subject 한 개만 허용한다.
- 전용 서비스 계정에는 프로젝트 `roles/datastore.viewer`, `roles/serviceusage.serviceUsageConsumer`와 production Firebase·backup 버킷의 `roles/storage.objectViewer`만 부여했다. Environment의 WIF와 서비스 계정 변수도 실제 리소스명으로 등록했다.
- 2026-08-23 [production status run](https://github.com/bodeul110/Bodeul/actions/runs/32637096512)을 master `0106bba8a1decea4eca836d8afa72d54bbb3c493`에서 실행했다. 보호된 Environment 승인, 실행 경계, Functions 테스트, WIF 인증과 고정 경로 조회가 모두 통과했고 production fixture 상태는 `ABSENT`였다. 문서 4개와 Storage 객체 4개는 모두 존재하지 않았다.
- 이 단계에서는 production API, Secret과 데이터를 변경하지 않았고 쓰기 권한도 부여하지 않았다. workflow의 `setup`, `apply`, `cleanup`은 IAM에서 차단되고 `status`만 검증 대상이다.
- 실행 절차와 중단 기준은 [Production Firebase 자동 파기 격리 픽스처](../operations/firebase/retention-production-fixture.md)에 고정했다.

## 출시 전 확인

2026-07-19 저장소 확인 결과, 사용자에게 고지할 개인정보 처리방침과 위치기반서비스 이용약관의 기준 원문은 아직 버전 관리되고 있지 않다. 아래 항목은 문서 원문을 추가한 뒤 대조해야 한다.

- [ ] 개인정보 처리방침에 위치 24시간, 채팅 180일, 첨부·증빙 30일 기준이 동일하게 반영됐는지 확인
- [ ] 위치기반서비스 이용약관의 위치정보 이용·보유·파기 문구 확인
- [ ] 개인정보 보호책임자 또는 법률 검토 결과 기록
- [x] production 전용 retention 권한 role 생성 (`NOLOGIN` 유지)
- [ ] production retention 로그인 secret 생성과 파기 함수 배포
- [x] production V13 backup 기준점과 격리 복원 검증
- [ ] production 파기 함수 dry-run 결과 승인
- [ ] production apply 활성화 전 격리 fixture 재검증

법률·개인정보 검토는 기술 구현으로 대신할 수 없으므로 완료 전까지 #222를 출시 게이트로 유지한다.
