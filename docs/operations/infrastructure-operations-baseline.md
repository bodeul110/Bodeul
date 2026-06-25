# 인프라 운영 기준

기준일: 2026-06-25

이 문서는 현재 인프라 기준으로 남아 있던 P0, P1, P2 액션 아이템을 한 곳에 정리한다. 실제 코드 기준은 `app/`, `admin-web/`, `functions/`, `firestore.rules`, `storage.rules`, `tools/firebase/`를 확인해 반영했다.

## 관리자 웹 배포 방식

운영 배포 기준은 Firebase Hosting으로 확정한다.

선택 이유:
- 관리자 웹은 Firebase Auth, Firestore, Storage를 직접 사용하므로 같은 Firebase 프로젝트 안에서 호스팅하는 편이 운영 경계가 단순하다.
- Firebase Hosting은 정적 Vite 빌드 산출물 배포에 맞고, 커스텀 도메인과 SSL을 Firebase 콘솔에서 함께 관리할 수 있다.
- Vercel을 쓰면 별도 계정, 도메인, 환경 변수, 배포 권한을 추가로 관리해야 한다.
- 로컬 실행은 개발/시연용일 뿐 운영 배포 방식으로 보지 않는다.

현재 상태:
- `admin-web`은 `npm --prefix admin-web run build`로 정적 산출물을 만들 수 있다.
- `firebase.json`의 `hosting` 블록은 `admin-web/dist`를 배포 대상으로 둔다.
- `/assets/**`는 Vite 해시 파일 기준 장기 캐시하고, `/index.html`은 no-cache로 둔다.
- 라우팅은 SPA fallback을 위해 모든 경로를 `/index.html`로 rewrite한다.

배포 절차:

```powershell
npm --prefix admin-web run build
firebase hosting:channel:deploy admin-web-preview --only hosting --project <firebase-project-id> --expires 7d
firebase deploy --only hosting --project <firebase-project-id>
```

## 비용 리스크

공식 가격은 변동될 수 있으므로, 운영 전에는 Firebase 콘솔과 Google Cloud Billing 예산 알림을 함께 확인한다. 현재 문서는 2026-06-25에 확인한 공식 문서 기준이다.

| 항목 | 공식 과금 기준 요약 | BoDeul 리스크 | 관리 기준 |
| --- | --- | --- | --- |
| Firestore reads | Firebase 가격표 기준 Standard edition은 무료 50K reads/day 이후 과금된다. 쿼리는 문서 읽기와 일부 인덱스 엔트리 읽기로 과금된다. | 관리자 대시보드 전체 스캔, 실시간 위치 리스너, 안심 채팅 리스너가 읽기를 늘릴 수 있다. | 대시보드 페이지네이션, 역할별 쿼리 축소, 요약 컬렉션, 리스너 해제 기준을 둔다. |
| Firestore writes | 무료 20K writes/day 이후 과금된다. `set`과 `update`는 각각 write로 계산된다. | 위치 공유가 30초 간격이면 세션 1시간당 약 120 writes가 생긴다. 채팅 메시지, 읽음 상태, 알림 큐도 write를 만든다. | 위치 업데이트 간격, 세션 종료 후 리스너/업데이트 중단, 큐 배치 크기를 운영 지표로 본다. |
| Firestore storage/egress | 저장 데이터와 외부 클라이언트 egress도 과금 대상이다. | 채팅 배열, 리포트, 감사 로그, 알림 큐가 장기 누적될 수 있다. | 보존 기간, 아카이브, 리포트/감사 로그 용량 기준을 정한다. |
| Firebase Storage | 버킷 종류와 리전에 따라 저장량, 다운로드, 업로드/다운로드 작업이 과금된다. | 매니저 서류와 채팅 첨부가 누적되고, 관리자 미리보기 다운로드가 반복될 수 있다. | 파일당 10MB 제한 유지, 고아 파일 점검, 보존 기간 확정, 미리보기 캐시를 검토한다. |
| Cloud Functions | 무료 2M invocations/month 이후 호출 과금이 있고, GB-seconds, CPU-seconds, outbound networking도 과금된다. | 문서 변경 트리거, 스케줄러, FCM 발송, 알림톡/관리자 푸시 연동이 호출량을 만든다. | 스케줄 주기와 배치 크기, 실패 재시도, 외부 호출 timeout을 관리한다. |
| FCM | Firebase 가격표 기준 Cloud Messaging은 no-cost다. | 직접 비용보다 토큰 정합성 실패, 중복 발송, 사용자 피로도가 리스크다. | 장기 미사용 토큰 정리와 invalid token 삭제를 유지한다. |
| Kakao Local REST API | Kakao Developers는 앱별 현재 쿼터를 콘솔의 Statistics > Quotas에서 확인하도록 안내한다. 쿼터나 초당 제한 초과 시 Local API는 429를 반환할 수 있다. | Android 앱 직접 호출 구조라 키 노출과 쿼터 소진 가능성이 있다. | 6시간 메모리 캐시 유지, Kakao 콘솔 쿼터 모니터링, 429 로깅, 필요 시 Functions 프록시로 이전한다. |
| Firebase Hosting | Firebase 가격표 기준 Hosting은 저장량과 데이터 전송량 기준으로 과금된다. | 관리자 웹은 트래픽이 낮아 초기 리스크가 작지만, 이미지/문서 미리보기를 Hosting으로 올리면 비용이 커질 수 있다. | 정적 앱만 Hosting에 두고 서류 원본은 Storage 경로를 유지한다. |

