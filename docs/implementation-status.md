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
- 운영 중 요청 조회
- 권한 없음 / 로그인 필요 / 불러오기 실패 상태 패널 표시

### 알림 / 서버

- 예약 시 `appointmentAtEpochMillis`, `appointmentDateKey`, `reminderStages` 저장
- 매일 오전 9시 기준 `D7`, `D3`, `D1` 알림 작업 생성
- 알림 작업 큐 처리 및 시뮬레이션 / 실발송 상태 기록
- 사용자 문서 생성 / 수정 시 기존 신청 문서 자동 재연결
- 예약 취소 / 삭제 / 일정 변경 시 남아 있는 `appointmentReminderJobs` 자동 정리

## 2. 이번 작업에서 구현한 내용

- 관리자 화면에서 등록된 병원 가이드를 선택해 단계 내용을 수정하는 흐름을 추가했다.
- 수정 중에는 병원 / 진료과를 고정하고 단계만 바꾸도록 폼 상태를 분리했다.
- 등록된 병원 가이드 삭제와 삭제 확인 다이얼로그를 추가했다.
- 목업 저장소에 병원 가이드 삭제 기능과 단위 테스트를 추가했다.

## 3. 변경된 범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/data/AdminRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockAdminRepository.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/res/layout/activity_admin.xml`
- `app/src/main/res/layout/item_admin_guide.xml`
- `app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java`
- `app/src/main/res/values/strings.xml`
- `docs/data-api-draft.md`
- `docs/implementation-status.md`

## 4. 남은 범위

### 기능

- 운영 이력 전용 화면과 필터링은 아직 없다.
- 매니저 홈의 `서류 등록`, `스케줄 등록`은 아직 플레이스홀더다.
- `IN_PROGRESS` 이후 요청 변경 / 취소 정책은 아직 앱에서 막기만 하고 별도 운영 대응 화면은 없다.

### UI

- 실제 소셜 아이콘 리소스 교체
- 날짜 / 시간 선택기 주변 빠른 선택 UX 보강 여부 검토

## 5. 다음 권장 순서

1. 관리자 운영 이력 / 필터 확장
2. 매니저 홈 플레이스홀더 기능 실제 연결
3. 날짜 / 시간 선택기 주변 빠른 선택 UX 검토

## 6. 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
