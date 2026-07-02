# Issue 113 관리자 웹 병원 가이드 API 연결 기록

기준일: 2026-07-02

## 작업 목적

관리자 웹이 `bodeul-api`의 첫 실제 read API를 브라우저에서 호출할 수 있는지 낮은 위험 도메인으로 검증한다.

## 선택한 방식

- `GET /admin/hospital-guides`를 첫 연결 대상으로 유지한다.
- 관리자 웹에는 `VITE_BODEUL_DATA_BACKEND=firebase|api` 전환 플래그를 둔다.
- `VITE_BODEUL_DATA_BACKEND=api`일 때만 병원 가이드 검증 화면에서 `bodeul-api`를 호출한다.
- API 서버는 관리자 웹 브라우저 호출을 위해 `BODEUL_API_ALLOWED_ORIGINS` 기반 CORS preflight를 처리한다.

## 대안

- 매니저 서류 심사 목록을 먼저 API로 전환한다.
- 문의 조회를 먼저 API로 전환한다.
- CORS 없이 서버 간 호출이나 proxy만 전제로 둔다.

## 선택 이유

현재 MVP 규모에서는 매니저 심사 화면 전체를 API로 옮기면 Firestore 모델, Storage 미리보기, 승인 저장까지 한 번에 흔들린다. 병원 가이드는 개인정보 노출 위험이 낮고 기존 서버 API가 이미 있어, 인증/인가/CORS/응답 파싱을 먼저 검증하기에 적합하다.

## 구현한 내용

- `api`에 CORS origin 설정과 `OPTIONS` preflight 응답을 추가했다.
- `admin-web`에 `bodeul-api` client를 추가했다.
- 관리자 웹 메뉴에 `병원 가이드` 검증 화면을 추가했다.
- API 응답의 병원명, 진료과, 단계 수, 갱신 시각을 표시한다.
- `.env.example`, `api/README.md`, `admin-web/README.md`, 관리자 API 계약 문서를 갱신했다.

## 리스크

- 운영/preview 도메인을 `BODEUL_API_ALLOWED_ORIGINS`에 넣지 않으면 브라우저 preflight가 실패한다.
- Firebase Auth 세션은 유효해도 PostgreSQL `app_users.role`에 `ADMIN` 매핑이 없으면 403이 난다.
- 병원 가이드 외 관리자 화면은 아직 Firestore 직접 조회를 사용한다.

## rollback

- 관리자 웹 환경변수를 `VITE_BODEUL_DATA_BACKEND=firebase`로 되돌린다.
- 이 경우 병원 가이드 검증 화면은 API를 호출하지 않고, 기존 매니저 심사 기능은 Firebase 경로를 그대로 사용한다.