비용 산식 예시:
- 위치 write/month = 월 진행 세션 수 * 세션당 공유 시간 * 3600 / 업데이트 간격초
- 위치 read/month = 위치 write 수 * 활성 구독자 수
- 채팅 write/month = 메시지 수 + 읽음/첨부 메타데이터 갱신 수
- 관리자 대시보드 read/month = 열람 횟수 * 화면에서 읽는 문서 수

## App Check 적용 로드맵

현재 상태:
- Android debug는 Debug provider, release는 Play Integrity provider를 설치한다.
- 관리자 웹은 `VITE_FIREBASE_APPCHECK_SITE_KEY`가 있으면 reCAPTCHA v3 App Check를 초기화한다.
- Functions callable은 `ENABLE_APPCHECK_ENFORCEMENT=true`일 때만 `enforceAppCheck`를 켠다.
- Firebase Console의 전면 enforcement는 아직 보류 상태다.

로드맵:

| 단계 | 조건 | 액션 |
| --- | --- | --- |
| 1. 개발 토큰 정리 | Android debug token, 관리자 웹 debug token이 안정적으로 등록됨 | Firebase Console allowlist와 로컬 설정 절차를 문서화한다. |
| 2. 릴리스 검증 | Play Integrity, reCAPTCHA 사이트 키가 실제 빌드/도메인에서 정상 동작함 | 실기기와 관리자 웹 배포 URL에서 App Check token 발급을 확인한다. |
| 3. Functions부터 강제 | callable 호출 실패율이 낮고 디버그 토큰 회수 기준이 정리됨 | `ENABLE_APPCHECK_ENFORCEMENT=true`로 Functions callable enforcement를 켠다. |
| 4. Firestore/Storage enforcement 검토 | 모든 클라이언트가 App Check token을 안정적으로 붙임 | Firebase Console에서 Firestore/Storage enforcement를 단계적으로 켠다. |
| 5. 운영 모니터링 | 403/App Check 실패 로그가 정상 범위 | 실패 로그, 고객 문의, debug token 남용 여부를 주간 점검한다. |

롤백 기준:
- 정상 사용자 로그인, 예약, 서류 업로드, 관리자 심사 흐름에서 App Check 관련 실패가 반복되면 먼저 Functions 환경 변수 enforcement를 끄고, Firebase Console enforcement는 서비스별로 되돌린다.

## Firestore 인덱스와 쿼리 목록

현재 `firestore.indexes.json`에는 복합 인덱스가 없다. 현재 코드가 쓰는 대부분의 쿼리는 단일 필드 필터, 문서 직접 조회, `limit(1)` 조회, 또는 소량 컬렉션 조합이다. 운영 데이터가 늘면 아래 쿼리부터 서버 필터와 복합 인덱스가 필요해질 가능성이 높다.

