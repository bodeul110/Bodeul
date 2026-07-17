# 관리자 웹 데이터 계약

기준일: 2026-07-17

관리자 웹 source of truth는 별도 [bodeul-admin-web 저장소](https://github.com/bodeul110/bodeul-admin-web)다. 이 문서는 메인 저장소의 Firebase Rules·Storage·PostgreSQL schema 변경이 관리자 웹에 미치는 공용 계약만 관리한다.

## 인증 계약

| 항목 | 기준 |
| --- | --- |
| 로그인 | Firebase Auth 이메일/비밀번호 |
| 화면 진입 | Firestore `users/{uid}.role == ADMIN` |
| 서버 API | Firebase ID token + PostgreSQL `app_users.role == ADMIN` |
| 비관리자 | 세션 종료 또는 API 403 |
| 유휴 세션 | 15분 비활동 시 로그아웃 |
| App Check | production 전 reCAPTCHA Enterprise와 custom backend 검증 필요 |

화면 진입의 Firestore role과 서버 최종 인가의 PostgreSQL role은 전환 기간에 모두 유지한다. 역할 변경 시 두 저장소의 동기화와 감사 이력이 필요하다.

## Firestore 계약

관리자 웹의 기존 운영 화면은 다음 범위를 사용한다.

| 범위 | 읽기/쓰기 | 주요 필드 |
| --- | --- | --- |
| `users/{uid}` | 읽기 | `role`, `name`, `email` |
| `users` 중 `role == MANAGER` | 읽기 | 연락처, 신청일, 서류 상태·요약·경로 |
| 매니저 심사 결과 | 쓰기 | 상태, 검토 메모, 검토 시각·관리자, 이력 |

Firestore 직접 접근을 서버 API로 옮길 때는 Rules를 먼저 제거하지 않는다. API 실제 검증과 rollback이 끝난 화면 단위로 직접 접근 범위를 줄인다.

## Storage 계약

- 매니저 신분증, 자격증, 범죄경력 확인 파일의 Storage 경로를 읽는다.
- 목록에서는 민감정보를 마스킹하고 상세 미리보기에서만 원본 URL을 사용한다.
- 원본 파일은 Storage에 유지하고 심사 상태·감사 메타데이터만 PostgreSQL 이전 후보로 둔다.
- Storage Rules 변경 PR은 관리자 미리보기 영향을 확인한다.

## PostgreSQL 관리자 API 계약

현재 첫 서버 경계는 `GET /admin/hospital-guides?limit=50`이다.

- Authorization이 없으면 401
- 유효한 token이지만 `ADMIN`이 아니면 403
- 관리자이면 200과 병원 가이드 목록
- DB 접속은 서버 전용 `bodeul_admin_service`를 사용
- 현재 runtime 권한은 필요한 SELECT만 허용

관리자 쓰기 API를 추가할 때는 DML grant, 감사 로그, 입력 검증, idempotency와 rollback을 함께 정의한다.

## 환경변수

- 공개 Firebase Web 설정만 `NEXT_PUBLIC_*`로 둔다.
- `FIREBASE_PROJECT_ID`, `ADMIN_DATABASE_URL`은 서버 환경변수다.
- DB URL과 token을 브라우저 번들, 로그, PR에 남기지 않는다.
- Preview와 production 값을 공유하지 않는다.

## 변경 규칙

다음 변경은 메인·관리자 저장소 이슈를 서로 링크한다.

- Firestore/Storage Rules
- `users` 필드 또는 Storage 경로
- PostgreSQL schema와 관리자 role grant
- Firebase Auth/App Check 정책
- Functions callable 계약

관련 문서: [관리자 웹 구조](admin-web-architecture.md), [관리자 웹 환경 기준](../operations/admin-web-environments.md)
