# Figma MVP 화면 인벤토리와 Android 구현 매핑

기준일: 2026-08-29

## 목적

새 Figma MVP 파일의 화면을 현재 Android 구현과 연결하고, 열려 있는 PR과 충돌하지 않는 구현 순서를 정한다. 이 문서는 화면 존재 여부, 구현 위치와 의존성을 정리하는 기준이며 픽셀 단위 디자인 명세는 각 화면 구현 직전에 좁은 node를 다시 조회해 확정한다.

## 기준

- Figma: [보들 MVP](https://www.figma.com/design/NX07k3Tu4cLc6YgAV82RXp/%EB%B3%B4%EB%93%A4-MVP?node-id=0-1&p=f&m=dev)
- file key: `NX07k3Tu4cLc6YgAV82RXp`
- 페이지: `Page 1`, node `0:1`
- 확인 결과: Dev Mode 계정으로 파일 메타데이터와 node 조회 가능
- 화면 기준 폭: 최상위 모바일 frame 41개가 모두 390px
- 별도 확인 node: 권한 설정 frame 안에 중첩된 로그인 화면 `8:84`
- Android 기준: Java + XML, 치수는 `dp/sp`
- 코드 기준: `origin/master`의 `32b04f2`

기존 [Figma 현행 화면 지도](figma-current-screen-map.md)는 이전 `보들 가이드` 파일을 정리한 기록이다. 새 MVP 구현에서는 이 문서를 우선하고, 이전 문서는 화면 의도와 과거 차이를 확인하는 보조 자료로 사용한다.

## 상태 정의

| 상태 | 의미 | 구현 처리 |
| --- | --- | --- |
| 직접 대응 | 현재 Activity와 XML이 같은 화면 책임을 가진다. | 기능과 ID를 유지하고 시각 구조를 맞춘다. |
| 상태 대응 | 별도 Activity보다 기존 화면의 서버 상태로 표현하는 편이 맞다. | 화면을 추가하지 않고 같은 Activity의 상태 UI로 만든다. |
| 부분 대응 | 핵심 기능은 있으나 Figma의 입력, 상태 또는 시각 구성이 일부 없다. | 구현된 계약까지만 노출하고 나머지는 숨긴다. |
| 신규 필요 | 현재 Android에 대응 화면이나 상태가 없다. | 정책과 API가 확보된 뒤 추가한다. |
| 계약 대기 | 화면은 있으나 서버 API 또는 제품 규칙이 없다. | 운영 진입점과 작동하지 않는 CTA를 노출하지 않는다. |
| 디자인 확인 | frame 이름만으로 상호작용을 확정하기 어렵다. | 해당 node의 디자인 컨텍스트와 prototype을 다시 확인한다. |

## 전체 frame 인벤토리

### 진입과 인증

| Figma | 크기 | 현재 Android | 상태 | 구현 판단 |
| --- | ---: | --- | --- | --- |
| 첫 화면 `8:179` | 390×998 | 전용 Activity 없음, `RoleSelectionActivity`가 launcher | 신규 필요 또는 생략 결정 | 단순 브랜드 스플래시인지 행동 가능한 랜딩인지 확인한다. 전용 화면이 필요해도 #381의 Manifest 변경 반영 뒤 추가한다. |
| 권한 설정 `8:2` | 390×1364 | `PermissionGuideActivity`, `activity_permission_guide.xml` | 직접 대응 | Android 최소 권한 원칙을 유지하며 Figma의 위계만 반영한다. |
| 로그인 화면 `8:84` | 중첩 node | `LoginActivity`, `activity_login.xml` | 직접 대응 | 실제 활성 제공자인 이메일·Google·Kakao만 유지한다. |
| 사용자 설정 `8:135` | 390×1258.25 | `RoleSelectionActivity`, `activity_role_selection.xml` | 직접 대응 | 환자/보호자와 매니저 역할 선택을 시각적으로 맞춘다. |

현재 앱의 첫 실행 흐름은 `EntryFlowCoordinator`가 `권한 안내 → 역할 선택 → 로그인`을 조정한다. Figma의 배치 순서만 보고 인증·권한 요청 순서를 바꾸지 않고, 실제 prototype과 Android 정책을 함께 확인한다.

### 환자·보호자 예약

| Figma | 크기 | 현재 Android | 상태 | 구현 판단 |
| --- | ---: | --- | --- | --- |
| 환자 홈 화면 `8:194` | 390×1458 | `MainActivity`, `activity_main.xml` | 직접 대응 | 환자/보호자 서버 상태에 따라 hero와 진행 카드를 유지하면서 시각을 맞춘다. |
| 환자 예약 `8:305` | 390×1657 | `BookingActivity`, `activity_booking.xml` | 직접 대응 | 현재 한 화면에 모인 입력을 Figma의 단계 위계에 맞춰 분리한다. |
| 병원 검색 화면 `8:398` | 390×1033 | `BookingHospitalSelectorActivity`, `activity_booking_hospital_selector.xml` | 직접 대응 | 실제 검색·선택 결과 연결을 유지한다. |
| 예약신청하기 `8:520` | 390×2065 | `BookingActivity`의 예약 폼 | 부분 대응 | 건강정보, 장소, 옵션, 결제 요약의 화면 책임을 Figma와 대조한다. |
| 예약 날짜 `8:632` | 390×884 | `BookingAppointmentSelector`가 `BookingActivity` 안에서 처리 | 상태 대응 | 별도 Activity보다 날짜 선택 상태/다이얼로그로 재사용한다. |
| 예약 (하) 선택 알림창 `8:775` | 390×891 | 정확한 대응 미확정 | 디자인 확인 | node 컨텍스트에서 열기 조건, 확인 결과와 취소 동작을 먼저 확인한다. |
| 예약 (하) 선택 알림창 `8:2691` | 390×891 | 정확한 대응 미확정 | 디자인 확인 | `8:775`와 중복/상태 변형인지 확인한 뒤 하나의 공용 다이얼로그로 설계한다. |
| 예약 신청완료(환자) `8:2751` | 390×958 | `BookingCompletionActivity`, `activity_booking_completion.xml` | 직접 대응 | 실제 저장 성공 응답 뒤에만 노출한다. |

현재 앱의 `HealthInfoActivity`, `BookingLocationSelectorActivity`, `BookingPaymentApprovalActivity`, `BookingStatusActivity`, `ClientBookingHistoryActivity`, `BookingFollowUpActivity`는 새 Figma의 최상위 frame에 직접 대응하지 않는다. 기능을 삭제하지 않고 예약 흐름의 하위 화면 또는 앱 고유 화면으로 유지한 뒤, 대응 node가 내부에 있는지 구현 시 다시 확인한다.

### 매니저 진입·매칭·홈

| Figma | 크기 | 현재 Android | 상태 | 구현 판단 |
| --- | ---: | --- | --- | --- |
| 자격인증 `8:3183` | 390×1052 | `ManagerDocumentRegistrationActivity`, `activity_manager_document_registration.xml` | 직접 대응 | 미제출·검토 중·보완 요청·승인 완료를 같은 화면의 상태로 표현한다. |
| 매니저 승인 요청 완료 `8:2590` | 390×884 | `ManagerDocumentRegistrationActivity` 제출 완료 상태 | 상태 대응 | 별도 Activity를 만들지 않고 서버 심사 상태로 렌더링한다. |
| 매니저 홈화면 `8:2479` | 390×1458 | `ManagerActivity`, `activity_manager_home.xml` | 직접 대응 | 활성 동행이 없는 상태의 기준 화면으로 사용한다. |
| 매니저 홈 화면 (예약 완료 후) `8:1624` | 390×1458 | `ManagerActivity` 활성 세션 상태 | 상태 대응 | 같은 홈에서 hero와 주요 CTA만 현재 동행 중심으로 바꾼다. |
| 동행 글 게시판 (매니저용) `8:3015` | 390×1078 | 화면·self-accept API 없음 | 계약 대기 | 요청 목록 필드와 경쟁 수락 규칙이 확정되기 전 운영 진입점을 숨긴다. |
| 요청 상세 확인 (매니저용) `8:2875` | 390×896 | 화면·API 없음 | 계약 대기 | 수락 전 개인정보 공개 범위와 상세 DTO가 필요하다. |
| 매칭 완료 안내 (매니저용) `8:2836` | 390×844 | 홈에서 배정 결과 표시 | 부분 대응 | 서버 배정 성공을 확인한 뒤 한 번만 보여줄 필요가 있는지 제품 결정을 받는다. |

`ManagerHistoryActivity`, `ManagerProfileActivity`, `ManagerSupportActivity`는 현재 앱에서 실제 기능을 담당하지만 이번 Figma의 최상위 frame에는 직접 대응 화면이 없다. Figma에 없다는 이유로 제거하지 않는다.

### 동행 가이드와 최종 리포트

13개 가이드 frame을 13개 Activity로 만들지 않는다. `ManagerGuideActivity`가 서버의 `currentStepCode`, `canAdvance`, `blockedReason`과 단계별 입력 계약에 따라 필요한 영역만 렌더링한다.

| 단계 | Figma | 현재 Android | 상태 | PR 의존성 |
| ---: | --- | --- | --- | --- |
| 1 | 동행 가이드 1 `8:3242` | `ManagerGuideActivity`의 첫 단계 | 부분 대응 | #382 반영 뒤 비교 |
| 2 | 동행가이드2 `8:2950` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 3 | 동행가이드3 `8:835` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 4 | 동행가이드4 `8:899` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 5 | 동행가이드5 `8:1023` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 6 | 동행가이드 6 `8:1735` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 7 | 동행가이드7 `8:1088` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 8 | 동행가이드 8 `8:1172` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 9 | 동행가이드 9 `8:1254` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 10 | 동행가이드 10 `8:1838` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 11 | 동행가이드 11 `8:1343` | 동적 단계 화면 | 부분 대응 | #382 반영 뒤 비교 |
| 12 | 동행가이드 12 `8:2614` | 동적 단계 화면 | 부분 대응 | #382의 정상 동행 종료 계약 필요 |
| 13 | 동행가이드 13 `8:1911` | 동적 단계와 리포트 제출 | 부분 대응 | #382의 최종 완료 계약 필요 |
| 결과 | 최종리포트 `8:1464` | `ManagerGuideActivity` 제출, `ManagerHistoryActivity`·`GuardianReportActivity` 조회 | 부분 대응 | #382 반영 뒤 작성/조회 책임 분리 |

MVP용 상봉 단계 변형은 다음처럼 별도로 존재한다.

| Figma | 의미 | 현재 판단 |
| --- | --- | --- |
| 동행 가이드 01: 상봉 번호 입력 (MVP) `8:3399` | 상봉번호 입력 | 현재 전용 서버 필드와 검증 계약을 확인해야 한다. |
| 동행가이드 1 틀렸을시 `8:3451` | 상봉번호 오류 | 서버 오류 코드와 재시도 규칙이 필요하다. |
| 동행 가이드 01: 상봉 완료 (MVP) `8:3344` | 검증 성공 상태 | 클라이언트가 번호 일치와 성공을 임의로 만들지 않는다. |

이 세 frame은 기존 1단계 `8:3242`의 최신 MVP 변형 후보로 본다. 구현 전 상봉번호 발급 주체, 입력 횟수 제한, 만료, 성공 시 단계 전환 API를 백엔드와 확정한다.

### 채팅형 가이드 시안

| Figma | 크기 | 현재 Android | 처리 |
| --- | ---: | --- | --- |
| 채팅형 가이드 01: 매칭 및 상봉 `8:2267` | 390×884 | `CompanionChatActivity` | 제품 표현 참고 |
| 채팅형 가이드 02: 매칭 및 상봉 `8:2340` | 390×884 | `CompanionChatActivity` | 제품 표현 참고 |
| 채팅형 가이드 04: 기초 측정 `8:2186` | 390×884 | `CompanionChatActivity` | 제품 표현 참고 |
| 채팅형 가이드 05: 문진 준비 `8:2406` | 390×884 | `CompanionChatActivity` | 제품 표현 참고 |
| 채팅형 가이드 07: 사후 가이드 `8:2083` | 390×898 | `CompanionChatActivity` | 제품 표현 참고 |
| 채팅형 가이드 09: 수납 및 결제 `8:2009` | 390×884 | `CompanionChatActivity` | 제품 표현 참고 |

페이지에는 `MVP단계에서는 제외` 텍스트 node `8:3504`가 있다. 현재 확보한 최상위 목록만으로 여섯 시안 전체가 해당 문구의 대상이라고 단정하지 않고, section 소속과 prototype을 확인할 때까지 운영 구현 대상에서 제외한다. 현재 직접 채팅 기능은 유지하며 서버가 보내지 않는 시스템 단계 메시지를 앱에서 가짜로 생성하지 않는다.

## Android 화면 책임 요약

| 사용자 흐름 | 화면 책임 |
| --- | --- |
| 진입 | `EntryFlowCoordinator`가 로그인 상태와 권한 안내 완료 여부로 다음 화면을 결정 |
| 역할 선택 | `RoleSelectionActivity` |
| 로그인 | `LoginActivity` |
| 프로필 보완 | `ProfileCompletionActivity` |
| 환자·보호자 홈 | `MainActivity` |
| 예약 작성 | `BookingActivity`와 병원·장소·결제 보조 Activity |
| 예약 완료·상태 | `BookingCompletionActivity`, `BookingStatusActivity` |
| 매니저 자격 | `ManagerDocumentRegistrationActivity` |
| 매니저 홈 | `ManagerActivity` |
| 동행 진행 | `ManagerGuideActivity` 한 화면의 단계별 상태 |
| 채팅 | `CompanionChatActivity` |
| 완료 조회 | `ManagerHistoryActivity`, `GuardianReportActivity` |

## 열린 PR과 충돌 회피

2026-08-29 확인 시 열린 PR 4개는 GitHub의 `mergeStateStatus=CLEAN`이다. 그러나 서로 같은 Android 파일을 수정하므로 Figma 화면 구현은 병합 뒤 최신 `master`에서 시작한다.

| 순서 | PR | Figma 구현과 겹치는 범위 | 처리 |
| ---: | --- | --- | --- |
| 1 | #381 성인 환자 보호자 동의와 인가 연결 | `BookingActivity`, 예약 상태 화면, Manifest, `strings.xml` | 환자 예약 화면 구현 전에 반영 |
| 2 | #382 정상 동행 종료와 최종 완료 흐름 분리 | `ManagerGuideActivity`, ViewModel, 가이드 입력, 리포트, 기존 디자인 문서 | 가이드 1~13 구현의 기준으로 먼저 반영 |
| 3 | #380 예약 공개 코드 발급과 역할별 조회를 연결 | `BookingStatusCoordinator`, `ManagerGuidePresentationFormatter`, `strings.xml` | #382 뒤 충돌 정리 후 반영 |
| 4 | #383 관리자 3역할 권한과 민감정보 감사 경계 추가 | `AdminActivity`, `strings.xml` | 앱 관리자 화면 변경 전 반영 |

#382는 #381 브랜치를 base로 사용하므로 #381 → #382 순서를 유지한다. #380은 #382와 `ManagerGuidePresentationFormatter`가 겹쳐 뒤에 반영하는 편이 안전하다. #383은 Figma MVP 화면과 직접 겹치지 않지만 공용 `strings.xml` 충돌을 줄이기 위해 같은 기준선에 합친 뒤 UI 작업을 시작한다.

이번 문서는 열린 PR이 수정하지 않는 새 파일에 만들었다. 기존 `figma-current-screen-map.md`와 `manager-screen-flow-map.md`는 #382가 수정 중이므로 이번 브랜치에서 변경하지 않는다.

## 구현 순서

### 0. 지금 진행할 준비 작업

1. 이 문서를 기준으로 node와 Android 화면 책임을 고정한다.
2. 각 구현 묶음 직전에 해당 node의 디자인 컨텍스트를 좁게 조회한다.
3. 반복 색상, 글꼴, 간격, 모서리, 그림자를 Android 리소스 후보로 추출한다.
4. 실제 기기 기준 폭과 시스템 영역을 포함한 비교 캡처 규칙을 정한다.

이 준비 단계는 열린 PR과 독립적으로 완료했다.

### 1. 진입·인증 화면

대상은 `8:179`, `8:2`, `8:84`, `8:135`다. 기존 Activity와 ID, 인증 흐름을 유지한 채 XML과 drawable/style을 맞춘다. 첫 화면은 행동 가능한 별도 화면인지 확인될 때까지 새 Activity를 만들지 않는다.

2026-08-29 기준으로 `8:2`, `8:84`, `8:135`의 디자인 컨텍스트를 조회하고 1차 반영을 완료했다.

- 공통 배경 `#F7F9FC`, 주요 색상 `#1958B7`, 48dp 카드·버튼 모서리와 Pretendard 계열 타이포그래피를 공용 리소스로 분리했다.
- Figma에서 내보낸 로고, 역할 아이콘, 선택 표시, 뒤로 가기와 소셜 로그인 에셋을 Android 리소스로 반영했다.
- 로그인·역할 선택의 기존 view ID와 이메일·Google·Kakao 인증 동작은 유지했다. 현재 비활성인 Naver 로그인은 Figma에 보여도 앱에서 노출하지 않는다.
- 권한 안내는 Figma의 카드 위계를 사용하되 앱이 실제로 쓰지 않는 카메라·저장소 권한을 추가하지 않았다. 알림, 시스템 문서 선택기, 기능 진입 시점의 위치 권한이라는 현재 Android 정책을 유지한다.
- `8:179`는 로고와 진행 표시가 있는 브랜드 시작 화면임을 확인했다. 전용 Activity와 Manifest 변경은 #381 반영 뒤 별도 묶음으로 구현한다.

1차 구현은 `assembleDebug`와 `testDebugUnitTest`를 통과했다. 실기기 ADB 연결이 없는 상태이므로 설치 후 화면 비교와 터치 흐름 검증은 남아 있다.

### 2. 환자·보호자 홈과 예약

#381과 #380이 병합된 최신 `master`에서 진행한다. `MainActivity` → `BookingActivity` → 병원/날짜/장소 선택 → 완료 → 상태 확인 순서로 한 묶음씩 구현하고, 각 묶음마다 실제 저장·복귀 흐름을 검증한다.

### 3. 매니저 자격과 홈

`8:3183`, `8:2590`, `8:2479`, `8:1624`를 같은 서버 상태 모델에 연결한다. 승인 전·후와 활성 동행 유무를 별도 Activity가 아니라 같은 화면의 상태로 만든다.

### 4. 동행 가이드

#382와 #380이 병합된 뒤 시작한다. 먼저 상봉 단계의 기존 frame과 MVP 변형의 우선순위를 확정하고, 이후 2~13단계를 `stepCode` 단위로 한 개씩 맞춘다. `canAdvance=false`와 `GUIDE_NOT_READY`에서는 작동하는 것처럼 보이는 CTA를 만들지 않는다.

### 5. 매칭 요청과 채팅형 시안

매니저 self-accept API, 개인정보 공개 범위, 경쟁 수락 규칙이 생기기 전에는 요청 목록·상세를 운영 화면에 추가하지 않는다. 채팅형 가이드는 MVP 포함 여부와 서버 시스템 이벤트 계약이 확정된 뒤 적용한다.

### 6. 시각 검증

각 화면은 다음 조건을 모두 통과해야 완료로 본다.

- Figma node와 동일한 화면 상태로 비교한다.
- 390px Figma 폭과 실제 Android 기기 폭의 배율 차이를 감안해 `dp/sp` 기준을 검증한다.
- 상태바·내비게이션 바가 포함된 실기기 캡처와 앱 콘텐츠 영역을 구분한다.
- 긴 화면은 스크롤 구간을 나눠 비교하고, 이어붙인 이미지는 검토 자료로만 사용한다.
- 로딩, 빈 상태, 오류, 권한 거부, 서버 차단 상태까지 확인한다.
- 화면의 CTA가 실제 저장/조회 결과와 연결되는지 확인한다.

## 팀에 먼저 확인할 계약

| 우선순위 | 대상 | 질문 |
| ---: | --- | --- |
| 1 | 제품·백엔드 | 상봉번호 발급·검증·만료·재시도와 단계 전환 API가 있는가? |
| 2 | 제품·백엔드 | 매니저 요청 목록/self-accept를 MVP에 포함하는가? 수락 전 공개 필드는 무엇인가? |
| 3 | 디자인·제품 | `8:775`와 `8:2691`은 같은 알림창의 상태 변형인가? 각각 언제 열린다? |
| 4 | 디자인·제품 | 채팅형 가이드 6개와 `MVP단계에서는 제외` 문구의 정확한 범위는 어디까지인가? |
| 5 | 제품 | 첫 화면 `8:179`은 필수 랜딩인가, 앱 시작용 시각 시안인가? |

## 설계 판단 기록

- 작업 목적: 새 Figma MVP 파일을 현재 Android 화면과 연결하고 열린 PR과 충돌하지 않는 구현 순서를 정한다.
- 선택한 방식: 41개 최상위 frame과 중첩 로그인 node를 화면군별로 매핑하고, 별도 화면보다 서버 상태로 표현할 대상을 구분했다.
- 대안: Figma frame마다 Activity를 새로 만들거나 이전 Figma 지도 문서를 바로 교체하는 방식은 현재 화면 책임과 열린 PR 변경을 중복시키므로 제외했다.
- 선택 이유: 현재 MVP 규모에서는 기존 Activity와 Coordinator/ViewModel의 기능 계약을 유지한 채 시각 계층을 교체하는 편이 회귀와 병합 충돌을 줄인다.
- 리스크: frame 이름만 확인한 두 예약 알림창, 상봉번호 변형과 채팅형 시안은 상호작용·제품 범위가 아직 확정되지 않아 디자인 컨텍스트와 팀 계약 확인이 필요하다.

## 남은 범위

- 진입·인증 화면의 실기기 스크린샷 비교와 접근성 검증
- 나머지 화면별 정확한 색상, 타이포그래피, 간격, 에셋과 component property 추출
- prototype 연결과 예약 알림창 두 node의 차이 확인
- 상봉번호 MVP 계약 확인
- 열린 PR 병합 뒤 화면별 구현 브랜치 생성
- #381 병합 뒤 브랜드 시작 화면과 Manifest 진입 경로 구현
