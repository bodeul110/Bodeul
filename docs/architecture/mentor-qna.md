# 멘토 Q&A 준비

기준일: 2026-06-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## Firebase/Firestore

### 왜 Firebase를 선택했나?

현재 MVP에서는 Android 앱, 관리자 웹, 인증, 데이터, 파일, 알림을 빠르게 연결하는 것이 중요했다. Firebase는 Auth, Firestore, Storage, Functions, FCM을 같은 프로젝트에서 제공하므로 현재 팀 규모에서는 별도 서버를 운영하는 것보다 효율적이다.

### 왜 Firestore를 선택했나?

예약 요청, 동행 세션, 리포트, 문의, 관리자 심사 상태가 문서 단위로 읽고 갱신되는 흐름이라 Firestore와 잘 맞는다. 실시간 위치, 채팅, 상태 변경도 Firestore listener와 FCM 트리거로 연결하기 쉽다.

### 왜 MySQL/PostgreSQL이 아닌가?

관계형 DB는 정산, 통계, 복잡한 조인에 강하지만 API 서버, 인증/권한 계층, 배포/장애 대응을 함께 운영해야 한다. 현재 MVP 규모에서는 그 운영 비용이 기능 검증 속도를 떨어뜨린다고 판단했다.

### 나중에 어떻게 바꿀 수 있나?

정산/통계/검색이 커지면 BigQuery export, PostgreSQL 보조 저장소, 별도 API 서버를 검토한다. Firestore 단독 구조를 버리는 기준은 비용, 조인 요구, 서버 검증 요구가 실제 지표로 확인될 때다.

### 멘토 피드백 이후에는 어떻게 전환하나?

운영 source of truth를 Supabase PostgreSQL과 Oracle Cloud API 서버로 옮기는 방향으로 결정했다. 다만 Firebase Auth, FCM, Storage, Hosting을 한 번에 제거하지는 않는다. 먼저 API 서버가 Firebase ID token을 검증하고 PostgreSQL의 역할/운영 데이터를 기준으로 응답하도록 만든 뒤, 관리자 웹과 Android 앱의 Firestore 직접 접근을 화면 단위로 API 호출로 바꾼다.

### 왜 Supabase를 1순위로 잡았나?

PostgreSQL을 쓰면서도 Realtime 기능을 검토할 수 있기 때문이다. Neon도 PostgreSQL 대안으로 좋지만, 실시간 구독은 별도 WebSocket/SSE 서버 구현 부담이 더 크다. 현재 멘토님이 지적한 “RDBMS 전환”과 “실시간 가능성”을 같이 설명하기에는 Supabase PostgreSQL이 더 맞다.

### 왜 Oracle Cloud에는 DB가 아니라 API 서버만 올리나?

Oracle VM에 PostgreSQL까지 직접 운영하면 백업, 보안 패치, 장애 대응을 모두 팀이 책임져야 한다. 현재 팀 규모에서는 DB는 관리형 Supabase를 쓰고, Oracle VM은 API 서버 운영 경험과 배포 구조를 만들기 위해 쓰는 편이 현실적이다.

## 앱 구조

### Activity는 무엇을 담당하나?

Activity는 생명주기, 권한 요청, 화면 이동, 저장소 호출 연결만 담당한다. 화면에 보여줄 데이터 조합과 문자열 정책은 Coordinator와 Formatter로 분리한다.

### Coordinator는 왜 있는가?

Repository에서 받은 도메인 데이터를 화면 모델로 바꾸기 위해 있다. Activity 안에 상태 분기와 카드 조합이 쌓이는 문제를 줄인다.

### Binder는 왜 있는가?

XML View에 ScreenModel을 반복적으로 연결하는 코드를 Activity에서 분리하기 위해 있다. 카드 목록, 상태 배지, 버튼 표시 같은 렌더링 규칙을 모은다.

### Repository는 왜 있는가?

Firebase 구현과 Mock 구현을 같은 화면 코드에서 교체하기 위해 있다. 데이터 접근 계약을 숨기면 화면 코드는 Firebase 여부를 몰라도 된다.

### Mock 모드는 왜 있는가?

Firebase 설정이 없거나 네트워크가 불안한 환경에서도 데모와 테스트를 할 수 있게 하기 위해 있다. CI/Dependabot 환경에서도 `google-services.json` 없이 컴파일이 깨지지 않는 장점이 있다.

## 관리자 웹

### 관리자 웹은 왜 필요한가?

관리자 웹은 서비스 신뢰성을 위한 운영 도구다. 매니저 서류 심사, 신고/문의 처리, 운영 상태 확인, 민감정보 마스킹, 관리자 세션 관리를 앱 사용자 흐름과 분리한다.

### 왜 Firebase Hosting인가?

현재 관리자 웹은 Vite 정적 SPA라 Firebase Hosting이 가장 단순하다. preview channel로 검증하고 live 채널로 배포하는 흐름도 확인했다.

## 보안/운영

### 관리자는 어떻게 구분하나?

현재는 Firebase Auth 로그인 후 `users/{uid}.role == ADMIN`인지를 관리자 웹과 Rules에서 확인한다. custom claims는 아직 사용하지 않는다.

### App Check는 왜 아직 강제하지 않았나?

초기에는 시연, preview, 디버그 환경이 자주 바뀌어서 enforcement를 바로 켜면 정상 검증이 막힐 수 있다. App Check는 Auth와 Rules를 대체하는 기능이 아니라 정상 앱/기기에서 온 요청인지 확인하는 추가 방어선이다. 현재는 Android debug/release provider, 관리자 웹 site key 경로, Functions 전환 스위치까지 준비했고, debug token과 웹 site key, 릴리스 provider 검증이 끝난 뒤 Functions, Storage, Firestore 순서로 단계적으로 강제한다. 세부 기준은 [App Check 적용 로드맵](../operations/app-check-enforcement-roadmap.md)에 정리했다.

### 백업/복원은 실제로 테스트했나?

백업/복원 도구 경로는 있으나, 복원 리허설 증적은 추가로 필요하다. 다음 운영 과제로 dev 데이터 일부를 대상으로 복원 가능성을 기록해야 한다.

### API Key는 어디에 두나?

Firebase Web API Key는 클라이언트 설정에 포함될 수 있지만 Rules/App Check/Auth 설정과 함께 보호한다. 서버 전용 비밀값과 Kakao/푸시 대행사 민감 키는 Git에 넣지 않고 Functions 환경 변수로 관리한다.
