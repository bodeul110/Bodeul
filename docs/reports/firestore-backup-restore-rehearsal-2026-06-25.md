# Firestore 백업/복원 리허설 기록

기준일: 2026-06-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Firestore 백업 도구가 실제 `bodeul-dev` 데이터를 백업하고, 복원 계획을 손상 없이 산출할 수 있는지 확인한다.

## 선택한 방식

운영 쓰기가 없는 읽기 전용 리허설로 `backup -> validate -> restore dry-run -> diff` 순서를 실행했다.

## 대안

- `restore:state:apply`까지 즉시 실행한다.
- 별도 Firebase 프로젝트 또는 emulator에 백업을 복원한 뒤 앱/관리자 웹으로 확인한다.

## 선택 이유

현재 Firebase CLI에서 확인되는 접근 가능 프로젝트는 `bodeul-dev` 1개뿐이다. `restore:state:apply`는 관리 대상 컬렉션 문서를 삭제한 뒤 백업 문서를 다시 쓰는 흐름이므로, 운영 기준 프로젝트에 바로 실행하면 데이터 손상 리스크가 크다. 따라서 이번에는 읽기 전용 검증으로 백업 파일, 복원 계획, 현재 Firestore와의 차이만 확인했다.

## 리스크

- 실제 write 복원은 아직 검증하지 않았다.
- Authentication 계정, Storage 객체, Functions 환경 변수는 Firestore 백업 파일에 포함되지 않는다.
- 격리 프로젝트 또는 emulator 복원 대상이 준비되기 전까지는 운영 복구 가능성을 완전히 증명했다고 볼 수 없다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| Firebase 프로젝트 | `bodeul-dev` |
| Storage bucket | `bodeul-dev.firebasestorage.app` |
| 백업 파일 | `tools/firebase/backups/firestore-backup-20260625-rehearsal.json` |
| 백업 생성 시각 | `2026-06-25T14:44:58.445Z` |
| 접근 가능 Firebase 프로젝트 | `bodeul-dev` 1개 |

생성된 백업 파일은 `.gitignore` 대상인 `tools/firebase/backups/*.json`에 해당하므로 저장소에 커밋하지 않는다.

## 실행 명령과 결과

| 순서 | 명령 | 결과 |
| --- | --- | --- |
| 1 | `npm --prefix tools/firebase run backup:state -- --output backups/firestore-backup-20260625-rehearsal.json` | 성공 |
| 2 | `npm --prefix tools/firebase run validate:backup -- --file backups/firestore-backup-20260625-rehearsal.json` | 오류 0건, 경고 0건 |
| 3 | `npm --prefix tools/firebase run restore:state:dry-run -- --file backups/firestore-backup-20260625-rehearsal.json` | 실제 삭제/복원 없이 복원 계획 산출 |
| 4 | `npm --prefix tools/firebase run diff:state -- --file backups/firestore-backup-20260625-rehearsal.json` | 추가 0건, 삭제 0건, 변경 0건 |
| 5 | `firebase projects:list --json` | 접근 가능 프로젝트 `bodeul-dev`만 확인 |

## 관리 컬렉션 문서 수

| 컬렉션 | 백업 문서 수 | dry-run 현재 문서 수 | dry-run 복원 문서 수 |
| --- | ---: | ---: | ---: |
| `users` | 6 | 6 | 6 |
| `hospitalGuides` | 1 | 1 | 1 |
| `appointmentRequests` | 4 | 4 | 4 |
| `companionSessions` | 2 | 2 | 2 |
| `sessionReports` | 2 | 2 | 2 |
| `appointmentFollowUps` | 1 | 1 | 1 |
| `supportInquiries` | 4 | 4 | 4 |
| `clientSupportRequests` | 4 | 4 | 4 |
| `adminSettlementRecords` | 1 | 1 | 1 |
| `adminEmergencyIssues` | 1 | 1 | 1 |
| `adminActionNotifications` | 9 | 9 | 9 |
| `adminAuditLogs` | 10 | 10 | 10 |
| `adminActionDeliveries` | 22 | 22 | 22 |
| `adminActionDeliveryJobs` | 9 | 9 | 9 |
| `appointmentReminderJobs` | 1 | 1 | 1 |

## 판단

읽기 전용 리허설 기준으로는 백업 파일 구조가 유효하고, 백업 직후 현재 Firestore와 차이가 없다. 따라서 현 도구는 현재 관리 컬렉션을 백업하고 복원 계획을 산출하는 데 사용할 수 있다.

다만 실제 복구 가능성은 격리 프로젝트 또는 emulator에서 `restore:state:apply`를 수행한 뒤 앱/관리자 웹 화면까지 확인해야 증명된다.

## 남은 범위

- 격리 Firebase 프로젝트 또는 Firestore emulator 복원 대상을 준비한다.
- 격리 대상에서 `restore:state:apply`를 실행하고, `diff:state`, `check:readiness`, 관리자 웹 주요 화면으로 복구 결과를 확인한다.
- Storage 객체, Auth 계정, Functions 환경 변수까지 포함한 재해 복구 절차를 별도 문서로 확장한다.