| 위치 | 쿼리/조회 | 현재 인덱스 판단 |
| --- | --- | --- |
| `admin-web/src/App.tsx` | `users where role == MANAGER` | 단일 필드. 현재 복합 인덱스 없음 |
| `functions/src/auth.js` | `users where role == expectedRole and email == email limit 1`, `role + phone` | 복합 인덱스 필요 오류가 발생하면 `role,email`, `role,phone` 후보 |
| `functions/src/auth.js` | `users where email == email limit 5` | 단일 필드 |
| `functions/src/sync.js` | `appointmentRequests where patientUserId/guardianUserId == empty/null and email/phone == value` | 연결 자동화가 늘면 복합 인덱스 후보 |
| `FirebaseBookingRepository` | `companionSessions where appointmentRequestId == requestId limit 1` | 단일 필드 |
| `FirebaseBookingRepository`, `FirebaseGuardianReportRepository`, `FirebaseManagerRepository` | `sessionReports where sessionId == sessionId limit 1` | 단일 필드 |
| `functions/src/reminders.js` | `appointmentRequests where status in REQUESTED,MATCHED` | 단일 필드 |
| `functions/src/reminders.js` | `appointmentReminderJobs where state in PENDING,FAILED limit batchSize` | 단일 필드 |
| `functions/src/sync.js` | `appointmentReminderJobs where appointmentRequestId == requestId` | 단일 필드 |
| `functions/src/action-delivery.js` | `adminActionDeliveryJobs where state in PENDING,FAILED limit batchSize` | 단일 필드 |
| `functions/src/notifications.js` | `clientSupportRequests where status == ANSWERED` | 단일 필드 |
| `functions/src/notifications.js` | `users orderBy documentId limit 200` | 문서 ID 순회 |

복합 인덱스 추가 기준:
- 특정 화면에서 `where(...) + orderBy(createdAt/appointmentAt)` 조합을 추가할 때
- 관리자 대시보드가 전체 컬렉션을 읽지 않고 서버 필터와 정렬을 사용하도록 바뀔 때
- Firebase SDK가 `FAILED_PRECONDITION: The query requires an index` 오류와 생성 링크를 반환할 때

우선 후보:
- `appointmentRequests`: `status + appointmentAtEpochMillis desc`
- `appointmentRequests`: `managerUserId + status + appointmentAtEpochMillis desc`
- `clientSupportRequests`: `status + createdAt desc`
- `supportInquiries`: `managerUserId + createdAt desc`
- `adminActionNotifications`: `state + priority + createdAt desc`

## 백업/복원 리허설

현재 상태:
- `tools/firebase`에는 `backup:state`, `validate:backup`, `diff:state`, `restore:state:dry-run`, `restore:state:apply`가 있다.
- 기존 구현 상태 문서에는 백업 검증, diff, workflow, preflight 실행 기록이 있다.
- 격리된 Firebase 프로젝트 또는 emulator에 `restore:state:apply`를 실제로 수행하고 앱/관리자 웹에서 복구 상태를 확인한 리허설 기록은 현재 문서상 확인되지 않는다.

운영 기준:
- 운영 프로젝트에서는 바로 `restore:state:apply`를 실행하지 않는다.
- 월 1회 또는 큰 데이터 계약 변경 전에는 격리 프로젝트에서 `backup -> validate -> restore dry-run -> restore apply -> diff -> readiness/report` 순서로 리허설을 남긴다.
- 리허설 결과는 `docs/reports/`에 날짜별로 저장한다.

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

## Kakao REST API Key 운영 방침

현재 구조:
- 카카오 로그인은 Android 앱이 받은 Kakao access token을 Functions에 전달하고, Functions가 `https://kapi.kakao.com/v2/user/me`를 호출한다.
- 병원/약국 실좌표 검색은 Android 앱이 `https://dapi.kakao.com/v2/local/search/keyword.json`을 직접 호출한다.
- Android 앱의 Kakao Local REST API key는 `local.properties`의 `kakaoRestApiKey`에서 빌드 값으로 주입되며 저장소에 커밋하지 않는다.
- 같은 질의 결과는 앱 메모리에서 6시간 캐시한다.

현재 결정:
- MVP에서는 Kakao Local REST API를 Android 직접 호출로 유지한다.
- Admin key, client secret, 알림톡 API key 같은 서버 비밀값은 앱에 절대 넣지 않는다.
- Kakao Local REST API key는 공개 클라이언트에 포함될 수 있는 키로 보고, Kakao Developers 콘솔의 앱/플랫폼 제한, 쿼터 확인, 429 관측을 운영 기준으로 둔다.

