# 구현 상태

기준: 2026-04-15

## 1. 현재 동작하는 기능

### 인증

- 이메일 로그인 / 회원가입 / 비밀번호 재설정
- Google, Kakao, Naver 로그인
- 이메일 인증, 프로필 보완
- Firebase 미설정 시 목업 모드 자동 전환

### 환자 / 보호자

- 병원 동행 신청 생성
- 내 신청 목록 조회
- `REQUESTED` 상태 요청 수정 / 취소
- `MATCHED` 상태 요청 취소
- 권한 없음 / 로그인 필요 / 불러오기 실패 상태 패널 표시
- 신청 단계에서 환자-보호자 연결 정보 입력
- 기존 계정이 있으면 이메일 또는 전화번호 기준 자동 연결
- 계정이 없어도 신청 시점 이름 / 전화번호 / 이메일 스냅샷 저장
- 보호자 진행 현황 조회
- 최종 진료 리포트 조회

### 매니저

- 매니저 홈
- 서류 등록 요약 저장
- 활동 가능 일정 저장
- 매니저 홈 권한 / 로그인 / 불러오기 실패 상태 패널 표시
- 병원 동행 가이드 진행
- 동행 가이드 권한 / 로그인 / 불러오기 실패 상태 패널 표시
- 보호자 공유 메시지 저장
- 복약 메모 저장
- 진료 리포트 저장

### 관리자

- 미배정 요청 조회
- 수동 매칭
- 병원 가이드 등록
- 병원 가이드 수정 / 삭제
- 운영 이력 상태별 필터
- 운영 이력 날짜별 필터
- 운영 요청 상세 정보 펼침
- 운영 중 요청 조회
- 권한 없음 / 로그인 필요 / 불러오기 실패 상태 패널 표시

### 알림 / 서버

- 예약 시 `appointmentAtEpochMillis`, `appointmentDateKey`, `reminderStages` 저장
- 매일 오전 9시 기준 `D7`, `D3`, `D1` 알림 작업 생성
- 알림 작업 큐 처리 및 시뮬레이션 / 실발송 상태 기록
- 사용자 문서 생성 / 수정 시 기존 신청 문서 자동 재연결
- 예약 취소 / 삭제 / 일정 변경 시 남아 있는 `appointmentReminderJobs` 자동 정리

## 2. 이번 작업에서 구현한 내용

- 관리자 운영 이력에 `오늘`, `다가오는 일정`, `지난 일정` 날짜 필터를 추가했다.
- 운영 카드마다 상세 정보 펼침 영역을 추가해 요청 ID, 세션 상태, 계정 연결 상태, 보호자 공유 메시지, 복약 메모를 바로 확인할 수 있게 했다.
- 상태 필터와 날짜 필터를 함께 적용해 현재 표시 건수와 집계를 동시에 보도록 정리했다.
- 관련 문자열과 현재 작업 상태 문서를 업데이트했다.

## 3. 변경된 범위

- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/res/layout/activity_admin.xml`
- `app/src/main/res/layout/item_admin_request.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

## 4. 남은 범위

### 기능

- 운영 이력 전용 별도 상세 화면은 아직 없다.
- 매니저 서류 파일 업로드, 증빙 이미지 첨부, 관리자 승인 상태는 아직 없다.
- `IN_PROGRESS` 이후 요청 변경 / 취소 정책은 아직 앱에서 막기만 하고 별도 운영 대응 화면은 없다.

### UI

- 실제 소셜 아이콘 리소스 교체
- 날짜 / 시간 선택기 주변 빠른 선택 UX 보강 여부 검토

## 5. 다음 권장 순서

1. 매니저 서류 파일 업로드 / 승인 상태 확장
2. 관리자 운영 이력 전용 상세 화면 검토
3. 날짜 / 시간 선택기 주변 빠른 선택 UX 검토

## 6. 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`

## 7. 2026-04-15 추가 업데이트

### 구현

- 매니저 홈에 `서류 검토 상태`, `관리자 메모` 표시를 추가했다.
- 매니저가 서류 요약을 다시 저장하면 상태가 `PENDING_REVIEW`로 바뀌고 기존 관리자 메모는 초기화된다.
- 관리자 화면에 `매니저 서류 검토` 섹션을 추가했다.
- 관리자 화면에서 매니저별 `서류 요약`, `가능 일정`, `검토 메모`를 보고 `승인`, `보완 요청`을 저장할 수 있다.
- 목업 저장소와 Firebase 저장소 모두 같은 상태 모델을 사용하도록 맞췄다.

### 변경 범위

- `domain/model`: `ManagerDocumentStatus`, `ManagerDocumentOverview`, `ManagerHomeProfile`, `AdminDashboard`
- `data`: `AdminRepository`, `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`, `ManagerActivity`
- `layout`: `activity_admin.xml`, `activity_manager_home.xml`, `item_admin_manager_document.xml`, `dialog_admin_document_review.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`와 `증빙 이미지 미리보기`는 아직 없다.
- 관리자 검토 이력의 `타임라인`과 `담당자 로그`는 아직 없다.
- 날짜/시간 선택기의 `빠른 선택 UX`는 아직 남아 있다.
