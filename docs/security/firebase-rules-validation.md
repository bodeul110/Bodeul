# Firestore/Storage Rules 검증 정리

기준일: 2026-06-25

이 문서는 현재 `firestore.rules`, `storage.rules` 기준으로 환자, 보호자, 매니저, 관리자 권한 경계를 정리한다. 결론부터 말하면 현재 운영 권한은 Firebase Auth 로그인과 `users/{uid}.role` 문서 필드로 판정하며, custom claims 기반 관리자 권한은 아직 사용하지 않는다.

## 관리자 권한 구조

- 인증 기준: Firebase Authentication 로그인 사용자
- 역할 기준: `users/{uid}.role`
- 관리자 값: `ADMIN`
- 관리자 웹 진입: `admin-web/src/adminSession.ts`에서 로그인한 사용자의 `users/{uid}` 문서를 읽고 `role == "ADMIN"`인지 확인한다.
- Firestore Rules: `currentRole()`이 `users/{request.auth.uid}.role`을 읽고 `isAdmin()`, `isManager()`, `isPatient()`, `isGuardian()`을 계산한다.
- Storage Rules: Firestore와 동일하게 `users/{uid}.role`을 읽어 관리자와 매니저 본인 여부를 판정한다.
- Functions 수동 실행 callable: `dispatchAppointmentReminderJobs`, `dispatchAdminActionDeliveryJobs`는 호출자의 `users/{uid}.role == ADMIN`을 별도로 확인한다.

현재 custom claims를 쓰지 않는 이유는 Android 앱, 관리자 웹, Rules, Functions가 모두 같은 `users` 문서 역할 계약을 공유하고 있고, 관리자 수가 적은 초기 운영에서는 권한 변경을 Firestore 문서 변경으로 즉시 추적하는 편이 단순하기 때문이다. 다만 관리자 계정 수가 늘거나 Rules role read 비용과 권한 전파 정책을 더 엄격히 분리해야 하면 custom claims 전환을 검토한다.

## 검증 상태

| 항목 | 현재 상태 |
| --- | --- |
| 정적 규칙 검토 | 2026-06-25 기준 `firestore.rules`, `storage.rules`를 다시 읽어 역할별 허용 범위를 문서화했다. |
| 기존 실계정 검증 기록 | `docs/security/firestore-hardening.md`에 2026-05-04 기준 guardian, manager, patient 권한 축소 검증 기록이 있다. |
| 자동 Rules 테스트 | `tools/firebase`의 `test:rules`와 `.github/workflows/firebase-rules.yml`로 Firestore/Storage emulator 기반 자동 테스트를 실행한다. |
| 배포 검증 | Rules 파일 변경이 없으므로 이번 작업에서는 배포를 수행하지 않았다. |

## Firestore 권한 경계

| 컬렉션 | 환자 | 보호자 | 매니저 | 관리자 |
| --- | --- | --- | --- | --- |
| `users` | 본인 문서 읽기/생성/수정 가능. 역할 변경은 제한된다. | 본인 문서 읽기/생성/수정 가능. 역할 변경은 제한된다. | 본인 문서 읽기/생성/수정 가능. 심사 관련 일부 필드는 제한된다. | 사용자 목록, 개별 문서 읽기/수정/삭제 가능 |
| `appointmentRequests` | 본인이 환자 또는 요청자인 예약 읽기/생성/일부 수정 가능 | 본인이 보호자 또는 요청자인 예약 읽기/생성/일부 수정 가능 | 배정된 예약의 상태 변경 가능 | 전체 읽기/쓰기 가능 |
| `companionSessions` | 연결 예약 참여자인 경우 읽기, 환자 채팅/취소 관련 업데이트 가능 | 연결 예약 참여자인 경우 읽기, 보호자 채팅/취소 관련 업데이트 가능 | 배정 매니저인 경우 읽기/생성/진행 업데이트 가능 | 전체 읽기/쓰기 가능 |
| `hospitalGuides` | 로그인 사용자 읽기 가능 | 로그인 사용자 읽기 가능 | 로그인 사용자 읽기 가능 | 쓰기 가능 |
| `sessionReports` | 연결 세션 참여자인 경우 읽기 가능 | 연결 세션 참여자인 경우 읽기 가능 | 연결 세션 매니저인 경우 생성/수정 가능 | 전체 읽기/쓰기 가능 |
| `appointmentFollowUps` | 연결 예약 참여자인 경우 읽기/생성/수정 가능 | 연결 예약 참여자인 경우 읽기/생성/수정 가능 | 직접 쓰기 없음 | 전체 읽기/쓰기 가능 |
| `supportInquiries` | 접근 없음 | 접근 없음 | 본인 문의 생성/읽기 가능 | 전체 읽기/쓰기 가능 |
| `clientSupportRequests` | 본인 문의 생성/읽기 가능 | 본인 문의 생성/읽기 가능 | 접근 없음 | 전체 읽기/쓰기 가능 |
| 관리자 운영 컬렉션 | 접근 없음 | 접근 없음 | 접근 없음 | 전체 읽기/쓰기 가능 |
| `appointmentReminderJobs` | 접근 없음 | 접근 없음 | 접근 없음 | 관리자 읽기만 가능, 클라이언트 쓰기 금지 |

