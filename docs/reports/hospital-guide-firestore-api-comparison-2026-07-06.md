# 병원 가이드 Firestore/API 응답 비교 기록

기준일: 2026-07-06

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

#113 / PR #121에서 연결한 관리자 웹 병원 가이드 API가 기존 Firestore 기준 데이터와 같은 목록을 내는지 확인한다.

## 선택한 방식

`bodeul-dev` Firestore 백업 파일을 기준 데이터로 삼고, 같은 백업을 PostgreSQL seed 입력으로 변환한 뒤 `api/src/hospital-guides.ts`의 API DTO 변환 경로를 통과시켜 비교했다.
이후 seed 기반 reader를 `createApiServer`에 주입해 테스트용 Firebase verifier와 관리자 role authorizer로 `GET /admin/hospital-guides?limit=50` HTTP 응답까지 확인했다.

이번 비교는 운영 DB에 쓰기 작업을 하지 않는 로컬 검증이다. `DATABASE_URL`, Firebase ID token, 서비스 계정 원문은 문서와 이슈에 기록하지 않는다.

## 대안

- 로컬 또는 배포된 `bodeul-api`에 실제 Firebase ID token을 붙여 `/admin/hospital-guides?limit=50`을 호출한다.
- Supabase SQL Editor에서 `hospital_guides`를 직접 조회하고 수동으로 비교한다.
- 관리자 웹 화면에서 API 모드를 켜고 눈으로만 비교한다.

## 선택 이유

현재 로컬 세션에는 `DATABASE_URL`, `FIREBASE_SERVICE_ACCOUNT_JSON`, 실제 관리자 Firebase ID token이 설정되어 있지 않다.
그래서 라이브 DB/API 호출까지 곧바로 진행하지 않고, 이미 검증된 Firestore 백업과 seed 변환 경로를 사용해 데이터 변환, API DTO 계약, HTTP 라우트 응답을 먼저 확인했다.

## 리스크

- 이 기록만으로는 배포된 API 서버와 실제 Supabase 개발 DB 연결 상태를 증명하지 못한다.
- Firebase ID token 검증, PostgreSQL role 기반 관리자 연결, CORS, API 호스트 설정은 테스트 주입 기반 로컬 라우트 호출로는 확인되지 않는다.
- `steps` 내부 필드는 아직 운영 UI 계약으로 고정하지 않았기 때문에 단계 수와 title 목록만 비교했다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| Firestore 기준 백업 | `tools/firebase/backups/firestore-backup-20260625-rehearsal.json` |
| 백업 생성 시각 | `2026-06-25T14:44:58.445Z` |
| Firebase 프로젝트 | `bodeul-dev` |
| seed 입력 출력 | `tools/firebase/reports/issue-123-hospital-guides-seed-input.json` |
| API DTO 경로 | `api/src/hospital-guides.ts` |
| API 라우트 경로 | `api/src/server.ts` |
| API limit | `50` |

`tools/firebase/backups/*.json`과 `tools/firebase/reports/*` 산출물은 운영 데이터가 포함될 수 있어 `.gitignore` 대상이며 커밋하지 않는다.

## 실행 명령

```powershell
npm --prefix tools/firebase run validate:backup -- --file backups/firestore-backup-20260625-rehearsal.json
npm --prefix tools/firebase run postgres:seed:build -- backups/firestore-backup-20260625-rehearsal.json reports/issue-123-hospital-guides-seed-input.json
npm --prefix tools/firebase run compare:hospital-guides -- -- --backup backups/firestore-backup-20260625-rehearsal.json --seed-input reports/issue-123-hospital-guides-seed-input.json --output reports/issue-123-hospital-guides-comparison.json --markdown reports/issue-123-hospital-guides-comparison.md
npm --prefix api run check
```

추가로 생성한 seed 입력의 `hospital_guides` row를 `createPostgresHospitalGuideReader`에 전달해 API DTO 변환 결과를 비교했다.
같은 reader를 `createApiServer`에 주입하고 테스트용 인증/인가 객체로 `GET /admin/hospital-guides?limit=50` HTTP 응답을 확인했다.

## 재검증 도구

이번 작업에서는 비교 절차를 반복할 수 있도록 `tools/firebase/compare-hospital-guides-api.js`를 추가했다.
이 도구는 네트워크 호출을 하지 않고, Firestore 백업과 API 응답 JSON 또는 PostgreSQL seed 입력 JSON만 비교한다.

seed 입력 기준 재검증:

