# Firestore Emulator 백업/복원 apply 리허설

기준일: 2026-07-16

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

읽기 전용으로 끝났던 2026-06-25 리허설을 보완해, 운영 데이터에 쓰지 않고도 `restore:state:apply`가 실제로 문서를 삭제하고 백업 상태를 복구하는지 검증한다.

## 선택한 방식

전용 project id `bodeul-restore-rehearsal`과 loopback port `127.0.0.1:8180`을 사용하는 Firestore Emulator에서 다음 round-trip을 실행했다.

1. 기존 로컬 백업으로 Emulator 초기 상태 구성
2. Emulator 상태 재백업
3. 백업 구조 검증
4. 복원 대상에 임시 문서 1건과 임시 필드 1개 추가
5. 복원 dry-run
6. 복원 apply
7. 전용 diff와 운영 workflow strict 검증

입력 백업과 생성된 JSON/HTML 결과는 기존 `.gitignore` 기준에 따라 커밋하지 않는다.

## 대안

- 별도 Firebase 개발 프로젝트를 만들어 실제 원격 Firestore에 복원한다.
- `bodeul-dev`에 직접 복원한 뒤 diff를 확인한다.
- 기존 읽기 전용 리허설만 유지한다.

## 선택 이유

현재 개발 인프라에서는 데이터 삭제가 포함된 복원 로직 자체를 증명하는 것이 우선이다. Emulator는 네트워크와 운영 권한 없이 같은 Firestore REST 문서 형식을 사용해 apply 경로를 반복 검증할 수 있고, 실패해도 `bodeul-dev` 데이터에 영향을 주지 않는다.

## 안전 경계

- 공용 Firebase 도구는 `FIRESTORE_EMULATOR_HOST`가 없으면 기존 Google Firestore endpoint를 사용한다.
- Emulator host는 `localhost`, `127.0.0.1`, `::1`과 명시적 port만 허용한다.
- 리허설 스크립트는 project id가 `bodeul-restore-rehearsal`이 아니면 종료한다.
- `.firebaserc`의 기본 프로젝트 `bodeul-dev`는 변경하지 않았다.
- `Auth`, Storage, Functions 환경값은 이번 복원 범위에 포함하지 않았다.

## 실행 명령

```powershell
cd D:\BoDeul
$env:FIREBASE_PROJECT_ID='bodeul-restore-rehearsal'
npx --prefix tools/firebase firebase emulators:exec `
  --only firestore `
  --project bodeul-restore-rehearsal `
  --config firebase.restore-rehearsal.json `
  "npm --prefix tools/firebase run rehearse:restore:emulator -- --file backups/firestore-backup-20260625-rehearsal.json"
```

`workflow:ops`는 `--firestore-only --no-app-evidence`로 실행했다. 따라서 Auth 로그인을 검증한 것으로 간주하지 않고, users 문서와 Firestore 관계만 검사하며 과거 앱 화면 증적도 이번 결과에 포함하지 않는다.

## 결과

| 항목 | 결과 |
| --- | --- |
| Emulator project | `bodeul-restore-rehearsal` |
| Emulator endpoint | `127.0.0.1:8180` |
| 복원 문서 | 15개 컬렉션, 총 77건 |
| 백업 구조 | 오류 0, 경고 0 |
| 임시 문서 제거 | 성공 |
| 임시 필드 제거 | 성공 |
| 전용 diff | 추가 0, 삭제 0, 변경 0 |
| workflow diff | 추가 0, 삭제 0, 변경 0 |
| Firestore 역할 관계 | 4/4 |
| 샘플 시나리오 | 3/3 |
| 전체 측정 시간 | 5,272ms |
| 복원 apply | 557ms |

측정 시간은 로컬 Emulator 기준이므로 원격 장애 복구의 RTO로 사용하지 않는다.

## 실행 중 확인한 문제

1. 기본 port `8080`이 다른 로컬 프로세스에 사용 중이었다. 기존 프로세스를 종료하지 않고 리허설 전용 config와 port `8180`을 추가했다.
2. 기존 workflow는 Firestore Emulator에서도 production Firebase Auth endpoint를 조회했다. Firestore 백업은 Auth를 포함하지 않으므로 `--firestore-only` 범위를 추가하고 보고서에 Auth 미검증 상태를 명시했다.
3. workflow diff는 timestamp field를 JS 문자열로 변환한 뒤 다시 인코딩해 53건을 변경으로 잘못 계산했다. 현재 snapshot에 원본 Firestore field를 보존해 전용 diff와 같은 기준으로 비교하도록 수정했다.

## 검증

```powershell
npm --prefix tools/firebase run test:toolkit
```

- Emulator endpoint 안전 경계 테스트 4건 통과
- Firestore 전용 기준선과 원본 field diff 테스트 2건 통과
- 실제 Emulator round-trip 성공, 종료 코드 0

## 리스크와 남은 범위

- Firebase Authentication 계정은 Firestore 백업과 별도이므로 별도 export/import 절차가 필요하다.
- Storage 객체와 Functions secret/환경값은 이번 리허설에서 복구하지 않았다.
- Emulator 결과는 복원 코드의 쓰기 동작을 증명하지만, 원격 네트워크·IAM·쿼터·대용량 복구 시간을 증명하지 않는다.
- production 전에는 별도 비운 Firebase 프로젝트에서 원격 복원 리허설을 한 번 더 수행하는 것이 좋다.

## 판단

이슈 #64의 핵심 조건인 격리 환경 `restore apply`와 복원 후 diff 검증은 완료됐다. Firestore 문서 복원 도구는 현재 개발 데이터 규모에서 반복 실행 가능한 상태이며, Auth/Storage/Functions 복구는 별도 재해 복구 범위로 관리한다.
