# Firestore 쿼리/인덱스 운영 점검

기준일: 2026-06-26  
관련 이슈: #67

## 구현한 내용

- Android 앱, 관리자 웹, Firebase Functions, Firebase 운영 도구의 Firestore 조회 경로를 재점검했다.
- 현재 `firestore.indexes.json`에 복합 인덱스를 추가하지 않아도 되는 이유와 향후 추가 후보를 분리했다.
- 관리자 대시보드처럼 현재는 전체 컬렉션을 읽지만 운영 데이터 증가 시 페이지네이션과 서버 필터가 필요한 지점을 정리했다.

## 변경된 범위

- `docs/operations/infrastructure-operations-baseline.md`
- `docs/reports/firestore-query-index-review-2026-06-26.md`
- `docs/reports/README.md`
- `docs/status/implementation-status.md`

## 결론

- 현재 `firestore.indexes.json`은 복합 인덱스 없이 유지한다.
- 현재 코드에는 `where(...) + orderBy(...)` 형태의 운영 복합 쿼리가 없다.
- 단일 필드 필터, 문서 직접 조회, `limit(1)` 상세 조회가 대부분이다.
- 인덱스보다 먼저 봐야 할 리스크는 관리자 앱 대시보드의 전체 컬렉션 스캔이다.
- Firebase SDK에서 `FAILED_PRECONDITION: The query requires an index` 오류가 발생하거나 서버 정렬/페이지네이션을 추가할 때 복합 인덱스를 반영한다.

## 주요 쿼리 목록

| 구분 | 위치 | 현재 조회 | 판단 |
| --- | --- | --- | --- |
| 관리자 앱 대시보드 | `FirebaseAdminRepository` | `appointmentRequests`, `users`, `companionSessions`, `sessionReports`, `adminSettlementRecords`, `adminEmergencyIssues`, `appointmentFollowUps`, `supportInquiries`, `adminActionNotifications`, `adminActionDeliveries`, `adminAuditLogs`, `hospitalGuides`, `clientSupportRequests` 전체 조회 | 복합 인덱스 이슈는 아니지만 읽기 비용과 화면 지연의 1순위 리스크 |
| 예약 목록 | `FirebaseBookingRepository` | `appointmentRequests where patientUserId == uid`, `guardianUserId == uid` | 단일 필드 |
| 예약 상세 | `FirebaseBookingRepository`, `FirebaseGuardianReportRepository` | `companionSessions where appointmentRequestId == requestId limit 1`, `hospitalGuides where hospitalName == name`, `sessionReports where sessionId == sessionId limit 1` | 단일 필드 |
| 매니저 홈/이력 | `FirebaseManagerRepository` | `companionSessions where managerUserId == managerUserId` 후 클라이언트에서 진행/완료 상태 분리 | 단일 필드. 운영 데이터 증가 시 `managerUserId + currentStatus` 후보 |
| 매니저 문의 | `FirebaseManagerRepository` | `supportInquiries where managerUserId == managerUserId` 후 클라이언트 정렬 | 단일 필드. 서버 정렬 전환 시 `managerUserId + createdAt desc` 후보 |
| 환자/보호자 문의 | `FirebaseClientSupportRepository` | `clientSupportRequests where userId == uid` 후 클라이언트 정렬 | 단일 필드. 서버 정렬 전환 시 `userId + createdAt desc` 후보 |
| 관리자 웹 매니저 목록 | `admin-web/src/App.tsx` | `users where role == MANAGER` | 단일 필드 |
| 로그인/중복 확인 Functions | `functions/src/auth.js` | `users where role == expectedRole and email == email limit 1`, `role + phone`, `email limit 5` | 동일성 필터 중심. 인덱스 오류가 관측되면 `role,email`, `role,phone` 후보 |
| 환자/보호자 자동 연결 Functions | `functions/src/sync.js` | `appointmentRequests where patientUserId/guardianUserId == empty/null and email/phone == value` | 연결 자동화 데이터가 늘면 복합 인덱스 후보 |
| 예약 리마인더 Functions | `functions/src/reminders.js` | `appointmentRequests where status in (...)`, `appointmentReminderJobs where state in (...) limit batchSize` | 단일 필드. 재시도 정렬을 추가하면 `state + nextAttemptAt asc` 후보 |
| 관리자 액션 전달 Functions | `functions/src/action-delivery.js` | `adminActionDeliveryJobs where state in (...) limit batchSize`, `users where role == ADMIN` | 단일 필드. 우선순위/시각 정렬을 추가하면 복합 후보 |
| 알림 Functions | `functions/src/notifications.js` | `clientSupportRequests where status == ANSWERED`, `users orderBy documentId limit 200` | 단일 필드 또는 문서 ID 순회 |
| 운영 도구 | `tools/firebase` | 백업, diff, readiness, 리포트용 전체 조회 | 사용자 런타임 쿼리가 아니므로 인덱스 선반영 대상에서 제외 |

