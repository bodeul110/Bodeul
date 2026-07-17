# Issue 159 Node API 종료 기록

기준일: 2026-07-17

## 작업 목적

기존 `api/` Node 프로토타입이 담당하던 계약을 Spring Core API와 Next.js 관리자 서버가 실제로 대체했는지 확인하고, 대체 완료 시 소스·CI·배포 설정을 함께 제거한다.

## 판단 기준과 결과

| 계약 | 대체 구현 | 실제 확인 | 결과 |
| --- | --- | --- | --- |
| 배포 상태 확인 | Spring `/health` | Cloud Run 200, revision rollback | 대체 완료 |
| Firebase ID token | Spring과 Next.js | 정상·변조·무인증 경계 | 대체 완료 |
| 사용자 PostgreSQL role | Spring Core API | 실제 token과 DB role 연결 | 대체 완료 |
| Kakao Local REST | Spring `/api/places/search` | 인증된 실호출, Android 직접 키 제거 | 대체 완료 |
| 관리자 `ADMIN` role | Next.js Route Handler | 비관리자 403, 관리자 200 | 대체 완료 |
| 병원 가이드 조회 | Next.js `/admin/hospital-guides` | 개발 DB 결과 1건과 응답 계약 확인 | 대체 완료 |
| 관리자 rollback | 별도 저장소 Vite build | CI에서 별도 build 유지 | Node API와 독립 |

## 관리자 Preview 실검증

- 무인증 요청: 401 `missing_authorization`
- 임시 비관리자: 403 `admin_role_required`
- 임시 관리자: 200, 병원 가이드 목록 반환
- 임시 Firebase 사용자와 `app_users` row: 검증 후 삭제, 잔여 0건
- DB 접속: Preview 전용 `bodeul_admin_service`, SELECT 전용, connection limit 5
- TLS: 공식 Supabase Root CA를 명시하고 인증서 검증 유지
- production Vercel environment: `ADMIN_DATABASE_URL` 미등록
- 최신 master deployment: Ready, 기본 alias 루트 200, 무인증 관리자 API 401
- Vercel project: GitHub `master` 연결, `live=false`, custom domain 0개
- Supabase Security Advisor: 경고 0건
- Supabase Performance Advisor: 신규 예약 조회 인덱스와 Flyway 내부 인덱스의 미사용 INFO 2건

비밀값, token 원문과 테스트 계정 정보는 공개 문서나 PR에 기록하지 않았다.

## 제거 범위

- 메인 저장소 `api/` 전체
- `.github/workflows/api.yml`
- 메인 저장소 `admin-web/` 중복본
- 메인 저장소 관리자 build·Firebase Hosting preview workflow
- 루트 `firebase.json`의 관리자 Hosting 설정
- 메인 Dependabot, CodeQL, preflight의 삭제 경로 분류
- Node/API·중복 관리자 웹을 현재 구조로 설명하던 README와 인프라 문서
- 메인·관리자 저장소의 기존 관리자 Hosting용 GitHub Environment
- Firebase Hosting site와 관리자 배포 전용 WIF provider·서비스 계정·IAM binding

별도 `bodeul-admin-web` 저장소의 Next.js 구현, Vite rollback과 Vercel CI는 유지한다. 과거 Node 검증 보고서는 당시 의사결정 이력으로 보존한다.

## 선택 이유

대체 구현의 실제 401·403·200과 DB 결과를 확인한 뒤 삭제했으므로 계약 공백이 없다. 프로토타입을 더 유지하면 Dependabot·CodeQL·workflow 비용이 늘고, 관리자 API의 source of truth가 Node인지 Next.js인지 다시 모호해진다.

외부 자원은 2026-07-17에 추가로 정리했다. Firebase Hosting 비활성화 후 기존 URL의 404 응답을 확인했고, 관리자 Hosting 배포에만 쓰던 WIF와 서비스 계정도 제거했다. Core API Preview용 WIF와 Vercel deployment environment는 현재 배포 경계이므로 유지한다.

Performance Advisor의 두 항목은 개발 트래픽이 아직 없는 신규 예약 조회 인덱스와 Flyway가 관리하는 내부 인덱스다. 조회 계약과 migration 도구의 관리 범위를 훼손할 수 있어 현재는 삭제하지 않고 실제 쿼리 통계가 쌓인 뒤 재평가한다.

## 리스크와 대응

| 리스크 | 대응 |
| --- | --- |
| 과거 Node 응답과의 회귀 비교가 어려움 | Git 이력과 기존 계약·검증 보고서 보존 |
| 관리자 장애 시 rollback 경로 상실 | 별도 저장소 Vite build 유지 |
| production 준비로 오해 | Preview 전용 DB 자격 증명만 유지하고 production 미설정 명시 |
| 두 저장소 계약 불일치 | 공용 schema는 메인 Flyway만 소유하고 이슈를 상호 링크 |

## 결론

Node API 종료 조건은 충족됐다. 메인 저장소에서 프로토타입과 중복 관리자 웹을 제거하고, 운영 후보를 Spring Core API와 Next.js 관리자 서버로 단일화한다. 남은 작업은 Node 종료가 아니라 production 환경 분리와 도메인별 PostgreSQL source of truth 전환이다.

## 관련

- [Issue #159](https://github.com/bodeul110/Bodeul/issues/159)
- [Issue #135](https://github.com/bodeul110/Bodeul/issues/135)
- [목표 인프라 구조](../architecture/target-infrastructure.md)
- [관리자 웹 구조](../architecture/admin-web-architecture.md)
