# #222 개인정보 자동 파기 구현 기록

기준일: 2026-07-19

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

위치 원본, 채팅 본문과 첨부, 매니저 증빙 원본에 확정한 보관 기간을 실제 개발 인프라에서 자동 집행한다. Firestore 전환 데이터와 PostgreSQL 원본이 동시에 남는 기간에도 동일한 기간을 적용하고, 파기 이력에는 원문 개인정보를 남기지 않는다.

## 선택한 방식

- `asia-northeast3`의 2세대 Firebase 예약 함수가 매일 04:45에 파기 작업을 실행한다.
- 예약 함수는 Supavisor transaction mode 6543에 파기 전용 `bodeul_retention_service`로 직접 연결한다.
- DB 계정은 테이블 DML을 직접 받지 않고 `bodeul_retention_runtime`에 허용된 파기 함수만 실행한다.
- PostgreSQL 첨부는 `ACTIVE -> DELETE_PENDING -> DELETED` 순서로 처리한다. Storage 삭제가 실패하면 `DELETE_PENDING`에 남겨 다음 실행에서 재시도한다.
- Firestore 전환 세션은 종료 시각을 `completedAt`, `canceledAt`, `updatedAt` 순서로 결정한다. 정밀 위치는 24시간, 첨부는 30일, 채팅 본문은 180일 후 정리한다.
- 매니저 증빙은 승인 또는 반려 심사 후 30일이 지나고, 제출 갱신 시각이 심사 시각보다 늦지 않으며, legal hold가 없을 때만 삭제한다.
- 매월 1일 05:15에 직전 달의 만료 후보 관측·성공·실패·legal hold 건수만 Cloud Logging에 기록한다. 재시도 후보는 실행일마다 다시 집계되므로 고유 레코드 수가 아니라 운영 부하 지표로 본다.
- `RETENTION_APPLY_ENABLED`의 기본값은 `false`다. 신규 환경은 dry-run 결과를 먼저 확인한 뒤 apply를 켠다.

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
  - `bodeul_retention_service` 로그인 role의 `NOLOGIN` 기반
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
- PostgreSQL 첨부와 Firestore 전환 첨부는 Storage 삭제 성공 후에만 메타데이터를 정리한다.
- 매니저 본인은 `managerDocumentLegalHoldUntil`을 수정할 수 없고 관리자만 수정할 수 있다.
- 로그에는 건수, 모드, 실패 단계 코드만 남기고 사용자 ID, 메시지, 좌표, 파일 경로를 남기지 않는다.

## 개발 환경 적용 순서

1. `004_retention_runtime.sql`을 개발 DB의 `postgres` 권한으로 적용한다.
2. `bodeul_retention_service`에 개발 전용 비밀번호를 보안 경로에서 설정하고 `LOGIN`을 활성화한다.
3. Flyway V13을 `Core API DB Migration`의 preview 대상으로 적용한다.
4. transaction pooler URL을 `RETENTION_DATABASE_URL` Secret Manager 값으로 등록한다.
5. `cleanupExpiredData`, `reportMonthlyRetention`을 개발 Firebase 프로젝트에 배포한다.
6. 다음 명령으로 dry-run 집계를 확인한다.

```powershell
npm --prefix functions run retention:dry-run -- --project bodeul-dev
```

7. 격리된 만료 fixture에서 다음 명령으로 apply를 한 번 실행하고 DB·Storage·Firestore 결과를 대조한다.

```powershell
npm --prefix functions run retention:apply -- --project bodeul-dev --confirm-project bodeul-dev
```

8. 실패 재시도와 legal hold 결과가 맞으면 개발 환경의 `RETENTION_APPLY_ENABLED=true`로 다시 배포한다.

로컬 실행 시 `RETENTION_DATABASE_URL`은 셸 환경변수로만 주입하며 파일이나 명령 이력에 값을 남기지 않는다.

## 검증 기록

| 검증 | 결과 |
| --- | --- |
| Functions Node 테스트 | 12개 통과 |
| Functions production dependency audit | 알려진 취약점 0건 |
| Core API `check` | 통과 |
| Firestore/Storage Rules Emulator | 7/7 통과 |
| 개발 Supabase V13 트랜잭션 적용 후 rollback | SQL 파싱과 권한 객체 생성 성공, 영구 변경 없음 |
| 개발 Supabase 격리 fixture 리허설 후 rollback | 만료 메시지 1건 비식별화, 위치 1건 삭제, 첨부 1건 완료, legal hold 메시지·위치 보존 확인 |
| Firebase Functions dry-run 배포 분석 | 소스 분석 성공, secret 입력 단계까지 확인 |

## 리스크와 전환 조건

- 하루 500건보다 만료 적재가 빠르면 backlog가 생길 수 있다. 7일 연속 backlog가 줄지 않으면 배치 반복 또는 Cloud Run Job 전환을 검토한다.
- legal hold 설정과 Storage 삭제가 같은 순간에 경합하면 외부 Storage 삭제를 DB 트랜잭션으로 되돌릴 수 없다. 운영에서는 legal hold 변경 중 파기 job을 일시 중지하고, 장기적으로 hold 변경용 관리자 API와 claim 잠금을 연결한다.
- Firestore 전환 문서는 문서 ID cursor로 500개씩 조회한다. 실행 시간이 Functions 한도에 가까워지면 페이지별 checkpoint 또는 Cloud Run Job으로 전환한다.
- Firebase 기본 Storage bucket 이름은 `{projectId}.firebasestorage.app` 규약을 사용한다. 환경별 bucket이 다르면 함수 설정을 분리해야 한다.

## 출시 전 확인

2026-07-19 저장소 확인 결과, 사용자에게 고지할 개인정보 처리방침과 위치기반서비스 이용약관의 기준 원문은 아직 버전 관리되고 있지 않다. 아래 항목은 문서 원문을 추가한 뒤 대조해야 한다.

- [ ] 개인정보 처리방침에 위치 24시간, 채팅 180일, 첨부·증빙 30일 기준이 동일하게 반영됐는지 확인
- [ ] 위치기반서비스 이용약관의 위치정보 이용·보유·파기 문구 확인
- [ ] 개인정보 보호책임자 또는 법률 검토 결과 기록
- [ ] production 전용 retention role과 secret 생성
- [ ] production backup 기준점과 dry-run 결과 승인
- [ ] production apply 활성화 전 격리 fixture 재검증

법률·개인정보 검토는 기술 구현으로 대신할 수 없으므로 완료 전까지 #222를 출시 게이트로 유지한다.
