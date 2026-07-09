# Issue 123 실제 배포 API 응답 비교 기록

기준일: 2026-07-08

## 작업 목적

#123에서 로컬 비교 이후 남아 있던 실제 배포 API 응답 비교 결과를 공개 가능한 범위로 기록한다.

## 근거

이 문서는 2026-07-08 GitHub Issue #123, #140 댓글에 남겨진 팀원 검증 결과를 문서화한 것이다. 이 문서 작성 시점에 Codex가 Oracle VM이나 Supabase에 직접 접속해 재검증한 기록은 아니다.

## 실행 환경

| 항목 | 기준 |
| --- | --- |
| API 서버 | Oracle Free Tier VM에서 실행한 `bodeul-api` |
| API endpoint | `GET /admin/hospital-guides?limit=50` |
| DB | Supabase PostgreSQL |
| 인증 | Firebase Admin SDK + Firebase ID token |
| 인가 | PostgreSQL `app_users.role == 'ADMIN'` |
| 관리자 웹 | 로컬 `admin-web` API 모드 |
| 비교 도구 | `npm run compare:hospital-guides` |

공개 문서에는 서버 접속 비밀값, DB 접속 문자열, Firebase service account JSON, Firebase ID token, Supabase password를 남기지 않는다.

## 확인 결과

- Oracle 환경에서 `bodeul-api`가 외부 `/healthz` 요청에 HTTP 200으로 응답했다.
- API 서버에서 Supabase PostgreSQL 연결과 `hospital_guides` 조회가 확인됐다.
- Firebase ID token 기반 인증과 PostgreSQL `ADMIN` role 인가가 통과했다.
- 인증된 `GET /admin/hospital-guides?limit=50` 호출이 성공했다.
- 로컬 관리자 웹을 `VITE_BODEUL_DATA_BACKEND=api`로 실행했을 때 병원 가이드 1건이 표시됐다.
- 실제 API 응답 JSON과 Firestore 기준 데이터를 `compare:hospital-guides`로 비교한 결과가 `passed`로 기록됐다.

## 비교 결과 요약

| 항목 | 결과 |
| --- | --- |
| Firestore 기준 | 1건 |
| API 응답 | 1건 |
| 일치 | 1건 |
| 누락 | 0건 |
| 추가 | 0건 |
| 불일치 | 0건 |
| 최종 상태 | `passed` |

## 판단

병원 가이드 read API의 현재 1건 데이터 기준으로는 Firestore 기준 데이터와 Supabase/API 응답이 일치한다. 따라서 #123의 실제 배포 API 응답 비교 blocker는 해소된 상태로 본다.

이 결과는 production 전환이나 관리자 웹 레포 분리 실행을 의미하지 않는다. production Firebase 프로젝트, Hosting site, Auth domain, App Check, live WIF 조건은 #134에서 확정해야 한다. Vercel 또는 Firebase Hosting preview URL 기반 팀 공유 화면 검증은 #140 후속 작업으로 분리한다.

## 보안 메모

- `DATABASE_URL`, Firebase service account JSON, Firebase ID token, Supabase password는 문서에 적지 않는다.
- 검증 결과에는 환경 종류, 성공 여부, 응답 건수, 비교 상태만 남긴다.
- 서버 주소나 계정 정보는 운영 이관 기준이 확정되기 전까지 공개 문서에 반복 기재하지 않는다.

## 관련 이슈와 문서

- [Issue #123 병원 가이드 Firestore/API 응답 비교 기록](https://github.com/bodeul110/Bodeul/issues/123)
- [Issue #140 Oracle/Vercel 기반 관리자 웹 API 모드 실연동 검증 환경 구축](https://github.com/bodeul110/Bodeul/issues/140)
- [병원 가이드 Firestore/API 응답 비교 기록](hospital-guide-firestore-api-comparison-2026-07-06.md)
- [PostgreSQL API 경계 기준](../architecture/postgres-api-boundary.md)
