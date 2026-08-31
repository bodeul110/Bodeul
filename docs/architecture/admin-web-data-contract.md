# 관리자 웹 데이터 계약

기준일: 2026-08-30

관리자 웹 source of truth는 별도 [bodeul-admin-web 저장소](https://github.com/bodeul110/bodeul-admin-web)다. 이 문서는 메인 저장소의 Firebase Rules·Storage·PostgreSQL schema 변경이 관리자 웹에 미치는 공용 계약만 관리한다.

## 인증 계약

| 항목 | 기준 |
| --- | --- |
| 로그인 | Firebase Auth 이메일/비밀번호 |
| 화면 진입 | Firestore `users/{uid}.role == ADMIN` |
| 서버 API | Firebase ID token + PostgreSQL `app_users.role == ADMIN` + 활성 세부 역할 |
| 비관리자 | 세션 종료 또는 API 403 |
| 유휴 세션 | 15분 비활동 시 로그아웃 |
| App Check | production 전 reCAPTCHA Enterprise와 custom backend 검증 필요 |

Firestore role은 로그인 화면의 진입 자격만 확인한다. 서버는 `resolve_admin_authorization` 결과의 `SUPER_ADMIN`, `OPERATIONS`, `DEVELOPER`를 최종 권한으로 사용하고 세부 역할이 없거나 회수됐으면 거부한다. 역할 변경과 긴급 접근은 PostgreSQL 감사 이력에 남긴다.

## Firestore 계약

관리자 웹의 운영 화면은 다음 범위를 Next.js 서버 route를 통해 사용한다. 브라우저 Firebase SDK의 `ADMIN` 직접 권한은 세부 역할을 확인할 수 없으므로 허용하지 않는다.

| 범위 | 읽기/쓰기 | 주요 필드 |
| --- | --- | --- |
| 관리자 본인 `users/{uid}` | 브라우저 읽기 | 로그인 진입 자격 확인용 `role`, `name` |
| `users` 중 `role == MANAGER` | 서버 읽기 | 서버에서 이름·연락처를 마스킹하고 경로는 반환하지 않음 |
| 매니저 심사 결과 | 서버 쓰기 | 상태, 검토 메모, 검토 시각·관리자 UUID, 이력 |

매니저 심사, 병원 가이드 조회와 배정은 서버 API 계약을 먼저 구현한 뒤 브라우저 `ADMIN` 권한을 차단한다. 새 관리자 기능도 같은 순서를 따른다.

## Storage 계약

- 신규 매니저 심사 원본은 `license` 또는 `nursingLicense` 자격 증빙 1종만 사용한다.
- `idCard`, `criminalRecord`, `healthCertificate`는 신규 업로드·심사 입력으로 받지 않는다. 기존 `healthCertificate`는 `nursingLicense` 이관 전용이고, 기존 신분증·범죄경력 원본은 보존 정책에 따른 파기 대상으로만 처리한다.
- 목록에는 마스킹된 정보와 제출 여부만 반환하고 Storage 경로를 반환하지 않는다.
- 원문은 10자 이상 사유를 받은 `no-store` 인라인 응답으로만 중계하고 공개 다운로드 URL을 만들지 않는다.
- 원본은 심사 중에만 Storage에 유지하고 심사 종료 후 30일 안에 삭제한다. PostgreSQL에는 자격 종류, 검증 결과·시각, 유효기간과 감사 메타데이터만 남긴다.
- Storage Rules 변경 PR은 관리자 미리보기 영향을 확인한다.

`managerDocumentFiles`와 `managerDocumentFilePaths`의 canonical key·경로는 서로 일치해야 하고, Storage 경로는 `manager-documents/{managerUserId}/{documentKey}/...` 형식을 따른다. `license`와 `nursingLicense`가 동시에 존재하는 제출은 거부하며, 자격 종류 교체는 서버가 이전 메타데이터와 원본을 정리한 뒤 새 revision으로 기록한다.

## PostgreSQL 관리자 API 계약

현재 첫 서버 경계는 `GET /admin/hospital-guides?limit=50`이며, 세부 역할 도입 뒤 모든 관리자 route가 같은 권한 판정을 재사용한다.

- Authorization이 없으면 401
- 유효한 token이지만 `ADMIN`이 아니거나 활성 세부 역할이 없으면 403
- 관리자이면 200과 병원 가이드 목록
- DB 접속은 서버 전용 `bodeul_admin_service`를 사용
- 현재 runtime 권한은 필요한 SELECT만 허용

관리자 쓰기 API를 추가할 때는 table DML을 직접 부여하지 않고 검증된 `security definer` 함수, 감사 로그, 입력 검증, idempotency와 rollback을 함께 정의한다. 세부 계약은 [관리자 RBAC](admin-rbac.md)를 따른다.

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