## Storage 권한 경계

| 경로 | 환자 | 보호자 | 매니저 | 관리자 |
| --- | --- | --- | --- | --- |
| `manager-documents/{managerUserId}/{documentKey}/{fileName}` | 접근 없음 | 접근 없음 | 본인 경로 읽기/쓰기 가능. 허용 키와 파일 형식, 10MB 제한 적용 | 모든 매니저 서류 읽기 가능 |
| `companion-chat-attachments/{sessionId}/{fileName}` | 세션 참여자이면 읽기/쓰기 가능 | 세션 참여자이면 읽기/쓰기 가능 | 세션 참여자이면 읽기/쓰기 가능 | 규칙상 세션 참여자 기준. 관리자 별도 우회는 없음 |
| 그 외 경로 | 거부 | 거부 | 거부 | 거부 |

## 확인된 보안 판단

- 관리자 권한은 단순 Firebase 로그인만으로 부여되지 않는다. 반드시 `users/{uid}.role == ADMIN`이어야 한다.
- 사용자가 스스로 `ADMIN` 역할을 만들거나 바꾸는 경로는 Firestore Rules에서 차단한다.
- `users` 목록 조회는 관리자만 가능하므로 환자/보호자/매니저가 이메일이나 전화번호로 다른 사용자를 직접 검색하는 구조는 닫혀 있다.
- 참여자 연결과 배정 매니저 조회는 클라이언트 직접 쿼리 대신 Functions callable을 사용한다.
- 매니저 서류 원본은 Storage에서 매니저 본인과 관리자 읽기 경계가 분리되어 있다.

## 남은 보강

- Rules 변경 시 PR에서 `Firebase Rules` workflow 결과를 확인한다.
- 관리자 custom claims 전환 여부는 관리자 계정 수, role read 비용, 긴급 권한 회수 정책이 확정된 뒤 다시 판단한다.
- `companion-chat-attachments`에 관리자 감사 목적 읽기 권한이 필요한지 운영 요구를 확정한다.

## 자동 테스트

로컬 실행:

```powershell
cd D:\BoDeul
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
npm --prefix tools/firebase run test:rules
```

검증 범위:
- `users`: 본인/관리자 읽기, 관리자 목록 조회, 클라이언트 역할 생성 제한
- `appointmentRequests`: 참여자 읽기, 환자 생성/취소, 비참여자 거부
- `companionSessions`: 참여자 읽기, 배정 매니저 생성/진행 수정, 환자 채팅 수정, 비허용 필드 거부
- `sessionReports`, 관리자 전용 컬렉션, `appointmentReminderJobs`: 역할별 쓰기/읽기 경계
- 관리자 전용 컬렉션: `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs`
- Storage `manager-documents`: 매니저 본인과 관리자 읽기, 허용 문서 키/파일 형식 검증
- Storage `companion-chat-attachments`: 세션 참여자 읽기/쓰기, 비참여자와 비허용 파일 형식 거부

CI 실행:
- workflow: `.github/workflows/firebase-rules.yml`
- 실행 조건: Rules 파일, Firebase 설정, Rules 테스트, `tools/firebase` 테스트 의존성 변경 PR
- emulator 실행을 위해 JDK 21을 사용한다.
