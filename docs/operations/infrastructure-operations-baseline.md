# 인프라 운영 기준

기준일: 2026-07-16

이 문서는 현재 인프라 기준으로 운영자가 봐야 하는 배포, 보안, 비용, 백업, API 전환 항목을 한 곳에 정리한다. 실제 코드 기준은 `app/`, `api/`, `core-api/`, `functions/`, 별도 `bodeul-admin-web` 저장소, Firebase Rules, `tools/firebase/`, `.github/workflows/`를 확인해 반영했다.

## 현재 운영 판단 요약

| 영역 | 현재 판단 |
| --- | --- |
| 관리자 웹 배포 | production 기준은 #134에서 확정. 현재 저장소에는 Firebase Hosting preview workflow가 있고, Vercel/Firebase preview API 모드 팀 공유 검증은 후속 작업으로 분리 |
| 관리자 웹 preview | Firebase Hosting WIF preview workflow 유지. #140에서는 Oracle API와 로컬 관리자 웹 API 모드 검증이 통과했고, Vercel preview는 제외됨 |
| Android 앱 데이터 | Firestore/Storage 직접 접근 유지 |
| 관리자 웹 데이터 | Firestore/Storage 직접 접근 유지, API 전환 후보 준비 |
| 운영 DB 전환 | Supabase PostgreSQL 개발 DB seed 검증 완료 |
| API 경계 | `bodeul-api` 구현 시작 완료. 관리자 웹 병원 가이드 read API 1차 연결 완료, #140/#123 댓글 기준 Oracle/Supabase/Firebase Admin/로컬 관리자 웹 API 모드와 실제 API 응답 비교 통과 |
| Spring Core API | Firebase token과 PostgreSQL role 연결 및 개발 DB migration 검증 완료. OCI 배포 계획은 폐기하고 Cloud Run preview 구축 중 |
| Firebase Functions | FCM, Kakao/Naver custom token, 운영 보조 작업 유지 |
| App Check | 초기화 경로는 있으나 enforcement는 단계 적용 |
| Code scanning | 2026-07-02 확인 기준 open alert 0건 |
| Dependabot/취약점 | `uuid` 전이 취약점은 PR #136 병합 후 #103 종료 |

## 관리자 웹 배포 방식

현재 저장소에서 검증된 관리자 웹 배포 경로는 Firebase Hosting preview다. production live 배포 기준은 #134에서 확정한다. #140에서는 Oracle API와 로컬 관리자 웹 API 모드 실연동이 통과했지만, Vercel preview는 production target 생성 문제로 제외됐다. Vercel/Firebase preview URL에서 팀원이 공유 가능한 API 모드 화면 검증은 후속 작업으로 분리한다.

선택 이유:

- 관리자 웹은 Firebase Auth, Firestore, Storage를 직접 사용하므로 같은 Firebase 프로젝트 안에서 호스팅하는 편이 운영 경계가 단순하다.
- Firebase Hosting은 정적 Vite 빌드 산출물 배포에 맞고, 커스텀 도메인과 SSL을 Firebase 콘솔에서 함께 관리할 수 있다.
- Vercel preview는 API 모드 팀 공유 검증 후보지만, production 기준은 도메인, Auth domain, App Check, live workflow, 권한 분리를 #134에서 별도로 결정해야 한다.
- 로컬 실행은 개발/시연용이지 운영 배포 방식으로 보지 않는다.

현재 상태:

- `firebase.json`의 Hosting public은 `admin-web/dist`다.
- `/assets/**`는 Vite hash asset 기준 장기 캐시를 사용한다.
- HTML과 SPA fallback 경로는 no-cache다.
- 모든 경로는 `/index.html`로 rewrite한다.
- Firebase Hosting dev live URL 기준은 `https://bodeul-dev.web.app`이다. production live URL은 #134에서 별도 확정한다.

관련 workflow:

- `.github/workflows/admin-web.yml`
- `.github/workflows/admin-web-preview-deploy.yml`

