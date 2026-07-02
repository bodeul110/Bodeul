# Issue 88 API 서버 골격 1차 기록

기준일: 2026-06-30

## 작업 목적

`bodeul-api` 서버 골격을 추가하고, PostgreSQL 접근 경계 작업을 시작하기 전에 로컬에서 실행 가능한 최소 헬스체크와 관리자 웹 초기 계약 확인 API를 만든다.

## 선택한 방식

1차 범위에서는 외부 서버 프레임워크를 추가하지 않고 Node 22 기본 `http` 모듈과 TypeScript로 구성한다.

- `api/` 디렉터리 추가
- Node 22 + TypeScript 설정
- `GET /healthz`와 `HEAD /healthz` 지원
- `GET /admin/api-contract`와 `HEAD /admin/api-contract` 지원
- Firebase ID token 검증 유틸을 관리자 API 라우트에 연결
- `DATABASE_URL` 누락/형식 검증 추가
- 지원하지 않는 메서드는 405 반환
- 없는 경로는 404 반환
- API 전용 GitHub Actions workflow 추가
- 기존 Android Preflight에서 `api/` 경로를 미분류로 보지 않도록 범위 분류 추가
- 관리자 API 초기 응답 계약 문서 추가

## 대안

- Fastify를 1차부터 추가한다.
- Express를 사용한다.
- Firebase Functions 안에 API를 둔다.
- 운영 데이터 조회 API까지 한 PR에 포함한다.

## 선택 이유

현재 1차 목표는 배포 가능한 도메인 API 전체가 아니라 서버 실행 경계와 CI 검증 경로, 관리자 웹이 붙을 최소 응답 계약을 만드는 것이다. 외부 프레임워크와 실제 DB/Firebase Admin SDK 연결을 동시에 추가하면 리뷰 범위가 커지므로, 먼저 Node 기본 서버로 헬스체크, 인증 유틸 연결 지점, 설정 검증, TypeScript 빌드 경로를 고정한다.

## 리스크

- Node 기본 `http`만으로는 라우팅, validation, plugin 구조가 부족하다.
- Firebase Admin SDK와 PostgreSQL client가 아직 없으므로 운영 API로 사용할 수 없다.
- 후속 PR에서 Fastify 같은 프레임워크를 도입하면 일부 구조를 조정해야 할 수 있다.

## 검증

| 항목 | 결과 |
| --- | --- |
| `npm install` in `api/` | 통과, 취약점 0건 |
| `npm run check` in `api/` | 통과 |
| `npm --prefix api run check` | 통과, 테스트 19개 성공 |
| `yq e '.' .github/workflows/api.yml` | 통과 |
| `yq e '.' .github/workflows/android-preflight.yml` | 통과 |
| `git diff --check` | 통과 |
| `GET /healthz` 실제 HTTP 호출 | 200, `status: ok`, `service: bodeul-api` 확인 |
| 신규 API 파일 BOM 확인 | BOM 없음 |

## 남은 범위

- Firebase Admin SDK 실제 초기화
- PostgreSQL DB client 초기화
- PostgreSQL role 기반 관리자 권한 확인
- 병원 가이드, 매니저 서류 심사, 문의 조회 중 하나를 실제 read API로 승격
- Oracle VM 또는 배포 환경 선택
- 관리자 웹/Android 연동
