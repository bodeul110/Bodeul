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
- 접수 대기 상태 요청 수정 / 취소
- 권한 없음 / 로그인 필요 / 불러오기 실패 상태 패널 표시
- 신청 단계에서 환자-보호자 연결 정보 입력
- 기존 계정이 있으면 이메일 또는 전화번호 기준 자동 연결
- 계정이 없어도 신청 시점 이름 / 전화번호 / 이메일 스냅샷 저장
- 보호자 진행 현황 조회
- 최종 진료 리포트 조회

### 매니저

- 매니저 홈
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
- 운영 중 요청 조회
- 권한 없음 / 로그인 필요 / 불러오기 실패 상태 패널 표시

### 알림 / 서버

- 예약 시 `appointmentAtEpochMillis`, `appointmentDateKey`, `reminderStages` 저장
- 매일 오전 9시 기준 `D7`, `D3`, `D1` 알림 작업 생성
- 알림 작업 큐 처리 및 시뮬레이션 / 실발송 상태 기록
- 사용자 문서 생성 / 수정 시 기존 신청 문서 자동 재연결
- 예약 취소 / 삭제 / 일정 변경 시 남아 있는 `appointmentReminderJobs` 자동 정리

## 2. 이번 작업에서 구현한 내용

- `include_state_panel.xml`과 `StatePanelHelper`를 추가해 공통 상태 패널을 만들었다.
- `Booking`, `GuardianReport`, `Admin`, `ManagerHome`, `ManagerGuide`에서 권한 없음 / 로그인 필요 / 불러오기 실패 상태를 `Toast + 종료` 대신 화면 안 패널과 버튼 액션으로 처리하도록 바꿨다.
- `Booking`, `GuardianReport`, `Admin`, `ManagerGuide`의 빈 상태도 같은 상태 패널을 재사용하도록 정리했다.
- 차단 상태가 보일 때는 각 화면의 주요 콘텐츠를 숨기고, `메인으로 이동`, `로그인 화면으로`, `다시 시도` 같은 후속 액션을 바로 제공하도록 맞췄다.

## 3. 변경된 범위

- `app/src/main/java/com/example/bodeul/ui/booking/BookingActivity.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportActivity.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/util/StatePanelHelper.java`
- `app/src/main/res/layout/activity_booking.xml`
- `app/src/main/res/layout/activity_guardian_report.xml`
- `app/src/main/res/layout/activity_admin.xml`
- `app/src/main/res/layout/activity_manager_home.xml`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/layout/include_state_panel.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `docs/implementation-status.md`

## 4. 남은 범위

### 기능

- `MATCHED` 이후 요청을 어떻게 변경 / 취소할지에 대한 운영 정책과 UI는 아직 없다.
- 관리자 가이드 수정 / 삭제와 운영 이력 UI는 아직 최소 범위까지만 구현됐다.
- 매니저 홈의 `서류 등록`, `스케줄 등록`은 아직 플레이스홀더다.

### UI

- 실제 소셜 아이콘 리소스 교체
- 날짜 / 시간 선택기 주변 빠른 선택 UX 보강 여부 검토

## 5. 다음 권장 순서

1. `MATCHED` 이후 예약 변경 / 취소 정책과 UI 정리
2. 관리자 세부 운영 기능 확장
3. 매니저 홈 플레이스홀더 기능 실제 연결

## 6. 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `node --check functions/index.js`
