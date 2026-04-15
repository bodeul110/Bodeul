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
- 신청 단계에서 환자-보호자 연결 정보 입력
- 기존 계정이 있으면 이메일 또는 전화번호 기준 자동 연결
- 계정이 없어도 신청 시점 이름 / 전화번호 / 이메일 스냅샷 저장
- 보호자 진행 현황 조회
- 최종 진료 리포트 조회

### 매니저

- 매니저 홈
- 병원 동행 가이드 진행
- 보호자 공유 메시지 저장
- 복약 메모 저장
- 진료 리포트 저장

### 관리자

- 미배정 요청 조회
- 수동 매칭
- 병원 가이드 등록
- 운영 중 요청 조회

### 알림 / 서버

- 예약 시 `appointmentAtEpochMillis`, `appointmentDateKey`, `reminderStages` 저장
- 매일 오전 9시 기준 `D7`, `D3`, `D1` 알림 작업 생성
- 알림 작업 큐 처리 및 시뮬레이션 / 실발송 상태 기록
- 사용자 문서 생성 / 수정 시 기존 신청 문서 자동 재연결

## 2. 이번 작업에서 구현한 내용

- `Booking` 화면의 예약 시간 입력을 직접 타이핑에서 `날짜 선택기 + 시간 선택기` 흐름으로 교체했다.
- 선택한 날짜와 시간은 기존 저장 포맷인 `yyyy-MM-dd HH:mm`으로 그대로 저장되도록 유지했다.
- `users` 문서가 생성되거나 연락처가 바뀌면 `appointmentRequests`의 미연결 환자 / 보호자 ID를 자동으로 다시 연결하는 Firebase Functions 트리거를 추가했다.
- 자동 재연결 시 요청 문서의 이름 / 연락처 / 이메일 스냅샷도 현재 사용자 정보 기준으로 같이 갱신되도록 정리했다.

## 3. 변경된 범위

- `app/src/main/java/com/example/bodeul/ui/booking/BookingActivity.java`
- `app/src/main/res/layout/activity_booking.xml`
- `app/src/main/res/values/strings.xml`
- `functions/index.js`
- `docs/data-api-draft.md`
- `docs/implementation-status.md`

## 4. 남은 범위

### 기능

- 예약 변경 / 취소 직후 기존 `appointmentReminderJobs`를 즉시 `SKIPPED` 처리하는 정리 함수가 아직 없다.
- 관리자 가이드 수정 / 삭제와 운영 이력 UI는 아직 최소 범위까지만 구현됐다.
- 매니저 홈의 `서류 등록`, `스케줄 등록`은 아직 플레이스홀더다.

### UI

- 권한 안내, 에러, 빈 상태 전용 화면 보강
- 실제 소셜 아이콘 리소스 교체
- 날짜 / 시간 선택기 주변 빠른 선택 UX 보강 여부 검토

## 5. 다음 권장 순서

1. 예약 변경 / 취소 시 알림 작업 정리
2. 권한 / 에러 / 빈 상태 전용 화면 보강
3. 관리자 세부 운영 기능 확장
4. 매니저 홈 플레이스홀더 기능 실제 연결

## 6. 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `node --check functions/index.js`
