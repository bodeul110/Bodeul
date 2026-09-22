# Firestore/Storage Rules 권한 경계와 검증

기준일: 2026-09-21

이 문서는 저장소의 [Firestore Rules](../../firestore.rules), [Storage Rules](../../storage.rules)와 [에뮬레이터 테스트](../../tools/firebase/rules-emulator-tests/run-rules-tests.js)를 기준으로 한다. 이번 갱신은 코드와 테스트 시나리오의 정적 대조이며, Rules 재배포나 에뮬레이터 재실행 결과가 아니다.

## 인증과 업무 권한

- Firebase Auth는 사용자 신원 확인을 맡는다. 로그인만으로 관리자 업무 권한을 부여하지 않는다.
- Core API는 Firebase ID token과 PostgreSQL 역할·참여 관계·동의를 확인한다.
- 관리자 서버는 PostgreSQL `app_users.role=ADMIN`과 활성 세부 역할 `SUPER_ADMIN`, `OPERATIONS`, `DEVELOPER`를 확인한다. 원문 접근과 권한 변경에는 사유·감사를 적용한다.
- Firestore와 Storage의 `isAdmin()`은 현재 항상 `false`다. 브라우저의 ADMIN 문서나 custom claim으로 서버 인가를 우회할 수 없다.
- Firebase에 남긴 본인 프로필·매니저 제출·지원 경로는 `users/{uid}.role`과 소유 관계를 검사한다. 이 역할 필드가 관리자 서버의 최종 권한 원본은 아니다.
- Realtime의 `role: authenticated` custom claim은 구독용 인증 역할이며 관리자 권한이 아니다.

자세한 관리자 계약은 [관리자 RBAC](../architecture/admin-rbac.md), 데이터 경계는 [목표 인프라](../architecture/target-infrastructure.md)를 따른다.

## Firestore 클라이언트 접근

아래 표는 Firebase 클라이언트 SDK에 적용되는 Rules다. Admin SDK와 서버 서비스 계정은 Rules를 우회하므로 서버의 인가·감사와 IAM을 별도로 검증해야 한다.

| 경로 | 허용하는 클라이언트 접근 | 차단하는 접근 |
| --- | --- | --- |
| `users/{uid}` | 본인 조회, PATIENT/GUARDIAN/MANAGER 본인 생성, 역할을 유지한 안전한 프로필·제출 갱신 | 다른 사용자 조회, 목록 조회, 삭제, ADMIN 생성·역할 승격, 서버 심사·삭제 claim 필드 위조 |
| `appointmentRequests` | 해당 문서의 환자 본인에게만 과거 비교 자료 읽기 | 보호자·매니저·관리자 직접 읽기, 모든 생성·수정·삭제 |
| `companionSessions` | 해당 문서의 환자 본인에게만 과거 비교 자료 읽기 | 보호자·매니저·관리자 직접 읽기, 채팅·위치·상태를 포함한 모든 쓰기 |
| `sessionReports` | 연결된 Firestore 세션의 환자 본인 읽기 | 보호자·매니저·관리자 직접 읽기, 모든 쓰기 |
| `appointmentFollowUps` | 연결된 Firestore 예약의 환자 본인 읽기 | 보호자·매니저·관리자 직접 읽기, 모든 쓰기 |
| `hospitalGuides` | 로그인 사용자 읽기 | 모든 클라이언트 쓰기 |
| `supportInquiries` | 매니저 본인 문의 생성·읽기 | 타인 조회와 클라이언트 수정·삭제 |
| `clientSupportRequests` | PATIENT/GUARDIAN의 본인 문의 생성, 본인 문서 읽기 | 타인 조회와 클라이언트 수정·삭제 |
| `adminSettlementRecords`, `adminEmergencyIssues`, 알림·감사·전달 job 컬렉션, `appointmentReminderJobs` | 없음 | ADMIN을 포함한 모든 클라이언트 읽기·쓰기 |

Core 업무의 원본은 PostgreSQL이다. Firestore 비교 자료 읽기 권한이 남아 있다는 이유로 신규 기능을 Firestore 경로에 구현하지 않는다. 관리자 업무와 보호자·매니저의 조회는 각 서버의 권한 경계를 거친다.

## Storage 클라이언트 접근

| 경로 | 현재 경계 |
| --- | --- |
| `manager-documents/{managerUserId}/{documentKey}/{fileName}` | 매니저 본인 읽기와 새 고유 경로 생성만 허용. 덮어쓰기·삭제는 서버 보존 절차로 처리 |
| 신규 매니저 제출 | `license` 또는 `nursingLicense`, JPEG/PNG/WebP, 파일당 최대 10 MiB. 실제 제출 메타데이터는 자격 증빙 1종만 허용 |
| 삭제 claim이 있는 매니저 | 신규 업로드·제출 변경 차단. 클라이언트가 claim을 지워 우회할 수 없음 |
| 신분증·범죄경력·건강 원본 | 신규 수집 경로 차단. 기존 자료의 정리·이관은 별도 보존 절차 |
| legacy `companion-chat-attachments/{sessionId}/{fileName}` | 연결된 Firestore 세션의 환자 본인 읽기만 유지. 보호자·매니저·관리자 직접 읽기와 모든 클라이언트 쓰기 차단 |
| Core-only 채팅 첨부 | Spring Core API가 PostgreSQL 참여 관계·동의·만료를 확인하고 버킷 IAM으로 원본 처리 |
| 관리자 증빙 원문 | Next.js 서버의 세부 역할·사유·감사 경유. Storage의 ADMIN 직접 읽기는 허용하지 않음 |
| 그 외 경로 | 거부 |

## 검증 방법과 범위

JDK 21과 `tools/firebase` 의존성을 준비한 뒤 저장소 루트에서 실행한다. Codex 터미널에서 실행할 수 있으며 Android Studio는 필수가 아니다.

```powershell
java -version
npm --prefix tools/firebase run test:rules
```

현재 테스트 소스는 다음을 검사한다.

- 본인 프로필 읽기와 역할 위조 거부, ADMIN 브라우저의 타인 프로필·업무·운영 컬렉션 접근 거부
- Core 전환 컬렉션의 모든 직접 쓰기 거부, 환자 비교 자료 읽기와 보호자·매니저 직접 읽기 거부
- 자격 증빙 canonical 경로 일치, 1종 제출, 심사 필드 보호, legacy 교체와 legal hold
- 삭제 claim 중 원본 재참조·업로드·상태 변경 거부
- Storage의 새 원본 생성 제한, 파일 형식·크기·타인 경로 거부, 채팅 직접 쓰기 거부

CI 기준은 [Firebase Rules workflow](../../.github/workflows/firebase-rules.yml)다. PR의 실제 체크 결과와 배포 대상 Rules revision을 각각 확인한다.

## 적용 시 주의

- [2026-05-04 보강 기록](firestore-hardening.md)은 당시 검증 결과다. 현재 권한 표를 대신하지 않는다.
- Rules만 바꾸면 구버전 Android 관리자 화면이나 legacy 데이터 경로가 실패할 수 있다. [관리자 RBAC](../architecture/admin-rbac.md)의 앱·Rules 동시 릴리스 경계를 유지한다.
- 운영 관리자 Auth 계정 등록, MFA 완료, PostgreSQL 세부 역할 부여, 운영 DB 연결은 서로 다른 단계다. 계정 등록만으로 운영 로그인이 검증된 것은 아니다.
- 이번 문서 수정에서는 Rules·데이터·IAM을 변경하지 않았다. 운영 적용 전 에뮬레이터, 서버 권한 테스트, 대상 환경 smoke를 별도로 통과시킨다.