Functions 프록시로 이전할 조건:
- Kakao 콘솔 쿼터가 반복적으로 임계치에 접근한다.
- REST API key 노출이 운영 리스크로 판단된다.
- 검색 결과를 서버 캐시하거나, 호출량을 사용자/세션별로 제한해야 한다.
- Kakao Local API 응답을 운영 감사 로그나 비용 리포트와 연결해야 한다.

## Mock 모드와 Firebase 모드

| 구분 | Mock 모드 | Firebase 모드 |
| --- | --- | --- |
| 진입 조건 | Android 앱에 Firebase 설정이 없거나 Mock Repository가 선택됨 | `google-services.json` 등 Firebase 설정이 준비됨 |
| 데이터 | 앱 내 목업 데이터와 메모리 상태 | Firestore, Storage, Auth, Functions |
| 가능 범위 | 화면 데모, 화면 전환, 모델 계약 확인 | 실제 권한, Rules, Storage 업로드, FCM, Functions, 운영 리포트 검증 |
| 불가능/제한 | Rules, App Check, FCM, Storage 원본, 실제 예약 연결 검증 | 외부 상태를 건드리므로 dry-run과 테스트 계정 기준 필요 |
| 운영 판단 | 운영 검증으로 보지 않음 | 운영 검증 기준 |

## Cloud Functions 목록

모든 함수의 기본 리전은 `asia-northeast3`이다.

| 함수 | 유형 | 트리거/호출 | 인증/권한 | 환경 변수 |
| --- | --- | --- | --- | --- |
| `kakaoCustomToken` | callable | Kakao access token으로 Firebase custom token 발급 | 클라이언트 생성 가능 역할 `PATIENT/GUARDIAN/MANAGER`, App Check는 env gated | 없음 |
| `naverCustomToken` | callable | Naver access token으로 Firebase custom token 발급 | 클라이언트 생성 가능 역할 `PATIENT/GUARDIAN/MANAGER`, App Check는 env gated | 없음 |
| `resolveLinkedParticipant` | callable | 연결 환자/보호자 조회 | 로그인 필요, 호출자 `PATIENT/GUARDIAN/ADMIN` | 없음 |
| `findSocialDuplicateEmailProvider` | callable | 소셜 가입 중복 이메일 확인 | 로그인 필요, 소셜 로그인 계정만 허용 | 없음 |
| `resolveAssignedManagerProfile` | callable | 배정 매니저 프로필 조회 | 로그인 필요, 예약 참여자 또는 관리자 | 없음 |
| `notifyClientSupportAnswered` | Firestore trigger | `clientSupportRequests/{supportRequestId}` 문서 변경 | 서비스 계정 | FCM 사용 |
| `notifyCompanionChatMessage` | Firestore trigger | `companionSessions/{sessionId}` 문서 변경 | 서비스 계정 | FCM 사용 |
| `notifyCompanionLocationAlert` | Firestore trigger | `companionSessions/{sessionId}` 문서 변경 | 서비스 계정 | FCM 사용 |
| `sendClientSupportAnswerReminders` | schedule | 매시간 정각 | 서비스 계정 | FCM 사용 |
| `cleanupStaleNotificationTokens` | schedule | 매일 04:30 Asia/Seoul | 서비스 계정 | FCM 사용 |
| `syncAppointmentReminderJobs` | schedule | 매일 09:00 Asia/Seoul | 서비스 계정 | 없음 |
| `deliverAppointmentReminderJobs` | schedule | 10분마다 | 서비스 계정 | `KAKAO_ALIMTALK_ENDPOINT`, `KAKAO_ALIMTALK_API_KEY`, `KAKAO_ALIMTALK_SENDER_KEY`, `KAKAO_ALIMTALK_AUTH_SCHEME` |
| `dispatchAppointmentReminderJobs` | callable | 예약 리마인더 수동 발송 | 로그인 필요, `users/{uid}.role == ADMIN` | 위 알림톡 환경 변수 |
| `deliverAdminActionDeliveryJobs` | schedule | 5분마다 | 서비스 계정 | `ADMIN_PUSH_ENDPOINT`, `ADMIN_PUSH_API_KEY`, `ADMIN_PUSH_AUTH_SCHEME` |
| `dispatchAdminActionDeliveryJobs` | callable | 관리자 후속 알림 수동 처리 | 로그인 필요, `users/{uid}.role == ADMIN` | 위 관리자 푸시 환경 변수 |
| `syncLinkedAppointmentParticipants` | Firestore trigger | `users/{userId}` 문서 변경 | 서비스 계정 | 없음 |
| `cleanupAppointmentReminderJobs` | Firestore trigger | `appointmentRequests/{appointmentRequestId}` 문서 변경 | 서비스 계정 | 없음 |

