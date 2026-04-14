# 구현 상태

기준일: 2026-04-14

이 문서는 현재 프로젝트에서 실제로 동작하는 범위와 최근 작업, 남은 범위를 빠르게 확인하기 위한 기준 문서다.

## 1. 현재 동작하는 기능

### 인증
- 스플래시에서 로그인 상태를 확인하고 다음 화면으로 분기한다.
- 역할 선택 화면을 통해 매니저와 일반 사용자 흐름을 나눈다.
- 이메일 로그인, 회원가입, 비밀번호 재설정 메일 전송이 동작한다.
- Firebase 모드에서는 이메일 인증 재전송과 소셜 로그인 후 사용자 문서 보완까지 연결된다.
- Google, Kakao, Naver 로그인과 Firebase custom token 연동이 구현돼 있다.
- 이름 또는 연락처가 비어 있으면 프로필 보완 화면으로 먼저 이동한다.
- Firebase 설정이 없을 때는 목업 데이터 기반 모드로 자동 전환된다.

### 매니저
- 매니저 홈에서 현재 동행 세션 요약과 기본 액션을 확인할 수 있다.
- 활성 세션이 있으면 병원 동행 가이드 화면으로 진입할 수 있다.
- 동행 가이드에서 단계 진행, 보호자 공유 메시지 저장, 복약 메모 저장, 진료 리포트 저장이 가능하다.
- 활성 세션이 없을 때도 빈 상태 화면으로 안전하게 동작한다.

### 환자 및 보호자
- 병원 동행 신청 화면에서 병원명, 진료과, 예약 시간, 만남 장소, 특이사항을 입력해 요청을 생성할 수 있다.
- 같은 화면에서 현재 계정 기준 요청 목록과 최근 요청 상태를 조회할 수 있다.
- 보호자 리포트 화면에서 진행 상태, 보호자 공유 메시지, 최종 진료 리포트를 한 번에 확인할 수 있다.

### 관리자
- 관리자 화면에서 미배정 요청 목록을 조회할 수 있다.
- 환자와 보호자 연결 정보, 병원 가이드 존재 여부, 매니저 가능 여부를 확인한 뒤 수동 매칭할 수 있다.
- 병원별 동행 가이드를 등록하고 저장된 가이드를 목록으로 확인할 수 있다.

### 공통
- Firebase 저장소와 목업 저장소를 같은 흐름으로 사용할 수 있다.
- 역할별 진입 분기와 프로필 보완 분기가 인증 직후 자동으로 적용된다.
- `assembleDebug`, `testDebugUnitTest` 기준으로 기본 동작을 검증해둔 상태다.

## 2. UI 1차 정렬 현황

### 피그마 기준으로 1차 정렬한 화면
- 스플래시
- 역할 선택
- 로그인
- 매니저 홈
- 매니저 동행 가이드

### 이번 UI 패스에서 반영한 방향
- 공통 색상, 배경 글로우, 버튼, 카드 스타일을 피그마 톤에 맞춰 재정의했다.
- 앱 로고 에셋을 실제 리소스로 추가해 인증과 매니저 화면 전반에 공통 적용했다.
- 매니저 홈은 피그마 구조를 참고하되 현재 기능과 맞도록 `현장 가이드`, `로그아웃` 등 실제 동작과 일치하는 카드로 재구성했다.
- 매니저 가이드는 피그마의 단계형 흐름을 유지하되 현재 데이터에 없는 이미지 영역은 정보 카드와 기록 카드로 치환했다.

### 아직 피그마 기준 정렬이 덜 된 화면
- 병원 동행 신청
- 보호자 리포트
- 관리자 화면
- 권한 안내 전용 화면

## 3. 최근 작업 정리

### 기능 구현
- `BookingActivity`를 실제 병원 동행 신청 화면으로 전환했다.
- `GuardianReportActivity`를 실제 조회 화면으로 전환해 `appointmentRequests`, `companionSessions.guardianUpdate`, `sessionReports`를 함께 읽도록 구성했다.
- `AdminActivity`를 운영 화면으로 전환해 미배정 요청 조회, 수동 매칭, 병원 가이드 저장을 지원하도록 구성했다.
- 목업 저장소와 Firebase 저장소에 신청, 보호자 리포트, 관리자 운영용 저장소 계층을 추가했다.

### UI 정렬
- 공통 색상과 카드 배경, 하단 내비게이션, 서비스 썸네일용 드로어블을 추가했다.
- 스플래시와 역할 선택 화면을 피그마 기준 구조로 재구성했다.
- 로그인 화면을 피그마 흐름에 맞춰 입력부, 간편 로그인, 하단 액션 순서로 재정렬했다.
- 매니저 홈을 히어로 카드, 2x2 액션 카드, 배정 정보, 서비스 소개 카드 구조로 재구성했다.
- 매니저 가이드를 정보 카드, 단계 카드, 현장 기록 카드 구조로 재구성했다.

## 4. 이번 작업의 변경 범위

- `app/src/main/res/drawable-nodpi/bodeul_logo_icon.png`
- `app/src/main/res/drawable/bg_bottom_nav.xml`
- `app/src/main/res/drawable/bg_nav_active.xml`
- `app/src/main/res/drawable/bg_primary_gradient.xml`
- `app/src/main/res/drawable/bg_primary_hero.xml`
- `app/src/main/res/drawable/bg_screen_glow.xml`
- `app/src/main/res/drawable/bg_service_thumb_cool.xml`
- `app/src/main/res/drawable/bg_service_thumb_warm.xml`
- `app/src/main/res/drawable/bg_surface_card_soft.xml`
- `app/src/main/res/drawable/bg_surface_pill.xml`
- `app/src/main/res/layout/activity_login.xml`
- `app/src/main/res/layout/activity_manager_home.xml`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/layout/activity_role_selection.xml`
- `app/src/main/res/layout/activity_splash.xml`
- `app/src/main/res/layout/item_manager_step.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `docs/implementation-status.md`

## 5. 남은 범위

### UI
- `Booking`, `GuardianReport`, `Admin` 화면을 현재 공통 스타일 토큰 기준으로 2차 정렬해야 한다.
- 디자인 export에 없는 권한 안내, 에러, 빈 상태 세부 화면을 보완해야 한다.
- 소셜 로그인 버튼은 현재 문자 기반이므로 실제 아이콘 리소스가 준비되면 교체하는 편이 좋다.

### 기능
- 신청 단계에서 환자와 보호자 연결 정보를 더 자연스럽게 받는 입력 구조가 필요하다.
- 관리자 화면의 가이드 수정, 삭제, 운영 이력 확인 UI가 아직 없다.
- 매니저 홈의 `서류 등록`, `스케줄 등록`은 아직 플레이스홀더 동작이다.
- 매니저 흐름은 현재 `매니저당 활성 세션 1건` 전제로 구현돼 있다.

### 검증
- 새 기능이나 UI 작업 후에는 계속 `assembleDebug`를 기준 검증으로 유지한다.
- 실제 배포 전에는 Kakao, Naver, Google 콘솔 설정과 Firebase Functions 연동 값을 최신 기준으로 다시 확인해야 한다.

## 6. 다음 권장 순서

1. `Booking` 화면을 현재 공통 스타일에 맞춰 재구성한다.
2. `GuardianReport` 화면을 같은 토큰으로 정렬해 보호자 축 UI를 닫는다.
3. `Admin` 화면을 운영 도구 톤으로 정리한다.
4. 이후 실제 아이콘, 상태별 세부 화면, 권한 안내 화면을 보완한다.
