# #222 개인정보 자동 파기 구현 기록

기준일: 2026-07-19

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

## 리스크와 전환 조건

- 하루 500건보다 만료 적재가 빠르면 backlog가 생길 수 있다. 7일 연속 backlog가 줄지 않으면 배치 반복 또는 Cloud Run Job 전환을 검토한다.
- legal hold 설정과 Storage 삭제가 같은 순간에 경합하면 외부 Storage 삭제를 DB 트랜잭션으로 되돌릴 수 없다. 운영에서는 legal hold 변경 중 파기 job을 일시 중지하고, 장기적으로 hold 변경용 관리자 API와 claim 잠금을 연결한다.
- Firestore 전환 문서는 문서 ID cursor로 500개씩 조회한다. 실행 시간이 Functions 한도에 가까워지면 페이지별 checkpoint 또는 Cloud Run Job으로 전환한다.
- Firebase 기본 Storage bucket 이름은 `{projectId}.firebasestorage.app` 규약을 사용한다. 환경별 bucket이 다르면 함수 설정을 분리해야 한다.
- Supabase CA가 교체되면 Secret의 새 버전을 등록하고 두 함수를 재배포해야 한다. CA 검증 실패 시 자동으로 우회하지 않는다.
- Firestore 전환 문서와 매니저 증빙의 파기 분기는 단위 테스트와 빈 개발 컬렉션 dry-run까지 확인했다. 실제 Firestore fixture APPLY는 별도 리허설 기록이 필요하다.

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
