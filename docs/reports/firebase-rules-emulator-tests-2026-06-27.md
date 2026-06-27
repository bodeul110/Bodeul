# Firestore/Storage Rules emulator 테스트 추가

기준일: 2026-06-27
관련 이슈: #63

## 구현한 내용

- `tools/firebase`에 `test:rules` 실행점을 추가했다.
- `@firebase/rules-unit-testing` 기반 Firestore/Storage Rules 테스트 러너를 추가했다.
- Rules 변경 시 자동으로 실행되는 `Firebase Rules` GitHub Actions workflow를 추가했다.
- 자동 테스트 상태와 실행 방법을 보안/운영 문서에 반영했다.

## 변경된 범위

- `.github/workflows/firebase-rules.yml`
- `.gitignore`
- `tools/firebase/package.json`
- `tools/firebase/package-lock.json`
- `tools/firebase/rules-emulator-tests/run-rules-tests.js`
- `docs/security/firebase-rules-validation.md`
- `docs/operations/firebase/tools.md`
- `docs/operations/infrastructure-operations-baseline.md`
- `docs/reports/README.md`

## 테스트 시나리오

| 영역 | 확인한 내용 |
| --- | --- |
| `users` | 본인/관리자 읽기, 관리자 목록 조회, 일반 사용자의 `ADMIN` 역할 생성 거부 |
| `appointmentRequests` | 환자/보호자/매니저 참여자 읽기, 비참여자 거부, 환자 생성/취소 허용, 매니저 생성 거부 |
| `companionSessions` | 참여자 읽기, 배정 매니저 생성/진행 수정, 환자 채팅 수정, 보호자 비허용 필드 수정 거부 |
| `sessionReports` | 참여자 읽기, 배정 매니저 작성 허용, 보호자 작성 거부 |
| 관리자 운영 컬렉션 | `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs` 관리자 CRUD 허용과 매니저 읽기/쓰기/삭제 거부 |
| `appointmentReminderJobs` | 관리자 읽기와 클라이언트 쓰기 금지 확인 |
| `manager-documents` | 매니저 본인/관리자 읽기, 환자 읽기 거부, 문서 키/파일 형식/타 매니저 쓰기 거부 |
| `companion-chat-attachments` | 세션 참여자와 관리자 읽기, 보호자 업로드 허용, 비참여자와 비허용 파일 형식 거부 |

## 검증

- `node --check tools\firebase\rules-emulator-tests\run-rules-tests.js`
- `npm --prefix tools/firebase run test:rules`
- `yq e '.' .github/workflows/firebase-rules.yml`

로컬 emulator 테스트는 기본 Java 17에서 Firebase CLI 요구사항 때문에 실패했고, Android Studio JBR 21로 `JAVA_HOME`을 지정해 통과했다.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
npm --prefix tools/firebase run test:rules
```

결과:

```text
Rules emulator 테스트 통과: 7/7
```

## 남은 범위

- PR 생성 후 GitHub Actions의 `Firebase Rules` workflow 통과 여부를 확인해야 한다.
- Firebase CLI 15.22.3의 transitive dev dependency에서 moderate audit 경고가 남아 있다. `npm audit fix --force`는 큰 버전 변경을 유도하므로 적용하지 않았다.
