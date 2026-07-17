# 멘토 Q&A 준비

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## Firebase/Firestore

### 왜 Firebase를 선택했나?

초기 MVP에서는 Android 앱, 인증, 데이터, 파일과 알림을 빠르게 연결하는 것이 중요했다. 현재는 Firebase의 역할을 Auth, FCM, Storage, Functions와 아직 전환하지 않은 실시간 Firestore 경로로 제한했다. 관계형 운영 데이터는 PostgreSQL, 사용자 API는 Spring Core API, 관리자 서버는 Next.js가 담당한다.

### 왜 Firestore를 선택했나?

초기 예약 요청, 동행 세션, 리포트와 상태 변경은 문서 단위 갱신이 많았고 실시간 위치·채팅은 listener와 연결하기 쉬워 Firestore로 시작했다. 다만 관계형 조회와 운영 감사가 커질 경로는 PostgreSQL로 옮기고 있으며, Firestore를 계속 쓰는 도메인은 전환 완료 전까지 source of truth를 명시한다.

### 왜 MySQL/PostgreSQL이 아닌가?

초기에는 관계형 DB와 API 서버를 함께 운영하는 비용이 기능 검증 속도를 떨어뜨린다고 판단했다. 지금은 Firebase ID token 검증, 역할 인가, 관계형 조회와 감사 경계를 서버에 모을 필요가 커져 Supabase PostgreSQL과 별도 서버를 도입했다. 즉 처음 선택은 개발 속도 기준이었고 현재 전환은 운영 경계 기준이다.

### 나중에 어떻게 바꿀 수 있나?

정산/통계/검색이 커지면 Firestore 단독 구조에서 PostgreSQL 운영 DB로 전환한다. Firestore를 바로 제거하는 것이 아니라 먼저 Firestore 백업을 PostgreSQL로 import하고, row count와 주요 필드 비교가 통과한 도메인부터 source of truth를 옮긴다.

2026-07-17 기준 개발 Supabase에 사용자, 병원 가이드와 예약 요청 read model migration을 적용했다. 예약 요청은 Firestore 백업 4건의 row count, 사용자 FK, 중복과 권한을 검증했지만 Android 쓰기는 아직 Firestore를 사용한다.

### 멘토 피드백 이후에는 어떻게 전환하나?

역할을 고정한 혼용 구조로 전환한다. Firebase Auth, FCM, Storage와 일부 실시간 경로는 유지하고, 예약, 세션, 관리자 운영, 정산·통계처럼 관계형 조회가 커질 데이터는 Supabase PostgreSQL로 옮긴다. Spring Core API와 Next.js 관리자 서버는 PostgreSQL 접근, Firebase ID token 검증과 역할 인가를 담당한다.

### 왜 Supabase를 1순위로 잡았나?

PostgreSQL을 쓰면서도 Realtime 기능을 검토할 수 있기 때문이다. Neon도 PostgreSQL 대안으로 좋지만, 실시간 구독은 별도 WebSocket/SSE 서버 구현 부담이 더 크다. 현재 멘토님이 지적한 “RDBMS 전환”과 “실시간 가능성”을 같이 설명하기에는 Supabase PostgreSQL이 더 맞다.

### 왜 Firebase와 Supabase를 섞어서 쓰나?

지금 규모에서는 Firebase가 맡는 Auth, FCM과 Storage까지 한 번에 옮기는 비용이 DB 전환 이익보다 크다. 대신 관계형 조회, 정산, 통계와 운영 감사 데이터는 PostgreSQL로 옮긴다. 중요한 기준은 “두 플랫폼을 막 섞는다”가 아니라 “인증·푸시·파일은 Firebase, 운영 DB는 PostgreSQL”처럼 역할과 source of truth를 고정하는 것이다.

### Oracle Cloud 대신 무엇을 쓰나?

Oracle Free Tier 계정 잠금 이후 Spring Core API의 개발 실행 환경은 Google Cloud Run으로 변경했다. 현재 API는 상태를 로컬 디스크에 저장하지 않고 Supabase PostgreSQL을 사용하므로 24시간 VM보다 요청 기반 컨테이너가 현재 규모에 맞다. Cloud Run은 Java 21 컨테이너, 기본 HTTPS, revision rollback과 Firebase 서비스 계정 ADC를 제공한다.

개발 환경은 Tokyo의 `bodeul-core-api-preview`를 최소 인스턴스 0, 최대 인스턴스 1로 운영한다. Cloudflare는 도메인이 생긴 뒤 DNS와 WAF 계층으로 검토하며 Spring을 Workers로 다시 작성하지 않는다.

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

### 왜 Vercel인가?

관리자 웹은 Firebase ID token 검증과 PostgreSQL 관리자 role 조회를 서버에서 수행해야 하므로 정적 Hosting보다 Next.js server runtime이 필요하다. 별도 저장소를 Vercel Git 연동으로 배포해 PR Preview와 master target을 분리했고, 실제 Preview에서 401·403·200을 검증했다. 기존 Firebase Hosting과 전용 WIF 자원은 종료했다.

## 보안/운영

### 관리자는 어떻게 구분하나?

관리자 웹은 Firebase Auth 로그인으로 받은 ID token을 Next.js 서버에서 검증하고 PostgreSQL `bodeul.app_users.role == ADMIN`을 확인한다. 관리자 DB role은 SELECT만 가능하다. Android의 아직 전환하지 않은 Firestore 경로는 기존 Rules 역할 검증을 유지하며 custom claims는 사용하지 않는다.

### App Check는 왜 아직 강제하지 않았나?

초기에는 시연, preview, 디버그 환경이 자주 바뀌어서 enforcement를 바로 켜면 정상 검증이 막힐 수 있다. App Check는 Auth와 Rules를 대체하는 기능이 아니라 정상 앱/기기에서 온 요청인지 확인하는 추가 방어선이다. 현재는 Android debug/release provider, 관리자 웹 site key 경로, Functions 전환 스위치까지 준비했고, debug token과 웹 site key, 릴리스 provider 검증이 끝난 뒤 Functions, Storage, Firestore 순서로 단계적으로 강제한다. 세부 기준은 [App Check 적용 로드맵](../operations/app-check-enforcement-roadmap.md)에 정리했다.

### 백업/복원은 실제로 테스트했나?

백업/복원 도구 경로는 있으나, 복원 리허설 증적은 추가로 필요하다. 다음 운영 과제로 dev 데이터 일부를 대상으로 복원 가능성을 기록해야 한다.

### API Key는 어디에 두나?

Firebase Web API Key는 클라이언트 설정에 포함될 수 있지만 Auth 허용 도메인, Rules와 App Check를 함께 적용한다. Kakao Local REST 키는 Google Secret Manager에 두고 Cloud Run Core API만 읽는다. DB URL과 서버 전용 비밀값은 Vercel 또는 Cloud Run server runtime에만 주입하고 Git과 브라우저 환경변수에는 넣지 않는다.
