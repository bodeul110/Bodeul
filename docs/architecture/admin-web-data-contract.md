# 관리자 웹 데이터 계약

기준일: 2026-06-26

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

`admin-web`을 별도 레포로 분리할 수 있는지 판단하기 위해 관리자 웹이 의존하는 Firebase Auth, Firestore, Storage, Functions 계약을 명확히 한다.

## 선택한 방식

현재 코드 기준으로 `admin-web`이 직접 읽고 쓰는 계약만 먼저 문서화한다. 아직 구현되지 않은 호출이나 예정 기능은 분리 후속 조건으로 둔다.

## 대안

- 레포를 먼저 분리한 뒤 깨지는 계약을 따라가며 보완한다.
- Android 앱, Functions, Rules까지 전체 데이터 계약을 한 번에 문서화한다.
- 관리자 웹을 분리하지 않고 현재 저장소 내부 문서만 유지한다.

## 선택 이유

관리자 웹은 Firestore와 Storage를 직접 사용하므로, 별도 레포로 분리하면 데이터 계약이 저장소 사이의 API 역할을 하게 된다. 현재 규모에서는 전체 플랫폼 계약보다 관리자 웹이 실제 사용하는 최소 계약을 먼저 고정하는 편이 안전하다.

## 리스크

- 이 문서는 2026-06-26 현재 `admin-web` 코드 기준이다.
- Firestore Rules, Storage Rules, Functions가 바뀌면 이 문서도 함께 갱신해야 한다.
- 관리자 웹에 문의 응답, 알림 수동 실행, 정산 관리 기능이 추가되면 계약 범위가 늘어난다.

## Firebase Auth 계약

| 항목 | 현재 기준 |
| --- | --- |
| 로그인 방식 | Firebase Auth 이메일/비밀번호 |
| 관리자 판별 | 로그인한 사용자의 `users/{uid}.role == ADMIN` |
| 비관리자 처리 | 세션 검증 실패 후 `signOut` |
| 유휴 세션 | 15분 동안 활동이 없으면 로그아웃 |
| App Check | `VITE_FIREBASE_APPCHECK_SITE_KEY`가 있을 때 웹 App Check 초기화 |

관리자 웹은 custom claims를 사용하지 않는다. 운영자가 늘거나 관리자 권한 변경 이력이 중요해지면 custom claims, MFA, 감사 로그 강화 여부를 별도로 검토한다.

## Firestore 읽기 계약

### 관리자 세션 확인

| 항목 | 값 |
| --- | --- |
| 컬렉션 | `users` |
| 문서 | `users/{uid}` |
| 코드 위치 | `admin-web/src/App.tsx`, `admin-web/src/adminSession.ts` |
| 목적 | 로그인 사용자가 관리자인지 확인 |
| 필수 필드 | `role` |
| 표시 필드 | `name`, `email` |

조건:
- 문서가 없으면 관리자 세션으로 보지 않는다.
- `role`이 `ADMIN`이 아니면 관리자 화면에 진입시키지 않는다.

### 매니저 목록 구독

| 항목 | 값 |
| --- | --- |
| 컬렉션 | `users` |
| 쿼리 | `where("role", "==", "MANAGER")` |
| 코드 위치 | `admin-web/src/App.tsx` |
| 목적 | 매니저 서류 심사 목록 표시 |
| 정렬 | 클라이언트에서 `name` 기준 한국어 정렬 |

읽는 주요 필드:

| 필드 | 목적 | 비고 |
| --- | --- | --- |
| `name` | 매니저 이름 표시 | 없으면 `이름 없음` |
| `email` | 연락처 표시, 기본 마스킹 | 상세 모달에서 원문 확인 |
| `phone` | 연락처 표시, 기본 마스킹 | 상세 모달에서 원문 확인 |
| `createdAt` | 신청일 표시 | `Date`, epoch, Firestore Timestamp를 허용 |
| `managerDocumentStatus` | 심사 상태 표시 | `PENDING_REVIEW`, `UNDER_REVIEW`, `APPROVED`, `REJECTED` |
| `managerDocumentSummary` | 제출 서류 요약 | 승인/반려 전 필수로 본다 |
| `managerDocumentReviewNote` | 반려 사유 또는 보완 메모 | 반려 시 입력 |
| `managerDocumentFiles` | Storage 원본 메타데이터 | 우선 사용 |
| `managerDocumentFilePaths` | Storage 경로 fallback | 이전 계약 호환 |
| 레거시 파일 경로 필드 | 이전 업로드 경로 fallback | 아래 레거시 필드 표 참고 |

레거시 파일 경로 필드:

| 문서 슬롯 | 후보 필드 |
| --- | --- |
| 신분증 | `managerIdCardFilePath`, `idCardFilePath`, `managerIdCardStoragePath` |
| 자격증 | `managerLicenseFilePath`, `licenseFilePath`, `managerLicenseStoragePath`, `managerHealthCertificateFilePath`, `healthCertificateFilePath`, `managerHealthCertificateStoragePath` |
| 범죄경력 조회 | `managerCriminalRecordFilePath`, `criminalRecordFilePath`, `managerCriminalRecordStoragePath` |

