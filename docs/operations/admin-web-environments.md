# 관리자 웹 환경 기준

기준일: 2026-07-17

## 현재 환경

| 환경 | 용도 | DB 자격 증명 | 상태 |
| --- | --- | --- | --- |
| 로컬 | 개발과 단위 검증 | 개발자가 로컬 비공개 파일로 주입 | 저장소에 커밋하지 않음 |
| Vercel Preview | PR·개발 DB 실연동 | `ADMIN_DATABASE_URL` 있음 | 401·403·200 검증 완료 |
| Vercel Production target | 기본 도메인 공유 화면 | `ADMIN_DATABASE_URL` 없음 | 운영으로 간주하지 않음 |
| 실제 production | 운영 서비스 | 별도 운영 DB 자격 증명 필요 | 미구축 |

## 환경변수 경계

| 종류 | 예 | 노출 범위 |
| --- | --- | --- |
| 브라우저 공개 설정 | `NEXT_PUBLIC_FIREBASE_*` | 번들에 포함 가능 |
| 서버 설정 | `FIREBASE_PROJECT_ID` | Next.js 서버만 사용 |
| 서버 비밀값 | `ADMIN_DATABASE_URL` | Vercel server runtime만 사용 |

DB URL, 비밀번호, Firebase Admin 자격 증명을 `NEXT_PUBLIC_*` 또는 `VITE_*`로 만들지 않는다. Firebase ID token은 요청 Authorization 헤더로 전달하고 로그에 남기지 않는다.

## Preview 기준

- Supabase transaction pooler 6543 포트를 사용한다.
- 관리자 애플리케이션 pool은 1, DB role connection limit은 5다.
- Supabase Root CA를 명시하고 `rejectUnauthorized`를 유지한다.
- `bodeul_admin_service`에는 필요한 SELECT만 허용한다.
- 임시 검증 계정과 row는 검증 직후 삭제한다.
- PR마다 test, lint, Next.js build, Vite rollback build와 CodeQL을 통과시킨다.

## production 생성 조건

1. 개발과 분리된 Firebase와 Supabase 프로젝트를 만든다.
2. custom domain과 Firebase Auth authorized domain을 연결한다.
3. production 전용 관리자 DB role과 비밀번호를 만들고 최소 권한을 재검증한다.
4. Vercel Production environment에만 운영값을 등록한다.
5. App Check, 관리자 MFA, 감사 로그와 긴급 권한 회수를 검증한다.
6. backup/restore와 이전 배포 rollback을 리허설한다.
7. live 배포 승인자와 장애 대응 담당을 정한다.

개발 Preview의 비밀번호나 프로젝트를 production에 복사하지 않는다. 운영값이 없을 때 production API가 500 설정 오류를 내는 것은 의도한 fail-closed 상태다.

## 배포 책임

관리자 웹 build·Vercel 배포와 웹 전용 환경변수는 `bodeul-admin-web` 저장소가 소유한다. 메인 저장소는 관리자 Firebase Hosting workflow나 관리자 배포 secret을 소유하지 않는다. 공용 DB migration과 Firebase Rules 변경은 메인 저장소에서 수행하고 관리자 웹 영향 여부를 함께 기록한다.

## 관련 문서

- [관리자 웹 구조](../architecture/admin-web-architecture.md)
- [관리자 웹 저장소 분리 기록](admin-web-repository-split.md)
- [목표 인프라 구조](../architecture/target-infrastructure.md)