## 페이지네이션/필터 전환 후보

| 우선순위 | 대상 | 전환 기준 | 후보 인덱스 |
| --- | --- | --- | --- |
| 1 | 관리자 앱 대시보드 요청 목록 | 요청 수가 늘어 화면 진입마다 전체 컬렉션 read가 부담될 때 | `appointmentRequests: status + appointmentAtEpochMillis desc` |
| 2 | 관리자 앱 매니저별 운영 요청 | 매니저 배정/상태/일자 필터를 서버에서 처리할 때 | `appointmentRequests: managerUserId + status + appointmentAtEpochMillis desc` |
| 3 | 매니저 활성 세션 조회 | 매니저별 세션 누적 후 클라이언트 필터가 느려질 때 | `companionSessions: managerUserId + currentStatus` |
| 4 | 환자/보호자 문의 이력 | 문의가 누적되어 최근순 서버 정렬이 필요할 때 | `clientSupportRequests: userId + createdAt desc` |
| 5 | 관리자 문의 큐 | 답변 대기/완료 상태와 생성일 정렬을 서버에서 처리할 때 | `clientSupportRequests: status + createdAt desc` |
| 6 | 매니저 문의 이력 | 매니저 문의 최근순 서버 정렬이 필요할 때 | `supportInquiries: managerUserId + createdAt desc` |
| 7 | 관리자 액션 센터 | 상태, 우선순위, 생성일 기준 서버 정렬이 필요할 때 | `adminActionNotifications: state + priority + createdAt desc` |
| 8 | 발송 작업 큐 | 실패 재시도 시각 순서로 안정 처리할 때 | `adminActionDeliveryJobs: state + nextAttemptAt asc` |

## 운영 기준

- `firestore.indexes.json`은 Firebase SDK가 반환한 인덱스 생성 링크 또는 실제 서버 필터/정렬 구현이 있을 때만 추가한다.
- 관리자 화면은 운영 데이터가 늘면 전체 조회를 유지하지 않고 화면별 컬렉션, 상태, 날짜 범위 기준으로 서버 필터와 페이지네이션을 먼저 설계한다.
- Functions 스케줄러는 배치 크기만으로 안정성이 부족해지는 시점에 `state`, `nextAttemptAt`, `createdAt` 기준 정렬을 추가하고 인덱스를 같이 반영한다.
- 운영 도구의 전체 조회는 백업/점검 성격이므로 사용자 화면 성능 판단과 분리한다.

## 검증

- `firestore.indexes.json` 복합 인덱스 없음 확인.
- Android 앱, 관리자 웹, Functions, 운영 도구의 Firestore 조회 경로를 정적 검색으로 확인.
- 문서 링크와 경로 정합성을 확인한다.
- 문서 전용 변경이므로 Android 빌드, 관리자 웹 빌드, Functions 배포 검증은 실행하지 않는다.

## 남은 범위

- 실제 운영 데이터 규모에서 관리자 대시보드 read 수와 응답 시간을 측정해야 한다.
- 서버 페이지네이션을 추가하는 후속 이슈가 생기면 후보 인덱스를 실제 `firestore.indexes.json`에 반영한다.