Firebase Hosting preview 배포는 `admin-web-preview` GitHub Environment와 Google Cloud Workload Identity Federation을 사용한다. Firebase refresh token fallback은 제거된 상태다. Vercel preview를 후속 검증에 사용할 때도 Firebase Web config와 `VITE_BODEUL_DATA_BACKEND`, `VITE_BODEUL_API_BASE_URL`은 preview 전용 환경값으로 분리한다.

## bodeul-api 운영 기준

현재 `bodeul-api`는 운영 DB 전환을 위한 얇은 API 경계다.

구현 상태:

- `api/` 디렉터리 존재
- Node 22 + TypeScript
- `npm --prefix api run check`
- `GET /healthz`
- `GET /admin/api-contract`
- `GET /admin/hospital-guides`
- Firebase Admin SDK ID token 검증
- PostgreSQL `pg` pool
- PostgreSQL `app_users.role == 'ADMIN'` 인가

운영 배포 전 필요한 항목:

| 항목 | 상태 | 설명 |
| --- | --- | --- |
| API 실행 환경 | preview 1차 검증 완료 | #140/#123 댓글 기준 Oracle Free Tier 환경에서 `/healthz`, 인증, Supabase 조회, 병원 가이드 API, 로컬 관리자 웹 API 모드, 응답 비교가 통과 |
| Spring Core API 실행 환경 | 구축 중 | `bodeul-dev` Tokyo Cloud Run, 최소 0/최대 1, 1 vCPU/1 GiB 기준 |
| `core-api-preview` GitHub Environment | 생성 완료 | WIF와 Cloud Run 식별용 Variables 사용. DB 값은 Secret Manager로 이전 |
| `core-api-production` GitHub Environment | 자리만 준비 | 운영 project, 비용, 도메인과 rollback 리허설 전에는 배포하지 않음 |
| `DATABASE_URL` | 필요 | 서버 secret으로만 주입 |
| `FIREBASE_PROJECT_ID` | 필요 | Firebase token 검증 project 지정 |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | 필요할 수 있음 | ADC가 아닌 서비스 계정 JSON 경로를 택할 때만 사용 |
| API 로그 정책 | 필요 | token 원문, DB URL, 서비스 계정 내용을 남기지 않는다. |

관리자 웹 API 모드 rollback 기준:

- `VITE_BODEUL_DATA_BACKEND=firebase`를 기본값으로 둔다.
- `api` 전환은 화면 단위로 진행한다.
- API 장애나 응답 불일치가 있으면 Firebase 직접 접근 경로로 되돌린다.
- #140에서 배포된 API 응답 JSON을 확보했고, #123 댓글 기준 병원 가이드 Firestore/API 응답 비교가 `passed`로 기록됐다.

## GitHub 기준 현재 상태

2026-07-16 확인 기준:

- 최근 병합된 인프라 PR:
  - #101 Supabase 개발 DB seed 검증 기준 추가
  - #109 `bodeul-api` 서버 골격 추가
  - #114 Firebase Admin SDK 인증 연결
  - #115 PostgreSQL client 초기화
  - #116 관리자 role 기반 인가 추가
  - #117 병원 가이드 read API 추가
  - #121 관리자 웹 병원 가이드 API 연결
  - #128 관리자 웹 API 환경변수와 CORS 기준 정리
  - #136 uuid 전이 의존성 취약 경로 override 적용
  - #137 병원 가이드 Firestore/API 로컬 비교 기록 추가
  - #138 병원 가이드 비교 도구 추가
- 2026-07-08 이슈 댓글 기준으로 #140에는 Oracle/Supabase/Firebase Admin/로컬 관리자 웹 API 모드 검증 결과가, #123에는 실제 배포 API 응답 비교 `passed` 결과가 기록됐다.
- 최근 열린 핵심 인프라 이슈는 #134, #156, #157이다.

정합성 주의:

- #88은 API 골격과 초기 경계 구축 기준으로 완료 처리했다.
- #113은 관리자 웹 1차 read API 연결 기준으로 완료 처리했다.
- #122는 API 환경변수와 CORS origin 기준 확정으로 종료됐다.
- #123은 병원 가이드 Firestore/API 응답 비교 기록을 계속 추적한다. 로컬 비교와 비교 도구는 #137, #138로 반영됐고, 실제 배포 API 응답 비교는 2026-07-08 댓글 기준 `passed`로 기록됐다.
- #134는 production 관리자 웹 배포 기준을 확정한다.
- #135는 `bodeul-admin-web` 저장소 분리 실행 준비를 추적한다.
- #140은 Oracle/Supabase/Firebase Admin/로컬 관리자 웹 API 모드 1차 검증을 기록했다. Vercel preview 검증은 후속 분리 대상이다.

## 비용 리스크

공식 가격은 변동될 수 있으므로 운영 전 Firebase Console, Google Cloud Billing, Supabase Dashboard, Kakao Developers Console에서 다시 확인한다. 현재 문서는 리스크 범위와 관리 기준을 정리한다.

| 항목 | BoDeul 리스크 | 관리 기준 |
| --- | --- | --- |
| Firestore reads | 관리자 대시보드 전체 스캔, 실시간 위치 리스너, 채팅 리스너가 read를 늘릴 수 있다. | 페이지네이션, 역할별 쿼리 축소, 리스너 해제 기준을 둔다. |
| Firestore writes | 위치 공유, 채팅, 읽음 상태, 알림 작업이 write를 만든다. | 위치 업데이트 간격과 세션 종료 시 리스너/쓰기 중단 기준을 둔다. |
| Firestore storage/egress | 채팅 배열, 리포트, 감사 로그, 알림 로그가 누적될 수 있다. | 보존 기간과 아카이브 기준을 둔다. |
| Firebase Storage | 매니저 서류와 채팅 첨부가 누적되고 관리자 미리보기 다운로드가 반복될 수 있다. | 10MB 제한, 고아 파일 점검, 보존 기간을 유지한다. |
| Cloud Functions | FCM, 리마인더, 관리자 액션 전달 job이 호출량을 만든다. | 배치 크기, 재시도 횟수, timeout을 관리한다. |
| Firebase Hosting | 관리자 웹 자체 비용은 낮지만 정적 asset과 트래픽은 증가할 수 있다. | 정적 SPA만 Hosting에 두고 파일 원본은 Storage에 둔다. |
| Supabase PostgreSQL | 관리자 API 전환 후 query/connection 사용량과 DB 저장량이 비용 요인이 된다. | pool max, query limit, row count 모니터링을 둔다. |
| Cloud Run Core API | 요청, CPU/RAM, Artifact Registry와 외부 DB egress 비용이 생길 수 있다. | 개발은 최소 0/최대 1, 1 vCPU/1 GiB, pool max 5로 제한하고 Billing 예산 알림을 설정한다. |
| Kakao Local REST API | Android 직접 호출 구조라 쿼터 소진과 429 가능성이 있다. | 6시간 메모리 캐시, Kakao Console quota 확인, Functions proxy 전환 조건을 둔다. |

후속 이슈:

