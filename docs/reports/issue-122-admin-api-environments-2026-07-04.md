# Issue 122 관리자 웹 API 환경변수와 CORS 기준 정리

기준일: 2026-07-04

## 작업 목적

관리자 웹이 preview/운영 환경에서 `bodeul-api`를 호출할 때 필요한 환경변수와 CORS origin 기준을 문서화했다.

## 선택한 방식

기존 관리자 웹 기본값은 `VITE_BODEUL_DATA_BACKEND=firebase`로 유지한다. 병원 가이드 read API 검증이 필요한 환경에서만 `api`로 전환하고, API 서버는 `BODEUL_API_ALLOWED_ORIGINS`에 등록된 origin만 허용한다.

## 대안

- 관리자 웹을 API 모드로 고정한다.
- CORS를 전체 origin 허용으로 둔다.
- preview와 production이 같은 API URL을 공유한다.

## 선택 이유

현재 MVP 규모에서는 기존 Firebase 기반 관리자 기능을 유지하면서 낮은 위험 read API만 검증하는 편이 안전하다. CORS 전체 허용은 빠르지만 관리자 API 경계 검증 목적과 맞지 않아 환경별 allow-list 기준으로 정리했다.

## 구현한 내용

- `docs/operations/admin-api-environments.md`를 추가해 local, preview, production 환경별 값을 정리했다.
- 관리자 웹 GitHub Environment와 API 서버 환경변수의 설정 위치를 분리했다.
- `VITE_BODEUL_DATA_BACKEND=firebase` rollback 기준을 문서화했다.
- CORS preflight 성공/실패 증상과 확인 명령을 정리했다.
- 기존 관리자 API 계약, 관리자 웹 README, API README에서 새 운영 기준 문서로 연결했다.

## 변경된 범위

- `../operations/admin-api-environments.md`
- `../operations/README.md`
- `../architecture/admin-api-contract.md`
- `../../admin-web/README.md`
- `../../api/README.md`
- `../reports/README.md`
- `../reports/issue-122-admin-api-environments-2026-07-04.md`
- `../status/implementation-status.md`

## 검증

- `npm --prefix api run check` 통과. CORS preflight 계약 테스트를 포함해 API 테스트 50개가 모두 통과했다.
- 로컬 API 서버를 `127.0.0.1:18080`으로 임시 실행하고 `OPTIONS /admin/hospital-guides?limit=50` preflight를 확인했다.
- 로컬 preflight 응답은 HTTP `204`, `Access-Control-Allow-Origin: http://localhost:5173`, `Access-Control-Allow-Headers: Authorization, Content-Type`이었다.
- `git diff --cached --check` 통과.

## 남은 범위

- preview API 배포 URL이 확정되면 `admin-web-preview`의 `VITE_BODEUL_API_BASE_URL`과 API 서버의 `BODEUL_API_ALLOWED_ORIGINS`를 실제 URL로 설정한다.
- production API 배포 URL이 확정되면 `admin-web-production`과 운영 API 서버 환경변수를 별도로 설정한다.
- 병원 가이드 Firestore/API 응답 비교는 Issue #123에서 계속 추적한다.
