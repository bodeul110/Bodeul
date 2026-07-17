# 인프라 운영 기준선

기준일: 2026-07-17

## 개발 인프라 기준선

| 범위 | 기준 |
| --- | --- |
| 관리자 웹 | 별도 저장소 Next.js, Vercel Preview, 관리자 DB 실제 401·403·200 검증 완료 |
| Core API | Cloud Run `bodeul-core-api-preview`, Spring Boot, WIF 배포와 revision rollback |
| 공용 DB | Supabase Tokyo 개발 프로젝트, migration/core/admin role 분리 |
| Firebase | `bodeul-dev`, Auth·Firestore·Storage·Functions·FCM 유지 |
| Kakao | Local REST 키는 Secret Manager, 호출은 Core API 뒤에서 수행 |
| production | GCP/Firebase·Supabase·WIF·DB migration 구축, 트래픽·도메인·관리자 DB·Kakao 미연결 |

## 배포 원칙

- 관리자 웹은 `bodeul-admin-web` 저장소와 Vercel이 소유한다.
- Core API와 DB migration은 메인 저장소가 소유한다.
- GitHub Actions는 WIF를 사용하고 장기 서비스 계정 JSON을 만들지 않는다.
- runtime과 migration 자격 증명을 분리한다.
- Preview 성공을 production 완료로 기록하지 않는다.
- 배포 후 health, 무인증 경계, 오류 로그와 비밀값 비노출을 확인한다.

## 변경 전후 점검

| 변경 | 필수 확인 |
| --- | --- |
| Core API | Gradle check, Cloud Run smoke test, DB pool과 Secret Manager 참조 |
| DB migration | preview migration, 검증 SQL, advisor, rollback·소유자 |
| 관리자 서버 | test/lint/Next build/Vite build, Preview 401·403·200 |
| Firebase Rules | emulator 또는 rules test, Android/관리자 영향 |
| App Check | observe 지표, 정상 실기기·웹 요청, rollback |
| source of truth | backfill, row 비교, 쓰기 주체, 장애 복구 |

## 비밀값

- DB URL, 비밀번호, Firebase token, Kakao REST 키 원문을 소스·문서·로그에 적지 않는다.
- 관리자 Preview DB URL은 Vercel Preview에만 둔다.
- Core API DB URL과 Kakao 키는 Google Secret Manager에 둔다.
- production 값은 개발값을 복사하지 않고 별도 생성한다.

## 종료된 자산

Oracle Node preview, 메인 `api/`, 메인 `admin-web/`과 관리자 Firebase Hosting workflow는 과거 전환 검증 자산이다. 현재 배포나 운영 후보로 사용하지 않는다. Git 이력과 보고서만 보존한다. Firebase Hosting site와 관리자 배포 전용 WIF·서비스 계정·GitHub Environment도 2026-07-17에 제거했다.

## 남은 운영 게이트

- 기준 도메인, 실명 운영자 2명과 출시 일정 확정
- Vercel 관리자 DB, Kakao production key와 첫 Cloud Run revision 연결
- production backup/restore와 rollback 리허설
- 관리자 웹 App Check와 MFA
- 도메인별 PostgreSQL 쓰기 전환
- 비용·오류율·연결 수 알림 구성

상세 구조는 [현재 인프라 구성도](../architecture/infra-overview.md)와 [Production 인프라 기본값](production-infrastructure-defaults.md)을 따른다.