- [#65 Firebase 비용 모니터링과 예산 알림 설정](https://github.com/bodeul110/Bodeul/issues/65)
- [#66 Kakao Local REST API Key 운영 리스크 점검](https://github.com/bodeul110/Bodeul/issues/66)

## App Check 적용 로드맵

현재 상태:

- Android debug는 Debug provider, release는 Play Integrity provider를 준비한다.
- 관리자 웹은 `VITE_FIREBASE_APPCHECK_SITE_KEY`가 있으면 reCAPTCHA v3 App Check를 초기화한다.
- Functions callable은 `ENABLE_APPCHECK_ENFORCEMENT=true`일 때만 `enforceAppCheck`를 켠다.
- Firebase Console enforcement는 단계적으로 켠다.

로드맵:

| 단계 | 조건 | 액션 |
| --- | --- | --- |
| 1. 개발 token 정리 | Android debug token, 관리자 웹 debug token 등록 안정화 | allowlist와 로컬 설정 절차 정리 |
| 2. 릴리스 검증 | Play Integrity, 웹 site key가 실제 빌드와 도메인에서 정상 동작 | 실기기와 Hosting preview/live에서 token 발급 확인 |
| 3. Functions enforcement | callable 호출 실패가 없고 debug token 회수 기준이 있음 | `ENABLE_APPCHECK_ENFORCEMENT=true` 적용 |
| 4. Storage enforcement | 서류/첨부 업로드와 미리보기가 정상 통과 | Firebase Console Storage enforcement 적용 |
| 5. Firestore enforcement | 앱과 관리자 웹의 모든 직접 Firestore 접근 흐름 통과 | Firebase Console Firestore enforcement 적용 |
| 6. 운영 모니터링 | 403/App Check 실패 로그가 정상 범위 | 실패 로그와 고객 문의를 주간 점검 |

후속 이슈:

- [#32 App Check 강제 적용과 Firebase 환경 분리 계획](https://github.com/bodeul110/Bodeul/issues/32)

## Firestore 인덱스와 쿼리

현재 `firestore.indexes.json`에는 복합 인덱스가 없다. 대부분의 쿼리는 단일 필드 필터, 문서 직접 조회, `limit(1)`, 또는 소량 컬렉션 조회다.

복합 인덱스 추가 기준:

- 특정 화면에서 `where(...) + orderBy(createdAt/appointmentAt)` 조합이 추가된다.
- 관리자 대시보드가 전체 컬렉션을 읽지 않고 서버 필터와 정렬을 사용하도록 바뀐다.
- Firebase SDK가 `FAILED_PRECONDITION: The query requires an index` 오류와 생성 링크를 반환한다.

우선 후보:

- `appointmentRequests`: `status + appointmentAtEpochMillis desc`
- `appointmentRequests`: `managerUserId + status + appointmentAtEpochMillis desc`
- `companionSessions`: `managerUserId + currentStatus`
- `clientSupportRequests`: `userId + createdAt desc`
- `clientSupportRequests`: `status + createdAt desc`
- `supportInquiries`: `managerUserId + createdAt desc`
- `adminActionNotifications`: `state + priority + createdAt desc`
- `adminActionDeliveryJobs`: `state + nextAttemptAt asc`

상세 기록은 [Firestore 쿼리/인덱스 운영 점검](../reports/firestore-query-index-review-2026-06-26.md)을 기준으로 본다.

## 백업/복원 리허설

현재 상태:

- `tools/firebase`에는 `backup:state`, `validate:backup`, `diff:state`, `restore:state:dry-run`, `restore:state:apply`가 있다.
- 2026-06-25 기준 `bodeul-dev`에서 `backup -> validate -> restore dry-run -> diff` 순서의 읽기 위주 리허설을 수행했다.
- 격리 Firebase 프로젝트에서 `restore:state:apply`까지 수행한 복원 리허설은 아직 없다.

운영 기준:

- 운영 프로젝트에서 바로 `restore:state:apply`를 실행하지 않는다.
- 월 1회 또는 데이터 계약 변경 전 격리 프로젝트에서 `backup -> validate -> restore dry-run -> restore apply -> diff -> readiness/report` 순서로 리허설한다.
- 결과는 `docs/reports/`에 날짜별로 저장한다.

권장 리허설 명령:

```powershell
cd D:\BoDeul\tools\firebase
npm run backup:state
npm run validate:backup -- --file backups/firestore-backup-YYYYMMDD-HHMMSS.json
npm run restore:state:dry-run -- --file backups/firestore-backup-YYYYMMDD-HHMMSS.json
npm run restore:state:apply -- --file backups/firestore-backup-YYYYMMDD-HHMMSS.json
npm run diff:state -- --file backups/firestore-backup-YYYYMMDD-HHMMSS.json
npm run workflow:ops -- --file backups/firestore-backup-YYYYMMDD-HHMMSS.json --strict
```

후속 이슈:

- [#64 Firestore 백업/복원 리허설 실행](https://github.com/bodeul110/Bodeul/issues/64)

## Kakao REST API Key 운영 방침

현재 구조:

- Kakao 로그인은 Android 앱이 받은 Kakao access token을 Functions에 전달하고, Functions가 Kakao user API를 호출한다.
- 병원/약국 실좌표 검색은 Android 앱이 Kakao Local REST API를 직접 호출한다.
- Android 앱의 Kakao Local REST API key는 `local.properties`의 `kakaoRestApiKey`에서 빌드 값으로 주입하며 저장소에 커밋하지 않는다.
- 같은 질의 결과는 앱 메모리에서 6시간 캐시한다.

현재 결정:

- MVP에서는 Kakao Local REST API를 Android 직접 호출로 유지한다.
- Admin key, client secret, 알림톡 API key 같은 서버 비밀값은 앱에 넣지 않는다.
- Kakao Local REST API key는 공개 클라이언트에 포함될 수 있는 값으로 보고, Kakao Developers Console의 플랫폼 제한, 쿼터 확인, 429 관측을 운영 기준으로 둔다.

Functions proxy 전환 조건:

- Kakao Console 쿼터가 반복적으로 임계치에 접근한다.
- REST API key 노출이 운영 리스크로 판단된다.
- 검색 결과를 서버 캐시하거나 호출량을 사용자/세션별로 제한해야 한다.
- Kakao Local API 응답을 운영 감사 로그나 비용 리포트에 연결해야 한다.

후속 이슈:

- [#66 Kakao Local REST API Key 운영 리스크 점검](https://github.com/bodeul110/Bodeul/issues/66)

## Mock 모드와 Firebase 모드

| 구분 | Mock 모드 | Firebase 모드 |
| --- | --- | --- |
| 진입 조건 | Android 앱에 Firebase 설정이 없거나 Mock Repository가 선택됨 | `google-services.json` 등 Firebase 설정이 준비됨 |
| 데이터 | 앱 내 목업 데이터와 메모리 상태 | Firestore, Storage, Auth, Functions |
| 가능 범위 | 화면 흐름, 화면 전환, 모델 계약 확인 | 실제 권한, Rules, Storage 업로드, FCM, Functions, 운영 리포트 검증 |
| 불가능한 검증 | Rules, App Check, FCM, Storage 원본, 실제 예약 연결 검증 | 운영 상태를 건드릴 수 있으므로 dry-run과 테스트 계정 기준 필요 |
| 운영 판단 | 운영 검증으로 보지 않음 | 운영 검증 기준 |

## Cloud Functions 목록

모든 함수의 기본 리전은 `asia-northeast3`이다.

| 함수 | 유형 | 트리거/호출 | 인증/권한 | 환경 변수 |
| --- | --- | --- | --- | --- |
| `kakaoCustomToken` | callable | Kakao access token으로 Firebase custom token 발급 | 클라이언트 생성 가능 역할 `PATIENT/GUARDIAN/MANAGER`, App Check env gated | 없음 |
| `naverCustomToken` | callable | Naver access token으로 Firebase custom token 발급 | 클라이언트 생성 가능 역할 `PATIENT/GUARDIAN/MANAGER`, App Check env gated | 없음 |
| `resolveLinkedParticipant` | callable | 연결 환자/보호자 조회 | 로그인 필요, 호출자 `PATIENT/GUARDIAN/ADMIN` | 없음 |
| `findSocialDuplicateEmailProvider` | callable | 소셜 가입 중복 이메일 확인 | 로그인 필요, 소셜 로그인 계정만 허용 | 없음 |
| `resolveAssignedManagerProfile` | callable | 배정 매니저 프로필 조회 | 로그인 필요, 예약 참여자 또는 관리자 | 없음 |
| `notifyClientSupportAnswered` | Firestore trigger | `clientSupportRequests/{supportRequestId}` 문서 변경 | 서비스 계정 | FCM 사용 |
| `notifyCompanionChatMessage` | Firestore trigger | `companionSessions/{sessionId}` 문서 변경 | 서비스 계정 | FCM 사용 |
| `notifyCompanionLocationAlert` | Firestore trigger | `companionSessions/{sessionId}` 문서 변경 | 서비스 계정 | FCM 사용 |
| `sendClientSupportAnswerReminders` | schedule | 매시 정각 | 서비스 계정 | FCM 사용 |
| `cleanupStaleNotificationTokens` | schedule | 매일 04:30 Asia/Seoul | 서비스 계정 | FCM 사용 |
| `syncAppointmentReminderJobs` | schedule | 매일 09:00 Asia/Seoul | 서비스 계정 | 없음 |
| `deliverAppointmentReminderJobs` | schedule | 10분마다 | 서비스 계정 | `KAKAO_ALIMTALK_ENDPOINT`, `KAKAO_ALIMTALK_API_KEY`, `KAKAO_ALIMTALK_SENDER_KEY`, `KAKAO_ALIMTALK_AUTH_SCHEME` |
| `dispatchAppointmentReminderJobs` | callable | 예약 리마인더 수동 발송 | 로그인 필요, `users/{uid}.role == ADMIN` | 알림톡 환경 변수 |
| `deliverAdminActionDeliveryJobs` | schedule | 5분마다 | 서비스 계정 | `ADMIN_PUSH_ENDPOINT`, `ADMIN_PUSH_API_KEY`, `ADMIN_PUSH_AUTH_SCHEME` |
| `dispatchAdminActionDeliveryJobs` | callable | 관리자 후속 알림 수동 처리 | 로그인 필요, `users/{uid}.role == ADMIN` | 관리자 푸시 환경 변수 |
| `syncLinkedAppointmentParticipants` | Firestore trigger | `users/{userId}` 문서 변경 | 서비스 계정 | 없음 |
| `cleanupAppointmentReminderJobs` | Firestore trigger | `appointmentRequests/{appointmentRequestId}` 문서 변경 | 서비스 계정 | 없음 |

공통 callable 환경 변수:

- `ENABLE_APPCHECK_ENFORCEMENT=true`이면 callable Functions의 App Check enforcement가 켜진다.

## 운영 도구 명령어

| 명령 | 사용 시점 | 쓰기 여부 |
| --- | --- | --- |
| `npm --prefix tools/firebase run check:state` | 기준 Auth 계정과 주요 컬렉션 상태 확인 | 읽기 |
| `npm --prefix tools/firebase run check:manager-storage` | 매니저 서류 Firestore 메타데이터와 Storage 객체 정합성 확인 | 읽기 |
| `npm --prefix tools/firebase run cleanup:manager-storage:dry-run` | 고아 Storage 파일 삭제 후보 확인 | 읽기 |
| `npm --prefix tools/firebase run cleanup:manager-storage:apply` | 고아 Storage 파일 실제 삭제 | 쓰기 |
| `npm --prefix tools/firebase run check:readiness` | 역할별 화면 진입 준비도 확인 | 읽기 |
| `npm --prefix tools/firebase run backup:state` | Firestore 관리 컬렉션 백업 생성 | 읽기, 로컬 파일 생성 |
| `npm --prefix tools/firebase run validate:backup -- --file ...` | 백업 파일 구조 검증 | 로컬 파일 읽기 |
| `npm --prefix tools/firebase run postgres:seed:dry-run -- --file ...` | PostgreSQL seed 후보 점검 | 로컬 파일 읽기 |
| `npm --prefix tools/firebase run postgres:seed:build -- --file ...` | PostgreSQL seed 입력 JSON 생성 | 로컬 파일 생성 |
| `npm --prefix tools/firebase run postgres:seed:sql -- --file ...` | upsert SQL 생성 | 로컬 파일 생성 |
| `npm --prefix tools/firebase run postgres:seed:rollback -- --file ...` | rollback SQL 생성 | 로컬 파일 생성 |
| `npm --prefix tools/firebase run diff:state -- --file ...` | 백업과 현재 Firestore 상태 비교 | 읽기 |
| `npm --prefix tools/firebase run report:ops -- --file ...` | 운영 상태 HTML 리포트 생성 | 읽기, 로컬 파일 생성 |
| `npm --prefix tools/firebase run workflow:ops -- --file ...` | 상태 점검, 백업 검증, diff, 리포트를 한 번에 수행 | 읽기 |
| `npm --prefix tools/firebase run preflight:local -- --file ...` | 로컬 프리플라이트 | 읽기, 빌드 산출물 생성 |
| `npm --prefix tools/firebase run preflight:ci` | GitHub Actions 프리플라이트 | CI 기준 |
| `npm --prefix tools/firebase run test:rules` | Firestore/Storage Rules emulator 테스트 | 로컬 emulator |
| `npm --prefix tools/firebase run restore:state:dry-run -- --file ...` | 백업 복원 계획 확인 | 읽기 |
| `npm --prefix tools/firebase run restore:state:apply -- --file ...` | Firestore 문서 실제 복원 | 쓰기 |

쓰기 명령은 운영 프로젝트에서 바로 실행하지 않고, dry-run 또는 격리 프로젝트 검증을 명시적으로 수행한다.

## 남은 GitHub 운영 이슈

| 이슈 | 현재 판단 |
| --- | --- |
| [#32](https://github.com/bodeul110/Bodeul/issues/32) | App Check enforcement와 Firebase 환경 분리 계획 |
| [#49](https://github.com/bodeul110/Bodeul/issues/49) | CodeQL 도입은 완료됐지만 이슈 정리 여부 확인 필요 |
| [#63](https://github.com/bodeul110/Bodeul/issues/63) | Rules emulator workflow는 있으나 이슈 open 상태 확인 필요 |
| [#64](https://github.com/bodeul110/Bodeul/issues/64) | 격리 프로젝트 복원 apply 리허설 필요 |
| [#65](https://github.com/bodeul110/Bodeul/issues/65) | 비용 모니터링과 예산 알림 설정 필요 |
| [#66](https://github.com/bodeul110/Bodeul/issues/66) | Kakao Local REST API 운영 리스크 점검 필요 |
| [#123](https://github.com/bodeul110/Bodeul/issues/123) | 실제 배포 API 응답 비교 `passed` 반영 후 종료/후속 분리 판단 필요 |
| [#134](https://github.com/bodeul110/Bodeul/issues/134) | 관리자 웹 production 배포 기준 확정 필요 |
| [#135](https://github.com/bodeul110/Bodeul/issues/135) | `bodeul-admin-web` 저장소 분리 실행 준비 |
| [#140](https://github.com/bodeul110/Bodeul/issues/140) | Oracle/Supabase/Firebase Admin/로컬 API 모드 검증 반영, Vercel preview 후속 분리 필요 |
| [#156](https://github.com/bodeul110/Bodeul/issues/156) | OCI 대신 Cloud Run에 Spring Core API preview 배포 및 rollback 검증 |
| [#157](https://github.com/bodeul110/Bodeul/issues/157) | Cloud Run에서 실제 Firebase token과 PostgreSQL role 인가 검증 |

## 참고 문서

- [현재 인프라 구성도](../architecture/infra-overview.md)
- [인프라 개요](../architecture/infrastructure.md)
- [PostgreSQL 운영 전환 결정](../architecture/postgres-operational-transition.md)
- [PostgreSQL API 경계 기준](../architecture/postgres-api-boundary.md)
- [관리자 API 초기 응답 계약](../architecture/admin-api-contract.md)
- [관리자 웹 Environment 기준](admin-web-environments.md)
- [App Check 적용 로드맵](app-check-enforcement-roadmap.md)
- [Firebase 운영 도구](firebase/tools.md)
