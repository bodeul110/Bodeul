# Firebase 선택 근거

기준일: 2026-07-18

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

Firebase 전체를 제거하지는 않지만 Firebase 중심 업무 데이터 구조도 유지하지 않는다. Firebase는 Auth, FCM, App Check와 Storage에 집중하고, 업무 데이터와 최종 role 인가는 Supabase PostgreSQL과 서버 계층이 담당한다.

## 작업 목적

PostgreSQL과 서버 계층을 도입한 뒤에도 Firebase의 어떤 기능을 왜 유지하는지 설명한다.

## 선택한 방식

- Firebase Auth로 사용자 로그인과 세션을 관리한다.
- Cloud Firestore는 도메인 전환 전 source of truth와 전환 후 30일 읽기 전용 rollback 자료로만 사용한다.
- Firebase Storage에 매니저 서류와 채팅 첨부 원본을 저장한다.
- Cloud Functions는 Firebase Auth·FCM·Storage와 직접 결합된 작업만 맡기고 업무 규칙은 Spring 또는 Next.js 서버로 옮긴다.
- FCM은 앱 푸시 알림에 사용한다.
- 관리자 웹 배포는 Vercel로 분리했으며 Firebase Hosting은 현재 메인 저장소의 운영 대상이 아니다.

## 대안

| 대안 | 장점 | 현재 판단 |
| --- | --- | --- |
| Spring/Next.js 서버 + PostgreSQL | 도메인 로직, 권한, 정산 쿼리를 서버에 집중할 수 있다. | 현재 채택했고 Cloud Run·Vercel·Supabase 기반을 준비했다. |
| Supabase 전체 전환 | PostgreSQL, Auth, Storage, Realtime을 한 공급자에서 제공한다. | Auth·FCM·Storage까지 동시에 바꾸지 않고 PostgreSQL과 Realtime만 사용한다. |
| AWS Amplify | 인증, API, Storage, Hosting을 묶어 제공한다. | 현재 Android/Firebase 구현과 운영 도구를 갈아엎는 비용이 이익보다 크다. |
| 자체 VM/DB 운영 | 인프라 제어권이 높다. | 보안 패치, 장애 대응, 백업, 모니터링까지 팀이 직접 떠안아야 한다. |

## 선택 이유

- 현재 MVP는 기능 검증과 운영 흐름 확인이 중요하다.
- Firebase Auth와 FCM의 Android 통합을 유지하면 로그인과 백그라운드 알림을 다시 만들 필요가 없다.
- Storage를 유지하면 매니저 서류와 채팅 첨부 이전을 DB 전환과 분리할 수 있다.
- App Check를 Firebase와 서버 양쪽에서 사용해 정식 앱·웹 요청 여부를 검증할 수 있다.
- GitHub Actions preflight와 `tools/firebase` 운영 스크립트가 이미 Firebase 구조를 기준으로 맞춰져 있다.

## 리스크

- 전환 전 클라이언트의 Firestore/Storage 직접 접근은 Rules 품질에 계속 의존한다.
- 복잡한 권한, 정산, 통계가 커지면 클라이언트 직접 접근 구조가 한계가 될 수 있다.
- release App Check가 아직 강제 상태가 아니라 운영 전 abuse 방어를 더 정리해야 한다.
- Firebase read/write, Storage, Functions 호출량이 늘면 비용 추적이 필요하다.

## 전환 조건

- 대부분의 쓰기가 Functions를 거쳐야 할 정도로 서버 검증 요구가 늘어난다.
- 정산/통계/검색이 핵심 기능이 되어 Firestore 쿼리와 집계 문서만으로 설명하기 어려워진다.
- 외부 API 연동과 권한 정책이 많아져 API gateway 성격의 서버가 필요해진다.
- 운영자가 늘고 관리자 권한 전파 정책을 더 엄격히 관리해야 한다.
