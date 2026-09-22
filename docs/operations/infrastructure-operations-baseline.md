# 인프라 운영 기준선

기준일: 2026-09-22

배포·실기기·복원 성공은 각 날짜의 증적이다. 9월 22일에는 Google Cloud 조직 이전과 설정·접근을 실조회했다. 다른 서비스의 모든 가동 상태를 다시 검증한 것은 아니다.

## 개발 인프라 기준선

| 범위 | 기준 |
| --- | --- |
| Google Cloud 조직·결제 | 개발·운영 프로젝트 모두 공식 `bodeul.kr` 소속. 공용 `bodeul-shared-billing1` 연결·결제 활성 유지. [이전 기록](google-cloud-organization-migration.md) |
| 관리자 웹 | 별도 저장소 Next.js, Vercel Preview/Production 환경 표시. 개발 DB 401·403·200 검증 기록과 운영 로그인 준비 상태는 별도 |
| Core API | Cloud Run `bodeul-core-api-preview`, Spring Boot, WIF 배포와 revision rollback |
| 공용 DB | 환경별 Supabase Tokyo 프로젝트, migration/core/admin/retention 역할 분리. 소스 V1~V23 |
| Firebase | `bodeul-dev`, Auth·Storage·Functions·FCM 유지. Firestore Core 업무 문서 client 쓰기 차단, 인증 프로필·지원·매니저 서류 메타데이터 유지 |
| Kakao | Local REST 키는 Secret Manager, 호출은 Core API 뒤에서 수행 |
| production | 기반 구축 기록 있음. 9월 21일 운영 DB 일시정지, Auth 등록만 진행. 서버 DB·권한·실제 업무 활성화와 별도 |

## 배포 원칙

- 관리자 웹은 `bodeul-admin-web` 저장소와 Vercel이 소유한다.
- Core API와 DB migration은 메인 저장소가 소유한다.
- GitHub Actions는 WIF를 사용하고 장기 서비스 계정 JSON을 만들지 않는다.
- runtime과 migration 자격 증명을 분리한다.
- 현재 기본 브랜치는 두 저장소 모두 `master`다. Core Preview/Production은 수동 배포 workflow로 나뉘며 `dev` 브랜치 전략 적용 완료를 의미하지 않는다.
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

예약·세션·리포트·후속 처리·채팅·읽음·위치의 Core 데이터 계약은 PostgreSQL 단일 쓰기다. 기존 매니저 위치 경로는 기본 OFF이며 환자 중심 위치 기능은 별도 구현·검증 대상이다. 현재 매칭 배정은 관리자 서버의 admin-only 함수가 담당한다. Firestore Rules는 해당 Core 업무 문서의 클라이언트 쓰기를 차단한다.

## 비밀값

- DB URL, 비밀번호, Firebase token, Kakao REST 키 원문을 소스·문서·로그에 적지 않는다.
- 관리자 Preview DB URL은 Vercel Preview에만 둔다.
- Core API DB URL과 Kakao 키는 Google Secret Manager에 둔다.
- production 값은 개발값을 복사하지 않고 별도 생성한다.

## 종료된 자산

Oracle Node preview, 메인 `api/`, 메인 `admin-web/`과 관리자 Firebase Hosting workflow는 과거 전환 검증 자산이다. 현재 배포나 운영 후보로 사용하지 않는다. Git 이력과 보고서만 보존한다. Firebase Hosting site와 관리자 배포 전용 WIF·서비스 계정·GitHub Environment도 2026-07-17에 제거했다.

## 남은 운영 게이트

- Preview Core API 500/503 재확인 이슈 #429의 원인·복구 검증
- 일시정지 운영 DB 재개 승인과 소스 V23 대비 실제 migration 적용·복원 검증

- 사용할 주소·도메인, 운영자와 실제 출시 일정 확인
- Vercel 관리자 DB, Kakao production key와 첫 Cloud Run revision 연결
- Cloud Run·Vercel production rollback 리허설
- 관리자 웹 App Check와 MFA
- production Core 도메인 migration·Kakao·자격 증명 종단 검증
- 최신 schema 기준 production 자동 파기·고지·정책 대조 (과거 fixture 결과와 구분)
- 비용·오류율·연결 수 알림 구성

상세 구조는 [현재 인프라 구성도](../architecture/infra-overview.md)와 [Production 인프라 기본값](production-infrastructure-defaults.md)을 따른다.