## Firestore 쓰기 계약

관리자 웹은 현재 매니저 서류 심사 결과만 Firestore에 쓴다.

| 항목 | 값 |
| --- | --- |
| 컬렉션 | `users` |
| 문서 | `users/{managerUserId}` |
| 코드 위치 | `admin-web/src/App.tsx` |
| 쓰기 API | `updateDoc` |
| 액션 | 승인 또는 반려 |

쓰기 필드:

| 필드 | 값 |
| --- | --- |
| `managerDocumentStatus` | `APPROVED` 또는 `REJECTED` |
| `managerDocumentReviewNote` | 반려 사유. 승인 시 빈 문자열 |
| `managerDocumentReviewedAt` | `serverTimestamp()` |
| `managerDocumentReviewedByName` | 현재 관리자 이름 |
| `managerDocumentHistory` | `arrayUnion`으로 심사 이벤트 추가 |

`managerDocumentHistory` 이벤트 구조:

| 필드 | 값 |
| --- | --- |
| `eventType` | `APPROVED` 또는 `REJECTED` |
| `happenedAt` | 클라이언트 `Date.now()` |
| `actorName` | 현재 관리자 이름 |
| `summary` | 심사 당시 `managerDocumentSummary` |
| `reviewNote` | 반려 사유 또는 빈 문자열 |

주의:
- 심사 저장 전 `managerDocumentSummary`가 비어 있으면 저장하지 않는다.
- 승인 시 신분증, 자격증, 범죄경력 조회 체크리스트가 모두 확인돼야 한다.
- 반려 시 반려 사유가 필요하다.

## Storage 읽기 계약

관리자 웹은 매니저 서류 원본을 Firebase Storage에서 읽는다.

| 항목 | 값 |
| --- | --- |
| 기본 폴더 | `manager-documents/{managerUserId}/{documentKey}` |
| 문서 슬롯 | `idCard`, `license`, `criminalRecord` |
| 호환 슬롯 | `healthCertificate`는 `license` 슬롯 후보로 함께 읽음 |
| 읽기 API | `getDownloadURL`, `getMetadata`, `listAll` |
| 코드 위치 | `admin-web/src/App.tsx` |

읽기 순서:

1. `users/{managerUserId}.managerDocumentFiles`의 명시 경로를 우선 사용한다.
2. `managerDocumentFilePaths`를 fallback으로 사용한다.
3. 레거시 파일 경로 필드를 fallback으로 사용한다.
4. 명시 경로가 없으면 `manager-documents/{managerUserId}/{documentKey}` 폴더를 탐색한다.

관리자 웹은 Storage 원본을 쓰거나 삭제하지 않는다.

## Functions 계약

현재 `admin-web` 코드는 callable Functions를 직접 호출하지 않는다.

향후 아래 기능이 관리자 웹에 연결되면 이 문서를 갱신해야 한다.

| 예정 가능 기능 | 관련 Functions 후보 |
| --- | --- |
| 관리자 후속 알림 수동 발송 | `dispatchAdminActionDeliveryJobs` |
| 예약 리마인더 수동 발송 | `dispatchAppointmentReminderJobs` |
| 관리자 권한 검증 서버화 | 신규 callable 또는 서버 API |

## 환경 변수와 배포 계약

| 항목 | 현재 기준 |
| --- | --- |
| Firebase Web config | `admin-web/firebase.ts`에 dev 프로젝트 값이 직접 들어 있음 |
| App Check site key | `VITE_FIREBASE_APPCHECK_SITE_KEY` |
| App Check debug token | `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` |
| 빌드 명령 | `npm --prefix admin-web run build` |
| 배포 대상 | Firebase Hosting `admin-web/dist` |
| preview 배포 | `firebase hosting:channel:deploy admin-web-preview --project <firebase-project-id> --expires 7d` |
| live 배포 | `firebase deploy --only hosting --project <firebase-project-id>` |

레포 분리 전에는 Firebase Web config를 환경별로 주입할지, 파일로 유지할지 결정해야 한다. 분리 후에는 `admin-web` 레포의 secret과 GitHub Environment 기준을 별도로 둔다.

## Rules 영향

관리자 웹 분리 후에도 `firestore.rules`와 `storage.rules`는 데이터 접근의 실제 보안 경계다.

필수 접근:
- 관리자 계정은 `users`의 매니저 문서를 읽을 수 있어야 한다.
- 관리자 계정은 매니저 서류 심사 필드를 업데이트할 수 있어야 한다.
- 관리자 계정은 `manager-documents/{managerUserId}/...` Storage 객체를 읽을 수 있어야 한다.

Rules가 이 계약을 깨면 관리자 웹은 별도 레포로 분리돼 있어도 정상 동작하지 않는다.

## 레포 분리 시 갱신 기준

아래 파일이나 규칙이 바뀌면 이 문서를 함께 갱신한다.

- `admin-web/src/App.tsx`
- `admin-web/src/adminSession.ts`
- `admin-web/firebase.ts`
- `firestore.rules`
- `storage.rules`
- `functions/src/*.js` 중 관리자 웹 callable 추가분
- `firebase.json` Hosting 설정