```powershell
npm --prefix tools/firebase run compare:hospital-guides -- -- --backup backups/firestore-backup-20260625-rehearsal.json --seed-input reports/issue-123-hospital-guides-seed-input.json
```

실제 API 응답 기준 재검증:

```powershell
npm --prefix tools/firebase run compare:hospital-guides -- -- --backup backups/firestore-backup-20260625-rehearsal.json --api-response reports/hospital-guides-api-response.json
```

npm/PowerShell 조합에 따른 인자 전달 차이를 피하려면 `tools/firebase` 디렉터리에서 직접 실행해도 된다.
비교 도구는 옵션형 입력과 위치 인자 입력을 모두 허용한다.

```powershell
cd tools/firebase
node compare-hospital-guides-api.js --backup backups/firestore-backup-20260625-rehearsal.json --seed-input reports/issue-123-hospital-guides-seed-input.json
node compare-hospital-guides-api.js backups/firestore-backup-20260625-rehearsal.json reports/issue-123-hospital-guides-seed-input.json
```

`reports/hospital-guides-api-response.json`은 실제 `/admin/hospital-guides?limit=50` 응답을 저장한 파일이어야 한다.
관리자 Firebase ID token, `DATABASE_URL`, 서비스 계정 키는 이 파일에 넣지 않는다.

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| Firestore 백업 검증 | 오류 0건, 경고 0건 |
| PostgreSQL seed 입력 상태 | `passed` |
| seed 진단 | 0건 |
| 비교 도구 결과 | `passed` |
| API typecheck/build/test | 통과, 50 tests passed |
| 로컬 API 라우트 호출 | HTTP 200, `items.length=1`, `limit=50` |

## row count 비교

| 기준 | count |
| --- | ---: |
| Firestore `hospitalGuides` | 1 |
| PostgreSQL seed `hospital_guides` | 1 |
| API DTO `items` | 1 |
| 로컬 API HTTP 응답 `items` | 1 |

## 주요 필드 spot check

| 필드 | Firestore 기준 | API DTO 기준 | 결과 |
| --- | --- | --- | --- |
| 원본 문서 ID | `guide-seed-seoul-internal-medicine` | deterministic UUID `324431eb-8e0d-5427-bbb2-a31a71e6d7c4` | 매핑 규칙 정상 |
| `hospitalName` | `서울내과병원` | `서울내과병원` | 일치 |
| `departmentName` | `내과` | `내과` | 일치 |
| `steps.length` | 7 | 7 | 일치 |
| `steps[0..2].title` | `환자 접수`, `접수 등록`, `진료 접수` | `환자 접수`, `접수 등록`, `진료 접수` | 일치 |
| `createdAt` | `2026-04-23T16:48:39.766Z` | `2026-04-23T16:48:39.766Z` | 일치 |
| `updatedAt` | `2026-04-23T16:48:39.766Z` | `2026-04-23T16:48:39.766Z` | 일치 |

API 응답의 `id`는 Firestore 문서 ID를 그대로 노출하지 않는다.
seed 변환 규칙에 따라 `hospital_guides:<Firestore 문서 ID>`를 deterministic UUID로 바꾸며, 같은 백업을 다시 변환해도 같은 UUID가 생성된다.

## 판단

병원 가이드 목록 수준에서는 Firestore 백업 기준 row와 PostgreSQL seed/API DTO/로컬 API HTTP 응답 row가 일치했다.
현재 비교 결과만 놓고 보면 관리자 웹의 병원 가이드 화면을 API 모드로 검증할 수 있는 최소 데이터 계약은 충족한다.

다만 이 결과는 로컬 테스트 주입 기반 API 호출 검증이다.
실제 운영 전환 판단에는 배포된 `bodeul-api`, 실제 Supabase 개발 DB, Firebase 관리자 ID token으로 `/admin/hospital-guides?limit=50`을 호출한 결과를 추가해야 한다.

## 남은 범위

- `DATABASE_URL`과 Firebase 인증이 준비된 환경에서 실제 `GET /admin/hospital-guides?limit=50` 호출 결과를 저장하고 `compare:hospital-guides`로 비교한다.
- 관리자 웹 `VITE_BODEUL_DATA_BACKEND=api` 모드에서 같은 1건이 표시되는지 확인한다.
- 병원 가이드 상세 UI 계약이 고정된 시점에는 `steps` 내부 전체 필드 비교를 별도 이슈로 분리한다.

## 관련 이슈

- [#113 관리자 웹 API 연결](https://github.com/bodeul110/Bodeul/issues/113)
- [#123 병원 가이드 Firestore/API 응답 비교 기록](https://github.com/bodeul110/Bodeul/issues/123)