공통 callable 환경 변수:
- `ENABLE_APPCHECK_ENFORCEMENT=true`이면 callable Functions의 App Check enforcement가 켜진다.

## 운영 도구 명령어

| 명령 | 사용 시점 | 쓰기 여부 |
| --- | --- | --- |
| `npm --prefix tools/firebase run check:state` | 기준선 Auth 계정과 주요 컬렉션 수 확인 | 읽기 |
| `npm --prefix tools/firebase run check:manager-storage` | 매니저 서류 Firestore 메타데이터와 Storage 객체 정합성 확인 | 읽기 |
| `npm --prefix tools/firebase run cleanup:manager-storage:dry-run` | 고아 Storage 파일 삭제 후보 확인 | 읽기 |
| `npm --prefix tools/firebase run cleanup:manager-storage:apply` | 고아 Storage 파일 실제 삭제 | 쓰기 |
| `npm --prefix tools/firebase run check:readiness` | 역할별 화면 진입 준비도 확인 | 읽기 |
| `npm --prefix tools/firebase run backup:state` | Firestore 관리 컬렉션 백업 생성 | 읽기, 로컬 파일 생성 |
| `npm --prefix tools/firebase run validate:backup -- --file ...` | 백업 파일 구조 검증 | 로컬 파일 읽기 |
| `npm --prefix tools/firebase run capture:app -- --preset ...` | 연결된 Android 기기 화면 증적 수집 | 기기 화면 캡처, 로컬 파일 생성 |
| `npm --prefix tools/firebase run diff:state -- --file ...` | 백업과 현재 Firestore 상태 비교 | 읽기 |
| `npm --prefix tools/firebase run report:ops -- --file ...` | 운영 상태 HTML 리포트 생성 | 읽기, 로컬 파일 생성 |
| `npm --prefix tools/firebase run workflow:ops -- --file ...` | 상태 점검, 백업 검증, diff, 리포트를 한 번에 수행 | 읽기, 로컬 파일 생성 |
| `npm --prefix tools/firebase run preflight:local -- --file ...` | Firebase 운영 워크플로, Android 빌드/테스트를 로컬에서 묶어 검증 | 읽기, 빌드 산출물 생성 |
| `npm --prefix tools/firebase run preflight:ci` | GitHub Actions용 프리플라이트 | CI 환경 기준 |
| `npm --prefix tools/firebase run reset:baseline:dry-run` | 기준선 초기화 영향 확인 | 읽기 |
| `npm --prefix tools/firebase run reset:baseline:apply` | 개발용 Firestore 기준선 재생성 | 쓰기 |
| `npm --prefix tools/firebase run seed:sample:dry-run` | 샘플 예약/세션 데이터 주입 영향 확인 | 읽기 |
| `npm --prefix tools/firebase run seed:sample:apply` | 샘플 예약/세션 데이터 주입 | 쓰기 |
| `npm --prefix tools/firebase run seed:manager-docs:dry-run` | 매니저 서류 샘플 업로드 영향 확인 | 읽기 |
| `npm --prefix tools/firebase run seed:manager-docs:apply` | 매니저 서류 샘플 업로드 | 쓰기 |
| `npm --prefix tools/firebase run restore:state:dry-run -- --file ...` | 백업 복원 계획 확인 | 읽기 |
| `npm --prefix tools/firebase run restore:state:apply -- --file ...` | Firestore 문서 실제 복원 | 쓰기 |

쓰기 명령은 운영 프로젝트에서 바로 실행하지 않고, dry-run 또는 격리 프로젝트 검증 후 명시적으로 수행한다.

## 참고 공식 문서

- Firebase 가격표: <https://firebase.google.com/pricing>
- Firestore 과금 방식: <https://firebase.google.com/docs/firestore/pricing>
- Google Cloud Firestore 가격표: <https://cloud.google.com/firestore/pricing>
- Kakao Developers 쿼터: <https://developers.kakao.com/docs/en/getting-started/quota>
- Kakao Developers REST API Reference: <https://developers.kakao.com/docs/en/rest-api/reference>
- Kakao Local REST API: <https://developers.kakao.com/docs/en/local/dev-guide>
